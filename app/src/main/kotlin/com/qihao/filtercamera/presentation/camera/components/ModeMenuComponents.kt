/**
 * ModeMenuComponents.kt - 模式菜单组件
 *
 * 将TopBar中的多个功能按钮整合到下拉菜单中
 * 减少UI拥挤，提升用户体验
 *
 * 整合功能：
 * - HDR开关
 * - 定时器
 * - 画幅比例
 * - 滤镜入口
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.presentation.camera.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.HdrOn
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qihao.filtercamera.domain.model.AspectRatio
import com.qihao.filtercamera.domain.model.CameraMode
import com.qihao.filtercamera.domain.model.HdrMode
import com.qihao.filtercamera.domain.model.TimerMode
import com.qihao.filtercamera.presentation.common.theme.CameraTheme
import com.qihao.filtercamera.presentation.common.theme.rememberResponsiveDimens

/**
 * 模式菜单按钮
 *
 * 点击展开/收起模式菜单
 *
 * @param isExpanded 菜单是否展开
 * @param hasActiveFeature 是否有激活的功能（用于高亮显示）
 * @param onClick 点击回调
 */
@Composable
fun ModeMenuButton(
    isExpanded: Boolean,
    hasActiveFeature: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = rememberResponsiveDimens()

    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(dimens.minTouchTarget)
            .clip(CircleShape)
            .background(
                if (isExpanded) CameraTheme.Colors.primary.copy(alpha = 0.2f)
                else CameraTheme.Colors.controlBackgroundLight
            )
    ) {
        Icon(
            imageVector = Icons.Default.ExpandMore,
            contentDescription = "模式菜单",
            tint = when {
                isExpanded -> CameraTheme.Colors.primary
                hasActiveFeature -> CameraTheme.Colors.primary
                else -> CameraTheme.Colors.textPrimary
            }
        )
    }
}

/**
 * 模式菜单弹窗
 *
 * 显示HDR、定时器、画幅、滤镜等功能选项
 * 根据当前相机模式动态显示/隐藏功能项
 *
 * 各模式功能支持：
 * - PHOTO/PRO: 全功能（HDR、定时器、画幅、滤镜）
 * - VIDEO: 无HDR、无定时器
 * - PORTRAIT: 无HDR、画幅固定
 * - DOCUMENT: 无HDR、无定时器、无滤镜、画幅固定
 * - NIGHT: 无HDR
 *
 * @param isVisible 是否可见
 * @param cameraMode 当前相机模式（用于过滤菜单项）
 * @param hdrMode 当前HDR模式
 * @param timerMode 当前定时器模式
 * @param aspectRatio 当前画幅比例
 * @param isFilterActive 滤镜是否激活
 * @param onHdrClick HDR点击回调
 * @param onTimerClick 定时器点击回调
 * @param onAspectRatioClick 画幅点击回调
 * @param onFilterClick 滤镜点击回调
 * @param onDismiss 关闭回调
 */
@Composable
fun ModeMenuPopup(
    isVisible: Boolean,
    cameraMode: CameraMode,                                              // 新增：相机模式
    hdrMode: HdrMode,
    timerMode: TimerMode,
    aspectRatio: AspectRatio,
    isFilterActive: Boolean,
    onHdrClick: () -> Unit,
    onTimerClick: () -> Unit,
    onAspectRatioClick: () -> Unit,
    onFilterClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = rememberResponsiveDimens()

    // 根据模式判断功能可用性
    val showHdr = cameraMode.supportsHdr()                               // HDR：仅拍照/专业模式
    val showTimer = cameraMode.supportsTimer()                           // 定时器：拍照/人像/夜景/专业
    val showAspectRatio = cameraMode.supportsAspectRatio()               // 画幅：非人像/文档模式
    val showFilter = cameraMode.supportsFilter()                         // 滤镜：非文档模式

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { -it / 2 },
        exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { -it / 2 },
        modifier = modifier
    ) {
        // 背景点击区域（点击关闭）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        ) {
            // 菜单卡片
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = dimens.spacing.md),
                shape = RoundedCornerShape(dimens.radius.medium),
                color = CameraTheme.ModeSelector.background.copy(alpha = 0.95f),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(dimens.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(dimens.spacing.sm)
                ) {
                    // HDR 选项（仅拍照/专业模式显示）
                    if (showHdr) {
                        ModeMenuItem(
                            icon = Icons.Default.HdrOn,
                            label = "HDR",
                            value = if (hdrMode == HdrMode.ON) "开" else "关",
                            isActive = hdrMode == HdrMode.ON,
                            onClick = {
                                onHdrClick()
                                onDismiss()
                            }
                        )
                    }

                    // 定时器选项（拍照/人像/夜景/专业模式显示）
                    if (showTimer) {
                        ModeMenuItem(
                            icon = Icons.Default.Timer,
                            label = "定时器",
                            value = when (timerMode) {
                                TimerMode.OFF -> "关"
                                TimerMode.TIMER_3S -> "3秒"
                                TimerMode.TIMER_5S -> "5秒"
                                TimerMode.TIMER_10S -> "10秒"
                            },
                            isActive = timerMode != TimerMode.OFF,
                            onClick = {
                                onTimerClick()
                                onDismiss()
                            }
                        )
                    }

                    // 画幅选项（非人像/文档模式显示）
                    if (showAspectRatio) {
                        ModeMenuItem(
                            icon = Icons.Default.CropFree,
                            label = "画幅",
                            value = aspectRatio.displayName,
                            isActive = false,
                            onClick = {
                                onAspectRatioClick()
                                onDismiss()
                            }
                        )
                    }

                    // 滤镜选项（非文档模式显示）
                    // 注意：滤镜点击不调用onDismiss()，因为toggleFilterSelector()内部
                    // 通过PopupStateHolder互斥逻辑已经隐式关闭ModeMenu
                    if (showFilter) {
                        ModeMenuItem(
                            icon = Icons.Default.AutoAwesome,
                            label = "滤镜",
                            value = if (isFilterActive) "已开启" else "关闭",
                            isActive = isFilterActive,
                            onClick = {
                                onFilterClick()
                                // 不调用onDismiss()，避免PopupStateHolder状态冲突
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 模式菜单项
 *
 * @param icon 图标
 * @param label 标签文本
 * @param value 当前值文本
 * @param isActive 是否激活（高亮显示）
 * @param onClick 点击回调
 */
@Composable
private fun ModeMenuItem(
    icon: ImageVector,
    label: String,
    value: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = rememberResponsiveDimens()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radius.small))
            .clickable(onClick = onClick)
            .padding(
                horizontal = dimens.spacing.md,
                vertical = dimens.spacing.sm
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(dimens.spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) CameraTheme.Colors.primary else CameraTheme.Colors.textPrimary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = label,
                color = CameraTheme.Colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Text(
            text = value,
            color = if (isActive) CameraTheme.Colors.primary else CameraTheme.Colors.textSecondary,
            fontSize = 12.sp
        )
    }
}

/**
 * 精简版 TopBar
 *
 * 减少按钮数量，使用模式菜单整合功能
 * 根据不同相机模式显示/隐藏对应功能项
 *
 * 布局：[闪光灯] [模式菜单▼] [设置]
 *
 * @param cameraMode 当前相机模式（用于过滤菜单项）
 * @param flashMode 闪光灯模式
 * @param hdrMode HDR模式
 * @param timerMode 定时器模式
 * @param aspectRatio 画幅比例
 * @param isFilterActive 滤镜是否激活
 * @param isModeMenuVisible 模式菜单是否可见
 * @param onFlashClick 闪光灯点击
 * @param onModeMenuClick 模式菜单点击
 * @param onHdrClick HDR点击
 * @param onTimerClick 定时器点击
 * @param onAspectRatioClick 画幅点击
 * @param onFilterClick 滤镜点击
 * @param onSettingsClick 设置点击
 */
@Composable
fun CompactCameraTopBar(
    cameraMode: CameraMode,                                              // 新增：当前相机模式
    flashMode: com.qihao.filtercamera.domain.model.FlashMode,
    hdrMode: HdrMode,
    timerMode: TimerMode,
    aspectRatio: AspectRatio,
    isFilterActive: Boolean,
    isModeMenuVisible: Boolean,
    onFlashClick: () -> Unit,
    onModeMenuClick: () -> Unit,
    onHdrClick: () -> Unit,
    onTimerClick: () -> Unit,
    onAspectRatioClick: () -> Unit,
    onFilterClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = rememberResponsiveDimens()

    // 检查是否有任何功能激活（仅检查当前模式支持的功能）
    val hasActiveFeature = (cameraMode.supportsHdr() && hdrMode == HdrMode.ON) ||
            (cameraMode.supportsTimer() && timerMode != TimerMode.OFF) ||
            (cameraMode.supportsFilter() && isFilterActive)

    // TopBar半透明渐变背景（从上到下：半透明黑→透明）
    // 增强透明度，确保全屏模式下按钮清晰可见
    val topBarGradient = Brush.verticalGradient(
        colors = listOf(
            Color.Black.copy(alpha = 0.75f),                             // 顶部更深，确保按钮可见
            Color.Black.copy(alpha = 0.5f),                              // 中间过渡
            Color.Black.copy(alpha = 0.2f),                              // 渐变到浅色
            Color.Transparent                                             // 底部透明
        ),
        startY = 0f,
        endY = Float.POSITIVE_INFINITY                                    // 使用无限大，让渐变更均匀
    )

    Column(modifier = modifier.fillMaxWidth()) {
        // TopBar 主体 - 添加渐变背景
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(topBarGradient)                               // 半透明渐变背景
                .padding(vertical = dimens.spacing.md)                    // 增加垂直内边距使背景区域更大
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.spacing.lg),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：闪光灯
                IconButton(onClick = onFlashClick) {
                    Icon(
                        imageVector = when (flashMode) {
                            com.qihao.filtercamera.domain.model.FlashMode.ON ->
                                Icons.Default.FlashOn
                            com.qihao.filtercamera.domain.model.FlashMode.OFF ->
                                Icons.Default.FlashOff
                            com.qihao.filtercamera.domain.model.FlashMode.AUTO ->
                                Icons.Default.FlashAuto
                            com.qihao.filtercamera.domain.model.FlashMode.TORCH ->
                                Icons.Default.Highlight
                        },
                        contentDescription = "闪光灯",
                        tint = if (flashMode == com.qihao.filtercamera.domain.model.FlashMode.OFF)
                            CameraTheme.Colors.textPrimary
                        else CameraTheme.Colors.primary
                    )
                }

                // 中间：模式菜单按钮
                ModeMenuButton(
                    isExpanded = isModeMenuVisible,
                    hasActiveFeature = hasActiveFeature,
                    onClick = onModeMenuClick
                )

                // 右侧：设置
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "设置",
                        tint = CameraTheme.Colors.textPrimary
                    )
                }
            }
        }

        // 模式菜单弹窗
        ModeMenuPopup(
            isVisible = isModeMenuVisible,
            cameraMode = cameraMode,                                      // 新增：传递相机模式
            hdrMode = hdrMode,
            timerMode = timerMode,
            aspectRatio = aspectRatio,
            isFilterActive = isFilterActive,
            onHdrClick = onHdrClick,
            onTimerClick = onTimerClick,
            onAspectRatioClick = onAspectRatioClick,
            onFilterClick = onFilterClick,
            onDismiss = onModeMenuClick  // 点击菜单外部关闭
        )
    }
}
