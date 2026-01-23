/**
 * SharedOverlayComponents.kt - 模式覆盖层共享组件
 *
 * 提供所有模式覆盖层使用的共享配置和通用组件
 *
 * 组件列表：
 * - OverlayColors: 统一颜色配置
 * - OverlayDimens: 统一尺寸配置
 * - TimerCountdownOverlay: 定时拍照倒计时覆盖层
 *
 * @author qihao
 * @since 3.0.0
 */
package com.qihao.filtercamera.presentation.camera.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qihao.filtercamera.presentation.common.theme.CameraTheme
import com.qihao.filtercamera.presentation.common.theme.rememberResponsiveDimens

// ==================== 全局配置常量 ====================

/**
 * 模式覆盖层颜色配置
 *
 * 集中管理所有覆盖层使用的颜色，引用 CameraTheme 统一主题
 */
object OverlayColors {
    val faceBoxColor = CameraTheme.Colors.primary                         // 人脸框：金色
    val documentBorderColor = CameraTheme.DocumentScan.frameBorder        // 文档边框：绿色
    val accentYellow = CameraTheme.Colors.primary                         // 强调色：金色
    val accentOrange = CameraTheme.Colors.warning                         // 强调色：橙色
    val accentGreen = CameraTheme.Colors.success                          // 状态色：成功绿色
    val panelBackground = CameraTheme.Overlay.background                  // 面板背景
    val hintBackground = CameraTheme.Overlay.backgroundLight              // 提示背景
    val chipUnselected = CameraTheme.Colors.controlBackgroundLight        // 芯片未选中
    val trackInactive = CameraTheme.Colors.textTertiary                   // 滑块轨道未激活
}

/**
 * 模式覆盖层尺寸配置
 *
 * 集中管理所有覆盖层使用的尺寸
 * 注意：这些是静态默认值，响应式组件应使用 rememberResponsiveDimens()
 */
object OverlayDimens {
    val panelCornerRadius = 14.dp                                    // 面板圆角
    val panelPadding = 12.dp                                         // 面板内边距
    val chipCornerRadius = 14.dp                                     // 芯片圆角
    val chipHorizontalPadding = 10.dp                                // 芯片水平内边距
    val chipVerticalPadding = 5.dp                                   // 芯片垂直内边距
    val itemSpacing = 6.dp                                           // 列表项间距
    val rowSpacing = 10.dp                                           // 行间距
    val labelSpacing = 6.dp                                          // 标签间距
    val sliderSpacing = 3.dp                                         // 滑块间距
    val hintCornerRadius = 16.dp                                     // 提示框圆角
    val statusDotSize = 8.dp                                         // 状态指示点大小
}

// ==================== 定时拍照倒计时覆盖层 ====================

/**
 * 定时拍照倒计时覆盖层
 *
 * 全屏显示大数字倒计时，带动画效果
 * 用于定时拍照模式下显示剩余秒数
 *
 * @param isVisible 是否可见（正在倒计时）
 * @param countdownSeconds 剩余秒数
 * @param onCancel 取消倒计时回调（点击屏幕取消）
 * @param modifier 修饰符
 */
@Composable
fun TimerCountdownOverlay(
    isVisible: Boolean,
    countdownSeconds: Int,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 仅当可见时显示
    if (!isVisible || countdownSeconds <= 0) return

    val dimens = rememberResponsiveDimens()                                       // 响应式尺寸

    // 数字缩放动画：每次数字变化时触发缩放效果
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(
            durationMillis = dimens.animation.normal,                             // 响应式动画时长
            easing = FastOutSlowInEasing
        ),
        label = "countdownScale"
    )

    // 透明度动画
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = dimens.animation.fast),            // 响应式动画时长
        label = "countdownAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))                    // 半透明黑色背景
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,                                          // 无点击涟漪
                onClick = onCancel                                          // 点击取消倒计时
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 大数字倒计时
            Text(
                text = "$countdownSeconds",
                color = OverlayColors.accentYellow.copy(alpha = alpha),
                fontSize = 160.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(bottom = 16.dp)
            )

            // 取消提示
            Text(
                text = "点击屏幕取消",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
        }
    }
}
