/**
 * SettingsViewModel.kt - 设置页面 ViewModel
 *
 * 管理设置页面的 UI 状态和用户交互
 * 连接 SettingsRepository 实现设置的读写
 *
 * 功能：
 * - 加载所有设置项
 * - 响应用户设置变更
 * - 提供 UI 状态流
 * - 支持重置设置
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.presentation.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qihao.filtercamera.domain.model.FilterType
import com.qihao.filtercamera.domain.repository.GridType
import com.qihao.filtercamera.domain.repository.ISettingsRepository
import com.qihao.filtercamera.domain.repository.PhotoQuality
import com.qihao.filtercamera.domain.repository.SaveLocation
import com.qihao.filtercamera.domain.repository.ThemeMode
import com.qihao.filtercamera.domain.repository.VideoQuality
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 设置页面 UI 状态
 *
 * @param photoQuality 照片质量
 * @param videoQuality 视频质量
 * @param gridType 网格类型
 * @param locationEnabled 位置信息开关
 * @param watermarkEnabled 水印开关
 * @param watermarkText 自定义水印文字
 * @param saveLocation 保存位置
 * @param customSavePath 自定义保存路径
 * @param defaultFilter 默认滤镜
 * @param shutterSoundEnabled 快门声音开关
 * @param hdrAutoEnabled HDR 自动模式开关
 * @param defaultBeautyIntensity 美颜默认强度
 * @param themeMode 主题模式（跟随系统/浅色/深色）
 * @param mirrorPreviewEnabled 镜像预览开关
 * @param autoSaveEnabled 自动保存开关
 * @param isLoading 是否正在加载
 * @param showResetDialog 是否显示重置确认对话框
 */
data class SettingsUiState(
    val photoQuality: PhotoQuality = PhotoQuality.HIGH,                   // 照片质量
    val videoQuality: VideoQuality = VideoQuality.QUALITY_1080P,          // 视频质量
    val gridType: GridType = GridType.RULE_OF_THIRDS,                     // 网格类型
    val locationEnabled: Boolean = false,                                 // 位置信息开关
    val watermarkEnabled: Boolean = false,                                // 水印开关
    val watermarkText: String = "FilterCamera",                           // 自定义水印文字
    val saveLocation: SaveLocation = SaveLocation.DCIM,                   // 保存位置
    val customSavePath: String = "",                                      // 自定义保存路径
    val defaultFilter: FilterType = FilterType.NONE,                      // 默认滤镜
    val shutterSoundEnabled: Boolean = true,                              // 快门声音开关
    val hdrAutoEnabled: Boolean = false,                                  // HDR 自动模式开关
    val defaultBeautyIntensity: Float = 0.5f,                             // 美颜默认强度
    val themeMode: ThemeMode = ThemeMode.SYSTEM,                          // 主题模式
    val mirrorPreviewEnabled: Boolean = true,                             // 镜像预览开关
    val autoSaveEnabled: Boolean = true,                                  // 自动保存开关
    val isLoading: Boolean = true,                                        // 是否正在加载
    val showResetDialog: Boolean = false                                  // 是否显示重置确认对话框
)

/**
 * 设置页面 ViewModel
 *
 * @param settingsRepository 设置仓库
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: ISettingsRepository
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsViewModel"                       // 日志标签
    }

    /** UI 状态 */
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        Log.d(TAG, "init: 初始化 SettingsViewModel")
        loadAllSettings()                                                 // 加载所有设置
    }

    /**
     * 加载所有设置
     *
     * 从 DataStore 读取所有设置项并更新 UI 状态
     */
    private fun loadAllSettings() {
        Log.d(TAG, "loadAllSettings: 开始加载设置")

        // 照片质量
        viewModelScope.launch {
            settingsRepository.getPhotoQuality().collect { quality ->
                _uiState.update { it.copy(photoQuality = quality) }
            }
        }

        // 视频质量
        viewModelScope.launch {
            settingsRepository.getVideoQuality().collect { quality ->
                _uiState.update { it.copy(videoQuality = quality) }
            }
        }

        // 网格类型
        viewModelScope.launch {
            settingsRepository.getGridType().collect { type ->
                _uiState.update { it.copy(gridType = type) }
            }
        }

        // 位置信息
        viewModelScope.launch {
            settingsRepository.isLocationEnabled().collect { enabled ->
                _uiState.update { it.copy(locationEnabled = enabled) }
            }
        }

        // 水印开关
        viewModelScope.launch {
            settingsRepository.isWatermarkEnabled().collect { enabled ->
                _uiState.update { it.copy(watermarkEnabled = enabled) }
            }
        }

        // 水印文字
        viewModelScope.launch {
            settingsRepository.getWatermarkText().collect { text ->
                _uiState.update { it.copy(watermarkText = text) }
            }
        }

        // 保存位置
        viewModelScope.launch {
            settingsRepository.getSaveLocation().collect { location ->
                _uiState.update { it.copy(saveLocation = location) }
            }
        }

        // 自定义保存路径
        viewModelScope.launch {
            settingsRepository.getCustomSavePath().collect { path ->
                _uiState.update { it.copy(customSavePath = path) }
            }
        }

        // 默认滤镜
        viewModelScope.launch {
            settingsRepository.getDefaultFilter().collect { filter ->
                _uiState.update { it.copy(defaultFilter = filter) }
            }
        }

        // 快门声音
        viewModelScope.launch {
            settingsRepository.isShutterSoundEnabled().collect { enabled ->
                _uiState.update { it.copy(shutterSoundEnabled = enabled) }
            }
        }

        // HDR 自动模式
        viewModelScope.launch {
            settingsRepository.isHdrAutoEnabled().collect { enabled ->
                _uiState.update { it.copy(hdrAutoEnabled = enabled) }
            }
        }

        // 美颜强度
        viewModelScope.launch {
            settingsRepository.getDefaultBeautyIntensity().collect { intensity ->
                _uiState.update { it.copy(defaultBeautyIntensity = intensity) }
            }
        }

        // 主题模式
        viewModelScope.launch {
            settingsRepository.getThemeMode().collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }

        // 镜像预览
        viewModelScope.launch {
            settingsRepository.isMirrorPreviewEnabled().collect { enabled ->
                _uiState.update { it.copy(mirrorPreviewEnabled = enabled) }
            }
        }

        // 自动保存
        viewModelScope.launch {
            settingsRepository.isAutoSaveEnabled().collect { enabled ->
                _uiState.update { it.copy(autoSaveEnabled = enabled, isLoading = false) }
            }
        }
    }

    // ==================== 照片设置 ====================

    /**
     * 设置照片质量
     */
    fun setPhotoQuality(quality: PhotoQuality) {
        Log.d(TAG, "setPhotoQuality: $quality")
        viewModelScope.launch {
            settingsRepository.setPhotoQuality(quality)
        }
    }

    // ==================== 视频设置 ====================

    /**
     * 设置视频质量
     */
    fun setVideoQuality(quality: VideoQuality) {
        Log.d(TAG, "setVideoQuality: $quality")
        viewModelScope.launch {
            settingsRepository.setVideoQuality(quality)
        }
    }

    // ==================== 网格设置 ====================

    /**
     * 设置网格类型
     */
    fun setGridType(type: GridType) {
        Log.d(TAG, "setGridType: $type")
        viewModelScope.launch {
            settingsRepository.setGridType(type)
        }
    }

    // ==================== 位置信息设置 ====================

    /**
     * 设置位置信息开关
     */
    fun setLocationEnabled(enabled: Boolean) {
        Log.d(TAG, "setLocationEnabled: $enabled")
        viewModelScope.launch {
            settingsRepository.setLocationEnabled(enabled)
        }
    }

    // ==================== 水印设置 ====================

    /**
     * 设置水印开关
     */
    fun setWatermarkEnabled(enabled: Boolean) {
        Log.d(TAG, "setWatermarkEnabled: $enabled")
        viewModelScope.launch {
            settingsRepository.setWatermarkEnabled(enabled)
        }
    }

    /**
     * 设置自定义水印文字
     */
    fun setWatermarkText(text: String) {
        Log.d(TAG, "setWatermarkText: $text")
        viewModelScope.launch {
            settingsRepository.setWatermarkText(text)
        }
    }

    // ==================== 保存位置设置 ====================

    /**
     * 设置保存位置
     */
    fun setSaveLocation(location: SaveLocation) {
        Log.d(TAG, "setSaveLocation: $location")
        viewModelScope.launch {
            settingsRepository.setSaveLocation(location)
        }
    }

    /**
     * 设置自定义保存路径
     */
    fun setCustomSavePath(path: String) {
        Log.d(TAG, "setCustomSavePath: $path")
        viewModelScope.launch {
            settingsRepository.setCustomSavePath(path)
        }
    }

    // ==================== 滤镜设置 ====================

    /**
     * 设置默认滤镜
     */
    fun setDefaultFilter(filterType: FilterType) {
        Log.d(TAG, "setDefaultFilter: $filterType")
        viewModelScope.launch {
            settingsRepository.setDefaultFilter(filterType)
        }
    }

    // ==================== 声音设置 ====================

    /**
     * 设置快门声音开关
     */
    fun setShutterSoundEnabled(enabled: Boolean) {
        Log.d(TAG, "setShutterSoundEnabled: $enabled")
        viewModelScope.launch {
            settingsRepository.setShutterSoundEnabled(enabled)
        }
    }

    // ==================== HDR 设置 ====================

    /**
     * 设置 HDR 自动模式开关
     */
    fun setHdrAutoEnabled(enabled: Boolean) {
        Log.d(TAG, "setHdrAutoEnabled: $enabled")
        viewModelScope.launch {
            settingsRepository.setHdrAutoEnabled(enabled)
        }
    }

    // ==================== 美颜设置 ====================

    /**
     * 设置美颜默认强度
     */
    fun setDefaultBeautyIntensity(intensity: Float) {
        Log.d(TAG, "setDefaultBeautyIntensity: $intensity")
        viewModelScope.launch {
            settingsRepository.setDefaultBeautyIntensity(intensity)
        }
    }

    // ==================== 主题设置 ====================

    /**
     * 设置主题模式
     */
    fun setThemeMode(mode: ThemeMode) {
        Log.d(TAG, "setThemeMode: $mode")
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    // ==================== 其他设置 ====================

    /**
     * 设置镜像预览开关
     */
    fun setMirrorPreviewEnabled(enabled: Boolean) {
        Log.d(TAG, "setMirrorPreviewEnabled: $enabled")
        viewModelScope.launch {
            settingsRepository.setMirrorPreviewEnabled(enabled)
        }
    }

    /**
     * 设置自动保存开关
     */
    fun setAutoSaveEnabled(enabled: Boolean) {
        Log.d(TAG, "setAutoSaveEnabled: $enabled")
        viewModelScope.launch {
            settingsRepository.setAutoSaveEnabled(enabled)
        }
    }

    // ==================== 重置设置 ====================

    /**
     * 显示重置确认对话框
     */
    fun showResetDialog() {
        Log.d(TAG, "showResetDialog: 显示重置对话框")
        _uiState.update { it.copy(showResetDialog = true) }
    }

    /**
     * 隐藏重置确认对话框
     */
    fun hideResetDialog() {
        Log.d(TAG, "hideResetDialog: 隐藏重置对话框")
        _uiState.update { it.copy(showResetDialog = false) }
    }

    /**
     * 重置所有设置
     */
    fun resetAllSettings() {
        Log.d(TAG, "resetAllSettings: 重置所有设置")
        viewModelScope.launch {
            settingsRepository.resetAllSettings()
            _uiState.update { it.copy(showResetDialog = false) }
        }
    }
}
