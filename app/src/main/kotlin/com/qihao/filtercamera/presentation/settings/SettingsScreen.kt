/**
 * SettingsScreen.kt - 设置页面
 *
 * 应用设置的完整 UI 实现
 * 使用 Material3 设计规范
 *
 * 功能：
 * - 照片设置（质量）
 * - 视频设置（质量）
 * - 网格设置
 * - 位置信息设置
 * - 水印设置
 * - 保存位置设置
 * - 滤镜设置
 * - 声音设置
 * - HDR 设置
 * - 美颜设置
 * - 其他设置
 * - 重置设置
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.presentation.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.qihao.filtercamera.domain.repository.GridType
import com.qihao.filtercamera.domain.repository.PhotoQuality
import com.qihao.filtercamera.domain.repository.SaveLocation
import com.qihao.filtercamera.domain.repository.ThemeMode
import com.qihao.filtercamera.domain.repository.VideoQuality

/**
 * 设置页面
 *
 * @param onNavigateBack 返回回调
 * @param viewModel ViewModel 实例
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,                                           // 返回回调
    viewModel: SettingsViewModel = hiltViewModel()                        // 注入 ViewModel
) {
    val uiState by viewModel.uiState.collectAsState()                     // 收集 UI 状态

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },                                  // 标题
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {                // 返回按钮
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface    // 背景色
                )
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            // 加载中
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            // 设置列表
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 照片设置
                item {
                    SettingsSectionHeader(
                        title = "照片设置",
                        icon = Icons.Default.CameraAlt
                    )
                }

                item {
                    PhotoQualitySelector(
                        selectedQuality = uiState.photoQuality,
                        onQualitySelected = viewModel::setPhotoQuality
                    )
                }

                // 视频设置
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsSectionHeader(
                        title = "视频设置",
                        icon = Icons.Default.Videocam
                    )
                }

                item {
                    VideoQualitySelector(
                        selectedQuality = uiState.videoQuality,
                        onQualitySelected = viewModel::setVideoQuality
                    )
                }

                // 网格设置
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsSectionHeader(
                        title = "网格设置",
                        icon = Icons.Default.GridOn
                    )
                }

                item {
                    GridTypeSelector(
                        selectedType = uiState.gridType,
                        onTypeSelected = viewModel::setGridType
                    )
                }

                // 位置与水印
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsSectionHeader(
                        title = "位置与水印",
                        icon = Icons.Default.LocationOn
                    )
                }

                item {
                    SwitchSettingItem(
                        title = "位置信息",
                        subtitle = "在照片中保存 GPS 位置",
                        checked = uiState.locationEnabled,
                        onCheckedChange = viewModel::setLocationEnabled
                    )
                }

                item {
                    SwitchSettingItem(
                        title = "水印",
                        subtitle = "在照片上添加水印",
                        checked = uiState.watermarkEnabled,
                        onCheckedChange = viewModel::setWatermarkEnabled
                    )
                }

                if (uiState.watermarkEnabled) {
                    item {
                        WatermarkTextInput(
                            text = uiState.watermarkText,
                            onTextChange = viewModel::setWatermarkText
                        )
                    }
                }

                // 存储设置
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsSectionHeader(
                        title = "存储设置",
                        icon = Icons.Default.Folder
                    )
                }

                item {
                    SaveLocationSelector(
                        selectedLocation = uiState.saveLocation,
                        onLocationSelected = viewModel::setSaveLocation
                    )
                }

                item {
                    SwitchSettingItem(
                        title = "自动保存",
                        subtitle = "拍照后自动保存到相册",
                        checked = uiState.autoSaveEnabled,
                        onCheckedChange = viewModel::setAutoSaveEnabled
                    )
                }

                // 声音设置
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsSectionHeader(
                        title = "声音设置",
                        icon = Icons.Default.VolumeUp
                    )
                }

                item {
                    SwitchSettingItem(
                        title = "快门声音",
                        subtitle = "拍照时播放快门音",
                        checked = uiState.shutterSoundEnabled,
                        onCheckedChange = viewModel::setShutterSoundEnabled
                    )
                }

                // HDR 设置
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsSectionHeader(
                        title = "HDR 设置",
                        icon = Icons.Default.HighQuality
                    )
                }

                item {
                    SwitchSettingItem(
                        title = "HDR 自动模式",
                        subtitle = "根据场景自动启用 HDR",
                        checked = uiState.hdrAutoEnabled,
                        onCheckedChange = viewModel::setHdrAutoEnabled
                    )
                }

                // 美颜设置
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsSectionHeader(
                        title = "美颜设置",
                        icon = Icons.Default.Face
                    )
                }

                item {
                    BeautyIntensitySlider(
                        intensity = uiState.defaultBeautyIntensity,
                        onIntensityChange = viewModel::setDefaultBeautyIntensity
                    )
                }

                // 主题设置
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsSectionHeader(
                        title = "主题设置",
                        icon = Icons.Outlined.DarkMode
                    )
                }

                item {
                    ThemeModeSelector(
                        selectedMode = uiState.themeMode,
                        onModeSelected = viewModel::setThemeMode
                    )
                }

                // 其他设置
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsSectionHeader(
                        title = "其他设置",
                        icon = Icons.Default.Settings
                    )
                }

                item {
                    SwitchSettingItem(
                        title = "前置镜像预览",
                        subtitle = "前置摄像头预览时镜像显示",
                        checked = uiState.mirrorPreviewEnabled,
                        onCheckedChange = viewModel::setMirrorPreviewEnabled
                    )
                }

                // 重置设置
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    ResetSettingsButton(onClick = viewModel::showResetDialog)
                }

                // 关于
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    AboutSection()
                }

                // 底部间距
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }

        // 重置确认对话框
        if (uiState.showResetDialog) {
            ResetConfirmDialog(
                onConfirm = viewModel::resetAllSettings,
                onDismiss = viewModel::hideResetDialog
            )
        }
    }
}

/**
 * 设置分区标题
 */
@Composable
private fun SettingsSectionHeader(
    title: String,                                                        // 标题
    icon: ImageVector                                                     // 图标
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * 开关设置项
 */
@Composable
private fun SwitchSettingItem(
    title: String,                                                        // 标题
    subtitle: String,                                                     // 副标题
    checked: Boolean,                                                     // 是否选中
    onCheckedChange: (Boolean) -> Unit                                    // 选中变更回调
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

/**
 * 照片质量选择器
 */
@Composable
private fun PhotoQualitySelector(
    selectedQuality: PhotoQuality,                                        // 当前选中
    onQualitySelected: (PhotoQuality) -> Unit                             // 选择回调
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .selectableGroup()
        ) {
            Text(
                text = "照片质量",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            PhotoQuality.entries.forEach { quality ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = quality == selectedQuality,
                            onClick = { onQualitySelected(quality) },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = quality == selectedQuality,
                        onClick = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = quality.displayName,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "JPEG ${quality.compressionQuality}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * 视频质量选择器
 */
@Composable
private fun VideoQualitySelector(
    selectedQuality: VideoQuality,                                        // 当前选中
    onQualitySelected: (VideoQuality) -> Unit                             // 选择回调
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .selectableGroup()
        ) {
            Text(
                text = "视频质量",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            VideoQuality.entries.forEach { quality ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = quality == selectedQuality,
                            onClick = { onQualitySelected(quality) },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = quality == selectedQuality,
                        onClick = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = quality.displayName,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = quality.resolution,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * 网格类型选择器
 */
@Composable
private fun GridTypeSelector(
    selectedType: GridType,                                               // 当前选中
    onTypeSelected: (GridType) -> Unit                                    // 选择回调
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .selectableGroup()
        ) {
            Text(
                text = "网格类型",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            GridType.entries.forEach { type ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = type == selectedType,
                            onClick = { onTypeSelected(type) },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = type == selectedType,
                        onClick = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = type.displayName,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

/**
 * 保存位置选择器
 */
@Composable
private fun SaveLocationSelector(
    selectedLocation: SaveLocation,                                       // 当前选中
    onLocationSelected: (SaveLocation) -> Unit                            // 选择回调
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .selectableGroup()
        ) {
            Text(
                text = "保存位置",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            // 只显示 DCIM 和 PICTURES，不显示 CUSTOM
            listOf(SaveLocation.DCIM, SaveLocation.PICTURES).forEach { location ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = location == selectedLocation,
                            onClick = { onLocationSelected(location) },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = location == selectedLocation,
                        onClick = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = location.displayName,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = location.path,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * 水印文字输入
 */
@Composable
private fun WatermarkTextInput(
    text: String,                                                         // 当前文字
    onTextChange: (String) -> Unit                                        // 文字变更回调
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                label = { Text("自定义水印文字") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.WaterDrop,
                        contentDescription = null
                    )
                }
            )
        }
    }
}

/**
 * 美颜强度滑块
 */
@Composable
private fun BeautyIntensitySlider(
    intensity: Float,                                                     // 当前强度
    onIntensityChange: (Float) -> Unit                                    // 强度变更回调
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "默认美颜强度",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "${(intensity * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = intensity,
                onValueChange = onIntensityChange,
                valueRange = 0f..1f
            )
        }
    }
}

/**
 * 重置设置按钮
 */
@Composable
private fun ResetSettingsButton(
    onClick: () -> Unit                                                   // 点击回调
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "重置所有设置",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * 重置确认对话框
 */
@Composable
private fun ResetConfirmDialog(
    onConfirm: () -> Unit,                                                // 确认回调
    onDismiss: () -> Unit                                                 // 取消回调
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重置设置") },
        text = { Text("确定要将所有设置恢复为默认值吗？此操作不可撤销。") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("重置", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 关于部分
 */
@Composable
private fun AboutSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "FilterCamera",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "版本 2.0.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "实时滤镜相机应用",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 主题模式选择器
 */
@Composable
private fun ThemeModeSelector(
    selectedMode: ThemeMode,                                              // 当前选中
    onModeSelected: (ThemeMode) -> Unit                                   // 选择回调
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .selectableGroup()
        ) {
            Text(
                text = "主题模式",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            ThemeMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = mode == selectedMode,
                            onClick = { onModeSelected(mode) },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = mode == selectedMode,
                        onClick = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = mode.displayName,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
