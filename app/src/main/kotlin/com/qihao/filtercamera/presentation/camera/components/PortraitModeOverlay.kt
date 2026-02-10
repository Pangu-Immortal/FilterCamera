/**
 * PortraitModeOverlay.kt - 人像模式覆盖层组件
 *
 * 提供人像模式下的人脸检测、美颜、虚化等控制组件
 *
 * 组件列表：
 * - FaceDetectionOverlay: 人脸检测覆盖层
 * - PortraitModeHint: 人像模式提示
 * - BeautySlider: 美颜滑块
 * - PortraitBlurLevelSelector: 虚化等级选择器
 * - PortraitBlurProcessingIndicator: 虚化处理指示器
 * - PortraitBlurQuickToggle: 虚化快捷切换按钮
 * - FaceTrackingStateIndicator: 人脸追踪状态指示器
 * - FaceTrackingToggleButton: 人脸追踪开关按钮
 * - PortraitModeControlBar: 人像模式控制栏
 *
 * @author qihao
 * @since 3.0.0
 */
package com.qihao.filtercamera.presentation.camera.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qihao.filtercamera.data.processor.FaceTrackingState
import com.qihao.filtercamera.domain.model.BeautyLevel
import com.qihao.filtercamera.domain.model.FaceInfo
import com.qihao.filtercamera.domain.model.NormalizedRect
import com.qihao.filtercamera.domain.model.PortraitBlurLevel
import com.qihao.filtercamera.presentation.common.theme.rememberResponsiveDimens

// ==================== 人脸检测覆盖层 ====================

/**
 * 人脸检测覆盖层
 *
 * 在检测到的人脸周围绘制边框
 *
 * @param faces 检测到的人脸列表
 * @param modifier 修饰符
 */
@Composable
fun FaceDetectionOverlay(
    faces: List<FaceInfo>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        faces.forEach { face ->
            drawFaceBox(face.boundingBox)
        }
    }
}

/**
 * 绘制人脸边框
 *
 * 使用四角线条样式绘制人脸检测框
 *
 * @param bounds 归一化边界框
 */
private fun DrawScope.drawFaceBox(bounds: NormalizedRect) {
    val left = bounds.left * size.width
    val top = bounds.top * size.height
    val right = bounds.right * size.width   // 修复：使用width而非height
    val bottom = bounds.bottom * size.height
    val width = right - left
    val height = bottom - top

    val cornerLength = minOf(width, height) * 0.2f                    // 角落线长度
    val strokeWidth = 4f                                              // 线条宽度
    val color = OverlayColors.faceBoxColor                            // 使用配置颜色

    // 绘制四个角落
    // 左上角
    drawLine(color, Offset(left, top), Offset(left + cornerLength, top), strokeWidth, StrokeCap.Round)
    drawLine(color, Offset(left, top), Offset(left, top + cornerLength), strokeWidth, StrokeCap.Round)

    // 右上角
    drawLine(color, Offset(right - cornerLength, top), Offset(right, top), strokeWidth, StrokeCap.Round)
    drawLine(color, Offset(right, top), Offset(right, top + cornerLength), strokeWidth, StrokeCap.Round)

    // 右下角
    drawLine(color, Offset(right - cornerLength, bottom), Offset(right, bottom), strokeWidth, StrokeCap.Round)
    drawLine(color, Offset(right, bottom - cornerLength), Offset(right, bottom), strokeWidth, StrokeCap.Round)

    // 左下角
    drawLine(color, Offset(left, bottom), Offset(left + cornerLength, bottom), strokeWidth, StrokeCap.Round)
    drawLine(color, Offset(left, bottom - cornerLength), Offset(left, bottom), strokeWidth, StrokeCap.Round)
}

// ==================== 人像模式提示 ====================

/**
 * 人像模式提示
 *
 * 显示人脸检测状态提示
 *
 * @param faceCount 检测到的人脸数量
 * @param modifier 修饰符
 */
@Composable
fun PortraitModeHint(
    faceCount: Int,
    modifier: Modifier = Modifier
) {
    val text = when {
        faceCount == 0 -> "未检测到人脸"
        faceCount == 1 -> "已对焦到人脸"
        else -> "检测到 $faceCount 张人脸"
    }
    val color = if (faceCount > 0) OverlayColors.accentGreen else OverlayColors.accentOrange

    Box(
        modifier = modifier
            .background(
                color = OverlayColors.hintBackground,
                shape = RoundedCornerShape(OverlayDimens.hintCornerRadius)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(OverlayDimens.statusDotSize)
                    .background(color, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                color = Color.White,
                fontSize = 12.sp
            )
        }
    }
}

// ==================== 美颜滑块 ====================

/**
 * 美颜滑块组件
 *
 * 显示在人像模式下，用于调整美颜强度
 *
 * @param currentLevel 当前美颜等级
 * @param onLevelChanged 美颜等级变化回调
 * @param modifier 修饰符
 */
@Composable
fun BeautySlider(
    currentLevel: BeautyLevel,
    onLevelChanged: (BeautyLevel) -> Unit,
    modifier: Modifier = Modifier
) {
    var sliderValue by remember(currentLevel) {
        mutableFloatStateOf(currentLevel.level.toFloat())
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = OverlayColors.hintBackground,
                shape = RoundedCornerShape(OverlayDimens.hintCornerRadius)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // 标题行：美颜图标和等级显示
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "美颜",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (currentLevel == BeautyLevel.OFF) "关闭" else "等级 ${currentLevel.level}",
                color = if (currentLevel == BeautyLevel.OFF) Color.Gray else OverlayColors.accentOrange,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 滑块
        Slider(
            value = sliderValue,
            onValueChange = { newValue ->
                sliderValue = newValue
            },
            onValueChangeFinished = {
                val level = BeautyLevel.fromLevel(sliderValue.toInt())
                onLevelChanged(level)
            },
            valueRange = 0f..10f,
            steps = 9,                                                       // 10个等级，9个步进
            colors = SliderDefaults.colors(
                thumbColor = OverlayColors.accentOrange,
                activeTrackColor = OverlayColors.accentOrange,
                inactiveTrackColor = OverlayColors.trackInactive
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // 等级标签
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "关",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 10.sp
            )
            Text(
                text = "最强",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 10.sp
            )
        }
    }
}

// ==================== 人像虚化等级选择器 ====================

/**
 * 人像虚化等级选择器
 *
 * 显示在人像模式下，允许用户选择背景虚化强度
 * 提供：关闭、轻度、中度、重度四个等级选择
 *
 * @param currentLevel 当前虚化等级
 * @param onLevelSelected 等级选中回调
 * @param modifier 修饰符
 */
@Composable
fun PortraitBlurLevelSelector(
    currentLevel: PortraitBlurLevel,
    onLevelSelected: (PortraitBlurLevel) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(
                color = OverlayColors.panelBackground,
                shape = RoundedCornerShape(OverlayDimens.panelCornerRadius)
            )
            .padding(OverlayDimens.panelPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 标题
        Text(
            text = "背景虚化",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 等级选择按钮
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PortraitBlurLevel.getAll().forEach { level ->
                PortraitBlurLevelChip(
                    level = level,
                    isSelected = level == currentLevel,
                    onClick = { onLevelSelected(level) }
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 当前等级描述
        Text(
            text = currentLevel.description,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 11.sp
        )
    }
}

/**
 * 人像虚化等级芯片按钮
 *
 * 触摸目标至少48dp以符合可访问性标准
 *
 * @param level 虚化等级
 * @param isSelected 是否选中
 * @param onClick 点击回调
 * @param modifier 修饰符
 */
@Composable
private fun PortraitBlurLevelChip(
    level: PortraitBlurLevel,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) OverlayColors.accentYellow else OverlayColors.chipUnselected
    val textColor = if (isSelected) Color.Black else Color.White

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)          // 最小触摸目标48dp
            .clip(RoundedCornerShape(OverlayDimens.chipCornerRadius))
            .background(backgroundColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(
                horizontal = 12.dp,                                        // 增加水平内边距
                vertical = 14.dp                                           // 增加垂直内边距
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = level.displayName,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// ==================== 人像虚化处理指示器 ====================

/**
 * 人像虚化处理指示器
 *
 * 显示虚化处理进度，用于拍照时显示处理状态
 *
 * @param isProcessing 是否正在处理中
 * @param progress 处理进度（0.0~1.0）
 * @param modifier 修饰符
 */
@Composable
fun PortraitBlurProcessingIndicator(
    isProcessing: Boolean,
    progress: Float,
    modifier: Modifier = Modifier
) {
    if (!isProcessing) return

    Box(
        modifier = modifier
            .background(
                color = Color.Black.copy(alpha = 0.8f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 圆形进度指示器
            PortraitBlurProgressRing(
                progress = progress,
                modifier = Modifier.size(24.dp)
            )

            // 进度文字
            Text(
                text = "正在虚化 ${(progress * 100).toInt()}%",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 人像虚化进度环
 *
 * 绘制圆形进度指示
 *
 * @param progress 进度值（0.0~1.0）
 * @param modifier 修饰符
 */
@Composable
private fun PortraitBlurProgressRing(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val dimens = rememberResponsiveDimens()                                           // 响应式尺寸
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = dimens.animation.fast),                // 响应式动画时长
        label = "blur_progress"
    )

    Canvas(modifier = modifier) {
        val strokeWidth = 3.dp.toPx()
        val radius = (size.minDimension - strokeWidth) / 2

        // 背景环
        drawCircle(
            color = Color.White.copy(alpha = 0.2f),
            radius = radius,
            style = Stroke(width = strokeWidth)
        )

        // 进度环
        drawArc(
            color = OverlayColors.accentYellow,
            startAngle = -90f,
            sweepAngle = animatedProgress * 360f,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
            size = Size(size.width - strokeWidth, size.height - strokeWidth)
        )
    }
}

// ==================== 人像虚化快捷按钮 ====================

/**
 * 人像模式虚化快捷按钮
 *
 * 小按钮形式，点击后循环切换虚化等级
 * 用于在相机界面快速调整虚化强度
 *
 * @param currentLevel 当前虚化等级
 * @param onClick 点击回调
 * @param modifier 修饰符
 */
@Composable
fun PortraitBlurQuickToggle(
    currentLevel: PortraitBlurLevel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = rememberResponsiveDimens()                               // 响应式尺寸
    val isEnabled = currentLevel != PortraitBlurLevel.NONE

    Box(
        modifier = modifier
            .size(dimens.minTouchTarget)                                  // 最小48dp触摸目标
            .clip(CircleShape)
            .background(
                if (isEnabled) OverlayColors.accentYellow.copy(alpha = 0.9f)
                else Color.White.copy(alpha = 0.2f)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 虚化图标（使用文字代替图标）
            Text(
                text = "f",
                color = if (isEnabled) Color.Black else Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            // 当前等级缩写
            Text(
                text = when (currentLevel) {
                    PortraitBlurLevel.NONE -> "关"
                    PortraitBlurLevel.LIGHT -> "轻"
                    PortraitBlurLevel.MEDIUM -> "中"
                    PortraitBlurLevel.HEAVY -> "强"
                },
                color = if (isEnabled) Color.Black else Color.White.copy(alpha = 0.8f),
                fontSize = 8.sp
            )
        }
    }
}

// ==================== 人脸追踪状态指示器 ====================

/**
 * 人脸追踪状态指示器
 *
 * 在人像模式下显示当前人脸追踪状态：
 * - IDLE: 未检测到人脸（灰色）
 * - TRACKING: 正在追踪人脸（绿色）
 * - LOST: 追踪目标丢失（红色）
 *
 * @param trackingState 当前追踪状态
 * @param modifier 修饰符
 */
@Composable
fun FaceTrackingStateIndicator(
    trackingState: FaceTrackingState,
    modifier: Modifier = Modifier
) {
    val (statusText, statusColor) = when (trackingState) {
        FaceTrackingState.IDLE -> "等待人脸" to Color.Gray
        FaceTrackingState.TRACKING -> "追踪中" to OverlayColors.accentGreen
        FaceTrackingState.LOST -> "追踪丢失" to Color(0xFFFF5722)
    }

    Row(
        modifier = modifier
            .background(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 状态指示点
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color = statusColor, shape = CircleShape)
        )

        // 状态文字
        Text(
            text = statusText,
            color = Color.White,
            fontSize = 12.sp
        )
    }
}

// ==================== 人脸追踪开关按钮 ====================

/**
 * 人脸追踪对焦开关按钮
 *
 * 用于在人像模式下切换自动追踪对焦功能
 * 开启时：自动追踪人脸并触发对焦
 * 关闭时：仅检测人脸用于UI显示，不自动对焦
 *
 * @param isEnabled 是否启用追踪对焦
 * @param trackingState 当前追踪状态
 * @param onClick 点击回调
 * @param modifier 修饰符
 */
@Composable
fun FaceTrackingToggleButton(
    isEnabled: Boolean,
    trackingState: FaceTrackingState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = rememberResponsiveDimens()                               // 响应式尺寸
    val isTracking = trackingState == FaceTrackingState.TRACKING
    val backgroundColor = when {
        isEnabled && isTracking -> OverlayColors.accentGreen.copy(alpha = 0.9f)
        isEnabled -> OverlayColors.accentYellow.copy(alpha = 0.8f)
        else -> Color.White.copy(alpha = 0.2f)
    }

    Box(
        modifier = modifier
            .size(dimens.minTouchTarget)                                  // 最小48dp触摸目标
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 追踪图标（使用文字代替图标）
            Text(
                text = "AF",
                color = if (isEnabled) Color.Black else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            // 状态文字
            Text(
                text = when {
                    isEnabled && isTracking -> "追踪"
                    isEnabled -> "等待"
                    else -> "关闭"
                },
                color = if (isEnabled) Color.Black else Color.White.copy(alpha = 0.8f),
                fontSize = 8.sp
            )
        }
    }
}

// ==================== 人像模式控制栏 ====================

/**
 * 人像模式组合控制栏
 *
 * 包含人脸追踪开关和虚化等级快捷按钮
 * 用于人像模式的快捷控制
 *
 * @param isTrackingEnabled 是否启用追踪对焦
 * @param trackingState 当前追踪状态
 * @param blurLevel 当前虚化等级
 * @param onTrackingToggle 追踪开关点击回调
 * @param onBlurToggle 虚化等级切换回调
 * @param modifier 修饰符
 */
@Composable
fun PortraitModeControlBar(
    isTrackingEnabled: Boolean,
    trackingState: FaceTrackingState,
    blurLevel: PortraitBlurLevel,
    onTrackingToggle: () -> Unit,
    onBlurToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                color = Color.Black.copy(alpha = 0.5f),
                shape = RoundedCornerShape(22.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 人脸追踪对焦开关
        FaceTrackingToggleButton(
            isEnabled = isTrackingEnabled,
            trackingState = trackingState,
            onClick = onTrackingToggle
        )

        // 虚化等级快捷按钮
        PortraitBlurQuickToggle(
            currentLevel = blurLevel,
            onClick = onBlurToggle
        )
    }
}

// ==================== 美颜快捷按钮 ====================

/**
 * 美颜快捷切换按钮
 *
 * 紧凑型按钮，点击循环切换美颜等级
 * 设计简洁，不遮挡预览画面
 *
 * @param currentLevel 当前美颜等级
 * @param onClick 点击回调
 * @param modifier 修饰符
 */
@Composable
fun BeautyQuickToggle(
    currentLevel: BeautyLevel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = rememberResponsiveDimens()
    val isEnabled = currentLevel != BeautyLevel.OFF

    Box(
        modifier = modifier
            .size(dimens.minTouchTarget)
            .clip(CircleShape)
            .background(
                if (isEnabled) OverlayColors.accentOrange.copy(alpha = 0.9f)
                else Color.White.copy(alpha = 0.2f)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 美颜图标（使用 Material Icon 替代 emoji，提升跨设备一致性）
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "美颜",
                tint = if (isEnabled) Color.Black else Color.White,
                modifier = Modifier.size(18.dp)
            )
            // 当前等级
            Text(
                text = if (currentLevel == BeautyLevel.OFF) "关" else "${currentLevel.level}",
                color = if (isEnabled) Color.Black else Color.White.copy(alpha = 0.8f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ==================== 紧凑型人像模式控制条 ====================

/**
 * 紧凑型人像模式控制条
 *
 * 简洁设计，整合美颜和虚化控制
 * 底部水平排列，不遮挡预览画面
 *
 * @param beautyLevel 当前美颜等级
 * @param blurLevel 当前虚化等级
 * @param onBeautyToggle 美颜等级切换回调
 * @param onBlurToggle 虚化等级切换回调
 * @param modifier 修饰符
 */
@Composable
fun CompactPortraitControls(
    beautyLevel: BeautyLevel,
    blurLevel: PortraitBlurLevel,
    onBeautyToggle: () -> Unit,
    onBlurToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                color = Color.Black.copy(alpha = 0.5f),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 美颜快捷按钮
        BeautyQuickToggle(
            currentLevel = beautyLevel,
            onClick = onBeautyToggle
        )

        // 虚化快捷按钮
        PortraitBlurQuickToggle(
            currentLevel = blurLevel,
            onClick = onBlurToggle
        )
    }
}
