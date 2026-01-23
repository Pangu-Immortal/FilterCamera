/**
 * MediaRepositoryImpl.kt - 媒体仓库实现
 *
 * 使用MediaStore API实现媒体文件存储
 * 适配Android 10+ Scoped Storage
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.exifinterface.media.ExifInterface
import com.qihao.filtercamera.domain.repository.IMediaRepository
import com.qihao.filtercamera.domain.repository.MediaFile
import com.qihao.filtercamera.domain.repository.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 媒体仓库实现类
 *
 * @param context 应用上下文
 */
@Singleton
class MediaRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : IMediaRepository {

    companion object {
        private const val TAG = "MediaRepositoryImpl"     // 日志标签
        private const val PHOTO_PREFIX = "IMG_"          // 照片文件名前缀
        private const val VIDEO_PREFIX = "VID_"          // 视频文件名前缀
        private const val PHOTO_EXTENSION = ".jpg"       // 照片扩展名
        private const val VIDEO_EXTENSION = ".mp4"       // 视频扩展名
        private const val ALBUM_NAME = "FilterCamera"    // 相册名称
    }

    // 最后保存的媒体Uri状态流
    private val _lastSavedMediaUri = MutableStateFlow<Uri?>(null)

    /**
     * 保存照片到相册（从Bitmap）
     */
    override suspend fun savePhoto(bitmap: Bitmap, fileName: String): Result<Uri> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "savePhoto: 开始保存照片 fileName=$fileName")
                val contentValues = createImageContentValues(fileName)
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                ) ?: return@withContext Result.failure(Exception("创建媒体文件失败"))

                Log.d(TAG, "savePhoto: 创建Uri成功 uri=$uri")

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                    Log.d(TAG, "savePhoto: 图像压缩写入成功")
                } ?: return@withContext Result.failure(Exception("打开输出流失败"))

                // 更新IS_PENDING状态（Android 10+）
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, contentValues, null, null)
                }

                _lastSavedMediaUri.value = uri
                Log.d(TAG, "savePhoto: 照片保存成功 uri=$uri")
                Result.success(uri)
            } catch (e: Exception) {
                Log.e(TAG, "savePhoto: 保存照片失败", e)
                Result.failure(e)
            }
        }

    /**
     * 保存照片到相册（从字节数组）
     */
    override suspend fun savePhoto(imageData: ByteArray, fileName: String): Result<Uri> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "savePhoto: 开始保存照片(字节数组) fileName=$fileName, size=${imageData.size}")
                val contentValues = createImageContentValues(fileName)
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                ) ?: return@withContext Result.failure(Exception("创建媒体文件失败"))

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(imageData)
                    Log.d(TAG, "savePhoto: 字节数据写入成功")
                } ?: return@withContext Result.failure(Exception("打开输出流失败"))

                // 更新IS_PENDING状态（Android 10+）
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, contentValues, null, null)
                }

                _lastSavedMediaUri.value = uri
                Log.d(TAG, "savePhoto: 照片保存成功 uri=$uri")
                Result.success(uri)
            } catch (e: Exception) {
                Log.e(TAG, "savePhoto: 保存照片失败", e)
                Result.failure(e)
            }
        }

    /**
     * 保存视频到相册
     */
    override suspend fun saveVideo(videoPath: String, fileName: String): Result<Uri> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "saveVideo: 开始保存视频 videoPath=$videoPath, fileName=$fileName")
                val sourceFile = File(videoPath)
                if (!sourceFile.exists()) {
                    return@withContext Result.failure(Exception("视频源文件不存在: $videoPath"))
                }

                val contentValues = createVideoContentValues(fileName)
                val uri = context.contentResolver.insert(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                ) ?: return@withContext Result.failure(Exception("创建媒体文件失败"))

                Log.d(TAG, "saveVideo: 创建Uri成功 uri=$uri")

                // 复制视频文件
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    FileInputStream(sourceFile).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                    Log.d(TAG, "saveVideo: 视频复制成功")
                } ?: return@withContext Result.failure(Exception("打开输出流失败"))

                // 更新IS_PENDING状态（Android 10+）
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, contentValues, null, null)
                }

                // 删除临时文件
                sourceFile.delete()
                Log.d(TAG, "saveVideo: 临时文件已删除")

                _lastSavedMediaUri.value = uri
                Log.d(TAG, "saveVideo: 视频保存成功 uri=$uri")
                Result.success(uri)
            } catch (e: Exception) {
                Log.e(TAG, "saveVideo: 保存视频失败", e)
                Result.failure(e)
            }
        }

    /**
     * 获取最近的媒体文件
     */
    override suspend fun getRecentMedia(limit: Int): List<MediaFile> =
        withContext(Dispatchers.IO) {
            val mediaFiles = mutableListOf<MediaFile>()
            try {
                Log.d(TAG, "getRecentMedia: 查询最近媒体 limit=$limit")

                // 查询图片
                val imageProjection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATA,
                    MediaStore.Images.Media.MIME_TYPE,
                    MediaStore.Images.Media.SIZE,
                    MediaStore.Images.Media.DATE_ADDED
                )

                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    imageProjection,
                    "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?",
                    arrayOf("%$ALBUM_NAME%"),
                    "${MediaStore.Images.Media.DATE_ADDED} DESC"
                )?.use { cursor ->
                    while (cursor.moveToNext() && mediaFiles.size < limit) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                        val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                        mediaFiles.add(
                            MediaFile(
                                uri = uri,
                                path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)) ?: "",
                                name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)) ?: "",
                                mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)) ?: "image/jpeg",
                                size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)),
                                dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)) * 1000
                            )
                        )
                    }
                }

                Log.d(TAG, "getRecentMedia: 查询到 ${mediaFiles.size} 个媒体文件")
            } catch (e: Exception) {
                Log.e(TAG, "getRecentMedia: 查询失败", e)
            }
            mediaFiles
        }

    /**
     * 删除媒体文件
     */
    override suspend fun deleteMedia(uri: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "deleteMedia: 删除媒体 uri=$uri")
                val deleted = context.contentResolver.delete(uri, null, null)
                if (deleted > 0) {
                    Log.d(TAG, "deleteMedia: 删除成功")
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("删除失败"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "deleteMedia: 删除失败", e)
                Result.failure(e)
            }
        }

    /**
     * 获取最后保存的媒体文件Uri
     */
    override fun getLastSavedMediaUri(): Flow<Uri?> = _lastSavedMediaUri

    /**
     * 生成照片文件名
     */
    override fun generatePhotoFileName(): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        return "$PHOTO_PREFIX${dateFormat.format(Date())}"
    }

    /**
     * 生成视频文件名
     */
    override fun generateVideoFileName(): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        return "$VIDEO_PREFIX${dateFormat.format(Date())}"
    }

    // ==================== 图片编辑功能扩展实现 ====================

    /**
     * 从Uri加载Bitmap图像
     *
     * 支持content://和file://类型的Uri
     * 自动处理图片方向（EXIF信息）
     *
     * @param uri 图片Uri
     * @return 加载的Bitmap，失败返回null
     */
    override suspend fun loadBitmap(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "loadBitmap: 开始加载图片 uri=$uri")

            // 打开输入流并解码Bitmap
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                // 先获取图片尺寸
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)

                // 计算合适的采样率（限制最大尺寸为4096）
                val maxSize = 4096
                options.inSampleSize = calculateInSampleSize(options, maxSize, maxSize)
                options.inJustDecodeBounds = false

                // 重新打开流解码完整Bitmap
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream, null, options)
                    if (bitmap != null) {
                        // 处理图片方向（EXIF）
                        val orientedBitmap = handleOrientation(context, uri, bitmap)
                        Log.d(TAG, "loadBitmap: 图片加载成功 ${orientedBitmap.width}x${orientedBitmap.height}")
                        orientedBitmap
                    } else {
                        Log.e(TAG, "loadBitmap: 解码失败")
                        null
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadBitmap: 加载图片失败", e)
            null
        }
    }

    /**
     * 处理图片方向（根据EXIF信息旋转）
     */
    private fun handleOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return bitmap
            val exif = ExifInterface(inputStream)                                     // 使用 androidx ExifInterface
            inputStream.close()

            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            val matrix = android.graphics.Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                else -> return bitmap
            }

            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            Log.e(TAG, "handleOrientation: 处理方向失败", e)
            bitmap
        }
    }

    /**
     * 创建图片ContentValues
     */
    private fun createImageContentValues(fileName: String): ContentValues {
        return ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$fileName$PHOTO_EXTENSION")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM_NAME")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
    }

    /**
     * 创建视频ContentValues
     */
    private fun createVideoContentValues(fileName: String): ContentValues {
        return ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "$fileName$VIDEO_EXTENSION")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$ALBUM_NAME")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
    }

    // ==================== 相册功能扩展实现 ====================

    /**
     * 分页获取媒体文件
     *
     * @param type 媒体类型筛选
     * @param offset 偏移量（跳过的数量）
     * @param limit 每页数量
     * @return 媒体文件列表
     */
    override suspend fun getMediaPaged(
        type: MediaType,
        offset: Int,
        limit: Int
    ): List<MediaFile> = withContext(Dispatchers.IO) {
        val mediaFiles = mutableListOf<MediaFile>()
        try {
            Log.d(TAG, "getMediaPaged: 分页查询媒体 type=$type, offset=$offset, limit=$limit")

            // 根据类型查询图片
            if (type == MediaType.ALL || type == MediaType.PHOTO) {
                queryImages(mediaFiles, type == MediaType.PHOTO, offset, limit)
            }

            // 根据类型查询视频
            if (type == MediaType.ALL || type == MediaType.VIDEO) {
                val videoOffset = if (type == MediaType.ALL) {
                    maxOf(0, offset - getImageCount())                         // 计算视频偏移量
                } else offset
                val videoLimit = if (type == MediaType.ALL) {
                    limit - mediaFiles.size                                    // 计算剩余需要的视频数量
                } else limit
                if (videoLimit > 0) {
                    queryVideos(mediaFiles, videoOffset, videoLimit)
                }
            }

            // 按添加时间排序（降序）
            mediaFiles.sortByDescending { it.dateAdded }

            Log.d(TAG, "getMediaPaged: 查询到 ${mediaFiles.size} 个媒体文件")
        } catch (e: Exception) {
            Log.e(TAG, "getMediaPaged: 查询失败", e)
        }
        mediaFiles
    }

    /**
     * 查询图片文件
     *
     * @param result 结果列表
     * @param applyPaging 是否应用分页
     * @param offset 偏移量
     * @param limit 数量限制
     */
    private fun queryImages(
        result: MutableList<MediaFile>,
        applyPaging: Boolean,
        offset: Int,
        limit: Int
    ) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT
        )

        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("%$ALBUM_NAME%")
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            var skipped = 0                                                    // 已跳过数量
            var added = 0                                                      // 已添加数量
            val targetOffset = if (applyPaging) offset else 0
            val targetLimit = if (applyPaging) limit else Int.MAX_VALUE

            while (cursor.moveToNext()) {
                if (skipped < targetOffset) {                                  // 跳过offset数量
                    skipped++
                    continue
                }
                if (added >= targetLimit) break                                // 达到limit数量

                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                result.add(
                    MediaFile(
                        uri = uri,
                        path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)) ?: "",
                        name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)) ?: "",
                        mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)) ?: "image/jpeg",
                        size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)),
                        dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)) * 1000,
                        width = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)),
                        height = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)),
                        duration = 0,
                        isVideo = false
                    )
                )
                added++
            }
            Log.d(TAG, "queryImages: 查询到 $added 张图片")
        }
    }

    /**
     * 查询视频文件
     *
     * @param result 结果列表
     * @param offset 偏移量
     * @param limit 数量限制
     */
    private fun queryVideos(
        result: MutableList<MediaFile>,
        offset: Int,
        limit: Int
    ) {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DURATION
        )

        val selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("%$ALBUM_NAME%")
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            var skipped = 0                                                    // 已跳过数量
            var added = 0                                                      // 已添加数量

            while (cursor.moveToNext()) {
                if (skipped < offset) {                                        // 跳过offset数量
                    skipped++
                    continue
                }
                if (added >= limit) break                                      // 达到limit数量

                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                val uri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString())
                result.add(
                    MediaFile(
                        uri = uri,
                        path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)) ?: "",
                        name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)) ?: "",
                        mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)) ?: "video/mp4",
                        size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)),
                        dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)) * 1000,
                        width = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)),
                        height = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)),
                        duration = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)),
                        isVideo = true
                    )
                )
                added++
            }
            Log.d(TAG, "queryVideos: 查询到 $added 个视频")
        }
    }

    /**
     * 获取图片总数（内部方法）
     */
    private fun getImageCount(): Int {
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("%$ALBUM_NAME%")

        return context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            selection,
            selectionArgs,
            null
        )?.use { it.count } ?: 0
    }

    /**
     * 获取视频总数（内部方法）
     */
    private fun getVideoCount(): Int {
        val selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("%$ALBUM_NAME%")

        return context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Video.Media._ID),
            selection,
            selectionArgs,
            null
        )?.use { it.count } ?: 0
    }

    /**
     * 获取媒体文件总数
     *
     * @param type 媒体类型筛选
     * @return 媒体文件总数
     */
    override suspend fun getMediaCount(type: MediaType): Int = withContext(Dispatchers.IO) {
        try {
            val count = when (type) {
                MediaType.PHOTO -> getImageCount()
                MediaType.VIDEO -> getVideoCount()
                MediaType.ALL -> getImageCount() + getVideoCount()
            }
            Log.d(TAG, "getMediaCount: type=$type, count=$count")
            count
        } catch (e: Exception) {
            Log.e(TAG, "getMediaCount: 查询失败", e)
            0
        }
    }

    /**
     * 加载媒体缩略图
     *
     * @param uri 媒体文件Uri
     * @param width 目标宽度
     * @param height 目标高度
     * @return 缩略图Bitmap，失败返回null
     */
    override suspend fun loadThumbnail(uri: Uri, width: Int, height: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "loadThumbnail: 加载缩略图 uri=$uri, size=${width}x$height")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10+ 使用 loadThumbnail API
                    context.contentResolver.loadThumbnail(uri, Size(width, height), null)
                } else {
                    // Android 9及以下使用传统方式
                    loadThumbnailLegacy(uri, width, height)
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadThumbnail: 加载失败", e)
                null
            }
        }

    /**
     * 传统方式加载缩略图（Android 9及以下）
     */
    @Suppress("DEPRECATION")
    private fun loadThumbnailLegacy(uri: Uri, width: Int, height: Int): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                // 先获取图片尺寸
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)

                // 计算采样率
                options.inSampleSize = calculateInSampleSize(options, width, height)
                options.inJustDecodeBounds = false

                // 重新打开流解码
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadThumbnailLegacy: 加载失败", e)
            null
        }
    }

    /**
     * 计算缩略图采样率
     */
    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * 获取相册媒体文件响应式流
     *
     * 当相册内容变化时自动更新
     *
     * @param type 媒体类型筛选
     * @return 媒体文件列表Flow
     */
    override fun observeMedia(type: MediaType): Flow<List<MediaFile>> = callbackFlow {
        Log.d(TAG, "observeMedia: 开始监听媒体变化 type=$type")

        // 创建内容观察者
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                Log.d(TAG, "observeMedia: 媒体内容变化")
                // 重新查询并发送
                trySend(queryAllMedia(type))
            }
        }

        // 注册图片内容观察者
        if (type == MediaType.ALL || type == MediaType.PHOTO) {
            context.contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
        }

        // 注册视频内容观察者
        if (type == MediaType.ALL || type == MediaType.VIDEO) {
            context.contentResolver.registerContentObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
        }

        // 发送初始数据
        trySend(queryAllMedia(type))

        // 等待关闭时取消注册
        awaitClose {
            Log.d(TAG, "observeMedia: 停止监听媒体变化")
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    /**
     * 查询所有媒体（用于observeMedia）
     */
    private fun queryAllMedia(type: MediaType): List<MediaFile> {
        val mediaFiles = mutableListOf<MediaFile>()

        if (type == MediaType.ALL || type == MediaType.PHOTO) {
            queryImages(mediaFiles, false, 0, Int.MAX_VALUE)
        }

        if (type == MediaType.ALL || type == MediaType.VIDEO) {
            queryVideos(mediaFiles, 0, Int.MAX_VALUE)
        }

        // 按添加时间排序（降序）
        mediaFiles.sortByDescending { it.dateAdded }

        return mediaFiles
    }
}
