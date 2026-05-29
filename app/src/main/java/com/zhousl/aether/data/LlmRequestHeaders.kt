package com.zhousl.aether.data

import com.zhousl.aether.BuildConfig
import okhttp3.Request
import java.net.HttpURLConnection

private val HttpHeaderNamePattern = Regex("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$")

internal val AetherLlmUserAgent: String
    get() = "Aether/${BuildConfig.VERSION_NAME} (Android)"

internal fun Request.Builder.applyAetherLlmHeaders(
    customHeaders: List<LlmCustomHeader>,
): Request.Builder = apply {
    header("User-Agent", AetherLlmUserAgent)
    customHeaders.normalizedLlmHeaders().forEach { header ->
        header(header.name, header.value)
    }
}

internal fun HttpURLConnection.applyAetherLlmHeaders(
    customHeaders: List<LlmCustomHeader>,
) {
    setRequestProperty("User-Agent", AetherLlmUserAgent)
    customHeaders.normalizedLlmHeaders().forEach { header ->
        setRequestProperty(header.name, header.value)
    }
}

internal fun List<LlmCustomHeader>.normalizedLlmHeaders(): List<LlmCustomHeader> =
    map { header -> LlmCustomHeader(header.name.trim(), header.value) }
        .filter { header -> header.name.isNotBlank() && HttpHeaderNamePattern.matches(header.name) }
        .distinctBy { header -> header.name.lowercase() }
