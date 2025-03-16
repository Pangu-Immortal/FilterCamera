/**
 * ISettingsRepository.kt - 设置仓库接口
 *
 * 定义应用设置的存取操作接口
 * 支持照片质量、视频质量、网格、位置、水印等设置项
 *
 * 功能：
 * - 照片质量设置（高/中/低）
 * - 视频质量设置（4K/1080P/720P）
 * - 网格显示开关
 * - 位置信息开关
 * - 水印设置
 * - 保存位置设置
 * - 默认滤镜设置
 * - 快门声音设置
 * - HDR 自动模式设置
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.domain.repository

import com.qihao.filtercamera.domain.model.FilterType
import kotlinx.coroutines.flow.Flow

/**
 * 照片质量枚举
 */
enum class PhotoQuality(
    val displayName: String,                                              // 显示名称
    val compressionQuality: Int                                           // JPEG压缩质量（0-100）
) {
    HIGH("高质量", 95),                                                   // 高质量
    MEDIUM("标准", 85),                                                   // 标准质量
    LOW("省空间", 70)                                                     // 低质量省空间
}

/**
 * 视频质量枚举
 */
enum class VideoQuality(
    val displayName: String,                                              // 显示名称
    val resolution: String                                                // 分辨率描述
) {
    QUALITY_4K("4K超清", "3840x2160"),                                    // 4K分辨率
    QUALITY_1080P("1080P高清", "1920x1080"),                              // 1080P分辨率
    QUALITY_720P("720P标清", "1280x720")                                  // 720P分辨率
}

/**
 * 网格类型枚举
 */
enum class GridType(
    val displayName: String                                               // 显示名称
) {
    NONE("关闭"),                                                         // 不显示网格
    RULE_OF_THIRDS("九宫格"),                                             // 三分法网格
    GOLDEN_RATIO("黄金分割"),                                             // 黄金分割网格
    SQUARE("方形")                                                        // 方形网格
}

/**
 * 主题模式枚举
 */
enum class ThemeMode(
    val displayName: String                                               // 显示名称
) {
    SYSTEM("跟随系统"),                                                   // 跟随系统设置
    LIGHT("浅色模式"),                                                    // 始终浅色
    DARK("深色模式")                                                      // 始终深色
}

/**
 * 保存位置枚举
 */
enum class SaveLocation(
    val displayName: String,                                              // 显示名称
    val path: String                                                      // 路径标识
) {
    DCIM("相册", "DCIM/FilterCamera"),                                    // 系统相册
    PICTURES("图片", "Pictures/FilterCamera"),                            // 图片文件夹
    CUSTOM("自定义", "")                                                  // 自定义路径
}

/**
 * 设置仓库接口
 *
 * 使用 Flow 实现响应式数据流
 */
interface ISettingsRepository {

    // ==================== 照片设置 ====================

    /**
     * 获取照片质量
     * @return 照片质量 Flow
     */
    fun getPhotoQuality(): Flow<PhotoQuality>

    /**
     * 设置照片质量
     * @param quality 照片质量
     */
    suspend fun setPhotoQuality(quality: PhotoQuality)

    // ==================== 视频设置 ====================

    /**
     * 获取视频质量
     * @return 视频质量 Flow
     */
    fun getVideoQuality(): Flow<VideoQuality>

    /**
     * 设置视频质量
     * @param quality 视频质量
     */
    suspend fun setVideoQuality(quality: VideoQuality)

    // ==================== 网格设置 ====================

    /**
     * 获取网格类型
     * @return 网格类型 Flow
     */
    fun getGridType(): Flow<GridType>

    /**
     * 设置网格类型
     * @param type 网格类型
     */
    suspend fun setGridType(type: GridType)

    // ==================== 位置信息设置 ====================

    /**
     * 获取位置信息开关状态
     * @return 是否启用位置信息 Flow
     */
    fun isLocationEnabled(): Flow<Boolean>

    /**
     * 设置位置信息开关
     * @param enabled 是否启用
     */
    suspend fun setLocationEnabled(enabled: Boolean)

    // ==================== 水印设置 ====================

    /**
     * 获取水印开关状态
     * @return 是否启用水印 Flow
     */
    fun isWatermarkEnabled(): Flow<Boolean>

    /**
     * 设置水印开关
     * @param enabled 是否启用
     */
    suspend fun setWatermarkEnabled(enabled: Boolean)

    /**
     * 获取自定义水印文字
     * @return 自定义水印文字 Flow
     */
    fun getWatermarkText(): Flow<String>

    /**
     * 设置自定义水印文字
     * @param text 水印文字
     */
    suspend fun setWatermarkText(text: String)

    // ==================== 保存位置设置 ====================

    /**
     * 获取保存位置
     * @return 保存位置 Flow
     */
    fun getSaveLocation(): Flow<SaveLocation>

    /**
     * 设置保存位置
     * @param location 保存位置
     */
    suspend fun setSaveLocation(location: SaveLocation)

    /**
     * 获取自定义保存路径
     * @return 自定义路径 Flow
     */
    fun getCustomSavePath(): Flow<String>

    /**
     * 设置自定义保存路径
     * @param path 自定义路径
     */
    suspend fun setCustomSavePath(path: String)

    // ==================== 滤镜设置 ====================

    /**
     * 获取默认滤镜
     * @return 默认滤镜类型 Flow
     */
    fun getDefaultFilter(): Flow<FilterType>

    /**
     * 设置默认滤镜
     * @param filterType 滤镜类型
     */
    suspend fun setDefaultFilter(filterType: FilterType)

    // ==================== 声音设置 ====================

    /**
     * 获取快门声音开关状态
     * @return 是否启用快门声音 Flow
     */
    fun isShutterSoundEnabled(): Flow<Boolean>

    /**
     * 设置快门声音开关
     * @param enabled 是否启用
     */
    suspend fun setShutterSoundEnabled(enabled: Boolean)

    // ==================== HDR 设置 ====================

    /**
     * 获取 HDR 自动模式开关状态
     * @return 是否启用 HDR 自动模式 Flow
     */
    fun isHdrAutoEnabled(): Flow<Boolean>

    /**
     * 设置 HDR 自动模式开关
     * @param enabled 是否启用
     */
    suspend fun setHdrAutoEnabled(enabled: Boolean)

    // ==================== 美颜设置 ====================

    /**
     * 获取美颜默认强度
     * @return 美颜强度（0.0-1.0）Flow
     */
    fun getDefaultBeautyIntensity(): Flow<Float>

    /**
     * 设置美颜默认强度
     * @param intensity 美颜强度（0.0-1.0）
     */
    suspend fun setDefaultBeautyIntensity(intensity: Float)

    // ==================== 主题设置 ====================

    /**
     * 获取主题模式
     * @return 主题模式 Flow
     */
    fun getThemeMode(): Flow<ThemeMode>

    /**
     * 设置主题模式
     * @param mode 主题模式
     */
    suspend fun setThemeMode(mode: ThemeMode)

    // ==================== 其他设置 ====================

    /**
     * 获取镜像预览开关状态（前置摄像头）
     * @return 是否启用镜像预览 Flow
     */
    fun isMirrorPreviewEnabled(): Flow<Boolean>

    /**
     * 设置镜像预览开关
     * @param enabled 是否启用
     */
    suspend fun setMirrorPreviewEnabled(enabled: Boolean)

    /**
     * 获取自动保存开关状态
     * @return 是否启用自动保存 Flow
     */
    fun isAutoSaveEnabled(): Flow<Boolean>

    /**
     * 设置自动保存开关
     * @param enabled 是否启用
     */
    suspend fun setAutoSaveEnabled(enabled: Boolean)

    /**
     * 重置所有设置为默认值
     */
    suspend fun resetAllSettings()
}
