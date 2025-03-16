/**
 * SettingsKeys.kt - DataStore 键定义
 *
 * 定义所有设置项在 DataStore 中的存储键
 * 统一管理键名，避免硬编码和冲突
 *
 * 功能：
 * - 照片设置键（质量）
 * - 视频设置键（质量）
 * - 网格设置键
 * - 位置信息设置键
 * - 水印设置键
 * - 保存位置设置键
 * - 滤镜设置键
 * - 声音设置键
 * - HDR 设置键
 * - 美颜设置键
 * - 其他设置键
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.data.local

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

/**
 * DataStore 设置键定义
 *
 * 使用 object 单例确保键的唯一性
 */
object SettingsKeys {

    // ==================== 照片设置 ====================

    /** 照片质量键 - 存储枚举名称 */
    val PHOTO_QUALITY = stringPreferencesKey("photo_quality")

    // ==================== 视频设置 ====================

    /** 视频质量键 - 存储枚举名称 */
    val VIDEO_QUALITY = stringPreferencesKey("video_quality")

    // ==================== 网格设置 ====================

    /** 网格类型键 - 存储枚举名称 */
    val GRID_TYPE = stringPreferencesKey("grid_type")

    // ==================== 位置信息设置 ====================

    /** 位置信息开关键 */
    val LOCATION_ENABLED = booleanPreferencesKey("location_enabled")

    // ==================== 水印设置 ====================

    /** 水印开关键 */
    val WATERMARK_ENABLED = booleanPreferencesKey("watermark_enabled")

    /** 自定义水印文字键 */
    val WATERMARK_TEXT = stringPreferencesKey("watermark_text")

    // ==================== 保存位置设置 ====================

    /** 保存位置键 - 存储枚举名称 */
    val SAVE_LOCATION = stringPreferencesKey("save_location")

    /** 自定义保存路径键 */
    val CUSTOM_SAVE_PATH = stringPreferencesKey("custom_save_path")

    // ==================== 滤镜设置 ====================

    /** 默认滤镜键 - 存储枚举名称 */
    val DEFAULT_FILTER = stringPreferencesKey("default_filter")

    // ==================== 声音设置 ====================

    /** 快门声音开关键 */
    val SHUTTER_SOUND_ENABLED = booleanPreferencesKey("shutter_sound_enabled")

    // ==================== HDR 设置 ====================

    /** HDR 自动模式开关键 */
    val HDR_AUTO_ENABLED = booleanPreferencesKey("hdr_auto_enabled")

    // ==================== 美颜设置 ====================

    /** 美颜默认强度键（0.0-1.0） */
    val DEFAULT_BEAUTY_INTENSITY = floatPreferencesKey("default_beauty_intensity")

    // ==================== 主题设置 ====================

    /** 主题模式键 - 存储枚举名称（SYSTEM/LIGHT/DARK） */
    val THEME_MODE = stringPreferencesKey("theme_mode")

    // ==================== 其他设置 ====================

    /** 镜像预览开关键（前置摄像头） */
    val MIRROR_PREVIEW_ENABLED = booleanPreferencesKey("mirror_preview_enabled")

    /** 自动保存开关键 */
    val AUTO_SAVE_ENABLED = booleanPreferencesKey("auto_save_enabled")

    // ==================== 收藏夹设置 ====================

    /** 收藏媒体URI集合键 - 存储收藏的媒体Uri字符串集合 */
    val FAVORITE_MEDIA_URIS = stringSetPreferencesKey("favorite_media_uris")
}

/**
 * 设置默认值定义
 *
 * 统一管理所有设置项的默认值
 */
object SettingsDefaults {

    // ==================== 照片设置默认值 ====================

    /** 照片质量默认值 - 高质量 */
    const val PHOTO_QUALITY = "HIGH"

    // ==================== 视频设置默认值 ====================

    /** 视频质量默认值 - 1080P */
    const val VIDEO_QUALITY = "QUALITY_1080P"

    // ==================== 网格设置默认值 ====================

    /** 网格类型默认值 - 九宫格 */
    const val GRID_TYPE = "RULE_OF_THIRDS"

    // ==================== 位置信息设置默认值 ====================

    /** 位置信息默认关闭 */
    const val LOCATION_ENABLED = false

    // ==================== 水印设置默认值 ====================

    /** 水印默认关闭 */
    const val WATERMARK_ENABLED = false

    /** 自定义水印默认文字 */
    const val WATERMARK_TEXT = "FilterCamera"

    // ==================== 保存位置设置默认值 ====================

    /** 保存位置默认值 - 相册 */
    const val SAVE_LOCATION = "DCIM"

    /** 自定义保存路径默认为空 */
    const val CUSTOM_SAVE_PATH = ""

    // ==================== 滤镜设置默认值 ====================

    /** 默认滤镜 - 无滤镜 */
    const val DEFAULT_FILTER = "NONE"

    // ==================== 声音设置默认值 ====================

    /** 快门声音默认开启 */
    const val SHUTTER_SOUND_ENABLED = true

    // ==================== HDR 设置默认值 ====================

    /** HDR 自动模式默认关闭 */
    const val HDR_AUTO_ENABLED = false

    // ==================== 美颜设置默认值 ====================

    /** 美颜默认强度 - 50% */
    const val DEFAULT_BEAUTY_INTENSITY = 0.5f

    // ==================== 主题设置默认值 ====================

    /** 主题模式默认值 - 跟随系统 */
    const val THEME_MODE = "SYSTEM"

    // ==================== 其他设置默认值 ====================

    /** 镜像预览默认开启（前置摄像头） */
    const val MIRROR_PREVIEW_ENABLED = true

    /** 自动保存默认开启 */
    const val AUTO_SAVE_ENABLED = true
}
