package com.zhousl.aether.data

import android.content.Context
import com.zhousl.aether.termux.TermuxBashTool
import com.zhousl.aether.termux.TermuxContract
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val RootProbeTimeoutMillis = 1_500L
private const val RootSetupTimeoutMillis = 30_000L

enum class RootSetupIssue {
    Unknown,
    Available,
    Running,
    Ready,
    Unavailable,
    PermissionDenied,
    TermuxNotInstalled,
    Failed,
}

data class RootSetupState(
    val issue: RootSetupIssue = RootSetupIssue.Unknown,
    val detail: String = "",
    val rootAvailable: Boolean = false,
    val suPath: String = "",
    val didLaunchTermuxForBackground: Boolean = false,
    val lastUpdatedMillis: Long = 0L,
) {
    val isReady: Boolean
        get() = issue == RootSetupIssue.Ready

    val isRunning: Boolean
        get() = issue == RootSetupIssue.Running
}

class RootSetupController(
    private val context: Context,
    private val bashTool: TermuxBashTool,
) {
    suspend fun inspect(): RootSetupState = withContext(Dispatchers.IO) {
        val suPath = findSuPath()
        if (suPath.isBlank()) {
            RootSetupState(
                issue = RootSetupIssue.Unavailable,
                detail = "No su binary was detected on this device.",
                rootAvailable = false,
                lastUpdatedMillis = System.currentTimeMillis(),
            )
        } else {
            RootSetupState(
                issue = RootSetupIssue.Available,
                detail = "Root appears to be available. Aether can request su to finish local setup automatically.",
                rootAvailable = true,
                suPath = suPath,
                lastUpdatedMillis = System.currentTimeMillis(),
            )
        }
    }

    suspend fun configureLocalAccess(): RootSetupState = withContext(Dispatchers.IO) {
        val suPath = findSuPath()
        if (suPath.isBlank()) {
            bashTool.setRootBackgroundLaunchEnabled(false)
            return@withContext RootSetupState(
                issue = RootSetupIssue.Unavailable,
                detail = "No su binary was detected on this device.",
                rootAvailable = false,
                lastUpdatedMillis = System.currentTimeMillis(),
            )
        }
        if (!isTermuxInstalled()) {
            bashTool.setRootBackgroundLaunchEnabled(false)
            return@withContext RootSetupState(
                issue = RootSetupIssue.TermuxNotInstalled,
                detail = "Install Termux before using root automatic setup.",
                rootAvailable = true,
                suPath = suPath,
                lastUpdatedMillis = System.currentTimeMillis(),
            )
        }

        val commandResult = runProcess(
            command = listOf(suPath, "-c", buildTermuxRootSetupScript(context.packageName)),
            timeoutMillis = RootSetupTimeoutMillis,
        )
        if (commandResult.timedOut || commandResult.exitCode != 0) {
            bashTool.setRootBackgroundLaunchEnabled(false)
            val detail = commandResult.combinedOutput().ifBlank {
                if (commandResult.timedOut) {
                    "Root request timed out. Grant su to Aether, then try again."
                } else {
                    commandResult.launchError.ifBlank { "Root setup command failed." }
                }
            }
            return@withContext RootSetupState(
                issue = if (looksLikeRootDenied(detail) || commandResult.timedOut) {
                    RootSetupIssue.PermissionDenied
                } else {
                    RootSetupIssue.Failed
                },
                detail = detail.take(280),
                rootAvailable = true,
                suPath = suPath,
                lastUpdatedMillis = System.currentTimeMillis(),
            )
        }

        bashTool.setRootBackgroundLaunchEnabled(true)
        RootSetupState(
            issue = RootSetupIssue.Ready,
            detail = "Root setup completed. Aether will keep Termux available in the background when needed.",
            rootAvailable = true,
            suPath = suPath,
            lastUpdatedMillis = System.currentTimeMillis(),
        )
    }

    private fun isTermuxInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo(TermuxContract.PackageName, 0)
        true
    }.getOrDefault(false)

    private fun findSuPath(): String {
        val commonPaths = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su",
            "/debug_ramdisk/su",
        )
        commonPaths.firstOrNull { path ->
            File(path).let { it.exists() && it.canExecute() }
        }?.let { return it }

        val result = runProcess(
            command = listOf("sh", "-c", "command -v su 2>/dev/null || true"),
            timeoutMillis = RootProbeTimeoutMillis,
        )
        return result.stdout.lineSequence().firstOrNull()?.trim().orEmpty()
    }

    private fun runProcess(
        command: List<String>,
        timeoutMillis: Long,
    ): RootCommandResult {
        val process = runCatching {
            ProcessBuilder(command).start()
        }.getOrElse { throwable ->
            return RootCommandResult(
                exitCode = -1,
                launchError = throwable.message.orEmpty(),
            )
        }

        val stdoutReader = process.inputStream.startReadingText()
        val stderrReader = process.errorStream.startReadingText()
        val finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
        if (!finished) {
            runCatching { process.destroy() }
            if (!process.waitFor(800, TimeUnit.MILLISECONDS)) {
                runCatching { process.destroyForcibly() }
                process.waitFor(800, TimeUnit.MILLISECONDS)
            }
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
        }

        val stdout = stdoutReader.awaitText()
        val stderr = stderrReader.awaitText()
        return RootCommandResult(
            exitCode = if (finished) process.exitValue() else -1,
            stdout = stdout,
            stderr = stderr,
            timedOut = !finished,
        )
    }

    private fun buildTermuxRootSetupScript(
        aetherPackageName: String,
    ): String = """
        set -u
        termux_pkg='${TermuxContract.PackageName}'
        aether_pkg='${escapeForSingleQuoted(aetherPackageName)}'
        run_command_permission='${TermuxContract.RunCommandPermission}'
        current_user="${'$'}(cmd activity get-current-user 2>/dev/null || am get-current-user 2>/dev/null || printf '0')"
        current_user="${'$'}(printf '%s' "${'$'}current_user" | tr -cd '0-9')"
        current_user="${'$'}{current_user:-0}"
        termux_data="${'$'}(
          cmd package dump "${'$'}termux_pkg" 2>/dev/null |
            sed -n 's/^[[:space:]]*dataDir=//p' |
            grep -v '^null${'$'}' |
            head -n 1
        )"
        if [ -z "${'$'}termux_data" ] || [ "${'$'}termux_data" = "null" ]; then
          termux_data="/data/user/${'$'}current_user/${'$'}termux_pkg"
        fi
        termux_home="${'$'}termux_data/files/home"
        termux_files="${'$'}termux_data/files"
        termux_bash="${'$'}termux_data/files/usr/bin/bash"
        props_dir="${'$'}termux_home/.termux"
        props="${'$'}props_dir/termux.properties"

        app_id="${'$'}(
          cmd package dump "${'$'}termux_pkg" 2>/dev/null |
            sed -n 's/.*userId=//p' |
            head -n 1 |
            sed 's/[^0-9].*//'
        )"
        owner=""
        if [ -n "${'$'}app_id" ]; then
          owner="${'$'}app_id:${'$'}app_id"
          if [ "${'$'}current_user" -gt 0 ] 2>/dev/null; then
            full_uid="${'$'}((current_user * 100000 + app_id))"
            owner="${'$'}full_uid:${'$'}full_uid"
          fi
        fi

        mkdir -p "${'$'}termux_home" || exit 20
        if [ -n "${'$'}owner" ]; then
          chown "${'$'}owner" "${'$'}termux_data" "${'$'}termux_files" "${'$'}termux_home" 2>/dev/null || true
        fi
        chmod 700 "${'$'}termux_home" 2>/dev/null || true

        if [ ! -x "${'$'}termux_bash" ]; then
          printf '%s\n' 'Termux is installed but its bash runtime is not initialized. Open Termux once, then retry Root setup.' >&2
          exit 25
        fi

        mkdir -p "${'$'}props_dir" || exit 21
        touch "${'$'}props" || exit 22
        if grep -Eq '^[[:space:]]*#?[[:space:]]*allow-external-apps[[:space:]]*=' "${'$'}props"; then
          sed -i -E 's/^[[:space:]]*#?[[:space:]]*allow-external-apps[[:space:]]*=.*/allow-external-apps=true/' "${'$'}props" || exit 23
        else
          printf '\nallow-external-apps=true\n' >> "${'$'}props" || exit 24
        fi

        owner="${'$'}(stat -c '%u:%g' "${'$'}termux_home" 2>/dev/null || true)"
        if [ -n "${'$'}owner" ]; then
          chown "${'$'}owner" "${'$'}props_dir" "${'$'}props" 2>/dev/null || true
        fi
        chmod 700 "${'$'}props_dir" 2>/dev/null || true
        chmod 600 "${'$'}props" 2>/dev/null || true

        pm grant --user "${'$'}current_user" "${'$'}aether_pkg" "${'$'}run_command_permission" >/dev/null 2>&1 ||
          cmd package grant --user "${'$'}current_user" "${'$'}aether_pkg" "${'$'}run_command_permission" >/dev/null 2>&1 ||
          pm grant "${'$'}aether_pkg" "${'$'}run_command_permission" >/dev/null 2>&1 ||
          cmd package grant "${'$'}aether_pkg" "${'$'}run_command_permission" >/dev/null 2>&1 ||
          true
        am broadcast --user "${'$'}current_user" -a com.termux.app.reload_style -p "${'$'}termux_pkg" >/dev/null 2>&1 || true
        echo AETHER_ROOT_SETUP_READY
    """.trimIndent()

    private fun escapeForSingleQuoted(value: String): String =
        value.replace("'", "'\"'\"'")

    private fun InputStream.startReadingText(): ProcessStreamReader {
        val output = StringBuffer()
        val thread = Thread(
            {
                runCatching {
                    bufferedReader().use { reader ->
                        output.append(reader.readText())
                    }
                }
            },
            "AetherRootSetupStreamReader",
        ).apply {
            isDaemon = true
            start()
        }
        return ProcessStreamReader(thread, output)
    }

    private fun ProcessStreamReader.awaitText(): String {
        runCatching { thread.join(800) }
        return output.toString()
    }

    private fun looksLikeRootDenied(value: String): Boolean {
        val normalized = value.lowercase()
        return "denied" in normalized ||
            "permission" in normalized ||
            "not allowed" in normalized ||
            "su:" in normalized
    }
}

private data class ProcessStreamReader(
    val thread: Thread,
    val output: StringBuffer,
)

private data class RootCommandResult(
    val exitCode: Int,
    val stdout: String = "",
    val stderr: String = "",
    val timedOut: Boolean = false,
    val launchError: String = "",
) {
    fun combinedOutput(): String = listOf(stdout, stderr, launchError)
        .map(String::trim)
        .filter(String::isNotBlank)
        .joinToString("\n")
}
