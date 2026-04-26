package com.zhousl.aether.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.zhousl.aether.data.InstalledSkill
import com.zhousl.aether.data.AppLanguage
import com.zhousl.aether.data.AgentModeDisplayState
import com.zhousl.aether.data.McpServerConfig
import com.zhousl.aether.data.McpTransportConfig
import com.zhousl.aether.data.PendingSessionInput
import com.zhousl.aether.data.SessionExecutionState
import com.zhousl.aether.data.SessionFollowUpMode
import com.zhousl.aether.data.quickActionLabel
import com.zhousl.aether.termux.TermuxSetupState
import com.zhousl.aether.ui.theme.AetherBackground
import com.zhousl.aether.ui.theme.AetherBackgroundGradientTop
import com.zhousl.aether.ui.theme.AetherOnSurface
import com.zhousl.aether.ui.theme.AetherOnPrimary
import com.zhousl.aether.ui.theme.AetherOnSurfaceVariant
import com.zhousl.aether.ui.theme.AetherPrimary
import com.zhousl.aether.ui.theme.AetherScrim
import com.zhousl.aether.ui.theme.AetherSurface
import com.zhousl.aether.ui.theme.AetherSurfaceHigh
import com.zhousl.aether.ui.theme.AetherSurfaceHigher
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import org.json.JSONObject

private sealed interface ConversationListItem {
    val key: String

    data class Message(
        val message: ChatMessage,
    ) : ConversationListItem {
        override val key: String = message.id
    }

    data class AssistantGroup(
        val messages: List<ChatMessage>,
    ) : ConversationListItem {
        override val key: String = messages.firstOrNull()?.responseGroupId
            ?: messages.firstOrNull()?.id
            ?: "assistant-group"
    }
}

private val ConversationTopFadeHeight = 42.dp
private val DrawerOverlayFadeHeight = 18.dp
private val ComposerCardShape = RoundedCornerShape(26.dp)
private val ComposerFocusedCardShape = RoundedCornerShape(28.dp)
private val ChatGptControlShadow = Color(0x14000000)
private val ChatGptComposerShadow = Color(0x18000000)
private val ChatGptPurple = Color(0xFF9B5CFF)
private val ChatGptMotionEasing = CubicBezierEasing(0.22f, 0.84f, 0.18f, 1f)

private fun topOverlayBodyGradient(): Brush = Brush.verticalGradient(
    colorStops = arrayOf(
        0.0f to AetherBackground.copy(alpha = 0.98f),
        0.28f to AetherBackground.copy(alpha = 0.92f),
        0.58f to AetherBackground.copy(alpha = 0.52f),
        0.82f to AetherBackground.copy(alpha = 0.18f),
        1.0f to Color.Transparent,
    )
)

private fun topOverlayTailGradient(): Brush = Brush.verticalGradient(
    colorStops = arrayOf(
        0.0f to AetherBackground.copy(alpha = 0.10f),
        0.42f to AetherBackground.copy(alpha = 0.04f),
        1.0f to Color.Transparent,
    )
)

private fun drawerOverlayBodyGradient(): Brush = Brush.verticalGradient(
    colorStops = arrayOf(
        0.0f to AetherSurface.copy(alpha = 0.94f),
        0.20f to AetherSurface.copy(alpha = 0.86f),
        0.48f to AetherSurface.copy(alpha = 0.54f),
        0.78f to AetherSurface.copy(alpha = 0.18f),
        1.0f to Color.Transparent,
    )
)

private fun drawerOverlayTailGradient(): Brush = Brush.verticalGradient(
    colorStops = arrayOf(
        0.0f to AetherSurface.copy(alpha = 0.18f),
        0.46f to AetherSurface.copy(alpha = 0.06f),
        1.0f to Color.Transparent,
    )
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationScreen(
    messages: List<ChatMessage>,
    workspaceDirectory: String,
    pendingToolInvocations: List<ChatToolInvocation>,
    pendingToolInvocationStateKey: String,
    pendingResponseBlocks: List<AssistantResponseBlock>,
    pendingStatusText: String,
    pendingInputs: List<PendingSessionInput>,
    inputValue: String,
    draftAttachments: List<ChatAttachment>,
    availableSkills: List<InstalledSkill>,
    availableMcpServers: List<McpServerConfig>,
    selectedSkillIds: List<String>,
    selectedMcpServerIds: List<String>,
    agentModeAvailable: Boolean,
    agentModeSelected: Boolean,
    agentModeDisplayState: AgentModeDisplayState,
    isEditing: Boolean,
    termuxSetupState: TermuxSetupState,
    showResumeSetupBanner: Boolean,
    showStarterPromptHint: Boolean,
    showTermuxSetupNotice: Boolean,
    onInputChanged: (String) -> Unit,
    onRemoveDraftAttachment: (String) -> Unit,
    onSetSkillSelected: (String, Boolean) -> Unit,
    onSetMcpServerSelected: (String, Boolean) -> Unit,
    onSetAgentModeSelected: (Boolean) -> Unit,
    onCancelEdit: () -> Unit,
    onSend: () -> Unit,
    onQueueFollowUp: () -> Unit,
    onSteerFollowUp: () -> Unit,
    onMenu: () -> Unit,
    onNewChat: () -> Unit,
    onPickImages: () -> Unit,
    onPickFiles: () -> Unit,
    onSaveAttachment: (ChatAttachment) -> Unit,
    onOpenLink: (String) -> Unit,
    onEditMessage: (String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onRedoAgentMessage: (String) -> Unit,
    onRetryUserMessage: (String) -> Unit,
    onSwitchUserMessageBranch: (String, Int) -> Unit,
    onCopyMessage: (ChatMessage) -> Unit,
    onRequestTermuxPermission: () -> Unit,
    onOpenAppPermissions: () -> Unit,
    onOpenTermuxSettings: () -> Unit,
    onOpenTermux: () -> Unit,
    onInstallTermux: () -> Unit,
    onRefreshTermuxSetup: () -> Unit,
    onPauseGeneration: () -> Unit,
    onResumeOnboarding: () -> Unit,
    onDismissStarterPromptHint: () -> Unit,
    isSending: Boolean,
) {
    val listState = rememberLazyListState()
    val conversationItems = remember(messages) { buildConversationListItems(messages) }
    val bottomAnchorRequester = remember { BringIntoViewRequester() }
    var previewAttachment by remember { mutableStateOf<ChatAttachment?>(null) }
    var shouldAutoFollow by rememberSaveable { mutableStateOf(true) }
    var topBarBodyHeightPx by remember { mutableIntStateOf(0) }
    var composerBodyHeightPx by remember { mutableIntStateOf(0) }
    var composerFocused by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val fallbackTopBarBodyHeight = with(density) {
        WindowInsets.statusBars.getTop(this).toDp() + 68.dp
    }
    val topBarBodyHeight = with(density) {
        if (topBarBodyHeightPx > 0) topBarBodyHeightPx.toDp() else fallbackTopBarBodyHeight
    }
    val composerBodyHeight = with(density) {
        if (composerBodyHeightPx > 0) composerBodyHeightPx.toDp() else 112.dp
    }
    val imeBottom = with(density) {
        WindowInsets.ime.getBottom(this).toDp()
    }
    val animatedImeBottom by animateDpAsState(
        targetValue = imeBottom,
        animationSpec = tween(durationMillis = 260, easing = ChatGptMotionEasing),
        label = "conversation_empty_ime_bottom",
    )
    val conversationScrollConnection = remember(listState) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.UserInput) {
                    shouldAutoFollow = listState.isAtConversationBottom()
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                shouldAutoFollow = listState.isAtConversationBottom()
                return Velocity.Zero
            }
        }
    }

    LaunchedEffect(listState, shouldAutoFollow) {
        if (!shouldAutoFollow) return@LaunchedEffect
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            listOf(
                layoutInfo.totalItemsCount,
                lastVisibleItem?.index ?: -1,
                lastVisibleItem?.offset ?: 0,
                lastVisibleItem?.size ?: 0,
            )
        }
            .distinctUntilChanged()
            .collect {
                if (shouldAutoFollow && listState.layoutInfo.totalItemsCount > 0) {
                    bottomAnchorRequester.bringIntoView()
                }
            }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = AetherBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(AetherBackgroundGradientTop, AetherBackground, AetherSurface)
                    )
                )
                .padding(innerPadding)
        ) {
            if (messages.isEmpty()) {
                ConversationEmptyState(
                    modifier = Modifier.padding(
                        top = topBarBodyHeight + 20.dp,
                        bottom = composerBodyHeight + animatedImeBottom + 16.dp,
                    ),
                    inputFocused = composerFocused,
                    showResumeSetupBanner = showResumeSetupBanner,
                    onResumeOnboarding = onResumeOnboarding,
                    onStarterPromptSelected = onInputChanged,
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(conversationScrollConnection),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = topBarBodyHeight + 10.dp,
                        bottom = composerBodyHeight + 28.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    items(conversationItems, key = { it.key }) { item ->
                        when (item) {
                            is ConversationListItem.Message -> {
                                val message = item.message
                                ConversationMessageBubble(
                                    message = message,
                                    actionsEnabled = !isSending,
                                    workspaceDirectory = workspaceDirectory,
                                    onOpenAttachment = { previewAttachment = it },
                                    onOpenLink = onOpenLink,
                                    onEdit = { onEditMessage(message.id) },
                                    onDelete = { onDeleteMessage(message.id) },
                                    onCopy = { onCopyMessage(message) },
                                    onRedo = { onRedoAgentMessage(message.id) },
                                    onRetry = { onRetryUserMessage(message.id) },
                                    onSwitchBranch = { delta -> onSwitchUserMessageBranch(message.id, delta) },
                                )
                            }

                            is ConversationListItem.AssistantGroup -> {
                                val lastMessage = item.messages.last()
                                ConversationAssistantGroupBubble(
                                    messages = item.messages,
                                    actionsEnabled = !isSending,
                                    workspaceDirectory = workspaceDirectory,
                                    onOpenAttachment = { previewAttachment = it },
                                    onOpenLink = onOpenLink,
                                    onCopy = {
                                        onCopyMessage(
                                            lastMessage.copy(
                                                text = item.messages.joinToString("\n\n") { message -> message.text }
                                                    .trim(),
                                            )
                                        )
                                    },
                                    onRedo = { onRedoAgentMessage(lastMessage.id) },
                                    onDelete = { onDeleteMessage(lastMessage.id) },
                                )
                            }
                        }
                    }
                    items(pendingInputs, key = { it.id }) { pendingInput ->
                        PendingSessionInputBubble(pendingInput = pendingInput)
                    }
                    if (pendingResponseBlocks.isNotEmpty() || pendingToolInvocations.isNotEmpty() || isSending) {
                        item(key = "pending-generation-block") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateContentSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                PendingAssistantTimeline(
                                    blocks = pendingResponseBlocks,
                                    workspaceDirectory = workspaceDirectory,
                                    onOpenLink = onOpenLink,
                                    pendingToolInvocationStateKey = pendingToolInvocationStateKey,
                                    pendingToolInvocations = pendingToolInvocations,
                                    agentModeSelected = agentModeSelected,
                                    agentModeDisplayState = agentModeDisplayState,
                                )
                                if (isSending) {
                                    if (pendingResponseBlocks.isEmpty() && pendingStatusText.isNotBlank()) {
                                        ShimmerStatusText(
                                            text = pendingStatusText,
                                            modifier = Modifier.padding(top = 6.dp),
                                        )
                                    } else if (pendingResponseBlocks.isEmpty()) {
                                        ConversationThinkingIndicator()
                                    } else if (pendingStatusText.isNotBlank()) {
                                        ShimmerStatusText(
                                            text = pendingStatusText,
                                            modifier = Modifier.padding(top = 6.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item(key = "conversation-bottom-anchor") {
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .bringIntoViewRequester(bottomAnchorRequester)
                        )
                    }
                }
            }

            ConversationTopOverlay(
                modifier = Modifier.align(Alignment.TopCenter),
                onBodyHeightChanged = { topBarBodyHeightPx = it },
                onMenu = onMenu,
                onNewChat = onNewChat,
            )

            ConversationComposerOverlay(
                modifier = Modifier.align(Alignment.BottomCenter),
                onBodyHeightChanged = { composerBodyHeightPx = it },
                value = inputValue,
                attachments = draftAttachments,
                availableSkills = availableSkills,
                availableMcpServers = availableMcpServers,
                selectedSkillIds = selectedSkillIds,
                selectedMcpServerIds = selectedMcpServerIds,
                agentModeAvailable = agentModeAvailable,
                agentModeSelected = agentModeSelected,
                isEditing = isEditing,
                        termuxSetupState = termuxSetupState,
                        isSending = isSending,
                        showStarterPromptHint = showStarterPromptHint,
                        showTermuxSetupNotice = showTermuxSetupNotice,
                        onValueChange = onInputChanged,
                onRemoveAttachment = onRemoveDraftAttachment,
                onSetSkillSelected = onSetSkillSelected,
                onSetMcpServerSelected = onSetMcpServerSelected,
                onSetAgentModeSelected = onSetAgentModeSelected,
                onCancelEdit = onCancelEdit,
                onPickImages = onPickImages,
                onPickFiles = onPickFiles,
                onRequestTermuxPermission = onRequestTermuxPermission,
                onOpenAppPermissions = onOpenAppPermissions,
                onOpenTermuxSettings = onOpenTermuxSettings,
                onOpenTermux = onOpenTermux,
                onInstallTermux = onInstallTermux,
                        onRefreshTermuxSetup = onRefreshTermuxSetup,
                        onPauseGeneration = onPauseGeneration,
                        onDismissStarterPromptHint = onDismissStarterPromptHint,
                        onFocusChanged = { composerFocused = it },
                onSend = onSend,
                onQueueFollowUp = onQueueFollowUp,
                onSteerFollowUp = onSteerFollowUp,
            )

            previewAttachment?.let { attachment ->
                AttachmentPreviewDialog(
                    attachment = attachment,
                    onDismiss = { previewAttachment = null },
                    onSave = { onSaveAttachment(attachment) },
                )
            }
        }
    }
}

private fun LazyListState.isAtConversationBottom(): Boolean {
    val layoutInfo = layoutInfo
    if (layoutInfo.totalItemsCount == 0) return true
    val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull() ?: return true
    val isLastItemVisible = lastVisibleItem.index == layoutInfo.totalItemsCount - 1
    val distanceFromBottom = layoutInfo.viewportEndOffset - (lastVisibleItem.offset + lastVisibleItem.size)
    return isLastItemVisible && distanceFromBottom >= -32
}

@Composable
private fun ConversationTopOverlay(
    modifier: Modifier = Modifier,
    onBodyHeightChanged: (Int) -> Unit,
    onMenu: () -> Unit,
    onNewChat: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(topOverlayBodyGradient())
                .onSizeChanged { onBodyHeightChanged(it.height) },
        ) {
            ConversationTopBar(
                onMenu = onMenu,
                onNewChat = onNewChat,
            )
        }
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(ConversationTopFadeHeight)
                .background(topOverlayTailGradient())
        )
    }
}

@Composable
private fun ConversationTopBar(
    modifier: Modifier = Modifier,
    onMenu: () -> Unit,
    onNewChat: () -> Unit,
) {
    val strings = rememberAetherStrings()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 15.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderCircleButton(
            icon = Icons.Rounded.Menu,
            contentDescription = strings.menu,
            onClick = onMenu,
            size = 44.dp,
            containerColor = AetherSurface.copy(alpha = 0.96f),
        )
        HeaderCircleButton(
            icon = LucideIcons.SquarePen,
            contentDescription = strings.newChat,
            onClick = onNewChat,
            size = 44.dp,
            containerColor = AetherSurface.copy(alpha = 0.96f),
        )
    }
}

@Composable
private fun ConversationEmptyState(
    modifier: Modifier = Modifier,
    inputFocused: Boolean,
    showResumeSetupBanner: Boolean,
    onResumeOnboarding: () -> Unit,
    onStarterPromptSelected: (String) -> Unit,
) {
    val strings = rememberAetherStrings()
    val titleOffset by animateDpAsState(
        targetValue = if (inputFocused) (-34).dp else (-24).dp,
        animationSpec = tween(durationMillis = 260, easing = ChatGptMotionEasing),
        label = "empty_state_title_offset",
    )
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 26.dp)
            .offset(y = titleOffset),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (showResumeSetupBanner) {
            ResumeSetupCard(onResumeOnboarding = onResumeOnboarding)
            Spacer(modifier = Modifier.height(22.dp))
        }
        Text(
            text = strings.whatCanIHelpWith,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                lineHeight = 30.sp,
                letterSpacing = (-0.2).sp,
            ),
            color = AetherOnSurface,
        )
        Spacer(modifier = Modifier.height(26.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EmptyStateChip(
                    icon = Icons.Rounded.Image,
                    label = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "分析图片" else "Analyze image",
                    iconTint = Color(0xFF38A961),
                    onClick = { onStarterPromptSelected("Analyze this image and describe the important details.") },
                )
                EmptyStateChip(
                    icon = Icons.Rounded.Terminal,
                    label = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "代码" else "Code",
                    iconTint = Color(0xFF7D70DD),
                    onClick = { onStarterPromptSelected("Help me write or debug this code: ") },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EmptyStateChip(
                    icon = Icons.Rounded.AutoAwesome,
                    label = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "帮我写" else "Help me write",
                    iconTint = Color(0xFFE48AAE),
                    onClick = { onStarterPromptSelected("Help me write a clear, polished message about ") },
                )
                EmptyStateChip(
                    icon = Icons.Rounded.AttachFile,
                    label = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "总结文件" else "Summarize file",
                    iconTint = Color(0xFF66C7D4),
                    onClick = { onStarterPromptSelected("Summarize this file and list the key points.") },
                )
            }
        }
    }
}

@Composable
private fun EmptyStateChip(
    icon: ImageVector,
    label: String,
    iconTint: Color,
    onClick: () -> Unit,
) {
    val strings = rememberAetherStrings()
    Row(
        modifier = Modifier
            .shadow(6.dp, RoundedCornerShape(999.dp), ambientColor = ChatGptControlShadow, spotColor = ChatGptControlShadow)
            .clip(RoundedCornerShape(999.dp))
            .background(AetherSurface.copy(alpha = 0.98f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(19.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = AetherOnSurfaceVariant,
        )
    }
}

@Composable
private fun ResumeSetupCard(
    onResumeOnboarding: () -> Unit,
) {
    val strings = rememberAetherStrings()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(24.dp), ambientColor = AetherScrim, spotColor = AetherScrim)
            .clip(RoundedCornerShape(24.dp))
            .background(AetherSurface.copy(alpha = 0.96f))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "完成设置" else "Finish setup",
            style = MaterialTheme.typography.titleMedium,
            color = AetherOnSurface,
        )
        Text(
            text = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "连接模型后返回聊天。" else "Connect a model, then come back to chat.",
            style = MaterialTheme.typography.bodySmall,
            color = AetherOnSurfaceVariant,
        )
        Button(
            onClick = onResumeOnboarding,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AetherPrimary,
                contentColor = AetherOnPrimary,
            ),
        ) {
            Text(if (strings.appLanguage == AppLanguage.SimplifiedChinese) "继续设置" else "Resume setup")
        }
    }
}

@Composable
private fun ConversationThinkingIndicator() {
    val strings = rememberAetherStrings()
    ShimmerStatusText(
        text = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "思考中" else "Thinking",
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun PendingAssistantTimeline(
    blocks: List<AssistantResponseBlock>,
    workspaceDirectory: String,
    onOpenLink: (String) -> Unit,
    pendingToolInvocationStateKey: String,
    pendingToolInvocations: List<ChatToolInvocation>,
    agentModeSelected: Boolean,
    agentModeDisplayState: AgentModeDisplayState,
) {
    if (blocks.isEmpty()) {
        val agentModePreviewVisible =
            pendingToolInvocations.isNotEmpty() &&
                (agentModeSelected ||
                    agentModeDisplayState.isActive ||
                    agentModeDisplayState.latestPreviewPath.isNotBlank())
        if (agentModePreviewVisible) {
            AgentModePreviewPanel(
                displayState = agentModeDisplayState,
                toolInvocation = pendingToolInvocations.lastOrNull(),
            )
        } else if (pendingToolInvocations.isNotEmpty()) {
            ToolInvocationList(
                toolInvocations = pendingToolInvocations,
                stateKey = pendingToolInvocationStateKey,
                autoExpand = true,
            )
        }
        return
    }

    blocks.forEachIndexed { index, block ->
        when (block) {
            is AssistantResponseBlock.Text -> PendingAssistantResponseBlock(
                text = block.text,
                workspaceDirectory = workspaceDirectory,
                onOpenLink = onOpenLink,
            )

            is AssistantResponseBlock.ToolGroup -> {
                val isLastBlock = index == blocks.lastIndex
                val shouldShowAgentModePreview =
                    isLastBlock &&
                        (agentModeSelected ||
                            agentModeDisplayState.isActive ||
                            agentModeDisplayState.latestPreviewPath.isNotBlank()) &&
                        block.toolInvocations.any { it.toolName.equals("agent_display", ignoreCase = true) }
                if (shouldShowAgentModePreview) {
                    AgentModePreviewPanel(
                        displayState = agentModeDisplayState,
                        toolInvocation = block.toolInvocations.lastOrNull(),
                    )
                } else {
                    ToolInvocationList(
                        toolInvocations = block.toolInvocations,
                        stateKey = "$pendingToolInvocationStateKey-${block.id}",
                        autoExpand = isLastBlock,
                    )
                }
            }
        }
    }
}

private fun buildConversationListItems(
    messages: List<ChatMessage>,
): List<ConversationListItem> = buildList {
    var index = 0
    while (index < messages.size) {
        val message = messages[index]
        val responseGroupId = message.responseGroupId
        if (
            message.author == MessageAuthor.Agent &&
            (!responseGroupId.isNullOrBlank() || isLegacyAssistantGroupStart(messages, index))
        ) {
            val groupedMessages = buildList {
                var groupIndex = index
                while (groupIndex < messages.size) {
                    val candidate = messages[groupIndex]
                    if (candidate.author != MessageAuthor.Agent) {
                        break
                    }
                    val matchesGroup = if (!responseGroupId.isNullOrBlank()) {
                        candidate.responseGroupId == responseGroupId
                    } else {
                        val offset = groupIndex - index
                        candidate.responseGroupId.isNullOrBlank() &&
                            candidate.createdAtMillis == message.createdAtMillis + offset
                    }
                    if (!matchesGroup) {
                        break
                    }
                    add(candidate)
                    groupIndex += 1
                }
            }
            if (groupedMessages.isNotEmpty()) {
                add(ConversationListItem.AssistantGroup(groupedMessages))
                index += groupedMessages.size
                continue
            }
        }
        add(ConversationListItem.Message(message))
        index += 1
    }
}

private fun isLegacyAssistantGroupStart(
    messages: List<ChatMessage>,
    index: Int,
): Boolean {
    val message = messages.getOrNull(index) ?: return false
    if (
        message.author != MessageAuthor.Agent ||
        !message.responseGroupId.isNullOrBlank()
    ) {
        return false
    }
    val next = messages.getOrNull(index + 1) ?: return false
    return next.author == MessageAuthor.Agent &&
        next.responseGroupId.isNullOrBlank() &&
        next.createdAtMillis == message.createdAtMillis + 1
}

@Composable
private fun PendingSessionInputBubble(
    pendingInput: PendingSessionInput,
) {
    val strings = rememberAetherStrings()
    val modeLabel = when (pendingInput.mode) {
        SessionFollowUpMode.Queue -> strings.pendingInputModeLabel(true)
        SessionFollowUpMode.Steer -> strings.pendingInputModeLabel(false)
    }
    val attachmentLabel = when (pendingInput.attachmentCount) {
        0 -> null
        else -> strings.attachmentCountLabel(pendingInput.attachmentCount)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .shadow(8.dp, RoundedCornerShape(24.dp), ambientColor = AetherScrim, spotColor = AetherScrim)
                .clip(RoundedCornerShape(24.dp))
                .background(AetherSurfaceHigh.copy(alpha = 0.96f))
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = modeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = AetherOnSurfaceVariant,
                )
                attachmentLabel?.let { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = AetherOnSurfaceVariant,
                    )
                }
            }
            Text(
                text = pendingInput.preview.ifBlank {
                    if (strings.appLanguage == AppLanguage.SimplifiedChinese) "补充上下文" else "Additional context"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = AetherOnSurface,
            )
        }
    }
}

@Composable
private fun ConversationComposerOverlay(
    modifier: Modifier = Modifier,
    onBodyHeightChanged: (Int) -> Unit,
    value: String,
    attachments: List<ChatAttachment>,
    availableSkills: List<InstalledSkill>,
    availableMcpServers: List<McpServerConfig>,
    selectedSkillIds: List<String>,
    selectedMcpServerIds: List<String>,
    agentModeAvailable: Boolean,
    agentModeSelected: Boolean,
    isEditing: Boolean,
    termuxSetupState: TermuxSetupState,
    isSending: Boolean,
    showStarterPromptHint: Boolean,
    showTermuxSetupNotice: Boolean,
    onValueChange: (String) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onSetSkillSelected: (String, Boolean) -> Unit,
    onSetMcpServerSelected: (String, Boolean) -> Unit,
    onSetAgentModeSelected: (Boolean) -> Unit,
    onCancelEdit: () -> Unit,
    onPickImages: () -> Unit,
    onPickFiles: () -> Unit,
    onRequestTermuxPermission: () -> Unit,
    onOpenAppPermissions: () -> Unit,
    onOpenTermuxSettings: () -> Unit,
    onOpenTermux: () -> Unit,
    onInstallTermux: () -> Unit,
    onRefreshTermuxSetup: () -> Unit,
    onPauseGeneration: () -> Unit,
    onDismissStarterPromptHint: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onSend: () -> Unit,
    onQueueFollowUp: () -> Unit,
    onSteerFollowUp: () -> Unit,
) {
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val bottomLift by animateDpAsState(
        targetValue = if (imeVisible) 12.dp else 18.dp,
        animationSpec = tween(durationMillis = 260, easing = ChatGptMotionEasing),
        label = "composer_bottom_lift",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.ime.only(WindowInsetsSides.Bottom))
            .navigationBarsPadding()
            .padding(bottom = bottomLift),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { onBodyHeightChanged(it.height) },
        ) {
            ConversationComposerBar(
                value = value,
                attachments = attachments,
                availableSkills = availableSkills,
                availableMcpServers = availableMcpServers,
            selectedSkillIds = selectedSkillIds,
            selectedMcpServerIds = selectedMcpServerIds,
            agentModeAvailable = agentModeAvailable,
            agentModeSelected = agentModeSelected,
            isEditing = isEditing,
                termuxSetupState = termuxSetupState,
                isSending = isSending,
                showStarterPromptHint = showStarterPromptHint,
                showTermuxSetupNotice = showTermuxSetupNotice,
                onValueChange = onValueChange,
                onRemoveAttachment = onRemoveAttachment,
            onSetSkillSelected = onSetSkillSelected,
            onSetMcpServerSelected = onSetMcpServerSelected,
            onSetAgentModeSelected = onSetAgentModeSelected,
                onCancelEdit = onCancelEdit,
                onPickImages = onPickImages,
                onPickFiles = onPickFiles,
                onRequestTermuxPermission = onRequestTermuxPermission,
                onOpenAppPermissions = onOpenAppPermissions,
                onOpenTermuxSettings = onOpenTermuxSettings,
                onOpenTermux = onOpenTermux,
                onInstallTermux = onInstallTermux,
                onRefreshTermuxSetup = onRefreshTermuxSetup,
                onPauseGeneration = onPauseGeneration,
                onDismissStarterPromptHint = onDismissStarterPromptHint,
                onFocusChanged = onFocusChanged,
                onSend = onSend,
                onQueueFollowUp = onQueueFollowUp,
                onSteerFollowUp = onSteerFollowUp,
            )
        }
    }
}

@Composable
private fun ConversationComposerBar(
    modifier: Modifier = Modifier,
    value: String,
    attachments: List<ChatAttachment>,
    availableSkills: List<InstalledSkill>,
    availableMcpServers: List<McpServerConfig>,
    selectedSkillIds: List<String>,
    selectedMcpServerIds: List<String>,
    agentModeAvailable: Boolean,
    agentModeSelected: Boolean,
    isEditing: Boolean,
    termuxSetupState: TermuxSetupState,
    isSending: Boolean,
    showStarterPromptHint: Boolean,
    showTermuxSetupNotice: Boolean,
    onValueChange: (String) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onSetSkillSelected: (String, Boolean) -> Unit,
    onSetMcpServerSelected: (String, Boolean) -> Unit,
    onSetAgentModeSelected: (Boolean) -> Unit,
    onCancelEdit: () -> Unit,
    onPickImages: () -> Unit,
    onPickFiles: () -> Unit,
    onRequestTermuxPermission: () -> Unit,
    onOpenAppPermissions: () -> Unit,
    onOpenTermuxSettings: () -> Unit,
    onOpenTermux: () -> Unit,
    onInstallTermux: () -> Unit,
    onRefreshTermuxSetup: () -> Unit,
    onPauseGeneration: () -> Unit,
    onDismissStarterPromptHint: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onSend: () -> Unit,
    onQueueFollowUp: () -> Unit,
    onSteerFollowUp: () -> Unit,
) {
    val strings = rememberAetherStrings()
    var attachmentMenuExpanded by remember { mutableStateOf(false) }
    val attachmentMenuVisibility = remember { MutableTransitionState(false) }
    attachmentMenuVisibility.targetState = attachmentMenuExpanded
    var followUpMenuExpanded by remember { mutableStateOf(false) }
    val followUpMenuVisibility = remember { MutableTransitionState(false) }
    followUpMenuVisibility.targetState = followUpMenuExpanded
    var textFieldFocused by remember { mutableStateOf(false) }
    var measuredTextLineCount by remember { mutableIntStateOf(1) }
    var measuredTextHeight by remember { mutableStateOf(22.dp) }
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val selectedSkillSet = remember(selectedSkillIds) { selectedSkillIds.toSet() }
    val selectedMcpServerSet = remember(selectedMcpServerIds) { selectedMcpServerIds.toSet() }
    val selectedSkillActions = remember(availableSkills, selectedSkillSet) {
        availableSkills.filter { selectedSkillSet.contains(it.id) }
    }
    val selectedMcpActions = remember(availableMcpServers, selectedMcpServerSet) {
        availableMcpServers.filter { selectedMcpServerSet.contains(it.id) }
    }
    val hasSelectedActions = selectedSkillActions.isNotEmpty() || selectedMcpActions.isNotEmpty() || agentModeSelected
    val composerPlaceholder = when {
        value.isNotBlank() -> ""
        attachments.isNotEmpty() -> if (strings.appLanguage == AppLanguage.SimplifiedChinese) "添加备注" else "Add a note"
        agentModeSelected && selectedSkillActions.isEmpty() && selectedMcpActions.isEmpty() ->
            if (strings.appLanguage == AppLanguage.SimplifiedChinese) "询问 Agent 模式" else "Ask Agent Mode"
        selectedSkillActions.size + selectedMcpActions.size == 1 && !agentModeSelected -> {
            selectedSkillActions.firstOrNull()?.quickActionLabel()
                ?: selectedMcpActions.firstOrNull()?.quickActionLabel()
                ?: strings.replyToAether
        }
        hasSelectedActions -> if (strings.appLanguage == AppLanguage.SimplifiedChinese) "使用所选工具提问" else "Ask with selected tools"
        else -> strings.askAether
    }
    val hasDraft = value.isNotBlank() || attachments.isNotEmpty()
    val canSendDraft = attachments.all { it.workspaceState == AttachmentWorkspaceState.Ready }
    val showPauseButton = isSending && !hasDraft
    val showSubmitButton = !isSending || hasDraft
    val keepPlusSeparated = value.isNotBlank() || hasSelectedActions
    val plusSeparated = keepPlusSeparated || (textFieldFocused && imeVisible)
    val explicitTextLineCount = if (value.isBlank()) {
        1
    } else {
        value.count { it == '\n' } + 1
    }
    val composerTextLineCount = if (value.isBlank()) {
        1
    } else {
        maxOf(explicitTextLineCount, measuredTextLineCount).coerceIn(1, 5)
    }
    val isMultilineComposer = composerTextLineCount > 1
    val composerTextStyle = MaterialTheme.typography.bodyLarge.copy(
        color = AetherOnSurface,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both,
        ),
    )
    val fieldTopPadding = when {
        hasSelectedActions -> 12.dp
        isMultilineComposer -> 12.dp
        else -> 8.dp
    }
    val fieldBottomPadding = when {
        hasSelectedActions -> 12.dp
        isMultilineComposer -> 12.dp
        else -> 8.dp
    }
    LaunchedEffect(plusSeparated) {
        onFocusChanged(plusSeparated)
    }
    val composerHorizontalPadding by animateDpAsState(
        targetValue = when {
            plusSeparated -> 14.dp
            hasSelectedActions -> 18.dp
            else -> 30.dp
        },
        animationSpec = tween(durationMillis = 260, easing = ChatGptMotionEasing),
        label = "composer_horizontal_padding",
    )
    val fieldStartPadding by animateDpAsState(
        targetValue = if (plusSeparated) 50.dp else 0.dp,
        animationSpec = tween(durationMillis = 260, easing = ChatGptMotionEasing),
        label = "composer_field_start",
    )
    val fieldContentStartPadding by animateDpAsState(
        targetValue = if (plusSeparated) 18.dp else 52.dp,
        animationSpec = tween(durationMillis = 260, easing = ChatGptMotionEasing),
        label = "composer_field_content_start",
    )
    val fieldMinHeight by animateDpAsState(
        targetValue = maxOf(
            if (plusSeparated) 56.dp else 50.dp,
            measuredTextHeight + fieldTopPadding + fieldBottomPadding,
        ),
        animationSpec = tween(durationMillis = 260, easing = ChatGptMotionEasing),
        label = "composer_field_min_height",
    )
    val plusShadowElevation by animateDpAsState(
        targetValue = if (plusSeparated) 10.dp else 0.dp,
        animationSpec = tween(durationMillis = 260, easing = ChatGptMotionEasing),
        label = "composer_plus_shadow",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = composerHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (showTermuxSetupNotice) {
            TermuxSetupNotice(
                setupState = termuxSetupState,
                onRequestPermission = onRequestTermuxPermission,
                onOpenAppPermissions = onOpenAppPermissions,
                onOpenTermuxSettings = onOpenTermuxSettings,
                onOpenTermux = onOpenTermux,
                onInstallTermux = onInstallTermux,
                onRefresh = onRefreshTermuxSetup,
            )
        }
        if (showStarterPromptHint) {
            SurfaceNotice(
                title = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "你的第一条提示已准备好" else "Your first prompt is ready",
                subtitle = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "点击发送来测试 Aether。" else "Tap send to test Aether.",
                actionLabel = strings.hide,
                onAction = onDismissStarterPromptHint,
                actionEnabled = true,
            )
        }
        if (isEditing) {
            SurfaceNotice(
                title = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "正在编辑较早的消息" else "Editing earlier message",
                subtitle = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "发送后会替换它之后的回复。" else "Sending will replace the replies that came after it.",
                actionLabel = strings.cancel,
                onAction = onCancelEdit,
                actionEnabled = true,
            )
        }
        if (attachments.isNotEmpty()) {
            ComposerAttachmentTray(
                attachments = attachments,
                onRemoveAttachment = onRemoveAttachment,
            )
        }

        val fieldShape = if (plusSeparated) ComposerFocusedCardShape else ComposerCardShape
        val fieldControlAlignment = if (isMultilineComposer) Alignment.Bottom else Alignment.CenterVertically
        val fieldTextAlignment = if (isMultilineComposer) Alignment.TopStart else Alignment.CenterStart
        val plusButtonAlignment = if (isMultilineComposer || hasSelectedActions) Alignment.BottomStart else Alignment.CenterStart
        Box(
            modifier = Modifier
                .fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = fieldStartPadding)
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .offset(y = 8.dp)
                        .blur(
                            radius = 22.dp,
                            edgeTreatment = BlurredEdgeTreatment.Unbounded,
                        )
                        .clip(fieldShape)
                        .background(ChatGptComposerShadow),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = fieldMinHeight)
                        .animateContentSize(
                            animationSpec = tween(durationMillis = 320, easing = ChatGptMotionEasing),
                        )
                        .clip(fieldShape)
                        .background(AetherSurface)
                        .padding(
                            start = fieldContentStartPadding,
                            end = 8.dp,
                            top = fieldTopPadding,
                            bottom = fieldBottomPadding,
                        ),
                    verticalArrangement = Arrangement.spacedBy(if (hasSelectedActions) 10.dp else 0.dp),
                ) {
                    AnimatedVisibility(
                        visible = hasSelectedActions,
                        enter = fadeIn(
                            animationSpec = tween(durationMillis = 220, easing = ChatGptMotionEasing),
                        ) + slideInVertically(
                            animationSpec = tween(durationMillis = 280, easing = ChatGptMotionEasing),
                            initialOffsetY = { -it / 2 },
                        ),
                        exit = fadeOut(
                            animationSpec = tween(durationMillis = 160, easing = ChatGptMotionEasing),
                        ) + slideOutVertically(
                            animationSpec = tween(durationMillis = 220, easing = ChatGptMotionEasing),
                            targetOffsetY = { -it / 3 },
                        ),
                    ) {
                        ComposerActionTray(
                            modifier = Modifier.fillMaxWidth(),
                            skills = selectedSkillActions,
                            mcpServers = selectedMcpActions,
                            agentModeSelected = agentModeSelected,
                            onRemoveSkill = { skillId -> onSetSkillSelected(skillId, false) },
                            onRemoveMcpServer = { serverId -> onSetMcpServerSelected(serverId, false) },
                            onRemoveAgentMode = { onSetAgentModeSelected(false) },
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = fieldControlAlignment,
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = measuredTextHeight.coerceAtLeast(22.dp)),
                            contentAlignment = fieldTextAlignment,
                        ) {
                            if (value.isBlank()) {
                                Text(
                                    text = composerPlaceholder,
                                    style = composerTextStyle,
                                    color = Color(0xFF8C8C8C),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            BasicTextField(
                                value = value,
                                onValueChange = onValueChange,
                                enabled = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { focusState ->
                                        if (textFieldFocused != focusState.isFocused) {
                                            textFieldFocused = focusState.isFocused
                                        }
                                    },
                                textStyle = composerTextStyle,
                                maxLines = 5,
                                onTextLayout = { textLayoutResult ->
                                    val lineCount = textLayoutResult.lineCount.coerceIn(1, 5)
                                    if (measuredTextLineCount != lineCount) {
                                        measuredTextLineCount = lineCount
                                    }
                                    val visibleLineBottom = textLayoutResult.getLineBottom(lineCount - 1)
                                    val visibleLineTop = textLayoutResult.getLineTop(0)
                                    val textHeight = with(density) {
                                        (visibleLineBottom - visibleLineTop).toDp()
                                    }
                                    if (measuredTextHeight != textHeight) {
                                        measuredTextHeight = textHeight
                                    }
                                },
                                cursorBrush = SolidColor(AetherOnSurface),
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        if (showPauseButton) {
                            ComposerPauseButton(
                                onClick = onPauseGeneration,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        if (showSubmitButton) {
                            Box {
                                ComposerSubmitButton(
                                    hasDraft = hasDraft,
                                    canSendDraft = canSendDraft,
                                    isSending = isSending,
                                    onClick = {
                                        if (!hasDraft || !canSendDraft) return@ComposerSubmitButton
                                        if (isSending) {
                                            followUpMenuExpanded = true
                                        } else {
                                            onSend()
                                        }
                                    },
                                )
                                if (isSending && (followUpMenuVisibility.currentState || followUpMenuVisibility.targetState)) {
                                    Popup(
                                        alignment = Alignment.BottomEnd,
                                        offset = with(density) {
                                            IntOffset(0, -12.dp.roundToPx())
                                        },
                                        onDismissRequest = { followUpMenuExpanded = false },
                                        properties = PopupProperties(
                                            focusable = true,
                                            dismissOnBackPress = true,
                                            dismissOnClickOutside = true,
                                        ),
                                    ) {
                                        androidx.compose.animation.AnimatedVisibility(
                                            visibleState = followUpMenuVisibility,
                                            enter = fadeIn() +
                                                scaleIn(
                                                    initialScale = 0.92f,
                                                    transformOrigin = TransformOrigin(1f, 1f),
                                                ) +
                                                slideInVertically(initialOffsetY = { it / 10 }),
                                            exit = fadeOut() +
                                                scaleOut(
                                                    targetScale = 0.96f,
                                                    transformOrigin = TransformOrigin(1f, 1f),
                                                ) +
                                                slideOutVertically(targetOffsetY = { it / 12 }),
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .widthIn(min = 252.dp, max = 284.dp)
                                                    .shadow(20.dp, RoundedCornerShape(30.dp), ambientColor = AetherScrim, spotColor = AetherScrim)
                                                    .clip(RoundedCornerShape(30.dp))
                                                    .background(AetherSurface)
                                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                            ) {
                                                ComposerPlusMenuRow(
                                                    title = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "引导当前运行" else "Steer current run",
                                                    icon = Icons.Rounded.AutoAwesome,
                                                    iconTint = Color(0xFF8D6C2F),
                                                    iconContainerColor = Color(0xFFFFF3DE),
                                                    onClick = {
                                                        followUpMenuExpanded = false
                                                        onSteerFollowUp()
                                                    },
                                                )
                                                ComposerPlusMenuRow(
                                                    title = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "排队下一轮" else "Queue next turn",
                                                    icon = Icons.Rounded.ArrowUpward,
                                                    iconTint = Color(0xFF2F6DA3),
                                                    iconContainerColor = Color(0xFFEAF2FF),
                                                    onClick = {
                                                        followUpMenuExpanded = false
                                                        onQueueFollowUp()
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

                Box(
            modifier = Modifier
                .align(plusButtonAlignment)
                .size(48.dp)
                    .shadow(plusShadowElevation, CircleShape, ambientColor = ChatGptControlShadow, spotColor = ChatGptControlShadow)
                    .clip(CircleShape)
                    .background(if (plusSeparated) AetherSurface else Color.Transparent)
                    .clickable(onClick = { attachmentMenuExpanded = !attachmentMenuExpanded }),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "添加附件或工具" else "Add attachment or tool",
                    tint = AetherOnSurface,
                    modifier = Modifier.size(27.dp),
                )
                if (attachmentMenuVisibility.currentState || attachmentMenuVisibility.targetState) {
                    Popup(
                        alignment = Alignment.BottomStart,
                        offset = with(density) {
                            IntOffset(0, -12.dp.roundToPx())
                        },
                        onDismissRequest = { attachmentMenuExpanded = false },
                        properties = PopupProperties(
                            focusable = true,
                            dismissOnBackPress = true,
                            dismissOnClickOutside = true,
                        ),
                    ) {
                        androidx.compose.animation.AnimatedVisibility(
                            visibleState = attachmentMenuVisibility,
                            enter = fadeIn() +
                                scaleIn(
                                    initialScale = 0.92f,
                                    transformOrigin = TransformOrigin(0f, 1f),
                                ) +
                                slideInVertically(initialOffsetY = { it / 10 }),
                            exit = fadeOut() +
                                scaleOut(
                                    targetScale = 0.96f,
                                    transformOrigin = TransformOrigin(0f, 1f),
                                ) +
                                slideOutVertically(targetOffsetY = { it / 12 }),
                        ) {
                            Column(
                                modifier = Modifier
                                    .widthIn(min = 284.dp, max = 304.dp)
                                    .shadow(20.dp, RoundedCornerShape(30.dp), ambientColor = AetherScrim, spotColor = AetherScrim)
                                    .clip(RoundedCornerShape(30.dp))
                                    .background(AetherSurface)
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                ComposerPlusMenuRow(
                                    title = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "照片" else "Photos",
                                    icon = Icons.Rounded.Image,
                                    iconTint = Color(0xFF4E8D5A),
                                    iconContainerColor = AetherSurfaceHigh,
                                    onClick = {
                                        attachmentMenuExpanded = false
                                        onPickImages()
                                    },
                                )
                                ComposerPlusMenuRow(
                                    title = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "文件" else "Files",
                                    icon = Icons.Rounded.AttachFile,
                                    iconTint = AetherOnSurface,
                                    iconContainerColor = AetherSurfaceHigh,
                                    onClick = {
                                        attachmentMenuExpanded = false
                                        onPickFiles()
                                    },
                                )
                                if (agentModeAvailable || availableSkills.isNotEmpty() || availableMcpServers.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                                if (agentModeAvailable) {
                                    ComposerPlusMenuRow(
                                        title = strings.agentMode,
                                        icon = LucideIcons.MousePointer2,
                                        selected = agentModeSelected,
                                        iconTint = Color(0xFF6D5CFF),
                                        iconContainerColor = AetherSurfaceHigh,
                                        onClick = {
                                            attachmentMenuExpanded = false
                                            onSetAgentModeSelected(!agentModeSelected)
                                        },
                                    )
                                }
                                availableSkills.forEach { skill ->
                                    val selected = selectedSkillSet.contains(skill.id)
                                    ComposerPlusMenuRow(
                                        title = skill.quickActionLabel(),
                                        icon = Icons.Rounded.Extension,
                                        selected = selected,
                                        iconTint = Color(0xFF9C6B2F),
                                        iconContainerColor = AetherSurfaceHigh,
                                        onClick = {
                                            attachmentMenuExpanded = false
                                            onSetSkillSelected(skill.id, !selected)
                                        },
                                    )
                                }
                                availableMcpServers.forEach { server ->
                                    val selected = selectedMcpServerSet.contains(server.id)
                                    val isStdIo = server.transport is McpTransportConfig.StdIo
                                    ComposerPlusMenuRow(
                                        title = server.quickActionLabel(),
                                        icon = if (isStdIo) Icons.Rounded.Terminal else Icons.Rounded.Cloud,
                                        selected = selected,
                                        iconTint = if (isStdIo) Color(0xFF2F6DA3) else Color(0xFF2A9C9A),
                                        iconContainerColor = AetherSurfaceHigh,
                                        onClick = {
                                            attachmentMenuExpanded = false
                                            onSetMcpServerSelected(server.id, !selected)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposerPauseButton(
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(ChatGptPurple)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .offset(x = 0.5.dp)
                .size(11.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White)
        )
    }
}

@Composable
private fun ComposerSubmitButton(
    hasDraft: Boolean,
    canSendDraft: Boolean,
    isSending: Boolean,
    onClick: () -> Unit,
) {
    val strings = rememberAetherStrings()
    val enabled = hasDraft && canSendDraft
    val buttonColor = if (enabled) {
        ChatGptPurple
    } else {
        AetherSurfaceHigher
    }
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(buttonColor)
            .clickable(
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.ArrowUpward,
            contentDescription = if (isSending) {
                if (strings.appLanguage == AppLanguage.SimplifiedChinese) "发送跟进" else "Send follow-up"
            } else {
                strings.send
            },
            tint = Color.White,
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
private fun ComposerActionTray(
    modifier: Modifier = Modifier,
    skills: List<InstalledSkill>,
    mcpServers: List<McpServerConfig>,
    agentModeSelected: Boolean,
    onRemoveSkill: (String) -> Unit,
    onRemoveMcpServer: (String) -> Unit,
    onRemoveAgentMode: () -> Unit,
) {
    val strings = rememberAetherStrings()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(end = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (agentModeSelected) {
            ComposerActionChip(
                label = strings.agentMode,
                icon = LucideIcons.MousePointer2,
                onRemove = onRemoveAgentMode,
            )
        }
        skills.forEach { skill ->
            ComposerActionChip(
                label = skill.quickActionLabel(),
                icon = Icons.Rounded.Extension,
                onRemove = { onRemoveSkill(skill.id) },
            )
        }
        mcpServers.forEach { server ->
            ComposerActionChip(
                label = server.quickActionLabel(),
                icon = if (server.transport is McpTransportConfig.StdIo) {
                    Icons.Rounded.Terminal
                } else {
                    Icons.Rounded.Cloud
                },
                onRemove = { onRemoveMcpServer(server.id) },
            )
        }
    }
}

@Composable
private fun AgentModePreviewPanel(
    displayState: AgentModeDisplayState,
    toolInvocation: ChatToolInvocation?,
) {
    val strings = rememberAetherStrings()
    val bitmap = remember(displayState.latestPreviewPath, displayState.lastUpdatedMillis) {
        displayState.latestPreviewPath
            .takeIf { it.isNotBlank() }
            ?.let { BitmapFactory.decodeFile(it) }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(24.dp), ambientColor = AetherScrim, spotColor = AetherScrim)
            .clip(RoundedCornerShape(24.dp))
            .background(AetherSurface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (toolInvocation != null) {
            AgentModePreviewToolStatus(toolInvocation = toolInvocation)
        } else {
            AgentModePreviewHeader(displayState = displayState)
        }
        if (bitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(agentModePreviewBackdropBrush()),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "Agent 模式虚拟显示" else "Agent Mode virtual display",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Fit,
                )
            }
        } else {
            Text(
                text = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "代理启动后会显示虚拟屏幕预览。" else "Virtual display preview will appear after the agent starts.",
                style = MaterialTheme.typography.bodySmall,
                color = AetherOnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AgentModePreviewHeader(
    displayState: AgentModeDisplayState,
) {
    val strings = rememberAetherStrings()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = LucideIcons.MousePointer2,
            contentDescription = null,
            tint = Color(0xFF6D5CFF),
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = strings.agentMode,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = AetherOnSurface,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = displayState.displayId?.let {
                if (strings.appLanguage == AppLanguage.SimplifiedChinese) "显示 $it" else "display $it"
            } ?: if (strings.appLanguage == AppLanguage.SimplifiedChinese) "待机" else "standby",
            style = MaterialTheme.typography.labelSmall,
            color = AetherOnSurfaceVariant,
        )
    }
}

@Composable
private fun AgentModePreviewToolStatus(
    toolInvocation: ChatToolInvocation,
) {
    val label = formatPendingAgentModeToolLabel(toolInvocation)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AetherSurfaceHigh)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = if (toolInvocation.toolName == "agent_display") {
                LucideIcons.MousePointer2
            } else {
                Icons.Rounded.AutoAwesome
            },
            contentDescription = null,
            tint = Color(0xFF5D7CFF),
            modifier = Modifier.size(16.dp),
        )
        if (toolInvocation.isRunning) {
            ShimmerStatusText(
                text = label,
                modifier = Modifier.weight(1f),
                travelDurationMillis = 2600,
                pauseDurationMillis = 900,
            )
        } else {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = AetherOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun formatPendingAgentModeToolLabel(toolInvocation: ChatToolInvocation): String {
    val strings = rememberAetherStrings()
    val arguments = parseJsonObject(toolInvocation.argumentsJson)
    return strings.toolInvocationTitleLabel(
        toolName = toolInvocation.toolName,
        isRunning = toolInvocation.isRunning,
        arguments = arguments,
    )
}

private fun parseJsonObject(rawValue: String): JSONObject? =
    if (rawValue.isBlank()) null else runCatching { JSONObject(rawValue) }.getOrNull()

private fun agentModePreviewBackdropBrush(): Brush = Brush.linearGradient(
    colorStops = arrayOf(
        0.00f to Color(0xFFBEEBFF),
        0.22f to Color(0xFF75C7FF),
        0.44f to Color(0xFFD5E9FF),
        0.68f to Color(0xFF83B5FF),
        1.00f to Color(0xFF4E86F7),
    ),
    start = Offset.Zero,
    end = Offset(900f, 620f),
)

@Composable
private fun ComposerActionChip(
    label: String,
    icon: ImageVector,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .widthIn(max = 220.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFFE8F1FF))
            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF4F8CFF),
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = Color(0xFF2E6FD5),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .size(18.dp)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Remove",
                tint = Color(0xFF4F8CFF),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun ComposerPlusMenuRow(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    iconContainerColor: Color,
    onClick: () -> Unit,
    selected: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(iconContainerColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = AetherOnSurface,
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(AetherPrimary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = AetherPrimary,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

@Composable
fun ConversationDrawer(
    sessions: List<ChatSession>,
    selectedSessionId: String,
    sessionExecutionStates: Map<String, SessionExecutionState>,
    unviewedCompletedSessionIds: Set<String>,
    onNewChat: () -> Unit,
    onSessionSelected: (String) -> Unit,
    onRenameSession: (String, String) -> Unit,
    onExportSession: (ChatSession) -> Unit,
    onDeleteSession: (String) -> Unit,
    onSettingsSelected: () -> Unit,
) {
    val strings = rememberAetherStrings()
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var overlayHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val overlayHeight = with(density) {
        if (overlayHeightPx > 0) overlayHeightPx.toDp() else 132.dp
    }
    val filteredSessions = remember(sessions, searchQuery) {
        val query = searchQuery.trim().lowercase()
        if (query.isBlank()) {
            sessions
        } else {
            sessions.filter { session -> session.title.lowercase().contains(query) }
        }
    }

    ModalDrawerSheet(
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(min = 304.dp, max = 328.dp),
        drawerContainerColor = AetherSurface,
        drawerShape = RoundedCornerShape(topEnd = 30.dp, bottomEnd = 30.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 18.dp)
        ) {
            if (filteredSessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = overlayHeight - DrawerOverlayFadeHeight,
                            bottom = 96.dp,
                        )
                ) {
                    Text(
                        text = if (sessions.isEmpty()) strings.noConversationsYet else strings.noChatsMatchSummary(searchQuery),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AetherOnSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = overlayHeight - DrawerOverlayFadeHeight,
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(filteredSessions, key = { it.id }) { session ->
                        DrawerSessionRow(
                            session = session,
                            selected = session.id == selectedSessionId,
                            indicator = when {
                                sessionExecutionStates[session.id]?.isRunning == true -> DrawerSessionIndicator.Working
                                unviewedCompletedSessionIds.contains(session.id) -> DrawerSessionIndicator.UnviewedComplete
                                else -> DrawerSessionIndicator.None
                            },
                            onClick = {
                                searchExpanded = false
                                searchQuery = ""
                                onSessionSelected(session.id)
                            },
                            onRename = { title -> onRenameSession(session.id, title) },
                            onExport = { onExportSession(session) },
                            onDelete = { onDeleteSession(session.id) },
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(drawerOverlayBodyGradient())
                    .onSizeChanged { overlayHeightPx = it.height }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 18.dp)
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Aether",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = AetherOnSurface,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            HeaderCircleButton(
                                icon = LucideIcons.Search,
                                contentDescription = strings.search,
                                onClick = {
                                    if (searchExpanded || searchQuery.isNotBlank()) {
                                        searchExpanded = false
                                        searchQuery = ""
                                    } else {
                                        searchExpanded = true
                                    }
                                },
                                size = 46.dp,
                                containerColor = AetherSurface.copy(alpha = 0.90f),
                            )
                            HeaderCircleButton(
                                icon = LucideIcons.Settings,
                                contentDescription = strings.settings,
                                onClick = {
                                    searchExpanded = false
                                    searchQuery = ""
                                    onSettingsSelected()
                                },
                                size = 46.dp,
                                containerColor = AetherSurface.copy(alpha = 0.90f),
                            )
                        }
                    }

                    AnimatedVisibility(visible = searchExpanded || searchQuery.isNotBlank()) {
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))
                            DrawerCompactSearchField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(if (searchExpanded || searchQuery.isNotBlank()) 10.dp else 12.dp))
                }

                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(DrawerOverlayFadeHeight)
                        .background(drawerOverlayTailGradient())
                )
            }

            DrawerFloatingChatButton(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 18.dp, bottom = 18.dp),
                onClick = {
                    searchExpanded = false
                    searchQuery = ""
                    onNewChat()
                },
            )
        }
    }
}

private enum class DrawerSessionIndicator {
    None,
    Working,
    UnviewedComplete,
}

@Composable
private fun DrawerCompactSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = rememberAetherStrings()
    Row(
        modifier = modifier
            .shadow(12.dp, RoundedCornerShape(24.dp), ambientColor = AetherScrim, spotColor = AetherScrim)
            .clip(RoundedCornerShape(24.dp))
            .background(AetherSurface)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = LucideIcons.Search,
            contentDescription = null,
            tint = AetherOnSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isBlank()) {
                Text(
                    text = strings.search,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AetherOnSurfaceVariant,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = AetherOnSurface),
                cursorBrush = SolidColor(AetherOnSurface),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerSessionRow(
    session: ChatSession,
    selected: Boolean,
    indicator: DrawerSessionIndicator,
    onClick: () -> Unit,
    onRename: (String) -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val strings = rememberAetherStrings()
    var menuExpanded by remember { mutableStateOf(false) }
    var isRenaming by remember { mutableStateOf(false) }
    var renameFieldHadFocus by remember { mutableStateOf(false) }
    var renameFocusRequest by remember { mutableIntStateOf(0) }
    var titleValue by remember(session.id, session.title) { mutableStateOf(session.title.ifBlank { strings.newChat }) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun commitRename() {
        val trimmed = titleValue.trim()
        if (trimmed.isNotBlank() && trimmed != session.title) {
            onRename(trimmed)
        }
        isRenaming = false
        keyboardController?.hide()
    }

    LaunchedEffect(renameFocusRequest, isRenaming) {
        if (renameFocusRequest > 0 && isRenaming) {
            delay(260)
            if (isRenaming) {
                focusRequester.requestFocus()
                keyboardController?.show()
            }
        }
    }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(if (selected) AetherSurfaceHigh else Color.Transparent)
                .then(
                    if (isRenaming) {
                        Modifier
                    } else {
                        Modifier.combinedClickable(
                            onClick = {
                                onClick()
                            },
                            onLongClick = { menuExpanded = true },
                        )
                    }
                )
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isRenaming) {
                BasicTextField(
                    value = titleValue,
                    onValueChange = { titleValue = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = AetherOnSurface,
                        fontWeight = FontWeight.Medium,
                    ),
                    cursorBrush = SolidColor(AetherOnSurface),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commitRename() }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onFocusChanged { state ->
                            if (state.isFocused) {
                                renameFieldHadFocus = true
                            } else if (renameFieldHadFocus && isRenaming) {
                                commitRename()
                            }
                        },
                )
            } else {
                Text(
                    text = session.title.ifBlank { strings.newChat },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    ),
                    color = AetherOnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            if (indicator != DrawerSessionIndicator.None) {
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            when (indicator) {
                                DrawerSessionIndicator.Working -> Color(0xFF22C55E)
                                DrawerSessionIndicator.UnviewedComplete -> Color(0xFF3B82F6)
                                DrawerSessionIndicator.None -> Color.Transparent
                            }
                        )
                )
            }
        }

        DrawerSessionActionMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            onRename = {
                menuExpanded = false
                titleValue = session.title.ifBlank { strings.newChat }
                renameFieldHadFocus = false
                isRenaming = true
                renameFocusRequest += 1
            },
            onExport = {
                menuExpanded = false
                onExport()
            },
            onDelete = {
                menuExpanded = false
                onDelete()
            },
        )
    }
}

@Composable
private fun DrawerSessionActionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val strings = rememberAetherStrings()
    val menuVisibility = remember { MutableTransitionState(false) }
    menuVisibility.targetState = expanded
    if (!menuVisibility.currentState && !menuVisibility.targetState) return

    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(0, 34),
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visibleState = menuVisibility,
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit = fadeOut() + scaleOut(targetScale = 0.96f),
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 188.dp, max = 220.dp)
                    .shadow(18.dp, RoundedCornerShape(24.dp), ambientColor = AetherScrim, spotColor = AetherScrim)
                    .clip(RoundedCornerShape(24.dp))
                    .background(AetherSurface)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                DrawerSessionActionRow(strings.rename, onRename)
                DrawerSessionActionRow(strings.export, onExport)
                DrawerSessionActionRow(strings.delete, onDelete, destructive = true)
            }
        }
    }
}

@Composable
private fun DrawerSessionActionRow(
    label: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (destructive) Color(0xFFB42318) else AetherOnSurface,
        )
    }
}

private fun pendingInputSummary(
    pendingInputs: List<PendingSessionInput>,
): String? {
    if (pendingInputs.isEmpty()) return null
    val queuedCount = pendingInputs.count { it.mode == SessionFollowUpMode.Queue }
    val steerCount = pendingInputs.count { it.mode == SessionFollowUpMode.Steer }
    return buildList {
        if (queuedCount > 0) {
            add("$queuedCount queued")
        }
        if (steerCount > 0) {
            add("$steerCount steer")
        }
    }.joinToString(" · ")
}

@Composable
private fun DrawerFloatingChatButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val strings = rememberAetherStrings()
    Row(
        modifier = modifier
            .shadow(18.dp, RoundedCornerShape(999.dp), ambientColor = AetherScrim, spotColor = AetherScrim)
            .clip(RoundedCornerShape(999.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(Color(0xFF7A4DFF), Color(0xFF925BFF)),
                )
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = LucideIcons.SquarePen,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(17.dp),
        )
        Text(
            text = strings.chat,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = Color.White,
        )
    }
}

@Composable
private fun HeaderCircleButton(
    icon: ImageVector? = null,
    iconPainter: Painter? = null,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 44.dp,
    containerColor: Color = Color.White,
    iconTint: Color = AetherOnSurface,
) {
    Box(
        modifier = modifier
            .size(size)
            .shadow(12.dp, CircleShape, ambientColor = AetherScrim, spotColor = AetherScrim)
            .clip(CircleShape)
            .background(if (enabled) containerColor else containerColor.copy(alpha = 0.55f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            iconPainter != null -> Icon(
                painter = iconPainter,
                contentDescription = contentDescription,
                tint = if (enabled) iconTint else iconTint.copy(alpha = 0.4f),
            )

            icon != null -> Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) iconTint else iconTint.copy(alpha = 0.4f),
            )
        }
    }
}
