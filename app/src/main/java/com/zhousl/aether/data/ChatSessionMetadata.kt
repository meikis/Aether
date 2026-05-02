package com.zhousl.aether.data

import com.zhousl.aether.ui.ChatMessage
import com.zhousl.aether.ui.ChatSession
import com.zhousl.aether.ui.MessageAuthor
import com.zhousl.aether.ui.syncActiveBranches

internal fun ChatSession.withDerivedMessages(
    messages: List<ChatMessage>,
): ChatSession {
    val metadata = deriveSessionMetadata(messages)
    return copy(
        title = if (hasCustomTitle) title else metadata.first,
        preview = metadata.second,
        messages = syncActiveBranches(messages),
    )
}

private fun deriveSessionMetadata(messages: List<ChatMessage>): Pair<String, String> {
    val title = messages
        .firstOrNull { it.author == MessageAuthor.User }
        ?.summaryText()
        .orEmpty()
        .ifBlank { "New chat" }
        .take(36)
    val preview = messages
        .lastOrNull()
        ?.summaryText()
        .orEmpty()
        .ifBlank { "No messages yet." }
        .take(96)
    return title to preview
}

internal fun ChatMessage.summaryText(): String {
    val textSummary = text.trim()
    if (textSummary.isNotBlank()) return textSummary
    reasoningTrace?.let { trace ->
        trace.chunks.lastOrNull { it.detail.isNotBlank() || it.title.isNotBlank() }?.let { chunk ->
            return chunk.detail.ifBlank { chunk.title }
        }
        return if (trace.toolInvocations.isNotEmpty()) {
            "Thought and used ${trace.toolInvocations.size} tools"
        } else {
            "Thought"
        }
    }
    if (toolInvocations.isNotEmpty()) {
        return if (toolInvocations.size == 1) {
            when (toolInvocations.first().toolName.lowercase()) {
                "bash" -> "Ran bash command"
                "fetch_bash_output" -> "Fetched bash output"
                "kill_bash" -> "Stopped bash command"
                "sleep" -> "Waited"
                else -> "Used ${toolInvocations.first().toolName}"
            }
        } else {
            "Used ${toolInvocations.size} tools"
        }
    }
    if (attachments.isEmpty()) return "Empty message"
    if (attachments.size == 1) return attachments.first().name
    return "${attachments.size} attachments"
}
