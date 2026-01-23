/**
 * DocumentModeOverlay.kt - 文档扫描模式覆盖层组件
 *
 * 提供文档扫描模式下的边界检测、模式选择等控制组件
 *
 * 组件列表：
 * - DocumentBoundsOverlay: 文档边界覆盖层
 * - DocumentModeHint: 文档模式提示
 * - DocumentScanModeUI: 文档扫描模式枚举
 * - DocumentScanModeSelector: 文档扫描模式选择器
 * - DocumentScanModeQuickToggle: 模式快捷切换按钮
 * - DocumentModeControlBar: 文档模式控制栏
 * - CompactDocumentScanModeSelector: 紧凑版模式选择器
 *
 * @author qihao
 * @since 3.0.0
 */
package com.qihao.filtercamera.presentation.camera.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qihao.filtercamera.domain.model.DocumentBounds
import com.qihao.filtercamera.presentation.common.theme.rememberResponsiveDimens

// ==================== 文档边界覆盖层 ====================

/**
 * 文档边界覆盖层
 *
 * 在检测到的文档边界绘制四角直角边框（类似小米相机样式）
 * 使用黄色/橙色边框，带呼吸动画效果
 *
 * @param bounds 文档边界
 * @param modifier 修饰符
 */
@Composable
fun DocumentBoundsOverlay(
    bounds: DocumentBounds?,
    modifier: Modifier = Modifier
) {
    if (bounds == null) return

    // 透明度动画（根据检测置信度）
    val alpha by animateFloatAsState(
        targetValue = if (bounds.confidence > 0.5f) 1f else 0.5f,
        animationSpec = tween(200),
        label = "documentOverlayAlpha"
    )

    // 呼吸动画（边框缩放）
    val infiniteTransition = rememberInfiniteTransition(label = "documentPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "documentPulseScale"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        drawDocumentCornerBrackets(bounds, alpha, pulseScale)
    }
}

/**
 * 绘制文档四角直角边框
 *
 * 绘制四个角的L形边框（小米相机样式）
 * 使用黄色/橙色颜色，带半透明填充
 *
 * @param bounds 文档边界
 * @param alpha 透明度
 * @param scale 缩放比例（呼吸动画）
 */
private fun DrawScope.drawDocumentCornerBrackets(
    bounds: DocumentBounds,
    alpha: Float,
    scale: Float
) {
    val w = size.width
    val h = size.height

    // 使用黄色/橙色作为文档边框颜色（类似小米相机）
    val borderColor = Color(0xFFFF9500)                                   // 橙黄色
    val fillColor = borderColor.copy(alpha = 0.08f * alpha)               // 半透明填充

    // 计算四个角的坐标
    val topLeft = Offset(bounds.topLeft.x * w, bounds.topLeft.y * h)
    val topRight = Offset(bounds.topRight.x * w, bounds.topRight.y * h)
    val bottomRight = Offset(bounds.bottomRight.x * w, bounds.bottomRight.y * h)
    val bottomLeft = Offset(bounds.bottomLeft.x * w, bounds.bottomLeft.y * h)

    // 计算边框角的长度（相对于边长的比例）
    val cornerLengthRatio = 0.15f                                          // 角的长度占边长的15%
    val strokeWidth = 4f * scale                                          // 边框宽度（带呼吸效果）

    // 绘制半透明填充区域
    val fillPath = Path().apply {
        moveTo(topLeft.x, topLeft.y)
        lineTo(topRight.x, topRight.y)
        lineTo(bottomRight.x, bottomRight.y)
        lineTo(bottomLeft.x, bottomLeft.y)
        close()
    }
    drawPath(path = fillPath, color = fillColor)

    // 绘制四个角的L形边框
    val cornerColor = borderColor.copy(alpha = alpha)

    // 左上角
    val topLeftCornerLen = minOf(
        (topRight.x - topLeft.x) * cornerLengthRatio,
        (bottomLeft.y - topLeft.y) * cornerLengthRatio
    )
    drawLine(
        color = cornerColor,
        start = topLeft,
        end = Offset(topLeft.x + topLeftCornerLen, topLeft.y),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    drawLine(
        color = cornerColor,
        start = topLeft,
        end = Offset(topLeft.x, topLeft.y + topLeftCornerLen),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )

    // 右上角
    val topRightCornerLen = minOf(
        (topRight.x - topLeft.x) * cornerLengthRatio,
        (bottomRight.y - topRight.y) * cornerLengthRatio
    )
    drawLine(
        color = cornerColor,
        start = topRight,
        end = Offset(topRight.x - topRightCornerLen, topRight.y),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    drawLine(
        color = cornerColor,
        start = topRight,
        end = Offset(topRight.x, topRight.y + topRightCornerLen),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )

    // 右下角
    val bottomRightCornerLen = minOf(
        (bottomRight.x - bottomLeft.x) * cornerLengthRatio,
        (bottomRight.y - topRight.y) * cornerLengthRatio
    )
    drawLine(
        color = cornerColor,
        start = bottomRight,
        end = Offset(bottomRight.x - bottomRightCornerLen, bottomRight.y),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    drawLine(
        color = cornerColor,
        start = bottomRight,
        end = Offset(bottomRight.x, bottomRight.y - bottomRightCornerLen),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )

    // 左下角
    val bottomLeftCornerLen = minOf(
        (bottomRight.x - bottomLeft.x) * cornerLengthRatio,
        (bottomLeft.y - topLeft.y) * cornerLengthRatio
    )
    drawLine(
        color = cornerColor,
        start = bottomLeft,
        end = Offset(bottomLeft.x + bottomLeftCornerLen, bottomLeft.y),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    drawLine(
        color = cornerColor,
        start = bottomLeft,
        end = Offset(bottomLeft.x, bottomLeft.y - bottomLeftCornerLen),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )

    // 绘制四个角的圆点（增强视觉效果）
    val cornerDotRadius = 6f * scale
    listOf(topLeft, topRight, bottomRight, bottomLeft).forEach { corner ->
        drawCircle(
            color = cornerColor,
            radius = cornerDotRadius,
            center = corner
        )
    }
}

// ==================== 文档模式提示 ====================

/**
 * 文档模式提示
 *
 * 显示文档检测状态提示
 *
 * @param isDetected 是否检测到文档
 * @param confidence 检测置信度
 * @param modifier 修饰符
 */
@Composable
fun DocumentModeHint(
    isDetected: Boolean,
    confidence: Float,
    modifier: Modifier = Modifier
) {
    val text = when {
        !isDetected -> "请将文档置于取景框内"
        confidence > 0.5f -> "已识别文档，请拍摄"
        else -> "正在识别文档..."
    }
    val color = when {
        !isDetected -> OverlayColors.accentOrange
        confidence > 0.5f -> OverlayColors.accentGreen
        else -> OverlayColors.accentYellow
    }

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

// ==================== 文档扫描模式枚举 ====================

/**
 * 文档扫描模式枚举（UI使用）
 *
 * 与data层DocumentScanMode对应的UI枚举
 * 定义不同的文档扫描效果模式
 *
 * @param displayName 显示名称
 * @param description 模式描述
 * @param iconText 图标文字（简化图标）
 */
enum class DocumentScanModeUI(
    val displayName: String,
    val description: String,
    val iconText: String
) {
    COLOR("彩色", "保留原始色彩，适合彩色文档", "🎨"),
    GRAYSCALE("灰度", "转为灰度图，减小文件体积", "⬛"),
    BLACK_WHITE("黑白", "高对比度黑白，适合纯文字", "📄"),
    AUTO_ENHANCE("增强", "自动优化对比度和清晰度", "✨"),
    OCR_READY("OCR", "优化用于文字识别", "🔤");

    companion object {
        fun getAll(): List<DocumentScanModeUI> = entries.toList()
        fun getDefault(): DocumentScanModeUI = AUTO_ENHANCE

        /**
         * 从数据层模式转换为UI模式
         */
        fun fromDataMode(mode: com.qihao.filtercamera.data.processor.DocumentScanMode): DocumentScanModeUI {
            return when (mode) {
                com.qihao.filtercamera.data.processor.DocumentScanMode.COLOR -> COLOR
                com.qihao.filtercamera.data.processor.DocumentScanMode.GRAYSCALE -> GRAYSCALE
                com.qihao.filtercamera.data.processor.DocumentScanMode.BLACK_WHITE -> BLACK_WHITE
                com.qihao.filtercamera.data.processor.DocumentScanMode.AUTO_ENHANCE -> AUTO_ENHANCE
                com.qihao.filtercamera.data.processor.DocumentScanMode.OCR_READY -> OCR_READY
            }
        }
    }

    /**
     * 转换为数据层模式
     */
    fun toDataMode(): com.qihao.filtercamera.data.processor.DocumentScanMode {
        return when (this) {
            COLOR -> com.qihao.filtercamera.data.processor.DocumentScanMode.COLOR
            GRAYSCALE -> com.qihao.filtercamera.data.processor.DocumentScanMode.GRAYSCALE
            BLACK_WHITE -> com.qihao.filtercamera.data.processor.DocumentScanMode.BLACK_WHITE
            AUTO_ENHANCE -> com.qihao.filtercamera.data.processor.DocumentScanMode.AUTO_ENHANCE
            OCR_READY -> com.qihao.filtercamera.data.processor.DocumentScanMode.OCR_READY
        }
    }
}

// ==================== 文档扫描模式选择器 ====================

/**
 * 文档扫描模式选择器
 *
 * 显示所有可用的文档扫描模式，允许用户选择
 * 设计为水平滚动的芯片选择器
 *
 * @param currentMode 当前选中的扫描模式
 * @param onModeSelected 模式选择回调
 * @param modifier 修饰符
 */
@Composable
fun DocumentScanModeSelector(
    currentMode: DocumentScanModeUI,
    onModeSelected: (DocumentScanModeUI) -> Unit,
    modifier: Modifier = Modifier
) {
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
        // 标题行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "扫描效果",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = currentMode.displayName,
                color = OverlayColors.documentBorderColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 模式选择芯片（水平滚动）
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(DocumentScanModeUI.getAll()) { mode ->
                DocumentScanModeChip(
                    mode = mode,
                    isSelected = mode == currentMode,
                    onClick = { onModeSelected(mode) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 当前模式描述
        Text(
            text = currentMode.description,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 11.sp
        )
    }
}

/**
 * 文档扫描模式芯片
 *
 * 单个模式选择按钮，显示图标和名称
 *
 * @param mode 扫描模式
 * @param isSelected 是否选中
 * @param onClick 点击回调
 * @param modifier 修饰符
 */
@Composable
private fun DocumentScanModeChip(
    mode: DocumentScanModeUI,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) OverlayColors.documentBorderColor else OverlayColors.chipUnselected
    val textColor = if (isSelected) Color.Black else Color.White

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(OverlayDimens.chipCornerRadius))
            .background(backgroundColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(
                horizontal = 12.dp,
                vertical = 8.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 模式图标
            Text(
                text = mode.iconText,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            // 模式名称
            Text(
                text = mode.displayName,
                color = textColor,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

// ==================== 文档扫描模式快捷按钮 ====================

/**
 * 文档扫描模式快捷切换按钮
 *
 * 小按钮形式，点击后循环切换扫描模式
 * 用于在相机界面快速切换扫描效果
 *
 * @param currentMode 当前扫描模式
 * @param onClick 点击回调
 * @param modifier 修饰符
 */
@Composable
fun DocumentScanModeQuickToggle(
    currentMode: DocumentScanModeUI,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = rememberResponsiveDimens()                               // 响应式尺寸

    Box(
        modifier = modifier
            .size(dimens.minTouchTarget)                                  // 最小48dp触摸目标
            .clip(CircleShape)
            .background(OverlayColors.documentBorderColor.copy(alpha = 0.9f))
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
            // 模式图标
            Text(
                text = currentMode.iconText,
                fontSize = 14.sp
            )
            // 模式缩写
            Text(
                text = when (currentMode) {
                    DocumentScanModeUI.COLOR -> "彩"
                    DocumentScanModeUI.GRAYSCALE -> "灰"
                    DocumentScanModeUI.BLACK_WHITE -> "白"
                    DocumentScanModeUI.AUTO_ENHANCE -> "增"
                    DocumentScanModeUI.OCR_READY -> "识"
                },
                color = Color.Black,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ==================== 文档模式控制栏 ====================

/**
 * 文档模式组合控制栏
 *
 * 包含扫描模式快捷切换和自动捕获开关
 * 用于文档模式的快捷控制
 *
 * @param currentMode 当前扫描模式
 * @param isAutoCapture 是否启用自动捕获
 * @param onModeToggle 模式切换回调
 * @param onAutoCaptureToggle 自动捕获开关回调
 * @param modifier 修饰符
 */
@Composable
fun DocumentModeControlBar(
    currentMode: DocumentScanModeUI,
    isAutoCapture: Boolean,
    onModeToggle: () -> Unit,
    onAutoCaptureToggle: () -> Unit,
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
        // 扫描模式快捷按钮
        DocumentScanModeQuickToggle(
            currentMode = currentMode,
            onClick = onModeToggle
        )

        // 自动捕获按钮
        DocumentAutoCaptureToggle(
            isEnabled = isAutoCapture,
            onClick = onAutoCaptureToggle
        )
    }
}

/**
 * 文档自动捕获开关按钮
 *
 * 开启时：检测到稳定文档边界后自动拍摄
 * 关闭时：手动点击拍摄
 *
 * @param isEnabled 是否启用自动捕获
 * @param onClick 点击回调
 * @param modifier 修饰符
 */
@Composable
private fun DocumentAutoCaptureToggle(
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = rememberResponsiveDimens()                               // 响应式尺寸
    val backgroundColor = if (isEnabled) {
        OverlayColors.accentGreen.copy(alpha = 0.9f)
    } else {
        Color.White.copy(alpha = 0.2f)
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
            // 自动图标
            Text(
                text = "⚡",
                fontSize = 14.sp
            )
            // 状态文字
            Text(
                text = if (isEnabled) "自动" else "手动",
                color = if (isEnabled) Color.Black else Color.White.copy(alpha = 0.8f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ==================== 紧凑版文档扫描模式选择器 ====================

/**
 * 文档扫描紧凑模式选择器
 *
 * 紧凑版设计，适合放在相机预览底部
 * 使用图标+文字的形式显示
 *
 * @param currentMode 当前扫描模式
 * @param onModeSelected 模式选择回调
 * @param modifier 修饰符
 */
@Composable
fun CompactDocumentScanModeSelector(
    currentMode: DocumentScanModeUI,
    onModeSelected: (DocumentScanModeUI) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DocumentScanModeUI.getAll().forEach { mode ->
            CompactDocumentModeChip(
                mode = mode,
                isSelected = mode == currentMode,
                onClick = { onModeSelected(mode) }
            )
        }
    }
}

/**
 * 紧凑版文档扫描模式芯片
 *
 * @param mode 扫描模式
 * @param isSelected 是否选中
 * @param onClick 点击回调
 * @param modifier 修饰符
 */
@Composable
private fun CompactDocumentModeChip(
    mode: DocumentScanModeUI,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) OverlayColors.documentBorderColor else Color.Transparent
    val borderColor = if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.3f)
    val textColor = if (isSelected) Color.Black else Color.White

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .border(
                width = if (isSelected) 0.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = mode.displayName,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
