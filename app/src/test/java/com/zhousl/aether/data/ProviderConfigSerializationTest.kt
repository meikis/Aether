package com.zhousl.aether.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderConfigSerializationTest {
    @Test
    fun importedProviderConfigBackfillsMissingNameAndBaseUrl() {
        val configs = parseProviderConfigs(
            JSONArray().put(
                JSONObject()
                    .put("providerType", LlmProvider.AnthropicMessages.storageValue)
                    .put("name", "")
                    .put("baseUrl", "")
                    .put("modelId", "claude-test")
            ).toString()
        )

        val config = configs.single()
        assertEquals(LlmProvider.AnthropicMessages.displayName, config.name)
        assertEquals(LlmProvider.AnthropicMessages.defaultBaseUrl, config.baseUrl)

        val option = configs.availableModelOptions().single()
        assertEquals(LlmProvider.AnthropicMessages.defaultBaseUrl, option.baseUrl)
        assertEquals("claude-test", option.modelId)
    }

    @Test
    fun availableModelOptionsSkipsConfigsWithBlankBaseUrl() {
        val options = listOf(
            LlmProviderConfig(
                id = "bad-provider",
                providerId = "bad",
                name = "Bad",
                providerType = LlmProvider.OpenAiCompatible,
                apiKey = "",
                baseUrl = "",
                modelId = "model-a",
            )
        ).availableModelOptions()

        assertTrue(options.isEmpty())
    }

    @Test
    fun providerConfigRoundTripsCustomHeaders() {
        val serialized = serializeProviderConfigs(
            listOf(
                LlmProviderConfig(
                    providerId = "openrouter",
                    name = "OpenRouter",
                    providerType = LlmProvider.OpenAiCompatible,
                    apiKey = "test-key",
                    baseUrl = "https://openrouter.ai/api/v1",
                    modelId = "openai/gpt-test",
                    customHeaders = listOf(
                        LlmCustomHeader("HTTP-Referer", "https://example.com"),
                        LlmCustomHeader("X-Title", "Aether"),
                    ),
                )
            )
        )

        val config = parseProviderConfigs(serialized).single()

        assertEquals("HTTP-Referer", config.customHeaders[0].name)
        assertEquals("https://example.com", config.customHeaders[0].value)
        assertEquals("X-Title", config.customHeaders[1].name)
        assertEquals("Aether", config.customHeaders[1].value)
        assertEquals(config.customHeaders, listOf(config).availableModelOptions().single().customHeaders)
    }

    @Test
    fun providerConfigRoundTripsManualModelIdsSeparatelyFromCachedModels() {
        val serialized = serializeProviderConfigs(
            listOf(
                LlmProviderConfig(
                    providerId = "custom",
                    name = "Custom",
                    providerType = LlmProvider.OpenAiCompatible,
                    apiKey = "test-key",
                    baseUrl = "https://api.example.com/v1",
                    modelId = "manual-a",
                    manualModelIds = listOf("manual-a", "manual-b"),
                    cachedModels = listOf("fetched-a"),
                    enabledModelIds = listOf("manual-a", "manual-b", "fetched-a"),
                )
            )
        )

        val config = parseProviderConfigs(serialized).single()

        assertEquals(listOf("manual-a", "manual-b"), config.manualModelIds)
        assertEquals(listOf("fetched-a"), config.cachedModels)
        assertEquals(listOf("fetched-a", "manual-a", "manual-b"), config.availableModels())
    }

    @Test
    fun providerConfigDropsEnabledManualModelWhenManualModelIdIsRemoved() {
        val config = LlmProviderConfig(
            providerId = "custom",
            name = "Custom",
            providerType = LlmProvider.OpenAiCompatible,
            apiKey = "test-key",
            baseUrl = "https://api.example.com/v1",
            modelId = "fetched-a",
            manualModelIds = emptyList(),
            cachedModels = listOf("fetched-a"),
            enabledModelIds = listOf("manual-a", "fetched-a"),
        )

        assertEquals(listOf("fetched-a"), config.availableModels())
        assertEquals(listOf("fetched-a"), config.enabledModels())
    }
}
