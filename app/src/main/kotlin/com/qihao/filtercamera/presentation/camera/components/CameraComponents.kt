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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qihao.filtercamera.domain.model.CameraMode
import com.qihao.filtercamera.domain.model.FilterGroup
import com.qihao.filtercamera.domain.model.FilterType
import com.qihao.filtercamera.presentation.common.theme.BottomBarBackground
import com.qihao.filtercamera.presentation.common.theme.FilterGroupActive
import com.qihao.filtercamera.presentation.common.theme.FilterGroupInactive
import com.qihao.filtercamera.presentation.common.theme.FilterSelectorBarBg
import com.qihao.filtercamera.presentation.common.theme.FilterThumbnailBorder
import com.qihao.filtercamera.presentation.common.theme.ModeTabActive
import com.qihao.filtercamera.presentation.common.theme.ModeTabInactive
import com.qihao.filtercamera.presentation.common.theme.ShutterButtonOuter
import com.qihao.filtercamera.presentation.common.theme.ShutterButtonRecording
import com.qihao.filtercamera.presentation.common.theme.iOSYellow
import com.qihao.filtercamera.presentation.common.theme.iOSRed
import com.qihao.filtercamera.presentation.common.theme.iOSGray

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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(BottomBarBackground)                              // 毛玻璃背景
            .padding(top = 12.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 模式切换标签行
        iOSModeTabRow(
            mode = mode,
            onToggleMode = onToggleMode
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 主控制按钮行：[切换] [拍照] [滤镜]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
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

        Spacer(modifier = Modifier.height(8.dp))
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
        Spacer(modifier = Modifier.width(40.dp))
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
    val textColor by animateColorAsState(                                 // 颜色动画
        targetValue = if (isSelected) ModeTabActive else ModeTabInactive,
        animationSpec = tween(200),
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
            .padding(horizontal = 12.dp, vertical = 8.dp)
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
    Box(
        modifier = modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.15f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = Color.White),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Cameraswitch,
            contentDescription = "切换摄像头",
            tint = Color.White,
            modifier = Modifier.size(26.dp)
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
    val innerScale by animateFloatAsState(                                // 内圆缩放动画
        targetValue = if (isCapturing) 0.85f else 1f,
        animationSpec = tween(100),
        label = "shutterScale"
    )

    Box(
        modifier = modifier
            .size(80.dp)
            .clip(CircleShape)
            .border(5.dp, ShutterButtonOuter, CircleShape)                // 白色外圈
            .clickable(
                enabled = !isCapturing,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            mode == CameraMode.VIDEO && isRecording -> {                  // 录像中：红色方块
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(ShutterButtonRecording)
                )
            }
            mode == CameraMode.VIDEO -> {                                 // 录像待机：红色圆点
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .scale(innerScale)
                        .clip(CircleShape)
                        .background(iOSRed)
                )
            }
            else -> {                                                     // 拍照模式：白色内圆
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .scale(innerScale)
                        .clip(CircleShape)
                        .background(ShutterButtonOuter)
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
    val rotation by animateFloatAsState(                                  // 箭头旋转动画
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "filterArrow"
    )

    val bgColor by animateColorAsState(                                   // 背景颜色动画
        targetValue = if (isExpanded) iOSYellow.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.15f),
        animationSpec = tween(200),
        label = "filterBgColor"
    )

    val iconColor by animateColorAsState(                                 // 图标颜色动画
        targetValue = if (isExpanded) iOSYellow else Color.White,
        animationSpec = tween(200),
        label = "filterIconColor"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = iOSYellow),
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowUp,
            contentDescription = if (isExpanded) "收起滤镜" else "展开滤镜",
            tint = iconColor,
            modifier = Modifier
                .size(24.dp)
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(                           // 渐变背景
                    colors = listOf(
                        Color.Transparent,
                        FilterSelectorBarBg.copy(alpha = 0.95f)
                    )
                )
            )
            .padding(top = 16.dp)
    ) {
        // 分组标签栏
        iOSFilterGroupTabBar(
            groups = groups,
            selectedGroup = selectedGroup,
            onGroupSelected = onGroupSelected
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 滤镜列表
        iOSFilterList(
            filters = filters,
            selectedFilter = selectedFilter,
            thumbnails = thumbnails,
            onFilterSelected = onFilterSelected
        )

        // 滤镜强度滑块（仅当选中非NONE滤镜时显示）
        if (selectedFilter != FilterType.NONE) {
            Spacer(modifier = Modifier.height(4.dp))
            CompactFilterIntensitySlider(
                currentIntensity = filterIntensity,
                onIntensityChanged = onIntensityChanged
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
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
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        contentPadding = PaddingValues(horizontal = 16.dp)
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
    val textColor by animateColorAsState(                                 // 颜色动画
        targetValue = if (isSelected) FilterGroupActive else FilterGroupInactive,
        animationSpec = tween(200),
        label = "groupTabColor"
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
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
    val borderColor by animateColorAsState(                               // 边框颜色动画
        targetValue = if (isSelected) FilterThumbnailBorder else Color.Transparent,
        animationSpec = tween(150),
        label = "filterBorder"
    )

    val scale by animateFloatAsState(                                     // 缩放动画
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = tween(150),
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
                .size(60.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF2C2C2E))                            // iOS深灰背景
                .border(
                    width = 2.5.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            // 如果有缩略图则显示缩略图，否则显示文字
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = filter.displayName,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                // 无缩略图时显示滤镜名称缩写
                Text(
                    text = filter.displayName.take(2),
                    color = if (isSelected) iOSYellow else Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 滤镜名称
        Text(
            text = filter.displayName,
            color = if (isSelected) iOSYellow else Color.White.copy(alpha = 0.7f),
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

/**
 * 权限请求UI
 */
@Composable
fun PermissionRequest(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "需要相机权限",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "请授予相机和麦克风权限以使用相机功能",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRequestPermission) {
            Text("授予权限")
        }
    }
}
