/**
 * ViewfinderComponents.kt - 取景器UI组件
 *
 * 设计稿风格的取景器组件集合
 * 包含：对焦框、网格叠加、变焦控制（胶囊/刻度盘）、HDR标识等
 *
 * 设计参考：
 * - 对焦框：金色边框 + 四角标记
 * - 网格：3x3 九宫格（10%白色）
 * - 变焦：胶囊式按钮组 或 刻度盘式
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.presentation.camera.components

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qihao.filtercamera.presentation.common.theme.CameraTheme
import com.qihao.filtercamera.presentation.common.theme.rememberResponsiveDimens
import java.util.Locale

// ==================== 对焦框组件 ====================

/**
 * 对焦框叠加层
 *
 * 显示在预览中心的对焦框，包含：
 * - 金色边框（40%透明度）
 * - 四角金色标记（2px宽度）
 * - 使用响应式尺寸系统适配不同设备
 *
 * @param size 对焦框尺寸（默认使用响应式尺寸）
 * @param cornerLength 角标长度（默认使用响应式尺寸）
 * @param cornerWidth 角标宽度（默认使用响应式尺寸）
 */
@Composable
fun FocusFrameOverlay(
    modifier: Modifier = Modifier,
    size: Dp? = null,
    cornerLength: Dp? = null,
    cornerWidth: Dp? = null
) {
    val dimens = rememberResponsiveDimens()                                      // 响应式尺寸系统
    val actualSize = size ?: dimens.focusIndicatorSize                           // 响应式对焦框尺寸
    val actualCornerLength = cornerLength ?: dimens.focusCornerLength            // 响应式角标长度
    val actualCornerWidth = cornerWidth ?: dimens.focusStrokeWidth               // 响应式角标宽度

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // 主框体
        Box(
            modifier = Modifier
                .size(actualSize)
                .border(
                    width = 1.dp,
                    color = CameraTheme.FocusIndicator.border,                   // 对焦框边框
                    shape = RoundedCornerShape(dimens.radius.xxs)                // 响应式圆角
                )
        ) {
            // 四角标记
            // 左上角
            CornerMarker(
                cornerLength = actualCornerLength,
                cornerWidth = actualCornerWidth,
                modifier = Modifier.align(Alignment.TopStart).offset(x = (-1).dp, y = (-1).dp),
                topLeft = true
            )
            // 右上角
            CornerMarker(
                cornerLength = actualCornerLength,
                cornerWidth = actualCornerWidth,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 1.dp, y = (-1).dp),
                topRight = true
            )
            // 左下角
            CornerMarker(
                cornerLength = actualCornerLength,
                cornerWidth = actualCornerWidth,
                modifier = Modifier.align(Alignment.BottomStart).offset(x = (-1).dp, y = 1.dp),
                bottomLeft = true
            )
            // 右下角
            CornerMarker(
                cornerLength = actualCornerLength,
                cornerWidth = actualCornerWidth,
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = 1.dp, y = 1.dp),
                bottomRight = true
            )
        }
    }
}

/**
 * 角标组件
 *
 * 绘制L形角标
 */
@Composable
private fun CornerMarker(
    cornerLength: Dp,
    cornerWidth: Dp,
    modifier: Modifier = Modifier,
    topLeft: Boolean = false,
    topRight: Boolean = false,
    bottomLeft: Boolean = false,
    bottomRight: Boolean = false
) {
    Box(modifier = modifier.size(cornerLength)) {
        val color = CameraTheme.FocusIndicator.corner                            // 角标颜色
        val widthPx = cornerWidth

        when {
            topLeft -> {
                // 水平线（顶部）
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .width(cornerLength)
                        .height(widthPx)
                        .background(color)
                )
                // 垂直线（左侧）
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .width(widthPx)
                        .height(cornerLength)
                        .background(color)
                )
            }
            topRight -> {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .width(cornerLength)
                        .height(widthPx)
                        .background(color)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .width(widthPx)
                        .height(cornerLength)
                        .background(color)
                )
            }
            bottomLeft -> {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .width(cornerLength)
                        .height(widthPx)
                        .background(color)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .width(widthPx)
                        .height(cornerLength)
                        .background(color)
                )
            }
            bottomRight -> {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .width(cornerLength)
                        .height(widthPx)
                        .background(color)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .width(widthPx)
                        .height(cornerLength)
                        .background(color)
                )
            }
        }
    }
}

// ==================== 网格叠加层组件 ====================

/**
 * 3x3 九宫格叠加层
 *
 * 显示取景器的三分法构图辅助线
 */
@Composable
fun GridOverlay(
    modifier: Modifier = Modifier,
    lineColor: Color = CameraTheme.DocumentScan.gridLine                         // 网格线颜色
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val strokeWidth = 1.dp.toPx()
                val thirdWidth = size.width / 3
                val thirdHeight = size.height / 3

                // 垂直线
                for (i in 1..2) {
                    drawLine(
                        color = lineColor,
                        start = Offset(thirdWidth * i, 0f),
                        end = Offset(thirdWidth * i, size.height),
                        strokeWidth = strokeWidth
                    )
                }

                // 水平线
                for (i in 1..2) {
                    drawLine(
                        color = lineColor,
                        start = Offset(0f, thirdHeight * i),
                        end = Offset(size.width, thirdHeight * i),
                        strokeWidth = strokeWidth
                    )
                }
            }
    )
}

// ==================== 变焦控制组件（胶囊式） ====================

/**
 * 胶囊式变焦控制
 *
 * 设计稿样式：iOS毛玻璃背景胶囊 + 多个变焦倍数按钮
 * 例如：[0.5] [1x] [2] [5]
 * 使用响应式尺寸系统适配不同设备
 *
 * 视觉效果：
 * - Android 12+：真实毛玻璃模糊效果（backdrop-filter: blur(20px)）
 * - 低版本：半透明深色背景降级
 *
 * @param currentZoom 当前变焦倍数
 * @param zoomOptions 可选变焦倍数列表
 * @param onZoomSelected 变焦选择回调
 */
@Composable
fun ZoomPillControl(
    currentZoom: Float,
    zoomOptions: List<Float> = listOf(0.5f, 1f, 2f, 5f),
    onZoomSelected: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = rememberResponsiveDimens()                                      // 响应式尺寸系统

    // iOS风格毛玻璃效果容器
    val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.graphicsLayer {                                         // Android 12+ 真实模糊
            renderEffect = RenderEffect.createBlurEffect(
                20f, 20f, Shader.TileMode.CLAMP
            ).asComposeRenderEffect()
        }
    } else {
        Modifier                                                         // 低版本无模糊
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))                                // 胶囊形状
            .then(blurModifier)                                          // 毛玻璃效果（Android 12+）
            .background(
                color = CameraTheme.ZoomControl.pillBackground           // 胶囊背景色
            )
            .border(
                width = 1.dp,
                color = CameraTheme.ZoomControl.pillBorder,              // 胶囊边框色
                shape = RoundedCornerShape(50)
            )
            .padding(horizontal = dimens.spacing.md, vertical = dimens.spacing.sm), // 响应式内边距
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm), // 响应式按钮间隔
        verticalAlignment = Alignment.CenterVertically
    ) {
        zoomOptions.forEach { zoom ->
            val isSelected = kotlin.math.abs(currentZoom - zoom) < 0.1f  // 浮点比较
            ZoomButton(
                zoom = zoom,
                isSelected = isSelected,
                onClick = { onZoomSelected(zoom) }
            )
        }
    }
}

/**
 * 单个变焦按钮
 *
 * 设计稿样式：
 * - 选中时：金色文字 + 微背景 + 缩放动画
 * - 未选中：白色50%透明文字
 * - 点击效果：缩放动画（active:scale-95）
 * - 使用响应式尺寸系统
 *
 * @param zoom 变焦倍数
 * @param isSelected 是否选中
 * @param onClick 点击回调
 */
@Composable
private fun ZoomButton(
    zoom: Float,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val dimens = rememberResponsiveDimens()                                      // 响应式尺寸系统
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // 点击缩放效果
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = tween(dimens.animation.instant),                         // 响应式动画时长
        label = "zoomPressScale"
    )

    val textColor by animateColorAsState(
        targetValue = if (isSelected) CameraTheme.ZoomControl.buttonActive else CameraTheme.ZoomControl.buttonInactive,
        animationSpec = tween(dimens.animation.fast),                            // 响应式动画时长
        label = "zoomTextColor"
    )

    val bgAlpha by animateFloatAsState(
        targetValue = if (isSelected) 0.2f else 0f,                  // 选中时稍强背景
        animationSpec = tween(dimens.animation.fast),
        label = "zoomBgAlpha"
    )

    // 按钮尺寸：使用响应式变焦按钮尺寸
    val buttonSize = if (isSelected) dimens.zoomButtonSize else (dimens.zoomButtonSize - 4.dp)

    Box(
        modifier = Modifier
            .size(buttonSize)
            .scale(pressScale)                                           // 点击缩放效果
            .clip(CircleShape)
            .background(Color.White.copy(alpha = bgAlpha))
            .then(
                if (isSelected) {
                    Modifier.border(1.dp, CameraTheme.Colors.primary.copy(alpha = 0.4f), CircleShape)
                } else {
                    Modifier
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, radius = 16.dp, color = CameraTheme.Colors.primary),  // 涟漪效果
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        val displayText = when {
            zoom == 1f -> "1x"
            zoom < 1f -> "$zoom"
            zoom == zoom.toInt().toFloat() -> "${zoom.toInt()}"
            else -> String.format(Locale.US, "%.1f", zoom)
        }
        Text(
            text = displayText,
            color = textColor,
            fontSize = if (isSelected) 12.sp else 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

// ==================== 变焦胶囊 + 滤镜按钮组合组件 ====================

/**
 * 变焦胶囊 + 滤镜按钮组合
 *
 * 设计稿样式：左侧变焦胶囊 + 右侧滤镜按钮（wand_stars图标）
 * 整体水平居中布局
 * 使用响应式尺寸系统
 *
 * @param currentZoom 当前变焦倍数
 * @param zoomOptions 可选变焦倍数列表
 * @param isFilterActive 滤镜是否激活（非原图滤镜时激活）
 * @param onZoomSelected 变焦选择回调
 * @param onFilterClick 滤镜按钮点击回调
 */
@Composable
fun ZoomPillWithFilterButton(
    currentZoom: Float,
    zoomOptions: List<Float> = listOf(0.5f, 1f, 2f, 5f),
    isFilterActive: Boolean = false,
    onZoomSelected: (Float) -> Unit,
    onFilterClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = rememberResponsiveDimens()                                      // 响应式尺寸系统

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.lg),         // 响应式间距
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 变焦胶囊
        ZoomPillControl(
            currentZoom = currentZoom,
            zoomOptions = zoomOptions,
            onZoomSelected = onZoomSelected
        )

        // 滤镜按钮（魔法棒图标）
        FilterIconButton(
            isActive = isFilterActive,
            onClick = onFilterClick
        )
    }
}

/**
 * 滤镜图标按钮（魔法棒）
 *
 * 设计稿样式：
 * - 响应式尺寸（overlayButtonSize）
 * - rounded-full bg-black/60 ios-blur
 * - border border-white/20
 * - active:scale-90
 * - 图标：wand_stars（响应式尺寸）
 *
 * @param isActive 滤镜是否激活
 * @param onClick 点击回调
 */
@Composable
fun FilterIconButton(
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = rememberResponsiveDimens()                                      // 响应式尺寸系统
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // 点击缩放效果（设计稿：active:scale-90）
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = tween(dimens.animation.instant),                         // 响应式动画时长
        label = "filterPressScale"
    )

    // 图标颜色动画
    val iconColor by animateColorAsState(
        targetValue = if (isActive) CameraTheme.Colors.primary else Color.White,
        animationSpec = tween(dimens.animation.fast),
        label = "filterIconColor"
    )

    // 背景颜色动画
    val bgColor by animateColorAsState(
        targetValue = if (isActive) CameraTheme.Colors.primary.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.6f),
        animationSpec = tween(dimens.animation.fast),
        label = "filterBgColor"
    )

    // iOS风格毛玻璃效果（Android 12+）
    val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.graphicsLayer {
            renderEffect = RenderEffect.createBlurEffect(
                20f, 20f, Shader.TileMode.CLAMP
            ).asComposeRenderEffect()
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .size(dimens.overlayButtonSize + dimens.spacing.xs)                  // 响应式尺寸
            .scale(pressScale)                                            // 点击缩放效果
            .clip(CircleShape)                                            // rounded-full
            .then(blurModifier)                                           // iOS毛玻璃效果
            .background(bgColor)                                          // bg-black/60
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.2f),                   // border-white/20
                shape = CircleShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = CameraTheme.Colors.primary),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,                      // 魔法棒图标（wand_stars）
            contentDescription = "滤镜",
            tint = iconColor,
            modifier = Modifier.size(dimens.overlayIconSize + 4.dp)       // 响应式图标尺寸
        )
    }
}

// ==================== 变焦控制组件（刻度盘式） ====================

/**
 * 刻度盘式变焦控制
 *
 * 设计稿精简版样式：当前倍数显示 + 刻度线
 * 使用响应式尺寸系统
 *
 * @param currentZoom 当前变焦倍数
 * @param onZoomChanged 变焦变化回调
 */
@Composable
fun ZoomDialControl(
    currentZoom: Float,
    onZoomChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = rememberResponsiveDimens()                                      // 响应式尺寸系统

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 当前变焦显示
        Row(
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = String.format(Locale.US, "%.1f", currentZoom),
                color = CameraTheme.Colors.primary,                              // 变焦文字颜色
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "x",
                color = CameraTheme.Colors.primary,                              // 变焦单位颜色
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }

        Spacer(modifier = Modifier.height(dimens.spacing.md))                    // 响应式间距

        // 刻度线区域
        ZoomDialTicks(
            currentZoom = currentZoom,
            modifier = Modifier
                .fillMaxWidth()
                .height(dimens.zoomSliderHeight / 3)                             // 响应式高度
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            CameraTheme.Colors.shadowDark.copy(alpha = 0.3f),    // 刻度盘背景
                            CameraTheme.Colors.shadowDark.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

/**
 * 刻度线绘制
 */
@Composable
private fun ZoomDialTicks(
    currentZoom: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier.padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // 左侧刻度线
            repeat(14) { index ->
                val isMajor = index % 5 == 4
                Box(
                    modifier = Modifier
                        .width(if (isMajor) 1.5.dp else 1.dp)
                        .height(if (isMajor) 12.dp else 7.dp)
                        .background(if (isMajor) CameraTheme.ZoomControl.tickMajor else CameraTheme.ZoomControl.tickMinor)
                )
            }

            // 中心指示器
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(48.dp)
                    .offset(y = (-14).dp)
                    .background(CameraTheme.ZoomControl.indicator)
            )

            // 右侧刻度线
            repeat(14) { index ->
                val isMajor = index % 5 == 0
                Box(
                    modifier = Modifier
                        .width(if (isMajor) 1.5.dp else 1.dp)
                        .height(if (isMajor) 12.dp else 7.dp)
                        .background(if (isMajor) CameraTheme.ZoomControl.tickMajor else CameraTheme.ZoomControl.tickMinor)
                )
            }
        }
    }
}

// ==================== HDR 标识组件 ====================

/**
 * HDR 标识
 *
 * 设计稿样式：金色边框 + 金色文字（激活时）
 * 点击效果：缩放动画（active:scale-95）
 * 使用响应式尺寸系统
 *
 * @param isActive HDR是否激活
 * @param onClick 点击回调
 */
@Composable
fun HdrBadge(
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = rememberResponsiveDimens()                                      // 响应式尺寸系统
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // 点击缩放效果
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = tween(dimens.animation.instant),                         // 响应式动画时长
        label = "hdrPressScale"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isActive) CameraTheme.Overlay.badge else CameraTheme.Colors.textTertiary.copy(alpha = 0.5f),
        animationSpec = tween(dimens.animation.fast),
        label = "hdrBorderColor"
    )

    val textColor by animateColorAsState(
        targetValue = if (isActive) CameraTheme.Colors.textPrimary else CameraTheme.Colors.textTertiary,
        animationSpec = tween(dimens.animation.fast),
        label = "hdrTextColor"
    )

    Box(
        modifier = modifier
            .scale(pressScale)                                           // 点击缩放效果
            .clip(RoundedCornerShape(dimens.radius.xxs))                 // 响应式圆角
            .border(1.dp, borderColor, RoundedCornerShape(dimens.radius.xxs)) // 响应式圆角
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = CameraTheme.Colors.primary),    // 涟漪效果
                onClick = onClick
            )
            .padding(horizontal = dimens.spacing.sm, vertical = dimens.spacing.xs), // 响应式内边距
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "HDR",
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp
        )
    }
}

// ==================== 画幅比例按钮 ====================

/**
 * 画幅比例按钮
 *
 * 设计稿样式：毛玻璃背景胶囊 + 白色文字
 * 点击效果：缩放动画（active:scale-95）
 * 使用响应式尺寸系统
 *
 * @param aspectRatio 当前画幅比例（如 "16:9", "4:3"）
 * @param onClick 点击回调
 */
@Composable
fun AspectRatioButton(
    aspectRatio: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = rememberResponsiveDimens()                                      // 响应式尺寸系统
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // 点击缩放效果
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = tween(dimens.animation.instant),                         // 响应式动画时长
        label = "aspectPressScale"
    )

    Box(
        modifier = modifier
            .scale(pressScale)                                           // 点击缩放效果
            .clip(RoundedCornerShape(50))
            .background(CameraTheme.Colors.controlBackgroundLight)       // 统一控件背景色
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = Color.White.copy(alpha = 0.3f)),
                onClick = onClick
            )
            .padding(horizontal = dimens.spacing.md, vertical = dimens.spacing.xs), // 响应式内边距
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = aspectRatio,
            color = CameraTheme.Colors.textPrimary,                      // 统一文字颜色
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}
