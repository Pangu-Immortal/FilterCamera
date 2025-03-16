/**
 * IMediaRepository.kt - 媒体仓库接口
 *
 * 定义媒体文件存储操作的抽象接口
 * 使用Scoped Storage和MediaStore API
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.domain.repository

import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.flow.Flow

/**
 * 媒体类型枚举
 */
enum class MediaType {
    ALL,                                                                  // 所有类型
    PHOTO,                                                                // 仅照片
    VIDEO                                                                 // 仅视频
}

/**
 * 媒体文件信息
 *
 * @param uri 文件Uri
 * @param path 文件路径
 * @param name 文件名
 * @param mimeType MIME类型
 * @param size 文件大小（字节）
 * @param dateAdded 添加时间（毫秒时间戳）
 * @param width 宽度（像素）
 * @param height 高度（像素）
 * @param duration 时长（毫秒，仅视频）
 * @param isVideo 是否为视频
 */
data class MediaFile(
    val uri: Uri,
    val path: String,
    val name: String,
    val mimeType: String,
    val size: Long,
    val dateAdded: Long,
    val width: Int = 0,
    val height: Int = 0,
    val duration: Long = 0,
    val isVideo: Boolean = false
)

/**
 * 媒体仓库接口
 * 负责媒体文件的保存和查询
 */
interface IMediaRepository {

    /**
     * 保存照片到相册
     * @param bitmap 图像数据
     * @param fileName 文件名（不含扩展名）
     * @return 保存结果，包含文件Uri
     */
    suspend fun savePhoto(bitmap: Bitmap, fileName: String): Result<Uri>

    /**
     * 保存照片到相册（从字节数组）
     * @param imageData JPEG图像字节数据
     * @param fileName 文件名（不含扩展名）
     * @return 保存结果，包含文件Uri
     */
    suspend fun savePhoto(imageData: ByteArray, fileName: String): Result<Uri>

    /**
     * 保存视频到相册
     * @param videoPath 视频临时文件路径
     * @param fileName 文件名（不含扩展名）
     * @return 保存结果，包含文件Uri
     */
    suspend fun saveVideo(videoPath: String, fileName: String): Result<Uri>

    /**
     * 获取最近的媒体文件
     * @param limit 数量限制
     * @return 媒体文件列表
     */
    suspend fun getRecentMedia(limit: Int = 20): List<MediaFile>

    /**
     * 删除媒体文件
     * @param uri 文件Uri
     * @return 删除结果
     */
    suspend fun deleteMedia(uri: Uri): Result<Unit>

    /**
     * 获取最后保存的媒体文件Uri
     * @return 最后保存的Uri Flow
     */
    fun getLastSavedMediaUri(): Flow<Uri?>

    /**
     * 生成照片文件名
     * @return 基于时间戳的文件名
     */
    fun generatePhotoFileName(): String

    /**
     * 生成视频文件名
     * @return 基于时间戳的文件名
     */
    fun generateVideoFileName(): String

    // ==================== 图片编辑功能扩展 ====================

    /**
     * 从Uri加载Bitmap图像
     *
     * 支持content://和file://类型的Uri
     * 自动处理图片方向（EXIF信息）
     *
     * @param uri 图片Uri
     * @return 加载的Bitmap，失败返回null
     */
    suspend fun loadBitmap(uri: Uri): Bitmap?

    // ==================== 相册功能扩展 ====================

    /**
     * 分页获取媒体文件
     *
     * @param type 媒体类型筛选
     * @param offset 偏移量（跳过的数量）
     * @param limit 每页数量
     * @return 媒体文件列表
     */
    suspend fun getMediaPaged(
        type: MediaType = MediaType.ALL,
        offset: Int = 0,
        limit: Int = 50
    ): List<MediaFile>

    /**
     * 获取媒体文件总数
     *
     * @param type 媒体类型筛选
     * @return 媒体文件总数
     */
    suspend fun getMediaCount(type: MediaType = MediaType.ALL): Int

    /**
     * 加载媒体缩略图
     *
     * @param uri 媒体文件Uri
     * @param width 目标宽度
     * @param height 目标高度
     * @return 缩略图Bitmap，失败返回null
     */
    suspend fun loadThumbnail(uri: Uri, width: Int, height: Int): Bitmap?

    /**
     * 获取相册媒体文件响应式流
     *
     * 当相册内容变化时自动更新
     *
     * @param type 媒体类型筛选
     * @return 媒体文件列表Flow
     */
    fun observeMedia(type: MediaType = MediaType.ALL): Flow<List<MediaFile>>
}
