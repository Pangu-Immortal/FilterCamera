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
                    contentDescription = "Flash",
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
                    contentDescription = "Timer",
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
                    contentDescription = "Settings",
                    tint = CameraTheme.Colors.textPrimary
                )
            }
        }
    }
}

@Composable
fun NewCameraBottomControls(
    galleryThumbnail: Bitmap?,
    onGalleryClick: () -> Unit,
    onShutterClick: () -> Unit,
    onSwitchCameraClick: () -> Unit
) {
    val dimens = rememberResponsiveDimens()                               // 响应式尺寸系统

    // 计算响应式尺寸
    val gallerySize = dimens.overlayButtonSize + dimens.spacing.lg        // 相册缩略图尺寸
    val shutterOuter = dimens.shutterButtonSize                           // 快门外圈
    val shutterInner = dimens.shutterInnerSize                            // 快门内圈
    val switchButtonSize = dimens.minTouchTarget                          // 切换按钮尺寸

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(dimens.bottomBarPadding),                            // 响应式内边距
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
                    CameraTheme.Colors.textSecondary,
                    RoundedCornerShape(dimens.radius.small)
                )
                .background(CameraTheme.Colors.controlBackgroundLight)
                .clickable(onClick = onGalleryClick),
            contentAlignment = Alignment.Center
        ) {
            if (galleryThumbnail != null) {
                androidx.compose.foundation.Image(
                    bitmap = galleryThumbnail.asImageBitmap(),
                    contentDescription = "Gallery preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        // Shutter Button - 快门按钮
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
                contentDescription = "Switch Camera",
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
            .height(dimens.modeSelectorHeight + dimens.spacing.md),       // 响应式高度
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = dimens.spacing.lg)    // 水平内边距
    ) {
        items(modes) { mode ->
            val isSelected = mode == currentMode

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(dimens.modeItemPadding)                      // 响应式间距
                    .clickable { onModeSelected(mode) }
            ) {
                Text(
                    text = mode.displayName,
                    color = if (isSelected) CameraTheme.ModeSelector.active else CameraTheme.ModeSelector.inactive,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                if (isSelected) {
                    Spacer(modifier = Modifier.height(dimens.spacing.sm - 2.dp))
                    Box(
                        modifier = Modifier
                            .size(dimens.modeIndicatorHeight + 1.dp)
                            .background(CameraTheme.ModeSelector.indicator, CircleShape)
                    )
                }
            }
        }
    }
}
