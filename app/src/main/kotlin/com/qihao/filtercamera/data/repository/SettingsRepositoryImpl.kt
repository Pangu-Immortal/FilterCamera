/**
 * SettingsRepositoryImpl.kt - 设置仓库实现
 *
 * 使用 DataStore Preferences 实现设置的持久化存储
 * 提供响应式数据流，支持设置变更的实时监听
 *
 * 技术实现：
 * - DataStore Preferences 进行键值对存储
 * - Flow 实现响应式数据流
 * - 枚举类型通过名称字符串存储
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.qihao.filtercamera.data.local.SettingsDefaults
import com.qihao.filtercamera.data.local.SettingsKeys
import com.qihao.filtercamera.domain.model.FilterType
import com.qihao.filtercamera.domain.repository.GridType
import com.qihao.filtercamera.domain.repository.ISettingsRepository
import com.qihao.filtercamera.domain.repository.PhotoQuality
import com.qihao.filtercamera.domain.repository.SaveLocation
import com.qihao.filtercamera.domain.repository.ThemeMode
import com.qihao.filtercamera.domain.repository.VideoQuality
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Context 扩展属性 - DataStore 实例
 *
 * 使用委托属性创建单例 DataStore
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "filter_camera_settings"                                       // DataStore 文件名
)

/**
 * 设置仓库实现类
 *
 * @param context 应用上下文
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ISettingsRepository {

    companion object {
        private const val TAG = "SettingsRepositoryImpl"                  // 日志标签
    }

    /** DataStore 实例 */
    private val dataStore = context.dataStore

    // ==================== 照片设置 ====================

    /**
     * 获取照片质量
     */
    override fun getPhotoQuality(): Flow<PhotoQuality> = dataStore.data
        .catch { exception ->                                             // 处理读取异常
            Log.e(TAG, "getPhotoQuality: 读取失败", exception)
            if (exception is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val name = preferences[SettingsKeys.PHOTO_QUALITY] ?: SettingsDefaults.PHOTO_QUALITY
            try {
                PhotoQuality.valueOf(name)                                // 解析枚举
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "getPhotoQuality: 无效值 $name，使用默认值")
                PhotoQuality.HIGH
            }
        }

    /**
     * 设置照片质量
     */
    override suspend fun setPhotoQuality(quality: PhotoQuality) {
        Log.d(TAG, "setPhotoQuality: $quality")
        dataStore.edit { preferences ->
            preferences[SettingsKeys.PHOTO_QUALITY] = quality.name        // 存储枚举名称
        }
    }

    // ==================== 视频设置 ====================

    /**
     * 获取视频质量
     */
    override fun getVideoQuality(): Flow<VideoQuality> = dataStore.data
        .catch { exception ->
            Log.e(TAG, "getVideoQuality: 读取失败", exception)
            if (exception is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val name = preferences[SettingsKeys.VIDEO_QUALITY] ?: SettingsDefaults.VIDEO_QUALITY
            try {
                VideoQuality.valueOf(name)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "getVideoQuality: 无效值 $name，使用默认值")
                VideoQuality.QUALITY_1080P
            }
        }

    /**
     * 设置视频质量
     */
    override suspend fun setVideoQuality(quality: VideoQuality) {
        Log.d(TAG, "setVideoQuality: $quality")
        dataStore.edit { preferences ->
            preferences[SettingsKeys.VIDEO_QUALITY] = quality.name
        }
    }

    // ==================== 网格设置 ====================

    /**
     * 获取网格类型
     */
    override fun getGridType(): Flow<GridType> = dataStore.data
        .catch { exception ->
            Log.e(TAG, "getGridType: 读取失败", exception)
            if (exception is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val name = preferences[SettingsKeys.GRID_TYPE] ?: SettingsDefaults.GRID_TYPE
            try {
                GridType.valueOf(name)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "getGridType: 无效值 $name，使用默认值")
                GridType.RULE_OF_THIRDS
            }
        }

    /**
     * 设置网格类型
     */
    override suspend fun setGridType(type: GridType) {
        Log.d(TAG, "setGridType: $type")
        dataStore.edit { preferences ->
            preferences[SettingsKeys.GRID_TYPE] = type.name
        }
    }

    // ==================== 位置信息设置 ====================

    /**
     * 获取位置信息开关状态
     */
    override fun isLocationEnabled(): Flow<Boolean> = dataStore.data
        .catch { exception ->
            Log.e(TAG, "isLocationEnabled: 读取失败", exception)
            if (exception is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[SettingsKeys.LOCATION_ENABLED] ?: SettingsDefaults.LOCATION_ENABLED
        }

    /**
     * 设置位置信息开关
     */
    override suspend fun setLocationEnabled(enabled: Boolean) {
        Log.d(TAG, "setLocationEnabled: $enabled")
        dataStore.edit { preferences ->
            preferences[SettingsKeys.LOCATION_ENABLED] = enabled
        }
    }

    // ==================== 水印设置 ====================

    /**
     * 获取水印开关状态
     */
    override fun isWatermarkEnabled(): Flow<Boolean> = dataStore.data
        .catch { exception ->
            Log.e(TAG, "isWatermarkEnabled: 读取失败", exception)
            if (exception is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[SettingsKeys.WATERMARK_ENABLED] ?: SettingsDefaults.WATERMARK_ENABLED
        }

    /**
     * 设置水印开关
     */
    override suspend fun setWatermarkEnabled(enabled: Boolean) {
        Log.d(TAG, "setWatermarkEnabled: $enabled")
        dataStore.edit { preferences ->
            preferences[SettingsKeys.WATERMARK_ENABLED] = enabled
        }
    }

    /**
     * 获取自定义水印文字
     */
    override fun getWatermarkText(): Flow<String> = dataStore.data
        .catch { exception ->
            Log.e(TAG, "getWatermarkText: 读取失败", exception)
            if (exception is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[SettingsKeys.WATERMARK_TEXT] ?: SettingsDefaults.WATERMARK_TEXT
        }

    /**
     * 设置自定义水印文字
     */
    override suspend fun setWatermarkText(text: String) {
        Log.d(TAG, "setWatermarkText: $text")
        dataStore.edit { preferences ->
            preferences[SettingsKeys.WATERMARK_TEXT] = text
        }
    }

    // ==================== 保存位置设置 ====================

    /**
     * 获取保存位置
     */
    override fun getSaveLocation(): Flow<SaveLocation> = dataStore.data
        .catch { exception ->
            Log.e(TAG, "getSaveLocation: 读取失败", exception)
            if (exception is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val name = preferences[SettingsKeys.SAVE_LOCATION] ?: SettingsDefaults.SAVE_LOCATION
            try {
                SaveLocation.valueOf(name)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "getSaveLocation: 无效值 $name，使用默认值")
                SaveLocation.DCIM
            }
        }

    /**
     * 设置保存位置
     */
    override suspend fun setSaveLocation(location: SaveLocation) {
        Log.d(TAG, "setSaveLocation: $location")
        dataStore.edit { preferences ->
            preferences[SettingsKeys.SAVE_LOCATION] = location.name
        }
    }

    /**
     * 获取自定义保存路径
     */
    override fun getCustomSavePath(): Flow<String> = dataStore.data
        .catch { exception ->
            Log.e(TAG, "getCustomSavePath: 读取失败", exception)
            if (exception is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[SettingsKeys.CUSTOM_SAVE_PATH] ?: SettingsDefaults.CUSTOM_SAVE_PATH
        }

    /**
     * 设置自定义保存路径
     */
    override suspend fun setCustomSavePath(path: String) {
        Log.d(TAG, "setCustomSavePath: $path")
        dataStore.edit { preferences ->
            preferences[SettingsKeys.CUSTOM_SAVE_PATH] = path
        }
    }

    // ==================== 滤镜设置 ====================

    /**
     * 获取默认滤镜
     */
    override fun getDefaultFilter(): Flow<FilterType> = dataStore.data
        .catch { exception ->
            Log.e(TAG, "getDefaultFilter: 读取失败", exception)
            if (exception is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val name = preferences[SettingsKeys.DEFAULT_FILTER] ?: SettingsDefaults.DEFAULT_FILTER
            try {
                FilterType.valueOf(name)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "getDefaultFilter: 无效值 $name，使用默认值")
                FilterType.NONE
            }
        }

    /**
     * 设置默认滤镜
     */
    override suspend fun setDefaultFilter(filterType: FilterType) {
        Log.d(TAG, "setDefaultFilter: $filterType")
        dataStore.edit { preferences ->
            preferences[SettingsKeys.DEFAULT_FILTER] = filterType.name
        }
    }

    // ==================== 声音设置 ====================

    /**
     * 获取快门声音开关状态
     */
    override fun isShutterSoundEnabled(): Flow<Boolean> = dataStore.data
        .catch { exception ->
            Log.e(TAG, "isShutterSoundEnabled: 读取失败", exception)
            if (exception is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[SettingsKeys.SHUTTER_SOUND_ENABLED] ?: SettingsDefaults.SHUTTER_SOUND_ENABLED
        }

    /**
     * 设置快门声音开关
     */
    override suspend fun setShutterSoundEnabled(enabled: Boolean) {
        Log.d(TAG, "setShutterSoundEnabled: $enabled")
        dataStore.edit { preferences ->
            preferences[SettingsKeys.SHUTTER_SOUND_ENABLED] = enabled
        }
    }

    // ==================== HDR 设置 ====================

    /**
     * 获取 HDR 自动模式开关状态
     */
    override fun isHdrAutoEnabled(): Flow<Boolean> = dataStore.data
        .catch { exception ->
            Log.e(TAG, "isHdrAutoEnabled: 读取失败", exception)
            if (exception is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[SettingsKeys.HDR_AUTO_ENABLED] ?: SettingsDefaults.HDR_AUTO_ENABLED
        }

    /**
     * 设置 HDR 自动模式开关
     */
    override suspend fun setHdrAutoEnabled(enabled: Boolean) {
        Log.d(TAG, "setHdrAutoEnabled: $enabled")
        dataStore.edit { preferences ->
            preferences[SettingsKeys.HDR_AUTO_ENABLED] = enabled
        }
    }

    // ==================== 美颜设置 ====================

    /**
     * 获取美颜默认强度
     */
    override fun getDefaultBeautyIntensity(): Flow<Float> = dataStore.data
        .catch { exception ->
            Log.e(TAG, "getDefaultBeautyIntensity: 读取失败", exception)
            if (exception is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[SettingsKeys.DEFAULT_BEAUTY_INTENSITY] ?: SettingsDefaults.DEFAULT_BEAUTY_INTENSITY
        }

    /**
     * 设置美颜默认强度
     */
    override suspend fun setDefaultBeautyIntensity(intensity: Float) {
        val clampedIntensity = intensity.coerceIn(0f, 1f)                 // 限制范围
        Log.d(TAG, "setDefaultBeautyIntensity: $clampedIntensity")
        dataStore.edit { preferences ->
            preferences[SettingsKeys.DEFAULT_BEAUTY_INTENSITY] = clampedIntensity
        }
    }

    // ==================== 主题设置 ====================

    /**
     * 获取主题模式
     */
    override fun getThemeMode(): Flow<ThemeMode> = dataStore.data
        .catch { exception ->
            Log.e(TAG, "getThemeMode: 读取失败", exception)
            if (exception is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val name = preferences[SettingsKeys.THEME_MODE] ?: SettingsDefaults.THEME_MODE
            try {
                ThemeMode.valueOf(name)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "getThemeMode: 无效值 $name，使用默认值")
                ThemeMode.SYSTEM
            }
        }

    /**
     * 设置主题模式
     */
    override suspend fun setThemeMode(mode: ThemeMode) {
        Log.d(TAG, "setThemeMode: $mode")
        dataStore.edit { preferences ->
            preferences[SettingsKeys.THEME_MODE] = mode.name
        }
    }

    // ==================== 其他设置 ====================

    /**
     * 获取镜像预览开关状态
     */
    override fun isMirrorPreviewEnabled(): Flow<Boolean> = dataStore.data
        .catch { exception ->
            Log.e(TAG, "isMirrorPreviewEnabled: 读取失败", exception)
            if (exception is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[SettingsKeys.MIRROR_PREVIEW_ENABLED] ?: SettingsDefaults.MIRROR_PREVIEW_ENABLED
        }

    /**
     * 设置镜像预览开关
     */
    override suspend fun setMirrorPreviewEnabled(enabled: Boolean) {
        Log.d(TAG, "setMirrorPreviewEnabled: $enabled")
        dataStore.edit { preferences ->
            preferences[SettingsKeys.MIRROR_PREVIEW_ENABLED] = enabled
        }
    }

    /**
     * 获取自动保存开关状态
     */
    override fun isAutoSaveEnabled(): Flow<Boolean> = dataStore.data
        .catch { exception ->
            Log.e(TAG, "isAutoSaveEnabled: 读取失败", exception)
            if (exception is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[SettingsKeys.AUTO_SAVE_ENABLED] ?: SettingsDefaults.AUTO_SAVE_ENABLED
        }

    /**
     * 设置自动保存开关
     */
    override suspend fun setAutoSaveEnabled(enabled: Boolean) {
        Log.d(TAG, "setAutoSaveEnabled: $enabled")
        dataStore.edit { preferences ->
            preferences[SettingsKeys.AUTO_SAVE_ENABLED] = enabled
        }
    }

    // ==================== 重置设置 ====================

    /**
     * 重置所有设置为默认值
     */
    override suspend fun resetAllSettings() {
        Log.d(TAG, "resetAllSettings: 重置所有设置")
        dataStore.edit { preferences ->
            preferences.clear()                                           // 清空所有设置
        }
    }
}
