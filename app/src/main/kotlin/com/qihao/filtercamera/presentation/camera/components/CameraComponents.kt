/**
 * CameraComponents.kt - 相机UI组件（iOS风格设计）
 *
 * 提供iOS风格的相机UI组件
 * 包含：底部控制栏、滤镜选择器、快门按钮等
 *
 * 设计规范：
 * - iOS风格毛玻璃背景
 * - 黄色主题色（iOS相机风格）
 * - 滤镜按钮在拍照按钮右侧，点击展开/收起
 * - 摄像头切换按钮在拍照按钮左侧
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.presentation.camera.components

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qihao.filtercamera.domain.model.CameraMode
import com.qihao.filtercamera.domain.model.FilterGroup
import com.qihao.filtercamera.domain.model.FilterType
import com.qihao.filtercamera.presentation.common.theme.CameraTheme
import com.qihao.filtercamera.presentation.common.theme.rememberResponsiveDimens

// ==================== iOS风格底部控制栏 ====================

/**
 * iOS风格底部控制栏
 *
 * 布局：[切换摄像头] [●拍照按钮●] [▲滤镜]
 *      [拍照]    [录像]  （模式标签）
 *
 * @param mode 当前相机模式（拍照/录像）
 * @param isRecording 是否正在录像
 * @param isCapturing 是否正在拍照
 * @param isFilterExpanded 滤镜选择器是否展开
 * @param onCapture 拍照/录像按钮回调
 * @param onToggleMode 切换模式回调
 * @param onSwitchCamera 切换摄像头回调
 * @param onToggleFilter 切换滤镜选择器回调
 */
@Composable
fun iOSBottomControls(
    mode: CameraMode,
    isRecording: Boolean,
    isCapturing: Boolean,
    isFilterExpanded: Boolean,
    onCapture: () -> Unit,
    onToggleMode: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleFilter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = rememberResponsiveDimens()                               // 响应式尺寸

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CameraTheme.BottomBar.background)                 // 统一主题色
            .padding(top = dimens.spacing.md, bottom = dimens.spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 模式切换标签行
        iOSModeTabRow(
            mode = mode,
            onToggleMode = onToggleMode
        )

        Spacer(modifier = Modifier.height(dimens.spacing.xl - 4.dp))

        // 主控制按钮行：[切换] [拍照] [滤镜]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.spacing.xxl),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：摄像头切换按钮
            iOSSwitchCameraButton(
                onClick = onSwitchCamera
            )

            // 中间：拍照/录像按钮
            iOSShutterButton(
                mode = mode,
                isRecording = isRecording,
                isCapturing = isCapturing,
                onClick = onCapture
            )

            // 右侧：滤镜切换按钮
            iOSFilterToggleButton(
                isExpanded = isFilterExpanded,
                onClick = onToggleFilter
            )
        }

        Spacer(modifier = Modifier.height(dimens.spacing.sm))
    }
}

/**
 * iOS风格模式切换标签行
 */
@Composable
private fun iOSModeTabRow(
    mode: CameraMode,
    onToggleMode: () -> Unit
) {
    val dimens = rememberResponsiveDimens()                               // 响应式尺寸

    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        iOSModeTab(                                                       // 拍照标签
            text = "拍照",
            isSelected = mode == CameraMode.PHOTO,
            onClick = { if (mode != CameraMode.PHOTO) onToggleMode() }
        )
        Spacer(modifier = Modifier.width(dimens.spacing.xxl + dimens.spacing.sm))
        iOSModeTab(                                                       // 录像标签
            text = "录像",
            isSelected = mode == CameraMode.VIDEO,
            onClick = { if (mode != CameraMode.VIDEO) onToggleMode() }
        )
    }
}

/**
 * iOS风格模式标签
 */
@Composable
private fun iOSModeTab(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val dimens = rememberResponsiveDimens()                               // 响应式尺寸

    val textColor by animateColorAsState(                                 // 颜色动画
        targetValue = if (isSelected) CameraTheme.ModeSelector.active else CameraTheme.ModeSelector.inactive,
        animationSpec = tween(dimens.animation.fast + 50),
        label = "modeTabColor"
    )

    Text(
        text = text,
        color = textColor,
        fontSize = 15.sp,
        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,                                        // 无点击效果
                onClick = onClick
            )
            .padding(horizontal = dimens.spacing.md, vertical = dimens.spacing.sm)
    )
}

/**
 * iOS风格摄像头切换按钮
 */
@Composable
fun iOSSwitchCameraButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = rememberResponsiveDimens()                               // 响应式尺寸

    Box(
        modifier = modifier
            .size(dimens.minTouchTarget + 2.dp)                           // 响应式按钮尺寸
            .clip(CircleShape)
            .background(CameraTheme.Colors.controlBackgroundLight)        // 统一主题色
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = CameraTheme.Colors.textPrimary),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Cameraswitch,
            contentDescription = "切换摄像头",
            tint = CameraTheme.Colors.iconActive,                         // 统一图标色
            modifier = Modifier.size(dimens.iconSizeMedium)
        )
    }
}

/**
 * iOS风格快门按钮
 *
 * 拍照模式：白色外圈 + 白色内圆
 * 录像模式：白色外圈 + 红色圆点（开始）/ 红色方块（录制中）
 */
@Composable
fun iOSShutterButton(
    mode: CameraMode,
    isRecording: Boolean,
    isCapturing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = rememberResponsiveDimens()                               // 响应式尺寸

    val innerScale by animateFloatAsState(                                // 内圆缩放动画
        targetValue = if (isCapturing) 0.85f else 1f,
        animationSpec = tween(dimens.animation.instant),
        label = "shutterScale"
    )

    // 响应式尺寸计算
    val outerSize = dimens.shutterButtonSize                              // 外圈尺寸
    val innerSize = dimens.shutterInnerSize                               // 内圈尺寸
    val recordingSquareSize = (innerSize.value * 0.45f).dp                // 录像方块尺寸

    Box(
        modifier = modifier
            .size(outerSize)
            .clip(CircleShape)
            .border(dimens.shutterStrokeWidth, CameraTheme.Shutter.outer, CircleShape)
            .clickable(
                enabled = !isCapturing,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(dimens.spacing.xs + 2.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            mode == CameraMode.VIDEO && isRecording -> {                  // 录像中：红色方块
                Box(
                    modifier = Modifier
                        .size(recordingSquareSize)
                        .clip(RoundedCornerShape(dimens.spacing.xs + 2.dp))
                        .background(CameraTheme.Shutter.recording)
                )
            }
            mode == CameraMode.VIDEO -> {                                 // 录像待机：红色圆点
                Box(
                    modifier = Modifier
                        .size(innerSize)
                        .scale(innerScale)
                        .clip(CircleShape)
                        .background(CameraTheme.Colors.recording)
                )
            }
            else -> {                                                     // 拍照模式：白色内圆
                Box(
                    modifier = Modifier
                        .size(innerSize)
                        .scale(innerScale)
                        .clip(CircleShape)
                        .background(CameraTheme.Shutter.inner)
                )
            }
        }
    }
}

/**
 * iOS风格滤镜切换按钮
 *
 * 收起时显示▲，展开时显示▼
 */
@Composable
fun iOSFilterToggleButton(
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = rememberResponsiveDimens()                               // 响应式尺寸

    val rotation by animateFloatAsState(                                  // 箭头旋转动画
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "filterArrow"
    )

    val bgColor by animateColorAsState(                                   // 背景颜色动画
        targetValue = if (isExpanded) CameraTheme.Colors.primary.copy(alpha = 0.3f) else CameraTheme.Colors.controlBackgroundLight,
        animationSpec = tween(dimens.animation.fast + 50),
        label = "filterBgColor"
    )

    val iconColor by animateColorAsState(                                 // 图标颜色动画
        targetValue = if (isExpanded) CameraTheme.Colors.primary else CameraTheme.Colors.iconActive,
        animationSpec = tween(dimens.animation.fast + 50),
        label = "filterIconColor"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(dimens.spacing.md))
            .background(bgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = CameraTheme.Colors.primary),
                onClick = onClick
            )
            .padding(horizontal = dimens.spacing.md + 2.dp, vertical = dimens.spacing.sm + 2.dp)
    ) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowUp,
            contentDescription = if (isExpanded) "收起滤镜" else "展开滤镜",
            tint = iconColor,
            modifier = Modifier
                .size(dimens.iconSizeMedium)
                .rotate(rotation)                                         // 旋转箭头
        )
        Text(
            text = "滤镜",
            color = iconColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ==================== iOS风格滤镜选择器 ====================

/**
 * iOS风格分组滤镜选择器
 *
 * 从下往上弹出的滤镜选择面板
 *
 * @param groups 可用分组列表
 * @param selectedGroup 当前选中分组
 * @param filters 当前分组的滤镜列表
 * @param selectedFilter 当前选中滤镜
 * @param thumbnails 滤镜预览缩略图映射
 * @param onGroupSelected 分组选择回调
 * @param onFilterSelected 滤镜选择回调
 */
@Composable
fun iOSFilterSelector(
    groups: List<FilterGroup>,
    selectedGroup: FilterGroup,
    filters: List<FilterType>,
    selectedFilter: FilterType,
    thumbnails: Map<FilterType, Bitmap?> = emptyMap(),
    filterIntensity: Float = 1.0f,                                    // 滤镜强度
    onGroupSelected: (FilterGroup) -> Unit,
    onFilterSelected: (FilterType) -> Unit,
    onIntensityChanged: (Float) -> Unit = {},                         // 强度变化回调
    modifier: Modifier = Modifier
) {
    val dimens = rememberResponsiveDimens()                               // 响应式尺寸

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(                           // 渐变背景
                    colors = listOf(
                        Color.Transparent,
                        CameraTheme.FilterSelector.background.copy(alpha = 0.95f)
                    )
                )
            )
            .padding(top = dimens.spacing.lg)
    ) {
        // 分组标签栏
        iOSFilterGroupTabBar(
            groups = groups,
            selectedGroup = selectedGroup,
            onGroupSelected = onGroupSelected
        )

        Spacer(modifier = Modifier.height(dimens.spacing.md))

        // 滤镜列表
        iOSFilterList(
            filters = filters,
            selectedFilter = selectedFilter,
            thumbnails = thumbnails,
            onFilterSelected = onFilterSelected
        )

        // 滤镜强度滑块（仅当选中非NONE滤镜时显示）
        if (selectedFilter != FilterType.NONE) {
            Spacer(modifier = Modifier.height(dimens.spacing.xs))
            CompactFilterIntensitySlider(
                currentIntensity = filterIntensity,
                onIntensityChanged = onIntensityChanged
            )
        }

        Spacer(modifier = Modifier.height(dimens.spacing.sm))
    }
}

/**
 * iOS风格分组标签栏
 */
@Composable
private fun iOSFilterGroupTabBar(
    groups: List<FilterGroup>,
    selectedGroup: FilterGroup,
    onGroupSelected: (FilterGroup) -> Unit
) {
    val dimens = rememberResponsiveDimens()                               // 响应式尺寸

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        contentPadding = PaddingValues(horizontal = dimens.spacing.lg)
    ) {
        items(groups) { group ->
            iOSFilterGroupTab(
                group = group,
                isSelected = group == selectedGroup,
                onClick = { onGroupSelected(group) }
            )
        }
    }
}

/**
 * iOS风格单个分组标签
 */
@Composable
private fun iOSFilterGroupTab(
    group: FilterGroup,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val dimens = rememberResponsiveDimens()                               // 响应式尺寸

    val textColor by animateColorAsState(                                 // 颜色动画
        targetValue = if (isSelected) CameraTheme.FilterSelector.groupActive else CameraTheme.FilterSelector.groupInactive,
        animationSpec = tween(dimens.animation.fast + 50),
        label = "groupTabColor"
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(dimens.spacing.lg))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = dimens.spacing.lg, vertical = dimens.spacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = group.displayName,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/**
 * iOS风格滤镜列表
 *
 * @param filters 滤镜列表
 * @param selectedFilter 当前选中滤镜
 * @param thumbnails 滤镜预览缩略图映射
 * @param onFilterSelected 滤镜选择回调
 */
@Composable
private fun iOSFilterList(
    filters: List<FilterType>,
    selectedFilter: FilterType,
    thumbnails: Map<FilterType, Bitmap?> = emptyMap(),
    onFilterSelected: (FilterType) -> Unit
) {
    val dimens = rememberResponsiveDimens()                               // 响应式尺寸

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = dimens.spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(dimens.filterItemSpacing)
    ) {
        items(filters) { filter ->
            iOSFilterItem(
                filter = filter,
                isSelected = filter == selectedFilter,
                thumbnail = thumbnails[filter],
                onClick = { onFilterSelected(filter) }
            )
        }
    }
}

/**
 * iOS风格滤镜项
 *
 * 显示滤镜预览缩略图和名称
 *
 * @param filter 滤镜类型
 * @param isSelected 是否选中
 * @param thumbnail 预览缩略图（可选）
 * @param onClick 点击回调
 */
@Composable
private fun iOSFilterItem(
    filter: FilterType,
    isSelected: Boolean,
    thumbnail: Bitmap? = null,
    onClick: () -> Unit
) {
    val dimens = rememberResponsiveDimens()                               // 响应式尺寸

    val borderColor by animateColorAsState(                               // 边框颜色动画
        targetValue = if (isSelected) CameraTheme.FilterSelector.itemSelected else Color.Transparent,
        animationSpec = tween(dimens.animation.fast),
        label = "filterBorder"
    )

    val scale by animateFloatAsState(                                     // 缩放动画
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = tween(dimens.animation.fast),
        label = "filterScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        // 滤镜预览框
        Box(
            modifier = Modifier
                .size(dimens.filterItemSize)                              // 响应式尺寸
                .clip(RoundedCornerShape(dimens.spacing.md))
                .background(CameraTheme.Colors.surface)                   // 统一主题色
                .border(
                    width = 2.5.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(dimens.spacing.md)
                ),
            contentAlignment = Alignment.Center
        ) {
            // 如果有缩略图则显示缩略图，否则显示文字
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = filter.displayName,
                    modifier = Modifier
                        .size(dimens.filterItemSize)
                        .clip(RoundedCornerShape(dimens.spacing.md)),
                    contentScale = ContentScale.Crop
                )
            } else {
                // 无缩略图时显示滤镜名称缩写
                Text(
                    text = filter.displayName.take(2),
                    color = if (isSelected) CameraTheme.Colors.primary else CameraTheme.Colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(dimens.spacing.xs + 2.dp))

        // 滤镜名称
        Text(
            text = filter.displayName,
            color = if (isSelected) CameraTheme.Colors.primary else CameraTheme.Colors.textSecondary,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

// ==================== 兼容旧组件（保留用于渐进迁移）====================

/**
 * 旧版顶部控制栏（保留兼容）
 * @deprecated 使用新的iOSBottomControls替代
 */
@Deprecated("使用iOSBottomControls替代，摄像头切换按钮已移至底部")
@Composable
fun TopControls(
    onSwitchCamera: () -> Unit,
    onToggleFilter: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 空实现，UI已迁移到底部
}

/**
 * 旧版底部控制栏（保留兼容）
 * @deprecated 使用新的iOSBottomControls替代
 */
@Deprecated("使用iOSBottomControls替代")
@Composable
fun BottomControls(
    mode: CameraMode,
    isRecording: Boolean,
    isCapturing: Boolean,
    onCapture: () -> Unit,
    onToggleMode: () -> Unit,
    onGalleryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 调用新组件（无滤镜按钮参数，提供默认值）
    iOSBottomControls(
        mode = mode,
        isRecording = isRecording,
        isCapturing = isCapturing,
        isFilterExpanded = false,
        onCapture = onCapture,
        onToggleMode = onToggleMode,
        onSwitchCamera = {},
        onToggleFilter = {},
        modifier = modifier
    )
}

/**
 * 旧版分组滤镜选择器（保留兼容）
 * @deprecated 使用新的iOSFilterSelector替代
 */
@Deprecated("使用iOSFilterSelector替代")
@Composable
fun GroupedFilterSelector(
    groups: List<FilterGroup>,
    selectedGroup: FilterGroup,
    filters: List<FilterType>,
    selectedFilter: FilterType,
    thumbnails: Map<FilterType, Bitmap?> = emptyMap(),
    onGroupSelected: (FilterGroup) -> Unit,
    onFilterSelected: (FilterType) -> Unit,
    modifier: Modifier = Modifier
) {
    iOSFilterSelector(
        groups = groups,
        selectedGroup = selectedGroup,
        filters = filters,
        selectedFilter = selectedFilter,
        thumbnails = thumbnails,
        onGroupSelected = onGroupSelected,
        onFilterSelected = onFilterSelected,
        modifier = modifier
    )
}

// ==================== 权限请求 ====================

// ==================== 对焦指示器 ====================

/**
 * iOS风格对焦指示器组件（带亮度调节）
 *
 * 在用户触摸屏幕进行对焦时显示四角直角指示器
 * 对焦过程中显示脉冲动画，并在右侧显示亮度调节滑块（太阳图标）
 * 参考小米相机样式：只有四个角的直角，没有完整边框
 *
 * @param focusPoint 对焦点位置（归一化坐标0.0~1.0，null表示不显示）
 * @param isFocusing 是否正在对焦中（用于显示脉冲动画）
 * @param exposureCompensation 曝光补偿值（-1.0到1.0）
 * @param onExposureChange 曝光补偿变化回调
 * @param showExposureSlider 是否显示曝光滑块
 * @param modifier Modifier修饰符（需要使用fillMaxSize以正确定位）
 */
@Composable
fun FocusIndicator(
    focusPoint: Offset?,
    isFocusing: Boolean,
    exposureCompensation: Float = 0f,
    onExposureChange: ((Float) -> Unit)? = null,
    showExposureSlider: Boolean = false,
    modifier: Modifier = Modifier
) {
    // 如果没有对焦点，不显示任何内容
    if (focusPoint == null) return

    val dimens = rememberResponsiveDimens()                               // 响应式尺寸

    // 对焦过程中的脉冲动画（缩放1.0~1.15）
    val infiniteTransition = rememberInfiniteTransition(label = "focusPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = dimens.animation.slow, easing = FastOutSlowInEasing), // 响应式动画
            repeatMode = RepeatMode.Reverse
        ),
        label = "focusPulseScale"
    )

    // 对焦完成后的静态缩放（初始1.3缩小到1.0）
    val staticScale by animateFloatAsState(
        targetValue = if (isFocusing) 1.3f else 1.0f,
        animationSpec = tween(durationMillis = dimens.animation.fast + 50, easing = FastOutSlowInEasing),
        label = "focusStaticScale"
    )

    // 最终缩放值：对焦中使用脉冲动画，否则使用静态缩放
    val finalScale = if (isFocusing) pulseScale * staticScale else staticScale

    // 边框透明度动画
    val borderAlpha by animateFloatAsState(
        targetValue = if (isFocusing) 1.0f else 0.8f,
        animationSpec = tween(durationMillis = dimens.animation.fast),
        label = "focusBorderAlpha"
    )

    Box(modifier = modifier) {
        // 使用BoxWithConstraints获取父容器尺寸，计算对焦点的实际像素位置
        BoxWithConstraints(
            modifier = Modifier.matchParentSize()
        ) {
            val focusSize = dimens.focusIndicatorSize                     // 响应式对焦框尺寸
            val halfSize = focusSize / 2
            val cornerSize = dimens.focusCornerLength                     // 响应式角标长度
            val cornerWidth = dimens.focusStrokeWidth + 0.5.dp            // 响应式角标宽度

            // 计算对焦框中心位置（基于归一化坐标）
            val centerX = maxWidth * focusPoint.x
            val centerY = maxHeight * focusPoint.y

            // 对焦框：只有四角直角标记（无完整边框）
            Box(
                modifier = Modifier
                    .size(focusSize)
                    .scale(finalScale)
                    .offset(                                            // 定位到触摸点（居中）
                        x = centerX - halfSize,
                        y = centerY - halfSize
                    )
            ) {
                // 移除主边框，只保留四角直角（类似小米相机样式）
                // 左上角
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(cornerSize)
                ) {
                    Box(                                                // 水平线
                        modifier = Modifier
                            .width(cornerSize)
                            .height(cornerWidth)
                            .background(CameraTheme.FocusIndicator.corner.copy(alpha = borderAlpha))
                    )
                    Box(                                                // 垂直线
                        modifier = Modifier
                            .width(cornerWidth)
                            .height(cornerSize)
                            .background(CameraTheme.FocusIndicator.corner.copy(alpha = borderAlpha))
                    )
                }

                // 右上角
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(cornerSize)
                ) {
                    Box(                                                // 水平线
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .width(cornerSize)
                            .height(cornerWidth)
                            .background(CameraTheme.FocusIndicator.corner.copy(alpha = borderAlpha))
                    )
                    Box(                                                // 垂直线
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .width(cornerWidth)
                            .height(cornerSize)
                            .background(CameraTheme.FocusIndicator.corner.copy(alpha = borderAlpha))
                    )
                }

                // 左下角
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .size(cornerSize)
                ) {
                    Box(                                                // 水平线
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .width(cornerSize)
                            .height(cornerWidth)
                            .background(CameraTheme.FocusIndicator.corner.copy(alpha = borderAlpha))
                    )
                    Box(                                                // 垂直线
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .width(cornerWidth)
                            .height(cornerSize)
                            .background(CameraTheme.FocusIndicator.corner.copy(alpha = borderAlpha))
                    )
                }

                // 右下角
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(cornerSize)
                ) {
                    Box(                                                // 水平线
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .width(cornerSize)
                            .height(cornerWidth)
                            .background(CameraTheme.FocusIndicator.corner.copy(alpha = borderAlpha))
                    )
                    Box(                                                // 垂直线
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .width(cornerWidth)
                            .height(cornerSize)
                            .background(CameraTheme.FocusIndicator.corner.copy(alpha = borderAlpha))
                    )
                }
            }

            // 曝光调节滑块（太阳图标 + 垂直滑块）
            // 显示在聚焦框右侧，上下滑动调节亮度
            if (showExposureSlider && onExposureChange != null) {
                val sliderHeight = 120.dp
                val sliderWidth = 36.dp
                val sliderX = centerX + halfSize + dimens.spacing.md    // 聚焦框右侧
                val sliderY = centerY - sliderHeight / 2                 // 垂直居中
                val sliderTrackHeight = 60f                              // 滑块轨道高度（dp）

                Column(
                    modifier = Modifier
                        .offset(x = sliderX, y = sliderY)
                        .width(sliderWidth)
                        .height(sliderHeight)
                        .background(
                            color = Color.Black.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(18.dp)
                        )
                        .pointerInput(Unit) {                            // 添加拖动手势
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var lastY = down.position.y
                                // 初始位置转换为曝光值
                                val initialExposure = 1f - (lastY / (sliderTrackHeight * density)) * 2f
                                onExposureChange(initialExposure.coerceIn(-1f, 1f))

                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull()
                                    if (change != null && change.pressed) {
                                        val currentY = change.position.y
                                        // 计算拖动距离转曝光变化（灵敏度调低，除以2）
                                        val deltaExposure = -(currentY - lastY) / (sliderTrackHeight * density * 2)
                                        val newExposure = (exposureCompensation + deltaExposure).coerceIn(-1f, 1f)
                                        onExposureChange(newExposure)
                                        lastY = currentY
                                        change.consume()
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // 太阳图标（顶部 - 亮）
                    Text(
                        text = "☀",
                        color = CameraTheme.FocusIndicator.corner,
                        fontSize = 14.sp
                    )

                    // 垂直滑块指示器
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .weight(1f)
                            .padding(vertical = 4.dp)
                            .background(
                                color = Color.White.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(2.dp)
                            )
                    ) {
                        // 滑块位置指示器（基于曝光值）
                        val indicatorPosition = (1f - (exposureCompensation + 1f) / 2f)  // 归一化到0-1
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = (indicatorPosition * 60).dp)
                                .size(8.dp)
                                .background(
                                    color = CameraTheme.FocusIndicator.corner,
                                    shape = CircleShape
                                )
                        )
                    }

                    // 月亮图标（底部 - 暗）
                    Text(
                        text = "🌙",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

// ==================== 权限请求组件 ====================

/**
 * 权限请求UI
 */
@Composable
fun PermissionRequest(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = rememberResponsiveDimens()                               // 响应式尺寸

    Column(
        modifier = modifier.padding(dimens.spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "需要相机权限",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(dimens.spacing.lg))
        Text(
            text = "请授予相机和麦克风权限以使用相机功能",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(dimens.spacing.xl))
        Button(onClick = onRequestPermission) {
            Text("授予权限")
        }
    }
}
