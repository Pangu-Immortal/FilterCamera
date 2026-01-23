/**
 * BeautyProcessor.kt - 美颜处理器
 *
 * 提供美颜功能的高级封装
 * 管理Native资源生命周期，提供线程安全的美颜处理
 *
 * 设计原则：
 * - 使用SafeMagicJni安全封装，避免JNI崩溃
 * - 优雅降级：处理失败时返回原图或使用Kotlin实现
 * - 大图片自动缩放，减少Native内存压力
 * - 详细的性能日志输出
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.data.processor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Log
import com.qihao.filtercamera.domain.model.BeautyLevel
import com.seu.magicfilter.beautify.JniError
import com.seu.magicfilter.beautify.SafeMagicJni
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * 美颜处理结果
 *
 * @param bitmap 处理后的Bitmap
 * @param processingTimeMs 处理耗时（毫秒）
 * @param success 处理是否成功
 * @param errorMessage 错误信息（仅失败时有值）
 */
data class BeautyResult(
    val bitmap: Bitmap,
    val processingTimeMs: Long,
    val success: Boolean = true,
    val errorMessage: String? = null
)

/**
 * 美颜处理器
 *
 * 封装Native美颜算法调用
 * 提供线程安全的图像处理能力
 * 使用SafeMagicJni确保JNI调用安全
 */
@Singleton
class BeautyProcessor @Inject constructor() {

    companion object {
        private const val TAG = "BeautyProcessor"         // 日志标签
        private const val WHITE_RATIO = 0.5f              // 美白强度比例
        private const val SMOOTH_RATIO = 0.8f             // 磨皮强度比例

        // Native处理的安全图片尺寸限制（防止内存溢出导致SIGSEGV）
        private const val MAX_NATIVE_DIMENSION = 1024     // 最大处理边长
        private const val MAX_NATIVE_PIXELS = 800000      // 最大像素数（约800K）

        // 是否启用Native美颜（设为false则使用纯Kotlin实现）
        private const val ENABLE_NATIVE_BEAUTY = false    // 禁用Native，防止SIGSEGV崩溃
    }

    // 处理互斥锁，确保线程安全
    private val processingMutex = Mutex()

    // 当前处理中的Bitmap句柄
    private var currentHandle: ByteBuffer? = null

    // 是否已初始化
    private var isInitialized = false

    // 处理统计
    private var totalProcessCount = 0L                    // 总处理次数
    private var successCount = 0L                         // 成功次数
    private var failureCount = 0L                         // 失败次数

    /**
     * 检查Native库是否可用
     *
     * @return true表示可用
     */
    fun isNativeAvailable(): Boolean = SafeMagicJni.isLibraryLoaded()

    /**
     * 处理Bitmap应用美颜效果
     *
     * @param sourceBitmap 源Bitmap（必须是ARGB_8888格式）
     * @param beautyLevel 美颜等级
     * @return 处理结果，失败时返回包含原图的结果
     */
    suspend fun processBeauty(
        sourceBitmap: Bitmap,
        beautyLevel: BeautyLevel
    ): BeautyResult = withContext(Dispatchers.Default) {
        processingMutex.withLock {
            val startTime = System.currentTimeMillis()
            totalProcessCount++
            Log.d(TAG, "processBeauty: 开始处理 #$totalProcessCount beautyLevel=$beautyLevel")

            // 确保Bitmap格式正确
            val workBitmap = ensureArgb8888(sourceBitmap)

            // 计算美颜参数
            val whiteLevel = beautyLevel.intensity * WHITE_RATIO
            val smoothLevel = beautyLevel.intensity * SMOOTH_RATIO
            Log.d(TAG, "processBeauty: 参数 white=$whiteLevel smooth=$smoothLevel")

            // 使用Kotlin实现的美颜效果（安全，不会崩溃）
            // 禁用Native美颜以避免SIGSEGV崩溃
            val resultBitmap = if (ENABLE_NATIVE_BEAUTY && SafeMagicJni.isLibraryLoaded()) {
                // Native处理（当前禁用）
                processWithNative(workBitmap, whiteLevel, smoothLevel)
            } else {
                // Kotlin纯实现（安全）
                Log.d(TAG, "processBeauty: 使用Kotlin美颜实现")
                processWithKotlin(workBitmap, beautyLevel.intensity)
            }

            val processingTime = System.currentTimeMillis() - startTime

            if (resultBitmap != null) {
                successCount++
                Log.d(TAG, "processBeauty: 处理成功 耗时=${processingTime}ms (成功率: ${getSuccessRate()}%)")
                BeautyResult(
                    bitmap = resultBitmap,
                    processingTimeMs = processingTime,
                    success = true
                )
            } else {
                failureCount++
                Log.w(TAG, "processBeauty: 处理失败，返回原图")
                BeautyResult(
                    bitmap = sourceBitmap,
                    processingTimeMs = processingTime,
                    success = false,
                    errorMessage = "美颜处理失败"
                )
            }
        }
    }

    /**
     * 使用Native库处理美颜（可能崩溃，需要先缩放）
     */
    private fun processWithNative(bitmap: Bitmap, whiteLevel: Float, smoothLevel: Float): Bitmap? {
        // 检查图片尺寸，过大的图片需要先缩放
        val scaledBitmap = scaleDownIfNeeded(bitmap)
        val result = SafeMagicJni.processBeauty(scaledBitmap, whiteLevel, smoothLevel)

        return result.fold(
            onSuccess = { resultBitmap ->
                // 如果缩放过，需要放大回原尺寸
                if (scaledBitmap !== bitmap) {
                    scaleToSize(resultBitmap, bitmap.width, bitmap.height)
                } else {
                    resultBitmap
                }
            },
            onFailure = { error ->
                Log.e(TAG, "processWithNative: 失败 - ${error.message}")
                null
            }
        )
    }

    /**
     * 使用纯Kotlin/Android实现的美颜效果
     * 通过ColorMatrix实现美白和柔化效果
     *
     * @param bitmap 源Bitmap
     * @param intensity 美颜强度 (0.0 - 1.0)
     * @return 处理后的Bitmap
     */
    private fun processWithKotlin(bitmap: Bitmap, intensity: Float): Bitmap {
        Log.d(TAG, "processWithKotlin: 开始Kotlin美颜处理 intensity=$intensity")

        // 创建输出Bitmap
        val resultBitmap = Bitmap.createBitmap(
            bitmap.width,
            bitmap.height,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(resultBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. 美白效果：增加亮度和对比度
        val whitenMatrix = ColorMatrix().apply {
            // 亮度增加（美白）
            val brightness = intensity * 0.15f  // 最大15%亮度增加
            val brightnessArray = floatArrayOf(
                1f, 0f, 0f, 0f, brightness * 255,
                0f, 1f, 0f, 0f, brightness * 255,
                0f, 0f, 1f, 0f, brightness * 255,
                0f, 0f, 0f, 1f, 0f
            )
            set(brightnessArray)
        }

        // 2. 饱和度轻微降低（使肤色更柔和）
        val saturationMatrix = ColorMatrix().apply {
            setSaturation(1f - intensity * 0.1f)  // 最多降低10%饱和度
        }

        // 3. 对比度轻微增加（让五官更立体）
        val contrastMatrix = ColorMatrix().apply {
            val contrast = 1f + intensity * 0.05f  // 最多增加5%对比度
            val translate = (1f - contrast) * 128f
            val contrastArray = floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
            set(contrastArray)
        }

        // 合并所有效果矩阵
        val combinedMatrix = ColorMatrix()
        combinedMatrix.postConcat(whitenMatrix)
        combinedMatrix.postConcat(saturationMatrix)
        combinedMatrix.postConcat(contrastMatrix)

        // 应用效果
        paint.colorFilter = ColorMatrixColorFilter(combinedMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        Log.d(TAG, "processWithKotlin: Kotlin美颜处理完成 ${bitmap.width}x${bitmap.height}")
        return resultBitmap
    }

    /**
     * 如果图片过大则缩放（防止Native内存溢出）
     */
    private fun scaleDownIfNeeded(bitmap: Bitmap): Bitmap {
        val pixels = bitmap.width * bitmap.height

        // 检查是否需要缩放
        if (bitmap.width <= MAX_NATIVE_DIMENSION &&
            bitmap.height <= MAX_NATIVE_DIMENSION &&
            pixels <= MAX_NATIVE_PIXELS) {
            return bitmap
        }

        // 计算缩放比例
        val scale = min(
            MAX_NATIVE_DIMENSION.toFloat() / max(bitmap.width, bitmap.height),
            kotlin.math.sqrt(MAX_NATIVE_PIXELS.toFloat() / pixels)
        )

        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()

        Log.d(TAG, "scaleDownIfNeeded: 缩放图片 ${bitmap.width}x${bitmap.height} -> ${newWidth}x${newHeight}")
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * 缩放Bitmap到指定尺寸
     */
    private fun scaleToSize(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        if (bitmap.width == width && bitmap.height == height) {
            return bitmap
        }
        Log.d(TAG, "scaleToSize: 放大图片 ${bitmap.width}x${bitmap.height} -> ${width}x${height}")
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    /**
     * 快速美颜处理（用于预览）
     *
     * 仅应用磨皮效果，速度更快
     *
     * @param sourceBitmap 源Bitmap
     * @param smoothLevel 磨皮等级 (0.0 - 1.0)
     * @return 处理后的Bitmap，失败返回原图
     */
    suspend fun quickSmooth(
        sourceBitmap: Bitmap,
        smoothLevel: Float
    ): Bitmap = withContext(Dispatchers.Default) {
        if (smoothLevel <= 0f) return@withContext sourceBitmap

        if (!SafeMagicJni.isLibraryLoaded()) {
            Log.w(TAG, "quickSmooth: Native库未加载，返回原图")
            return@withContext sourceBitmap
        }

        processingMutex.withLock {
            val startTime = System.currentTimeMillis()
            Log.d(TAG, "quickSmooth: 开始处理 level=$smoothLevel")

            val workBitmap = ensureArgb8888(sourceBitmap)

            // 存储Bitmap
            val handleResult = SafeMagicJni.storeBitmapData(workBitmap)
            if (handleResult.isFailure) {
                Log.e(TAG, "quickSmooth: 存储Bitmap失败")
                return@withLock sourceBitmap
            }
            val handle = handleResult.getOrThrow()
            currentHandle = handle

            // 初始化
            val initResult = SafeMagicJni.initBeautify(handle)
            if (initResult.isFailure) {
                releaseResources()
                return@withLock sourceBitmap
            }
            isInitialized = true

            // 磨皮处理
            val smoothResult = SafeMagicJni.applySkinSmooth(smoothLevel)
            if (smoothResult.isFailure) {
                releaseResources()
                return@withLock sourceBitmap
            }

            // 获取结果
            val bitmapResult = SafeMagicJni.getResultBitmap(handle)
            releaseResources()

            val processingTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "quickSmooth: 处理完成 耗时=${processingTime}ms")

            bitmapResult.getOrDefault(sourceBitmap)
        }
    }

    /**
     * 快速美白处理
     *
     * @param sourceBitmap 源Bitmap
     * @param whiteLevel 美白等级 (0.0 - 1.0)
     * @return 处理后的Bitmap，失败返回原图
     */
    suspend fun quickWhiten(
        sourceBitmap: Bitmap,
        whiteLevel: Float
    ): Bitmap = withContext(Dispatchers.Default) {
        if (whiteLevel <= 0f) return@withContext sourceBitmap

        if (!SafeMagicJni.isLibraryLoaded()) {
            Log.w(TAG, "quickWhiten: Native库未加载，返回原图")
            return@withContext sourceBitmap
        }

        processingMutex.withLock {
            val startTime = System.currentTimeMillis()
            Log.d(TAG, "quickWhiten: 开始处理 level=$whiteLevel")

            val workBitmap = ensureArgb8888(sourceBitmap)

            // 存储Bitmap
            val handleResult = SafeMagicJni.storeBitmapData(workBitmap)
            if (handleResult.isFailure) {
                Log.e(TAG, "quickWhiten: 存储Bitmap失败")
                return@withLock sourceBitmap
            }
            val handle = handleResult.getOrThrow()
            currentHandle = handle

            // 初始化
            val initResult = SafeMagicJni.initBeautify(handle)
            if (initResult.isFailure) {
                releaseResources()
                return@withLock sourceBitmap
            }
            isInitialized = true

            // 美白处理
            val whiteResult = SafeMagicJni.applyWhiteSkin(whiteLevel)
            if (whiteResult.isFailure) {
                releaseResources()
                return@withLock sourceBitmap
            }

            // 获取结果
            val bitmapResult = SafeMagicJni.getResultBitmap(handle)
            releaseResources()

            val processingTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "quickWhiten: 处理完成 耗时=${processingTime}ms")

            bitmapResult.getOrDefault(sourceBitmap)
        }
    }

    /**
     * 确保Bitmap是ARGB_8888格式
     */
    private fun ensureArgb8888(bitmap: Bitmap): Bitmap {
        return if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            Log.d(TAG, "ensureArgb8888: 转换Bitmap格式 ${bitmap.config} -> ARGB_8888")
            bitmap.copy(Bitmap.Config.ARGB_8888, true)
        }
    }

    /**
     * 释放Native资源（使用SafeMagicJni）
     */
    private fun releaseResources() {
        Log.d(TAG, "releaseResources: 开始释放资源")
        if (isInitialized) {
            SafeMagicJni.uninitBeautify()                 // 安全释放
            isInitialized = false
        }
        currentHandle?.let { handle ->
            SafeMagicJni.freeBitmapData(handle)           // 安全释放
            currentHandle = null
        }
        Log.d(TAG, "releaseResources: 资源释放完成")
    }

    /**
     * 释放所有资源（在不再使用时调用）
     */
    fun release() {
        releaseResources()
        Log.d(TAG, "release: 美颜处理器资源已释放 统计: 总计=$totalProcessCount 成功=$successCount 失败=$failureCount")
    }

    /**
     * 获取成功率百分比
     */
    private fun getSuccessRate(): Int {
        return if (totalProcessCount > 0) {
            ((successCount * 100) / totalProcessCount).toInt()
        } else {
            100
        }
    }

    /**
     * 获取处理统计信息
     *
     * @return 统计信息字符串
     */
    fun getStatistics(): String {
        return "总计: $totalProcessCount, 成功: $successCount, 失败: $failureCount, 成功率: ${getSuccessRate()}%"
    }
}
