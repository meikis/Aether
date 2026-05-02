package com.zhousl.aether.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.zhousl.aether.ui.theme.AetherMessageBubble
import com.zhousl.aether.ui.theme.AetherError
import com.zhousl.aether.ui.theme.AetherOnPrimaryContainer
import com.zhousl.aether.ui.theme.AetherOnSurface
import com.zhousl.aether.ui.theme.AetherOnSurfaceVariant
import com.zhousl.aether.ui.theme.AetherOutlineSoft
import com.zhousl.aether.ui.theme.AetherPrimary
import com.zhousl.aether.ui.theme.AetherScrim
import com.zhousl.aether.ui.theme.AetherSecondary
import com.zhousl.aether.ui.theme.AetherSurface
import com.zhousl.aether.ui.theme.AetherSurfaceHigh
import com.zhousl.aether.ui.theme.AetherTertiary
import com.zhousl.aether.data.AppLanguage
import com.zhousl.aether.termux.TermuxContract
import com.zhousl.aether.termux.TermuxSetupIssue
import com.zhousl.aether.termux.TermuxSetupState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.IDN
import java.net.URI
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private const val ToolInvocationCollapseThreshold = 3
private const val ToolTransitionDurationMillis = 360
private const val ToolInvocationAutoExpandDelayMillis = 1_000L
private const val ToolGroupCollapseStageDelayMillis = 180L
private const val StreamingChunkFadeDurationMillis = 400
private const val StreamingInitialChunkFadeDurationMillis = 600
private const val StreamingCjkChunkLength = 1
private const val StreamingFallbackChunkLength = 18
private const val FaviconFetchTimeoutMillis = 3_000
private const val TavilyFallbackDomain = "tavily.com"
private const val DefaultFaviconUserAgent =
    "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Mobile Safari/537.36"
private val ToolTransitionEasing = CubicBezierEasing(0.22f, 0.84f, 0.18f, 1f)
private val ToolGroupIndent = 14.dp
private val TimelineGlyphWidth = 22.dp
private val TimelineIconSize = 18.dp
private val TimelineLineWidth = 2.dp
private val TimelineLineTopGap = 9.dp
private val TimelineLineBottomGap = 0.dp
private val MessageTimestampFormatter = DateTimeFormatter.ofPattern("MMMM d, h:mm a", Locale.US)

@Composable
fun ConversationMessageBubble(
    message: ChatMessage,
    actionsEnabled: Boolean,
    workspaceDirectory: String?,
    allowRootImageRead: Boolean = false,
    onOpenAttachment: (ChatAttachment) -> Unit,
    onOpenLink: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onRedo: () -> Unit,
    onRetry: () -> Unit,
    onSwitchBranch: (Int) -> Unit,
) {
    if (message.author == MessageAuthor.User) {
        UserMessageBlock(
            message = message,
            onOpenAttachment = onOpenAttachment,
            onEdit = onEdit,
            onCopy = onCopy,
            onRetry = onRetry,
            onSwitchBranch = onSwitchBranch,
        )
    } else {
        AssistantMessageBlock(
            message = message,
            actionsEnabled = actionsEnabled,
            showActions = !message.assistantActionsHidden,
            workspaceDirectory = workspaceDirectory,
            allowRootImageRead = allowRootImageRead,
            onOpenAttachment = onOpenAttachment,
            onOpenLink = onOpenLink,
            onCopy = onCopy,
            onRedo = onRedo,
            onDelete = onDelete,
        )
    }
}

@Composable
fun SurfaceNotice(
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
    actionEnabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(24.dp), ambientColor = AetherScrim, spotColor = AetherScrim)
            .clip(RoundedCornerShape(24.dp))
            .background(AetherSurface.copy(alpha = 0.96f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = AetherOnSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AetherOnSurfaceVariant)
        }
        ActionIconLabel(
            icon = Icons.Rounded.Close,
            label = actionLabel,
            enabled = actionEnabled,
            onClick = onAction,
        )
    }
}

@Composable
fun TermuxSetupNotice(
    setupState: TermuxSetupState,
    onRequestPermission: () -> Unit,
    onOpenAppPermissions: () -> Unit,
    onOpenTermuxSettings: () -> Unit,
    onOpenTermux: () -> Unit,
    onInstallTermux: () -> Unit,
    onRefresh: () -> Unit,
    showRefreshAction: Boolean = true,
) {
    if (setupState.isReady) return
    val strings = rememberAetherStrings()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    fun copyTermuxSetupCommand() {
        clipboardManager.setText(AnnotatedString(TermuxContract.ExternalAppsSetupCommand))
        Toast.makeText(
            context,
            if (strings.appLanguage == AppLanguage.SimplifiedChinese) "已复制 Termux 配置命令" else "Termux setup command copied",
            Toast.LENGTH_SHORT,
        ).show()
    }
    fun copyTermuxSetupCommandAndOpenTermux() {
        copyTermuxSetupCommand()
        onOpenTermux()
    }

    val title: String
    val subtitle: String

    when (setupState.issue) {
        TermuxSetupIssue.NotInstalled -> {
            title = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "安装 Termux 以启用 bash" else "Install Termux to enable bash"
            subtitle = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "Aether 使用 Termux 在设备上运行 shell 命令。" else "Aether uses Termux to run shell commands on-device."
        }

        TermuxSetupIssue.PermissionMissing -> {
            title = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "授予 Termux 命令访问权限" else "Grant Termux command access"
            subtitle = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "在系统权限中允许 Aether“在 Termux 环境中运行命令”。" else "Allow Aether to use the \"Run commands in Termux environment\" Android permission."
        }

        TermuxSetupIssue.ExternalAppsDisabled -> {
            title = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "在 Termux 中启用外部应用" else "Enable external apps in Termux"
            subtitle = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "打开 Termux，粘贴 Aether 配置命令，然后返回刷新状态。" else "Open Termux, paste the Aether setup command, then return and refresh."
        }

        TermuxSetupIssue.DispatchFailed -> {
            title = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "完成 Termux 设置" else "Finish Termux setup"
            subtitle = setupState.detail.ifBlank {
                if (strings.appLanguage == AppLanguage.SimplifiedChinese) "打开一次 Termux，并确认其集成设置。" else "Open Termux once and verify its integration settings."
            }
        }

        TermuxSetupIssue.Ready -> return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(24.dp), ambientColor = AetherScrim, spotColor = AetherScrim)
            .clip(RoundedCornerShape(24.dp))
            .background(AetherSurface.copy(alpha = 0.96f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = AetherOnSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AetherOnSurfaceVariant)
            if (setupState.detail.isNotBlank() && setupState.detail != subtitle) {
                Text(setupState.detail, style = MaterialTheme.typography.bodySmall, color = AetherOnSurfaceVariant)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (setupState.issue) {
                TermuxSetupIssue.NotInstalled -> {
                    ActionIconLabel(
                        icon = Icons.AutoMirrored.Rounded.OpenInNew,
                        label = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "安装 Termux" else "Install Termux",
                        enabled = true,
                        onClick = onInstallTermux,
                    )
                    if (showRefreshAction) {
                        ActionIconLabel(
                            icon = Icons.Rounded.Refresh,
                            label = strings.refresh,
                            enabled = true,
                            onClick = onRefresh,
                        )
                    }
                }

                TermuxSetupIssue.PermissionMissing -> {
                    ActionIconLabel(
                        icon = Icons.Rounded.Settings,
                        label = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "授予访问权限" else "Grant access",
                        enabled = true,
                        onClick = onRequestPermission,
                    )
                    ActionIconLabel(
                        icon = Icons.AutoMirrored.Rounded.OpenInNew,
                        label = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "应用设置" else "App settings",
                        enabled = true,
                        onClick = onOpenAppPermissions,
                    )
                }

                TermuxSetupIssue.ExternalAppsDisabled -> {
                    ActionIconLabel(
                        icon = Icons.AutoMirrored.Rounded.OpenInNew,
                        label = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "复制并打开 Termux" else "Copy and Open Termux",
                        enabled = true,
                        onClick = ::copyTermuxSetupCommandAndOpenTermux,
                    )
                    if (showRefreshAction) {
                        ActionIconLabel(
                            icon = Icons.Rounded.Refresh,
                            label = strings.refresh,
                            enabled = true,
                            onClick = onRefresh,
                        )
                    }
                }

                TermuxSetupIssue.DispatchFailed -> {
                    ActionIconLabel(
                        icon = Icons.Rounded.ContentCopy,
                        label = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "复制配置命令" else "Copy setup command",
                        enabled = true,
                        onClick = ::copyTermuxSetupCommand,
                    )
                    ActionIconLabel(
                        icon = Icons.AutoMirrored.Rounded.OpenInNew,
                        label = strings.openTermux,
                        enabled = true,
                        onClick = onOpenTermux,
                    )
                    ActionIconLabel(
                        icon = Icons.Rounded.Settings,
                        label = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "Termux 设置" else "Termux settings",
                        enabled = true,
                        onClick = onOpenTermuxSettings,
                    )
                    if (showRefreshAction) {
                        ActionIconLabel(
                            icon = Icons.Rounded.Refresh,
                            label = strings.refresh,
                            enabled = true,
                            onClick = onRefresh,
                        )
                    }
                }

                TermuxSetupIssue.Ready -> Unit
            }
        }
    }
}

@Composable
fun ComposerAttachmentTray(
    attachments: List<ChatAttachment>,
    onRemoveAttachment: (String) -> Unit,
) {
    val strings = rememberAetherStrings()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(24.dp), ambientColor = AetherScrim, spotColor = AetherScrim)
            .clip(RoundedCornerShape(24.dp))
            .background(AetherSurface.copy(alpha = 0.96f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        attachments.forEach { attachment ->
            if (attachment.kind == AttachmentKind.Image) {
                ComposerImageAttachmentCard(attachment = attachment, onRemove = { onRemoveAttachment(attachment.id) })
            } else {
                ComposerFileAttachmentCard(attachment = attachment, onRemove = { onRemoveAttachment(attachment.id) })
            }
        }
    }
}

@Composable
private fun UserMessageBlock(
    message: ChatMessage,
    onOpenAttachment: (ChatAttachment) -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onRetry: () -> Unit,
    onSwitchBranch: (Int) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var selectTextExpanded by remember { mutableStateOf(false) }
    val branchNavigation = message.branchNavigation()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                UserAttachments(message.attachments, onOpenAttachment)
                if (message.text.isNotBlank()) {
                    UserTextBubble(
                        text = message.text,
                        onLongPress = { menuExpanded = true },
                    )
                }
                if (branchNavigation != null) {
                    UserMessageBranchSwitcher(
                        navigation = branchNavigation,
                        onPrevious = { onSwitchBranch(-1) },
                        onNext = { onSwitchBranch(1) },
                    )
                }
            }

            UserMessageActionDialog(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                timestamp = formatMessageTimestamp(message.createdAtMillis),
                onCopy = {
                    menuExpanded = false
                    onCopy()
                },
                onSelectText = {
                    menuExpanded = false
                    selectTextExpanded = true
                },
                onEdit = {
                    menuExpanded = false
                    onEdit()
                },
                onRetry = {
                    menuExpanded = false
                    onRetry()
                },
            )

            SelectUserMessageTextDialog(
                expanded = selectTextExpanded,
                text = message.text,
                onDismissRequest = { selectTextExpanded = false },
            )
        }
    }
}

@Composable
private fun UserMessageActionDialog(
    expanded: Boolean,
    timestamp: String,
    onDismissRequest: () -> Unit,
    onCopy: () -> Unit,
    onSelectText: () -> Unit,
    onEdit: () -> Unit,
    onRetry: () -> Unit,
) {
    val strings = rememberAetherStrings()
    val menuVisibility = remember { MutableTransitionState(false) }
    menuVisibility.targetState = expanded
    if (!menuVisibility.currentState && !menuVisibility.targetState) return

    val density = LocalDensity.current
    val popupOffset = with(density) {
        IntOffset(x = 0, y = 30.dp.roundToPx())
    }

    Popup(
        alignment = Alignment.TopEnd,
        offset = popupOffset,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        AnimatedVisibility(
            visibleState = menuVisibility,
            enter = fadeIn() +
                scaleIn(
                    initialScale = 0.92f,
                    transformOrigin = TransformOrigin(1f, 0f),
                ) +
                slideInVertically(initialOffsetY = { -it / 10 }),
            exit = fadeOut() +
                scaleOut(
                    targetScale = 0.96f,
                    transformOrigin = TransformOrigin(1f, 0f),
                ) +
                slideOutVertically(targetOffsetY = { -it / 12 }),
        ) {
            Column(
                modifier = Modifier
                    .width(228.dp)
                    .shadow(20.dp, RoundedCornerShape(30.dp), ambientColor = AetherScrim, spotColor = AetherScrim)
                    .clip(RoundedCornerShape(30.dp))
                    .background(AetherSurface)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (timestamp.isNotBlank()) {
                    Text(
                        text = timestamp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = AetherOnSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                UserMessageActionRow(
                    icon = Icons.Rounded.ContentCopy,
                    label = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "复制" else "Copy",
                    onClick = onCopy,
                )
                UserMessageActionRow(
                    icon = Icons.Rounded.Description,
                    label = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "选择文本" else "Select Text",
                    onClick = onSelectText,
                )
                UserMessageActionRow(
                    icon = Icons.Rounded.Edit,
                    label = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "编辑消息" else "Edit Message",
                    onClick = onEdit,
                )
                UserMessageActionRow(
                    icon = Icons.Rounded.Refresh,
                    label = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "重试" else "Retry",
                    onClick = onRetry,
                )
            }
        }
    }
}

@Composable
private fun UserMessageBranchSwitcher(
    navigation: ChatBranchNavigation,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BranchStepButton(
            enabled = navigation.canGoPrevious,
            icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
            onClick = onPrevious,
        )
        Text(
            text = "${navigation.selectedIndex + 1}/${navigation.branchCount}",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = AetherOnSurfaceVariant,
        )
        BranchStepButton(
            enabled = navigation.canGoNext,
            icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            onClick = onNext,
        )
    }
}

@Composable
private fun BranchStepButton(
    enabled: Boolean,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) AetherOnSurfaceVariant else AetherOnSurfaceVariant.copy(alpha = 0.32f),
            modifier = Modifier.size(30.dp),
        )
    }
}

@Composable
private fun UserMessageActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AetherOnSurface,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = AetherOnSurface,
        )
    }
}

@Composable
private fun SelectUserMessageTextDialog(
    expanded: Boolean,
    text: String,
    onDismissRequest: () -> Unit,
) {
    val strings = rememberAetherStrings()
    if (!expanded) return

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .widthIn(max = 520.dp)
                .shadow(22.dp, RoundedCornerShape(28.dp), ambientColor = AetherScrim, spotColor = AetherScrim)
                .clip(RoundedCornerShape(28.dp))
                .background(AetherSurface)
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "选择文本" else "Select Text",
                style = MaterialTheme.typography.titleMedium,
                color = AetherOnSurface,
            )
            SelectionContainer {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = AetherOnSurface,
                )
            }
        }
    }
}

@Composable
private fun UserAttachments(
    attachments: List<ChatAttachment>,
    onOpenAttachment: (ChatAttachment) -> Unit,
) {
    attachments.forEach { attachment ->
        when (attachment.kind) {
            AttachmentKind.Image -> UserImageAttachmentCard(attachment = attachment, onClick = { onOpenAttachment(attachment) })
            AttachmentKind.File -> UserFileAttachmentCard(attachment = attachment, onClick = { onOpenAttachment(attachment) })
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun UserTextBubble(
    text: String,
    onLongPress: () -> Unit,
) {
    Box(
        modifier = Modifier
            .widthIn(max = 300.dp)
            .shadow(10.dp, RoundedCornerShape(24.dp), ambientColor = AetherScrim, spotColor = AetherScrim)
            .clip(RoundedCornerShape(24.dp))
            .background(AetherMessageBubble)
            .combinedClickable(
                onClick = {},
                onLongClick = onLongPress,
            )
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = AetherOnPrimaryContainer,
        )
    }
}

@Composable
private fun AssistantMessageBlock(
    message: ChatMessage,
    actionsEnabled: Boolean,
    showActions: Boolean,
    workspaceDirectory: String?,
    allowRootImageRead: Boolean,
    onOpenAttachment: (ChatAttachment) -> Unit,
    onOpenLink: (String) -> Unit,
    onCopy: () -> Unit,
    onRedo: () -> Unit,
    onDelete: () -> Unit,
) {
    val strings = rememberAetherStrings()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val agentModeFrames = remember(message.toolInvocations) {
            buildAgentModeReplayFrames(message.toolInvocations)
        }
        if (message.reasoningTrace != null) {
            ReasoningTraceStatus(
                trace = message.reasoningTrace,
                onOpenLink = onOpenLink,
            )
        } else {
            message.thoughtDurationMillis?.let { duration ->
                Text(
                    text = if (strings.appLanguage == AppLanguage.SimplifiedChinese) {
                        "鎬濊€冧簡 ${formatThoughtDuration(duration)}"
                    } else {
                        "Thought for ${formatThoughtDuration(duration)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = AetherOnSurfaceVariant,
                )
            }
        }
        if (message.reasoningTrace == null && agentModeFrames.isNotEmpty()) {
            AgentModeReplayPanel(
                frames = agentModeFrames,
                stateKey = "agent-mode-replay-${message.id}",
            )
        } else if (message.reasoningTrace == null) {
            ToolInvocationList(
                toolInvocations = message.toolInvocations,
                stateKey = "message-tools-${message.id}",
            )
        }
        AssistantAttachments(
            attachments = message.attachments,
            onOpenAttachment = onOpenAttachment,
        )
        if (message.text.isNotBlank()) {
            MarkdownContent(
                markdown = message.text,
                workspaceDirectory = workspaceDirectory,
                allowRootImageRead = allowRootImageRead,
                onLinkClick = onOpenLink,
            )
        }
        if (showActions) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistantMessageAction(
                icon = LucideIcons.Copy,
                contentDescription = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "复制回复" else "Copy reply",
                onClick = onCopy,
            )
            AssistantMessageAction(
                icon = LucideIcons.RotateCcw,
                contentDescription = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "重新执行回复" else "Redo reply",
                enabled = actionsEnabled,
                onClick = onRedo,
            )
            AssistantMessageAction(
                icon = LucideIcons.Trash2,
                contentDescription = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "删除回复" else "Delete reply",
                enabled = actionsEnabled,
                onClick = onDelete,
            )
            }
        }
    }
}

@Composable
fun ConversationAssistantGroupBubble(
    messages: List<ChatMessage>,
    actionsEnabled: Boolean,
    workspaceDirectory: String?,
    allowRootImageRead: Boolean = false,
    onOpenAttachment: (ChatAttachment) -> Unit,
    onOpenLink: (String) -> Unit,
    onCopy: () -> Unit,
    onRedo: () -> Unit,
    onDelete: () -> Unit,
) {
    if (messages.isEmpty()) return
    val strings = rememberAetherStrings()
    val thoughtDurationMillis = messages.lastOrNull()?.thoughtDurationMillis
    val hasReasoningTrace = messages.any { it.reasoningTrace != null }
    val showActions = messages.none { it.assistantActionsHidden }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!hasReasoningTrace) thoughtDurationMillis?.let { duration ->
            Text(
                text = if (strings.appLanguage == AppLanguage.SimplifiedChinese) {
                    "思考了 ${formatThoughtDuration(duration)}"
                } else {
                    "Thought for ${formatThoughtDuration(duration)}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = AetherOnSurfaceVariant,
            )
        }
        messages.forEach { message ->
            val agentModeFrames = remember(message.toolInvocations) {
                buildAgentModeReplayFrames(message.toolInvocations)
            }
            if (message.reasoningTrace != null) {
                ReasoningTraceStatus(
                    trace = message.reasoningTrace,
                    onOpenLink = onOpenLink,
                )
            } else if (agentModeFrames.isNotEmpty()) {
                AgentModeReplayPanel(
                    frames = agentModeFrames,
                    stateKey = "agent-mode-replay-${message.id}",
                )
            } else {
                ToolInvocationList(
                    toolInvocations = message.toolInvocations,
                    stateKey = "message-tools-${message.id}",
                )
            }
            AssistantAttachments(
                attachments = message.attachments,
                onOpenAttachment = onOpenAttachment,
            )
            if (message.text.isNotBlank()) {
                MarkdownContent(
                    markdown = message.text,
                    workspaceDirectory = workspaceDirectory,
                    allowRootImageRead = allowRootImageRead,
                    onLinkClick = onOpenLink,
                )
            }
        }
        if (showActions) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistantMessageAction(
                icon = LucideIcons.Copy,
                contentDescription = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "复制回复" else "Copy reply",
                onClick = onCopy,
            )
            AssistantMessageAction(
                icon = LucideIcons.RotateCcw,
                contentDescription = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "重新执行回复" else "Redo reply",
                enabled = actionsEnabled,
                onClick = onRedo,
            )
            AssistantMessageAction(
                icon = LucideIcons.Trash2,
                contentDescription = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "删除回复" else "Delete reply",
                enabled = actionsEnabled,
                onClick = onDelete,
            )
            }
        }
    }
}

@Composable
private fun AgentModeReplayPanel(
    frames: List<AgentModeReplayFrame>,
    stateKey: String,
) {
    val strings = rememberAetherStrings()
    var selectedIndex by rememberSaveable(stateKey, frames.size) {
        mutableStateOf((frames.size - 1).coerceAtLeast(0))
    }
    if (selectedIndex !in frames.indices) {
        selectedIndex = (frames.size - 1).coerceAtLeast(0)
    }
    val frame = frames[selectedIndex]
    val bitmap = remember(frame.previewPath, frame.completedAtUptimeMillis) {
        frame.previewPath
            .takeIf { it.isNotBlank() && File(it).exists() }
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
        AgentModeReplayToolStatus(frame = frame)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(agentModeReplayBackdropBrush()),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "Agent 模式回放帧" else "Agent Mode replay frame",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text(
                    text = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "预览不可用" else "Preview unavailable",
                    style = MaterialTheme.typography.bodySmall,
                    color = AetherOnSurfaceVariant,
                )
            }
        }
        if (frames.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReplayStepButton(
                    enabled = selectedIndex > 0,
                    reverse = true,
                    onClick = { selectedIndex = (selectedIndex - 1).coerceAtLeast(0) },
                )
                Text(
                    text = "${selectedIndex + 1} / ${frames.size}",
                    modifier = Modifier.padding(horizontal = 14.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = AetherOnSurfaceVariant,
                )
                ReplayStepButton(
                    enabled = selectedIndex < frames.lastIndex,
                    reverse = false,
                    onClick = { selectedIndex = (selectedIndex + 1).coerceAtMost(frames.lastIndex) },
                )
            }
        }
    }
}

@Composable
private fun AgentModeReplayToolStatus(
    frame: AgentModeReplayFrame,
) {
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
            imageVector = LucideIcons.MousePointer2,
            contentDescription = null,
            tint = Color(0xFF5D7CFF),
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = frame.toolTitle,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = AetherOnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ReplayStepButton(
    enabled: Boolean,
    reverse: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(if (enabled) AetherSurfaceHigh else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
            contentDescription = null,
            tint = if (enabled) AetherOnSurface else AetherOnSurfaceVariant.copy(alpha = 0.36f),
            modifier = Modifier
                .size(13.dp)
                .graphicsLayer { rotationZ = if (reverse) 180f else 0f },
        )
    }
}

@Composable
fun ToolInvocationList(
    toolInvocations: List<ChatToolInvocation>,
    stateKey: String,
    autoExpand: Boolean = false,
) {
    val strings = rememberAetherStrings()
    if (toolInvocations.isEmpty()) return

    if (toolInvocations.size < ToolInvocationCollapseThreshold) {
        ToolInvocationCardsColumn(
            toolInvocations = toolInvocations,
        )
        return
    }

    val isRunning = toolInvocations.any { it.isRunning }
    var headerVisible by rememberSaveable(stateKey) { mutableStateOf(!autoExpand) }
    var expanded by rememberSaveable(stateKey) { mutableStateOf(autoExpand) }
    var lastAutoExpanded by rememberSaveable(stateKey) { mutableStateOf(autoExpand) }
    LaunchedEffect(autoExpand) {
        if (lastAutoExpanded != autoExpand) {
            if (autoExpand) {
                headerVisible = false
                expanded = true
            } else {
                headerVisible = true
                expanded = true
                delay(ToolGroupCollapseStageDelayMillis)
                expanded = false
            }
            lastAutoExpanded = autoExpand
        }
    }
    val childIndent by animateDpAsState(
        targetValue = if (headerVisible) ToolGroupIndent else 0.dp,
        animationSpec = tween(durationMillis = ToolTransitionDurationMillis, easing = ToolTransitionEasing),
        label = "tool_group_indent",
    )
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(durationMillis = ToolTransitionDurationMillis, easing = ToolTransitionEasing),
        label = "tool_group_arrow_rotation",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimatedVisibility(
            visible = headerVisible,
            enter = fadeIn(
                animationSpec = tween(
                    durationMillis = ToolTransitionDurationMillis - 100,
                    easing = ToolTransitionEasing,
                ),
            ) + expandVertically(
                animationSpec = tween(durationMillis = ToolTransitionDurationMillis, easing = ToolTransitionEasing),
                expandFrom = Alignment.Top,
            ),
            exit = fadeOut(
                animationSpec = tween(durationMillis = 180, easing = FastOutLinearInEasing),
            ) + shrinkVertically(
                animationSpec = tween(durationMillis = 220, easing = FastOutLinearInEasing),
                shrinkTowards = Alignment.Top,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .noRippleClickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isRunning) {
                    ShimmerStatusText(
                        text = strings.toolInvocationGroupTitle(toolInvocations.size, isRunning = true),
                        modifier = Modifier.weight(1f),
                        travelDurationMillis = 2600,
                        pauseDurationMillis = 1000,
                    )
                } else {
                    Text(
                        text = strings.toolInvocationGroupTitle(toolInvocations.size, isRunning = false),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AetherOnSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                    contentDescription = if (expanded) {
                        if (strings.appLanguage == AppLanguage.SimplifiedChinese) "折叠工具" else "Collapse tools"
                    } else {
                        if (strings.appLanguage == AppLanguage.SimplifiedChinese) "展开工具" else "Expand tools"
                    },
                    tint = AetherOnSurfaceVariant,
                    modifier = Modifier
                        .size(14.dp)
                        .graphicsLayer { rotationZ = arrowRotation },
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = tween(durationMillis = ToolTransitionDurationMillis, easing = ToolTransitionEasing),
                expandFrom = Alignment.Top,
            ) + fadeIn(
                animationSpec = tween(
                    durationMillis = ToolTransitionDurationMillis - 90,
                    delayMillis = 40,
                    easing = ToolTransitionEasing,
                ),
            ),
            exit = shrinkVertically(
                animationSpec = tween(durationMillis = 260, easing = FastOutLinearInEasing),
                shrinkTowards = Alignment.Top,
            ) + fadeOut(
                animationSpec = tween(durationMillis = 180, easing = FastOutLinearInEasing),
            ),
        ) {
            ToolInvocationCardsColumn(
                toolInvocations = toolInvocations,
                indent = childIndent,
                topPadding = 4.dp,
            )
        }
    }
}

@Composable
private fun ToolInvocationCardsColumn(
    toolInvocations: List<ChatToolInvocation>,
    indent: Dp = 0.dp,
    topPadding: Dp = 6.dp,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent)
            .animateContentSize(
                animationSpec = tween(durationMillis = ToolTransitionDurationMillis, easing = ToolTransitionEasing),
            ),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        toolInvocations.forEach { toolInvocation ->
            ToolInvocationAnimatedCard(
                toolInvocation = toolInvocation,
                topPadding = topPadding,
            )
        }
    }
}

@Composable
private fun ToolInvocationAnimatedCard(
    toolInvocation: ChatToolInvocation,
    topPadding: Dp,
) {
    var visible by rememberSaveable(toolInvocation.id) { mutableStateOf(false) }
    LaunchedEffect(toolInvocation.id) {
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(
            animationSpec = tween(durationMillis = ToolTransitionDurationMillis, easing = ToolTransitionEasing),
            expandFrom = Alignment.Top,
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = ToolTransitionDurationMillis - 90,
                delayMillis = 30,
                easing = ToolTransitionEasing,
            ),
        ),
    ) {
        ToolInvocationCard(
            toolInvocation = toolInvocation,
            topPadding = topPadding,
        )
    }
}

@Composable
fun PendingAssistantResponseBlock(
    text: String,
    workspaceDirectory: String?,
    allowRootImageRead: Boolean = false,
    onOpenLink: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StreamingMarkdownContent(
            markdown = text,
            workspaceDirectory = workspaceDirectory,
            allowRootImageRead = allowRootImageRead,
            onLinkClick = onOpenLink,
        )
    }
}

@Composable
private fun StreamingMarkdownContent(
    markdown: String,
    workspaceDirectory: String?,
    allowRootImageRead: Boolean,
    onLinkClick: (String) -> Unit,
) {
    var trackedSource by remember { mutableStateOf("") }
    var activeFadeRange by remember { mutableStateOf<IntRange?>(null) }
    var activeFadeDurationMillis by remember { mutableStateOf(StreamingChunkFadeDurationMillis) }
    val fadeProgress = remember { Animatable(1f) }

    LaunchedEffect(markdown) {
        if (markdown.isBlank()) {
            trackedSource = ""
            activeFadeRange = null
            fadeProgress.snapTo(1f)
            return@LaunchedEffect
        }

        if (!markdown.startsWith(trackedSource)) {
            trackedSource = ""
            activeFadeRange = null
            fadeProgress.snapTo(1f)
        }

        val deltaStart = trackedSource.length
        val delta = markdown.removePrefix(trackedSource)
        trackedSource = markdown
        if (delta.isEmpty()) return@LaunchedEffect

        activeFadeRange = deltaStart until markdown.length
        activeFadeDurationMillis = if (deltaStart == 0) {
            StreamingInitialChunkFadeDurationMillis
        } else {
            StreamingChunkFadeDurationMillis
        }
    }

    LaunchedEffect(activeFadeRange, activeFadeDurationMillis) {
        val fadeRange = activeFadeRange ?: return@LaunchedEffect
        if (fadeRange.first > fadeRange.last) {
            fadeProgress.snapTo(1f)
            return@LaunchedEffect
        }

        fadeProgress.snapTo(0f)
        fadeProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = activeFadeDurationMillis, easing = LinearEasing),
        )
    }

    val streamingFrame = remember(markdown, activeFadeRange, fadeProgress.value) {
        val normalizedFadeRange = activeFadeRange?.let { range ->
            val clampedStart = range.first.coerceIn(0, markdown.length)
            val clampedEndExclusive = (range.last + 1).coerceIn(clampedStart, markdown.length)
            if (clampedEndExclusive <= clampedStart) {
                null
            } else {
                clampedStart until clampedEndExclusive
            }
        }

        StreamingMarkdownFrame(
            markdown = markdown,
            fadeSpan = normalizedFadeRange?.let { range ->
                MarkdownFadeSpan(
                    sourceRange = range,
                    alpha = fadeProgress.value.coerceIn(0f, 1f),
                )
            },
        )
    }

    MarkdownContent(
        markdown = streamingFrame.markdown,
        modifier = Modifier.fillMaxWidth(),
        workspaceDirectory = workspaceDirectory,
        allowRootImageRead = allowRootImageRead,
        onLinkClick = onLinkClick,
        fadeSpan = streamingFrame.fadeSpan,
    )
}

private data class StreamingMarkdownFrame(
    val markdown: String,
    val fadeSpan: MarkdownFadeSpan?,
)

private fun extractStreamingRevealUnits(buffer: String): Pair<List<String>, String> {
    if (buffer.isEmpty()) return emptyList<String>() to ""

    val units = mutableListOf<String>()
    var start = 0
    var index = 0

    while (index < buffer.length) {
        val char = buffer[index]
        val nextChar = buffer.getOrNull(index + 1)
        val tokenLength = index - start + 1

        val shouldSplit = when {
            isStreamingTokenBoundary(char) -> true
            char.isWhitespace() && start < index -> true
            isCjkCharacter(char) && tokenLength >= StreamingCjkChunkLength && nextChar != null && isCjkCharacter(nextChar) -> true
            tokenLength >= StreamingFallbackChunkLength &&
                nextChar != null &&
                !nextChar.isWhitespace() &&
                !isStreamingTokenBoundary(nextChar) -> true
            else -> false
        }

        if (shouldSplit) {
            var end = index + 1
            while (end < buffer.length && buffer[end].isWhitespace() && buffer[end] != '\n') {
                end += 1
            }
            units += buffer.substring(start, end)
            start = end
            index = end
            continue
        }

        index += 1
    }

    return units to buffer.substring(start)
}

private fun isStreamingTokenBoundary(char: Char): Boolean =
    isSentenceBoundary(char) ||
        char == ',' ||
        char == '\uFF0C' ||
        char == '\u3001' ||
        char == ')' ||
        char == ']' ||
        char == '}'

private fun isSentenceBoundary(char: Char): Boolean =
    char == '\n' || char == '.' || char == '!' || char == '?' ||
        char == '。' || char == '！' || char == '？' ||
        char == ';' || char == '；' || char == ':' || char == '：'

private fun isCjkCharacter(char: Char): Boolean {
    val block = Character.UnicodeBlock.of(char)
    return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
        block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
        block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
        block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
        block == Character.UnicodeBlock.HIRAGANA ||
        block == Character.UnicodeBlock.KATAKANA ||
        block == Character.UnicodeBlock.HANGUL_SYLLABLES
}

@Composable
fun ShimmerStatusText(
    text: String,
    modifier: Modifier = Modifier,
    travelDurationMillis: Int = 1800,
    pauseDurationMillis: Int = 1000,
) {
    val travelDistance = (280f + text.length * 18f).coerceIn(280f, 760f)
    val sweepHalfWidth = 180f
    val totalDurationMillis = travelDurationMillis + pauseDurationMillis
    val shimmerOffset by rememberInfiniteTransition(label = "status_shimmer").animateFloat(
        initialValue = -travelDistance,
        targetValue = travelDistance,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = totalDurationMillis
                travelDistance at travelDurationMillis using LinearEasing
                travelDistance at totalDurationMillis
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "status_shimmer_offset",
    )
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            AetherOnSurfaceVariant.copy(alpha = 0.42f),
            AetherOnSurface.copy(alpha = 0.96f),
            AetherOnSurfaceVariant.copy(alpha = 0.42f),
        ),
        start = Offset(shimmerOffset - sweepHalfWidth, 0f),
        end = Offset(shimmerOffset + sweepHalfWidth, 0f),
    )

    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(brush = shimmerBrush),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReasoningTraceStatus(
    trace: ReasoningTrace,
    modifier: Modifier = Modifier,
    onOpenLink: (String) -> Unit = {},
) {
    val strings = rememberAetherStrings()
    var sheetVisible by remember(trace.id) { mutableStateOf(false) }
    val latestDetail = remember(trace.latestStatusText, trace.chunks) {
        trace.latestStatusText.ifBlank {
            trace.chunks.lastOrNull { it.detail.isNotBlank() || it.title.isNotBlank() }
            ?.let { chunk -> chunk.detail.ifBlank { chunk.title } }
            .orEmpty()
        }
    }
    val completed = trace.completedAtMillis != null
    val statusText = if (completed) {
        formatReasoningTraceDoneLabel(strings, trace)
    } else {
        latestDetail
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .noRippleClickable { sheetVisible = true },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when {
            completed -> Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = AetherOnSurfaceVariant,
            )

            statusText.isNotBlank() -> ReasoningTypewriterText(
                text = statusText,
                styleColor = AetherOnSurfaceVariant,
            )

            else -> ShimmerStatusText(
                text = "Thinking",
            )
        }
    }

    if (sheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { sheetVisible = false },
            containerColor = AetherSurface,
            contentColor = AetherOnSurface,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 8.dp)
                        .width(56.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(AetherOnSurfaceVariant.copy(alpha = 0.16f))
                )
            },
        ) {
            ReasoningTraceSheetContent(
                trace = trace,
                onOpenLink = onOpenLink,
            )
        }
    }
}

@Composable
private fun ReasoningTypewriterText(
    text: String,
    styleColor: Color,
) {
    var rendered by remember(text) { mutableStateOf("") }
    LaunchedEffect(text) {
        if (text.isBlank()) {
            rendered = ""
            return@LaunchedEffect
        }
        if (!text.startsWith(rendered)) {
            rendered = ""
        }
        while (rendered.length < text.length) {
            val nextEnd = (rendered.length + 3).coerceAtMost(text.length)
            rendered = text.substring(0, nextEnd)
            delay(18)
        }
    }
    Text(
        text = rendered.ifBlank { text },
        style = MaterialTheme.typography.bodyMedium,
        color = styleColor,
    )
}

@Composable
private fun ReasoningTraceSheetContent(
    trace: ReasoningTrace,
    onOpenLink: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 640.dp)
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, top = 10.dp, end = 24.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (trace.hasTimelineContent || trace.completedAtMillis != null) {
            ReasoningTimeline(
                trace = trace,
                onOpenLink = onOpenLink,
            )
        } else {
            RawReasoningPanel(rawText = trace.rawText)
        }
    }
}

@Composable
private fun RawReasoningPanel(
    rawText: String,
) {
    val displayText = remember(rawText) {
        rawText.ifBlank { "Waiting for reasoning..." }.let { text ->
            if (text.length <= 12_000) text else text.take(12_000).trimEnd() + "\n..."
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Raw reasoning",
            style = MaterialTheme.typography.labelMedium,
            color = AetherOnSurfaceVariant,
        )
        SelectionContainer {
            Text(
                text = displayText,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(AetherSurfaceHigh)
                    .padding(14.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = AetherOnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReasoningTimeline(
    trace: ReasoningTrace,
    onOpenLink: (String) -> Unit,
) {
    val timelineItems = remember(trace.chunks, trace.toolInvocations) {
        reasoningTimelineItems(trace)
    }
    val hasDoneChunk = trace.completedAtMillis != null
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        timelineItems.forEachIndexed { index, item ->
            val isLast = !hasDoneChunk && index == timelineItems.lastIndex
            when (item) {
                is ReasoningTimelineItem.Summary -> ReasoningTimelineRow(
                    title = item.chunk.title.ifBlank { "Summarizing reasoning" },
                    detail = when {
                        item.chunk.detail.isNotBlank() -> item.chunk.detail
                        else -> "Preparing a short visible summary."
                    },
                    isLast = isLast,
                )

                is ReasoningTimelineItem.Tool -> ReasoningTimelineToolRow(
                    toolInvocation = item.toolInvocation,
                    isLast = isLast,
                    onOpenLink = onOpenLink,
                )
            }
        }
        if (hasDoneChunk) {
            ReasoningTimelineDoneRow(trace = trace)
        }
    }
}

internal sealed interface ReasoningTimelineItem {
    val sortOrder: Long
    val fallbackOrder: Int

    data class Summary(
        val chunk: ReasoningSummaryChunk,
        override val sortOrder: Long,
        override val fallbackOrder: Int,
    ) : ReasoningTimelineItem

    data class Tool(
        val toolInvocation: ChatToolInvocation,
        override val sortOrder: Long,
        override val fallbackOrder: Int,
    ) : ReasoningTimelineItem
}

internal fun reasoningTimelineItems(trace: ReasoningTrace): List<ReasoningTimelineItem> {
    val visibleChunks = trace.chunks.filter {
        it.title.isNotBlank() || it.detail.isNotBlank() || it.isPending || it.rawText.isNotBlank()
    }
    val items = buildList {
        visibleChunks.forEachIndexed { index, chunk ->
            add(
                ReasoningTimelineItem.Summary(
                    chunk = chunk,
                    sortOrder = chunk.timelineOrder
                        .takeIf { it > 0L }
                        ?: chunk.createdAtMillis.takeIf { it > 0L }
                        ?: Long.MAX_VALUE,
                    fallbackOrder = index,
                )
            )
        }
        trace.toolInvocations.forEachIndexed { index, invocation ->
            add(
                ReasoningTimelineItem.Tool(
                    toolInvocation = invocation,
                    sortOrder = invocation.timelineOrder
                        .takeIf { it > 0L }
                        ?: invocation.startedAtMillis.takeIf { it > 0L }
                        ?: Long.MAX_VALUE,
                    fallbackOrder = visibleChunks.size + index,
                )
            )
        }
    }

    return items.sortedWith(
        compareBy<ReasoningTimelineItem> { it.sortOrder }
            .thenBy { it.fallbackOrder }
    )
}

@Composable
private fun ReasoningTimelineRow(
    title: String,
    detail: String,
    isLast: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TimelineGlyph(isLast = isLast)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 22.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = AetherOnSurface,
            )
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AetherOnSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ReasoningTimelineToolRow(
    toolInvocation: ChatToolInvocation,
    isLast: Boolean,
    onOpenLink: (String) -> Unit,
) {
    val strings = rememberAetherStrings()
    val arguments = remember(toolInvocation.argumentsJson) { parseJsonObject(toolInvocation.argumentsJson) }
    val output = remember(toolInvocation.outputJson) { parseJsonObject(toolInvocation.outputJson) }
    val title = remember(toolInvocation, strings.appLanguage) {
        strings.toolInvocationTitleLabel(toolInvocation.toolName, toolInvocation.isRunning, arguments)
    }
    val webSourceMetadata = remember(toolInvocation.toolName, toolInvocation.argumentsJson, toolInvocation.outputJson) {
        webSourceMetadata(toolInvocation.toolName, arguments, output)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TimelineGlyph(
            icon = reasoningToolIcon(toolInvocation.toolName),
            isLast = isLast,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (toolInvocation.isRunning) {
                ShimmerStatusText(
                    text = title,
                    travelDurationMillis = 3200,
                    pauseDurationMillis = 1000,
                )
            } else {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AetherOnSurface,
                )
            }
            webSourceMetadata?.let { metadata ->
                WebSourcePill(
                    metadata = metadata,
                    onOpenLink = onOpenLink,
                )
            }
        }
    }
}

@Composable
private fun ReasoningTimelineDoneRow(
    trace: ReasoningTrace,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TimelineGlyph(
            icon = Icons.Rounded.CheckCircle,
            isLast = true,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = formatReasoningTraceDoneChunkTitle(trace),
                style = MaterialTheme.typography.bodyMedium,
                color = AetherOnSurface,
            )
            Text(
                text = "Done",
                style = MaterialTheme.typography.bodyMedium,
                color = AetherOnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TimelineGlyph(
    icon: ImageVector? = null,
    isLast: Boolean,
) {
    Box(
        modifier = Modifier
            .width(TimelineGlyphWidth)
            .fillMaxHeight(),
        contentAlignment = Alignment.TopCenter,
    ) {
        if (!isLast) {
            Box(
                modifier = Modifier
                    .padding(
                        top = TimelineIconSize + TimelineLineTopGap,
                        bottom = TimelineLineBottomGap,
                    )
                    .width(TimelineLineWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(999.dp))
                    .background(AetherOnSurfaceVariant.copy(alpha = 0.12f))
            )
        }
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AetherOnSurfaceVariant,
                modifier = Modifier.size(TimelineIconSize),
            )
        } else {
            Box(
                modifier = Modifier
                    .padding(top = 5.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(AetherOnSurfaceVariant)
            )
        }
    }
}

private fun reasoningToolIcon(toolName: String): ImageVector = when (toolName.lowercase()) {
    "bash", "fetch_bash_output", "kill_bash" -> Icons.Rounded.Terminal
    "fetch_web_url", "tavily_search" -> Icons.Rounded.Language
    else -> Icons.Rounded.Build
}

@Composable
private fun WebSourcePill(
    metadata: WebSourceMetadata,
    onOpenLink: (String) -> Unit,
) {
    val favicon = rememberRemoteBitmap(metadata.faviconUrl)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(AetherSurfaceHigh.copy(alpha = 0.72f))
            .noRippleClickable { onOpenLink(metadata.url) }
            .padding(start = 10.dp, top = 7.dp, end = 12.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (favicon != null) {
            Image(
                bitmap = favicon,
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Fit,
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Language,
                contentDescription = null,
                tint = AetherOnSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = metadata.domain,
            style = MaterialTheme.typography.bodyMedium,
            color = AetherOnSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun rememberRemoteBitmap(
    url: String?,
): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, url) {
        value = withContext(Dispatchers.IO) {
            val sourceUrl = url?.takeIf { it.isNotBlank() } ?: return@withContext null
            runCatching {
                val connection = (URL(sourceUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = FaviconFetchTimeoutMillis
                    readTimeout = FaviconFetchTimeoutMillis
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", DefaultFaviconUserAgent)
                    setRequestProperty("Accept", "image/avif,image/webp,image/png,image/svg+xml,image/*;q=0.8,*/*;q=0.5")
                }
                connection.inputStream.use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    return bitmap
}

private fun webSourceMetadata(
    toolName: String,
    arguments: JSONObject?,
    output: JSONObject?,
): WebSourceMetadata? = when (toolName.lowercase()) {
    "fetch_web_url" -> {
        val sourceUrl = output?.optString("final_url").orEmpty()
            .ifBlank { output?.optString("request_url").orEmpty() }
            .ifBlank { arguments?.optString("url").orEmpty() }
        webSourceMetadataFromUrl(sourceUrl)
    }
    "tavily_search" -> tavilySourceMetadata(arguments, output)
    else -> null
}

private fun tavilySourceMetadata(
    arguments: JSONObject?,
    output: JSONObject?,
): WebSourceMetadata {
    val result = output?.optJSONArray("results")?.let { results ->
        (0 until results.length())
            .asSequence()
            .mapNotNull(results::optJSONObject)
            .firstOrNull { it.optString("url").isNotBlank() || it.optString("favicon").isNotBlank() }
    }
    val resultUrl = result?.optString("url").orEmpty()
    val argumentDomain = firstSearchArgumentDomain(arguments)
    val domain = normalizedDomain(resultUrl)
        .ifBlank { argumentDomain }
        .ifBlank { TavilyFallbackDomain }
    val url = normalizedHttpUrl(resultUrl)
        .ifBlank { normalizedHttpUrl(domain) }
        .ifBlank { "https://$TavilyFallbackDomain" }
    val faviconUrl = result?.optString("favicon").orEmpty()
        .takeIf { (it.startsWith("http://") || it.startsWith("https://")) && !it.endsWith(".svg", ignoreCase = true) }
        ?: faviconUrlForDomain(domain)
    return WebSourceMetadata(
        domain = domain,
        url = url,
        faviconUrl = faviconUrl,
    )
}

private fun webSourceMetadataFromUrl(url: String): WebSourceMetadata? {
    val domain = normalizedDomain(url)
    if (domain.isBlank()) return null
    return WebSourceMetadata(
        domain = domain,
        url = normalizedHttpUrl(url).ifBlank { "https://$domain" },
        faviconUrl = faviconUrlForDomain(domain),
    )
}

private fun firstSearchArgumentDomain(arguments: JSONObject?): String {
    val includeDomains = arguments?.optJSONArray("include_domains")
        ?: arguments?.optJSONArray("includeDomains")
    if (includeDomains != null) {
        for (index in 0 until includeDomains.length()) {
            val domain = normalizedDomain(includeDomains.optString(index))
            if (domain.isNotBlank()) return domain
        }
    }

    val query = arguments?.optString("query").orEmpty()
    val domainPattern = Regex("""(?:site:)?([A-Za-z0-9][A-Za-z0-9.-]*\.[A-Za-z]{2,})(?:/[^\s]*)?""")
    return domainPattern.find(query)
        ?.groupValues
        ?.getOrNull(1)
        ?.let(::normalizedDomain)
        .orEmpty()
}

private fun normalizedDomain(urlOrDomain: String): String {
    val trimmed = urlOrDomain.trim()
    if (trimmed.isBlank()) return ""
    val candidate = if (trimmed.contains("://")) trimmed else "https://$trimmed"
    val host = runCatching { URI(candidate).host }
        .getOrNull()
        .orEmpty()
        .ifBlank { trimmed.substringBefore('/').substringBefore('?').substringBefore('#') }
        .trim('.')
    if (host.isBlank()) return ""
    return runCatching { IDN.toUnicode(host) }
        .getOrDefault(host)
        .removePrefix("www.")
        .lowercase(Locale.US)
}

private fun normalizedHttpUrl(urlOrDomain: String): String {
    val trimmed = urlOrDomain.trim()
    if (trimmed.isBlank()) return ""
    val candidate = if (trimmed.contains("://")) trimmed else "https://$trimmed"
    val uri = runCatching { URI(candidate) }.getOrNull() ?: return ""
    val scheme = uri.scheme?.lowercase(Locale.US).orEmpty()
    if (scheme != "http" && scheme != "https") return ""
    val host = uri.host.orEmpty()
    if (host.isBlank()) return ""
    return uri.toString()
}

private fun faviconUrlForDomain(domain: String): String =
    "https://www.google.com/s2/favicons?domain=${Uri.encode(domain)}&sz=64"

@Composable
fun ReconnectingStatusCard(
    text: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    val strings = rememberAetherStrings()
    var expanded by rememberSaveable(text, detail) { mutableStateOf(false) }
    LaunchedEffect(detail) {
        if (detail.isBlank()) {
            expanded = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = tween(durationMillis = ToolTransitionDurationMillis, easing = ToolTransitionEasing),
            )
            .noRippleClickable(enabled = detail.isNotBlank()) { expanded = !expanded },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ShimmerStatusText(text = text)

        AnimatedVisibility(
            visible = expanded && detail.isNotBlank(),
            enter = expandVertically(
                animationSpec = tween(durationMillis = ToolTransitionDurationMillis, easing = ToolTransitionEasing),
                expandFrom = Alignment.Top,
            ) + fadeIn(
                animationSpec = tween(
                    durationMillis = ToolTransitionDurationMillis - 90,
                    delayMillis = 40,
                    easing = ToolTransitionEasing,
                ),
            ),
            exit = shrinkVertically(
                animationSpec = tween(durationMillis = 240, easing = FastOutLinearInEasing),
                shrinkTowards = Alignment.Top,
            ) + fadeOut(
                animationSpec = tween(durationMillis = 160, easing = FastOutLinearInEasing),
            ),
        ) {
            SyntaxHighlightedCodeBlock(
                label = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "错误" else "Error",
                content = remember(detail) { highlightToolResult(detail) },
            )
        }
    }
}

@Composable
fun ToolInvocationCard(
    toolInvocation: ChatToolInvocation,
    topPadding: Dp = 6.dp,
) {
    val strings = rememberAetherStrings()
    val arguments = remember(toolInvocation.argumentsJson) { parseJsonObject(toolInvocation.argumentsJson) }
    val detail = remember(toolInvocation, strings.appLanguage) { formatToolInvocationDetail(strings, toolInvocation) }
    var expanded by rememberSaveable(toolInvocation.id) { mutableStateOf(false) }
    LaunchedEffect(
        toolInvocation.id,
        toolInvocation.isRunning,
        toolInvocation.startedAtUptimeMillis,
        toolInvocation.completedAtUptimeMillis,
    ) {
        if (!toolInvocation.isRunning) {
            expanded = false
            return@LaunchedEffect
        }

        val startedAt = toolInvocation.startedAtUptimeMillis
            .takeIf { it > 0L }
            ?: SystemClock.uptimeMillis()
        val remainingDelayMillis = ToolInvocationAutoExpandDelayMillis -
            (SystemClock.uptimeMillis() - startedAt)
        if (remainingDelayMillis > 0L) {
            delay(remainingDelayMillis)
        }
        expanded = true
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = tween(durationMillis = ToolTransitionDurationMillis, easing = ToolTransitionEasing),
            )
            .noRippleClickable { expanded = !expanded }
            .padding(top = topPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (toolInvocation.isRunning) {
            ShimmerStatusText(
                text = strings.toolInvocationTitleLabel(toolInvocation.toolName, true, arguments),
                travelDurationMillis = 3200,
                pauseDurationMillis = 1000,
            )
        } else {
            Text(
                text = strings.toolInvocationTitleLabel(toolInvocation.toolName, false, arguments),
                style = MaterialTheme.typography.bodyMedium,
                color = AetherOnSurfaceVariant,
            )
        }
        AnimatedVisibility(
            visible = expanded && detail.command.isNotBlank(),
            enter = expandVertically(
                animationSpec = tween(durationMillis = ToolTransitionDurationMillis, easing = ToolTransitionEasing),
                expandFrom = Alignment.Top,
            ) + fadeIn(
                animationSpec = tween(
                    durationMillis = ToolTransitionDurationMillis - 90,
                    delayMillis = 40,
                    easing = ToolTransitionEasing,
                ),
            ),
            exit = shrinkVertically(
                animationSpec = tween(durationMillis = 240, easing = FastOutLinearInEasing),
                shrinkTowards = Alignment.Top,
            ) + fadeOut(
                animationSpec = tween(durationMillis = 160, easing = FastOutLinearInEasing),
            ),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SyntaxHighlightedCodeBlock(
                    label = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "命令" else "Command",
                    content = remember(detail.command) { highlightBashCommand(detail.command) },
                )
                detail.result?.let { result ->
                    SyntaxHighlightedCodeBlock(
                        label = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "结果" else "Result",
                        content = remember(result) { highlightToolResult(result) },
                    )
                }
            }
        }
    }
}

private fun Modifier.noRippleClickable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    clickable(
        enabled = enabled,
        indication = null,
        interactionSource = remember { MutableInteractionSource() },
        onClick = onClick,
    )
}

@Composable
private fun SyntaxHighlightedCodeBlock(
    label: String,
    content: AnnotatedString,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = AetherOnSurfaceVariant,
        )
        SelectionContainer {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AetherSurfaceHigh)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = AetherOnSurface,
                )
            }
        }
    }
}

@Composable
private fun AssistantAttachments(
    attachments: List<ChatAttachment>,
    onOpenAttachment: (ChatAttachment) -> Unit,
) {
    attachments.forEach { attachment ->
        when (attachment.kind) {
            AttachmentKind.Image -> UserImageAttachmentCard(attachment = attachment, onClick = { onOpenAttachment(attachment) })
            AttachmentKind.File -> UserFileAttachmentCard(attachment = attachment, onClick = { onOpenAttachment(attachment) })
        }
    }
}

@Composable
fun AttachmentPreviewDialog(
    attachment: ChatAttachment,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val strings = rememberAetherStrings()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 24.dp)
                .shadow(24.dp, RoundedCornerShape(30.dp), ambientColor = AetherScrim, spotColor = AetherScrim)
                .clip(RoundedCornerShape(30.dp))
            .background(AetherSurface.copy(alpha = 0.98f))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AetherSurfaceHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (attachment.kind == AttachmentKind.Image) Icons.Rounded.Image else Icons.Rounded.Description,
                        contentDescription = null,
                        tint = AetherOnSurface,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = attachment.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = AetherOnSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                    text = formatAttachmentMetaLabel(strings, attachment),
                        style = MaterialTheme.typography.bodySmall,
                        color = AetherOnSurfaceVariant,
                    )
                }
                IconOnlyAction(
                    icon = Icons.Rounded.Close,
                    contentDescription = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "关闭预览" else "Close preview",
                    onClick = onDismiss,
                )
            }

            when (attachment.kind) {
                AttachmentKind.Image -> AttachmentImagePreview(attachment)
                AttachmentKind.File -> AttachmentFilePreview(attachment)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionIconLabel(
                    icon = Icons.Rounded.Download,
                    label = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "保存" else "Save",
                    enabled = true,
                    onClick = onSave,
                )
            }
        }
    }
}

@Composable
private fun AttachmentImagePreview(
    attachment: ChatAttachment,
) {
    val bitmap = rememberAttachmentBitmap(attachment.uri, maxSize = 2200)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp, max = 560.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(AetherSurfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = attachment.name,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Fit,
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Image,
                contentDescription = null,
                tint = AetherOnSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

@Composable
private fun AttachmentFilePreview(
    attachment: ChatAttachment,
) {
    val strings = rememberAetherStrings()
    val preview = rememberAttachmentTextPreview(attachment)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(AetherSurfaceHigh)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (preview == null) {
            Text(
                text = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "此文件类型无法预览。" else "Preview unavailable for this file type.",
                style = MaterialTheme.typography.bodyMedium,
                color = AetherOnSurfaceVariant,
            )
        } else {
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = preview.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AetherOnSurface,
                    )
                }
            }
            if (preview.isTruncated) {
                Text(
                    text = if (strings.appLanguage == AppLanguage.SimplifiedChinese) "为了便于阅读，预览内容已截断。" else "Preview truncated for readability.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AetherOnSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun UserImageAttachmentCard(
    attachment: ChatAttachment,
    onClick: () -> Unit,
) {
    val bitmap = rememberAttachmentBitmap(attachment.uri, maxSize = 1400)
    Box(
        modifier = Modifier
            .widthIn(max = 300.dp)
            .shadow(10.dp, RoundedCornerShape(24.dp), ambientColor = AetherScrim, spotColor = AetherScrim)
            .clip(RoundedCornerShape(24.dp))
            .background(AetherSurface)
            .clickable(onClick = onClick)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = attachment.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .background(AetherSurfaceHigh),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Image,
                    contentDescription = null,
                    tint = AetherOnSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

@Composable
private fun UserFileAttachmentCard(
    attachment: ChatAttachment,
    onClick: () -> Unit,
) {
    val strings = rememberAetherStrings()
    Row(
        modifier = Modifier
            .widthIn(max = 300.dp)
            .shadow(10.dp, RoundedCornerShape(22.dp), ambientColor = AetherScrim, spotColor = AetherScrim)
            .clip(RoundedCornerShape(22.dp))
            .background(AetherSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(AetherSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Description, contentDescription = null, tint = AetherOnSurface)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = attachment.name,
                style = MaterialTheme.typography.labelLarge,
                color = AetherOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                    text = formatAttachmentMetaLabel(strings, attachment),
                style = MaterialTheme.typography.bodySmall,
                color = AetherOnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
            contentDescription = null,
            tint = AetherOnSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun ComposerImageAttachmentCard(
    attachment: ChatAttachment,
    onRemove: () -> Unit,
) {
    val strings = rememberAetherStrings()
    val bitmap = rememberAttachmentBitmap(attachment.uri, maxSize = 600)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AetherSurfaceHigh)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(RoundedCornerShape(16.dp))
            .background(AetherSurface),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = attachment.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(Icons.Rounded.Image, contentDescription = null, tint = AetherOnSurfaceVariant)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = attachment.name,
                style = MaterialTheme.typography.labelLarge,
                color = AetherOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                    text = formatComposerAttachmentMetaLabel(strings, attachment),
                style = MaterialTheme.typography.bodySmall,
                color = AetherOnSurfaceVariant,
            )
        }
        IconOnlyAction(
            icon = Icons.Rounded.Close,
            contentDescription = "Remove attachment",
            onClick = onRemove,
        )
    }
}

@Composable
private fun ComposerFileAttachmentCard(
    attachment: ChatAttachment,
    onRemove: () -> Unit,
) {
    val strings = rememberAetherStrings()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AetherSurfaceHigh)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
            .background(AetherSurface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.AttachFile, contentDescription = null, tint = AetherOnSurface)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = attachment.name,
                style = MaterialTheme.typography.labelLarge,
                color = AetherOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                    text = formatComposerAttachmentMetaLabel(strings, attachment),
                style = MaterialTheme.typography.bodySmall,
                color = AetherOnSurfaceVariant,
            )
        }
        IconOnlyAction(
            icon = Icons.Rounded.Close,
            contentDescription = "Remove attachment",
            onClick = onRemove,
        )
    }
}

@Composable
private fun ActionIconLabel(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (enabled) AetherSurfaceHigh else AetherSurfaceHigh.copy(alpha = 0.45f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) AetherOnSurface else AetherOnSurface.copy(alpha = 0.45f), modifier = Modifier.size(14.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (enabled) AetherOnSurface else AetherOnSurface.copy(alpha = 0.45f))
    }
}

@Composable
private fun IconOnlyAction(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(
                if (enabled) AetherSurface.copy(alpha = 0.72f) else AetherSurface.copy(alpha = 0.35f)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) AetherOnSurfaceVariant else AetherOnSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun AssistantMessageAction(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) AetherOnSurfaceVariant else AetherOnSurfaceVariant.copy(alpha = 0.36f),
            modifier = Modifier.size(14.dp),
        )
    }
}

private fun formatMessageTimestamp(createdAtMillis: Long): String {
    if (createdAtMillis <= 0L) return ""
    return runCatching {
        Instant.ofEpochMilli(createdAtMillis)
            .atZone(ZoneId.systemDefault())
            .format(MessageTimestampFormatter)
    }.getOrDefault("")
}

@Composable
private fun rememberAttachmentBitmap(
    uriString: String,
    maxSize: Int,
): ImageBitmap? {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, uriString, maxSize) {
        value = withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val uri = Uri.parse(uriString)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return@withContext null
            }

            val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, maxSize)
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)?.asImageBitmap()
            }
        }
    }
    return bitmap
}

@Composable
private fun rememberAttachmentTextPreview(
    attachment: ChatAttachment,
): AttachmentTextPreview? {
    val context = LocalContext.current
    val preview by produceState<AttachmentTextPreview?>(initialValue = null, attachment) {
        value = withContext(Dispatchers.IO) {
            if (!isLikelyTextAttachment(attachment)) {
                return@withContext null
            }

            val bytes = readAttachmentBytes(
                resolver = context.contentResolver,
                uriString = attachment.uri,
                byteLimit = 64 * 1024 + 1,
            ) ?: return@withContext null

            val decoded = decodeTextAttachment(bytes) ?: return@withContext null
            val isTruncated = bytes.size > 64 * 1024 || decoded.length > 12_000
            AttachmentTextPreview(
                text = decoded.take(12_000),
                isTruncated = isTruncated,
            )
        }
    }
    return preview
}

private fun readAttachmentBytes(
    resolver: android.content.ContentResolver,
    uriString: String,
    byteLimit: Int,
): ByteArray? = runCatching {
    resolver.openInputStream(Uri.parse(uriString))?.use { inputStream ->
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalRead = 0

        while (true) {
            val read = inputStream.read(buffer)
            if (read <= 0) break

            val remaining = byteLimit - totalRead
            if (remaining <= 0) break

            val writeCount = minOf(read, remaining)
            output.write(buffer, 0, writeCount)
            totalRead += writeCount

            if (totalRead >= byteLimit) break
        }

        output.toByteArray()
    }
}.getOrNull()

private fun decodeTextAttachment(
    bytes: ByteArray,
): String? {
    if (bytes.take(512).any { it == 0.toByte() }) return null

    val decoded = bytes.toString(Charsets.UTF_8)
    val replacementCount = decoded.count { it == '\uFFFD' }
    return if (replacementCount > 12) null else decoded
}

private fun isLikelyTextAttachment(
    attachment: ChatAttachment,
): Boolean {
    val mimeType = attachment.mimeType.lowercase()
    if (
        mimeType.startsWith("text/") ||
        mimeType.contains("json") ||
        mimeType.contains("xml") ||
        mimeType.contains("yaml") ||
        mimeType.contains("csv") ||
        mimeType.contains("javascript")
    ) {
        return true
    }

    val extension = attachment.name.substringAfterLast('.', "").lowercase()
    return extension in setOf(
        "txt",
        "md",
        "json",
        "xml",
        "yaml",
        "yml",
        "csv",
        "log",
        "kt",
        "java",
        "kts",
        "js",
        "ts",
        "tsx",
        "jsx",
        "py",
        "rb",
        "go",
        "rs",
        "c",
        "cc",
        "cpp",
        "h",
        "hpp",
        "html",
        "css",
        "sql",
        "sh",
        "gradle",
        "properties",
    )
}

private fun calculateSampleSize(
    width: Int,
    height: Int,
    maxSize: Int,
): Int {
    var sampleSize = 1
    var currentWidth = width
    var currentHeight = height

    while (currentWidth > maxSize || currentHeight > maxSize) {
        currentWidth /= 2
        currentHeight /= 2
        sampleSize *= 2
    }
    return sampleSize.coerceAtLeast(1)
}

private fun formatThoughtDuration(durationMillis: Long): String {
    val totalSeconds = (durationMillis / 1000f).toInt().coerceAtLeast(1)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes == 0) {
        "${seconds}s"
    } else {
        "${minutes}m ${seconds}s"
    }
}

private fun formatReasoningTraceDoneLabel(
    strings: AetherStrings,
    trace: ReasoningTrace,
): String {
    val startedAt = trace.startedAtMillis.takeIf { it > 0L }
    val endedAt = trace.completedAtMillis ?: System.currentTimeMillis()
    val duration = startedAt?.let { formatThoughtDuration((endedAt - it).coerceAtLeast(1L)) } ?: "0s"
    val toolCount = trace.toolInvocations.size
    return if (toolCount > 0) {
        val toolLabel = if (toolCount == 1) "tool" else "tools"
        if (strings.appLanguage == AppLanguage.SimplifiedChinese) {
            "Thought for $duration and executed $toolCount $toolLabel"
        } else {
            "Thought for $duration and executed $toolCount $toolLabel"
        }
    } else {
        if (strings.appLanguage == AppLanguage.SimplifiedChinese) {
            "Thought for $duration"
        } else {
            "Thought for $duration"
        }
    }
}

private fun formatReasoningTraceDoneChunkTitle(trace: ReasoningTrace): String {
    val startedAt = trace.startedAtMillis.takeIf { it > 0L }
    val endedAt = trace.completedAtMillis ?: System.currentTimeMillis()
    val duration = startedAt?.let { formatThoughtDuration((endedAt - it).coerceAtLeast(1L)) } ?: "0s"
    return "Thought for $duration"
}

private fun formatAttachmentMetaLabel(strings: AetherStrings, attachment: ChatAttachment): String {
    val typeLabel = strings.attachmentTypeLabel(attachment.kind == AttachmentKind.Image)
    val sizeLabel = attachment.sizeBytes?.let(::formatAttachmentSize)
    return listOfNotNull(typeLabel, sizeLabel).joinToString(" | ")
}

private fun formatComposerAttachmentMetaLabel(strings: AetherStrings, attachment: ChatAttachment): String {
    val statusLabel = when (attachment.workspaceState) {
        AttachmentWorkspaceState.Pending -> if (strings.appLanguage == AppLanguage.SimplifiedChinese) "正在复制到工作区" else "Copying to workspace"
        AttachmentWorkspaceState.Failed -> if (strings.appLanguage == AppLanguage.SimplifiedChinese) "工作区复制失败" else "Workspace copy failed"
        AttachmentWorkspaceState.Ready -> null
    }
    return listOfNotNull(
        formatAttachmentMetaLabel(strings, attachment).ifBlank { null },
        statusLabel,
    ).joinToString(" | ")
}

private fun formatToolInvocationTitleLabel(toolInvocation: ChatToolInvocation): String {
    val arguments = parseJsonObject(toolInvocation.argumentsJson)
    return when (toolInvocation.toolName.lowercase()) {
        "bash" -> if (toolInvocation.isRunning) "Executing bash command" else "Executed bash command"
        "fetch_bash_output" -> if (toolInvocation.isRunning) "Fetching bash output" else "Fetched bash output"
        "kill_bash" -> if (toolInvocation.isRunning) "Stopping bash command" else "Stopped bash command"
        "sleep" -> if (toolInvocation.isRunning) "Waiting" else "Waited"
        "read" -> if (toolInvocation.isRunning) "Reading file" else "Read file"
        "edit" -> if (toolInvocation.isRunning) "Editing file" else "Edited file"
        "write" -> if (toolInvocation.isRunning) "Writing file" else "Wrote file"
        "grep" -> if (toolInvocation.isRunning) "Searching files" else "Searched files"
        "find" -> if (toolInvocation.isRunning) "Finding files" else "Found files"
        "ls" -> if (toolInvocation.isRunning) "Listing files" else "Listed files"
        "analyze_image" -> if (toolInvocation.isRunning) "Analyzing image" else "Analyzed image"
        "agent_display" -> formatAgentDisplayTitle(
            isRunning = toolInvocation.isRunning,
            arguments = arguments,
        )
        "tavily_search" -> formatArgumentDrivenTitle(
            isRunning = toolInvocation.isRunning,
            progressiveVerb = "Searching",
            completedVerb = "Searched",
            subject = arguments?.optString("query").orEmpty(),
            fallback = "Tavily search",
        )
        "fetch_web_url" -> formatArgumentDrivenTitle(
            isRunning = toolInvocation.isRunning,
            progressiveVerb = "Fetching",
            completedVerb = "Fetched",
            subject = arguments?.optString("url").orEmpty(),
            fallback = "web page",
        )
        else -> if (toolInvocation.isRunning) {
            "Using ${toolInvocation.toolName}"
        } else {
            "Used ${toolInvocation.toolName}"
        }
    }
}

private fun summarizeToolInvocationCommandLabel(
    toolName: String,
    arguments: JSONObject?,
): String {
    if (arguments == null) return toolName
    return when (toolName.lowercase()) {
        "bash" -> arguments.optString("command").trim()
        "fetch_bash_output" -> "fetch ${arguments.optString("run_id").ifBlank { arguments.optString("runId") }.trim()}"
        "kill_bash" -> "kill ${arguments.optString("run_id").ifBlank { arguments.optString("runId") }.trim()}"
        "sleep" -> "sleep ${arguments.optString("duration_ms").ifBlank { arguments.optString("durationMs") }.trim()}ms"
        "read" -> buildString {
            append("read ")
            append(arguments.optString("path").trim())
            val offset = arguments.takeIf { it.has("offset") }?.optInt("offset") ?: 0
            val limit = arguments.takeIf { it.has("limit") }?.optInt("limit")
            if (offset > 0 || limit != null) {
                append(" (offset=")
                append(offset)
                if (limit != null) {
                    append(", limit=")
                    append(limit)
                }
                append(')')
            }
        }
        "edit" -> {
            val path = arguments.optString("path").trim()
            val editCount = arguments.optJSONArray("edits")?.length()
                ?: if (arguments.has("oldText") || arguments.has("newText")) 1 else 0
            "edit $path${if (editCount > 0) " ($editCount edit${if (editCount == 1) "" else "s"})" else ""}"
        }
        "write" -> "write ${arguments.optString("path").trim()}"
        "grep" -> "grep ${arguments.optString("pattern").trim()} in ${arguments.optString("path").trim()}"
        "find" -> "find ${arguments.optString("pattern").trim()} in ${arguments.optString("path").trim()}"
        "ls" -> "ls ${arguments.optString("path").trim()}"
        "analyze_image" -> buildString {
            append("analyze_image ")
            append(arguments.optString("path").trim())
            val prompt = arguments.optString("prompt").trim()
            if (prompt.isNotBlank()) {
                append(" (")
                append(prompt.take(48))
                if (prompt.length > 48) {
                    append("...")
                }
                append(')')
            }
        }
        "agent_display" -> summarizeAgentDisplayCommand(arguments)
        "tavily_search" -> "search ${arguments.optString("query").trim()}"
        "fetch_web_url" -> "fetch ${arguments.optString("url").trim()}"
        else -> toolName
    }.trim()
}

private fun formatAttachmentMeta(attachment: ChatAttachment): String {
    val typeLabel = if (attachment.kind == AttachmentKind.Image) "Photo" else "File"
    val sizeLabel = attachment.sizeBytes?.let(::formatAttachmentSize)
    return listOfNotNull(typeLabel, sizeLabel).joinToString(" • ")
}

private fun formatToolInvocationTitle(toolInvocation: ChatToolInvocation): String =
    formatToolInvocationTitleLabel(toolInvocation)

private fun formatToolInvocationGroupTitle(
    count: Int,
    isRunning: Boolean,
): String = if (isRunning) {
    "Executing $count tools"
} else {
    "Executed $count tools"
}

private fun formatToolInvocationDetail(strings: AetherStrings, toolInvocation: ChatToolInvocation): ToolInvocationDetail {
    val arguments = parseJsonObject(toolInvocation.argumentsJson)
    val output = parseJsonObject(toolInvocation.outputJson)
    val command = output?.optString("command")
        .orEmpty()
        .trim()
        .ifBlank { strings.toolInvocationCommandLabel(toolInvocation.toolName, arguments) }
        .ifBlank { toolInvocation.toolName }

    if (toolInvocation.isRunning) {
        return ToolInvocationDetail(
            command = command,
            result = null,
        )
    }

    val result = when {
        output == null -> toolInvocation.outputJson.trim().ifBlank { strings.noOutput }
        toolInvocation.toolName.equals("fetch_web_url", ignoreCase = true) -> {
            formatFetchWebUrlResult(strings, output)
        }
        output.optString("stdout").trim().isNotBlank() &&
            output.optString("stderr").trim().isNotBlank() -> {
            buildString {
                appendLine(output.optString("stdout").trim())
                appendLine()
                append("stderr: ")
                append(output.optString("stderr").trim())
            }
        }
        output.optString("stdout").trim().isNotBlank() -> output.optString("stdout").trim()
        output.optString("stderr").trim().isNotBlank() -> output.optString("stderr").trim()
        output.optString("errmsg").trim().isNotBlank() -> output.optString("errmsg").trim()
        output.optString("hint").trim().isNotBlank() -> output.optString("hint").trim()
        output.has("exit_code") && output.optInt("exit_code") != 0 -> if (strings.appLanguage == AppLanguage.SimplifiedChinese) "退出代码：${output.optInt("exit_code")}" else "Exit code: ${output.optInt("exit_code")}"
        else -> strings.noOutput
    }

    return ToolInvocationDetail(
        command = command,
        result = result,
    )
}

private fun formatFetchWebUrlResult(strings: AetherStrings, output: JSONObject): String {
    val markdown = output.optString("markdown").trim()
    if (markdown.isNotBlank()) {
        return buildString {
            append(markdown)

            if (output.optBoolean("truncated")) {
                appendLine()
                appendLine()
                append(strings.contentTruncated)
            }
        }.trim()
    }

    return output.optString("stdout").trim().ifBlank { strings.noOutput }
}

private fun summarizeToolInvocationCommand(
    toolName: String,
    arguments: JSONObject?,
): String {
    if (arguments == null) return toolName
    return when (toolName.lowercase()) {
        "bash" -> arguments.optString("command").trim()
        "fetch_bash_output" -> "fetch ${arguments.optString("run_id").ifBlank { arguments.optString("runId") }.trim()}"
        "kill_bash" -> "kill ${arguments.optString("run_id").ifBlank { arguments.optString("runId") }.trim()}"
        "sleep" -> "sleep ${arguments.optString("duration_ms").ifBlank { arguments.optString("durationMs") }.trim()}ms"
        "read" -> buildString {
            append("read ")
            append(arguments.optString("path").trim())
            val offset = arguments.takeIf { it.has("offset") }?.optInt("offset") ?: 0
            val limit = arguments.takeIf { it.has("limit") }?.optInt("limit")
            if (offset > 0 || limit != null) {
                append(" (offset=")
                append(offset)
                if (limit != null) {
                    append(", limit=")
                    append(limit)
                }
                append(')')
            }
        }
        "edit" -> {
            val path = arguments.optString("path").trim()
            val editCount = arguments.optJSONArray("edits")?.length()
                ?: if (arguments.has("oldText") || arguments.has("newText")) 1 else 0
            "edit $path${if (editCount > 0) " ($editCount edit${if (editCount == 1) "" else "s"})" else ""}"
        }
        "write" -> "write ${arguments.optString("path").trim()}"
        "grep" -> "grep ${arguments.optString("pattern").trim()} in ${arguments.optString("path").trim()}"
        "find" -> "find ${arguments.optString("pattern").trim()} in ${arguments.optString("path").trim()}"
        "ls" -> "ls ${arguments.optString("path").trim()}"
        "agent_display" -> summarizeAgentDisplayCommand(arguments)
        "tavily_search" -> "search ${arguments.optString("query").trim()}"
        "fetch_web_url" -> "fetch ${arguments.optString("url").trim()}"
        else -> toolName
    }.trim()
}

private fun formatArgumentDrivenTitle(
    isRunning: Boolean,
    progressiveVerb: String,
    completedVerb: String,
    subject: String,
    fallback: String,
): String {
    val normalizedSubject = subject.trim()
    val action = if (isRunning) progressiveVerb else completedVerb
    if (normalizedSubject.isBlank()) {
        return "$action $fallback"
    }

    return "$action ${normalizedSubject.take(72)}${if (normalizedSubject.length > 72) "..." else ""}"
}

private fun formatAgentDisplayTitle(
    isRunning: Boolean,
    arguments: JSONObject?,
): String {
    val action = arguments?.optString("action").orEmpty().lowercase()
    return when (action) {
        "start" -> if (isRunning) "Starting Agent Mode display" else "Started Agent Mode display"
        "status" -> if (isRunning) "Checking Agent Mode display" else "Checked Agent Mode display"
        "launch" -> formatArgumentDrivenTitle(
            isRunning = isRunning,
            progressiveVerb = "Launching",
            completedVerb = "Launched",
            subject = arguments?.optString("target").orEmpty(),
            fallback = "app in Agent Mode",
        )
        "tap" -> if (isRunning) "Tapping Agent Mode display" else "Tapped Agent Mode display"
        "swipe" -> if (isRunning) "Swiping Agent Mode display" else "Swiped Agent Mode display"
        "key" -> formatArgumentDrivenTitle(
            isRunning = isRunning,
            progressiveVerb = "Pressing",
            completedVerb = "Pressed",
            subject = arguments?.optString("key").orEmpty(),
            fallback = "key in Agent Mode",
        )
        "text" -> if (isRunning) "Typing in Agent Mode" else "Typed in Agent Mode"
        "screenshot" -> if (isRunning) "Capturing Agent Mode display" else "Captured Agent Mode display"
        "stop" -> if (isRunning) "Stopping Agent Mode display" else "Stopped Agent Mode display"
        else -> if (isRunning) "Using Agent Mode display" else "Used Agent Mode display"
    }
}

private fun summarizeAgentDisplayCommand(arguments: JSONObject?): String {
    if (arguments == null) return "agent_display"
    val action = arguments.optString("action").trim().ifBlank { "unknown" }
    return when (action.lowercase()) {
        "launch" -> "agent_display launch ${arguments.optString("target").trim()}"
        "tap" -> "agent_display tap x=${arguments.optString("x").trim()} y=${arguments.optString("y").trim()}"
        "swipe" -> "agent_display swipe ${arguments.optString("x1").trim()},${arguments.optString("y1").trim()} -> ${arguments.optString("x2").trim()},${arguments.optString("y2").trim()} ${arguments.optString("duration_ms").ifBlank { arguments.optString("durationMs") }.trim()}ms"
        "key" -> "agent_display key ${arguments.optString("key").trim()}"
        "text" -> {
            val text = arguments.optString("text").trim()
            "agent_display text ${text.take(48)}${if (text.length > 48) "..." else ""}"
        }
        "start", "status", "screenshot", "stop" -> "agent_display $action"
        else -> "agent_display $action"
    }.trim()
}

private fun buildAgentModeReplayFrames(
    toolInvocations: List<ChatToolInvocation>,
): List<AgentModeReplayFrame> = buildList {
    toolInvocations.forEach { invocation ->
        if (!invocation.toolName.equals("agent_display", ignoreCase = true)) return@forEach
        val output = parseJsonObject(invocation.outputJson) ?: return@forEach
        if (!output.optBoolean("ok")) return@forEach
        val previewPath = output.optString("preview_path").ifBlank {
            output.optString("previewPath")
        }
        if (previewPath.isBlank()) return@forEach
        add(
            AgentModeReplayFrame(
                previewPath = previewPath,
                width = output.optInt("width"),
                height = output.optInt("height"),
                completedAtUptimeMillis = invocation.completedAtUptimeMillis ?: invocation.startedAtUptimeMillis,
                toolTitle = formatToolInvocationTitleLabel(invocation.copy(isRunning = true)),
            )
        )
    }
}

private fun agentModeReplayBackdropBrush(): Brush = Brush.linearGradient(
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

private fun parseJsonObject(rawValue: String): JSONObject? {
    if (rawValue.isBlank()) return null
    return runCatching { JSONObject(rawValue) }.getOrNull()
}

private fun highlightBashCommand(command: String): AnnotatedString = buildAnnotatedString {
    appendStyled("$ ", SpanStyle(color = AetherSecondary, fontWeight = FontWeight.SemiBold))

    val tokenPattern = Regex("""\s+|&&|\|\||[|;><()]|"(?:\\.|[^"])*"|'(?:\\.|[^'])*'|\$[A-Za-z_][A-Za-z0-9_]*|--?[A-Za-z0-9][\w-]*|[^\s|;><()]+""")
    var expectsCommand = true

    tokenPattern.findAll(command).forEach { match ->
        val token = match.value
        val style = when {
            token.isBlank() -> null
            token in setOf("|", "||", "&&", ";", ">", "<", "(", ")") -> {
                expectsCommand = true
                SpanStyle(color = AetherOnSurfaceVariant)
            }
            token.startsWith("\"") || token.startsWith("'") -> SpanStyle(color = AetherTertiary)
            token.startsWith("$") -> SpanStyle(color = AetherPrimary)
            token.startsWith("-") -> SpanStyle(color = AetherSecondary)
            token.startsWith("/") || token.startsWith("~/") || token.startsWith("./") || token.startsWith("../") -> SpanStyle(color = AetherSecondary)
            expectsCommand -> {
                expectsCommand = false
                SpanStyle(color = AetherPrimary, fontWeight = FontWeight.SemiBold)
            }
            token.all(Char::isDigit) -> SpanStyle(color = AetherTertiary)
            else -> SpanStyle(color = AetherOnSurface)
        }
        appendStyled(token, style)
    }
}

private fun highlightToolResult(result: String): AnnotatedString = buildAnnotatedString {
    val tokenPattern = Regex("""\s+|~?/[\w./-]+|\b\d+\b|[A-Za-z_][A-Za-z0-9_]*:|[^\s]+""")

    tokenPattern.findAll(result).forEach { match ->
        val token = match.value
        val lowerToken = token.lowercase()
        val style = when {
            token.isBlank() -> null
            lowerToken.contains("error") ||
                lowerToken.contains("failed") ||
                lowerToken.contains("denied") -> SpanStyle(color = AetherError, fontWeight = FontWeight.Medium)
            token.startsWith("/") || token.startsWith("~/") -> SpanStyle(color = AetherSecondary)
            token.all(Char::isDigit) -> SpanStyle(color = AetherTertiary)
            token.endsWith(":") -> SpanStyle(color = AetherOnSurfaceVariant, fontWeight = FontWeight.Medium)
            else -> SpanStyle(color = AetherOnSurface)
        }
        appendStyled(token, style)
    }
}

private fun AnnotatedString.Builder.appendStyled(
    text: String,
    style: SpanStyle?,
) {
    if (style == null) {
        append(text)
        return
    }

    pushStyle(style)
    append(text)
    pop()
}

private fun formatAttachmentSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024f * 1024f))
    bytes >= 1024 -> String.format("%.1f KB", bytes / 1024f)
    else -> "$bytes B"
}

private data class AttachmentTextPreview(
    val text: String,
    val isTruncated: Boolean,
)

private data class ToolInvocationDetail(
    val command: String,
    val result: String?,
)

private data class WebSourceMetadata(
    val domain: String,
    val url: String,
    val faviconUrl: String?,
)

private data class AgentModeReplayFrame(
    val previewPath: String,
    val width: Int,
    val height: Int,
    val completedAtUptimeMillis: Long,
    val toolTitle: String,
)
