package com.zhousl.aether.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zhousl.aether.ui.AttachmentKind
import com.zhousl.aether.ui.AttachmentWorkspaceState
import com.zhousl.aether.ui.ChatAttachment
import com.zhousl.aether.ui.ChatBranchGroup
import com.zhousl.aether.ui.ChatMessage
import com.zhousl.aether.ui.ChatSession
import com.zhousl.aether.ui.ChatToolInvocation
import com.zhousl.aether.ui.MessageAuthor
import com.zhousl.aether.ui.syncActiveBranches
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private const val DraftSessionId = "draft"

private val Context.chatDataStore by preferencesDataStore(name = "aether_chats")

data class PersistedChatState(
    val sessions: List<ChatSession> = emptyList(),
    val currentSessionId: String = DraftSessionId,
)

class ChatRepository(
    private val context: Context,
) {
    val chatState: Flow<PersistedChatState> = context.chatDataStore.data.map { preferences ->
        PersistedChatState(
            sessions = parseChatSessions(preferences[SESSIONS_JSON].orEmpty()),
            currentSessionId = preferences[CURRENT_SESSION_ID] ?: DraftSessionId,
        )
    }

    suspend fun updateChatState(
        sessions: List<ChatSession>,
        currentSessionId: String,
    ) {
        context.chatDataStore.edit {
            it[SESSIONS_JSON] = serializeChatSessions(sessions)
            it[CURRENT_SESSION_ID] = currentSessionId
        }
    }

    private companion object {
        val SESSIONS_JSON = stringPreferencesKey("sessions_json")
        val CURRENT_SESSION_ID = stringPreferencesKey("current_session_id")
    }
}

internal fun parseChatSessions(rawValue: String): List<ChatSession> {
    if (rawValue.isBlank()) return emptyList()

    return runCatching {
        val sessions = JSONArray(rawValue)
        buildList {
            for (sessionIndex in 0 until sessions.length()) {
                val session = sessions.optJSONObject(sessionIndex) ?: continue
                add(
                    ChatSession(
                        id = session.optString("id").ifBlank { "session-$sessionIndex" },
                        title = session.optString("title"),
                        preview = session.optString("preview"),
                        hasCustomTitle = session.optBoolean("hasCustomTitle", false),
                        messages = parseMessages(session.optJSONArray("messages")),
                        selectedSkillIds = parseStringList(session.optJSONArray("selectedSkillIds")).ifEmpty {
                            parseActiveSkillContexts(session.optString("activeSkillsJson")).map { it.skillId }
                        },
                        activeSkills = parseActiveSkillContexts(session.optString("activeSkillsJson")),
                        activeMcpServerIds = parseStringList(session.optJSONArray("activeMcpServerIds")),
                        agentModeEnabled = session.optBoolean("agentModeEnabled", false),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}

internal fun serializeChatSessions(sessions: List<ChatSession>): String =
    JSONArray().apply {
        sessions.forEach { session ->
            put(session.toJson())
        }
    }.toString()

internal fun ChatSession.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("title", title)
    put("preview", preview)
    put("hasCustomTitle", hasCustomTitle)
    put("agentModeEnabled", agentModeEnabled)
    put("selectedSkillIds", JSONArray().apply { selectedSkillIds.forEach(::put) })
    put("messages", JSONArray().apply { syncActiveBranches(messages).forEach { put(it.toJson()) } })
    put("activeSkillsJson", serializeActiveSkillContexts(activeSkills))
    put("activeMcpServerIds", JSONArray().apply { activeMcpServerIds.forEach(::put) })
}

private fun parseMessages(messages: JSONArray?): List<ChatMessage> {
    if (messages == null) return emptyList()

    return buildList {
        for (messageIndex in 0 until messages.length()) {
            val message = messages.optJSONObject(messageIndex) ?: continue
            add(
                ChatMessage(
                    id = message.optString("id").ifBlank { "message-$messageIndex" },
                    author = if (message.optString("author") == MessageAuthor.User.name) {
                        MessageAuthor.User
                    } else {
                        MessageAuthor.Agent
                    },
                    text = message.optString("text"),
                    createdAtMillis = message.optLong("createdAtMillis").takeIf { it > 0L }
                        ?: timestampFromMessageId(message.optString("id")),
                    attachments = parseAttachments(message.optJSONArray("attachments")),
                    toolInvocations = parseToolInvocations(message.optJSONArray("toolInvocations")),
                    thoughtDurationMillis = if (message.has("thoughtDurationMillis")) {
                        message.optLong("thoughtDurationMillis")
                    } else {
                        null
                    },
                    branchGroup = parseBranchGroup(message.optJSONObject("branchGroup")),
                    responseGroupId = message.optString("responseGroupId").ifBlank { null },
                )
            )
        }
    }
}

private fun ChatMessage.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("author", author.name)
    put("text", text)
    if (createdAtMillis > 0L) {
        put("createdAtMillis", createdAtMillis)
    }
    thoughtDurationMillis?.let { put("thoughtDurationMillis", it) }
    branchGroup?.let { put("branchGroup", it.toJson()) }
    responseGroupId?.let { put("responseGroupId", it) }
    put("toolInvocations", JSONArray().apply { toolInvocations.forEach { put(it.toJson()) } })
    put("attachments", JSONArray().apply { attachments.forEach { put(it.toJson()) } })
}

private fun parseBranchGroup(json: JSONObject?): ChatBranchGroup? {
    if (json == null) return null
    val branchesJson = json.optJSONArray("branches") ?: return null
    val branches = buildList {
        for (index in 0 until branchesJson.length()) {
            add(parseMessages(branchesJson.optJSONArray(index)))
        }
    }.filter { it.isNotEmpty() }
    if (branches.size <= 1) return null
    return ChatBranchGroup(
        branches = branches,
        selectedIndex = json.optInt("selectedIndex", 0).coerceIn(0, branches.lastIndex),
    )
}

private fun ChatBranchGroup.toJson(): JSONObject = JSONObject().apply {
    val safeSelectedIndex = selectedIndex.coerceIn(0, branches.lastIndex.coerceAtLeast(0))
    put("selectedIndex", safeSelectedIndex)
    put(
        "branches",
        JSONArray().apply {
            branches.forEach { branch ->
                put(JSONArray().apply { branch.forEach { put(it.toJson()) } })
            }
        },
    )
}

private fun parseAttachments(attachments: JSONArray?): List<ChatAttachment> {
    if (attachments == null) return emptyList()

    return buildList {
        for (attachmentIndex in 0 until attachments.length()) {
            val attachment = attachments.optJSONObject(attachmentIndex) ?: continue
            val mimeType = attachment.optString("mimeType")
            val workspacePath = attachment.optString("workspacePath")
            add(
                ChatAttachment(
                    id = attachment.optString("id").ifBlank { "attachment-$attachmentIndex" },
                    uri = attachment.optString("uri"),
                    name = attachment.optString("name").ifBlank { "Attachment ${attachmentIndex + 1}" },
                    mimeType = mimeType,
                    sizeBytes = if (attachment.has("sizeBytes")) attachment.optLong("sizeBytes") else null,
                    kind = AttachmentKind.fromStored(
                        value = attachment.optString("kind"),
                        mimeType = mimeType,
                    ),
                    workspacePath = workspacePath,
                    workspaceState = if (workspacePath.isBlank()) {
                        AttachmentWorkspaceState.Failed
                    } else {
                        AttachmentWorkspaceState.Ready
                    },
                    workspaceError = if (workspacePath.isBlank()) {
                        "This attachment is missing its workspace copy."
                    } else {
                        ""
                    },
                )
            )
        }
    }
}

private fun ChatAttachment.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("uri", uri)
    put("name", name)
    put("mimeType", mimeType)
    put("kind", kind.name)
    put("workspacePath", workspacePath)
    sizeBytes?.let { put("sizeBytes", it) }
}

private fun parseToolInvocations(toolInvocations: JSONArray?): List<ChatToolInvocation> {
    if (toolInvocations == null) return emptyList()

    return buildList {
        for (toolIndex in 0 until toolInvocations.length()) {
            val toolInvocation = toolInvocations.optJSONObject(toolIndex) ?: continue
            add(
                ChatToolInvocation(
                    id = toolInvocation.optString("id").ifBlank { "tool-$toolIndex" },
                    toolName = toolInvocation.optString("toolName"),
                    argumentsJson = toolInvocation.optString("argumentsJson"),
                    outputJson = toolInvocation.optString("outputJson"),
                    isRunning = toolInvocation.optBoolean("isRunning"),
                    startedAtUptimeMillis = toolInvocation.optLong("startedAtUptimeMillis"),
                    completedAtUptimeMillis = if (toolInvocation.has("completedAtUptimeMillis")) {
                        toolInvocation.optLong("completedAtUptimeMillis")
                    } else {
                        null
                    },
                )
            )
        }
    }
}

private fun ChatToolInvocation.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("toolName", toolName)
    put("argumentsJson", argumentsJson)
    put("outputJson", outputJson)
    put("isRunning", isRunning)
    put("startedAtUptimeMillis", startedAtUptimeMillis)
    completedAtUptimeMillis?.let { put("completedAtUptimeMillis", it) }
}

private fun parseStringList(array: JSONArray?): List<String> {
    if (array == null) return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val value = array.optString(index).trim()
            if (value.isNotEmpty()) {
                add(value)
            }
        }
    }
}

private fun timestampFromMessageId(messageId: String): Long {
    val timestamp = messageId.substringAfterLast('-', missingDelimiterValue = "")
    return timestamp.toLongOrNull()?.takeIf { it > 0L } ?: 0L
}
