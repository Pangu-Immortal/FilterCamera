/**
 * ModeOverlayComponents.kt - 模式覆盖层UI组件
 *
 * 提供人像模式人脸框、文档模式边框、专业模式控制面板等UI组件
 *
 * 组件列表：
 * - FaceDetectionOverlay: 人脸检测覆盖层
 * - DocumentBoundsOverlay: 文档边界覆盖层
 * - ProModeControlPanel: 专业模式控制面板
 * - PortraitModeHint: 人像模式提示
 * - BeautySlider: 美颜滑块
 * - DocumentModeHint: 文档模式提示
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.presentation.camera.components

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
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
import com.qihao.filtercamera.domain.model.BeautyLevel
import com.qihao.filtercamera.domain.model.DocumentBounds
import com.qihao.filtercamera.domain.model.FaceInfo
import com.qihao.filtercamera.domain.model.FocusMode
import com.qihao.filtercamera.domain.model.NormalizedRect
import com.qihao.filtercamera.domain.model.MeteringMode
import com.qihao.filtercamera.domain.model.ProModeSettings
import com.qihao.filtercamera.domain.model.WhiteBalanceMode

// ==================== 全局配置常量 ====================

/**
 * 模式覆盖层颜色配置
 *
 * 集中管理所有覆盖层使用的颜色
 */
object OverlayColors {
    val faceBoxColor = Color(0xFFFFCC00)                             // 人脸框：黄色
    val documentBorderColor = Color(0xFF00BCD4)                      // 文档边框：青色
    val accentYellow = Color(0xFFFFCC00)                             // 强调色：黄色
    val accentOrange = Color(0xFFFF9800)                             // 强调色：橙色
    val accentGreen = Color(0xFF4CAF50)                              // 状态色：成功绿色
    val panelBackground = Color.Black.copy(alpha = 0.7f)             // 面板背景
    val hintBackground = Color.Black.copy(alpha = 0.6f)              // 提示背景
    val chipUnselected = Color.White.copy(alpha = 0.1f)              // 芯片未选中
    val trackInactive = Color.White.copy(alpha = 0.3f)               // 滑块轨道未激活
}

/**
 * 模式覆盖层尺寸配置
 *
 * 集中管理所有覆盖层使用的尺寸
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

// ==================== 人像模式人脸框覆盖层 ====================

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
    val right = bounds.right * size.width
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

// ==================== 文档模式边框覆盖层 ====================

/**
 * 文档边界覆盖层
 *
 * 在检测到的文档边界绘制边框
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

    val alpha by animateFloatAsState(                                 // 透明度动画
        targetValue = if (bounds.confidence > 0.5f) 1f else 0.5f,
        animationSpec = tween(200),
        label = "documentOverlayAlpha"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        drawDocumentBounds(bounds, alpha)
    }
}

/**
 * 绘制文档边界
 *
 * 绘制检测到的文档四边形边框
 *
 * @param bounds 文档边界
 * @param alpha 透明度
 */
private fun DrawScope.drawDocumentBounds(bounds: DocumentBounds, alpha: Float) {
    val w = size.width
    val h = size.height
    val color = OverlayColors.documentBorderColor                    // 使用配置颜色

    // 构建路径
    val path = Path().apply {
        moveTo(bounds.topLeft.x * w, bounds.topLeft.y * h)
        lineTo(bounds.topRight.x * w, bounds.topRight.y * h)
        lineTo(bounds.bottomRight.x * w, bounds.bottomRight.y * h)
        lineTo(bounds.bottomLeft.x * w, bounds.bottomLeft.y * h)
        close()
    }

    // 绘制半透明填充
    drawPath(
        path = path,
        color = color.copy(alpha = 0.1f * alpha)
    )

    // 绘制边框
    drawPath(
        path = path,
        color = color.copy(alpha = alpha),
        style = Stroke(width = 4f, cap = StrokeCap.Round)
    )

    // 绘制四个角的小圆点
    val cornerRadius = 8f
    val corners = listOf(
        Offset(bounds.topLeft.x * w, bounds.topLeft.y * h),
        Offset(bounds.topRight.x * w, bounds.topRight.y * h),
        Offset(bounds.bottomRight.x * w, bounds.bottomRight.y * h),
        Offset(bounds.bottomLeft.x * w, bounds.bottomLeft.y * h)
    )
    corners.forEach { corner ->
        drawCircle(
            color = color.copy(alpha = alpha),
            radius = cornerRadius,
            center = corner
        )
    }
}

// ==================== 专业模式控制面板 ====================

/**
 * 专业模式控制面板
 *
 * 显示ISO、快门、曝光补偿、白平衡等参数调节控件
 *
 * @param settings 当前参数设置
 * @param onIsoChanged ISO改变回调
 * @param onShutterChanged 快门改变回调
 * @param onEvChanged 曝光补偿改变回调
 * @param onWbChanged 白平衡改变回调
 * @param onFocusModeChanged 对焦模式改变回调
 * @param onFocusDistanceChanged 对焦距离改变回调
 * @param modifier 修饰符
 */
@Composable
fun ProModeControlPanel(
    settings: ProModeSettings,
    onIsoChanged: (Int?) -> Unit,
    onShutterChanged: (Float?) -> Unit,
    onEvChanged: (Float) -> Unit,
    onWbChanged: (WhiteBalanceMode) -> Unit,
    onFocusModeChanged: (FocusMode) -> Unit,
    onFocusDistanceChanged: (Float) -> Unit,
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
            .padding(OverlayDimens.panelPadding)
    ) {
        // ISO 控制
        ProModeRow(
            label = "ISO",
            value = ProModeSettings.formatIso(settings.iso)
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(OverlayDimens.itemSpacing)
            ) {
                items(ProModeSettings.ISO_VALUES) { iso ->
                    ProModeChip(
                        text = ProModeSettings.formatIso(iso),
                        isSelected = settings.iso == iso,
                        onClick = { onIsoChanged(iso) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(OverlayDimens.rowSpacing))

        // 快门速度控制
        ProModeRow(
            label = "快门",
            value = ProModeSettings.formatShutterSpeed(settings.shutterSpeed)
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(OverlayDimens.itemSpacing)
            ) {
                items(ProModeSettings.SHUTTER_SPEEDS) { speed ->
                    ProModeChip(
                        text = ProModeSettings.formatShutterSpeed(speed),
                        isSelected = settings.shutterSpeed == speed,
                        onClick = { onShutterChanged(speed) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(OverlayDimens.rowSpacing))

        // 曝光补偿滑块
        ProModeSliderRow(
            label = "曝光",
            value = settings.exposureCompensation,
            valueText = ProModeSettings.formatEV(settings.exposureCompensation),
            range = ProModeSettings.EV_MIN..ProModeSettings.EV_MAX,
            onValueChange = onEvChanged
        )

        Spacer(modifier = Modifier.height(OverlayDimens.rowSpacing))

        // 白平衡控制
        ProModeRow(
            label = "白平衡",
            value = settings.whiteBalance.displayName
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(OverlayDimens.itemSpacing)
            ) {
                items(WhiteBalanceMode.getAll()) { wb ->
                    ProModeChip(
                        text = wb.displayName,
                        isSelected = settings.whiteBalance == wb,
                        onClick = { onWbChanged(wb) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(OverlayDimens.rowSpacing))

        // 对焦模式控制
        ProModeRow(
            label = "对焦",
            value = settings.focusMode.displayName
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(OverlayDimens.itemSpacing)) {
                FocusMode.getAll().forEach { mode ->
                    ProModeChip(
                        text = mode.displayName,
                        isSelected = settings.focusMode == mode,
                        onClick = { onFocusModeChanged(mode) }
                    )
                }
            }
        }

        // 手动对焦距离滑块（仅手动模式显示）
        if (settings.focusMode == FocusMode.MANUAL) {
            Spacer(modifier = Modifier.height(OverlayDimens.rowSpacing))
            ProModeSliderRow(
                label = "距离",
                value = settings.focusDistance,
                valueText = "${(settings.focusDistance * 100).toInt()}%",
                range = 0f..1f,
                onValueChange = onFocusDistanceChanged
            )
        }
    }
}

/**
 * 专业模式参数行
 *
 * 显示参数标签、当前值和控制区域
 *
 * @param label 参数标签
 * @param value 当前值文本
 * @param content 控制区域内容
 */
@Composable
private fun ProModeRow(
    label: String,
    value: String,
    content: @Composable () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                color = OverlayColors.accentYellow,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(OverlayDimens.labelSpacing))
        content()
    }
}

/**
 * 专业模式滑块行
 *
 * 显示带标签的滑块控件
 *
 * @param label 参数标签
 * @param value 当前值
 * @param valueText 显示的值文本
 * @param range 取值范围
 * @param onValueChange 值变化回调
 */
@Composable
private fun ProModeSliderRow(
    label: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = valueText,
                color = OverlayColors.accentYellow,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(OverlayDimens.sliderSpacing))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = OverlayColors.accentYellow,
                activeTrackColor = OverlayColors.accentYellow,
                inactiveTrackColor = OverlayColors.trackInactive
            )
        )
    }
}

/**
 * 专业模式选项芯片
 *
 * 显示可选择的参数选项
 *
 * @param text 选项文本
 * @param isSelected 是否选中
 * @param onClick 点击回调
 */
@Composable
private fun ProModeChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) OverlayColors.accentYellow else OverlayColors.chipUnselected
    val textColor = if (isSelected) Color.Black else Color.White

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(OverlayDimens.chipCornerRadius))
            .background(backgroundColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(
                horizontal = OverlayDimens.chipHorizontalPadding,
                vertical = OverlayDimens.chipVerticalPadding
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// ==================== 人像模式提示UI ====================

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

// ==================== 人像模式美颜滑块 ====================

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

// ==================== 文档模式提示UI ====================

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

    // 数字缩放动画：每次数字变化时触发缩放效果
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(
            durationMillis = 300,
            easing = androidx.compose.animation.core.FastOutSlowInEasing
        ),
        label = "countdownScale"
    )

    // 透明度动画
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 200),
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

// ==================== 滤镜强度滑块 ====================

/**
 * 滤镜强度滑块
 *
 * 用于调节滤镜效果的强度（0% - 100%）
 * 0% = 原图（无滤镜效果）
 * 100% = 全强度滤镜效果
 *
 * 设计参考小米相机的滤镜强度调节
 *
 * @param currentIntensity 当前强度值（0.0~1.0）
 * @param onIntensityChanged 强度变化回调
 * @param modifier 修饰符
 */
@Composable
fun FilterIntensitySlider(
    currentIntensity: Float,
    onIntensityChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var sliderValue by remember(currentIntensity) {
        mutableFloatStateOf(currentIntensity)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = OverlayColors.hintBackground,
                shape = RoundedCornerShape(OverlayDimens.hintCornerRadius)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 强度图标（调节器图标）
        Text(
            text = "⚙",
            color = OverlayColors.accentOrange,
            fontSize = 18.sp
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 滑块区域（占剩余空间）
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // 滑块
            Slider(
                value = sliderValue,
                onValueChange = { newValue ->
                    sliderValue = newValue
                    onIntensityChanged(newValue)                             // 实时回调更新
                },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = OverlayColors.accentOrange,
                    activeTrackColor = OverlayColors.accentOrange,
                    inactiveTrackColor = OverlayColors.trackInactive
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 百分比显示
        Text(
            text = "${(sliderValue * 100).toInt()}%",
            color = if (sliderValue > 0) OverlayColors.accentOrange else Color.Gray,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(44.dp)                                  // 固定宽度保持对齐
        )
    }
}

/**
 * 简洁版滤镜强度滑块
 *
 * 更紧凑的设计，适合放在滤镜选择器下方
 * 仅显示滑块和百分比，无标题
 *
 * @param currentIntensity 当前强度值（0.0~1.0）
 * @param onIntensityChanged 强度变化回调
 * @param modifier 修饰符
 */
@Composable
fun CompactFilterIntensitySlider(
    currentIntensity: Float,
    onIntensityChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var sliderValue by remember(currentIntensity) {
        mutableFloatStateOf(currentIntensity)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 滑块左侧标签
        Text(
            text = "强度",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp,
            modifier = Modifier.width(36.dp)
        )

        // 滑块
        Slider(
            value = sliderValue,
            onValueChange = { newValue ->
                sliderValue = newValue
                onIntensityChanged(newValue)
            },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = OverlayColors.accentOrange,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
            ),
            modifier = Modifier.weight(1f)
        )

        // 百分比显示
        Text(
            text = "${(sliderValue * 100).toInt()}%",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp,
            modifier = Modifier.width(40.dp)
        )
    }
}
