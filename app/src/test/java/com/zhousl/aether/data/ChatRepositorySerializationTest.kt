package com.zhousl.aether.data

import com.zhousl.aether.ui.AttachmentKind
import com.zhousl.aether.ui.ChatAttachment
import com.zhousl.aether.ui.ChatMessage
import com.zhousl.aether.ui.ChatSession
import com.zhousl.aether.ui.ChatToolInvocation
import com.zhousl.aether.ui.MessageAuthor
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRepositorySerializationTest {
    @Test
    fun serializationDropsInlineImageBytesButKeepsWorkspacePath() {
        val serialized = serializeChatSessions(
            listOf(
                ChatSession(
                    id = "session-1",
                    title = "Image",
                    preview = "Image",
                    messages = listOf(
                        ChatMessage(
                            id = "user-1",
                            author = MessageAuthor.User,
                            text = "see attached",
                            attachments = listOf(
                                ChatAttachment(
                                    id = "attachment-1",
                                    uri = "content://image",
                                    name = "image.png",
                                    mimeType = "image/png",
                                    sizeBytes = 1_024,
                                    kind = AttachmentKind.Image,
                                    workspacePath = "/workspace/image.png",
                                    inlineBase64 = "a".repeat(120_000),
                                )
                            ),
                        )
                    ),
                )
            )
        )

        val attachment = JSONArray(serialized)
            .getJSONObject(0)
            .getJSONArray("messages")
            .getJSONObject(0)
            .getJSONArray("attachments")
            .getJSONObject(0)

        assertFalse(attachment.has("inlineBase64"))
        assertEquals("/workspace/image.png", attachment.getString("workspacePath"))
    }

    @Test
    fun serializationCompactsLargeToolOutputJson() {
        val serialized = serializeChatSessions(
            listOf(
                ChatSession(
                    id = "session-1",
                    title = "Tool",
                    preview = "Tool",
                    messages = listOf(
                        ChatMessage(
                            id = "agent-1",
                            author = MessageAuthor.Agent,
                            text = "",
                            toolInvocations = listOf(
                                ChatToolInvocation(
                                    id = "tool-1",
                                    toolName = "bash",
                                    argumentsJson = JSONObject()
                                        .put("command", "yes")
                                        .toString(),
                                    outputJson = JSONObject()
                                        .put("ok", true)
                                        .put("stdout", "x".repeat(140_000))
                                        .toString(),
                                )
                            ),
                        )
                    ),
                )
            )
        )

        val outputJson = JSONArray(serialized)
            .getJSONObject(0)
            .getJSONArray("messages")
            .getJSONObject(0)
            .getJSONArray("toolInvocations")
            .getJSONObject(0)
            .getString("outputJson")
        val output = JSONObject(outputJson)

        assertTrue(output.getBoolean("aetherPersistedOutputTruncated"))
        assertTrue(output.getString("stdout").length < 40_000)
    }
}
