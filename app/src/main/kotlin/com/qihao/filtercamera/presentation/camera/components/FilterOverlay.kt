/**
 * FilterOverlay.kt - 滤镜控制覆盖层组件
 *
 * 提供滤镜强度调节等控制组件
 *
 * 组件列表：
 * - FilterIntensitySlider: 滤镜强度滑块
 * - CompactFilterIntensitySlider: 简洁版滤镜强度滑块
 *
 * @author qihao
 * @since 3.0.0
 */
package com.qihao.filtercamera.presentation.camera.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
            modifier = Modifier.weight(1f)
        )

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

// ==================== 简洁版滤镜强度滑块 ====================

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
