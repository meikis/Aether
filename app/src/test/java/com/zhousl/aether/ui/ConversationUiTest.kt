package com.zhousl.aether.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationUiTest {
    @Test
    fun pendingIndicatorShowsThinkingAfterBodyTextResetsForToolCall() {
        val previousBlocks = listOf(
            AssistantResponseBlock.Text(
                id = "text-1",
                text = "I will inspect the file first.",
            ),
            AssistantResponseBlock.ToolGroup(
                id = "tools-1",
                toolInvocations = listOf(
                    ChatToolInvocation(
                        id = "call-1",
                        toolName = "read",
                        argumentsJson = """{"path":"README.md"}""",
                        isRunning = true,
                    )
                ),
            ),
        )

        assertTrue(previousBlocks.any { it is AssistantResponseBlock.Text && it.text.isNotBlank() })
        assertEquals(
            PendingGenerationIndicator.Thinking,
            pendingGenerationIndicator(
                isSending = true,
                pendingAssistantText = "",
                pendingStatusText = "",
                lastVisibleMessageAuthor = MessageAuthor.User,
            ),
        )
    }

    @Test
    fun pendingIndicatorHidesThinkingWhileBodyTextIsActive() {
        assertEquals(
            PendingGenerationIndicator.None,
            pendingGenerationIndicator(
                isSending = true,
                pendingAssistantText = "Streaming body text",
                pendingStatusText = "",
                lastVisibleMessageAuthor = MessageAuthor.User,
            ),
        )
    }

    @Test
    fun pendingIndicatorHidesThinkingAfterAgentMessageAppears() {
        assertEquals(
            PendingGenerationIndicator.None,
            pendingGenerationIndicator(
                isSending = true,
                pendingAssistantText = "",
                pendingStatusText = "",
                lastVisibleMessageAuthor = MessageAuthor.Agent,
            ),
        )
    }

    @Test
    fun pendingIndicatorHidesThinkingWhilePendingWorkIsVisible() {
        assertEquals(
            PendingGenerationIndicator.None,
            pendingGenerationIndicator(
                isSending = true,
                pendingAssistantText = "",
                pendingStatusText = "",
                hasVisiblePendingWork = true,
                lastVisibleMessageAuthor = MessageAuthor.User,
            ),
        )
    }

    @Test
    fun pendingIndicatorShowsThinkingForEmptyReasoningPlaceholder() {
        assertEquals(
            PendingGenerationIndicator.Thinking,
            pendingGenerationIndicator(
                isSending = true,
                pendingAssistantText = "",
                pendingStatusText = "",
                hasVisiblePendingReasoning = hasVisibleReasoningStatus(ReasoningTrace(id = "empty")),
                lastVisibleMessageAuthor = MessageAuthor.User,
            ),
        )
    }

    @Test
    fun pendingIndicatorHidesThinkingForVisibleReasoningStatus() {
        assertEquals(
            PendingGenerationIndicator.None,
            pendingGenerationIndicator(
                isSending = true,
                pendingAssistantText = "",
                pendingStatusText = "",
                hasVisiblePendingReasoning = hasVisibleReasoningStatus(
                    ReasoningTrace(id = "reasoning", latestStatusText = "Checking"),
                ),
                lastVisibleMessageAuthor = MessageAuthor.User,
            ),
        )
    }

    @Test
    fun pendingIndicatorShowsStatusWhenStatusTextExists() {
        assertEquals(
            PendingGenerationIndicator.Status,
            pendingGenerationIndicator(
                isSending = true,
                pendingAssistantText = "Streaming body text",
                pendingStatusText = "Reconnecting...",
                lastVisibleMessageAuthor = MessageAuthor.User,
            ),
        )
    }

    @Test
    fun pendingIndicatorHidesAfterTurnEnds() {
        assertEquals(
            PendingGenerationIndicator.None,
            pendingGenerationIndicator(
                isSending = false,
                pendingAssistantText = "",
                pendingStatusText = "",
                lastVisibleMessageAuthor = MessageAuthor.Agent,
            ),
        )
    }

    @Test
    fun pendingGenerationBlockHidesCommittedTextEcho() {
        val reply = "I'm doing great, thank you for asking!"

        assertEquals(
            false,
            shouldRenderPendingGenerationBlock(
                isSending = true,
                pendingResponseBlocks = listOf(
                    AssistantResponseBlock.Text(id = "pending-text", text = reply),
                ),
                pendingToolInvocations = emptyList(),
                pendingStatusText = "",
                lastVisibleAgentText = reply,
            ),
        )
    }

    @Test
    fun pendingGenerationBlockKeepsDistinctPendingText() {
        assertEquals(
            true,
            shouldRenderPendingGenerationBlock(
                isSending = true,
                pendingResponseBlocks = listOf(
                    AssistantResponseBlock.Text(id = "pending-text", text = "Still streaming"),
                ),
                pendingToolInvocations = emptyList(),
                pendingStatusText = "",
                lastVisibleAgentText = "Previous reply",
            ),
        )
    }

    @Test
    fun reasoningTimelineKeepsSummaryAndToolsInRecordedOrder() {
        val trace = ReasoningTrace(
            id = "reasoning-1",
            chunks = listOf(
                ReasoningSummaryChunk(
                    id = "summary-1",
                    title = "Planning",
                    detail = "I am checking the input first.",
                    timelineOrder = 1,
                ),
                ReasoningSummaryChunk(
                    id = "summary-2",
                    title = "Reviewing output",
                    detail = "I should inspect the command result.",
                    timelineOrder = 3,
                ),
            ),
            toolInvocations = listOf(
                ChatToolInvocation(
                    id = "tool-1",
                    toolName = "bash",
                    argumentsJson = """{"command":"pwd"}""",
                    timelineOrder = 2,
                ),
            ),
        )

        val items = reasoningTimelineItems(trace)

        assertEquals(
            listOf("summary-1", "tool-1", "summary-2"),
            items.map { item ->
                when (item) {
                    is ReasoningTimelineItem.Summary -> item.chunk.id
                    is ReasoningTimelineItem.Tool -> item.toolInvocation.id
                }
            },
        )
    }
}
