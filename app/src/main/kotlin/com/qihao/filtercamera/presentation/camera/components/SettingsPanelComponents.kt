/**
 * SettingsPanelComponents.kt - 设置面板UI组件
 *
 * 参考小米相机设置面板设计
 * 包含：顶部控制栏、展开式设置面板、变焦滑块
 *
 * 设计特点：
 * - 顶部箭头按钮控制面板展开/收起
 * - 网格布局的设置选项
 * - 选中状态高亮显示（橙红色）
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.presentation.camera.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.FlashAuto
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.HdrOn
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Camera
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qihao.filtercamera.domain.model.ApertureMode
import com.qihao.filtercamera.domain.model.AspectRatio
import com.qihao.filtercamera.domain.model.CameraAdvancedSettings
import com.qihao.filtercamera.domain.model.FlashMode
import com.qihao.filtercamera.domain.model.HdrMode
import com.qihao.filtercamera.domain.model.MacroMode
import com.qihao.filtercamera.domain.model.TimerMode
import com.qihao.filtercamera.domain.model.ZoomConfig
import com.qihao.filtercamera.domain.repository.ZoomRange

// ==================== 颜色常量 ====================

private val ActiveColor = Color(0xFFFF6B35)                               // 激活状态颜色（橙红色）
private val InactiveColor = Color.White                                   // 未激活颜色
private val PanelBackground = Color(0xCC1C1C1E)                          // 面板背景色（半透明深色）

// ==================== 顶部控制栏 ====================

/**
 * 顶部控制栏
 *
 * 包含展开/收起箭头按钮
 *
 * @param isExpanded 是否展开设置面板
 * @param onToggle 切换展开状态回调
 * @param modifier 修饰符
 */
@Composable
fun TopControlBar(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 展开/收起按钮
        ExpandArrowButton(
            isExpanded = isExpanded,
            onClick = onToggle
        )
    }
}

/**
 * 展开/收起箭头按钮
 *
 * @param isExpanded 是否展开
 * @param onClick 点击回调
 * @param modifier 修饰符
 */
@Composable
fun ExpandArrowButton(
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 旋转动画：展开时向下，收起时向上
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(300),
        label = "arrowRotation"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x33FFFFFF))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 28.dp, vertical = 6.dp),                       // 精致化：32/8→28/6
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowUp,
            contentDescription = if (isExpanded) "收起设置" else "展开设置",
            tint = if (isExpanded) ActiveColor else InactiveColor,
            modifier = Modifier
                .size(24.dp)                                                     // 精致化：28→24dp
                .rotate(rotation)
        )
    }
}

// ==================== 设置面板 ====================

/**
 * 设置面板
 *
 * 网格布局的设置选项：闪光灯、HDR、超级微距、画幅、光圈、定时拍照、设置
 *
 * @param isVisible 是否可见
 * @param settings 当前设置
 * @param timerMode 当前定时拍照模式
 * @param onFlashModeChanged 闪光灯模式改变回调
 * @param onHdrModeChanged HDR模式改变回调
 * @param onMacroModeChanged 微距模式改变回调
 * @param onAspectRatioChanged 画幅改变回调
 * @param onApertureModeChanged 光圈模式改变回调
 * @param onTimerModeChanged 定时拍照模式改变回调
 * @param onSettingsClick 设置按钮点击回调
 * @param modifier 修饰符
 */
@Composable
fun SettingsPanel(
    isVisible: Boolean,
    settings: CameraAdvancedSettings,
    timerMode: TimerMode = TimerMode.OFF,
    onFlashModeChanged: (FlashMode) -> Unit,
    onHdrModeChanged: (HdrMode) -> Unit,
    onMacroModeChanged: (MacroMode) -> Unit,
    onAspectRatioChanged: (AspectRatio) -> Unit,
    onApertureModeChanged: (ApertureMode) -> Unit,
    onTimerModeChanged: (TimerMode) -> Unit = {},
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = expandVertically(
            animationSpec = tween(300),
            expandFrom = Alignment.Top
        ) + fadeIn(animationSpec = tween(200)),
        exit = shrinkVertically(
            animationSpec = tween(250),
            shrinkTowards = Alignment.Top
        ) + fadeOut(animationSpec = tween(150)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = PanelBackground,
                    shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                )
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Column {
                // 第一行：闪光灯、HDR、超级微距、画幅、光圈
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // 闪光灯
                    SettingsIconItem(
                        icon = when (settings.flashMode) {
                            FlashMode.OFF -> Icons.Outlined.FlashOff
                            FlashMode.ON -> Icons.Outlined.FlashOn
                            FlashMode.AUTO -> Icons.Outlined.FlashAuto
                            FlashMode.TORCH -> Icons.Outlined.FlashlightOn
                        },
                        label = when (settings.flashMode) {
                            FlashMode.OFF -> "闪光关"
                            FlashMode.ON -> "闪光开"
                            FlashMode.AUTO -> "闪光自动"
                            FlashMode.TORCH -> "常亮"
                        },
                        isActive = settings.flashMode != FlashMode.OFF,
                        onClick = {
                            // 循环切换：OFF -> AUTO -> ON -> TORCH -> OFF
                            val nextMode = when (settings.flashMode) {
                                FlashMode.OFF -> FlashMode.AUTO
                                FlashMode.AUTO -> FlashMode.ON
                                FlashMode.ON -> FlashMode.TORCH
                                FlashMode.TORCH -> FlashMode.OFF
                            }
                            onFlashModeChanged(nextMode)
                        }
                    )

                    // HDR
                    SettingsIconItem(
                        icon = Icons.Outlined.HdrOn,
                        label = when (settings.hdrMode) {
                            HdrMode.OFF -> "HDR关"
                            HdrMode.AUTO -> "HDR自动"
                            HdrMode.ON -> "HDR开"
                        },
                        isActive = settings.hdrMode != HdrMode.OFF,
                        onClick = {
                            val nextMode = when (settings.hdrMode) {
                                HdrMode.OFF -> HdrMode.AUTO
                                HdrMode.AUTO -> HdrMode.ON
                                HdrMode.ON -> HdrMode.OFF
                            }
                            onHdrModeChanged(nextMode)
                        }
                    )

                    // 超级微距
                    SettingsIconItem(
                        icon = Icons.Outlined.LocalFlorist,
                        label = when (settings.macroMode) {
                            MacroMode.OFF -> "微距关"
                            MacroMode.AUTO -> "微距自动"
                            MacroMode.ON -> "微距开"
                        },
                        isActive = settings.macroMode != MacroMode.OFF,
                        onClick = {
                            val nextMode = when (settings.macroMode) {
                                MacroMode.OFF -> MacroMode.AUTO
                                MacroMode.AUTO -> MacroMode.ON
                                MacroMode.ON -> MacroMode.OFF
                            }
                            onMacroModeChanged(nextMode)
                        }
                    )

                    // 画幅
                    SettingsIconItem(
                        icon = Icons.Outlined.Crop,
                        label = settings.aspectRatio.displayName,
                        isActive = false,
                        onClick = {
                            val ratios = AspectRatio.getAll()
                            val currentIndex = ratios.indexOf(settings.aspectRatio)
                            val nextIndex = (currentIndex + 1) % ratios.size
                            onAspectRatioChanged(ratios[nextIndex])
                        }
                    )

                    // 光圈
                    SettingsItem(
                        icon = "⬡",
                        label = "光圈",
                        isActive = settings.apertureMode != ApertureMode.AUTO,
                        subLabel = if (settings.apertureMode == ApertureMode.AUTO) "AUTO"
                                   else settings.apertureMode.displayName,
                        onClick = {
                            // 循环切换光圈
                            val modes = ApertureMode.getAll()
                            val currentIndex = modes.indexOf(settings.apertureMode)
                            val nextIndex = (currentIndex + 1) % modes.size
                            onApertureModeChanged(modes[nextIndex])
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 第二行：定时拍照、设置
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    // 定时拍照
                    SettingsItem(
                        icon = when (timerMode) {
                            TimerMode.OFF -> "⏱"
                            TimerMode.TIMER_3S -> "3"
                            TimerMode.TIMER_5S -> "5"
                            TimerMode.TIMER_10S -> "10"
                        },
                        label = timerMode.displayName,
                        isActive = timerMode != TimerMode.OFF,
                        subLabel = if (timerMode != TimerMode.OFF) "s" else null,
                        onClick = {
                            // 使用 next() 扩展函数循环切换
                            with(TimerMode.Companion) {
                                onTimerModeChanged(timerMode.next())
                            }
                        }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // 设置
                    SettingsItem(
                        icon = "⚙",
                        label = "设置",
                        isActive = false,
                        onClick = onSettingsClick
                    )
                }
            }
        }
    }
}

/**
 * 设置项
 *
 * @param icon 图标文字
 * @param label 标签
 * @param isActive 是否激活
 * @param subLabel 副标签（如"A"表示自动）
 * @param onClick 点击回调
 * @param modifier 修饰符
 */
@Composable
private fun SettingsItem(
    icon: String,
    label: String,
    isActive: Boolean,
    subLabel: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textColor = if (isActive) ActiveColor else InactiveColor

    Column(
        modifier = modifier
            .widthIn(min = 52.dp, max = 64.dp)                                   // 精致化：60-80→52-64dp
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 6.dp, vertical = 6.dp),                        // 精致化：8→6dp
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 图标区域（固定高度）
        Box(
            modifier = Modifier
                .size(28.dp),                                                    // 精致化：32→28dp
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = icon,
                color = textColor,
                fontSize = 16.sp,                                                // 精致化：18→16sp
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            // 副标签（右上角小标）
            if (subLabel != null) {
                Text(
                    text = subLabel,
                    color = textColor,
                    fontSize = 7.sp,                                             // 精致化：8→7sp
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(start = 14.dp)                                  // 精致化：16→14dp
                )
            }
        }

        Spacer(modifier = Modifier.height(3.dp))                                 // 精致化：4→3dp

        // 标签（限制宽度，单行显示）
        Text(
            text = label,
            color = textColor,
            fontSize = 9.sp,                                                     // 精致化：10→9sp
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 设置项（使用Material图标）
 *
 * @param icon Material图标
 * @param label 标签
 * @param isActive 是否激活
 * @param onClick 点击回调
 * @param modifier 修饰符
 */
@Composable
private fun SettingsIconItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconColor = if (isActive) ActiveColor else InactiveColor

    Column(
        modifier = modifier
            .widthIn(min = 52.dp, max = 64.dp)                                   // 精致化：60-80→52-64dp
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 6.dp, vertical = 6.dp),                        // 精致化：8→6dp
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 图标区域（固定高度）
        Box(
            modifier = Modifier.size(28.dp),                                     // 精致化：32→28dp
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(20.dp)                                  // 精致化：24→20dp
            )
        }

        Spacer(modifier = Modifier.height(3.dp))                                 // 精致化：4→3dp

        // 标签（限制宽度，单行显示）
        Text(
            text = label,
            color = iconColor,
            fontSize = 9.sp,                                                     // 精致化：10→9sp
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ==================== 变焦滑块 ====================

/**
 * 变焦指示器（胶囊样式）
 *
 * 显示当前变焦倍数，点击展开/收起完整变焦滑块
 * 精致小巧的胶囊设计
 *
 * @param currentZoom 当前变焦倍数
 * @param isExpanded 滑块是否展开
 * @param onClick 点击回调
 * @param modifier 修饰符
 */
@Composable
fun ZoomIndicator(
    currentZoom: Float,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isExpanded) ActiveColor else Color(0x66000000)
    val textColor = if (isExpanded) Color.Black else Color.White

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))                                  // 圆角胶囊
            .background(backgroundColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),                   // 精致内边距
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = ZoomConfig.formatZoom(currentZoom),
            color = textColor,
            fontSize = 11.sp,                                                 // 精致字号
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 变焦控制滑块
 *
 * 显示当前变焦倍数，支持滑动调节和快捷档位
 *
 * @param currentZoom 当前变焦倍数
 * @param zoomRange 设备支持的变焦范围
 * @param onZoomChanged 变焦改变回调
 * @param modifier 修饰符
 */
@Composable
fun ZoomSlider(
    currentZoom: Float,
    zoomRange: ZoomRange = ZoomRange(),
    onZoomChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    // 根据设备实际范围过滤可用的快捷档位
    val availablePresets = ZoomConfig.ZOOM_PRESETS.filter { zoom ->
        zoom >= zoomRange.minZoom && zoom <= zoomRange.maxZoom
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 变焦倍数显示
        Text(
            text = ZoomConfig.formatZoom(currentZoom),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 快捷档位按钮（仅显示设备支持的档位）
        if (availablePresets.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                availablePresets.forEach { zoom ->
                    ZoomPresetButton(
                        zoom = zoom,
                        isSelected = kotlin.math.abs(currentZoom - zoom) < 0.1f,
                        onClick = { onZoomChanged(zoom) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // 滑块（使用设备实际范围）
        Slider(
            value = currentZoom.coerceIn(zoomRange.minZoom, zoomRange.maxZoom),
            onValueChange = onZoomChanged,
            valueRange = zoomRange.minZoom..zoomRange.maxZoom,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = ActiveColor,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
            )
        )

        // 显示变焦范围提示
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = ZoomConfig.formatZoom(zoomRange.minZoom),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 10.sp
            )
            Text(
                text = ZoomConfig.formatZoom(zoomRange.maxZoom),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 10.sp
            )
        }
    }
}

/**
 * 变焦快捷档位按钮
 *
 * @param zoom 变焦倍数
 * @param isSelected 是否选中
 * @param onClick 点击回调
 */
@Composable
private fun ZoomPresetButton(
    zoom: Float,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) ActiveColor else Color.Transparent
    val textColor = if (isSelected) Color.Black else Color.White
    val borderColor = if (isSelected) ActiveColor else Color.White.copy(alpha = 0.5f)

    Box(
        modifier = modifier
            .size(28.dp)                                                          // 精致化：36→28dp
            .clip(CircleShape)
            .background(backgroundColor)
            .border(1.dp, borderColor, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = ZoomConfig.formatZoom(zoom),
            color = textColor,
            fontSize = 8.sp,                                                      // 精致化：10→8sp
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// ==================== 完整顶部区域组件 ====================

/**
 * 相机顶部区域
 *
 * 包含展开按钮和设置面板
 *
 * @param isPanelExpanded 面板是否展开
 * @param settings 当前设置
 * @param timerMode 当前定时拍照模式
 * @param onTogglePanel 切换面板回调
 * @param onFlashModeChanged 闪光灯改变回调
 * @param onHdrModeChanged HDR改变回调
 * @param onMacroModeChanged 微距改变回调
 * @param onAspectRatioChanged 画幅改变回调
 * @param onApertureModeChanged 光圈改变回调
 * @param onTimerModeChanged 定时拍照模式改变回调
 * @param onSettingsClick 设置点击回调
 * @param modifier 修饰符
 */
@Composable
fun CameraTopSection(
    isPanelExpanded: Boolean,
    settings: CameraAdvancedSettings,
    timerMode: TimerMode = TimerMode.OFF,
    onTogglePanel: () -> Unit,
    onFlashModeChanged: (FlashMode) -> Unit,
    onHdrModeChanged: (HdrMode) -> Unit,
    onMacroModeChanged: (MacroMode) -> Unit,
    onAspectRatioChanged: (AspectRatio) -> Unit,
    onApertureModeChanged: (ApertureMode) -> Unit,
    onTimerModeChanged: (TimerMode) -> Unit = {},
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // 顶部控制栏（箭头按钮）
        TopControlBar(
            isExpanded = isPanelExpanded,
            onToggle = onTogglePanel
        )

        // 设置面板
        SettingsPanel(
            isVisible = isPanelExpanded,
            settings = settings,
            timerMode = timerMode,
            onFlashModeChanged = onFlashModeChanged,
            onHdrModeChanged = onHdrModeChanged,
            onMacroModeChanged = onMacroModeChanged,
            onAspectRatioChanged = onAspectRatioChanged,
            onApertureModeChanged = onApertureModeChanged,
            onTimerModeChanged = onTimerModeChanged,
            onSettingsClick = onSettingsClick
        )
    }
}
