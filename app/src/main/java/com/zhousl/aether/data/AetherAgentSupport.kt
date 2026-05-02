package com.zhousl.aether.data

import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

internal val LlmReconnectDelayScheduleMillis = listOf(5_000L, 10_000L, 12_000L, 15_000L, 20_000L)
internal val TextSkillResourceExtensions = setOf(
    "md",
    "markdown",
    "txt",
    "json",
    "yaml",
    "yml",
    "toml",
    "xml",
    "html",
    "css",
    "csv",
    "tsv",
    "py",
    "sh",
    "js",
    "ts",
    "kt",
    "java",
    "rs",
    "rb",
    "pl",
    "ps1",
)

internal class LlmInactivityTimeoutException(
    timeoutMillis: Long,
) : java.io.IOException(
    "LLM inactivity timeout after ${timeoutMillis / 1000} seconds without any response activity."
)

internal class LlmInactivityTimeoutCancellationException(
    message: String,
) : CancellationException(message)
internal data class IndexedToolCall(
    val toolCall: ChatCompletionToolCall,
    val id: String,
)

internal data class BatchToolCall(
    val toolName: String,
    val argumentsJson: String,
)

internal data class ExecutedToolCallResult(
    val id: String,
    val name: String,
    val argumentsJson: String,
    val rawOutput: String,
    val visibleOutput: String,
)

internal class ParallelToolCallsUnsupportedRestart : RuntimeException()

data class AgentToolEvent(
    val id: String,
    val name: String,
    val argumentsJson: String,
    val outputJson: String? = null,
)

data class StreamingStatus(
    val text: String,
    val detail: String = "",
)

internal val LlmProvider.supportsParallelToolCallParameter: Boolean
    get() = this == LlmProvider.OpenAiResponses || this == LlmProvider.OpenAiCompatible

internal fun shouldReconnectLlmRequest(throwable: Throwable): Boolean {
    var current: Throwable? = throwable
    while (current != null) {
        when (current) {
            is LlmHttpException -> return current.isRetryable()
            is java.io.IOException -> return true
        }
        val message = current.message.orEmpty().lowercase()
        if (
            "timeout" in message ||
            "timed out" in message ||
            "connection reset" in message ||
            "unexpected end of stream" in message ||
            "stream was reset" in message ||
            "failed to connect" in message ||
            "network is unreachable" in message ||
            "broken pipe" in message ||
            "stream disconnected before completion" in message ||
            "stream closed before response.completed" in message ||
            "temporarily unavailable" in message ||
            "try again" in message ||
            "too many requests" in message ||
            "internal server error" in message ||
            "bad gateway" in message ||
            "gateway timeout" in message ||
            "service unavailable" in message ||
            "resource exhausted" in message ||
            "server overloaded" in message
        ) {
            return true
        }
        current = current.cause
    }
    return false
}

internal fun isParallelToolCallsUnsupportedFailure(throwable: Throwable): Boolean {
    var current: Throwable? = throwable
    while (current != null) {
        val message = current.message.orEmpty().lowercase()
        if (
            ("parallel_tool_calls" in message || "parallel tool" in message) &&
            (
                "unsupported" in message ||
                    "not supported" in message ||
                    "unrecognized" in message ||
                    "unknown" in message ||
                    "invalid" in message ||
                    "extra" in message ||
                    "forbidden" in message
                )
        ) {
            return true
        }
        current = current.cause
    }
    return false
}

internal fun buildStrictToolParameters(
    properties: JSONObject,
    required: List<String>,
): JSONObject {
    val requiredSet = required.toSet()
    val normalizedProperties = JSONObject()
    val normalizedRequired = JSONArray()
    val iterator = properties.keys()

    while (iterator.hasNext()) {
        val propertyName = iterator.next()
        normalizedRequired.put(propertyName)
        val propertySchema = JSONObject(properties.getJSONObject(propertyName).toString())
        normalizedProperties.put(
            propertyName,
            if (propertyName in requiredSet) {
                propertySchema
            } else {
                makeSchemaNullable(propertySchema)
            },
        )
    }

    return JSONObject().apply {
        put("type", "object")
        put("properties", normalizedProperties)
        put("required", normalizedRequired)
        put("additionalProperties", false)
    }
}

private fun makeSchemaNullable(schema: JSONObject): JSONObject =
    JSONObject(schema.toString()).apply {
        when (val typeValue = opt("type")) {
            is String -> {
                if (typeValue != "null") {
                    put("type", JSONArray().put(typeValue).put("null"))
                }
            }

            is JSONArray -> {
                var hasNull = false
                for (index in 0 until typeValue.length()) {
                    if (typeValue.optString(index) == "null") {
                        hasNull = true
                        break
                    }
                }
                if (!hasNull) {
                    typeValue.put("null")
                }
                put("type", typeValue)
            }
        }
    }

internal fun resolveReconnectDelayMillis(
    throwable: Throwable,
    reconnectFailureIndex: Int,
): Long = preferredRetryDelayMillis(throwable)
    ?.coerceAtLeast(0L)
    ?: LlmReconnectDelayScheduleMillis[reconnectFailureIndex]
