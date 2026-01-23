/**
 * TimelapseModeOverlay.kt - 延时摄影模式覆盖层组件
 *
 * 提供延时摄影模式下的控制和状态显示组件
 *
 * 组件列表：
 * - TimelapseModeHint: 延时摄影状态提示
 * - TimelapseEncodingIndicator: 编码进度指示器
 * - TimelapseProgressRing: 进度圆环
 * - TimelapseControlPanel: 控制面板
 * - TimelapseButton: 控制按钮
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
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qihao.filtercamera.presentation.common.theme.rememberResponsiveDimens
import java.util.Locale

// ==================== 延时摄影模式提示 ====================

/**
 * 延时摄影模式提示
 *
 * 显示延时摄影的状态提示
 * 包括录制状态、已捕获帧数、已用时间
 *
 * @param isRecording 是否正在录制
 * @param framesCaptured 已捕获帧数
 * @param elapsedMs 已用时间（毫秒）
 * @param modifier 修饰符
 */
@Composable
fun TimelapseModeHint(
    isRecording: Boolean,
    framesCaptured: Int,
    elapsedMs: Long,
    modifier: Modifier = Modifier
) {
    val elapsedSeconds = (elapsedMs / 1000).toInt()                                // 转换为秒
    val minutes = elapsedSeconds / 60                                              // 分钟
    val seconds = elapsedSeconds % 60                                              // 秒
    val timeText = String.format(Locale.US, "%02d:%02d", minutes, seconds)       // 格式化时间

    val text = when {
        !isRecording && framesCaptured == 0 -> "点击开始延时摄影"
        isRecording -> "$framesCaptured 帧 · $timeText"
        else -> "已捕获 $framesCaptured 帧"
    }
    val color = if (isRecording) OverlayColors.accentOrange else OverlayColors.accentGreen

    Box(
        modifier = modifier
            .background(
                color = OverlayColors.hintBackground,
                shape = RoundedCornerShape(OverlayDimens.hintCornerRadius)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 录制指示点（录制中时闪烁效果用颜色区分）
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

// ==================== 延时摄影编码进度指示器 ====================

/**
 * 延时摄影编码进度指示器
 *
 * 当延时摄影停止后编码视频时显示的进度覆盖层
 * 显示编码进度百分比
 *
 * @param isEncoding 是否正在编码
 * @param progress 编码进度（0.0~1.0）
 * @param modifier 修饰符
 */
@Composable
fun TimelapseEncodingIndicator(
    isEncoding: Boolean,
    progress: Float,
    modifier: Modifier = Modifier
) {
    // 只有在编码时才显示
    if (!isEncoding) return

    val dimens = rememberResponsiveDimens()                                         // 响应式尺寸

    // 动画化进度值
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = dimens.animation.fast),              // 使用响应式动画时长
        label = "timelapse_encoding_progress"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
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
            TimelapseProgressRing(
                progress = animatedProgress,
                modifier = Modifier.size(120.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 处理阶段文字
            Text(
                text = "正在编码视频",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 进度百分比
            Text(
                text = "${(animatedProgress * 100).toInt()}%",
                color = OverlayColors.accentOrange,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 提示文字
            Text(
                text = "请勿退出应用",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp
            )
        }
    }
}

// ==================== 延时摄影进度圆环 ====================

/**
 * 延时摄影进度圆环
 *
 * 参考小米相机的圆环进度设计，用于编码进度显示
 *
 * @param progress 进度（0.0~1.0）
 * @param modifier 修饰符
 * @param strokeWidth 圆环宽度
 * @param trackColor 轨道颜色
 * @param progressColor 进度颜色
 */
@Composable
private fun TimelapseProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 8.dp,
    trackColor: Color = Color.White.copy(alpha = 0.2f),
    progressColor: Color = OverlayColors.accentOrange
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
            startAngle = -90f,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(strokePx / 2, strokePx / 2),
            size = Size(diameter - strokePx, diameter - strokePx),
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )

        // 绘制中心延时摄影图标（时钟形状）
        drawTimelapseIcon(
            center = Offset(size.width / 2, size.height / 2),
            iconRadius = radius * 0.3f,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}

/**
 * 绘制延时摄影时钟图标
 *
 * 在进度圆环中心绘制简化的时钟图标
 */
private fun DrawScope.drawTimelapseIcon(
    center: Offset,
    iconRadius: Float,
    color: Color
) {
    // 绘制时钟外圈
    drawCircle(
        color = color,
        radius = iconRadius,
        center = center,
        style = Stroke(width = 2f)
    )

    // 绘制时钟指针（分针 - 指向12点）
    val minuteHandEnd = Offset(center.x, center.y - iconRadius * 0.6f)
    drawLine(
        color = color,
        start = center,
        end = minuteHandEnd,
        strokeWidth = 2f,
        cap = StrokeCap.Round
    )

    // 绘制时钟指针（时针 - 指向3点）
    val hourHandEnd = Offset(center.x + iconRadius * 0.4f, center.y)
    drawLine(
        color = color,
        start = center,
        end = hourHandEnd,
        strokeWidth = 3f,
        cap = StrokeCap.Round
    )

    // 绘制中心点
    drawCircle(
        color = color,
        radius = 3f,
        center = center
    )
}

// ==================== 延时摄影控制面板 ====================

/**
 * 延时摄影控制面板
 *
 * 显示延时摄影的录制控制和状态信息
 * 包括开始/停止/取消按钮、帧数显示、已用时间
 *
 * @param isRecording 是否正在录制
 * @param framesCaptured 已捕获帧数
 * @param elapsedMs 已用时间（毫秒）
 * @param onStart 开始录制回调
 * @param onStop 停止录制并编码回调
 * @param onCancel 取消录制回调
 * @param modifier 修饰符
 */
@Composable
fun TimelapseControlPanel(
    isRecording: Boolean,
    framesCaptured: Int,
    elapsedMs: Long,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val elapsedSeconds = (elapsedMs / 1000).toInt()
    val minutes = elapsedSeconds / 60
    val seconds = elapsedSeconds % 60
    val timeText = String.format(Locale.US, "%02d:%02d", minutes, seconds)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = OverlayColors.panelBackground,
                shape = RoundedCornerShape(
                    topStart = OverlayDimens.panelCornerRadius,
                    topEnd = OverlayDimens.panelCornerRadius
                )
            )
            .padding(OverlayDimens.panelPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 状态信息行
        if (isRecording || framesCaptured > 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // 帧数显示
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$framesCaptured",
                        color = OverlayColors.accentOrange,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "帧",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }

                // 时间显示
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = timeText,
                        color = OverlayColors.accentOrange,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "时长",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }

                // 预估输出时长（假设30fps）
                val estimatedOutputSec = framesCaptured / 30f
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = String.format(Locale.US, "%.1f", estimatedOutputSec),
                        color = OverlayColors.accentGreen,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "输出秒",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        // 控制按钮行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            if (isRecording) {
                // 录制中：显示停止和取消按钮
                TimelapseButton(
                    text = "取消",
                    color = Color.Gray,
                    onClick = onCancel
                )
                TimelapseButton(
                    text = "完成",
                    color = OverlayColors.accentGreen,
                    onClick = onStop
                )
            } else {
                // 未录制：显示开始按钮
                TimelapseButton(
                    text = if (framesCaptured > 0) "继续" else "开始",
                    color = OverlayColors.accentOrange,
                    onClick = onStart
                )
                if (framesCaptured > 0) {
                    TimelapseButton(
                        text = "完成",
                        color = OverlayColors.accentGreen,
                        onClick = onStop
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 提示文字
        Text(
            text = if (isRecording) "录制中，请保持稳定" else "点击开始录制延时摄影",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp
        )
    }
}

/**
 * 延时摄影控制按钮
 *
 * @param text 按钮文字
 * @param color 按钮颜色
 * @param onClick 点击回调
 * @param modifier 修饰符
 */
@Composable
private fun TimelapseButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
