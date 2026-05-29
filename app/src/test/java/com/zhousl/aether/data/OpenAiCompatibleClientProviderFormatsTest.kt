package com.zhousl.aether.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiCompatibleClientProviderFormatsTest {
    private val client = OpenAiCompatibleClient()

    @Test
    fun officialOpenAiEndpointRequiresApiKeyBeforeRequest() = runBlocking {
        val result = client.createChatCompletion(
            settings = AppSettings(
                provider = LlmProvider.OpenAiCompatible,
                apiKey = "",
                baseUrl = "https://api.openai.com/v1",
                modelId = "gpt-5.4",
            ),
            systemPrompt = "",
            conversation = listOf(
                JSONObject().apply {
                    put("role", "user")
                    put("content", "Hello")
                }
            ),
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("API Key"))
    }

    @Test
    fun basicCompatibilityModeUsesMinimalChatCompletionTools() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "choices": [
                        {
                          "message": {
                            "role": "assistant",
                            "content": "Done."
                          }
                        }
                      ]
                    }
                    """.trimIndent()
                )
        )
        server.start()

        try {
            val settings = AppSettings(
                provider = LlmProvider.OpenAiCompatible,
                apiKey = "test-key",
                baseUrl = server.url("/v1").toString(),
                modelId = "deepseek-v4",
                basicFunctionCallingCompatibilityMode = true,
            )
            val conversation = client.buildConversation(
                settings = settings,
                messages = listOf(LlmMessage("user", listOf(LlmTextPart("Read README.md")))),
            )

            val result = client.createChatCompletion(
                settings = settings,
                systemPrompt = "Be concise.",
                conversation = conversation,
                tools = listOf(readTool()),
                toolChoice = "required",
                parallelToolCalls = true,
            ).getOrThrow()

            assertEquals("Done.", result.assistantText)

            val request = server.takeRequest()
            assertEquals("/v1/chat/completions", request.path)
            val payload = JSONObject(request.body.readUtf8())
            assertEquals("auto", payload.getString("tool_choice"))
            assertFalse(payload.has("parallel_tool_calls"))

            val tool = payload.getJSONArray("tools").getJSONObject(0)
            assertEquals("function", tool.getString("type"))
            assertFalse(tool.has("strict"))
            val function = tool.getJSONObject("function")
            assertEquals("read", function.getString("name"))
            assertFalse(function.has("strict"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun chatCompletionParsesOpenAiTokenUsage() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "choices": [
                        {
                          "message": {
                            "role": "assistant",
                            "content": "Done."
                          }
                        }
                      ],
                      "usage": {
                        "prompt_tokens": 11,
                        "completion_tokens": 7,
                        "total_tokens": 18,
                        "completion_tokens_details": {
                          "reasoning_tokens": 3
                        },
                        "prompt_tokens_details": {
                          "cached_tokens": 5
                        }
                      }
                    }
                    """.trimIndent()
                )
        )
        server.start()

        try {
            val result = client.createChatCompletion(
                settings = AppSettings(
                    provider = LlmProvider.OpenAiCompatible,
                    apiKey = "test-key",
                    baseUrl = server.url("/v1").toString(),
                    modelId = "gpt-test",
                ),
                systemPrompt = "",
                conversation = listOf(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", "Hello")
                    }
                ),
            ).getOrThrow()

            val usage = result.tokenUsage!!
            assertEquals(11L, usage.inputTokens)
            assertEquals(7L, usage.outputTokens)
            assertEquals(18L, usage.totalTokens)
            assertEquals(3L, usage.reasoningTokens)
            assertEquals(5L, usage.cachedInputTokens)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun vertexGenerateContentParsesTokenUsage() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "candidates": [
                        {
                          "content": {
                            "role": "model",
                            "parts": [
                              { "text": "Done." }
                            ]
                          }
                        }
                      ],
                      "usageMetadata": {
                        "promptTokenCount": 13,
                        "candidatesTokenCount": 8,
                        "totalTokenCount": 21,
                        "cachedContentTokenCount": 4
                      }
                    }
                    """.trimIndent()
                )
        )
        server.start()

        try {
            val result = client.createChatCompletion(
                settings = AppSettings(
                    provider = LlmProvider.VertexExpress,
                    apiKey = "test-key",
                    baseUrl = server.url("/v1").toString(),
                    modelId = "gemini-test",
                ),
                systemPrompt = "",
                conversation = listOf(
                    JSONObject().apply {
                        put("role", "user")
                        put("parts", org.json.JSONArray().put(JSONObject().put("text", "Hello")))
                    }
                ),
            ).getOrThrow()

            val usage = result.tokenUsage!!
            assertEquals(13L, usage.inputTokens)
            assertEquals(8L, usage.outputTokens)
            assertEquals(21L, usage.totalTokens)
            assertEquals(4L, usage.cachedInputTokens)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun llmRequestsIncludeAetherUserAgentAndCustomHeaders() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "choices": [
                        {
                          "message": {
                            "role": "assistant",
                            "content": "Done."
                          }
                        }
                      ]
                    }
                    """.trimIndent()
                )
        )
        server.start()

        try {
            val settings = AppSettings(
                provider = LlmProvider.OpenAiCompatible,
                apiKey = "test-key",
                baseUrl = server.url("/v1").toString(),
                modelId = "gpt-test",
                customHeaders = listOf(
                    LlmCustomHeader("X-Aether-Test", "enabled"),
                ),
            )

            client.createChatCompletion(
                settings = settings,
                systemPrompt = "",
                conversation = listOf(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", "Hello")
                    }
                ),
            ).getOrThrow()

            val request = server.takeRequest()
            assertEquals(AetherLlmUserAgent, request.getHeader("User-Agent"))
            assertEquals("enabled", request.getHeader("X-Aether-Test"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun disableReasoningAddsDeepSeekThinkingDisabledParameter() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "choices": [
                        {
                          "message": {
                            "role": "assistant",
                            "content": "Title"
                          }
                        }
                      ]
                    }
                    """.trimIndent()
                )
        )
        server.start()

        try {
            val settings = AppSettings(
                provider = LlmProvider.OpenAiCompatible,
                apiKey = "test-key",
                baseUrl = server.url("/v1").toString(),
                modelId = "deepseek-v4-flash",
            )

            client.createChatCompletion(
                settings = settings,
                systemPrompt = "",
                conversation = listOf(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", "Summarize")
                    }
                ),
                disableReasoning = true,
            ).getOrThrow()

            val payload = JSONObject(server.takeRequest().body.readUtf8())
            assertEquals("disabled", payload.getJSONObject("thinking").getString("type"))
            assertFalse(payload.has("reasoning"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun disableReasoningAddsOpenRouterReasoningNoneParameter() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "choices": [
                        {
                          "message": {
                            "role": "assistant",
                            "content": "Title"
                          }
                        }
                      ]
                    }
                    """.trimIndent()
                )
        )
        server.start()

        try {
            val settings = AppSettings(
                provider = LlmProvider.OpenAiCompatible,
                apiKey = "test-key",
                baseUrl = server.url("/v1").toString(),
                modelId = "openrouter/reasoning-model",
            )

            client.createChatCompletion(
                settings = settings,
                systemPrompt = "",
                conversation = listOf(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", "Summarize")
                    }
                ),
                disableReasoning = true,
            ).getOrThrow()

            val payload = JSONObject(server.takeRequest().body.readUtf8())
            assertEquals("none", payload.getJSONObject("reasoning").getString("effort"))
            assertFalse(payload.has("thinking"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun openAiResponsesUsesResponsesEndpointAndToolShape() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "output": [
                        {
                          "type": "message",
                          "role": "assistant",
                          "content": [
                            { "type": "output_text", "text": "I'll read it." }
                          ]
                        },
                        {
                          "type": "function_call",
                          "call_id": "call_1",
                          "name": "read",
                          "arguments": "{\"path\":\"README.md\"}"
                        }
                      ]
                    }
                    """.trimIndent()
                )
        )
        server.start()

        try {
            val settings = AppSettings(
                provider = LlmProvider.OpenAiResponses,
                apiKey = "test-key",
                baseUrl = server.url("/v1").toString(),
                modelId = "gpt-5.4",
            )
            val conversation = client.buildConversation(
                settings = settings,
                messages = listOf(LlmMessage("user", listOf(LlmTextPart("Read README.md")))),
            )

            val result = client.createChatCompletion(
                settings = settings,
                systemPrompt = "Be concise.",
                conversation = conversation,
                tools = listOf(readTool()),
                parallelToolCalls = true,
            ).getOrThrow()

            assertEquals("I'll read it.", result.assistantText)
            assertEquals("read", result.toolCalls.single().name)
            assertEquals("""{"path":"README.md"}""", result.toolCalls.single().arguments)

            val request = server.takeRequest()
            assertEquals("/v1/responses", request.path)
            assertEquals("Bearer test-key", request.getHeader("Authorization"))

            val payload = JSONObject(request.body.readUtf8())
            assertEquals(false, payload.getBoolean("store"))
            assertEquals("Be concise.", payload.getString("instructions"))
            assertEquals(
                "input_text",
                payload.getJSONArray("input")
                    .getJSONObject(0)
                    .getJSONArray("content")
                    .getJSONObject(0)
                    .getString("type"),
            )
            val tool = payload.getJSONArray("tools").getJSONObject(0)
            assertEquals("function", tool.getString("type"))
            assertEquals("read", tool.getString("name"))
            assertFalse(tool.has("function"))
            assertEquals(true, payload.getBoolean("parallel_tool_calls"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun anthropicToolResultsAreGroupedIntoOneUserMessage() {
        val settings = AppSettings(
            provider = LlmProvider.AnthropicMessages,
            apiKey = "test-key",
            baseUrl = "https://api.anthropic.com/v1",
            modelId = "claude-sonnet-4-5",
        )

        val messages = client.buildToolResultMessages(
            settings = settings,
            results = listOf(
                ChatCompletionToolResult("toolu_1", "read", """{"ok":true}"""),
                ChatCompletionToolResult("toolu_2", "ls", """{"files":[]}"""),
            ),
        )

        assertEquals(1, messages.size)
        val message = messages.single()
        assertEquals("user", message.getString("role"))
        val content = message.getJSONArray("content")
        assertEquals(2, content.length())
        assertEquals("tool_result", content.getJSONObject(0).getString("type"))
        assertEquals("toolu_1", content.getJSONObject(0).getString("tool_use_id"))
        assertEquals("toolu_2", content.getJSONObject(1).getString("tool_use_id"))
    }

    @Test
    fun anthropicMessagesUsesMessagesEndpointAndToolShape() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "role": "assistant",
                      "content": [
                        { "type": "text", "text": "I'll read it." },
                        {
                          "type": "tool_use",
                          "id": "toolu_1",
                          "name": "read",
                          "input": { "path": "README.md" }
                        }
                      ]
                    }
                    """.trimIndent()
                )
        )
        server.start()

        try {
            val settings = AppSettings(
                provider = LlmProvider.AnthropicMessages,
                apiKey = "test-key",
                baseUrl = server.url("/v1").toString(),
                modelId = "claude-sonnet-4-5",
            )
            val conversation = client.buildConversation(
                settings = settings,
                messages = listOf(LlmMessage("user", listOf(LlmTextPart("Read README.md")))),
            )

            val result = client.createChatCompletion(
                settings = settings,
                systemPrompt = "Be concise.",
                conversation = conversation,
                tools = listOf(readTool()),
            ).getOrThrow()

            assertEquals("I'll read it.", result.assistantText)
            assertEquals("read", result.toolCalls.single().name)
            assertEquals("""{"path":"README.md"}""", result.toolCalls.single().arguments)

            val request = server.takeRequest()
            assertEquals("/v1/messages", request.path)
            assertEquals("test-key", request.getHeader("x-api-key"))
            assertEquals("2023-06-01", request.getHeader("anthropic-version"))

            val payload = JSONObject(request.body.readUtf8())
            assertEquals("Be concise.", payload.getString("system"))
            assertEquals(4096, payload.getInt("max_tokens"))
            assertEquals(
                "text",
                payload.getJSONArray("messages")
                    .getJSONObject(0)
                    .getJSONArray("content")
                    .getJSONObject(0)
                    .getString("type"),
            )
            val tool = payload.getJSONArray("tools").getJSONObject(0)
            assertEquals("read", tool.getString("name"))
            assertEquals("object", tool.getJSONObject("input_schema").getString("type"))
            assertFalse(tool.has("function"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun anthropicStreamingToolUseKeepsInputJsonDeltaArguments() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "text/event-stream")
                .setBody(
                    """
                    data: {"type":"message_start","message":{"role":"assistant","content":[]}}

                    data: {"type":"content_block_start","index":0,"content_block":{"type":"tool_use","id":"toolu_1","name":"read","input":{}}}

                    data: {"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"{\"path\":\"README.md\"}"}}

                    data: {"type":"content_block_stop","index":0}

                    data: {"type":"message_stop"}

                    """.trimIndent()
                )
        )
        server.start()

        try {
            val settings = AppSettings(
                provider = LlmProvider.AnthropicMessages,
                apiKey = "test-key",
                baseUrl = server.url("/v1").toString(),
                modelId = "claude-sonnet-4-5",
            )
            val conversation = client.buildConversation(
                settings = settings,
                messages = listOf(LlmMessage("user", listOf(LlmTextPart("Read README.md")))),
            )

            val result = client.streamChatCompletion(
                settings = settings,
                systemPrompt = "Be concise.",
                conversation = conversation,
                tools = listOf(readTool()),
            ).getOrThrow()

            assertEquals("read", result.toolCalls.single().name)
            assertEquals("README.md", JSONObject(result.toolCalls.single().arguments).getString("path"))

            val request = server.takeRequest()
            assertEquals("/v1/messages", request.path)
            assertEquals("text/event-stream", request.getHeader("Accept"))
            assertEquals(true, JSONObject(request.body.readUtf8()).getBoolean("stream"))
        } finally {
            server.shutdown()
        }
    }

    private fun readTool(): JSONObject = JSONObject(
        """
        {
          "type": "function",
          "function": {
            "name": "read",
            "description": "Read a file.",
            "parameters": {
              "type": "object",
              "properties": {
                "path": { "type": "string" }
              },
              "required": ["path"]
            },
            "strict": true
          }
        }
        """.trimIndent()
    )
}
