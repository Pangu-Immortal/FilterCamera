/**
 * XiaomiCameraComponents.kt - 小米相机风格UI组件
 *
 * 参考小米相机设计的UI组件集合
 * 包含：底部控制栏、模式TAB选择器、相册缩略图、快门按钮等
 *
 * 布局设计：
 * - 左下角：相册缩略图（显示最新拍摄的照片）
 * - 中间：快门按钮（大白圆）
 * - 右下角：切换镜头按钮
 * - 上方：滚动模式TAB（拍照、录像、人像、文档、专业）
 * - 预览区上层：滤镜按钮（魔法棒图标）
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.presentation.camera.components

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qihao.filtercamera.domain.model.CameraMode
import com.qihao.filtercamera.presentation.common.theme.XiaomiBottomBarBg
import com.qihao.filtercamera.presentation.common.theme.XiaomiBottomBarGradient
import com.qihao.filtercamera.presentation.common.theme.XiaomiControlBg
import com.qihao.filtercamera.presentation.common.theme.XiaomiControlIcon
import com.qihao.filtercamera.presentation.common.theme.XiaomiFilterActive
import com.qihao.filtercamera.presentation.common.theme.XiaomiFilterInactive
import com.qihao.filtercamera.presentation.common.theme.XiaomiGalleryBorder
import com.qihao.filtercamera.presentation.common.theme.XiaomiModeActive
import com.qihao.filtercamera.presentation.common.theme.XiaomiModeInactive
import com.qihao.filtercamera.presentation.common.theme.XiaomiModeIndicator
import com.qihao.filtercamera.presentation.common.theme.XiaomiShutterOuter
import com.qihao.filtercamera.presentation.common.theme.XiaomiShutterRecording

// ==================== 小米风格底部控制栏 ====================

/**
 * 小米风格底部控制栏
 *
 * 布局：
 * 上层：[拍照] [录像] [人像] [文档] [专业]  ← 滚动TAB
 * 下层：[相册] -------- [●快门●] -------- [切换]
 *
 * @param currentMode 当前相机模式
 * @param isRecording 是否正在录像
 * @param isCapturing 是否正在拍照
 * @param galleryThumbnail 相册最新照片缩略图
 * @param onModeSelected 模式选择回调
 * @param onCapture 拍照/录像按钮回调
 * @param onGalleryClick 相册点击回调
 * @param onSwitchCamera 切换摄像头回调
 */
@Composable
fun XiaomiBottomControls(
    currentMode: CameraMode,
    isRecording: Boolean,
    isCapturing: Boolean,
    galleryThumbnail: Bitmap?,
    onModeSelected: (CameraMode) -> Unit,
    onCapture: () -> Unit,
    onGalleryClick: () -> Unit,
    onSwitchCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(                               // 渐变背景
                    colors = listOf(
                        Color.Transparent,
                        XiaomiBottomBarGradient,
                        XiaomiBottomBarBg
                    )
                )
            )
            .padding(top = 20.dp, bottom = 16.dp),                            // 增加顶部padding
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 模式TAB选择器
        XiaomiModeTabRow(
            currentMode = currentMode,
            onModeSelected = onModeSelected
        )

        Spacer(modifier = Modifier.height(28.dp))                             // 增加间距

        // 主控制按钮行：[相册] [快门] [切换]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：相册缩略图
            XiaomiGalleryThumbnail(
                thumbnail = galleryThumbnail,
                onClick = onGalleryClick
            )

            // 中间：快门按钮
            XiaomiShutterButton(
                mode = currentMode,
                isRecording = isRecording,
                isCapturing = isCapturing,
                onClick = onCapture
            )

            // 右侧：切换摄像头按钮
            XiaomiSwitchCameraButton(
                onClick = onSwitchCamera
            )
        }
    }
}

// ==================== 小米风格模式TAB选择器 ====================

/**
 * 小米风格模式TAB行
 *
 * 水平滚动的模式选择标签，支持6种模式
 * 自动滚动到当前选中的模式
 *
 * @param currentMode 当前选中模式
 * @param onModeSelected 模式选择回调
 */
@Composable
fun XiaomiModeTabRow(
    currentMode: CameraMode,
    onModeSelected: (CameraMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val modes = CameraMode.getAllModes()                                      // 获取所有模式
    val listState = rememberLazyListState()                                   // 列表滚动状态
    val currentIndex = modes.indexOf(currentMode)                             // 当前模式索引

    // 自动滚动到当前选中的模式
    LaunchedEffect(currentMode) {
        if (currentIndex >= 0) {
            listState.animateScrollToItem(
                index = maxOf(0, currentIndex - 1),                           // 居中显示
                scrollOffset = 0
            )
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        contentPadding = PaddingValues(horizontal = 60.dp)
    ) {
        items(modes) { mode ->
            XiaomiModeTab(
                mode = mode,
                isSelected = mode == currentMode,
                onClick = { onModeSelected(mode) }
            )
        }
    }
}

/**
 * 小米风格单个模式标签
 *
 * @param mode 模式类型
 * @param isSelected 是否选中
 * @param onClick 点击回调
 */
@Composable
private fun XiaomiModeTab(
    mode: CameraMode,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val textColor by animateColorAsState(                                     // 文字颜色动画
        targetValue = if (isSelected) XiaomiModeActive else XiaomiModeInactive,
        animationSpec = tween(200),
        label = "modeTabTextColor"
    )

    val fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal   // 字体粗细

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,                                            // 无点击效果
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = mode.displayName,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = fontWeight
        )

        // 选中指示器（黄色下划线）
        if (isSelected) {
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(2.dp)
                    .background(
                        color = XiaomiModeIndicator,
                        shape = RoundedCornerShape(1.dp)
                    )
            )
        } else {
            Spacer(modifier = Modifier.height(6.dp))                          // 保持高度一致
        }
    }
}

// ==================== 小米风格快门按钮 ====================

/**
 * 小米风格快门按钮
 *
 * 拍照模式：白色外圈 + 白色内圆
 * 录像模式：白色外圈 + 红色圆点（开始）/ 红色方块（录制中）
 *
 * @param mode 当前模式
 * @param isRecording 是否正在录像
 * @param isCapturing 是否正在拍照
 * @param onClick 点击回调
 */
@Composable
fun XiaomiShutterButton(
    mode: CameraMode,
    isRecording: Boolean,
    isCapturing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val innerScale by animateFloatAsState(                                    // 内圆缩放动画
        targetValue = if (isCapturing) 0.85f else 1f,
        animationSpec = tween(100),
        label = "shutterScale"
    )

    val isVideoMode = CameraMode.isVideoMode(mode)                            // 是否为视频模式

    Box(
        modifier = modifier
            .size(68.dp)                                                          // 精致化：76→68dp
            .clip(CircleShape)
            .border(3.dp, XiaomiShutterOuter, CircleShape)                         // 精致化：4→3dp
            .clickable(
                enabled = !isCapturing,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(4.dp),                                                       // 精致化：5→4dp
        contentAlignment = Alignment.Center
    ) {
        when {
            isVideoMode && isRecording -> {                                   // 录像中：红色方块
                Box(
                    modifier = Modifier
                        .size(22.dp)                                              // 精致化：26→22dp
                        .clip(RoundedCornerShape(5.dp))
                        .background(XiaomiShutterRecording)
                )
            }
            isVideoMode -> {                                                  // 录像待机：红色圆点
                Box(
                    modifier = Modifier
                        .size(52.dp)                                              // 精致化：58→52dp
                        .scale(innerScale)
                        .clip(CircleShape)
                        .background(XiaomiShutterRecording)
                )
            }
            else -> {                                                         // 拍照模式：白色内圆
                Box(
                    modifier = Modifier
                        .size(52.dp)                                              // 精致化：58→52dp
                        .scale(innerScale)
                        .clip(CircleShape)
                        .background(XiaomiShutterOuter)
                )
            }
        }
    }
}

// ==================== 小米风格相册缩略图 ====================

/**
 * 小米风格相册缩略图
 *
 * 显示最新拍摄的照片，点击进入相册
 *
 * @param thumbnail 缩略图Bitmap
 * @param onClick 点击回调
 */
@Composable
fun XiaomiGalleryThumbnail(
    thumbnail: Bitmap?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(40.dp)                                                          // 精致化：48→40dp
            .clip(RoundedCornerShape(6.dp))                                       // 精致化：8→6dp
            .border(1.dp, XiaomiGalleryBorder, RoundedCornerShape(6.dp))          // 精致化：1.5→1dp
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = Color.White),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (thumbnail != null) {
            // 显示缩略图
            Image(
                bitmap = thumbnail.asImageBitmap(),
                contentDescription = "相册",
                modifier = Modifier
                    .size(40.dp)                                                  // 精致化：48→40dp
                    .clip(RoundedCornerShape(6.dp)),                              // 精致化：8→6dp
                contentScale = ContentScale.Crop
            )
        } else {
            // 无缩略图时显示图标
            Icon(
                imageVector = Icons.Default.PhotoLibrary,
                contentDescription = "相册",
                tint = XiaomiControlIcon,
                modifier = Modifier.size(20.dp)                                   // 精致化：24→20dp
            )
        }
    }
}

// ==================== 小米风格切换摄像头按钮 ====================

/**
 * 小米风格切换摄像头按钮
 *
 * @param onClick 点击回调
 */
@Composable
fun XiaomiSwitchCameraButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(40.dp)                                                          // 精致化：48→40dp
            .clip(CircleShape)
            .background(XiaomiControlBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = Color.White),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Cameraswitch,
            contentDescription = "切换摄像头",
            tint = XiaomiControlIcon,
            modifier = Modifier.size(20.dp)                                       // 精致化：24→20dp
        )
    }
}

// ==================== 小米风格滤镜按钮 ====================

/**
 * 小米风格滤镜按钮（魔法棒图标）
 *
 * 放置在预览区右上角或其他位置
 *
 * @param isActive 滤镜是否激活（非原图滤镜时激活）
 * @param onClick 点击回调
 */
@Composable
fun XiaomiFilterButton(
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconColor by animateColorAsState(                                     // 图标颜色动画
        targetValue = if (isActive) XiaomiFilterActive else XiaomiFilterInactive,
        animationSpec = tween(200),
        label = "filterIconColor"
    )

    val bgColor by animateColorAsState(                                       // 背景颜色动画
        targetValue = if (isActive) XiaomiFilterActive.copy(alpha = 0.2f) else XiaomiControlBg,
        animationSpec = tween(200),
        label = "filterBgColor"
    )

    Box(
        modifier = modifier
            .size(36.dp)                                                          // 精致化：44→36dp
            .clip(CircleShape)
            .background(bgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = XiaomiFilterActive),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,                          // 魔法棒图标
            contentDescription = "滤镜",
            tint = iconColor,
            modifier = Modifier.size(18.dp)                                       // 精致化：22→18dp
        )
    }
}

// ==================== 小米风格拍照闪屏动画 ====================

/**
 * 拍照闪屏覆盖层
 *
 * 拍照时显示黑色闪屏效果
 *
 * @param isVisible 是否显示闪屏
 */
@Composable
fun XiaomiCaptureFlash(
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val alpha by animateFloatAsState(                                         // 透明度动画
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isVisible) 50 else 150,                      // 闪入快，消失慢
            easing = FastOutSlowInEasing
        ),
        label = "flashAlpha"
    )

    if (alpha > 0f) {
        Box(
            modifier = modifier
                .background(Color.Black.copy(alpha = alpha))
        )
    }
}
