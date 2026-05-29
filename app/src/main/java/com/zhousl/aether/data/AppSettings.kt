package com.zhousl.aether.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.Locale
import java.util.UUID

enum class LlmProvider(
    val storageValue: String,
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModelId: String,
) {
    OpenAiResponses(
        storageValue = "openai_responses",
        displayName = "OpenAI (Responses)",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModelId = "gpt-5.4",
    ),
    OpenAiCompatible(
        storageValue = "openai_compatible",
        displayName = "OpenAI (Chat Completions)",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModelId = "gpt-5.4",
    ),
    VertexExpress(
        storageValue = "vertex_express",
        displayName = "Vertex AI (Express Mode)",
        defaultBaseUrl = "https://aiplatform.googleapis.com/v1",
        defaultModelId = "gemini-2.5-flash",
    ),
    AnthropicMessages(
        storageValue = "anthropic_messages",
        displayName = "Anthropic Messages API",
        defaultBaseUrl = "https://api.anthropic.com/v1",
        defaultModelId = "claude-sonnet-4-5",
    );

    companion object {
        fun fromStorage(value: String?): LlmProvider =
            entries.firstOrNull { it.storageValue == value } ?: OpenAiCompatible
    }
}

enum class AgentModeAuthorizationMethod(
    val storageValue: String,
    val displayName: String,
) {
    Root(
        storageValue = "root",
        displayName = "Root",
    ),
    Shizuku(
        storageValue = "shizuku",
        displayName = "Shizuku",
    );

    companion object {
        fun fromStorage(
            value: String?,
            defaultValue: AgentModeAuthorizationMethod = Shizuku,
        ): AgentModeAuthorizationMethod =
            entries.firstOrNull { it.storageValue == value } ?: defaultValue
    }
}

enum class AppLanguage(
    val storageValue: String,
    val languageTag: String,
) {
    English(
        storageValue = "en",
        languageTag = "en",
    ),
    SimplifiedChinese(
        storageValue = "zh-CN",
        languageTag = "zh-CN",
    );

    companion object {
        fun fromStorage(
            value: String?,
            defaultValue: AppLanguage = defaultAppLanguage(),
        ): AppLanguage = entries.firstOrNull { it.storageValue == value } ?: defaultValue
    }
}

enum class AppThemeMode(
    val storageValue: String,
) {
    System("system"),
    Light("light"),
    Dark("dark");

    companion object {
        fun fromStorage(value: String?): AppThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: System
    }
}

enum class AgentWorkspaceMode(
    val storageValue: String,
    val displayName: String,
) {
    Shared(
        storageValue = "shared",
        displayName = "Single Workspace",
    ),
    PerSession(
        storageValue = "per_session",
        displayName = "Independent Workspaces",
    );

    companion object {
        fun fromStorage(value: String?): AgentWorkspaceMode =
            entries.firstOrNull { it.storageValue == value } ?: Shared
    }
}

data class AppSettings(
    val provider: LlmProvider = LlmProvider.OpenAiCompatible,
    val apiKey: String = "",
    val baseUrl: String = LlmProvider.OpenAiCompatible.defaultBaseUrl,
    val modelId: String = LlmProvider.OpenAiCompatible.defaultModelId,
    val customHeaders: List<LlmCustomHeader> = emptyList(),
    val systemPrompt: String = "You are Aether, a local-first Android agent that can call tools and complete tasks on-device. Use available tools instead of guessing local state.",
    val tavilyApiKey: String = "",
    val tavilyBaseUrl: String = DefaultTavilyBaseUrl,
    val llmInactivityReconnectTimeoutSeconds: Int = DefaultLlmInactivityReconnectTimeoutSeconds,
    val keepTasksRunningInBackground: Boolean = true,
    val notifyOnTaskCompletion: Boolean = true,
    val agentWorkspaceMode: AgentWorkspaceMode = AgentWorkspaceMode.Shared,
    val termuxSetupCompleted: Boolean = false,
    val termuxSetupNoticeDismissed: Boolean = false,
    val termuxEnvironmentVariables: List<TermuxEnvironmentVariable> = emptyList(),
    val agentModeAuthorizationEnabled: Boolean = false,
    val agentModeAuthorizationMethod: AgentModeAuthorizationMethod = AgentModeAuthorizationMethod.Shizuku,
    val language: AppLanguage = defaultAppLanguage(),
    val themeMode: AppThemeMode = AppThemeMode.System,
    val defaultChatModelKey: String = "",
    val defaultTitleModelKey: String = "",
    val defaultNamingModelKey: String = "",
    val defaultCompactingModelKey: String = "",
    val unsupportedParallelToolCallProviderKeys: List<String> = emptyList(),
    val basicFunctionCallingCompatibilityMode: Boolean = false,
    val onboardingSeenVersion: Int = 0,
    val onboardingCompletedVersion: Int = 0,
    val privacyPolicyAccepted: Boolean = false,
    val lastUpdateCheckAtMillis: Long = 0L,
)

data class LlmCustomHeader(
    val name: String,
    val value: String,
)

data class TermuxEnvironmentVariable(
    val name: String,
    val value: String,
)

const val CurrentOnboardingVersion = 1
const val DefaultLlmInactivityReconnectTimeoutSeconds = 360
private const val MinLlmInactivityReconnectTimeoutSeconds = 30
private const val MaxLlmInactivityReconnectTimeoutSeconds = 3600
const val OnboardingStarterPrompt = "Hi"
const val AetherWebsiteUrl = "https://github.com/Zhou-Shilin"
const val AetherPrivacyPolicyUrl = "https://github.com/Zhou-Shilin/Aether/wiki/Privacy-Policy"

fun defaultAppLanguage(
    locale: Locale = Locale.getDefault(),
): AppLanguage = if (locale.language.equals("zh", ignoreCase = true)) {
    AppLanguage.SimplifiedChinese
} else {
    AppLanguage.English
}

fun normalizeLlmInactivityReconnectTimeoutSeconds(
    value: Int?,
): Int = when (value) {
    null -> DefaultLlmInactivityReconnectTimeoutSeconds
    else -> value.coerceIn(
        MinLlmInactivityReconnectTimeoutSeconds,
        MaxLlmInactivityReconnectTimeoutSeconds,
    )
}

fun normalizeTavilyBaseUrl(value: String): String =
    value.trim().ifBlank { DefaultTavilyBaseUrl }

fun AppSettings.shouldLaunchOnboarding(
    onboardingVersion: Int = CurrentOnboardingVersion,
): Boolean = onboardingSeenVersion < onboardingVersion

fun AppSettings.isOnboardingComplete(
    onboardingVersion: Int = CurrentOnboardingVersion,
): Boolean = onboardingCompletedVersion >= onboardingVersion

fun AppSettings.parallelToolCallSupportKey(): String {
    val normalizedBaseUrl = runCatching {
        val uri = URI(baseUrl.trim())
        val scheme = uri.scheme?.lowercase(Locale.US).orEmpty()
        val host = uri.host?.lowercase(Locale.US).orEmpty()
        val port = if (uri.port >= 0) ":${uri.port}" else ""
        val path = uri.path.orEmpty().trimEnd('/')
        "$scheme://$host$port$path"
    }.getOrDefault(baseUrl.trim().trimEnd('/').lowercase(Locale.US))
    return listOf(
        provider.storageValue,
        normalizedBaseUrl,
        modelId.trim().lowercase(Locale.US),
    ).joinToString("|")
}

fun AppSettings.supportsParallelToolCalls(): Boolean =
    !basicFunctionCallingCompatibilityMode &&
        parallelToolCallSupportKey() !in unsupportedParallelToolCallProviderKeys

fun isProviderSetupValid(
    provider: LlmProvider,
    apiKey: String,
    baseUrl: String,
    modelId: String,
): Boolean {
    if (baseUrl.trim().isEmpty() || modelId.trim().isEmpty()) return false
    if (provider.requiresApiKey(baseUrl) && apiKey.trim().isEmpty()) return false
    return true
}

val LlmProvider.requiresApiKey: Boolean
    get() = this == LlmProvider.VertexExpress || this == LlmProvider.AnthropicMessages

fun LlmProvider.requiresApiKey(baseUrl: String): Boolean =
    requiresApiKey || usesOfficialOpenAiEndpoint(this, baseUrl)

fun usesOfficialOpenAiEndpoint(
    provider: LlmProvider,
    baseUrl: String,
): Boolean {
    if (provider != LlmProvider.OpenAiResponses && provider != LlmProvider.OpenAiCompatible) return false
    val host = runCatching {
        URI(baseUrl.trim()).host.orEmpty().lowercase(Locale.US)
    }.getOrDefault("")
    return host == "api.openai.com"
}

val OfficialVertexPreviewModels: List<String> = listOf(
    "gemini-3.1-pro-preview",
    "gemini-3-flash-preview",
    "gemini-3.1-flash-lite-preview",
)

fun usesOfficialVertexEndpoint(
    provider: LlmProvider,
    baseUrl: String,
): Boolean {
    if (provider != LlmProvider.VertexExpress) return false
    val host = runCatching {
        URI(baseUrl.trim()).host.orEmpty().lowercase(Locale.US)
    }.getOrDefault("")
    return host == "aiplatform.googleapis.com" ||
        host.endsWith("-aiplatform.googleapis.com")
}

fun shouldMarkOnboardingCompleted(
    settings: AppSettings,
    isSuccessfulAssistantReply: Boolean,
): Boolean = isSuccessfulAssistantReply && !settings.isOnboardingComplete()

fun shouldRevealFollowUpTourCard(
    isAwaitingFollowUpTour: Boolean,
    isSuccessfulAssistantReply: Boolean,
): Boolean = isAwaitingFollowUpTour && isSuccessfulAssistantReply

// ──────────────────────────────────────────────────────────────────────────────
// Multi-Provider Configuration
// ──────────────────────────────────────────────────────────────────────────────

data class LlmProviderConfig(
    val id: String = UUID.randomUUID().toString(),
    val providerId: String,
    val name: String,
    val providerType: LlmProvider,
    val apiKey: String,
    val baseUrl: String,
    val modelId: String,
    val manualModelIds: List<String> = listOf(modelId),
    val customHeaders: List<LlmCustomHeader> = emptyList(),
    val cachedModels: List<String> = emptyList(),
    val enabledModelIds: List<String> = cachedModels + manualModelIds,
    val isEnabled: Boolean = true,
    val basicFunctionCallingCompatibilityMode: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = createdAtMillis,
)

internal fun LlmProviderConfig.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("providerId", providerId)
    put("name", name)
    put("providerType", providerType.storageValue)
    put("apiKey", apiKey)
    put("baseUrl", baseUrl)
    put("modelId", modelId)
    put("manualModelIds", JSONArray().apply { manualModelIds.forEach(::put) })
    put("customHeaders", customHeaders.toJsonArray())
    put("cachedModels", JSONArray().apply { cachedModels.forEach(::put) })
    put("enabledModelIds", JSONArray().apply { enabledModelIds.forEach(::put) })
    put("isEnabled", isEnabled)
    put("basicFunctionCallingCompatibilityMode", basicFunctionCallingCompatibilityMode)
    put("createdAtMillis", createdAtMillis)
    put("updatedAtMillis", updatedAtMillis)
}

internal fun parseProviderConfigs(rawValue: String): List<LlmProviderConfig> {
    if (rawValue.isBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(rawValue)
        buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                val providerType = LlmProvider.fromStorage(json.optString("providerType"))
                val providerName = json.optString("name").trim()
                    .ifBlank { providerType.displayName }
                val baseUrl = json.optString("baseUrl").trim()
                    .ifBlank { providerType.defaultBaseUrl }
                val modelId = json.optString("modelId").trim()
                    .ifBlank { providerType.defaultModelId }
                val enabledModelIds = json.optJSONArray("enabledModelIds").toStringListSafe()
                val manualModelIds = if (json.has("manualModelIds")) {
                    json.optJSONArray("manualModelIds").toStringListSafe()
                } else {
                    listOf(modelId)
                }
                val cachedModels = normalizeStringList(
                    buildList {
                        addAll(json.optJSONArray("cachedModels").toStringListSafe())
                        if (!json.has("manualModelIds")) {
                            removeAll(manualModelIds)
                        }
                    }
                )
                val availableModels = normalizeStringList(cachedModels + manualModelIds)
                val inferredProviderId = providerName
                    .sanitizeProviderId()
                    .ifBlank { "${providerType.storageValue}_${index + 1}" }
                add(
                    LlmProviderConfig(
                        id = json.optString("id").trim().ifBlank { UUID.randomUUID().toString() },
                        providerId = json.optString("providerId").trim().ifBlank { inferredProviderId },
                        name = providerName,
                        providerType = providerType,
                        apiKey = json.optString("apiKey"),
                        baseUrl = baseUrl,
                        modelId = modelId,
                        manualModelIds = manualModelIds,
                        customHeaders = parseCustomHeaders(json.optJSONArray("customHeaders")),
                        cachedModels = cachedModels,
                        enabledModelIds = if (json.has("enabledModelIds")) {
                            normalizeStringList(enabledModelIds.filter(availableModels::contains))
                        } else {
                            availableModels
                        },
                        isEnabled = if (json.has("isEnabled")) {
                            json.optBoolean("isEnabled", true)
                        } else {
                            true
                        },
                        basicFunctionCallingCompatibilityMode = json.optBoolean(
                            "basicFunctionCallingCompatibilityMode",
                            false,
                        ),
                        createdAtMillis = json.optLong("createdAtMillis", System.currentTimeMillis()),
                        updatedAtMillis = json.optLong("updatedAtMillis", System.currentTimeMillis()),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}

internal fun serializeProviderConfigs(configs: List<LlmProviderConfig>): String =
    JSONArray().apply { configs.forEach { put(it.toJson()) } }.toString()

internal fun List<LlmCustomHeader>.toJsonArray(): JSONArray = JSONArray().apply {
    forEach { header ->
        put(
            JSONObject().apply {
                put("name", header.name)
                put("value", header.value)
            }
        )
    }
}

internal fun parseCustomHeaders(array: JSONArray?): List<LlmCustomHeader> {
    if (array == null) return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val json = array.optJSONObject(index) ?: continue
            val name = json.optString("name").trim()
            if (name.isBlank()) continue
            add(
                LlmCustomHeader(
                    name = name,
                    value = json.optString("value"),
                )
            )
        }
    }.distinctBy { it.name.lowercase(Locale.US) }
}

private fun JSONArray?.toStringListSafe(): List<String> {
    if (this == null) return emptyList()
    return normalizeStringList(
        buildList {
            for (index in 0 until length()) {
                val value = optString(index).trim()
                if (value.isNotEmpty()) {
                    add(value)
                }
            }
        }
    )
}

private fun normalizeStringList(values: List<String>): List<String> =
    values
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()

private val ProviderIdPattern = Regex("^[a-z0-9_]+$")

fun isValidProviderId(value: String): Boolean = ProviderIdPattern.matches(value.trim())

fun String.sanitizeProviderId(): String =
    lowercase(Locale.US)
        .replace(Regex("[^a-z0-9_]+"), "_")
        .trim('_')

fun buildModelOptionKey(
    providerConfigId: String,
    modelId: String,
): String = "$providerConfigId::$modelId"

fun LlmProviderConfig.availableModels(): List<String> = normalizeStringList(cachedModels + manualModelIds)

fun LlmProviderConfig.enabledModels(): List<String> = normalizeStringList(
    enabledModelIds.filter { availableModels().contains(it) }
)

data class ProviderModelOption(
    val key: String,
    val providerConfigId: String,
    val providerId: String,
    val providerName: String,
    val providerType: LlmProvider,
    val apiKey: String,
    val baseUrl: String,
    val modelId: String,
    val customHeaders: List<LlmCustomHeader>,
    val basicFunctionCallingCompatibilityMode: Boolean,
    val fullLabel: String,
    val chatLabel: String,
)

enum class AutomaticModelPurpose {
    Chat,
    Title,
    Naming,
    Compacting,
}

fun List<LlmProviderConfig>.availableModelOptions(
    includeDisabledProviders: Boolean = false,
    includeDisabledModels: Boolean = false,
): List<ProviderModelOption> {
    val scopedConfigs = (if (includeDisabledProviders) this else filter { it.isEnabled })
        .filter { it.baseUrl.trim().isNotEmpty() && it.providerId.trim().isNotEmpty() }
    val modelCounts = scopedConfigs
        .flatMap { config ->
            val models = if (includeDisabledModels) config.availableModels() else config.enabledModels()
            models.map { modelId -> modelId to config.id }
        }
        .groupingBy { it.first }
        .eachCount()

    return scopedConfigs.flatMap { config ->
        val models = if (includeDisabledModels) config.availableModels() else config.enabledModels()
        models.map { modelId ->
            val providerId = config.providerId.trim()
            val providerName = config.name.trim().ifBlank { providerId }
            val normalizedModelId = modelId.trim()
            val fullLabel = "$providerId/$normalizedModelId"
            ProviderModelOption(
                key = buildModelOptionKey(config.id, normalizedModelId),
                providerConfigId = config.id,
                providerId = providerId,
                providerName = providerName,
                providerType = config.providerType,
                apiKey = config.apiKey,
                baseUrl = config.baseUrl.trim(),
                modelId = normalizedModelId,
                customHeaders = config.customHeaders,
                basicFunctionCallingCompatibilityMode = config.basicFunctionCallingCompatibilityMode,
                fullLabel = fullLabel,
                chatLabel = if ((modelCounts[normalizedModelId] ?: 0) > 1) fullLabel else normalizedModelId,
            )
        }
    }.sortedWith(compareBy(ProviderModelOption::providerId, ProviderModelOption::modelId))
}

fun AppSettings.withModelOption(option: ProviderModelOption): AppSettings = copy(
    provider = option.providerType,
    apiKey = option.apiKey.trim(),
    baseUrl = option.baseUrl.trim(),
    modelId = option.modelId.trim(),
    customHeaders = option.customHeaders,
    basicFunctionCallingCompatibilityMode = option.basicFunctionCallingCompatibilityMode,
)

fun List<ProviderModelOption>.findModelOption(key: String?): ProviderModelOption? =
    firstOrNull { it.key == key }

fun List<ProviderModelOption>.resolveAutomaticModelKey(
    purpose: AutomaticModelPurpose,
): String {
    if (isEmpty()) return ""
    val rankedOption = mapNotNull { option ->
        automaticModelPriority(option.modelId, purpose)?.let { priority -> option to priority }
    }
        .minWithOrNull(compareBy<Pair<ProviderModelOption, Int>> { it.second }.thenBy { it.first.providerId }.thenBy { it.first.modelId })
        ?.first
    return rankedOption?.key ?: firstOrNull()?.key.orEmpty()
}

private fun automaticModelPriority(
    modelId: String,
    purpose: AutomaticModelPurpose,
): Int? {
    val normalized = modelId.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "")
    val isGemini31Pro = normalized.contains("gemini31pro")
    val isGemini3Flash = Regex("gemini3(?:0)?flash").containsMatchIn(normalized) ||
        normalized.contains("gemini3flashpreview")
    val isGemini31FlashLite = normalized.contains("gemini31flashlite")

    return when (purpose) {
        AutomaticModelPurpose.Chat -> when {
            normalized.contains("gpt55") -> 0
            normalized.contains("gpt54") -> 1
            normalized.contains("claude") && normalized.contains("opus") && normalized.contains("47") -> 2
            normalized.contains("claude") && normalized.contains("sonnet") && normalized.contains("46") -> 3
            isGemini31Pro -> 4
            isGemini3Flash -> 5
            else -> null
        }

        AutomaticModelPurpose.Title,
        AutomaticModelPurpose.Naming,
        AutomaticModelPurpose.Compacting -> when {
            isGemini3Flash -> 0
            isGemini31FlashLite -> 1
            normalized.contains("gpt54mini") -> 2
            normalized.contains("claude") && normalized.contains("haiku") && normalized.contains("46") -> 3
            normalized.contains("gpt54") -> 4
            normalized.contains("claude") && normalized.contains("sonnet") -> 5
            else -> null
        }
    }
}
