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
}
