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
import androidx.compose.ui.geometry.Offset
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
import com.qihao.filtercamera.presentation.common.theme.CameraTheme
import com.qihao.filtercamera.presentation.common.theme.rememberResponsiveDimens

// ==================== 颜色常量（使用CameraTheme） ====================

private val ActiveColor @Composable get() = CameraTheme.Colors.primary          // 激活状态颜色（金色）
private val InactiveColor @Composable get() = CameraTheme.Colors.textPrimary    // 未激活颜色（白色）
private val PanelBackground @Composable get() = CameraTheme.SettingsPanel.background  // 面板背景色

// ==================== 顶部控制栏 ====================

/**
 * 顶部控制栏
 *
 * 包含展开/收起箭头按钮
 * 使用响应式尺寸系统
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
    val dimens = rememberResponsiveDimens()                                      // 响应式尺寸系统

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.spacing.lg, vertical = dimens.spacing.sm), // 响应式内边距
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
 * 使用响应式尺寸系统
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
    val dimens = rememberResponsiveDimens()                                      // 响应式尺寸系统

    // 旋转动画：展开时向下，收起时向上
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(dimens.animation.normal),                          // 响应式动画时长
        label = "arrowRotation"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(dimens.radius.medium))                      // 响应式圆角
            .background(CameraTheme.Colors.controlBackgroundLight)               // 统一控件背景
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = dimens.spacing.xl, vertical = dimens.spacing.sm), // 响应式内边距
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowUp,
            contentDescription = if (isExpanded) "收起设置" else "展开设置",
            tint = if (isExpanded) ActiveColor else InactiveColor,
            modifier = Modifier
                .size(dimens.topBarIconSize)                                     // 响应式图标尺寸
                .rotate(rotation)
        )
    }
}

// ==================== 设置面板 ====================

/**
 * 设置面板
 *
 * 网格布局的设置选项：闪光灯、HDR、超级微距、画幅、光圈、定时拍照、设置
 * 使用响应式尺寸系统
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
    val dimens = rememberResponsiveDimens()                                      // 响应式尺寸系统

    AnimatedVisibility(
        visible = isVisible,
        enter = expandVertically(
            animationSpec = tween(dimens.animation.normal),
            expandFrom = Alignment.Top
        ) + fadeIn(animationSpec = tween(dimens.animation.fast)),
        exit = shrinkVertically(
            animationSpec = tween(250),
            shrinkTowards = Alignment.Top
        ) + fadeOut(animationSpec = tween(dimens.animation.fast)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = PanelBackground,
                    shape = RoundedCornerShape(
                        bottomStart = dimens.radius.medium,
                        bottomEnd = dimens.radius.medium
                    )
                )
                .padding(horizontal = dimens.spacing.md, vertical = dimens.spacing.md) // 响应式内边距
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

                Spacer(modifier = Modifier.height(dimens.spacing.lg))            // 响应式间距

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

                    Spacer(modifier = Modifier.width(dimens.spacing.sm))         // 响应式间距

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
 * 使用响应式尺寸系统
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
    val dimens = rememberResponsiveDimens()                                      // 响应式尺寸系统
    val textColor = if (isActive) ActiveColor else InactiveColor

    Column(
        modifier = modifier
            .widthIn(min = dimens.settingsItemHeight, max = dimens.settingsItemHeight + 12.dp) // 响应式宽度
            .clip(RoundedCornerShape(dimens.radius.small))                       // 响应式圆角
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = dimens.spacing.sm, vertical = dimens.spacing.sm), // 响应式内边距
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 图标区域（固定高度）
        Box(
            modifier = Modifier
                .size(dimens.settingsIconSize + 4.dp),                           // 响应式图标区域尺寸
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = icon,
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            // 副标签（右上角小标）
            if (subLabel != null) {
                Text(
                    text = subLabel,
                    color = textColor,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(start = dimens.spacing.md)                      // 响应式偏移
                )
            }
        }

        Spacer(modifier = Modifier.height(dimens.spacing.xs))                    // 响应式间距

        // 标签（限制宽度，单行显示）
        Text(
            text = label,
            color = textColor,
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 设置项（使用Material图标）
 *
 * 使用响应式尺寸系统
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
    val dimens = rememberResponsiveDimens()                                      // 响应式尺寸系统
    val iconColor = if (isActive) ActiveColor else InactiveColor

    Column(
        modifier = modifier
            .widthIn(min = dimens.settingsItemHeight, max = dimens.settingsItemHeight + 12.dp) // 响应式宽度
            .clip(RoundedCornerShape(dimens.radius.small))                       // 响应式圆角
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = dimens.spacing.sm, vertical = dimens.spacing.sm), // 响应式内边距
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 图标区域（固定高度）
        Box(
            modifier = Modifier.size(dimens.settingsIconSize + 4.dp),            // 响应式图标区域尺寸
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(dimens.settingsIconSize - 4.dp)         // 响应式图标尺寸
            )
        }

        Spacer(modifier = Modifier.height(dimens.spacing.xs))                    // 响应式间距

        // 标签（限制宽度，单行显示）
        Text(
            text = label,
            color = iconColor,
            fontSize = 9.sp,
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
 * 使用响应式尺寸系统
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
    val dimens = rememberResponsiveDimens()                                      // 响应式尺寸系统
    val backgroundColor = if (isExpanded) ActiveColor else CameraTheme.Colors.controlBackground
    val textColor = if (isExpanded) CameraTheme.Colors.onPrimary else CameraTheme.Colors.textPrimary

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(dimens.radius.medium))                      // 响应式圆角
            .background(backgroundColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = dimens.spacing.md, vertical = dimens.spacing.xs), // 响应式内边距
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = ZoomConfig.formatZoom(currentZoom),
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 变焦控制滑块
 *
 * 显示当前变焦倍数，支持滑动调节和快捷档位
 * 使用响应式尺寸系统
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
    val dimens = rememberResponsiveDimens()                                      // 响应式尺寸系统

    // 根据设备实际范围过滤可用的快捷档位
    val availablePresets = ZoomConfig.ZOOM_PRESETS.filter { zoom ->
        zoom >= zoomRange.minZoom && zoom <= zoomRange.maxZoom
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.spacing.xl),                            // 响应式内边距
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 变焦倍数显示
        Text(
            text = ZoomConfig.formatZoom(currentZoom),
            color = CameraTheme.Colors.textPrimary,                              // 统一文字颜色
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(dimens.spacing.sm))                    // 响应式间距

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

            Spacer(modifier = Modifier.height(dimens.spacing.sm))                // 响应式间距
        }

        // 滑块（使用设备实际范围）
        Slider(
            value = currentZoom.coerceIn(zoomRange.minZoom, zoomRange.maxZoom),
            onValueChange = onZoomChanged,
            valueRange = zoomRange.minZoom..zoomRange.maxZoom,
            colors = SliderDefaults.colors(
                thumbColor = CameraTheme.Colors.textPrimary,                     // 统一滑块颜色
                activeTrackColor = CameraTheme.Colors.primary,                   // 激活轨道颜色
                inactiveTrackColor = CameraTheme.Colors.textTertiary             // 未激活轨道颜色
            )
        )

        // 显示变焦范围提示
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = ZoomConfig.formatZoom(zoomRange.minZoom),
                color = CameraTheme.Colors.textSecondary,                        // 统一提示文字颜色
                fontSize = 10.sp
            )
            Text(
                text = ZoomConfig.formatZoom(zoomRange.maxZoom),
                color = CameraTheme.Colors.textSecondary,
                fontSize = 10.sp
            )
        }
    }
}

/**
 * 变焦快捷档位按钮
 *
 * 使用响应式尺寸系统
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
    val dimens = rememberResponsiveDimens()                                      // 响应式尺寸系统
    val backgroundColor = if (isSelected) CameraTheme.Colors.primary else Color.Transparent
    val textColor = if (isSelected) CameraTheme.Colors.onPrimary else CameraTheme.Colors.textPrimary
    val borderColor = if (isSelected) CameraTheme.Colors.primary else CameraTheme.Colors.textSecondary

    Box(
        modifier = modifier
            .size(dimens.zoomButtonSize)                                         // 响应式按钮尺寸
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
            fontSize = 8.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// ==================== Pill样式变焦控件（小米相机风格） ====================

/**
 * Pill样式变焦选择器（小米相机风格）
 *
 * 显示多个预设变焦倍数的胶囊形状选择器
 * 点击某个倍数直接切换到该倍数
 *
 * @param currentZoom 当前变焦倍数
 * @param zoomRange 设备支持的变焦范围
 * @param onZoomChanged 变焦改变回调
 * @param onExpandSlider 点击展开滑块回调
 * @param modifier 修饰符
 */
@Composable
fun ZoomPillSelector(
    currentZoom: Float,
    zoomRange: ZoomRange = ZoomRange(),
    onZoomChanged: (Float) -> Unit,
    onExpandSlider: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val dimens = rememberResponsiveDimens()

    // 预设变焦倍数（根据设备范围过滤）
    val presets = listOf(0.5f, 1f, 2f, 3.2f, 5f).filter { zoom ->
        zoom >= zoomRange.minZoom && zoom <= zoomRange.maxZoom
    }

    // 计算当前选中的预设
    val selectedPreset = presets.minByOrNull { kotlin.math.abs(it - currentZoom) }

    Row(
        modifier = modifier
            .background(
                color = CameraTheme.ZoomControl.pillBackground,
                shape = RoundedCornerShape(20.dp)
            )
            .border(
                width = 1.dp,
                color = CameraTheme.ZoomControl.pillBorder,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        presets.forEach { zoom ->
            val isSelected = selectedPreset == zoom
            val bgColor = if (isSelected) CameraTheme.Colors.primary else Color.Transparent
            val txtColor = if (isSelected) CameraTheme.Colors.onPrimary else CameraTheme.Colors.textPrimary

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(bgColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (isSelected) {
                                onExpandSlider()                             // 已选中则展开滑块
                            } else {
                                onZoomChanged(zoom)                          // 切换到该倍数
                            }
                        }
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (zoom < 1f) "${(zoom * 10).toInt() / 10.0}x"
                           else if (zoom == zoom.toInt().toFloat()) "${zoom.toInt()}x"
                           else "${zoom}x",
                    color = txtColor,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

/**
 * 带刻度的变焦滑块（小米相机风格）
 *
 * 显示带有刻度标记的水平滑块
 * 刻度对应预设变焦倍数
 *
 * @param currentZoom 当前变焦倍数
 * @param zoomRange 设备支持的变焦范围
 * @param onZoomChanged 变焦改变回调
 * @param onDismiss 关闭滑块回调
 * @param modifier 修饰符
 */
@Composable
fun TickMarkZoomSlider(
    currentZoom: Float,
    zoomRange: ZoomRange = ZoomRange(),
    onZoomChanged: (Float) -> Unit,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val dimens = rememberResponsiveDimens()

    // 预设变焦倍数作为刻度
    val tickMarks = listOf(0.5f, 1f, 2f, 3f, 4f, 5f, 6f, 8f, 10f).filter { zoom ->
        zoom >= zoomRange.minZoom && zoom <= zoomRange.maxZoom
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = CameraTheme.ZoomControl.pillBackground,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 当前变焦倍数显示
        Text(
            text = ZoomConfig.formatZoom(currentZoom),
            color = CameraTheme.Colors.primary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 刻度条
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
        ) {
            // 刻度线绘制
            Canvas(modifier = Modifier.fillMaxWidth().height(20.dp).align(Alignment.TopCenter)) {
                val width = size.width
                val minZoom = zoomRange.minZoom
                val maxZoom = zoomRange.maxZoom
                val range = maxZoom - minZoom

                // 绘制刻度线
                tickMarks.forEach { zoom ->
                    val xPos = ((zoom - minZoom) / range) * width

                    // 主刻度（预设值）
                    drawLine(
                        color = CameraTheme.ZoomControl.tickMajor,
                        start = Offset(xPos, 0f),
                        end = Offset(xPos, 12f),
                        strokeWidth = 2f
                    )
                }

                // 绘制次刻度
                val step = (range / 20f).coerceAtLeast(0.1f)
                var tick = minZoom
                while (tick <= maxZoom) {
                    val xPos = ((tick - minZoom) / range) * width
                    if (tickMarks.none { kotlin.math.abs(it - tick) < 0.1f }) {
                        drawLine(
                            color = CameraTheme.ZoomControl.tickMinor,
                            start = Offset(xPos, 0f),
                            end = Offset(xPos, 6f),
                            strokeWidth = 1f
                        )
                    }
                    tick += step
                }

                // 绘制当前位置指示器
                val currentX = ((currentZoom - minZoom) / range) * width
                drawCircle(
                    color = CameraTheme.Colors.primary,
                    radius = 8f,
                    center = Offset(currentX, 16f)
                )
            }

            // 刻度标签
            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${zoomRange.minZoom}x",
                    color = CameraTheme.Colors.textTertiary,
                    fontSize = 9.sp
                )
                Text(
                    text = "${zoomRange.maxZoom.toInt()}x",
                    color = CameraTheme.Colors.textTertiary,
                    fontSize = 9.sp
                )
            }
        }

        // 滑块控制
        Slider(
            value = currentZoom.coerceIn(zoomRange.minZoom, zoomRange.maxZoom),
            onValueChange = onZoomChanged,
            valueRange = zoomRange.minZoom..zoomRange.maxZoom,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = CameraTheme.Colors.primary,
                activeTrackColor = CameraTheme.Colors.primary,
                inactiveTrackColor = CameraTheme.Colors.textTertiary.copy(alpha = 0.3f)
            )
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
