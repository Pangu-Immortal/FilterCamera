/**
 * BeautyProcessor.kt - 美颜处理器
 *
 * 提供美颜功能的高级封装
 * 管理Native资源生命周期，提供线程安全的美颜处理
 *
 * 设计原则：
 * - 使用SafeMagicJni安全封装，避免JNI崩溃
 * - 优雅降级：处理失败时返回原图
 * - 详细的性能日志输出
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.data.processor

import android.graphics.Bitmap
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

            // 检查Native库状态
            if (!SafeMagicJni.isLibraryLoaded()) {
                failureCount++
                val errorMsg = "Native库未加载，返回原图"
                Log.w(TAG, "processBeauty: $errorMsg")
                return@withLock BeautyResult(
                    bitmap = sourceBitmap,
                    processingTimeMs = System.currentTimeMillis() - startTime,
                    success = false,
                    errorMessage = errorMsg
                )
            }

            // 确保Bitmap格式正确
            val workBitmap = ensureArgb8888(sourceBitmap)

            // 计算美颜参数
            val whiteLevel = beautyLevel.intensity * WHITE_RATIO
            val smoothLevel = beautyLevel.intensity * SMOOTH_RATIO
            Log.d(TAG, "processBeauty: 参数 white=$whiteLevel smooth=$smoothLevel")

            // 使用SafeMagicJni进行处理
            val result = SafeMagicJni.processBeauty(workBitmap, whiteLevel, smoothLevel)

            val processingTime = System.currentTimeMillis() - startTime

            result.fold(
                onSuccess = { resultBitmap ->
                    successCount++
                    Log.d(TAG, "processBeauty: 处理成功 耗时=${processingTime}ms (成功率: ${getSuccessRate()}%)")
                    BeautyResult(
                        bitmap = resultBitmap,
                        processingTimeMs = processingTime,
                        success = true
                    )
                },
                onFailure = { error ->
                    failureCount++
                    val errorMsg = when (error) {
                        is JniError.LibraryNotLoaded -> "Native库未加载"
                        is JniError.InvalidParameter -> "参数无效: ${error.message}"
                        is JniError.NativeCallFailed -> "Native调用失败: ${error.message}"
                        is JniError.NullResult -> "结果为空: ${error.message}"
                        else -> "未知错误: ${error.message}"
                    }
                    Log.e(TAG, "processBeauty: 处理失败 $errorMsg (成功率: ${getSuccessRate()}%)")
                    BeautyResult(
                        bitmap = sourceBitmap,             // 失败时返回原图
                        processingTimeMs = processingTime,
                        success = false,
                        errorMessage = errorMsg
                    )
                }
            )
        }
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
