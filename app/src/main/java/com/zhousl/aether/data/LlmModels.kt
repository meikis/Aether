package com.zhousl.aether.data

import org.json.JSONObject
data class ChatCompletionResult(
    val assistantText: String,
    val toolCalls: List<ChatCompletionToolCall>,
    val assistantMessage: JSONObject,
    val reasoningText: String = "",
    val reasoningSummaryText: String = "",
)

data class ChatCompletionToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)

data class ChatCompletionToolResult(
    val callId: String,
    val name: String,
    val output: String,
)

data class OpenAiResponsesCompactionResult(
    val assistantText: String,
    val providerPayload: JSONObject,
)

sealed interface LlmContentPart

data class LlmTextPart(
    val text: String,
) : LlmContentPart

data class LlmImagePart(
    val mimeType: String,
    val base64Data: String,
) : LlmContentPart

data class LlmMessage(
    val role: String,
    val contentParts: List<LlmContentPart>,
    val providerPayload: JSONObject? = null,
)
