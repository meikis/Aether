package com.zhousl.aether.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object LlmApiClient {

    data class FetchModelsResult(
        val models: List<String>,
        val error: String? = null,
    )

    suspend fun fetchModels(config: LlmProviderConfig): FetchModelsResult = withContext(Dispatchers.IO) {
        try {
            when (config.providerType) {
                LlmProvider.OpenAiResponses -> fetchOpenAiModels(config)
                LlmProvider.OpenAiCompatible -> fetchOpenAiModels(config)
                LlmProvider.VertexExpress -> fetchVertexModels(config)
                LlmProvider.AnthropicMessages -> fetchAnthropicModels(config)
            }
        } catch (e: Exception) {
            FetchModelsResult(emptyList(), e.message ?: "Unknown error")
        }
    }

    private fun fetchOpenAiModels(config: LlmProviderConfig): FetchModelsResult {
        val baseUrl = config.baseUrl.trimEnd('/')
        val modelsUrl = when {
            baseUrl.endsWith("/responses") -> baseUrl.replace("/responses", "/models")
            baseUrl.endsWith("/chat/completions") -> baseUrl.replace("/chat/completions", "/models")
            baseUrl.endsWith("/v1") -> "$baseUrl/models"
            else -> "$baseUrl/models"
        }

        val connection = URL(modelsUrl).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.applyAetherLlmHeaders(config.customHeaders)
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000

        return try {
            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(responseText)
                val dataArray = json.optJSONArray("data")
                val models = mutableListOf<String>()
                if (dataArray != null) {
                    for (i in 0 until dataArray.length()) {
                        val modelObj = dataArray.optJSONObject(i)
                        val modelId = modelObj?.optString("id")
                        if (!modelId.isNullOrBlank()) {
                            models.add(modelId)
                        }
                    }
                }
                // Sort models: prefer chat/gpt models first
                models.sortWith(compareBy(
                    { if (it.contains("gpt") || it.contains("chat")) 0 else 1 },
                    { it }
                ))
                FetchModelsResult(models)
            } else {
                val errorText = connection.errorStream?.bufferedReader()?.readText() ?: "HTTP ${connection.responseCode}"
                FetchModelsResult(emptyList(), errorText)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchVertexModels(config: LlmProviderConfig): FetchModelsResult {
        // Vertex AI Express Mode doesn't have a simple models list endpoint
        // Return commonly used Gemini models
        val previewModels = if (usesOfficialVertexEndpoint(config.providerType, config.baseUrl)) {
            OfficialVertexPreviewModels
        } else {
            emptyList()
        }
        val defaultModels = previewModels + listOf(
            "gemini-2.5-flash",
            "gemini-2.5-pro",
            "gemini-2.0-flash",
            "gemini-1.5-flash",
            "gemini-1.5-pro",
        )
        return FetchModelsResult(defaultModels)
    }

    private fun fetchAnthropicModels(config: LlmProviderConfig): FetchModelsResult {
        val baseUrl = config.baseUrl.trimEnd('/')
        val modelsUrl = when {
            baseUrl.endsWith("/messages") -> baseUrl.replace("/messages", "/models")
            baseUrl.endsWith("/v1") -> "$baseUrl/models"
            else -> "$baseUrl/models"
        }

        val connection = URL(modelsUrl).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("x-api-key", config.apiKey)
        connection.setRequestProperty("anthropic-version", "2023-06-01")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.applyAetherLlmHeaders(config.customHeaders)
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000

        return try {
            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(responseText)
                val dataArray = json.optJSONArray("data")
                val models = mutableListOf<String>()
                if (dataArray != null) {
                    for (i in 0 until dataArray.length()) {
                        val modelObj = dataArray.optJSONObject(i)
                        val modelId = modelObj?.optString("id")
                        if (!modelId.isNullOrBlank()) {
                            models.add(modelId)
                        }
                    }
                }
                if (models.isEmpty()) {
                    FetchModelsResult(defaultAnthropicModels())
                } else {
                    FetchModelsResult(models.sorted())
                }
            } else {
                val errorText = connection.errorStream?.bufferedReader()?.readText() ?: "HTTP ${connection.responseCode}"
                FetchModelsResult(defaultAnthropicModels(), errorText)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun defaultAnthropicModels(): List<String> = listOf(
        "claude-opus-4-5",
        "claude-sonnet-4-5",
        "claude-haiku-4-5",
    )
}
