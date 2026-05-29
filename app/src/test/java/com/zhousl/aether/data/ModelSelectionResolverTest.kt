package com.zhousl.aether.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelSelectionResolverTest {
    @Test
    fun resolveModelSettingsUsesSessionPreferredModelBeforeDefault() {
        val defaultConfig = providerConfig(
            id = "default-provider",
            providerId = "default",
            baseUrl = "https://default.example/v1",
            modelId = "gpt-5.4",
        )
        val sessionConfig = providerConfig(
            id = "session-provider",
            providerId = "session",
            baseUrl = "https://session.example/v1",
            modelId = "claude-sonnet-4-5",
        )
        val providerConfigs = listOf(defaultConfig, sessionConfig)
        val settings = AppSettings(
            baseUrl = "https://legacy.example/v1",
            modelId = "legacy",
            defaultChatModelKey = buildModelOptionKey(defaultConfig.id, defaultConfig.modelId),
        )

        val resolved = resolveModelSettings(
            baseSettings = settings,
            providerConfigs = providerConfigs,
            preferredModelKey = buildModelOptionKey(sessionConfig.id, sessionConfig.modelId),
            fallbackModelKey = resolveDefaultChatModelKey(settings, providerConfigs),
        )

        assertEquals("https://session.example/v1", resolved.baseUrl)
        assertEquals("claude-sonnet-4-5", resolved.modelId)
    }

    @Test
    fun resolveModelSettingsFallsBackToDefaultWhenPreferredMissing() {
        val defaultConfig = providerConfig(
            id = "default-provider",
            providerId = "default",
            baseUrl = "https://default.example/v1",
            modelId = "gpt-5.4",
        )
        val settings = AppSettings(
            baseUrl = "https://legacy.example/v1",
            modelId = "legacy",
            defaultChatModelKey = buildModelOptionKey(defaultConfig.id, defaultConfig.modelId),
        )

        val resolved = resolveModelSettings(
            baseSettings = settings,
            providerConfigs = listOf(defaultConfig),
            preferredModelKey = "missing-provider::missing-model",
            fallbackModelKey = resolveDefaultChatModelKey(settings, listOf(defaultConfig)),
        )

        assertEquals("https://default.example/v1", resolved.baseUrl)
        assertEquals("gpt-5.4", resolved.modelId)
    }

    @Test
    fun resolveDefaultCompactingModelPrefersSummaryOptimizedAutomaticModel() {
        val chatConfig = providerConfig(
            id = "chat-provider",
            providerId = "chat",
            baseUrl = "https://chat.example/v1",
            modelId = "gpt-5.4",
        )
        val compactConfig = providerConfig(
            id = "compact-provider",
            providerId = "compact",
            baseUrl = "https://compact.example/v1",
            modelId = "gemini-3-flash-preview",
        )

        val resolved = resolveDefaultCompactingModelKey(
            settings = AppSettings(),
            providerConfigs = listOf(chatConfig, compactConfig),
        )

        assertEquals(buildModelOptionKey(compactConfig.id, compactConfig.modelId), resolved)
    }

    @Test
    fun resolveDefaultCompactingModelPrefersOpenAiResponsesProvider() {
        val chatOptimizedConfig = providerConfig(
            id = "chat-optimized-provider",
            providerId = "chat-optimized",
            baseUrl = "https://chat.example/v1",
            modelId = "gemini-3-flash-preview",
        )
        val openAiResponsesConfig = providerConfig(
            id = "openai-responses-provider",
            providerId = "openai",
            baseUrl = "https://api.openai.com/v1",
            modelId = "gpt-5.4",
            providerType = LlmProvider.OpenAiResponses,
        )

        val resolved = resolveDefaultCompactingModelKey(
            settings = AppSettings(),
            providerConfigs = listOf(chatOptimizedConfig, openAiResponsesConfig),
        )

        assertEquals(buildModelOptionKey(openAiResponsesConfig.id, openAiResponsesConfig.modelId), resolved)
    }

    private fun providerConfig(
        id: String,
        providerId: String,
        baseUrl: String,
        modelId: String,
        providerType: LlmProvider = LlmProvider.OpenAiCompatible,
    ): LlmProviderConfig = LlmProviderConfig(
        id = id,
        providerId = providerId,
        name = providerId,
        providerType = providerType,
        apiKey = "test-key",
        baseUrl = baseUrl,
        modelId = modelId,
    )
}
