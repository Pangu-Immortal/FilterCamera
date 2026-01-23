/**
 * NightModeOverlay.kt - 夜景模式覆盖层组件
 *
 * 提供夜景模式下的状态提示和处理进度组件
 *
 * 组件列表：
 * - NightModeHint: 夜景模式状态提示
 * - NightProcessingIndicator: 夜景处理进度指示器
 * - NightProgressRing: 进度圆环
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qihao.filtercamera.presentation.common.theme.rememberResponsiveDimens

// ==================== 夜景模式提示 ====================

/**
 * 夜景模式提示
 *
 * 显示夜景模式状态提示
 * 提醒用户保持稳定以获得最佳效果
 *
 * @param isOptimizing 是否正在进行夜景优化
 * @param modifier 修饰符
 */
@Composable
fun NightModeHint(
    isOptimizing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val text = if (isOptimizing) "正在进行夜景优化..." else "夜景模式已启用，请保持稳定"
    val color = if (isOptimizing) OverlayColors.accentYellow else OverlayColors.accentGreen

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

// ==================== 夜景处理进度指示器 ====================

/**
 * 夜景处理进度指示器
 *
 * 当夜景模式正在处理图像时显示的进度覆盖层
 * 显示当前处理阶段和进度百分比
 *
 * 设计参考小米相机夜景处理UI：
 * - 全屏半透明遮罩
 * - 居中进度圆环
 * - 显示处理阶段和进度
 *
 * @param isProcessing 是否正在处理
 * @param progress 处理进度（0.0~1.0）
 * @param stageName 当前处理阶段名称（如"捕获中"、"降噪中"等）
 * @param modifier 修饰符
 */
@Composable
fun NightProcessingIndicator(
    isProcessing: Boolean,
    progress: Float,
    stageName: String = "处理中",
    modifier: Modifier = Modifier
) {
    // 只有在处理时才显示
    if (!isProcessing) return

    val dimens = rememberResponsiveDimens()                                         // 响应式尺寸

    // 动画化进度值
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = dimens.animation.fast),              // 使用响应式动画时长
        label = "night_progress"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))                           // 半透明遮罩
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* 消费点击事件，防止穿透 */ },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 进度圆环
            NightProgressRing(
                progress = animatedProgress,
                modifier = Modifier.size(120.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 处理阶段文字
            Text(
                text = stageName,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 进度百分比
            Text(
                text = "${(animatedProgress * 100).toInt()}%",
                color = OverlayColors.accentYellow,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 提示文字
            Text(
                text = "请保持手机稳定",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp
            )
        }
    }
}

// ==================== 夜景进度圆环 ====================

/**
 * 夜景处理进度圆环
 *
 * 参考小米相机的圆环进度设计
 *
 * @param progress 进度（0.0~1.0）
 * @param modifier 修饰符
 * @param strokeWidth 圆环宽度
 * @param trackColor 轨道颜色
 * @param progressColor 进度颜色
 */
@Composable
private fun NightProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 8.dp,
    trackColor: Color = Color.White.copy(alpha = 0.2f),
    progressColor: Color = OverlayColors.accentYellow
) {
    Canvas(modifier = modifier) {
        val diameter = size.minDimension
        val radius = diameter / 2
        val strokePx = strokeWidth.toPx()

        // 绘制背景轨道
        drawCircle(
            color = trackColor,
            radius = radius - strokePx / 2,
            style = Stroke(width = strokePx)
        )

        // 绘制进度弧
        val sweepAngle = progress * 360f
        drawArc(
            color = progressColor,
            startAngle = -90f,                                                      // 从12点方向开始
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(strokePx / 2, strokePx / 2),
            size = Size(diameter - strokePx, diameter - strokePx),
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )

        // 绘制中心月亮图标（夜景模式标识）
        drawNightModeIcon(
            center = Offset(size.width / 2, size.height / 2),
            iconRadius = radius * 0.3f,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}

/**
 * 绘制夜景模式月亮图标
 *
 * 在进度圆环中心绘制简化的月亮图标
 */
private fun DrawScope.drawNightModeIcon(
    center: Offset,
    iconRadius: Float,
    color: Color
) {
    // 使用简化的月牙形状
    val moonPath = Path().apply {
        // 月亮外圆
        addOval(
            Rect(
                left = center.x - iconRadius,
                top = center.y - iconRadius,
                right = center.x + iconRadius,
                bottom = center.y + iconRadius
            )
        )
    }

    // 绘制月牙遮挡部分（使用背景色创建月牙效果）
    val cutoutOffset = iconRadius * 0.5f
    val cutoutPath = Path().apply {
        addOval(
            Rect(
                left = center.x - iconRadius + cutoutOffset,
                top = center.y - iconRadius - cutoutOffset * 0.3f,
                right = center.x + iconRadius + cutoutOffset,
                bottom = center.y + iconRadius - cutoutOffset * 0.3f
            )
        )
    }

    // 绘制月牙
    drawPath(moonPath, color = color)
    drawPath(cutoutPath, color = Color.Black.copy(alpha = 0.7f))                   // 遮挡创建月牙
}
