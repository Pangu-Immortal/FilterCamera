/**
 * NewCameraComponents.kt - 新版相机UI组件
 *
 * 基于新的设计稿实现的相机UI组件
 * 使用ResponsiveDimens响应式尺寸系统和CameraTheme统一颜色
 *
 * @author Jules
 * @since 3.0.0
 */
package com.qihao.filtercamera.presentation.camera.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qihao.filtercamera.domain.model.CameraMode
import com.qihao.filtercamera.domain.model.AspectRatio
import com.qihao.filtercamera.domain.model.FlashMode
import com.qihao.filtercamera.domain.model.HdrMode
import com.qihao.filtercamera.domain.model.TimerMode
import com.qihao.filtercamera.presentation.common.theme.CameraTheme
import com.qihao.filtercamera.presentation.common.theme.rememberResponsiveDimens

@Composable
fun NewCameraTopBar(
    flashMode: FlashMode,
    hdrMode: HdrMode,
    timerMode: TimerMode,
    aspectRatio: AspectRatio,
    isFilterActive: Boolean = false,                                    // 滤镜是否激活
    onFlashClick: () -> Unit,
    onHdrClick: () -> Unit,
    onTimerClick: () -> Unit,
    onFilterClick: () -> Unit,                                          // 滤镜按钮点击回调
    onAspectRatioClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = rememberResponsiveDimens()                               // 响应式尺寸系统

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.spacing.lg),                     // 修改：减小水平间距避免裁剪
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.spacing.md)) {  // 修改：减小间距避免溢出
            IconButton(onClick = onFlashClick) {
                Icon(
                    imageVector = when (flashMode) {
                        FlashMode.ON -> Icons.Default.FlashOn
                        FlashMode.OFF -> Icons.Default.FlashOff
                        FlashMode.AUTO -> Icons.Default.FlashAuto
                        FlashMode.TORCH -> Icons.Default.Highlight
                    },
                    contentDescription = "闪光灯",
                    tint = if (flashMode == FlashMode.OFF) CameraTheme.Colors.textPrimary else CameraTheme.Colors.primary
                )
            }
            Button(
                onClick = onHdrClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (hdrMode == HdrMode.ON) CameraTheme.Colors.primary else CameraTheme.ModeSelector.background,
                ),
                shape = RoundedCornerShape(dimens.radius.small / 2),      // 响应式圆角
                modifier = Modifier.border(
                    1.dp,
                    if (hdrMode == HdrMode.ON) CameraTheme.Colors.primary else CameraTheme.Colors.textPrimary,
                    RoundedCornerShape(dimens.radius.small / 2)
                )
            ) {
                Text(
                    "HDR",
                    color = if (hdrMode == HdrMode.ON) CameraTheme.Colors.onPrimary else CameraTheme.Colors.textPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(onClick = onTimerClick) {
                Icon(
                    Icons.Default.Timer,
                    contentDescription = "定时器",
                    tint = if (timerMode != TimerMode.OFF) CameraTheme.Colors.primary else CameraTheme.Colors.textPrimary
                )
            }
            // 滤镜按钮（魔法棒图标）
            IconButton(onClick = onFilterClick) {
                Icon(
                    Icons.Default.AutoAwesome,                           // 魔法棒图标
                    contentDescription = "滤镜",
                    tint = if (isFilterActive) CameraTheme.Colors.primary else CameraTheme.Colors.textPrimary
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm)) {  // 修改：减小右侧间距
            Button(
                onClick = onAspectRatioClick,
                colors = ButtonDefaults.buttonColors(containerColor = CameraTheme.Colors.controlBackgroundLight),
                shape = CircleShape
            ) {
                Text(
                    aspectRatio.displayName,
                    color = CameraTheme.Colors.textPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(onClick = onSettingsClick) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "设置",
                    tint = CameraTheme.Colors.textPrimary
                )
            }
        }
    }
}

/**
 * NewCameraBottomControls - 底部控制区
 *
 * 包含相册缩略图、快门按钮、切换镜头按钮
 * 使用响应式尺寸系统确保多设备适配
 *
 * 优化点：
 * - 增加控件间距，避免误触
 * - 快门按钮居中对齐
 * - 相册和切换按钮等宽，保持对称
 *
 * @param galleryThumbnail 相册缩略图（可空）
 * @param onGalleryClick 点击相册回调
 * @param onShutterClick 点击快门回调
 * @param onSwitchCameraClick 点击切换镜头回调
 */
@Composable
fun NewCameraBottomControls(
    galleryThumbnail: Bitmap?,
    onGalleryClick: () -> Unit,
    onShutterClick: () -> Unit,
    onSwitchCameraClick: () -> Unit
) {
    val dimens = rememberResponsiveDimens()                               // 响应式尺寸系统

    // 计算响应式尺寸
    val gallerySize = dimens.overlayButtonSize + dimens.spacing.md        // 相册缩略图尺寸（减小以增加间距）
    val shutterOuter = dimens.shutterButtonSize                           // 快门外圈
    val shutterInner = dimens.shutterInnerSize                            // 快门内圈
    val switchButtonSize = dimens.minTouchTarget                          // 切换按钮尺寸

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = dimens.spacing.xl,                           // 增加水平边距
                vertical = dimens.spacing.lg                              // 增加垂直边距
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Gallery Preview - 相册预览缩略图
        Box(
            modifier = Modifier
                .size(gallerySize)
                .clip(RoundedCornerShape(dimens.radius.small))
                .border(
                    1.5.dp,
                    CameraTheme.Colors.textSecondary.copy(alpha = 0.5f),  // 减淡边框
                    RoundedCornerShape(dimens.radius.small)
                )
                .background(CameraTheme.Colors.controlBackgroundLight)
                .clickable(onClick = onGalleryClick),
            contentAlignment = Alignment.Center
        ) {
            if (galleryThumbnail != null) {
                androidx.compose.foundation.Image(
                    bitmap = galleryThumbnail.asImageBitmap(),
                    contentDescription = "相册预览",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        // Shutter Button - 快门按钮（居中）
        Box(
            modifier = Modifier
                .size(shutterOuter)
                .clip(CircleShape)
                .border(dimens.shutterStrokeWidth, CameraTheme.Shutter.outer, CircleShape)
                .padding(dimens.spacing.xs),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = onShutterClick,
                modifier = Modifier.size(shutterInner),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = CameraTheme.Shutter.inner)
            ) {}
        }

        // Switch Camera Button - 切换镜头按钮
        IconButton(
            onClick = onSwitchCameraClick,
            modifier = Modifier
                .size(switchButtonSize)
                .clip(CircleShape)
                .background(CameraTheme.Colors.controlBackgroundLight)
        ) {
            Icon(
                Icons.Default.Cached,
                contentDescription = "切换摄像头",
                tint = CameraTheme.Colors.iconActive,
                modifier = Modifier.size(dimens.iconSizeLarge)
            )
        }
    }
}

/**
 * CameraModeSelector - 相机模式选择器
 *
 * 使用LazyRow实现水平滚动的模式选择器，防止小屏设备溢出裁剪
 * 支持响应式尺寸和统一主题颜色
 *
 * 优化点：
 * - 增加与预览区的间距，避免遮挡
 * - 调整选中指示器位置
 * - 改善触摸反馈区域
 *
 * @param currentMode 当前选中的模式
 * @param onModeSelected 模式选中回调
 */
@Composable
fun CameraModeSelector(
    currentMode: CameraMode,
    onModeSelected: (CameraMode) -> Unit
) {
    val dimens = rememberResponsiveDimens()                               // 响应式尺寸系统
    val modes = CameraMode.getAllModes()                                  // 获取所有模式
    val listState = rememberLazyListState()                               // LazyRow状态

    // 自动滚动到当前选中的模式
    val currentIndex = modes.indexOf(currentMode)
    LaunchedEffect(currentMode) {
        if (currentIndex >= 0) {
            listState.animateScrollToItem(
                index = currentIndex,
                scrollOffset = -100                                       // 使选中项居中偏左
            )
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimens.spacing.sm)                        // 垂直间距：与预览区保持距离
            .height(dimens.modeSelectorHeight + dimens.spacing.sm),       // 响应式高度（减小以避免遮挡）
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = dimens.spacing.xl)    // 增加水平内边距
    ) {
        items(modes) { mode ->
            val isSelected = mode == currentMode

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(horizontal = dimens.spacing.md)              // 水平间距：模式项之间
                    .clickable { onModeSelected(mode) }
            ) {
                Text(
                    text = mode.displayName,
                    color = if (isSelected) CameraTheme.ModeSelector.active else CameraTheme.ModeSelector.inactive,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    letterSpacing = 1.5.sp                                // 减小字间距，更紧凑
                )
                if (isSelected) {
                    Spacer(modifier = Modifier.height(dimens.spacing.xs))
                    Box(
                        modifier = Modifier
                            .size(dimens.modeIndicatorHeight + 2.dp)
                            .background(CameraTheme.ModeSelector.indicator, CircleShape)
                    )
                }
            }
        }
    }
}
