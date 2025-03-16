/**
 * FileUtils.kt - 文件操作工具类
 *
 * 提供统一的临时文件创建、查询和管理功能
 * 支持照片和视频文件的命名规范
 *
 * 功能：
 * - 创建带时间戳的临时照片文件
 * - 创建带时间戳的临时视频文件
 * - 查询最新的临时文件
 * - 清理过期临时文件
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.data.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 文件操作工具类
 *
 * 单例对象，提供线程安全的文件操作方法
 */
object FileUtils {

    private const val TAG = "FileUtils"                                      // 日志标签

    // 文件名前缀常量
    private const val PHOTO_PREFIX = "IMG_"                                  // 照片文件前缀
    private const val VIDEO_PREFIX = "VID_"                                  // 视频文件前缀

    // 文件扩展名常量
    private const val PHOTO_EXTENSION = ".jpg"                               // 照片文件扩展名
    private const val VIDEO_EXTENSION = ".mp4"                               // 视频文件扩展名

    // 日期格式
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    /**
     * 文件类型枚举
     */
    enum class FileType(
        val prefix: String,                                                  // 文件名前缀
        val extension: String                                                // 文件扩展名
    ) {
        PHOTO(PHOTO_PREFIX, PHOTO_EXTENSION),                               // 照片类型
        VIDEO(VIDEO_PREFIX, VIDEO_EXTENSION)                                 // 视频类型
    }

    /**
     * 创建临时文件
     *
     * 在缓存目录创建带时间戳的临时文件
     *
     * @param context 应用上下文
     * @param fileType 文件类型（照片/视频）
     * @return 创建的临时文件
     */
    fun createTempFile(context: Context, fileType: FileType): File {
        val timestamp = dateFormat.format(Date())
        val fileName = "${fileType.prefix}${timestamp}${fileType.extension}"
        val file = File(context.cacheDir, fileName)
        Log.d(TAG, "createTempFile: 创建临时文件 path=${file.absolutePath}")
        return file
    }

    /**
     * 创建临时照片文件
     *
     * @param context 应用上下文
     * @return 照片临时文件
     */
    fun createTempPhotoFile(context: Context): File {
        return createTempFile(context, FileType.PHOTO)
    }

    /**
     * 创建临时视频文件
     *
     * @param context 应用上下文
     * @return 视频临时文件
     */
    fun createTempVideoFile(context: Context): File {
        return createTempFile(context, FileType.VIDEO)
    }

    /**
     * 获取最新的临时文件
     *
     * 根据文件类型查找缓存目录中最新的匹配文件
     *
     * @param context 应用上下文
     * @param fileType 文件类型
     * @return 最新的临时文件，不存在则返回null
     */
    fun getLatestTempFile(context: Context, fileType: FileType): File? {
        val latestFile = context.cacheDir.listFiles { file ->
            file.name.startsWith(fileType.prefix) &&
            file.name.endsWith(fileType.extension)
        }?.maxByOrNull { it.lastModified() }

        Log.d(TAG, "getLatestTempFile: type=$fileType, file=${latestFile?.name}")
        return latestFile
    }

    /**
     * 获取最新的临时视频文件
     *
     * @param context 应用上下文
     * @return 最新的视频临时文件
     */
    fun getLatestTempVideoFile(context: Context): File? {
        return getLatestTempFile(context, FileType.VIDEO)
    }

    /**
     * 清理过期临时文件
     *
     * 删除超过指定时间的临时文件
     *
     * @param context 应用上下文
     * @param maxAgeMillis 最大保留时间（毫秒），默认24小时
     * @return 删除的文件数量
     */
    fun cleanupTempFiles(context: Context, maxAgeMillis: Long = 24 * 60 * 60 * 1000): Int {
        val now = System.currentTimeMillis()
        var deletedCount = 0

        context.cacheDir.listFiles { file ->
            (file.name.startsWith(PHOTO_PREFIX) || file.name.startsWith(VIDEO_PREFIX)) &&
            (now - file.lastModified() > maxAgeMillis)
        }?.forEach { file ->
            if (file.delete()) {
                deletedCount++
                Log.d(TAG, "cleanupTempFiles: 删除过期文件 ${file.name}")
            }
        }

        Log.d(TAG, "cleanupTempFiles: 共删除 $deletedCount 个过期文件")
        return deletedCount
    }

    /**
     * 获取缓存目录大小
     *
     * 计算临时文件占用的空间
     *
     * @param context 应用上下文
     * @return 缓存大小（字节）
     */
    fun getTempFilesSize(context: Context): Long {
        return context.cacheDir.listFiles { file ->
            file.name.startsWith(PHOTO_PREFIX) || file.name.startsWith(VIDEO_PREFIX)
        }?.sumOf { it.length() } ?: 0L
    }
}
