/**
 * EditScreen.kt - 图片编辑页面
 *
 * 提供完整的图片编辑功能界面
 * 包含：预览区、编辑模式切换、调整/裁剪/滤镜工具
 *
 * 设计特点：
 * - 全屏预览区域
 * - 底部工具栏切换编辑模式
 * - 顶部工具栏：返回、撤销/重做、保存
 * - 支持手势操作（缩放、拖动）
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.presentation.edit

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.qihao.filtercamera.domain.model.AdjustType
import com.qihao.filtercamera.domain.model.CropRatio
import com.qihao.filtercamera.domain.model.EditEvent
import com.qihao.filtercamera.domain.model.EditMode
import com.qihao.filtercamera.domain.model.FilterType
import kotlinx.coroutines.flow.collectLatest

// ==================== 颜色常量 ====================

private val AccentColor = Color(0xFFFF6B35)                                   // 主题色（橙红色）
private val BackgroundColor = Color(0xFF1C1C1E)                               // 背景色
private val SurfaceColor = Color(0xFF2C2C2E)                                  // 表面色
private val OnSurfaceColor = Color.White                                      // 表面上的文字色

/**
 * 图片编辑页面
 *
 * @param imageUri 要编辑的图片URI
 * @param viewModel 编辑ViewModel
 * @param onNavigateBack 返回回调
 */
@Composable
fun EditScreen(
    imageUri: Uri? = null,
    viewModel: EditViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // 处理事件
    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is EditEvent.SaveSuccess -> {
                    Toast.makeText(context, "图片已保存", Toast.LENGTH_SHORT).show()
                    onNavigateBack()
                }
                is EditEvent.SaveFailed -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is EditEvent.Error -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is EditEvent.LoadSuccess -> {
                    // 加载成功，无需额外操作
                }
            }
        }
    }

    // 如果传入了 URI 且当前没有加载图片，则加载
    LaunchedEffect(imageUri) {
        if (imageUri != null && uiState.sourceUri == null) {
            viewModel.loadImage(imageUri)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 顶部工具栏
            EditTopBar(
                canUndo = uiState.canUndo(),
                canRedo = uiState.canRedo(),
                isSaving = uiState.isSaving,
                onBack = onNavigateBack,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                onSave = viewModel::saveImage
            )

            // 预览区域
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                // 显示预览图或原图（对比模式）
                val displayBitmap = if (uiState.isComparing) {
                    uiState.sourceBitmap
                } else {
                    uiState.previewBitmap
                }

                if (displayBitmap != null) {
                    Image(
                        bitmap = displayBitmap.asImageBitmap(),
                        contentDescription = "编辑预览",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        viewModel.startCompare()
                                        tryAwaitRelease()
                                        viewModel.endCompare()
                                    }
                                )
                            },
                        contentScale = ContentScale.Fit
                    )

                    // 对比模式提示
                    if (uiState.isComparing) {
                        Text(
                            text = "原图",
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .background(Color(0x80000000), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // 加载中
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        color = AccentColor,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            // 编辑工具区域
            EditToolsArea(
                currentMode = uiState.currentMode,
                adjustParams = uiState.adjustParams,
                selectedAdjustType = uiState.selectedAdjustType,
                cropState = uiState.cropState,
                filterType = uiState.filterType,
                filterIntensity = uiState.filterIntensity,
                viewModel = viewModel
            )

            // 底部模式切换栏
            EditModeBar(
                currentMode = uiState.currentMode,
                onModeSelected = viewModel::setEditMode
            )
        }

        // 保存中遮罩
        if (uiState.isSaving) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x80000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AccentColor)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("正在保存...", color = Color.White)
                }
            }
        }
    }
}

/**
 * 顶部工具栏
 */
@Composable
private fun EditTopBar(
    canUndo: Boolean,
    canRedo: Boolean,
    isSaving: Boolean,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：返回按钮
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = OnSurfaceColor
            )
        }

        // 中间：撤销/重做
        Row {
            IconButton(onClick = onUndo, enabled = canUndo) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "撤销",
                    tint = if (canUndo) OnSurfaceColor else OnSurfaceColor.copy(alpha = 0.3f)
                )
            }
            IconButton(onClick = onRedo, enabled = canRedo) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Redo,
                    contentDescription = "重做",
                    tint = if (canRedo) OnSurfaceColor else OnSurfaceColor.copy(alpha = 0.3f)
                )
            }
        }

        // 右侧：保存按钮
        IconButton(onClick = onSave, enabled = !isSaving) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "保存",
                tint = AccentColor
            )
        }
    }
}

/**
 * 编辑工具区域
 */
@Composable
private fun EditToolsArea(
    currentMode: EditMode,
    adjustParams: com.qihao.filtercamera.domain.model.AdjustParams,
    selectedAdjustType: AdjustType,
    cropState: com.qihao.filtercamera.domain.model.CropState,
    filterType: FilterType,
    filterIntensity: Float,
    viewModel: EditViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceColor)
            .padding(vertical = 8.dp)
    ) {
        when (currentMode) {
            EditMode.ADJUST -> AdjustTools(
                selectedType = selectedAdjustType,
                currentValue = viewModel.getAdjustParamValue(selectedAdjustType),
                onTypeSelected = viewModel::selectAdjustType,
                onValueChanged = { viewModel.updateAdjustParam(selectedAdjustType, it) },
                onReset = viewModel::resetAdjustParams
            )
            EditMode.CROP -> CropTools(
                currentRatio = cropState.cropRatio,
                rotation = cropState.rotation,
                onRatioSelected = viewModel::setCropRatio,
                onRotate = viewModel::rotateImage,
                onFlipHorizontal = viewModel::flipHorizontal,
                onFlipVertical = viewModel::flipVertical
            )
            EditMode.FILTER -> FilterTools(
                selectedFilter = filterType,
                filterIntensity = filterIntensity,
                onFilterSelected = viewModel::selectFilter,
                onIntensityChanged = viewModel::setFilterIntensity
            )
        }
    }
}

/**
 * 调整工具
 */
@Composable
private fun AdjustTools(
    selectedType: AdjustType,
    currentValue: Float,
    onTypeSelected: (AdjustType) -> Unit,
    onValueChanged: (Float) -> Unit,
    onReset: () -> Unit
) {
    Column {
        // 当前参数滑块
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selectedType.displayName,
                color = OnSurfaceColor,
                fontSize = 14.sp,
                modifier = Modifier.width(56.dp)
            )

            var sliderValue by remember(currentValue) { mutableFloatStateOf(currentValue) }

            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { onValueChanged(sliderValue) },
                valueRange = if (selectedType == AdjustType.VIGNETTE) 0f..1f else -1f..1f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = AccentColor,
                    activeTrackColor = AccentColor,
                    inactiveTrackColor = OnSurfaceColor.copy(alpha = 0.3f)
                )
            )

            Text(
                text = "${(sliderValue * 100).toInt()}",
                color = OnSurfaceColor,
                fontSize = 14.sp,
                modifier = Modifier.width(40.dp),
                textAlign = TextAlign.End
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 调整类型选择
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
        ) {
            items(AdjustType.getAll()) { type ->
                AdjustTypeItem(
                    type = type,
                    isSelected = type == selectedType,
                    onClick = { onTypeSelected(type) }
                )
            }

            // 重置按钮
            item {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onReset() }
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "重置",
                        tint = OnSurfaceColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "重置",
                        color = OnSurfaceColor,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

/**
 * 调整类型项
 */
@Composable
private fun AdjustTypeItem(
    type: AdjustType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) AccentColor.copy(alpha = 0.2f) else Color.Transparent)
            .clickable { onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = type.icon,
            fontSize = 20.sp,
            color = if (isSelected) AccentColor else OnSurfaceColor
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = type.displayName,
            color = if (isSelected) AccentColor else OnSurfaceColor,
            fontSize = 12.sp
        )
    }
}

/**
 * 裁剪工具
 */
@Composable
private fun CropTools(
    currentRatio: CropRatio,
    rotation: Float,
    onRatioSelected: (CropRatio) -> Unit,
    onRotate: () -> Unit,
    onFlipHorizontal: () -> Unit,
    onFlipVertical: () -> Unit
) {
    Column {
        // 裁剪比例选择
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
        ) {
            items(CropRatio.getAll()) { ratio ->
                CropRatioItem(
                    ratio = ratio,
                    isSelected = ratio == currentRatio,
                    onClick = { onRatioSelected(ratio) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 变换工具
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TransformButton(
                icon = Icons.Default.Rotate90DegreesCw,
                label = "旋转",
                onClick = onRotate
            )
            TransformButton(
                icon = Icons.Default.FlipCameraAndroid,
                label = "水平翻转",
                onClick = onFlipHorizontal
            )
            TransformButton(
                icon = Icons.Default.FlipCameraAndroid,
                label = "垂直翻转",
                onClick = onFlipVertical,
                iconRotation = 90f
            )
        }
    }
}

/**
 * 裁剪比例项
 */
@Composable
private fun CropRatioItem(
    ratio: CropRatio,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) AccentColor.copy(alpha = 0.2f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 比例图标
        Box(
            modifier = Modifier
                .size(32.dp)
                .border(
                    width = 2.dp,
                    color = if (isSelected) AccentColor else OnSurfaceColor.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(4.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (ratio != CropRatio.FREE) {
                // 显示比例指示
                val aspectRatio = ratio.getAspectRatio()
                Box(
                    modifier = Modifier
                        .size(
                            width = if (aspectRatio >= 1) 24.dp else (24 * aspectRatio).dp,
                            height = if (aspectRatio <= 1) 24.dp else (24 / aspectRatio).dp
                        )
                        .background(
                            if (isSelected) AccentColor.copy(alpha = 0.3f)
                            else OnSurfaceColor.copy(alpha = 0.2f),
                            RoundedCornerShape(2.dp)
                        )
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = ratio.displayName,
            color = if (isSelected) AccentColor else OnSurfaceColor,
            fontSize = 12.sp
        )
    }
}

/**
 * 变换按钮
 */
@Composable
private fun TransformButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    iconRotation: Float = 0f
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = OnSurfaceColor,
            modifier = Modifier
                .size(28.dp)
                .then(
                    if (iconRotation != 0f)
                        Modifier.graphicsLayer { rotationZ = iconRotation }
                    else Modifier
                )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = OnSurfaceColor,
            fontSize = 12.sp
        )
    }
}

/**
 * 滤镜工具
 */
@Composable
private fun FilterTools(
    selectedFilter: FilterType,
    filterIntensity: Float,
    onFilterSelected: (FilterType) -> Unit,
    onIntensityChanged: (Float) -> Unit
) {
    Column {
        // 滤镜强度滑块（仅在选中滤镜时显示）
        if (selectedFilter != FilterType.NONE) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "强度",
                    color = OnSurfaceColor,
                    fontSize = 14.sp,
                    modifier = Modifier.width(40.dp)
                )

                var sliderValue by remember(filterIntensity) { mutableFloatStateOf(filterIntensity) }

                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = { onIntensityChanged(sliderValue) },
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = AccentColor,
                        activeTrackColor = AccentColor,
                        inactiveTrackColor = OnSurfaceColor.copy(alpha = 0.3f)
                    )
                )

                Text(
                    text = "${(sliderValue * 100).toInt()}%",
                    color = OnSurfaceColor,
                    fontSize = 14.sp,
                    modifier = Modifier.width(48.dp),
                    textAlign = TextAlign.End
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // 滤镜列表
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
        ) {
            // 常用滤镜列表
            val filters = listOf(
                FilterType.NONE,
                FilterType.AMARO,
                FilterType.RISE,
                FilterType.HUDSON,
                FilterType.VALENCIA,
                FilterType.XPROII,
                FilterType.SIERRA,
                FilterType.LOMO,
                FilterType.EARLYBIRD,
                FilterType.TOASTER,
                FilterType.BRANNAN,
                FilterType.INKWELL,
                FilterType.WALDEN,
                FilterType.HEFE,
                FilterType.NASHVILLE,
                FilterType.N1977
            )

            items(filters) { filter ->
                FilterItem(
                    filter = filter,
                    isSelected = filter == selectedFilter,
                    onClick = { onFilterSelected(filter) }
                )
            }
        }
    }
}

/**
 * 滤镜项
 */
@Composable
private fun FilterItem(
    filter: FilterType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) AccentColor.copy(alpha = 0.2f) else Color.Transparent)
            .clickable { onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 滤镜缩略图占位
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isSelected) AccentColor.copy(alpha = 0.3f) else SurfaceColor)
                .border(
                    width = if (isSelected) 2.dp else 0.dp,
                    color = if (isSelected) AccentColor else Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (filter == FilterType.NONE) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = OnSurfaceColor.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Text(
                    text = filter.displayName.take(2),
                    color = if (isSelected) AccentColor else OnSurfaceColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (filter == FilterType.NONE) "原图" else filter.displayName,
            color = if (isSelected) AccentColor else OnSurfaceColor,
            fontSize = 10.sp,
            maxLines = 1
        )
    }
}

/**
 * 底部模式切换栏
 */
@Composable
private fun EditModeBar(
    currentMode: EditMode,
    onModeSelected: (EditMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BackgroundColor)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        EditMode.getAll().forEach { mode ->
            EditModeItem(
                mode = mode,
                isSelected = mode == currentMode,
                onClick = { onModeSelected(mode) }
            )
        }
    }
}

/**
 * 编辑模式项
 */
@Composable
private fun EditModeItem(
    mode: EditMode,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val icon = when (mode) {
        EditMode.ADJUST -> Icons.Default.Tune
        EditMode.CROP -> Icons.Default.Crop
        EditMode.FILTER -> Icons.Outlined.AutoFixHigh
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = mode.displayName,
            tint = if (isSelected) AccentColor else OnSurfaceColor,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = mode.displayName,
            color = if (isSelected) AccentColor else OnSurfaceColor,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )

        // 选中指示器
        if (isSelected) {
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .background(AccentColor, CircleShape)
            )
        }
    }
}
