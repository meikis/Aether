package com.zhousl.aether.ui

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zhousl.aether.aetherRuntime
import com.zhousl.aether.data.ActiveSkillContext
import com.zhousl.aether.data.AgentModeDisplayState
import com.zhousl.aether.data.AgentModeAuthorizationMethod
import com.zhousl.aether.data.AgentExtensionsRepository
import com.zhousl.aether.data.AgentSkillManager
import com.zhousl.aether.data.AppLanguage
import com.zhousl.aether.data.AppSettings
import com.zhousl.aether.data.AppThemeMode
import com.zhousl.aether.data.AetherAgent
import com.zhousl.aether.data.CurrentOnboardingVersion
import com.zhousl.aether.data.InstalledSkill
import com.zhousl.aether.data.LlmApiClient
import com.zhousl.aether.data.LlmContentPart
import com.zhousl.aether.data.LlmMessage
import com.zhousl.aether.data.LlmTextPart
import com.zhousl.aether.data.LlmProvider
import com.zhousl.aether.data.LlmProviderConfig
import com.zhousl.aether.data.McpClientManager
import com.zhousl.aether.data.McpServerConfig
import com.zhousl.aether.data.normalizeLlmInactivityReconnectTimeoutSeconds
import com.zhousl.aether.data.OnboardingStarterPrompt
import com.zhousl.aether.data.OpenAiCompatibleClient
import com.zhousl.aether.data.PendingSessionInput
import com.zhousl.aether.data.SessionExecutionState
import com.zhousl.aether.data.SessionFollowUpMode
import com.zhousl.aether.data.SessionTurnEvent
import com.zhousl.aether.data.SessionTurnOutcome
import com.zhousl.aether.data.SessionTurnRequest
import com.zhousl.aether.data.WebToolsClient
import com.zhousl.aether.data.WorkspaceFileBridge
import com.zhousl.aether.data.parseChatSessions
import com.zhousl.aether.data.parseInstalledSkills
import com.zhousl.aether.data.parseMcpServerConfigs
import com.zhousl.aether.data.parseProviderConfigs
import com.zhousl.aether.data.serializeChatSessions
import com.zhousl.aether.data.serializeInstalledSkills
import com.zhousl.aether.data.serializeMcpServerConfigs
import com.zhousl.aether.data.serializeProviderConfigs
import com.zhousl.aether.data.toJson
import com.zhousl.aether.data.isProviderSetupValid
import com.zhousl.aether.data.isOnboardingComplete
import com.zhousl.aether.data.shouldMarkOnboardingCompleted
import com.zhousl.aether.data.shouldLaunchOnboarding
import com.zhousl.aether.data.shouldRevealFollowUpTourCard
import com.zhousl.aether.termux.TermuxBashTool
import com.zhousl.aether.termux.TermuxSetupState
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val DraftSessionId = "draft"
private const val FollowUpTourAutoOpenDelayMillis = 2_500L
private const val SessionTitleSystemPrompt =
    "Generate a concise chat title for this conversation. Return only the title, in the user's language when possible, with no quotes, no emoji, and at most 6 words."

enum class AppScreen {
    Onboarding,
    Chat,
    Settings,
}

enum class OnboardingStep {
    Landing,
    ProviderSetup,
    TermuxSetup,
    AgentModeAuthorization,
    TavilySetup,
    SkillsOverview,
    McpOverview,
}

private enum class AssistantResponseOutcome {
    Success,
    ValidationError,
    Failure,
    Neutral,
}

enum class MessageAuthor {
    User,
    Agent,
}

enum class AttachmentKind {
    Image,
    File;

    companion object {
        fun fromStored(
            value: String,
            mimeType: String,
        ): AttachmentKind = when {
            value == Image.name -> Image
            value == File.name -> File
            mimeType.startsWith("image/") -> Image
            else -> File
        }
    }
}

enum class AttachmentWorkspaceState {
    Pending,
    Ready,
    Failed,
}

data class ChatAttachment(
    val id: String,
    val uri: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long?,
    val kind: AttachmentKind,
    val workspacePath: String = "",
    val workspaceState: AttachmentWorkspaceState = AttachmentWorkspaceState.Ready,
    val workspaceError: String = "",
)

data class ChatToolInvocation(
    val id: String,
    val toolName: String,
    val argumentsJson: String,
    val outputJson: String = "",
    val isRunning: Boolean = false,
    val startedAtUptimeMillis: Long = 0L,
    val completedAtUptimeMillis: Long? = null,
)

sealed interface AssistantResponseBlock {
    val id: String

    data class Text(
        override val id: String,
        val text: String,
    ) : AssistantResponseBlock

    data class ToolGroup(
        override val id: String,
        val toolInvocations: List<ChatToolInvocation>,
    ) : AssistantResponseBlock
}

data class ChatMessage(
    val id: String,
    val author: MessageAuthor,
    val text: String,
    val createdAtMillis: Long = 0L,
    val attachments: List<ChatAttachment> = emptyList(),
    val toolInvocations: List<ChatToolInvocation> = emptyList(),
    val thoughtDurationMillis: Long? = null,
    val branchGroup: ChatBranchGroup? = null,
    val responseGroupId: String? = null,
)

data class ChatSession(
    val id: String,
    val title: String,
    val preview: String,
    val hasCustomTitle: Boolean = false,
    val messages: List<ChatMessage>,
    val selectedSkillIds: List<String> = emptyList(),
    val activeSkills: List<ActiveSkillContext> = emptyList(),
    val activeMcpServerIds: List<String> = emptyList(),
    val agentModeEnabled: Boolean = false,
)

data class AetherUiState(
    val currentScreen: AppScreen = AppScreen.Chat,
    val isStartupRouteResolved: Boolean = false,
    val isOnboardingReplay: Boolean = false,
    val onboardingStep: OnboardingStep = OnboardingStep.Landing,
    val onboardingReturnScreen: AppScreen = AppScreen.Chat,
    val sessions: List<ChatSession> = emptyList(),
    val currentSessionId: String = DraftSessionId,
    val draftInput: String = "",
    val draftAttachments: List<ChatAttachment> = emptyList(),
    val draftSelectedSkillIds: List<String> = emptyList(),
    val draftSelectedMcpServerIds: List<String> = emptyList(),
    val draftAgentModeEnabled: Boolean = false,
    val draftWorkspaceId: String? = null,
    val editingSessionId: String? = null,
    val editingMessageId: String? = null,
    val settings: AppSettings = AppSettings(),
    val isSending: Boolean = false,
    val pendingResponseSessionId: String? = null,
    val pendingToolInvocations: List<ChatToolInvocation> = emptyList(),
    val pendingResponseBlocks: List<AssistantResponseBlock> = emptyList(),
    val pendingAssistantText: String = "",
    val pendingStatusText: String = "",
    val sessionExecutionStates: Map<String, SessionExecutionState> = emptyMap(),
    val unviewedCompletedSessionIds: Set<String> = emptySet(),
    val termuxSetupState: TermuxSetupState = TermuxSetupState(),
    val installedSkills: List<InstalledSkill> = emptyList(),
    val mcpServers: List<McpServerConfig> = emptyList(),
    val providerConfigs: List<LlmProviderConfig> = emptyList(),
    val isFetchingModels: Boolean = false,
    val showStarterPromptHint: Boolean = false,
    val awaitingFollowUpTour: Boolean = false,
    val showFollowUpTourCard: Boolean = false,
    val agentModeDisplayState: AgentModeDisplayState = AgentModeDisplayState(),
)

class AetherViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val runtime = application.aetherRuntime
    private val settingsRepository = runtime.settingsRepository
    private val chatStateStore = runtime.chatStateStore
    private val extensionsRepository = runtime.extensionsRepository
    private val sessionExecutionManager = runtime.sessionExecutionManager
    private val client = OpenAiCompatibleClient()
    private val bashTool = runtime.bashTool
    private val workspaceFileBridge = runtime.workspaceFileBridge
    private val agentModeController = runtime.agentModeController
    private val skillManager = runtime.skillManager
    private val mcpClientManager = McpClientManager(bashTool = bashTool)
    private val webToolsClient = runtime.webToolsClient
    private val agent = AetherAgent(
        client = client,
        bashTool = bashTool,
        workspaceFileBridge = workspaceFileBridge,
        agentModeController = agentModeController,
        skillManager = skillManager,
        mcpClientManager = mcpClientManager,
        webToolsClient = webToolsClient,
    )
    private var activeGenerationJob: Job? = null
    private var activeGenerationRequestId: Long? = null
    private var activeGenerationStartedAtMillis: Long? = null
    private val _uiState = MutableStateFlow(AetherUiState())
    private val _transientMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    val uiState: StateFlow<AetherUiState> = _uiState.asStateFlow()
    val transientMessages = _transientMessages.asSharedFlow()

    init {
        refreshTermuxSetup()

        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { current ->
                    if (!current.isStartupRouteResolved) {
                        current.copy(
                            settings = settings,
                            currentScreen = if (settings.shouldLaunchOnboarding()) {
                                AppScreen.Onboarding
                            } else {
                                AppScreen.Chat
                            },
                            isStartupRouteResolved = true,
                            isOnboardingReplay = false,
                            onboardingStep = OnboardingStep.Landing,
                            onboardingReturnScreen = AppScreen.Chat,
                        )
                    } else {
                        current.copy(settings = settings)
                    }
                }
            }
        }

        viewModelScope.launch {
            chatStateStore.state.collect { persisted ->
                _uiState.update { current ->
                    val currentExecution = current.sessionExecutionStates[persisted.currentSessionId.ifBlank { DraftSessionId }]
                    current.copy(
                        sessions = persisted.sessions,
                        currentSessionId = persisted.currentSessionId.ifBlank { DraftSessionId },
                        isSending = currentExecution?.isRunning == true,
                        pendingResponseSessionId = currentExecution?.sessionId,
                        pendingToolInvocations = currentExecution?.pendingToolInvocations.orEmpty(),
                        pendingResponseBlocks = currentExecution?.pendingResponseBlocks.orEmpty(),
                        pendingAssistantText = currentExecution?.pendingAssistantText.orEmpty(),
                        pendingStatusText = currentExecution?.pendingStatusText.orEmpty(),
                    )
                }
            }
        }

        viewModelScope.launch {
            sessionExecutionManager.executionStates.collect { executionStates ->
                _uiState.update { current ->
                    val currentExecution = executionStates[current.currentSessionId]
                    current.copy(
                        sessionExecutionStates = executionStates,
                        isSending = currentExecution?.isRunning == true,
                        pendingResponseSessionId = currentExecution?.sessionId,
                        pendingToolInvocations = currentExecution?.pendingToolInvocations.orEmpty(),
                        pendingResponseBlocks = currentExecution?.pendingResponseBlocks.orEmpty(),
                        pendingAssistantText = currentExecution?.pendingAssistantText.orEmpty(),
                        pendingStatusText = currentExecution?.pendingStatusText.orEmpty(),
                    )
                }
            }
        }

        viewModelScope.launch {
            sessionExecutionManager.turnEvents.collect { event ->
                handleTurnEvent(event)
            }
        }

        viewModelScope.launch {
            extensionsRepository.extensionState.collect { extensionState ->
                var didPruneSelections = false
                _uiState.update { current ->
                    val enabledSkillIds = extensionState.installedSkills
                        .filter { it.isEnabled }
                        .map { it.id }
                        .toSet()
                    val enabledMcpServerIds = extensionState.mcpServers
                        .filter { it.isEnabled }
                        .map { it.id }
                        .toSet()
                    val updatedSessions = current.sessions.map { session ->
                        val updatedSelectedSkillIds = session.selectedSkillIds.filter(enabledSkillIds::contains)
                        val updatedActiveSkills = session.activeSkills.filter { activeSkill ->
                            updatedSelectedSkillIds.contains(activeSkill.skillId)
                        }
                        val updatedActiveMcpServerIds = session.activeMcpServerIds.filter(enabledMcpServerIds::contains)
                        if (
                            updatedSelectedSkillIds != session.selectedSkillIds ||
                            updatedActiveSkills != session.activeSkills ||
                            updatedActiveMcpServerIds != session.activeMcpServerIds
                        ) {
                            didPruneSelections = true
                            session.copy(
                                selectedSkillIds = updatedSelectedSkillIds,
                                activeSkills = updatedActiveSkills,
                                activeMcpServerIds = updatedActiveMcpServerIds,
                            )
                        } else {
                            session
                        }
                    }
                    val updatedDraftSelectedSkillIds = current.draftSelectedSkillIds.filter(enabledSkillIds::contains)
                    val updatedDraftSelectedMcpServerIds = current.draftSelectedMcpServerIds.filter(enabledMcpServerIds::contains)
                    if (
                        updatedDraftSelectedSkillIds != current.draftSelectedSkillIds ||
                        updatedDraftSelectedMcpServerIds != current.draftSelectedMcpServerIds
                    ) {
                        didPruneSelections = true
                    }
                    current.copy(
                        sessions = updatedSessions,
                        draftSelectedSkillIds = updatedDraftSelectedSkillIds,
                        draftSelectedMcpServerIds = updatedDraftSelectedMcpServerIds,
                        installedSkills = extensionState.installedSkills,
                        mcpServers = extensionState.mcpServers,
                    )
                }
                if (didPruneSelections) {
                    persistChats()
                }
            }
        }

        viewModelScope.launch {
            settingsRepository.providerConfigs.collect { configs ->
                _uiState.update { current -> current.copy(providerConfigs = configs) }
            }
        }
        viewModelScope.launch {
            agentModeController.displayState.collect { displayState ->
                _uiState.update { current -> current.copy(agentModeDisplayState = displayState) }
            }
        }
    }

    fun refreshTermuxSetup() {
        viewModelScope.launch {
            val setupState = withContext(Dispatchers.IO) { bashTool.inspectSetup() }
            _uiState.update { current -> current.copy(termuxSetupState = setupState) }
        }
    }

    fun stopAgentModeDisplay() {
        agentModeController.stopDisplay()
    }

    fun refreshAgentModeDisplays() {
        viewModelScope.launch {
            agentModeController.refreshDisplays(_uiState.value.settings)
        }
    }

    fun updateDraftInput(value: String) {
        _uiState.update { current ->
            current.copy(
                draftInput = value,
                showStarterPromptHint = if (
                    current.showStarterPromptHint && value != current.draftInput
                ) {
                    false
                } else {
                    current.showStarterPromptHint
                },
            )
        }
    }

    fun skipOnboarding() {
        viewModelScope.launch {
            settingsRepository.updateOnboardingSeenVersion(CurrentOnboardingVersion)
            _uiState.update { current ->
                current.copy(
                    currentScreen = AppScreen.Chat,
                    isStartupRouteResolved = true,
                    isOnboardingReplay = false,
                    onboardingStep = OnboardingStep.Landing,
                    onboardingReturnScreen = AppScreen.Chat,
                )
            }
        }
    }

    fun openOnboardingFromSettings() {
        _uiState.update { current ->
            current.copy(
                currentScreen = AppScreen.Onboarding,
                isOnboardingReplay = true,
                onboardingStep = OnboardingStep.Landing,
                onboardingReturnScreen = AppScreen.Settings,
            )
        }
    }

    fun openFollowUpOnboardingFromSettings() {
        _uiState.update { current ->
            current.copy(
                currentScreen = AppScreen.Onboarding,
                isOnboardingReplay = true,
                onboardingStep = OnboardingStep.TermuxSetup,
                onboardingReturnScreen = AppScreen.Settings,
                awaitingFollowUpTour = false,
                showFollowUpTourCard = false,
            )
        }
    }

    fun resumeOnboarding() {
        _uiState.update { current ->
            current.copy(
                currentScreen = AppScreen.Onboarding,
                isOnboardingReplay = true,
                onboardingStep = OnboardingStep.ProviderSetup,
                onboardingReturnScreen = AppScreen.Chat,
            )
        }
    }

    fun closeOnboarding() {
        _uiState.update { current ->
            current.copy(
                currentScreen = current.onboardingReturnScreen,
                isOnboardingReplay = false,
                onboardingStep = OnboardingStep.Landing,
                onboardingReturnScreen = AppScreen.Chat,
            )
        }
    }

    fun completeOnboardingProviderSetup(config: LlmProviderConfig) {
        viewModelScope.launch {
            val activeConfig = config.copy(isActive = true)
            settingsRepository.upsertProviderConfig(activeConfig)
            settingsRepository.setActiveProvider(activeConfig.id)
            settingsRepository.updateOnboardingSeenVersion(CurrentOnboardingVersion)
            _uiState.update { current ->
                current.copy(
                    currentScreen = AppScreen.Chat,
                    isStartupRouteResolved = true,
                    isOnboardingReplay = false,
                    onboardingStep = OnboardingStep.Landing,
                    onboardingReturnScreen = AppScreen.Chat,
                    currentSessionId = DraftSessionId,
                    draftInput = OnboardingStarterPrompt,
                    draftAttachments = emptyList(),
                    draftSelectedSkillIds = emptyList(),
                    draftSelectedMcpServerIds = emptyList(),
                    draftAgentModeEnabled = false,
                    draftWorkspaceId = null,
                    editingSessionId = null,
                    editingMessageId = null,
                    showStarterPromptHint = true,
                    awaitingFollowUpTour = true,
                    showFollowUpTourCard = false,
                )
            }
            persistChats()
        }
    }

    fun dismissStarterPromptHint() {
        _uiState.update { current -> current.copy(showStarterPromptHint = false) }
    }

    fun openFollowUpTour() {
        _uiState.update { current ->
            current.copy(
                currentScreen = AppScreen.Onboarding,
                isOnboardingReplay = true,
                onboardingStep = OnboardingStep.TermuxSetup,
                onboardingReturnScreen = AppScreen.Chat,
                awaitingFollowUpTour = false,
                showFollowUpTourCard = false,
            )
        }
    }

    fun saveOnboardingTavilyApiKey(value: String) {
        viewModelScope.launch {
            settingsRepository.updateTavilyApiKey(value.trim())
        }
    }

    fun saveOnboardingAgentModeAuthorization(
        enabled: Boolean,
        method: AgentModeAuthorizationMethod,
    ) {
        viewModelScope.launch {
            settingsRepository.updateSettings(
                _uiState.value.settings.copy(
                    agentModeAuthorizationEnabled = enabled,
                    agentModeAuthorizationMethod = method,
                )
            )
        }
    }

    fun exploreSettingsFromOnboardingTour() {
        _uiState.update { current ->
            current.copy(
                currentScreen = AppScreen.Settings,
                isOnboardingReplay = false,
                onboardingStep = OnboardingStep.Landing,
                onboardingReturnScreen = AppScreen.Chat,
                awaitingFollowUpTour = false,
                showFollowUpTourCard = false,
            )
        }
    }

    fun appendDraftAttachments(uris: List<Uri>) {
        if (uris.isEmpty()) return

        val targetSessionId = ensureDraftWorkspaceId()

        viewModelScope.launch {
            val pendingAttachments = withContext(Dispatchers.IO) {
                uris
                    .mapNotNull { uri -> buildPendingDraftAttachment(uri) }
                    .distinctBy { it.uri }
                    .toList()
            }
            if (pendingAttachments.isEmpty()) return@launch

            var attachmentsToImport = emptyList<ChatAttachment>()
            _uiState.update { current ->
                val newAttachments = pendingAttachments.filterNot { candidate ->
                    current.draftAttachments.any { existing -> existing.uri == candidate.uri }
                }
                attachmentsToImport = newAttachments
                if (newAttachments.isEmpty()) {
                    current
                } else {
                    current.copy(
                        draftAttachments = current.draftAttachments + newAttachments
                    )
                }
            }

            attachmentsToImport.forEach { attachment ->
                launch(Dispatchers.IO) {
                    importDraftAttachmentToWorkspace(
                        attachment = attachment,
                        sessionId = targetSessionId,
                    )
                }
            }
        }
    }

    fun removeDraftAttachment(attachmentId: String) {
        _uiState.update { current ->
            current.copy(
                draftAttachments = current.draftAttachments.filterNot { it.id == attachmentId }
            )
        }
    }

    fun pauseGeneration() {
        val snapshot = _uiState.value
        val sessionId = sequenceOf(
            snapshot.currentSessionId,
            snapshot.pendingResponseSessionId,
        )
            .filterNotNull()
            .firstOrNull(sessionExecutionManager::isSessionRunning)
            ?: return
        sessionExecutionManager.pauseSession(sessionId)
    }

    fun openSettings() {
        _uiState.update { it.copy(currentScreen = AppScreen.Settings) }
    }

    fun closeSettings() {
        _uiState.update { it.copy(currentScreen = AppScreen.Chat) }
    }

    fun startNewChat() {
        _uiState.update {
            it.copy(
                currentScreen = AppScreen.Chat,
                currentSessionId = DraftSessionId,
                draftInput = "",
                draftAttachments = emptyList(),
                draftSelectedSkillIds = emptyList(),
                draftSelectedMcpServerIds = emptyList(),
                draftAgentModeEnabled = false,
                draftWorkspaceId = null,
                editingSessionId = null,
                editingMessageId = null,
                unviewedCompletedSessionIds = it.unviewedCompletedSessionIds - DraftSessionId,
                showStarterPromptHint = false,
            )
        }
        persistChats()
    }

    fun selectSession(sessionId: String) {
        _uiState.update {
            it.copy(
                currentScreen = AppScreen.Chat,
                currentSessionId = sessionId,
                draftInput = "",
                draftAttachments = emptyList(),
                draftSelectedSkillIds = emptyList(),
                draftSelectedMcpServerIds = emptyList(),
                draftAgentModeEnabled = false,
                draftWorkspaceId = null,
                editingSessionId = null,
                editingMessageId = null,
                unviewedCompletedSessionIds = it.unviewedCompletedSessionIds - sessionId,
                showStarterPromptHint = false,
            )
        }
        persistChats()
    }

    fun renameSession(
        sessionId: String,
        title: String,
    ) {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isBlank()) return
        updateSession(sessionId) { session ->
            if (session.title == trimmedTitle && session.hasCustomTitle) {
                null
            } else {
                session.copy(
                    title = trimmedTitle.take(80),
                    hasCustomTitle = true,
                )
            }
        }
    }

    fun deleteSession(sessionId: String) {
        if (sessionExecutionManager.isSessionRunning(sessionId)) {
            emitTransientMessage("Pause this session before deleting it.")
            return
        }

        var didUpdate = false
        _uiState.update { current ->
            val updatedSessions = current.sessions.filterNot { it.id == sessionId }
            if (updatedSessions.size == current.sessions.size) return@update current
            didUpdate = true
            current.copy(
                sessions = updatedSessions,
                currentSessionId = if (current.currentSessionId == sessionId) DraftSessionId else current.currentSessionId,
                draftInput = if (current.editingSessionId == sessionId) "" else current.draftInput,
                draftAttachments = if (current.editingSessionId == sessionId) emptyList() else current.draftAttachments,
                draftWorkspaceId = if (current.editingSessionId == sessionId) null else current.draftWorkspaceId,
                editingSessionId = if (current.editingSessionId == sessionId) null else current.editingSessionId,
                editingMessageId = if (current.editingSessionId == sessionId) null else current.editingMessageId,
                unviewedCompletedSessionIds = current.unviewedCompletedSessionIds - sessionId,
                showStarterPromptHint = false,
            )
        }
        if (didUpdate) {
            persistChats()
        }
    }

    fun exportSessionToUri(
        sessionId: String,
        destinationUri: Uri,
    ) {
        val session = _uiState.value.sessions.firstOrNull { it.id == sessionId } ?: return
        viewModelScope.launch {
            val didExport = withContext(Dispatchers.IO) {
                writeTextToUri(
                    uri = destinationUri,
                    text = JSONObject().apply {
                        put("schemaVersion", 1)
                        put("exportType", "session")
                        put("exportedAtMillis", System.currentTimeMillis())
                        put("session", session.copy(messages = syncActiveBranches(session.messages)).toJson())
                    }.toString(2),
                )
            }
            emitTransientMessage(if (didExport) "Session exported" else "Couldn't export session")
        }
    }

    fun exportAllDataToUri(destinationUri: Uri) {
        val snapshot = _uiState.value
        viewModelScope.launch {
            val didExport = withContext(Dispatchers.IO) {
                writeTextToUri(
                    uri = destinationUri,
                    text = buildFullAppExportJson(snapshot).toString(2),
                )
            }
            emitTransientMessage(if (didExport) "App data exported" else "Couldn't export app data")
        }
    }

    fun importAllDataFromUri(sourceUri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val rawValue = readTextFromUri(sourceUri)
                    parseFullAppImport(JSONObject(rawValue))
                }
            }
            result
                .onSuccess { imported ->
                    settingsRepository.replaceImportedSettings(
                        settings = imported.settings,
                        providerConfigs = imported.providerConfigs,
                    )
                    extensionsRepository.updateInstalledSkills(imported.installedSkills)
                    extensionsRepository.updateMcpServers(imported.mcpServers)
                    chatStateStore.update {
                        it.copy(
                            sessions = imported.sessions,
                            currentSessionId = imported.currentSessionId,
                        )
                    }
                    _uiState.update { current ->
                        current.copy(
                            sessions = imported.sessions,
                            currentSessionId = imported.currentSessionId,
                            draftInput = "",
                            draftAttachments = emptyList(),
                            draftSelectedSkillIds = emptyList(),
                            draftSelectedMcpServerIds = emptyList(),
                            draftAgentModeEnabled = false,
                            draftWorkspaceId = null,
                            editingSessionId = null,
                            editingMessageId = null,
                            unviewedCompletedSessionIds = emptySet(),
                        )
                    }
                    emitTransientMessage("App data imported")
                }
                .onFailure { throwable ->
                    emitTransientMessage("Couldn't import app data: ${throwable.userFacingMessage()}")
                }
        }
    }

    fun startEditingUserMessage(
        sessionId: String,
        messageId: String,
    ) {
        if (sessionExecutionManager.isSessionRunning(sessionId)) return

        val session = _uiState.value.sessions.firstOrNull { it.id == sessionId } ?: return
        val message = session.messages.firstOrNull {
            it.id == messageId && it.author == MessageAuthor.User
        } ?: return

        _uiState.update {
            it.copy(
                currentScreen = AppScreen.Chat,
                currentSessionId = sessionId,
                draftInput = message.text,
                draftAttachments = message.attachments.map(::normalizeDraftAttachmentForEditing),
                draftWorkspaceId = sessionId,
                editingSessionId = sessionId,
                editingMessageId = messageId,
                showStarterPromptHint = false,
            )
        }
        persistChats()
    }

    fun cancelMessageEdit() {
        _uiState.update {
            it.copy(
                draftInput = "",
                draftAttachments = emptyList(),
                draftSelectedSkillIds = emptyList(),
                draftSelectedMcpServerIds = emptyList(),
                draftAgentModeEnabled = false,
                draftWorkspaceId = null,
                editingSessionId = null,
                editingMessageId = null,
                showStarterPromptHint = false,
            )
        }
    }

    fun deleteMessage(
        sessionId: String,
        messageId: String,
    ) {
        if (sessionExecutionManager.isSessionRunning(sessionId)) return

        var didUpdate = false

        _uiState.update { current ->
            val sessionIndex = current.sessions.indexOfFirst { it.id == sessionId }
            if (sessionIndex < 0) return@update current

            val session = current.sessions[sessionIndex]
            val messageIndex = session.messages.indexOfFirst { it.id == messageId }
            if (messageIndex < 0) return@update current

            val trimFromIndex = session.messages.resolveConversationTrimIndex(messageIndex)
            val trimmedMessages = session.messages.take(trimFromIndex)
            val updatedSessions = current.sessions.toMutableList().apply {
                removeAt(sessionIndex)
                if (trimmedMessages.isNotEmpty()) {
                    add(sessionIndex.coerceAtMost(size), session.withMessages(trimmedMessages))
                }
            }

            didUpdate = true
                current.copy(
                    sessions = updatedSessions,
                    currentSessionId = when {
                        trimmedMessages.isEmpty() && current.currentSessionId == sessionId -> DraftSessionId
                        else -> current.currentSessionId
                    },
                    draftSelectedSkillIds = if (
                        trimmedMessages.isEmpty() && current.currentSessionId == sessionId
                    ) {
                        emptyList()
                    } else {
                        current.draftSelectedSkillIds
                    },
                    draftSelectedMcpServerIds = if (
                        trimmedMessages.isEmpty() && current.currentSessionId == sessionId
                    ) {
                        emptyList()
                    } else {
                        current.draftSelectedMcpServerIds
                    },
                    draftInput = if (current.editingSessionId == sessionId) "" else current.draftInput,
                    draftAttachments = if (current.editingSessionId == sessionId) {
                        emptyList()
                } else {
                    current.draftAttachments
                },
                draftWorkspaceId = if (current.editingSessionId == sessionId) null else current.draftWorkspaceId,
                editingSessionId = if (current.editingSessionId == sessionId) null else current.editingSessionId,
                editingMessageId = if (current.editingSessionId == sessionId) null else current.editingMessageId,
                pendingResponseSessionId = if (current.pendingResponseSessionId == sessionId) null else current.pendingResponseSessionId,
                pendingToolInvocations = if (current.pendingResponseSessionId == sessionId) {
                    emptyList()
                } else {
                    current.pendingToolInvocations
                },
            )
        }

        if (didUpdate) {
            persistChats()
        }
    }

    fun redoAgentMessage(
        sessionId: String,
        messageId: String,
    ) {
        if (sessionExecutionManager.isSessionRunning(sessionId)) return

        val snapshot = _uiState.value
        var request: SessionTurnRequest? = null

        _uiState.update { current ->
            val sessionIndex = current.sessions.indexOfFirst { it.id == sessionId }
            if (sessionIndex < 0) return@update current

            val session = current.sessions[sessionIndex]
            val messageIndex = session.messages.indexOfFirst {
                it.id == messageId && it.author == MessageAuthor.Agent
            }
            if (messageIndex < 0) return@update current

            val trimFromIndex = session.messages.resolveConversationTrimIndex(messageIndex)
            val trimmedMessages = session.messages.take(trimFromIndex)
            if (trimmedMessages.lastOrNull()?.author != MessageAuthor.User) {
                return@update current
            }

            request = SessionTurnRequest(
                sessionId = sessionId,
                settings = snapshot.settings,
                requestMessages = trimmedMessages,
                selectedSkillIds = session.selectedSkillIds,
                activeSkills = session.activeSkills,
                activeMcpServerIds = session.activeMcpServerIds,
                agentModeEnabled = session.agentModeEnabled,
            )
            val updatedSessions = current.sessions.toMutableList().apply {
                removeAt(sessionIndex)
                add(0, session.withMessages(trimmedMessages))
            }

            current.copy(
                sessions = updatedSessions,
                currentSessionId = sessionId,
                currentScreen = AppScreen.Chat,
                draftInput = "",
                draftAttachments = emptyList(),
                draftWorkspaceId = null,
                editingSessionId = null,
                editingMessageId = null,
            )
        }

        val turnRequest = request ?: return
        persistChats()
        sessionExecutionManager.startTurn(turnRequest)
    }

    fun retryUserMessage(
        sessionId: String,
        messageId: String,
    ) {
        if (sessionExecutionManager.isSessionRunning(sessionId)) return

        val snapshot = _uiState.value
        var request: SessionTurnRequest? = null

        _uiState.update { current ->
            val sessionIndex = current.sessions.indexOfFirst { it.id == sessionId }
            if (sessionIndex < 0) return@update current

            val session = current.sessions[sessionIndex]
            val userMessage = session.messages.firstOrNull {
                it.id == messageId && it.author == MessageAuthor.User
            } ?: return@update current

            val retryMessage = userMessage.copy(
                id = "user-${System.currentTimeMillis()}",
                createdAtMillis = System.currentTimeMillis(),
                branchGroup = null,
            )
            val branchedMessages = createEditedMessageBranch(
                messages = session.messages,
                messageId = messageId,
                replacement = retryMessage,
            ) ?: return@update current
            val updatedSession = session.withMessages(branchedMessages)
            val updatedSessions = current.sessions.toMutableList().apply {
                removeAt(sessionIndex)
                add(0, updatedSession)
            }

            request = SessionTurnRequest(
                sessionId = sessionId,
                settings = snapshot.settings,
                requestMessages = updatedSession.messages,
                selectedSkillIds = updatedSession.selectedSkillIds,
                activeSkills = updatedSession.activeSkills,
                activeMcpServerIds = updatedSession.activeMcpServerIds,
                agentModeEnabled = updatedSession.agentModeEnabled,
            )

            current.copy(
                sessions = updatedSessions,
                currentSessionId = sessionId,
                currentScreen = AppScreen.Chat,
                draftInput = "",
                draftAttachments = emptyList(),
                draftWorkspaceId = null,
                editingSessionId = null,
                editingMessageId = null,
            )
        }

        val turnRequest = request ?: return
        persistChats()
        sessionExecutionManager.startTurn(turnRequest)
    }

    fun switchUserMessageBranch(
        sessionId: String,
        messageId: String,
        delta: Int,
    ) {
        if (sessionExecutionManager.isSessionRunning(sessionId)) return
        var didUpdate = false

        _uiState.update { current ->
            val sessionIndex = current.sessions.indexOfFirst { it.id == sessionId }
            if (sessionIndex < 0) return@update current
            val session = current.sessions[sessionIndex]
            val updatedMessages = switchMessageBranch(
                messages = session.messages,
                messageId = messageId,
                delta = delta,
            ) ?: return@update current
            didUpdate = true
            val updatedSession = session.withMessages(updatedMessages)
            val updatedSessions = current.sessions.toMutableList().apply {
                set(sessionIndex, updatedSession)
            }
            current.copy(sessions = updatedSessions)
        }

        if (didUpdate) {
            persistChats()
        }
    }

    fun saveSettings(
        provider: LlmProvider,
        apiKey: String,
        baseUrl: String,
        modelId: String,
        systemPrompt: String,
        tavilyApiKey: String,
        llmInactivityReconnectTimeoutSeconds: Int,
        keepTasksRunningInBackground: Boolean,
        notifyOnTaskCompletion: Boolean,
        agentModeAuthorizationEnabled: Boolean,
        agentModeAuthorizationMethod: AgentModeAuthorizationMethod,
        language: AppLanguage,
        themeMode: AppThemeMode,
    ) {
        viewModelScope.launch {
            settingsRepository.updateSettings(
                AppSettings(
                    provider = provider,
                    apiKey = apiKey.trim(),
                    baseUrl = baseUrl.trim(),
                    modelId = modelId.trim(),
                    systemPrompt = systemPrompt,
                    tavilyApiKey = tavilyApiKey.trim(),
                    llmInactivityReconnectTimeoutSeconds =
                        normalizeLlmInactivityReconnectTimeoutSeconds(
                            llmInactivityReconnectTimeoutSeconds
                        ),
                    keepTasksRunningInBackground = keepTasksRunningInBackground,
                    notifyOnTaskCompletion = notifyOnTaskCompletion,
                    agentModeAuthorizationEnabled = agentModeAuthorizationEnabled,
                    agentModeAuthorizationMethod = agentModeAuthorizationMethod,
                    language = language,
                    themeMode = themeMode,
                )
            )
        }
    }

    // ── Multi-Provider methods ───────────────────────────────────────────────

    fun updateAppLanguage(language: AppLanguage) {
        viewModelScope.launch {
            settingsRepository.updateLanguage(language)
        }
    }

    fun updateAppThemeMode(themeMode: AppThemeMode) {
        viewModelScope.launch {
            settingsRepository.updateThemeMode(themeMode)
        }
    }

    fun upsertProviderConfig(config: LlmProviderConfig) {
        viewModelScope.launch {
            settingsRepository.upsertProviderConfig(config)
        }
    }

    fun removeProviderConfig(id: String) {
        viewModelScope.launch {
            settingsRepository.removeProviderConfig(id)
        }
    }

    fun setActiveProvider(id: String) {
        viewModelScope.launch {
            settingsRepository.setActiveProvider(id)
        }
    }

    fun fetchModels(
        config: LlmProviderConfig,
        onComplete: (List<String>) -> Unit,
    ) {
        _uiState.update { it.copy(isFetchingModels = true) }
        viewModelScope.launch {
            val result = LlmApiClient.fetchModels(config)
            _uiState.update { it.copy(isFetchingModels = false) }
            if (result.models.isNotEmpty()) {
                settingsRepository.updateProviderCachedModels(config.id, result.models)
            }
            onComplete(result.models)
            if (result.error != null) {
                _transientMessages.emit("Failed to fetch models: ${result.error}")
            }
        }
    }

    fun installSkillFromDirectory(treeUri: Uri) {
        performSkillInstall {
            skillManager.installSkillFromDirectory(treeUri)
        }
    }

    fun installSkillFromZip(
        zipUri: Uri,
        onComplete: (Boolean) -> Unit = {},
    ) {
        performSkillInstall(onComplete = onComplete) {
            skillManager.installSkillFromZipUri(zipUri)
        }
    }

    fun installSkillFromRemote(
        url: String,
        onComplete: (Boolean) -> Unit = {},
    ) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) {
            onComplete(false)
            return
        }
        performSkillInstall(onComplete = onComplete) {
            skillManager.installSkillFromRemote(trimmedUrl)
        }
    }

    fun removeSkill(skillId: String) {
        viewModelScope.launch {
            skillManager.uninstallSkill(skillId)
        }
    }

    fun setSkillEnabled(
        skillId: String,
        enabled: Boolean,
    ) {
        viewModelScope.launch {
            extensionsRepository.setSkillEnabled(skillId, enabled)
        }
    }

    fun setComposerSkillSelected(
        skillId: String,
        selected: Boolean,
    ) {
        var didUpdate = false
        _uiState.update { current ->
            if (current.currentSessionId == DraftSessionId) {
                val updatedDraftSelection = updateOrderedSelection(
                    current.draftSelectedSkillIds,
                    skillId,
                    selected,
                )
                if (updatedDraftSelection == current.draftSelectedSkillIds) {
                    current
                } else {
                    didUpdate = true
                    current.copy(draftSelectedSkillIds = updatedDraftSelection)
                }
            } else {
                val sessionIndex = current.sessions.indexOfFirst { it.id == current.currentSessionId }
                if (sessionIndex < 0) return@update current
                val updatedSessions = current.sessions.toMutableList()
                val session = updatedSessions.removeAt(sessionIndex)
                val updatedSelectedSkillIds = updateOrderedSelection(
                    session.selectedSkillIds,
                    skillId,
                    selected,
                )
                val updatedActiveSkills = session.activeSkills.filter { activeSkill ->
                    updatedSelectedSkillIds.contains(activeSkill.skillId)
                }
                if (
                    updatedSelectedSkillIds == session.selectedSkillIds &&
                    updatedActiveSkills == session.activeSkills
                ) {
                    updatedSessions.add(sessionIndex, session)
                    current
                } else {
                    didUpdate = true
                    updatedSessions.add(
                        sessionIndex.coerceAtMost(updatedSessions.size),
                        session.copy(
                            selectedSkillIds = updatedSelectedSkillIds,
                            activeSkills = updatedActiveSkills,
                        ),
                    )
                    current.copy(sessions = updatedSessions)
                }
            }
        }
        if (didUpdate && _uiState.value.currentSessionId != DraftSessionId) {
            persistChats()
        }
    }

    fun saveStreamableHttpMcpServer(
        serverId: String?,
        displayName: String,
        url: String,
        headersRaw: String,
    ) {
        val trimmedName = displayName.trim()
        val trimmedUrl = url.trim()
        if (trimmedName.isBlank() || trimmedUrl.isBlank()) return
        viewModelScope.launch {
            val existingServer = serverId?.let(::findMcpServerById)
            val now = System.currentTimeMillis()
            extensionsRepository.upsertMcpServer(
                McpServerConfig(
                    id = existingServer?.id ?: "mcp-$now",
                    displayName = trimmedName,
                    actionLabel = com.zhousl.aether.data.generateQuickActionLabel(
                        trimmedName,
                        trimmedUrl,
                    ),
                    transport = com.zhousl.aether.data.McpTransportConfig.StreamableHttp(
                        url = trimmedUrl,
                        headers = parseKeyValueLines(headersRaw),
                    ),
                    isEnabled = existingServer?.isEnabled ?: true,
                    connectTimeoutMillis = existingServer?.connectTimeoutMillis ?: 15_000L,
                    requestTimeoutMillis = existingServer?.requestTimeoutMillis ?: 60_000L,
                    createdAtMillis = existingServer?.createdAtMillis ?: now,
                    updatedAtMillis = now,
                ),
            )
            if (existingServer != null) {
                mcpClientManager.disconnect(existingServer.id)
            }
        }
    }

    fun saveStdIoMcpServer(
        serverId: String?,
        displayName: String,
        command: String,
        workingDirectory: String,
        environmentRaw: String,
    ) {
        val trimmedName = displayName.trim()
        val trimmedCommand = command.trim()
        if (trimmedName.isBlank() || trimmedCommand.isBlank()) return
        viewModelScope.launch {
            val existingServer = serverId?.let(::findMcpServerById)
            val now = System.currentTimeMillis()
            extensionsRepository.upsertMcpServer(
                McpServerConfig(
                    id = existingServer?.id ?: "mcp-$now",
                    displayName = trimmedName,
                    actionLabel = com.zhousl.aether.data.generateQuickActionLabel(
                        trimmedName,
                        trimmedCommand,
                    ),
                    transport = com.zhousl.aether.data.McpTransportConfig.StdIo(
                        command = trimmedCommand,
                        workingDirectory = workingDirectory.trim(),
                        environment = parseKeyValueLines(environmentRaw),
                    ),
                    isEnabled = existingServer?.isEnabled ?: true,
                    connectTimeoutMillis = existingServer?.connectTimeoutMillis ?: 15_000L,
                    requestTimeoutMillis = existingServer?.requestTimeoutMillis ?: 60_000L,
                    createdAtMillis = existingServer?.createdAtMillis ?: now,
                    updatedAtMillis = now,
                ),
            )
            if (existingServer != null) {
                mcpClientManager.disconnect(existingServer.id)
            }
        }
    }

    fun removeMcpServer(serverId: String) {
        viewModelScope.launch {
            extensionsRepository.removeMcpServer(serverId)
            mcpClientManager.disconnect(serverId)
        }
    }

    fun setMcpServerEnabled(
        serverId: String,
        enabled: Boolean,
    ) {
        viewModelScope.launch {
            extensionsRepository.setMcpServerEnabled(serverId, enabled)
            if (!enabled) {
                mcpClientManager.disconnect(serverId)
            }
        }
    }

    fun setComposerMcpServerSelected(
        serverId: String,
        selected: Boolean,
    ) {
        var didUpdate = false
        _uiState.update { current ->
            if (current.currentSessionId == DraftSessionId) {
                val updatedDraftSelection = updateOrderedSelection(
                    current.draftSelectedMcpServerIds,
                    serverId,
                    selected,
                )
                if (updatedDraftSelection == current.draftSelectedMcpServerIds) {
                    current
                } else {
                    didUpdate = true
                    current.copy(draftSelectedMcpServerIds = updatedDraftSelection)
                }
            } else {
                val sessionIndex = current.sessions.indexOfFirst { it.id == current.currentSessionId }
                if (sessionIndex < 0) return@update current
                val updatedSessions = current.sessions.toMutableList()
                val session = updatedSessions.removeAt(sessionIndex)
                val updatedActiveIds = updateOrderedSelection(
                    session.activeMcpServerIds,
                    serverId,
                    selected,
                )
                if (updatedActiveIds == session.activeMcpServerIds) {
                    updatedSessions.add(sessionIndex, session)
                    current
                } else {
                    didUpdate = true
                    updatedSessions.add(
                        sessionIndex.coerceAtMost(updatedSessions.size),
                        session.copy(activeMcpServerIds = updatedActiveIds),
                    )
                    current.copy(sessions = updatedSessions)
                }
            }
        }
        if (didUpdate && _uiState.value.currentSessionId != DraftSessionId) {
            persistChats()
        }
    }

    fun setComposerAgentModeSelected(selected: Boolean) {
        var didUpdate = false
        _uiState.update { current ->
            if (current.currentSessionId == DraftSessionId) {
                if (current.draftAgentModeEnabled == selected) {
                    current
                } else {
                    didUpdate = true
                    current.copy(draftAgentModeEnabled = selected)
                }
            } else {
                val sessionIndex = current.sessions.indexOfFirst { it.id == current.currentSessionId }
                if (sessionIndex < 0) return@update current
                val updatedSessions = current.sessions.toMutableList()
                val session = updatedSessions.removeAt(sessionIndex)
                if (session.agentModeEnabled == selected) {
                    updatedSessions.add(sessionIndex, session)
                    current
                } else {
                    didUpdate = true
                    updatedSessions.add(
                        sessionIndex.coerceAtMost(updatedSessions.size),
                        session.copy(agentModeEnabled = selected),
                    )
                    current.copy(sessions = updatedSessions)
                }
            }
        }
        if (didUpdate && _uiState.value.currentSessionId != DraftSessionId) {
            persistChats()
        }
    }

    fun sendCurrentMessage() {
        submitCurrentMessage(SessionFollowUpMode.Queue)
    }

    fun queueCurrentMessage() {
        submitCurrentMessage(SessionFollowUpMode.Queue)
    }

    fun steerCurrentMessage() {
        submitCurrentMessage(SessionFollowUpMode.Steer)
    }

    private fun submitCurrentMessage(
        runningFollowUpMode: SessionFollowUpMode,
    ) {
        val snapshot = _uiState.value
        val content = snapshot.draftInput.trim()
        val attachments = snapshot.draftAttachments

        if (content.isEmpty() && attachments.isEmpty()) return
        if (attachments.any { it.workspaceState != AttachmentWorkspaceState.Ready }) return

        val targetSessionId = snapshot.editingSessionId ?: when {
            snapshot.currentSessionId != DraftSessionId -> snapshot.currentSessionId
            !snapshot.draftWorkspaceId.isNullOrBlank() -> snapshot.draftWorkspaceId.orEmpty()
            else -> "session-${System.currentTimeMillis()}"
        }

        val now = System.currentTimeMillis()
        val userMessage = ChatMessage(
            id = "user-$now",
            author = MessageAuthor.User,
            text = content,
            createdAtMillis = now,
            attachments = attachments,
        )

        if (snapshot.editingSessionId != null && sessionExecutionManager.isSessionRunning(targetSessionId)) {
            emitTransientMessage("Pause this session before editing an earlier message.")
            return
        }

        if (sessionExecutionManager.isSessionRunning(targetSessionId)) {
            if (!sessionExecutionManager.submitFollowUp(targetSessionId, userMessage, runningFollowUpMode)) {
                emitTransientMessage("This session is no longer running. Try sending again.")
                return
            }
            _uiState.update { current ->
                current.copy(
                    currentScreen = AppScreen.Chat,
                    draftInput = "",
                    draftAttachments = emptyList(),
                    draftWorkspaceId = null,
                    editingSessionId = null,
                    editingMessageId = null,
                    showStarterPromptHint = false,
                )
            }
            return
        }

        var request: SessionTurnRequest? = null
        var requestMessages: List<ChatMessage> = emptyList()
        var requestSelectedSkillIds: List<String> = emptyList()
        var requestActiveSkills: List<ActiveSkillContext> = emptyList()
        var requestActiveMcpServerIds: List<String> = emptyList()
        var requestAgentModeEnabled = false
        var shouldGenerateSessionTitle = false

        _uiState.update { current ->
            val updatedSessions = current.sessions.toMutableList()

            if (
                current.editingSessionId != null &&
                current.editingMessageId != null
            ) {
                val editingSessionIndex = updatedSessions.indexOfFirst {
                    it.id == current.editingSessionId
                }
                if (editingSessionIndex >= 0) {
                    val editingSession = updatedSessions.removeAt(editingSessionIndex)
                    val editingMessageIndex = editingSession.messages.indexOfFirst {
                        it.id == current.editingMessageId && it.author == MessageAuthor.User
                    }
                    if (editingMessageIndex >= 0) {
                        val branchedMessages = createEditedMessageBranch(
                            messages = editingSession.messages,
                            messageId = current.editingMessageId,
                            replacement = userMessage,
                        ) ?: (editingSession.messages.take(editingMessageIndex) + userMessage)
                        val updated = editingSession.withMessages(branchedMessages)
                        updatedSessions.add(0, updated)
                        requestMessages = updated.messages
                        requestSelectedSkillIds = updated.selectedSkillIds
                        requestActiveSkills = updated.activeSkills
                        requestActiveMcpServerIds = updated.activeMcpServerIds
                        requestAgentModeEnabled = updated.agentModeEnabled
                    } else {
                        updatedSessions.add(editingSessionIndex, editingSession)
                    }
                }
            }

            if (requestMessages.isEmpty()) {
                val existingIndex = updatedSessions.indexOfFirst { it.id == targetSessionId }
                if (existingIndex >= 0) {
                    val existing = updatedSessions.removeAt(existingIndex)
                    val updated = existing.withMessages(existing.messages + userMessage)
                    updatedSessions.add(0, updated)
                    requestMessages = updated.messages
                    requestSelectedSkillIds = updated.selectedSkillIds
                    requestActiveSkills = updated.activeSkills
                    requestActiveMcpServerIds = updated.activeMcpServerIds
                    requestAgentModeEnabled = updated.agentModeEnabled
                } else {
                    val newSession = createSession(
                        id = targetSessionId,
                        messages = listOf(userMessage),
                        title = "New chat",
                        hasCustomTitle = true,
                        selectedSkillIds = current.draftSelectedSkillIds,
                        activeMcpServerIds = current.draftSelectedMcpServerIds,
                        agentModeEnabled = current.draftAgentModeEnabled,
                    )
                    shouldGenerateSessionTitle = true
                    updatedSessions.add(0, newSession)
                    requestMessages = newSession.messages
                    requestSelectedSkillIds = newSession.selectedSkillIds
                    requestActiveSkills = newSession.activeSkills
                    requestActiveMcpServerIds = newSession.activeMcpServerIds
                    requestAgentModeEnabled = newSession.agentModeEnabled
                }
            }

            request = SessionTurnRequest(
                sessionId = targetSessionId,
                settings = current.settings,
                requestMessages = requestMessages,
                selectedSkillIds = requestSelectedSkillIds,
                activeSkills = requestActiveSkills,
                activeMcpServerIds = requestActiveMcpServerIds,
                agentModeEnabled = requestAgentModeEnabled,
            )

            current.copy(
                sessions = updatedSessions,
                currentSessionId = targetSessionId,
                draftInput = "",
                draftAttachments = emptyList(),
                draftSelectedSkillIds = emptyList(),
                draftSelectedMcpServerIds = emptyList(),
                draftAgentModeEnabled = false,
                draftWorkspaceId = null,
                editingSessionId = null,
                editingMessageId = null,
                currentScreen = AppScreen.Chat,
                showStarterPromptHint = false,
            )
        }

        val turnRequest = request ?: return
        persistChats()
        if (shouldGenerateSessionTitle) {
            generateSessionTitle(
                sessionId = targetSessionId,
                seedMessage = userMessage,
                settings = turnRequest.settings,
            )
        }
        sessionExecutionManager.startTurn(turnRequest)
    }

    private fun handleTurnEvent(
        event: SessionTurnEvent,
    ) {
        val isSuccessfulAssistantReply = event.outcome == SessionTurnOutcome.Success
        if (
            shouldMarkOnboardingCompleted(
                settings = _uiState.value.settings,
                isSuccessfulAssistantReply = isSuccessfulAssistantReply,
            )
        ) {
            viewModelScope.launch {
                settingsRepository.updateOnboardingCompletedVersion(CurrentOnboardingVersion)
            }
        }
        if (
            shouldRevealFollowUpTourCard(
                isAwaitingFollowUpTour = _uiState.value.awaitingFollowUpTour,
                isSuccessfulAssistantReply = isSuccessfulAssistantReply,
            )
        ) {
            scheduleFollowUpTourAfterFirstReply()
        }
        _uiState.update { current ->
            val unviewedCompletedSessionIds = when {
                event.sessionId == current.currentSessionId -> current.unviewedCompletedSessionIds - event.sessionId
                event.outcome != SessionTurnOutcome.Neutral -> current.unviewedCompletedSessionIds + event.sessionId
                else -> current.unviewedCompletedSessionIds
            }
            current.copy(unviewedCompletedSessionIds = unviewedCompletedSessionIds)
        }
    }

    private fun requestAssistantReply(
        sessionId: String,
        requestMessages: List<ChatMessage>,
        settings: AppSettings,
        selectedSkillIds: List<String>,
        activeSkills: List<ActiveSkillContext>,
        activeMcpServerIds: List<String>,
    ) {
        if (settings.provider == LlmProvider.VertexExpress && settings.apiKey.isBlank()) {
            appendAgentMessage(
                sessionId = sessionId,
                text = "API Key is required before sending with Vertex AI (Express Mode).",
                outcome = AssistantResponseOutcome.ValidationError,
            )
            return
        }

        if (settings.baseUrl.isBlank() || settings.modelId.isBlank()) {
            appendAgentMessage(
                sessionId = sessionId,
                text = "Base URL and Model ID are required before sending.",
                outcome = AssistantResponseOutcome.ValidationError,
            )
            return
        }

        val requestId = System.currentTimeMillis()
        val workspaceDirectory = workspaceFileBridge.workspaceDirectory(sessionId)
        activeGenerationRequestId = requestId
        activeGenerationStartedAtMillis = System.currentTimeMillis()

        val job = viewModelScope.launch {
            val requestJob = coroutineContext[Job]
            val startedAt = activeGenerationStartedAtMillis ?: System.currentTimeMillis()
            val completedToolInvocations = LinkedHashMap<String, ChatToolInvocation>()

            try {
                var resolvedActiveSkills = resolveSelectedActiveSkills(
                    selectedSkillIds = selectedSkillIds,
                    existingActiveSkills = activeSkills,
                )
                var resolvedSelectedSkillIds = resolvedActiveSkills.map { it.skillId }
                val resolvedAvailableSkills = _uiState.value.installedSkills
                    .filter { it.isEnabled }
                    .sortedBy { it.name.lowercase() }
                val resolvedMcpServers = resolveSelectedMcpServers(activeMcpServerIds)
                val resolvedMcpServerIds = resolvedMcpServers.map { it.id }
                setSessionSelectedSkillIds(sessionId, resolvedSelectedSkillIds)
                setSessionActiveSkills(sessionId, resolvedActiveSkills)
                setSessionActiveMcpServerIds(sessionId, resolvedMcpServerIds)
                mcpClientManager.syncServers(
                    servers = resolvedMcpServers,
                    workspaceDirectory = workspaceDirectory,
                )

                val result = agent.runTurn(
                    settings = settings,
                    messages = buildRequestMessages(requestMessages),
                    workspaceDirectory = workspaceDirectory,
                    availableSkills = resolvedAvailableSkills,
                    activeSkills = resolvedActiveSkills,
                    mcpToolBindings = mcpClientManager.toolBindings(),
                    agentModeEnabled = false,
                    onToolEvent = toolEvent@{ event ->
                        if (!isActiveGeneration(requestId)) return@toolEvent

                        val toolEventUptimeMillis = SystemClock.uptimeMillis()
                        val toolInvocation = ChatToolInvocation(
                            id = event.id,
                            toolName = event.name,
                            argumentsJson = event.argumentsJson,
                            outputJson = event.outputJson.orEmpty(),
                            isRunning = event.outputJson == null,
                            startedAtUptimeMillis = toolEventUptimeMillis,
                            completedAtUptimeMillis = if (event.outputJson == null) {
                                null
                            } else {
                                toolEventUptimeMillis
                            },
                        )
                        if (!toolInvocation.isRunning) {
                            completedToolInvocations[toolInvocation.id] = toolInvocation
                        }
                        pushPendingToolInvocation(
                            sessionId = sessionId,
                            toolInvocation = toolInvocation,
                        )
                    },
                    onAssistantTextDelta = textEvent@{ delta ->
                        if (!isActiveGeneration(requestId)) return@textEvent
                        appendPendingAssistantText(
                            sessionId = sessionId,
                            delta = delta,
                        )
                    },
                    onAssistantTextReset = resetText@{
                        if (!isActiveGeneration(requestId)) return@resetText
                        clearPendingAssistantText(sessionId)
                    },
                    onStreamingStatus = statusEvent@{ status ->
                        if (!isActiveGeneration(requestId)) return@statusEvent
                        setPendingStatusText(sessionId, status)
                    },
                    onSkillActivated = skillEvent@{ activeSkill ->
                        if (!isActiveGeneration(requestId)) return@skillEvent
                        resolvedSelectedSkillIds = (resolvedSelectedSkillIds + activeSkill.skillId).distinct()
                        resolvedActiveSkills = upsertActiveSkillContext(resolvedActiveSkills, activeSkill)
                        setSessionSelectedSkillIds(sessionId, resolvedSelectedSkillIds)
                        setSessionActiveSkills(sessionId, resolvedActiveSkills)
                    },
                )
                if (!isActiveGeneration(requestId)) return@launch

                val thoughtDurationMillis = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
                result.fold(
                    onSuccess = { reply ->
                        appendAgentMessage(
                            sessionId = sessionId,
                            text = reply,
                            toolInvocations = completedToolInvocations.values.toList(),
                            thoughtDurationMillis = thoughtDurationMillis,
                            outcome = AssistantResponseOutcome.Success,
                        )
                    },
                    onFailure = { throwable ->
                        appendAgentMessage(
                            sessionId = sessionId,
                            text = "Request failed: ${throwable.message ?: "Unknown error"}",
                            toolInvocations = completedToolInvocations.values.toList(),
                            thoughtDurationMillis = thoughtDurationMillis,
                            outcome = AssistantResponseOutcome.Failure,
                        )
                    },
                )
            } catch (_: CancellationException) {
                if (isActiveGeneration(requestId)) {
                    clearPendingGenerationState()
                }
            } finally {
                if (activeGenerationRequestId == requestId && activeGenerationJob === requestJob) {
                    activeGenerationRequestId = null
                    activeGenerationJob = null
                    activeGenerationStartedAtMillis = null
                }
            }
        }

        activeGenerationJob?.cancel()
        activeGenerationJob = job
    }

    private fun buildRequestMessages(messages: List<ChatMessage>): List<LlmMessage> =
        messages.map { message ->
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

            LlmMessage(
                role = if (message.author == MessageAuthor.User) "user" else "assistant",
                contentParts = parts,
            )
        }

    private fun buildWorkspaceAttachmentPart(attachment: ChatAttachment): LlmTextPart {
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

    private suspend fun buildPendingDraftAttachment(
        uri: Uri,
    ): ChatAttachment? {
        val metadata = readAttachmentMetadata(uri) ?: return null
        return ChatAttachment(
            id = "attachment-${System.currentTimeMillis()}-${uri.hashCode()}",
            uri = uri.toString(),
            name = metadata.displayName,
            mimeType = metadata.mimeType,
            sizeBytes = metadata.sizeBytes,
            kind = metadata.kind,
            workspaceState = AttachmentWorkspaceState.Pending,
        )
    }

    private suspend fun importDraftAttachmentToWorkspace(
        attachment: ChatAttachment,
        sessionId: String,
    ) {
        val importResult = workspaceFileBridge.importAttachmentToWorkspace(
            sourceUri = Uri.parse(attachment.uri),
            sessionId = sessionId,
            attachmentId = attachment.id,
            displayName = attachment.name,
        )

        _uiState.update { current ->
            val attachmentIndex = current.draftAttachments.indexOfFirst { it.id == attachment.id }
            if (attachmentIndex < 0) return@update current

            val existingAttachment = current.draftAttachments[attachmentIndex]
            val updatedAttachment = importResult.fold(
                onSuccess = { importedFile ->
                    val resolvedMimeType = existingAttachment.mimeType.ifBlank {
                        workspaceFileBridge.guessMimeType(importedFile.absolutePath)
                    }
                    existingAttachment.copy(
                        mimeType = resolvedMimeType,
                        sizeBytes = existingAttachment.sizeBytes ?: importedFile.bytesCopied,
                        kind = if (resolvedMimeType.startsWith("image/")) {
                            AttachmentKind.Image
                        } else {
                            AttachmentKind.File
                        },
                        workspacePath = importedFile.absolutePath,
                        workspaceState = AttachmentWorkspaceState.Ready,
                        workspaceError = "",
                    )
                },
                onFailure = { throwable ->
                    existingAttachment.copy(
                        workspaceState = AttachmentWorkspaceState.Failed,
                        workspaceError = throwable.message
                            .orEmpty()
                            .ifBlank { "Couldn't copy this attachment into the workspace." },
                    )
                },
            )

            current.copy(
                draftAttachments = current.draftAttachments.toMutableList().apply {
                    set(attachmentIndex, updatedAttachment)
                }
            )
        }
    }

    private fun normalizeDraftAttachmentForEditing(
        attachment: ChatAttachment,
    ): ChatAttachment = if (attachment.workspacePath.isNotBlank()) {
        attachment.copy(
            workspaceState = AttachmentWorkspaceState.Ready,
            workspaceError = "",
        )
    } else {
        attachment.copy(
            workspaceState = AttachmentWorkspaceState.Failed,
            workspaceError = "This attachment is missing its workspace copy. Re-upload it before sending.",
        )
    }

    private fun readAttachmentMetadata(
        uri: Uri,
    ): AttachmentMetadata? {
        val resolver = getApplication<Application>().contentResolver
        var mimeType = resolver.getType(uri).orEmpty()
        var displayName = uri.lastPathSegment ?: "Attachment"
        var sizeBytes: Long? = null

        resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    displayName = cursor.getString(nameIndex) ?: displayName
                }

                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    sizeBytes = cursor.getLong(sizeIndex)
                }
            }
        }

        if (mimeType.isBlank()) {
            mimeType = workspaceFileBridge.guessMimeType(displayName)
        }

        return AttachmentMetadata(
            displayName = displayName,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            kind = if (mimeType.startsWith("image/")) AttachmentKind.Image else AttachmentKind.File,
        )
    }

    private fun appendAgentMessage(
        sessionId: String,
        text: String,
        toolInvocations: List<ChatToolInvocation> = emptyList(),
        thoughtDurationMillis: Long? = null,
        outcome: AssistantResponseOutcome = AssistantResponseOutcome.Neutral,
    ) {
        var didUpdate = false

        _uiState.update { current ->
            val targetIndex = current.sessions.indexOfFirst { it.id == sessionId }
            if (targetIndex < 0) {
                current.copy(
                    isSending = false,
                    pendingResponseSessionId = null,
                    pendingToolInvocations = emptyList(),
                    pendingAssistantText = "",
                    pendingStatusText = "",
                )
            } else {
                val updatedSessions = current.sessions.toMutableList()
                val target = updatedSessions.removeAt(targetIndex)
                updatedSessions.add(
                    0,
                    target.withMessages(
                        target.messages + ChatMessage(
                            id = "agent-${System.currentTimeMillis()}",
                            author = MessageAuthor.Agent,
                            text = text,
                            createdAtMillis = System.currentTimeMillis(),
                            toolInvocations = toolInvocations,
                            thoughtDurationMillis = thoughtDurationMillis,
                        )
                    ),
                )
                didUpdate = true
                current.copy(
                    sessions = updatedSessions,
                    isSending = false,
                    pendingResponseSessionId = null,
                    pendingToolInvocations = emptyList(),
                    pendingAssistantText = "",
                    pendingStatusText = "",
                    showStarterPromptHint = false,
                )
            }
        }

        if (didUpdate) {
            persistChats()
            val isSuccessfulAssistantReply = outcome == AssistantResponseOutcome.Success
            if (
                shouldMarkOnboardingCompleted(
                    settings = _uiState.value.settings,
                    isSuccessfulAssistantReply = isSuccessfulAssistantReply,
                )
            ) {
                viewModelScope.launch {
                    settingsRepository.updateOnboardingCompletedVersion(CurrentOnboardingVersion)
                }
            }
            if (
                shouldRevealFollowUpTourCard(
                    isAwaitingFollowUpTour = _uiState.value.awaitingFollowUpTour,
                    isSuccessfulAssistantReply = isSuccessfulAssistantReply,
                )
            ) {
                scheduleFollowUpTourAfterFirstReply()
            }
        }
    }

    private fun scheduleFollowUpTourAfterFirstReply() {
        _uiState.update { current ->
            current.copy(
                awaitingFollowUpTour = false,
                showFollowUpTourCard = true,
            )
        }
        viewModelScope.launch {
            delay(FollowUpTourAutoOpenDelayMillis)
            _uiState.update { current ->
                if (current.currentScreen != AppScreen.Chat) {
                    current
                } else {
                    current.copy(
                        currentScreen = AppScreen.Onboarding,
                        isOnboardingReplay = true,
                        onboardingStep = OnboardingStep.TermuxSetup,
                        onboardingReturnScreen = AppScreen.Chat,
                        awaitingFollowUpTour = false,
                        showFollowUpTourCard = false,
                    )
                }
            }
        }
    }

    private fun persistChats() {
        val snapshot = _uiState.value
        chatStateStore.update { persisted ->
            persisted.copy(
                sessions = snapshot.sessions,
                currentSessionId = snapshot.currentSessionId,
            )
        }
    }

    private fun createSession(
        id: String,
        messages: List<ChatMessage>,
        title: String? = null,
        hasCustomTitle: Boolean = false,
        selectedSkillIds: List<String> = emptyList(),
        activeSkills: List<ActiveSkillContext> = emptyList(),
        activeMcpServerIds: List<String> = emptyList(),
        agentModeEnabled: Boolean = false,
    ): ChatSession {
        val metadata = deriveSessionMetadata(messages)
        return ChatSession(
            id = id,
            title = title ?: metadata.title,
            preview = metadata.preview,
            hasCustomTitle = hasCustomTitle,
            messages = messages,
            selectedSkillIds = selectedSkillIds,
            activeSkills = activeSkills,
            activeMcpServerIds = activeMcpServerIds,
            agentModeEnabled = agentModeEnabled,
        )
    }

    private fun ChatSession.withMessages(messages: List<ChatMessage>): ChatSession {
        val syncedMessages = syncActiveBranches(messages)
        val metadata = deriveSessionMetadata(syncedMessages)
        return copy(
            title = if (hasCustomTitle) title else metadata.title,
            preview = metadata.preview,
            messages = syncedMessages,
        )
    }

    private suspend fun resolveSelectedActiveSkills(
        selectedSkillIds: List<String>,
        existingActiveSkills: List<ActiveSkillContext>,
    ): List<ActiveSkillContext> {
        if (selectedSkillIds.isEmpty()) return emptyList()
        val installedSkillsById = _uiState.value.installedSkills
            .filter { it.isEnabled }
            .associateBy { it.id }
        val existingSkillsById = existingActiveSkills.associateBy { it.skillId }
        return buildList {
            selectedSkillIds.distinct().forEach { skillId ->
                val installedSkill = installedSkillsById[skillId] ?: return@forEach
                val refreshedSkill = skillManager.buildActiveSkillContext(installedSkill)
                    .getOrElse { existingSkillsById[skillId] ?: return@forEach }
                add(refreshedSkill)
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
        val enabledServersById = _uiState.value.mcpServers
            .filter { it.isEnabled }
            .associateBy { it.id }
        return selectedServerIds.distinct().mapNotNull(enabledServersById::get)
    }

    private fun setSessionSelectedSkillIds(
        sessionId: String,
        selectedSkillIds: List<String>,
    ) {
        updateSession(sessionId) { session ->
            if (session.selectedSkillIds == selectedSkillIds) {
                null
            } else {
                session.copy(selectedSkillIds = selectedSkillIds)
            }
        }
    }

    private fun setSessionActiveSkills(
        sessionId: String,
        activeSkills: List<ActiveSkillContext>,
    ) {
        updateSession(sessionId) { session ->
            if (session.activeSkills == activeSkills) {
                null
            } else {
                session.copy(activeSkills = activeSkills)
            }
        }
    }

    private fun setSessionActiveMcpServerIds(
        sessionId: String,
        activeMcpServerIds: List<String>,
    ) {
        updateSession(sessionId) { session ->
            if (session.activeMcpServerIds == activeMcpServerIds) {
                null
            } else {
                session.copy(activeMcpServerIds = activeMcpServerIds)
            }
        }
    }

    private fun updateSession(
        sessionId: String,
        transform: (ChatSession) -> ChatSession?,
    ) {
        var didUpdate = false
        _uiState.update { current ->
            val sessionIndex = current.sessions.indexOfFirst { it.id == sessionId }
            if (sessionIndex < 0) return@update current
            val updatedSessions = current.sessions.toMutableList()
            val session = updatedSessions.removeAt(sessionIndex)
            val updatedSession = transform(session)
            if (updatedSession == null) {
                updatedSessions.add(sessionIndex, session)
                current
            } else {
                didUpdate = true
                updatedSessions.add(
                    sessionIndex.coerceAtMost(updatedSessions.size),
                    updatedSession,
                )
                current.copy(sessions = updatedSessions)
            }
        }
        if (didUpdate) {
            persistChats()
        }
    }

    private fun findMcpServerById(serverId: String): McpServerConfig? =
        _uiState.value.mcpServers.firstOrNull { it.id == serverId }

    private fun updateOrderedSelection(
        currentSelection: List<String>,
        id: String,
        selected: Boolean,
    ): List<String> = when {
        selected && currentSelection.contains(id) -> currentSelection
        selected -> currentSelection + id
        else -> currentSelection.filterNot { it == id }
    }

    private fun deriveSessionMetadata(messages: List<ChatMessage>): SessionMetadata {
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

        return SessionMetadata(title = title, preview = preview)
    }

    private fun generateSessionTitle(
        sessionId: String,
        seedMessage: ChatMessage,
        settings: AppSettings,
    ) {
        if (!isProviderSetupValid(settings.provider, settings.apiKey, settings.baseUrl, settings.modelId)) {
            return
        }

        val titleInput = buildTitleGenerationInput(seedMessage)
        if (titleInput.isBlank()) return

        viewModelScope.launch {
            val titleResult = client.createChatCompletion(
                settings = settings,
                systemPrompt = SessionTitleSystemPrompt,
                conversation = listOf(buildProviderUserMessage(settings, titleInput)),
            )
            val title = titleResult.getOrNull()
                ?.assistantText
                ?.sanitizeGeneratedSessionTitle()
                .orEmpty()

            if (title.isBlank()) return@launch

            updateSession(sessionId) { session ->
                val firstUserMessage = session.messages.firstOrNull { it.author == MessageAuthor.User }
                if (firstUserMessage?.id != seedMessage.id) {
                    null
                } else {
                    session.copy(
                        title = title,
                        hasCustomTitle = true,
                    )
                }
            }
        }
    }

    private fun buildTitleGenerationInput(
        message: ChatMessage,
    ): String = buildString {
        val text = message.text.trim()
        if (text.isNotBlank()) {
            appendLine("First user message:")
            appendLine(text)
        }
        if (message.attachments.isNotEmpty()) {
            if (isNotEmpty()) appendLine()
            appendLine("Attachments:")
            message.attachments.forEach { attachment ->
                appendLine("- ${attachment.name}")
            }
        }
    }.trim()

    private fun buildProviderUserMessage(
        settings: AppSettings,
        text: String,
    ): JSONObject = when (settings.provider) {
        LlmProvider.OpenAiCompatible -> JSONObject().apply {
            put("role", "user")
            put("content", text)
        }

        LlmProvider.VertexExpress -> JSONObject().apply {
            put("role", "user")
            put(
                "parts",
                JSONArray().put(
                    JSONObject().apply {
                        put("text", text)
                    }
                ),
            )
        }
    }

    private fun String.sanitizeGeneratedSessionTitle(): String =
        lineSequence()
            .map { line ->
                line.trim()
                    .removePrefix("Title:")
                    .removePrefix("title:")
                    .removePrefix("标题：")
                    .removePrefix("标题:")
                    .trim()
                    .trim('"', '\'', '“', '”', '`')
            }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
            .trimEnd('.', '。', '!', '！', '?', '？')
            .take(36)

    private fun pushPendingToolInvocation(
        sessionId: String,
        toolInvocation: ChatToolInvocation,
    ) {
        _uiState.update { current ->
            current.copy(
                pendingResponseSessionId = sessionId,
                pendingToolInvocations = upsertToolInvocation(
                    current.pendingToolInvocations,
                    toolInvocation,
                ),
            )
        }
    }

    private fun appendPendingAssistantText(
        sessionId: String,
        delta: String,
    ) {
        if (delta.isEmpty()) return

        _uiState.update { current ->
            if (current.pendingResponseSessionId != sessionId) {
                current
            } else {
                current.copy(
                    pendingStatusText = "",
                    pendingAssistantText = current.pendingAssistantText + delta
                )
            }
        }
    }

    private fun clearPendingAssistantText(
        sessionId: String,
    ) {
        _uiState.update { current ->
            if (current.pendingResponseSessionId != sessionId || current.pendingAssistantText.isEmpty()) {
                current
            } else {
                current.copy(pendingAssistantText = "")
            }
        }
    }

    private fun setPendingStatusText(
        sessionId: String,
        status: String?,
    ) {
        _uiState.update { current ->
            if (current.pendingResponseSessionId != sessionId) {
                current
            } else {
                current.copy(pendingStatusText = status.orEmpty())
            }
        }
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
        if (existingIndex < 0) {
            return invocations + normalized
        }

        return invocations.toMutableList().apply {
            set(existingIndex, normalized)
        }
    }

    private fun ensureDraftWorkspaceId(): String {
        val snapshot = _uiState.value
        return snapshot.editingSessionId ?: when {
            snapshot.currentSessionId != DraftSessionId -> snapshot.currentSessionId
            !snapshot.draftWorkspaceId.isNullOrBlank() -> snapshot.draftWorkspaceId.orEmpty()
            else -> {
                val generatedId = "session-${System.currentTimeMillis()}"
                _uiState.update { current ->
                    if (current.currentSessionId == DraftSessionId && current.draftWorkspaceId.isNullOrBlank()) {
                        current.copy(draftWorkspaceId = generatedId)
                    } else {
                        current
                    }
                }
                _uiState.value.draftWorkspaceId ?: generatedId
            }
        }
    }

    private fun clearPendingGenerationState() {
        _uiState.update { current ->
            current.copy(
                isSending = false,
                pendingResponseSessionId = null,
                pendingToolInvocations = emptyList(),
                pendingAssistantText = "",
                pendingStatusText = "",
            )
        }
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
        if (!output.has("stdout")) {
            output.put("stdout", "")
        }
        if (!output.has("stderr")) {
            output.put("stderr", "")
        }
        if (!output.has("exit_code")) {
            output.put("exit_code", 143)
        }
        if (!output.has("err")) {
            output.put("err", -1)
        }
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
        if (rawValue.isBlank()) {
            null
        } else {
            runCatching { JSONObject(rawValue) }.getOrNull()
        }

    private fun isActiveGeneration(requestId: Long): Boolean =
        activeGenerationRequestId == requestId

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

    private fun List<ChatMessage>.resolveConversationTrimIndex(
        targetIndex: Int,
    ): Int {
        val targetMessage = getOrNull(targetIndex) ?: return targetIndex
        val responseGroupId = targetMessage.responseGroupId
        if (
            targetMessage.author != MessageAuthor.Agent ||
            responseGroupId.isNullOrBlank()
        ) {
            return targetIndex
        }
        if (responseGroupId.isNullOrBlank()) {
            return resolveLegacyAssistantGroupStartIndex(targetIndex)
        }
        val groupStartIndex = indexOfFirst { message ->
            message.author == MessageAuthor.Agent && message.responseGroupId == responseGroupId
        }
        return if (groupStartIndex >= 0) groupStartIndex else targetIndex
    }

    private fun List<ChatMessage>.resolveLegacyAssistantGroupStartIndex(
        targetIndex: Int,
    ): Int {
        val targetMessage = getOrNull(targetIndex) ?: return targetIndex
        if (targetMessage.author != MessageAuthor.Agent) return targetIndex
        var groupStartIndex = targetIndex
        var expectedCreatedAtMillis = targetMessage.createdAtMillis
        while (groupStartIndex > 0) {
            val previous = this[groupStartIndex - 1]
            if (
                previous.author != MessageAuthor.Agent ||
                !previous.responseGroupId.isNullOrBlank() ||
                previous.createdAtMillis != expectedCreatedAtMillis - 1
            ) {
                break
            }
            groupStartIndex -= 1
            expectedCreatedAtMillis = previous.createdAtMillis
        }
        return groupStartIndex
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024f * 1024f))
        bytes >= 1024 -> String.format("%.1f KB", bytes / 1024f)
        else -> "$bytes B"
    }

    private fun parseKeyValueLines(rawValue: String): List<com.zhousl.aether.data.McpKeyValue> =
        rawValue.lineSequence()
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) return@mapNotNull null
                val separatorIndex = trimmed.indexOf('=')
                if (separatorIndex <= 0) return@mapNotNull null
                com.zhousl.aether.data.McpKeyValue(
                    key = trimmed.substring(0, separatorIndex).trim(),
                    value = trimmed.substring(separatorIndex + 1).trim(),
                )
            }
            .toList()

    private fun performSkillInstall(
        onComplete: (Boolean) -> Unit = {},
        installBlock: suspend () -> Result<InstalledSkill>,
    ) {
        viewModelScope.launch {
            val result = installBlock()
            result
                .onSuccess { installedSkill ->
                    emitTransientMessage("Installed skill: ${installedSkill.name}")
                }
                .onFailure { throwable ->
                    emitTransientMessage(
                        "Couldn't install skill: ${throwable.userFacingMessage()}"
                    )
                }
            onComplete(result.isSuccess)
        }
    }

    private fun writeTextToUri(
        uri: Uri,
        text: String,
    ): Boolean = runCatching {
        getApplication<Application>().contentResolver.openOutputStream(uri)?.use { output ->
            output.write(text.toByteArray(Charsets.UTF_8))
            output.flush()
        } ?: return false
        true
    }.getOrDefault(false)

    private fun readTextFromUri(uri: Uri): String =
        getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        } ?: error("Unable to read the selected file.")

    private fun buildFullAppExportJson(snapshot: AetherUiState): JSONObject =
        JSONObject().apply {
            put("schemaVersion", 1)
            put("exportType", "app")
            put("exportedAtMillis", System.currentTimeMillis())
            put("settings", snapshot.settings.toJson())
            put("providerConfigs", JSONArray(serializeProviderConfigs(snapshot.providerConfigs)))
            put("sessions", JSONArray(serializeChatSessions(snapshot.sessions)))
            put("currentSessionId", snapshot.currentSessionId)
            put("installedSkills", JSONArray(serializeInstalledSkills(snapshot.installedSkills)))
            put("mcpServers", JSONArray(serializeMcpServerConfigs(snapshot.mcpServers)))
        }

    private fun parseFullAppImport(json: JSONObject): ImportedAppData {
        val sessions = parseChatSessions(json.optJSONArray("sessions")?.toString().orEmpty())
        return ImportedAppData(
            settings = parseImportedSettings(json.optJSONObject("settings")),
            providerConfigs = parseProviderConfigs(json.optJSONArray("providerConfigs")?.toString().orEmpty()),
            sessions = sessions,
            currentSessionId = json.optString("currentSessionId")
                .takeIf { id -> id == DraftSessionId || sessions.any { it.id == id } }
                ?: DraftSessionId,
            installedSkills = parseInstalledSkills(json.optJSONArray("installedSkills")?.toString().orEmpty()),
            mcpServers = parseMcpServerConfigs(json.optJSONArray("mcpServers")?.toString().orEmpty()),
        )
    }

    private fun AppSettings.toJson(): JSONObject = JSONObject().apply {
        put("provider", provider.storageValue)
        put("apiKey", apiKey)
        put("baseUrl", baseUrl)
        put("modelId", modelId)
        put("systemPrompt", systemPrompt)
        put("tavilyApiKey", tavilyApiKey)
        put("llmInactivityReconnectTimeoutSeconds", llmInactivityReconnectTimeoutSeconds)
        put("keepTasksRunningInBackground", keepTasksRunningInBackground)
        put("notifyOnTaskCompletion", notifyOnTaskCompletion)
        put("agentModeAuthorizationEnabled", agentModeAuthorizationEnabled)
        put("agentModeAuthorizationMethod", agentModeAuthorizationMethod.storageValue)
        put("language", language.storageValue)
        put("themeMode", themeMode.storageValue)
        put("onboardingSeenVersion", onboardingSeenVersion)
        put("onboardingCompletedVersion", onboardingCompletedVersion)
    }

    private fun parseImportedSettings(json: JSONObject?): AppSettings {
        if (json == null) return AppSettings()
        val defaults = AppSettings()
        return AppSettings(
            provider = LlmProvider.fromStorage(json.optString("provider")),
            apiKey = json.optString("apiKey", defaults.apiKey),
            baseUrl = json.optString("baseUrl", defaults.baseUrl),
            modelId = json.optString("modelId", defaults.modelId),
            systemPrompt = json.optString("systemPrompt", defaults.systemPrompt),
            tavilyApiKey = json.optString("tavilyApiKey", defaults.tavilyApiKey),
            llmInactivityReconnectTimeoutSeconds = normalizeLlmInactivityReconnectTimeoutSeconds(
                json.optInt(
                    "llmInactivityReconnectTimeoutSeconds",
                    defaults.llmInactivityReconnectTimeoutSeconds,
                )
            ),
            keepTasksRunningInBackground = json.optBoolean(
                "keepTasksRunningInBackground",
                defaults.keepTasksRunningInBackground,
            ),
            notifyOnTaskCompletion = json.optBoolean(
                "notifyOnTaskCompletion",
                defaults.notifyOnTaskCompletion,
            ),
            agentModeAuthorizationEnabled = json.optBoolean(
                "agentModeAuthorizationEnabled",
                defaults.agentModeAuthorizationEnabled,
            ),
            agentModeAuthorizationMethod = AgentModeAuthorizationMethod.fromStorage(
                json.optString("agentModeAuthorizationMethod"),
                defaults.agentModeAuthorizationMethod,
            ),
            language = AppLanguage.fromStorage(
                json.optString("language"),
                defaults.language,
            ),
            themeMode = AppThemeMode.fromStorage(json.optString("themeMode")),
            onboardingSeenVersion = json.optInt("onboardingSeenVersion", defaults.onboardingSeenVersion),
            onboardingCompletedVersion = json.optInt(
                "onboardingCompletedVersion",
                defaults.onboardingCompletedVersion,
            ),
        )
    }

    private fun emitTransientMessage(message: String) {
        _transientMessages.tryEmit(message)
    }

    private fun Throwable.userFacingMessage(): String =
        message?.trim().takeUnless { it.isNullOrBlank() } ?: javaClass.simpleName

    private data class SessionMetadata(
        val title: String,
        val preview: String,
    )

    private data class AttachmentMetadata(
        val displayName: String,
        val mimeType: String,
        val sizeBytes: Long?,
        val kind: AttachmentKind,
    )

    private data class ImportedAppData(
        val settings: AppSettings,
        val providerConfigs: List<LlmProviderConfig>,
        val sessions: List<ChatSession>,
        val currentSessionId: String,
        val installedSkills: List<InstalledSkill>,
        val mcpServers: List<McpServerConfig>,
    )
}
