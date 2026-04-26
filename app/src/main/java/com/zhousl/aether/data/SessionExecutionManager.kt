package com.zhousl.aether.data

import android.app.Application
import android.os.SystemClock
import com.zhousl.aether.AetherForegroundService
import com.zhousl.aether.AetherNotificationController
import com.zhousl.aether.AppForegroundTracker
import com.zhousl.aether.termux.TermuxBashTool
import com.zhousl.aether.ui.AttachmentKind
import com.zhousl.aether.ui.AssistantResponseBlock
import com.zhousl.aether.ui.ChatAttachment
import com.zhousl.aether.ui.ChatMessage
import com.zhousl.aether.ui.ChatSession
import com.zhousl.aether.ui.ChatToolInvocation
import com.zhousl.aether.ui.MessageAuthor
import com.zhousl.aether.ui.syncActiveBranches
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

enum class SessionFollowUpMode {
    Queue,
    Steer,
}

enum class SessionTurnOutcome {
    Success,
    ValidationError,
    Failure,
    Neutral,
}

data class PendingSessionInput(
    val id: String,
    val mode: SessionFollowUpMode,
    val preview: String,
    val attachmentCount: Int,
)

data class SessionExecutionState(
    val sessionId: String,
    val isRunning: Boolean = false,
    val pendingToolInvocations: List<ChatToolInvocation> = emptyList(),
    val pendingResponseBlocks: List<AssistantResponseBlock> = emptyList(),
    val pendingAssistantText: String = "",
    val pendingStatusText: String = "",
    val pendingInputs: List<PendingSessionInput> = emptyList(),
    val activeTurnStartedAtMillis: Long? = null,
)

data class SessionTurnRequest(
    val sessionId: String,
    val settings: AppSettings,
    val requestMessages: List<ChatMessage>,
    val selectedSkillIds: List<String>,
    val activeSkills: List<ActiveSkillContext>,
    val activeMcpServerIds: List<String>,
    val agentModeEnabled: Boolean,
)

data class SessionTurnEvent(
    val sessionId: String,
    val outcome: SessionTurnOutcome,
)

class SessionExecutionManager(
    private val application: Application,
    private val scope: CoroutineScope,
    settingsRepository: SettingsRepository,
    extensionsRepository: AgentExtensionsRepository,
    private val chatStateStore: ChatStateStore,
    private val bashTool: TermuxBashTool,
    private val workspaceFileBridge: WorkspaceFileBridge,
    private val agentModeController: AgentModeController,
    private val skillManager: AgentSkillManager,
    private val webToolsClient: WebToolsClient,
    private val notificationController: AetherNotificationController,
    private val appForegroundTracker: AppForegroundTracker,
) {
    private val currentSettings = MutableStateFlow(AppSettings())
    private val currentExtensionsState = MutableStateFlow(AgentExtensionsState())
    private val _executionStates = MutableStateFlow<Map<String, SessionExecutionState>>(emptyMap())
    private val _turnEvents = MutableSharedFlow<SessionTurnEvent>(extraBufferCapacity = 8)
    private val executionHandles = ConcurrentHashMap<String, SessionExecutionHandle>()

    val executionStates: StateFlow<Map<String, SessionExecutionState>> = _executionStates.asStateFlow()
    val turnEvents = _turnEvents.asSharedFlow()

    init {
        scope.launch {
            settingsRepository.settings.collect { settings ->
                currentSettings.value = settings
                if (
                    settings.keepTasksRunningInBackground &&
                    _executionStates.value.values.any { it.isRunning }
                ) {
                    AetherForegroundService.ensureRunning(application)
                }
            }
        }
        scope.launch {
            extensionsRepository.extensionState.collect { currentExtensionsState.value = it }
        }
    }

    fun isSessionRunning(sessionId: String): Boolean =
        _executionStates.value[sessionId]?.isRunning == true

    fun startTurn(request: SessionTurnRequest) {
        if (executionHandles.containsKey(request.sessionId)) return

        val validationError = validateRequest(request)
        if (validationError != null) {
            val completion = appendAgentMessage(
                sessionId = request.sessionId,
                blocks = listOf(
                    AssistantResponseBlock.Text(
                        id = "agent-validation-${System.currentTimeMillis()}",
                        text = validationError,
                    )
                ),
                thoughtDurationMillis = null,
                outcome = SessionTurnOutcome.ValidationError,
            )
            _turnEvents.tryEmit(SessionTurnEvent(request.sessionId, completion.outcome))
            return
        }

        val handle = SessionExecutionHandle(sessionId = request.sessionId)
        executionHandles[request.sessionId] = handle
        updateExecutionState(request.sessionId) {
            it.copy(
                sessionId = request.sessionId,
                isRunning = true,
                pendingToolInvocations = emptyList(),
                pendingResponseBlocks = emptyList(),
                pendingAssistantText = "",
                pendingStatusText = "",
                activeTurnStartedAtMillis = System.currentTimeMillis(),
            )
        }

        handle.job = scope.launch {
            runSession(
                handle = handle,
                initialRequest = request,
            )
        }
    }

    fun submitFollowUp(
        sessionId: String,
        message: ChatMessage,
        mode: SessionFollowUpMode,
    ): Boolean {
        val handle = executionHandles[sessionId] ?: return false
        val pending = PendingEnvelope(
            id = "pending-${System.currentTimeMillis()}-${message.id}",
            mode = mode,
            message = message,
        )
        synchronized(handle.lock) {
            when (mode) {
                SessionFollowUpMode.Queue -> handle.queuedInputs += pending
                SessionFollowUpMode.Steer -> handle.steerInputs += pending
            }
        }
        updateExecutionState(sessionId) { current ->
            current.copy(
                pendingInputs = current.pendingInputs + pending.toUiState()
            )
        }
        return true
    }

    fun pauseSession(sessionId: String) {
        val handle = executionHandles[sessionId] ?: return
        handle.pauseRequested = true
        val snapshot = _executionStates.value[sessionId]
        val runningRunIds = snapshot?.pendingToolInvocations?.let(::extractActiveManagedRunIds).orEmpty()
        handle.job?.cancel(CancellationException("Paused by user."))
        if (runningRunIds.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                runningRunIds.forEach { runId ->
                    runCatching { bashTool.killExecutionByRunId(runId) }
                }
            }
        }
    }

    private suspend fun runSession(
        handle: SessionExecutionHandle,
        initialRequest: SessionTurnRequest,
    ) {
        var nextRequest: SessionTurnRequest? = initialRequest
        var lastCompletion: CompletionSummary? = null

        try {
            while (nextRequest != null && !handle.pauseRequested) {
                lastCompletion = executeTurn(
                    handle = handle,
                    request = nextRequest,
                )
                promoteRemainingSteersToQueue(handle)
                if (handle.pauseRequested) break

                val nextQueued = pollNextQueuedInput(handle) ?: break
                nextRequest = buildQueuedTurnRequest(
                    sessionId = handle.sessionId,
                    queuedInput = nextQueued.message,
                )
            }
        } finally {
            clearPendingInputs(handle)
            executionHandles.remove(handle.sessionId, handle)
            updateExecutionState(handle.sessionId) {
                it.copy(
                    sessionId = handle.sessionId,
                    isRunning = false,
                    pendingToolInvocations = emptyList(),
                    pendingResponseBlocks = emptyList(),
                    pendingAssistantText = "",
                    pendingStatusText = "",
                    pendingInputs = emptyList(),
                    activeTurnStartedAtMillis = null,
                )
            }

            if (
                !handle.pauseRequested &&
                lastCompletion != null &&
                currentSettings.value.notifyOnTaskCompletion &&
                !appForegroundTracker.isForeground.value
            ) {
                notificationController.notifyCompletion(
                    sessionId = handle.sessionId,
                    sessionTitle = lastCompletion.sessionTitle,
                    summary = lastCompletion.summary,
                    failed = lastCompletion.outcome == SessionTurnOutcome.Failure,
                )
            }
        }
    }

    private suspend fun executeTurn(
        handle: SessionExecutionHandle,
        request: SessionTurnRequest,
    ): CompletionSummary {
        val turnStartedAtMillis = System.currentTimeMillis()
        val mcpClientManager = McpClientManager(bashTool = bashTool)
        val agent = AetherAgent(
            client = OpenAiCompatibleClient(),
            bashTool = bashTool,
            workspaceFileBridge = workspaceFileBridge,
            agentModeController = agentModeController,
            skillManager = skillManager,
            mcpClientManager = mcpClientManager,
            webToolsClient = webToolsClient,
        )

        updateExecutionState(handle.sessionId) {
            it.copy(
                sessionId = handle.sessionId,
                isRunning = true,
                pendingToolInvocations = emptyList(),
                pendingResponseBlocks = emptyList(),
                pendingAssistantText = "",
                pendingStatusText = "",
                activeTurnStartedAtMillis = turnStartedAtMillis,
            )
        }

        return try {
            var resolvedActiveSkills = resolveSelectedActiveSkills(
                selectedSkillIds = request.selectedSkillIds,
                existingActiveSkills = request.activeSkills,
            )
            var resolvedSelectedSkillIds = resolvedActiveSkills.map { it.skillId }
            val resolvedAvailableSkills = currentExtensionsState.value.installedSkills
                .filter { it.isEnabled }
                .sortedBy { it.name.lowercase() }
            val resolvedMcpServers = resolveSelectedMcpServers(request.activeMcpServerIds)
            val resolvedMcpServerIds = resolvedMcpServers.map { it.id }
            updateSessionSelections(
                sessionId = handle.sessionId,
                selectedSkillIds = resolvedSelectedSkillIds,
                activeSkills = resolvedActiveSkills,
                activeMcpServerIds = resolvedMcpServerIds,
            )

            val workspaceDirectory = workspaceFileBridge.workspaceDirectory(handle.sessionId)
            mcpClientManager.syncServers(
                servers = resolvedMcpServers,
                workspaceDirectory = workspaceDirectory,
            )

            val result = agent.runTurn(
                settings = request.settings,
                messages = buildRequestMessages(request.requestMessages),
                workspaceDirectory = workspaceDirectory,
                availableSkills = resolvedAvailableSkills,
                activeSkills = resolvedActiveSkills,
                mcpToolBindings = mcpClientManager.toolBindings(),
                agentModeEnabled = request.agentModeEnabled,
                onToolEvent = { event ->
                    val now = SystemClock.uptimeMillis()
                    val invocation = ChatToolInvocation(
                        id = event.id,
                        toolName = event.name,
                        argumentsJson = event.argumentsJson,
                        outputJson = event.outputJson.orEmpty(),
                        isRunning = event.outputJson == null,
                        startedAtUptimeMillis = now,
                        completedAtUptimeMillis = if (event.outputJson == null) null else now,
                    )
                    updateExecutionState(handle.sessionId) { current ->
                        val pendingToolInvocations = upsertToolInvocation(
                            current.pendingToolInvocations,
                            invocation,
                        )
                        val pendingResponseBlocks = upsertAssistantResponseToolInvocation(
                            blocks = current.pendingResponseBlocks,
                            toolInvocation = invocation,
                        ) { handle.nextPendingBlockId("pending-tools") }
                        current.copy(
                            pendingToolInvocations = pendingToolInvocations,
                            pendingResponseBlocks = pendingResponseBlocks,
                        )
                    }
                },
                onAssistantTextDelta = { delta ->
                    if (delta.isEmpty()) return@runTurn
                    updateExecutionState(handle.sessionId) { current ->
                        val pendingResponseBlocks = appendAssistantResponseText(
                            blocks = current.pendingResponseBlocks,
                            delta = delta,
                        ) { handle.nextPendingBlockId("pending-text") }
                        current.copy(
                            pendingStatusText = "",
                            pendingAssistantText = pendingTrailingAssistantText(pendingResponseBlocks),
                            pendingResponseBlocks = pendingResponseBlocks,
                        )
                    }
                },
                onAssistantTextReset = {
                    updateExecutionState(handle.sessionId) { current ->
                        if (current.pendingAssistantText.isEmpty()) {
                            current
                        } else {
                            current.copy(pendingAssistantText = "")
                        }
                    }
                },
                onStreamingStatus = { status ->
                    updateExecutionState(handle.sessionId) { current ->
                        current.copy(pendingStatusText = status.orEmpty())
                    }
                },
                onSkillActivated = { activeSkill ->
                    resolvedSelectedSkillIds = (resolvedSelectedSkillIds + activeSkill.skillId).distinct()
                    resolvedActiveSkills = upsertActiveSkillContext(resolvedActiveSkills, activeSkill)
                    updateSessionSelections(
                        sessionId = handle.sessionId,
                        selectedSkillIds = resolvedSelectedSkillIds,
                        activeSkills = resolvedActiveSkills,
                        activeMcpServerIds = resolvedMcpServerIds,
                    )
                },
                pollInjectedUserMessages = {
                    val drained = drainSteerInputs(handle)
                    if (drained.isNotEmpty()) {
                        appendConsumedUserMessages(
                            sessionId = handle.sessionId,
                            messages = drained.map { it.message },
                        )
                    }
                    drained.map { buildSteerRequestMessage(it.message) }
                },
            )

            val thoughtDurationMillis = (System.currentTimeMillis() - turnStartedAtMillis).coerceAtLeast(0L)
            val completion = result.fold(
                onSuccess = { reply ->
                    appendAgentMessage(
                        sessionId = handle.sessionId,
                        blocks = ensureAssistantResponseFinalText(
                            blocks = currentAssistantResponseBlocks(handle.sessionId),
                            finalText = reply,
                        ) { handle.nextPendingBlockId("agent-text") },
                        thoughtDurationMillis = thoughtDurationMillis,
                        outcome = SessionTurnOutcome.Success,
                    )
                },
                onFailure = { throwable ->
                    appendAgentMessage(
                        sessionId = handle.sessionId,
                        blocks = appendAssistantResponseText(
                            blocks = currentAssistantResponseBlocks(handle.sessionId),
                            delta = buildString {
                                if (currentAssistantResponseBlocks(handle.sessionId).lastOrNull() is AssistantResponseBlock.Text) {
                                    append("\n\n")
                                }
                                append("Request failed: ${throwable.message ?: "Unknown error"}")
                            },
                        ) { handle.nextPendingBlockId("agent-text") },
                        thoughtDurationMillis = thoughtDurationMillis,
                        outcome = SessionTurnOutcome.Failure,
                    )
                },
            )
            _turnEvents.tryEmit(SessionTurnEvent(handle.sessionId, completion.outcome))
            completion
        } catch (_: CancellationException) {
            val snapshot = _executionStates.value[handle.sessionId] ?: SessionExecutionState(sessionId = handle.sessionId)
            val finalizedToolInvocations = finalizeInterruptedToolInvocations(snapshot.pendingToolInvocations)
            val finalizedResponseBlocks = finalizeInterruptedAssistantResponseBlocks(snapshot.pendingResponseBlocks)
            val thoughtDurationMillis = (System.currentTimeMillis() - turnStartedAtMillis).coerceAtLeast(0L)
            clearPendingInputs(handle)
            val completion = if (
                finalizedResponseBlocks.isEmpty() &&
                snapshot.pendingAssistantText.isBlank() &&
                finalizedToolInvocations.isEmpty()
            ) {
                CompletionSummary(
                    sessionTitle = resolveSessionTitle(handle.sessionId),
                    summary = "",
                    outcome = SessionTurnOutcome.Neutral,
                )
            } else {
                appendAgentMessage(
                    sessionId = handle.sessionId,
                    blocks = finalizedResponseBlocks.ifEmpty {
                        buildList {
                            if (snapshot.pendingAssistantText.isNotBlank()) {
                                add(
                                    AssistantResponseBlock.Text(
                                        id = handle.nextPendingBlockId("agent-text"),
                                        text = snapshot.pendingAssistantText,
                                    )
                                )
                            }
                            if (finalizedToolInvocations.isNotEmpty()) {
                                add(
                                    AssistantResponseBlock.ToolGroup(
                                        id = handle.nextPendingBlockId("agent-tools"),
                                        toolInvocations = finalizedToolInvocations,
                                    )
                                )
                            }
                        }
                    },
                    thoughtDurationMillis = thoughtDurationMillis,
                    outcome = SessionTurnOutcome.Neutral,
                )
            }
            _turnEvents.tryEmit(SessionTurnEvent(handle.sessionId, completion.outcome))
            completion
        } finally {
            mcpClientManager.snapshots().forEach { snapshot ->
                runCatching { mcpClientManager.disconnect(snapshot.config.id) }
            }
            updateExecutionState(handle.sessionId) { current ->
                current.copy(
                    pendingToolInvocations = emptyList(),
                    pendingResponseBlocks = emptyList(),
                    pendingAssistantText = "",
                    activeTurnStartedAtMillis = null,
                )
            }
        }
    }

    private fun buildQueuedTurnRequest(
        sessionId: String,
        queuedInput: ChatMessage,
    ): SessionTurnRequest? {
        var requestMessages: List<ChatMessage> = emptyList()
        var selectedSkillIds: List<String> = emptyList()
        var activeSkills: List<ActiveSkillContext> = emptyList()
        var activeMcpServerIds: List<String> = emptyList()
        var agentModeEnabled = false

        chatStateStore.update { persisted ->
            val sessionIndex = persisted.sessions.indexOfFirst { it.id == sessionId }
            if (sessionIndex < 0) return@update persisted

            val updatedSessions = persisted.sessions.toMutableList()
            val session = updatedSessions.removeAt(sessionIndex)
            val updatedSession = session.withDerivedMessages(
                syncActiveBranches(session.messages + queuedInput)
            )
            requestMessages = updatedSession.messages
            selectedSkillIds = updatedSession.selectedSkillIds
            activeSkills = updatedSession.activeSkills
            activeMcpServerIds = updatedSession.activeMcpServerIds
            agentModeEnabled = updatedSession.agentModeEnabled
            updatedSessions.add(0, updatedSession)
            persisted.copy(sessions = updatedSessions)
        }

        if (requestMessages.isEmpty()) return null

        return SessionTurnRequest(
            sessionId = sessionId,
            settings = currentSettings.value,
            requestMessages = requestMessages,
            selectedSkillIds = selectedSkillIds,
            activeSkills = activeSkills,
            activeMcpServerIds = activeMcpServerIds,
            agentModeEnabled = agentModeEnabled,
        )
    }

    private fun updateSessionSelections(
        sessionId: String,
        selectedSkillIds: List<String>,
        activeSkills: List<ActiveSkillContext>,
        activeMcpServerIds: List<String>,
    ) {
        chatStateStore.update { persisted ->
            val sessionIndex = persisted.sessions.indexOfFirst { it.id == sessionId }
            if (sessionIndex < 0) return@update persisted
            val updatedSessions = persisted.sessions.toMutableList()
            val session = updatedSessions.removeAt(sessionIndex)
            updatedSessions.add(
                sessionIndex.coerceAtMost(updatedSessions.size),
                session.copy(
                    selectedSkillIds = selectedSkillIds,
                    activeSkills = activeSkills,
                    activeMcpServerIds = activeMcpServerIds,
                ),
            )
            persisted.copy(sessions = updatedSessions)
        }
    }

    private suspend fun resolveSelectedActiveSkills(
        selectedSkillIds: List<String>,
        existingActiveSkills: List<ActiveSkillContext>,
    ): List<ActiveSkillContext> {
        if (selectedSkillIds.isEmpty()) return emptyList()
        val installedSkillsById = currentExtensionsState.value.installedSkills
            .filter { it.isEnabled }
            .associateBy { it.id }
        val existingById = existingActiveSkills.associateBy { it.skillId }
        return buildList {
            selectedSkillIds.distinct().forEach { skillId ->
                val installedSkill = installedSkillsById[skillId] ?: return@forEach
                val activeSkill = skillManager.buildActiveSkillContext(installedSkill)
                    .getOrElse { existingById[skillId] ?: return@forEach }
                add(activeSkill)
            }
        }
    }

    private fun upsertActiveSkillContext(
        activeSkills: List<ActiveSkillContext>,
        activeSkill: ActiveSkillContext,
    ): List<ActiveSkillContext> {
        val existingIndex = activeSkills.indexOfFirst { it.skillId == activeSkill.skillId }
        if (existingIndex < 0) return activeSkills + activeSkill
        return activeSkills.toMutableList().apply {
            set(existingIndex, activeSkill)
        }
    }

    private fun resolveSelectedMcpServers(
        selectedServerIds: List<String>,
    ): List<McpServerConfig> {
        if (selectedServerIds.isEmpty()) return emptyList()
        val serversById = currentExtensionsState.value.mcpServers
            .filter { it.isEnabled }
            .associateBy { it.id }
        return selectedServerIds.distinct().mapNotNull(serversById::get)
    }

    private fun appendAgentMessage(
        sessionId: String,
        blocks: List<AssistantResponseBlock>,
        thoughtDurationMillis: Long?,
        outcome: SessionTurnOutcome,
    ): CompletionSummary {
        var sessionTitle = resolveSessionTitle(sessionId)
        var replySummary = blocks.lastOrNull()
            ?.let(::assistantResponseBlockSummaryText)
            .orEmpty()

        val normalizedBlocks = normalizeAssistantResponseBlocks(blocks)
        val messageTimestamp = System.currentTimeMillis()
        val responseGroupId = "agent-group-$messageTimestamp"
        val appendedMessages = normalizedBlocks.mapIndexedNotNull { index, block ->
            when (block) {
                is AssistantResponseBlock.Text -> {
                    if (block.text.isBlank()) {
                        null
                    } else {
                        ChatMessage(
                            id = "agent-${messageTimestamp + index}",
                            author = MessageAuthor.Agent,
                            text = block.text,
                            createdAtMillis = messageTimestamp + index,
                            responseGroupId = responseGroupId,
                        )
                    }
                }

                is AssistantResponseBlock.ToolGroup -> {
                    if (block.toolInvocations.isEmpty()) {
                        null
                    } else {
                        ChatMessage(
                            id = "agent-${messageTimestamp + index}",
                            author = MessageAuthor.Agent,
                            text = "",
                            createdAtMillis = messageTimestamp + index,
                            toolInvocations = block.toolInvocations,
                            responseGroupId = responseGroupId,
                        )
                    }
                }
            }
        }.let { messages ->
            if (messages.isEmpty()) {
                emptyList()
            } else {
                messages.toMutableList().apply {
                    val lastIndex = lastIndex
                    set(lastIndex, get(lastIndex).copy(thoughtDurationMillis = thoughtDurationMillis))
                }
            }
        }

        chatStateStore.update { persisted ->
            val sessionIndex = persisted.sessions.indexOfFirst { it.id == sessionId }
            if (sessionIndex < 0) return@update persisted

            val updatedSessions = persisted.sessions.toMutableList()
            val session = updatedSessions.removeAt(sessionIndex)
            val updatedSession = session.withDerivedMessages(
                session.messages + appendedMessages
            )
            sessionTitle = updatedSession.title
            replySummary = updatedSession.preview
            updatedSessions.add(0, updatedSession)
            persisted.copy(sessions = updatedSessions)
        }

        return CompletionSummary(
            sessionTitle = sessionTitle,
            summary = replySummary,
            outcome = outcome,
        )
    }

    private fun currentAssistantResponseBlocks(sessionId: String): List<AssistantResponseBlock> =
        _executionStates.value[sessionId]?.pendingResponseBlocks.orEmpty()

    private fun appendConsumedUserMessages(
        sessionId: String,
        messages: List<ChatMessage>,
    ) {
        if (messages.isEmpty()) return
        chatStateStore.update { persisted ->
            val sessionIndex = persisted.sessions.indexOfFirst { it.id == sessionId }
            if (sessionIndex < 0) return@update persisted

            val updatedSessions = persisted.sessions.toMutableList()
            val session = updatedSessions.removeAt(sessionIndex)
            updatedSessions.add(
                0,
                session.withDerivedMessages(syncActiveBranches(session.messages + messages)),
            )
            persisted.copy(sessions = updatedSessions)
        }
    }

    private fun drainSteerInputs(
        handle: SessionExecutionHandle,
    ): List<PendingEnvelope> {
        val drained = synchronized(handle.lock) {
            buildList {
                while (handle.steerInputs.isNotEmpty()) {
                    add(handle.steerInputs.removeFirst())
                }
            }
        }
        if (drained.isEmpty()) return emptyList()

        val drainedIds = drained.map { it.id }.toSet()
        updateExecutionState(handle.sessionId) { current ->
            current.copy(
                pendingInputs = current.pendingInputs.filterNot { drainedIds.contains(it.id) }
            )
        }
        return drained
    }

    private fun promoteRemainingSteersToQueue(
        handle: SessionExecutionHandle,
    ) {
        val movedIds = synchronized(handle.lock) {
            if (handle.steerInputs.isEmpty()) {
                emptyList()
            } else {
                buildList {
                    while (handle.steerInputs.isNotEmpty()) {
                        val entry = handle.steerInputs.removeFirst()
                        handle.queuedInputs.addFirst(entry.copy(mode = SessionFollowUpMode.Queue))
                        add(entry.id)
                    }
                }
            }
        }
        if (movedIds.isEmpty()) return

        updateExecutionState(handle.sessionId) { current ->
            current.copy(
                pendingInputs = current.pendingInputs.map { pending ->
                    if (movedIds.contains(pending.id)) {
                        pending.copy(mode = SessionFollowUpMode.Queue)
                    } else {
                        pending
                    }
                }
            )
        }
    }

    private fun pollNextQueuedInput(
        handle: SessionExecutionHandle,
    ): PendingEnvelope? {
        val next = synchronized(handle.lock) {
            if (handle.queuedInputs.isEmpty()) {
                null
            } else {
                handle.queuedInputs.removeFirst()
            }
        } ?: return null

        updateExecutionState(handle.sessionId) { current ->
            current.copy(
                pendingInputs = current.pendingInputs.filterNot { it.id == next.id }
            )
        }
        return next
    }

    private fun clearPendingInputs(
        handle: SessionExecutionHandle,
    ) {
        synchronized(handle.lock) {
            handle.queuedInputs.clear()
            handle.steerInputs.clear()
        }
        updateExecutionState(handle.sessionId) { current ->
            current.copy(pendingInputs = emptyList())
        }
    }

    private fun updateExecutionState(
        sessionId: String,
        transform: (SessionExecutionState) -> SessionExecutionState,
    ) {
        _executionStates.value = _executionStates.value.toMutableMap().apply {
            val current = get(sessionId) ?: SessionExecutionState(sessionId = sessionId)
            put(sessionId, transform(current))
        }
        if (
            currentSettings.value.keepTasksRunningInBackground &&
            _executionStates.value.values.any { it.isRunning }
        ) {
            AetherForegroundService.ensureRunning(application)
        }
    }

    private fun validateSettings(settings: AppSettings): String? = when {
        settings.provider == LlmProvider.VertexExpress && settings.apiKey.isBlank() ->
            "API Key is required before sending with Vertex AI (Express Mode)."

        settings.baseUrl.isBlank() || settings.modelId.isBlank() ->
            "Base URL and Model ID are required before sending."

        else -> null
    }

    private fun validateRequest(request: SessionTurnRequest): String? = when {
        request.agentModeEnabled && !request.settings.agentModeAuthorizationEnabled ->
                "Agent Mode is selected, but authorization is disabled. Enable it in Settings > Agent Mode first."

        else -> validateSettings(request.settings)
    }

    private fun resolveSessionTitle(sessionId: String): String =
        chatStateStore.state.value.sessions.firstOrNull { it.id == sessionId }?.title.orEmpty()

    private fun ChatSession.withDerivedMessages(
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

    private fun buildRequestMessages(messages: List<ChatMessage>): List<LlmMessage> =
        messages.map(::buildRequestMessage)

    private fun buildRequestMessage(message: ChatMessage): LlmMessage {
        val parts = mutableListOf<LlmContentPart>()
        if (message.text.isNotBlank()) {
            parts += LlmTextPart(message.text)
        }
        message.attachments.forEach { attachment ->
            parts += buildWorkspaceAttachmentPart(attachment)
        }
        if (parts.isEmpty()) {
            parts += LlmTextPart("[Empty message]")
        }
        return LlmMessage(
            role = if (message.author == MessageAuthor.User) "user" else "assistant",
            contentParts = parts,
        )
    }

    private fun buildSteerRequestMessage(message: ChatMessage): LlmMessage {
        val steerText = buildString {
            append(
                "The user sent this while you were already working. Treat it as supplemental context for the current task. " +
                    "Continue the ongoing work, do not restart just to acknowledge it, and only change course if the new note requires it."
            )
            if (message.text.isNotBlank()) {
                append("\n\nSupplemental user note:\n")
                append(message.text)
            } else if (message.attachments.isNotEmpty()) {
                append("\n\nThe user also attached additional files for the current task.")
            }
        }
        return buildRequestMessage(message.copy(text = steerText))
    }

    private fun buildWorkspaceAttachmentPart(
        attachment: ChatAttachment,
    ): LlmTextPart {
        if (attachment.workspacePath.isBlank()) {
            return LlmTextPart(
                "Attached file '${attachment.name}' is missing a workspace path. Ask the user to re-upload it if you need to inspect the file."
            )
        }

        val accessHint = if (attachment.kind == AttachmentKind.Image) {
            "This image was copied into the workspace but was not passed to model vision automatically. Use analyze_image on this path if you need to inspect it."
        } else {
            "Inspect this file through read, grep, find, ls, or bash inside the workspace instead of assuming its contents."
        }

        return LlmTextPart(
            buildString {
                append("Workspace attachment:\n")
                append("Name: ${attachment.name}\n")
                append("Type: ${attachment.mimeType.ifBlank { "unknown" }}\n")
                attachment.sizeBytes?.let { append("Size: ${formatBytes(it)}\n") }
                append("Path: ${attachment.workspacePath}\n")
                append(accessHint)
            }
        )
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024f * 1024f))
        bytes >= 1024 -> String.format("%.1f KB", bytes / 1024f)
        else -> "$bytes B"
    }

    private fun upsertToolInvocation(
        invocations: List<ChatToolInvocation>,
        toolInvocation: ChatToolInvocation,
    ): List<ChatToolInvocation> {
        val now = SystemClock.uptimeMillis()
        val existingIndex = invocations.indexOfFirst { it.id == toolInvocation.id }
        val normalized = if (existingIndex < 0) {
            toolInvocation.copy(
                startedAtUptimeMillis = toolInvocation.startedAtUptimeMillis.takeIf { it > 0L } ?: now,
                completedAtUptimeMillis = if (toolInvocation.isRunning) {
                    null
                } else {
                    toolInvocation.completedAtUptimeMillis ?: now
                },
            )
        } else {
            val existing = invocations[existingIndex]
            toolInvocation.copy(
                startedAtUptimeMillis = existing.startedAtUptimeMillis
                    .takeIf { it > 0L }
                    ?: toolInvocation.startedAtUptimeMillis.takeIf { it > 0L }
                    ?: now,
                completedAtUptimeMillis = if (toolInvocation.isRunning) {
                    null
                } else {
                    toolInvocation.completedAtUptimeMillis
                        ?: existing.completedAtUptimeMillis
                        ?: now
                },
            )
        }
        return if (existingIndex < 0) {
            invocations + normalized
        } else {
            invocations.toMutableList().apply { set(existingIndex, normalized) }
        }
    }

    private fun appendAssistantResponseText(
        blocks: List<AssistantResponseBlock>,
        delta: String,
        newBlockId: () -> String,
    ): List<AssistantResponseBlock> {
        if (delta.isEmpty()) return blocks
        val lastBlock = blocks.lastOrNull()
        return if (lastBlock is AssistantResponseBlock.Text) {
            blocks.toMutableList().apply {
                set(lastIndex, lastBlock.copy(text = lastBlock.text + delta))
            }
        } else {
            blocks + AssistantResponseBlock.Text(
                id = newBlockId(),
                text = delta,
            )
        }
    }

    private fun upsertAssistantResponseToolInvocation(
        blocks: List<AssistantResponseBlock>,
        toolInvocation: ChatToolInvocation,
        newBlockId: () -> String,
    ): List<AssistantResponseBlock> {
        val existingIndex = blocks.indexOfFirst { block ->
            block is AssistantResponseBlock.ToolGroup &&
                block.toolInvocations.any { it.id == toolInvocation.id }
        }
        if (existingIndex >= 0) {
            val toolBlock = blocks[existingIndex] as AssistantResponseBlock.ToolGroup
            return blocks.toMutableList().apply {
                set(
                    existingIndex,
                    toolBlock.copy(
                        toolInvocations = upsertToolInvocation(toolBlock.toolInvocations, toolInvocation),
                    ),
                )
            }
        }

        val lastBlock = blocks.lastOrNull()
        return if (lastBlock is AssistantResponseBlock.ToolGroup) {
            blocks.toMutableList().apply {
                set(
                    lastIndex,
                    lastBlock.copy(
                        toolInvocations = upsertToolInvocation(lastBlock.toolInvocations, toolInvocation),
                    ),
                )
            }
        } else {
            blocks + AssistantResponseBlock.ToolGroup(
                id = newBlockId(),
                toolInvocations = upsertToolInvocation(emptyList(), toolInvocation),
            )
        }
    }

    private fun pendingTrailingAssistantText(
        blocks: List<AssistantResponseBlock>,
    ): String = (blocks.lastOrNull() as? AssistantResponseBlock.Text)?.text.orEmpty()

    private fun ensureAssistantResponseFinalText(
        blocks: List<AssistantResponseBlock>,
        finalText: String,
        newBlockId: () -> String,
    ): List<AssistantResponseBlock> {
        if (finalText.isBlank()) return normalizeAssistantResponseBlocks(blocks)
        val normalized = normalizeAssistantResponseBlocks(blocks)
        val lastTextIndex = normalized.indexOfLast { it is AssistantResponseBlock.Text }
        if (lastTextIndex < 0) {
            return normalized + AssistantResponseBlock.Text(
                id = newBlockId(),
                text = finalText,
            )
        }
        val lastTextBlock = normalized[lastTextIndex] as AssistantResponseBlock.Text
        if (lastTextBlock.text == finalText) return normalized
        return normalized.toMutableList().apply {
            set(lastTextIndex, lastTextBlock.copy(text = finalText))
        }
    }

    private fun normalizeAssistantResponseBlocks(
        blocks: List<AssistantResponseBlock>,
    ): List<AssistantResponseBlock> = buildList {
        blocks.forEach { block ->
            when (block) {
                is AssistantResponseBlock.Text -> {
                    if (block.text.isBlank()) return@forEach
                    val previous = lastOrNull()
                    if (previous is AssistantResponseBlock.Text) {
                        removeAt(lastIndex)
                        add(previous.copy(text = previous.text + block.text))
                    } else {
                        add(block)
                    }
                }

                is AssistantResponseBlock.ToolGroup -> {
                    if (block.toolInvocations.isEmpty()) return@forEach
                    val previous = lastOrNull()
                    if (previous is AssistantResponseBlock.ToolGroup) {
                        removeAt(lastIndex)
                        add(
                            previous.copy(
                                toolInvocations = previous.toolInvocations + block.toolInvocations,
                            ),
                        )
                    } else {
                        add(block)
                    }
                }
            }
        }
    }

    private fun finalizeInterruptedAssistantResponseBlocks(
        blocks: List<AssistantResponseBlock>,
    ): List<AssistantResponseBlock> = normalizeAssistantResponseBlocks(
        blocks.map { block ->
            when (block) {
                is AssistantResponseBlock.Text -> block
                is AssistantResponseBlock.ToolGroup -> block.copy(
                    toolInvocations = finalizeInterruptedToolInvocations(block.toolInvocations),
                )
            }
        }
    )

    private fun assistantResponseBlockSummaryText(
        block: AssistantResponseBlock,
    ): String = when (block) {
        is AssistantResponseBlock.Text -> block.text.trim()
        is AssistantResponseBlock.ToolGroup -> ChatMessage(
            id = block.id,
            author = MessageAuthor.Agent,
            text = "",
            toolInvocations = block.toolInvocations,
        ).summaryText()
    }

    private fun finalizeInterruptedToolInvocations(
        invocations: List<ChatToolInvocation>,
    ): List<ChatToolInvocation> = invocations.map { invocation ->
        if (!isInterruptedToolInvocation(invocation)) {
            invocation
        } else {
            invocation.copy(
                isRunning = false,
                outputJson = buildInterruptedToolOutput(invocation),
                completedAtUptimeMillis = invocation.completedAtUptimeMillis ?: SystemClock.uptimeMillis(),
            )
        }
    }

    private fun isInterruptedToolInvocation(invocation: ChatToolInvocation): Boolean {
        if (invocation.isRunning) return true
        if (invocation.toolName.lowercase() != "bash") return false
        val output = parseJsonObject(invocation.outputJson) ?: return false
        return output.optString("status") == "running" || output.optString("status") == "launching"
    }

    private fun buildInterruptedToolOutput(invocation: ChatToolInvocation): String {
        val output = parseJsonObject(invocation.outputJson) ?: JSONObject()
        output.put("ok", false)
        output.put("status", "cancelled")
        output.put("running", false)
        output.put("completed", true)
        if (!output.has("stdout")) output.put("stdout", "")
        if (!output.has("stderr")) output.put("stderr", "")
        if (!output.has("exit_code")) output.put("exit_code", 143)
        if (!output.has("err")) output.put("err", -1)
        output.put("errmsg", "Stopped by user.")
        return output.toString()
    }

    private fun extractActiveManagedRunIds(
        invocations: List<ChatToolInvocation>,
    ): List<String> = invocations.mapNotNull { invocation ->
        if (invocation.toolName.lowercase() != "bash") return@mapNotNull null
        val output = parseJsonObject(invocation.outputJson) ?: return@mapNotNull null
        val status = output.optString("status")
        if (status != "running" && status != "launching") {
            return@mapNotNull null
        }
        output.optString("run_id").trim().ifBlank { null }
    }.distinct()

    private fun parseJsonObject(rawValue: String): JSONObject? =
        if (rawValue.isBlank()) null else runCatching { JSONObject(rawValue) }.getOrNull()

    private data class PendingEnvelope(
        val id: String,
        val mode: SessionFollowUpMode,
        val message: ChatMessage,
    ) {
        fun toUiState(): PendingSessionInput = PendingSessionInput(
            id = id,
            mode = mode,
            preview = message.summaryText().take(72),
            attachmentCount = message.attachments.size,
        )
    }

    private data class CompletionSummary(
        val sessionTitle: String,
        val summary: String,
        val outcome: SessionTurnOutcome,
    )

    private class SessionExecutionHandle(
        val sessionId: String,
    ) {
        val lock = Any()
        val queuedInputs = ArrayDeque<PendingEnvelope>()
        val steerInputs = ArrayDeque<PendingEnvelope>()
        private var pendingBlockCounter: Long = 0

        @Volatile
        var pauseRequested: Boolean = false

        @Volatile
        var job: Job? = null

        fun nextPendingBlockId(prefix: String): String {
            val nextId = pendingBlockCounter
            pendingBlockCounter += 1
            return "$prefix-$sessionId-$nextId"
        }
    }
}

private fun ChatMessage.summaryText(): String {
    val textSummary = text.trim()
    if (textSummary.isNotBlank()) return textSummary
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
