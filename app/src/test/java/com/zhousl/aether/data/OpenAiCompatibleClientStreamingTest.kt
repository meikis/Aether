package com.zhousl.aether.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiCompatibleClientStreamingTest {
    private val client = OpenAiCompatibleClient()

    @Test
    fun streamChatCompletionMarksToolCallOnlyChunksAsActivity() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "text/event-stream")
                .setBody(
                    """
                    data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"read","arguments":"{\"path\":\"README.md\"}"}}]}}]}

                    data: [DONE]

                    """.trimIndent()
                )
        )
        server.start()

        try {
            val settings = AppSettings(
                provider = LlmProvider.OpenAiCompatible,
                apiKey = "test-key",
                baseUrl = server.url("/v1").toString(),
                modelId = "gpt-5.4",
            )
            var activityCount = 0

            val result = client.streamChatCompletion(
                settings = settings,
                systemPrompt = "",
                conversation = listOf(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", "Read README.md")
                    }
                ),
                onStreamActivity = {
                    activityCount += 1
                },
            ).getOrThrow()

            assertEquals("", result.assistantText)
            assertEquals(1, result.toolCalls.size)
            assertEquals("read", result.toolCalls.single().name)
            assertTrue(activityCount >= 1)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun streamChatCompletionRequestsAndParsesUsageChunk() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "text/event-stream")
                .setBody(
                    """
                    data: {"choices":[{"delta":{"content":"Hi"}}]}

                    data: {"choices":[],"usage":{"prompt_tokens":9,"completion_tokens":4,"total_tokens":13}}

                    data: [DONE]

                    """.trimIndent()
                )
        )
        server.start()

        try {
            val settings = AppSettings(
                provider = LlmProvider.OpenAiCompatible,
                apiKey = "test-key",
                baseUrl = server.url("/v1").toString(),
                modelId = "gpt-5.4",
            )

            val result = client.streamChatCompletion(
                settings = settings,
                systemPrompt = "",
                conversation = listOf(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", "Hi")
                    }
                ),
            ).getOrThrow()

            assertEquals("Hi", result.assistantText)
            assertEquals(9L, result.tokenUsage?.inputTokens)
            assertEquals(4L, result.tokenUsage?.outputTokens)
            assertEquals(13L, result.tokenUsage?.totalTokens)

            val payload = JSONObject(server.takeRequest().body.readUtf8())
            assertTrue(payload.getBoolean("stream"))
            assertTrue(payload.getJSONObject("stream_options").getBoolean("include_usage"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun streamAnthropicMessagesParsesMessageDeltaUsage() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "text/event-stream")
                .setBody(
                    """
                    event: message_start
                    data: {"type":"message_start","message":{"id":"msg_1","type":"message","role":"assistant","content":[],"usage":{"input_tokens":12,"output_tokens":1}}}

                    event: content_block_start
                    data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

                    event: content_block_delta
                    data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Done."}}

                    event: content_block_stop
                    data: {"type":"content_block_stop","index":0}

                    event: message_delta
                    data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":6}}

                    event: message_stop
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
                modelId = "claude-test",
            )

            val result = client.streamChatCompletion(
                settings = settings,
                systemPrompt = "",
                conversation = listOf(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", org.json.JSONArray().put(JSONObject().put("type", "text").put("text", "Hello")))
                    }
                ),
            ).getOrThrow()

            assertEquals("Done.", result.assistantText)
            assertEquals(12L, result.tokenUsage?.inputTokens)
            assertEquals(6L, result.tokenUsage?.outputTokens)
            assertEquals(18L, result.tokenUsage?.totalTokens)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun streamChatCompletionPreservesReasoningContentForAssistantMessage() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "text/event-stream")
                .setBody(
                    """
                    data: {"choices":[{"delta":{"reasoning_content":"I should read the file first. "}}]}

                    data: {"choices":[{"delta":{"reasoning_content":"Then answer from evidence."}}]}

                    data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"read","arguments":"{\"path\":\"README.md\"}"}}]}}]}

                    data: [DONE]

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
            )
            val reasoningDeltas = StringBuilder()

            val result = client.streamChatCompletion(
                settings = settings,
                systemPrompt = "",
                conversation = listOf(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", "Read README.md")
                    }
                ),
                onReasoningDelta = { reasoningDeltas.append(it) },
            ).getOrThrow()

            val expectedReasoning = "I should read the file first. Then answer from evidence."
            assertEquals(expectedReasoning, reasoningDeltas.toString())
            assertEquals(expectedReasoning, result.reasoningText)
            assertEquals(expectedReasoning, result.assistantMessage.getString("reasoning_content"))
            assertEquals(JSONObject.NULL, result.assistantMessage.get("content"))
            assertEquals("read", result.toolCalls.single().name)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun streamChatCompletionPreservesEmptyDeepSeekReasoningContentForToolCalls() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "text/event-stream")
                .setBody(
                    """
                    data: {"choices":[{"delta":{"reasoning_content":""}}]}

                    data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"read","arguments":"{\"path\":\"README.md\"}"}}]}}]}

                    data: [DONE]

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

            val result = client.streamChatCompletion(
                settings = settings,
                systemPrompt = "",
                conversation = listOf(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", "Read README.md")
                    }
                ),
            ).getOrThrow()

            assertTrue(result.assistantMessage.has("reasoning_content"))
            assertEquals("", result.assistantMessage.getString("reasoning_content"))
            assertEquals("read", result.toolCalls.single().name)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun streamChatCompletionIgnoresNullReasoningContentOnBodyDeltas() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "text/event-stream")
                .setBody(
                    """
                    data: {"choices":[{"delta":{"reasoning_content":"I can greet the user."}}]}

                    data: {"choices":[{"delta":{"reasoning_content":null,"content":"Hi"}}]}

                    data: {"choices":[{"delta":{"reasoning_content":null,"content":"!"}}]}

                    data: [DONE]

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
            val reasoningDeltas = mutableListOf<String>()
            val textDeltas = mutableListOf<String>()

            val result = client.streamChatCompletion(
                settings = settings,
                systemPrompt = "",
                conversation = listOf(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", "Hi")
                    }
                ),
                onReasoningDelta = { reasoningDeltas += it },
                onTextDelta = { textDeltas += it },
            ).getOrThrow()

            assertEquals(listOf("I can greet the user."), reasoningDeltas)
            assertEquals(listOf("Hi", "!"), textDeltas)
            assertEquals("Hi!", result.assistantText)
            assertEquals("I can greet the user.", result.reasoningText)
            assertEquals("I can greet the user.", result.assistantMessage.getString("reasoning_content"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun streamChatCompletionPreservesOpenRouterReasoningDetails() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "text/event-stream")
                .setBody(
                    """
                    data: {"choices":[{"delta":{"reasoning":"Checking docs.","reasoning_details":[{"type":"reasoning.text","text":"Checking docs.","signature":"sig_1"}]}}]}

                    data: {"choices":[{"delta":{"content":"Done."}}]}

                    data: [DONE]

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
            val rawReasoningDeltas = StringBuilder()
            val summaryReasoningDeltas = StringBuilder()

            val result = client.streamChatCompletion(
                settings = settings,
                systemPrompt = "",
                conversation = listOf(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", "Check docs")
                    }
                ),
                onReasoningDelta = { rawReasoningDeltas.append(it) },
                onReasoningSummaryDelta = { summaryReasoningDeltas.append(it) },
            ).getOrThrow()

            assertEquals("Done.", result.assistantText)
            assertEquals("Checking docs.", rawReasoningDeltas.toString())
            assertEquals("", summaryReasoningDeltas.toString())
            assertEquals("Checking docs.", result.reasoningText)
            assertEquals("", result.reasoningSummaryText)
            assertEquals("Checking docs.", result.assistantMessage.getString("reasoning"))
            val detail = result.assistantMessage
                .getJSONArray("reasoning_details")
                .getJSONObject(0)
            assertEquals("sig_1", detail.getString("signature"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun streamChatCompletionUsesReasoningDetailsTextAsRawReasoningWhenTopLevelReasoningIsMissing() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "text/event-stream")
                .setBody(
                    """
                    data: {"choices":[{"delta":{"reasoning_details":[{"type":"reasoning.text","text":"Checking docs from raw reasoning details.","signature":"sig_1"}]}}]}

                    data: {"choices":[{"delta":{"content":"Done."}}]}

                    data: [DONE]

                    """.trimIndent()
                )
        )
        server.start()

        try {
            val settings = AppSettings(
                provider = LlmProvider.OpenAiCompatible,
                apiKey = "test-key",
                baseUrl = server.url("/v1").toString(),
                modelId = "openrouter/raw-reasoning-model",
            )
            val rawReasoningDeltas = StringBuilder()
            val summaryReasoningDeltas = StringBuilder()

            val result = client.streamChatCompletion(
                settings = settings,
                systemPrompt = "",
                conversation = listOf(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", "Check docs")
                    }
                ),
                onReasoningDelta = { rawReasoningDeltas.append(it) },
                onReasoningSummaryDelta = { summaryReasoningDeltas.append(it) },
            ).getOrThrow()

            assertEquals("Done.", result.assistantText)
            assertEquals("Checking docs from raw reasoning details.", rawReasoningDeltas.toString())
            assertEquals("", summaryReasoningDeltas.toString())
            assertEquals("Checking docs from raw reasoning details.", result.reasoningText)
            assertEquals("", result.reasoningSummaryText)
            assertEquals("reasoning.text", result.assistantMessage
                .getJSONArray("reasoning_details")
                .getJSONObject(0)
                .getString("type"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun streamChatCompletionUsesReasoningDetailsSummaryAsVisibleReasoningSummary() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "text/event-stream")
                .setBody(
                    """
                    data: {"choices":[{"delta":{"reasoning_details":[{"type":"reasoning.summary","summary":[{"type":"summary_text","text":"Checking official docs before answering."}]}]}}]}

                    data: {"choices":[{"delta":{"content":"Done."}}]}

                    data: [DONE]

                    """.trimIndent()
                )
        )
        server.start()

        try {
            val settings = AppSettings(
                provider = LlmProvider.OpenAiCompatible,
                apiKey = "test-key",
                baseUrl = server.url("/v1").toString(),
                modelId = "openrouter/reasoning-summary-model",
            )
            val rawReasoningDeltas = StringBuilder()
            val summaryReasoningDeltas = StringBuilder()

            val result = client.streamChatCompletion(
                settings = settings,
                systemPrompt = "",
                conversation = listOf(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", "Check docs")
                    }
                ),
                onReasoningDelta = { rawReasoningDeltas.append(it) },
                onReasoningSummaryDelta = { summaryReasoningDeltas.append(it) },
            ).getOrThrow()

            assertEquals("Done.", result.assistantText)
            assertEquals("", rawReasoningDeltas.toString())
            assertEquals("Checking official docs before answering.", summaryReasoningDeltas.toString())
            assertEquals("Checking official docs before answering.", result.reasoningText)
            assertEquals("Checking official docs before answering.", result.reasoningSummaryText)
            assertEquals("reasoning.summary", result.assistantMessage
                .getJSONArray("reasoning_details")
                .getJSONObject(0)
                .getString("type"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun streamChatCompletionReportsErrorJsonWithoutMessage() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "error": {
                        "type": "invalid_request_error",
                        "code": "missing_reasoning_content",
                        "param": "messages"
                      }
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

            val result = client.streamChatCompletion(
                settings = settings,
                systemPrompt = "",
                conversation = listOf(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", "Hello")
                    }
                ),
            )

            assertTrue(result.isFailure)
            val message = result.exceptionOrNull()?.message.orEmpty()
            assertTrue(message.contains("missing_reasoning_content"))
            assertTrue(message.contains("invalid_request_error"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun streamChatCompletionFailsAfterInactivityTimeoutWithoutAnyResponseActivity() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setSocketPolicy(SocketPolicy.NO_RESPONSE)
        )
        server.start()

        try {
            val settings = AppSettings(
                provider = LlmProvider.OpenAiCompatible,
                apiKey = "test-key",
                baseUrl = server.url("/v1").toString(),
                modelId = "gpt-5.4",
                llmInactivityReconnectTimeoutSeconds = 1,
            )

            val startedAt = System.nanoTime()
            val result = client.streamChatCompletion(
                settings = settings,
                systemPrompt = "",
                conversation = listOf(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", "Say hello")
                    }
                ),
            )
            val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L

            assertTrue(result.isFailure)
            assertTrue(elapsedMillis in 800L..5_000L)
            val message = result.exceptionOrNull()?.message.orEmpty().lowercase()
            assertTrue(
                message.contains("timeout") ||
                    message.contains("timed out") ||
                    message.contains("failed to connect") ||
                    message.contains("canceled") ||
                    message.contains("cancelled"),
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun streamChatCompletionFailsWhenOpenAiStreamClosesBeforeDoneMarker() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "text/event-stream")
                .setBody(
                    """
                    data: {"choices":[{"delta":{"content":"hello"}}]}

                    """.trimIndent()
                )
        )
        server.start()

        try {
            val settings = AppSettings(
                provider = LlmProvider.OpenAiCompatible,
                apiKey = "test-key",
                baseUrl = server.url("/v1").toString(),
                modelId = "gpt-5.4",
                llmInactivityReconnectTimeoutSeconds = 5,
            )

            val result = client.streamChatCompletion(
                settings = settings,
                systemPrompt = "",
                conversation = listOf(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", "Say hello")
                    }
                ),
            )

            assertTrue(result.isFailure)
            val message = result.exceptionOrNull()?.message.orEmpty().lowercase()
            assertTrue(message.contains("completion"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun streamChatCompletionIgnoresNullToolCallsOnDeltaAndAccumulatesRealToolCalls() = runBlocking {
        // Reproduces mimo-v2.5-pro behavior where deltas include
        // \"tool_calls\": null before the actual tool_calls delta arrives.
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "text/event-stream")
                .setBody(
                    """
                    data: {"choices":[{"delta":{"reasoning_content":"Need to read the file.","tool_calls":null}}]}

                    data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"read","arguments":"{\"path\":\""}}]}}]}

                    data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"README.md\"}"}}]}}]}

                    data: [DONE]

                    """.trimIndent()
                )
        )
        server.start()

        try {
            val settings = AppSettings(
                provider = LlmProvider.OpenAiCompatible,
                apiKey = "test-key",
                baseUrl = server.url("/v1").toString(),
                modelId = "mimo-v2.5-pro",
            )
            val reasoningDeltas = StringBuilder()

            val result = client.streamChatCompletion(
                settings = settings,
                systemPrompt = "",
                conversation = listOf(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", "Read README.md")
                    }
                ),
                onReasoningDelta = { reasoningDeltas.append(it) },
            ).getOrThrow()

            assertEquals("Need to read the file.", reasoningDeltas.toString())
            assertEquals(1, result.toolCalls.size)
            assertEquals("read", result.toolCalls.single().name)
            assertEquals("""{"path":"README.md"}""", result.toolCalls.single().arguments)
        } finally {
            server.shutdown()
        }
    }
}
