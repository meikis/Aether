package com.zhousl.aether.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AetherSelfManagementToolRoutingTest {
    @Test
    fun scheduledTaskToolIsRoutedByAgentExecutor() {
        val source = File("src/main/java/com/zhousl/aether/data/AetherAgent.kt").readText()
        val routeStart = source.indexOf("private suspend fun executeFunctionCall")
        val routeEnd = source.indexOf("private fun sanitizeToolOutputForConversation")
        val routeSource = source.substring(routeStart, routeEnd)

        assertTrue(routeSource.contains("\"aether_scheduled_task_manage\""))
        assertTrue(routeSource.contains("selfManagementTool.execute"))
    }
}
