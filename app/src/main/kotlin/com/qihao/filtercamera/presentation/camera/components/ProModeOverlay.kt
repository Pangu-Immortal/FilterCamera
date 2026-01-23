/**
 * ProModeOverlay.kt - 专业模式覆盖层组件
 *
 * 提供专业模式下的参数控制面板
 *
 * 组件列表：
 * - ProModeControlPanel: 专业模式控制面板
 * - ProModeRow: 参数行组件
 * - ProModeSliderRow: 滑块参数行组件
 * - ProModeChip: 参数选项芯片
 *
 * @author qihao
 * @since 3.0.0
 */
package com.qihao.filtercamera.presentation.camera.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.qihao.filtercamera.domain.model.FocusMode
import com.qihao.filtercamera.domain.model.ProModeSettings
import com.qihao.filtercamera.domain.model.WhiteBalanceMode

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
