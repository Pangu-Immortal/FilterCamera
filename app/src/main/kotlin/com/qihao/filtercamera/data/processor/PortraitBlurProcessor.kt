/**
 * PortraitBlurProcessor.kt - 人像背景虚化处理器
 *
 * 使用 ML Kit Selfie Segmentation 实现人像背景虚化效果
 * 核心功能：
 * - 人物前景分割（基于 ML Kit Selfie Segmentation）
 * - 背景高斯模糊处理
 * - 前景/背景合成
 * - 可调节模糊强度（散景效果）
 *
 * 技术实现：
 * - 使用 BlurProvider 策略模式进行高效高斯模糊（支持 Toolkit 和 Legacy RenderScript）
 * - 支持实时预览和拍照后处理
 * - 优雅降级：ML Kit 不可用时返回原图
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.data.processor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.Log
import com.qihao.filtercamera.data.processor.blur.BlurProvider
import com.qihao.filtercamera.data.processor.blur.BlurProviderFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.Segmenter
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 人像虚化处理结果
 *
 * @param bitmap 处理后的图像（人像清晰 + 背景虚化）
 * @param processingTimeMs 处理耗时（毫秒）
 * @param success 处理是否成功
 * @param errorMessage 错误信息（失败时有值）
 * @param hasPerson 是否检测到人物
 */
data class PortraitBlurResult(
    val bitmap: Bitmap,                                          // 处理后的Bitmap
    val processingTimeMs: Long,                                  // 处理耗时
    val success: Boolean = true,                                 // 是否成功
    val errorMessage: String? = null,                           // 错误信息
    val hasPerson: Boolean = false                              // 是否检测到人物
)

/**
 * 人像虚化配置
 *
 * @param blurRadius 模糊半径（1-25，值越大越模糊）
 * @param edgeSmooth 边缘平滑度（0.0-1.0，越高边缘越柔和）
 * @param foregroundThreshold 前景阈值（0.0-1.0，越高要求越严格）
 * @param enableRawSegmentMask 是否启用原始分割蒙版（用于调试）
 */
data class PortraitBlurConfig(
    val blurRadius: Float = 20f,                                // 模糊半径（1-25）
    val edgeSmooth: Float = 0.5f,                               // 边缘平滑度
    val foregroundThreshold: Float = 0.5f,                      // 前景阈值
    val enableRawSegmentMask: Boolean = false                   // 调试模式
) {
    init {
        require(blurRadius in 1f..25f) { "blurRadius must be in range 1-25" }
        require(edgeSmooth in 0f..1f) { "edgeSmooth must be in range 0-1" }
        require(foregroundThreshold in 0f..1f) { "foregroundThreshold must be in range 0-1" }
    }

    companion object {
        /** 轻度虚化预设 */
        val LIGHT = PortraitBlurConfig(blurRadius = 10f, edgeSmooth = 0.3f)

        /** 中度虚化预设（默认） */
        val MEDIUM = PortraitBlurConfig(blurRadius = 18f, edgeSmooth = 0.5f)

        /** 重度虚化预设（强散景效果） */
        val HEAVY = PortraitBlurConfig(blurRadius = 25f, edgeSmooth = 0.7f)
    }
}

/**
 * 人像背景虚化处理器
 *
 * 使用 ML Kit Selfie Segmentation 分离人物和背景
 * 对背景应用高斯模糊，实现人像虚化效果
 */
@Singleton
class PortraitBlurProcessor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "PortraitBlurProcessor"         // 日志标签
        private const val MIN_CONFIDENCE_THRESHOLD = 0.3f       // 最小置信度阈值
    }

    // 处理互斥锁，确保线程安全
    private val processingMutex = Mutex()

    // ML Kit Selfie Segmenter 实例
    private var segmenter: Segmenter? = null

    // 模糊处理提供者（使用策略模式替代已弃用的 RenderScript）
    private var blurProvider: BlurProvider? = null

    // 处理进度流（用于UI显示）
    private val _processingProgress = MutableStateFlow<Pair<String, Float>?>(null)
    val processingProgress: StateFlow<Pair<String, Float>?> = _processingProgress.asStateFlow()

    // 处理统计
    private var totalProcessCount = 0L                          // 总处理次数
    private var successCount = 0L                               // 成功次数（检测到人物）
    private var noPersonCount = 0L                              // 未检测到人物次数
    private var errorCount = 0L                                 // 错误次数

    // 是否已初始化
    private var isInitialized = false

    /**
     * 初始化处理器
     *
     * 创建 ML Kit Segmenter 和 RenderScript 实例
     * 建议在相机启动时调用
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.Default) {
        processingMutex.withLock {
            runCatching {
                if (isInitialized) {
                    Log.d(TAG, "initialize: 已初始化，跳过")
                    return@runCatching
                }

                Log.d(TAG, "initialize: 开始初始化人像虚化处理器")
                _processingProgress.value = "正在初始化..." to 0f

                // 1. 创建 ML Kit Selfie Segmenter
                val options = SelfieSegmenterOptions.Builder()
                    .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)  // 流模式（适合实时处理）
                    .enableRawSizeMask()                                  // 启用原始大小蒙版
                    .build()

                segmenter = Segmentation.getClient(options)
                Log.d(TAG, "initialize: ML Kit Segmenter 创建成功")
                _processingProgress.value = "ML Kit 就绪" to 0.5f

                // 2. 获取模糊处理提供者（自动选择最佳实现）
                blurProvider = BlurProviderFactory.create(context)
                Log.d(TAG, "initialize: BlurProvider 创建成功 provider=${blurProvider?.name}")

                isInitialized = true
                _processingProgress.value = "初始化完成" to 1f
                Log.d(TAG, "initialize: 人像虚化处理器初始化完成")

                // 短暂延迟后清除进度，避免UI一直显示"初始化完成"
                kotlinx.coroutines.delay(500)
                _processingProgress.value = null
            }
        }
    }

    /**
     * 处理图像，应用人像虚化效果
     *
     * 核心处理流程：
     * 1. 使用 ML Kit 分割人物前景
     * 2. 对原图应用高斯模糊生成模糊背景
     * 3. 使用分割蒙版合成最终图像（前景清晰 + 背景模糊）
     *
     * @param sourceBitmap 源图像（建议 ARGB_8888 格式）
     * @param config 虚化配置
     * @return 处理结果
     */
    suspend fun processPortraitBlur(
        sourceBitmap: Bitmap,
        config: PortraitBlurConfig = PortraitBlurConfig.MEDIUM
    ): PortraitBlurResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        totalProcessCount++
        Log.d(TAG, "processPortraitBlur: 开始处理 #$totalProcessCount config=$config")

        // 整体处理超时保护（包括初始化和分割），避免ML Kit在模拟器上无限卡住
        val result = withTimeoutOrNull(15000L) {  // 15秒总超时
            processingMutex.withLock {
                try {
                    // 确保已初始化（带超时保护）
                    if (!isInitialized) {
                        Log.d(TAG, "processPortraitBlur: 自动初始化")
                        val initResult = withTimeoutOrNull(5000L) {  // 初始化5秒超时
                            initialize()
                        }
                        if (initResult == null || initResult.isFailure) {
                            Log.w(TAG, "processPortraitBlur: 初始化超时或失败")
                            return@withLock null
                        }
                    }

                    val segmenterInstance = segmenter ?: throw IllegalStateException("Segmenter 未初始化")

                // 步骤1：分割人物（带超时保护，避免ML Kit在模拟器上卡住）
                _processingProgress.value = "分割人物..." to 0.2f
                Log.d(TAG, "processPortraitBlur: 开始人物分割")
                val segmentStartTime = System.currentTimeMillis()

                val inputImage = InputImage.fromBitmap(sourceBitmap, 0)

                // 使用超时机制，防止ML Kit在模拟器上无限等待
                val segmentationMask = withTimeoutOrNull(10000L) {  // 10秒超时
                    segmentImage(segmenterInstance, inputImage)
                }

                // 如果超时或分割失败，返回原图
                if (segmentationMask == null) {
                    Log.w(TAG, "processPortraitBlur: 人物分割超时或失败，返回原图")
                    _processingProgress.value = null
                    return@withLock PortraitBlurResult(
                        bitmap = sourceBitmap,
                        processingTimeMs = System.currentTimeMillis() - startTime,
                        success = true,
                        hasPerson = false,
                        errorMessage = "人物分割超时"
                    )
                }

                val segmentTime = System.currentTimeMillis() - segmentStartTime
                Log.d(TAG, "processPortraitBlur: 人物分割完成 耗时=${segmentTime}ms")

                // 检查是否检测到人物
                val hasPerson = checkHasPerson(segmentationMask, config.foregroundThreshold)
                if (!hasPerson) {
                    noPersonCount++
                    Log.d(TAG, "processPortraitBlur: 未检测到人物，返回原图")
                    _processingProgress.value = null
                    return@withLock PortraitBlurResult(
                        bitmap = sourceBitmap,
                        processingTimeMs = System.currentTimeMillis() - startTime,
                        success = true,
                        hasPerson = false,
                        errorMessage = "未检测到人物"
                    )
                }

                // 步骤2：创建模糊背景
                _processingProgress.value = "创建模糊背景..." to 0.5f
                Log.d(TAG, "processPortraitBlur: 开始创建模糊背景")
                val blurStartTime = System.currentTimeMillis()

                val blurredBitmap = applyGaussianBlur(sourceBitmap, config.blurRadius)

                val blurTime = System.currentTimeMillis() - blurStartTime
                Log.d(TAG, "processPortraitBlur: 模糊背景创建完成 耗时=${blurTime}ms")

                // 步骤3：合成图像（前景清晰 + 背景模糊）
                _processingProgress.value = "合成图像..." to 0.8f
                Log.d(TAG, "processPortraitBlur: 开始合成图像")
                val compositeStartTime = System.currentTimeMillis()

                val resultBitmap = compositeImage(
                    foreground = sourceBitmap,
                    blurredBackground = blurredBitmap,
                    mask = segmentationMask,
                    threshold = config.foregroundThreshold,
                    edgeSmooth = config.edgeSmooth
                )

                val compositeTime = System.currentTimeMillis() - compositeStartTime
                Log.d(TAG, "processPortraitBlur: 图像合成完成 耗时=${compositeTime}ms")

                // 清理临时资源
                if (blurredBitmap != sourceBitmap && !blurredBitmap.isRecycled) {
                    blurredBitmap.recycle()
                }

                successCount++
                val totalTime = System.currentTimeMillis() - startTime
                _processingProgress.value = "处理完成" to 1f

                Log.d(TAG, "processPortraitBlur: 处理成功 总耗时=${totalTime}ms " +
                        "(分割=${segmentTime}ms, 模糊=${blurTime}ms, 合成=${compositeTime}ms)")

                // 短暂延迟后清除进度
                _processingProgress.value = null

                PortraitBlurResult(
                    bitmap = resultBitmap,
                    processingTimeMs = totalTime,
                    success = true,
                    hasPerson = true
                )

            } catch (e: Exception) {
                errorCount++
                val errorMsg = "人像虚化处理失败: ${e.message}"
                Log.e(TAG, "processPortraitBlur: $errorMsg", e)
                _processingProgress.value = null

                PortraitBlurResult(
                    bitmap = sourceBitmap,
                    processingTimeMs = System.currentTimeMillis() - startTime,
                    success = false,
                    errorMessage = errorMsg,
                    hasPerson = false
                )
            }
            }
        }

        // 如果整体处理超时，返回原图
        if (result == null) {
            Log.w(TAG, "processPortraitBlur: 整体处理超时，返回原图")
            _processingProgress.value = null
            errorCount++
            return@withContext PortraitBlurResult(
                bitmap = sourceBitmap,
                processingTimeMs = System.currentTimeMillis() - startTime,
                success = true,
                hasPerson = false,
                errorMessage = "处理超时"
            )
        }

        result
    }

    /**
     * 快速人像虚化（用于实时预览）
     *
     * 使用较低分辨率进行处理，速度更快
     *
     * @param sourceBitmap 源图像
     * @param blurRadius 模糊半径
     * @return 处理后的图像，失败返回原图
     */
    suspend fun quickPortraitBlur(
        sourceBitmap: Bitmap,
        blurRadius: Float = 15f
    ): Bitmap = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "quickPortraitBlur: 开始快速处理 radius=$blurRadius")

        processingMutex.withLock {
            try {
                // 确保已初始化
                if (!isInitialized) {
                    initialize().getOrThrow()
                }

                val segmenterInstance = segmenter ?: return@withLock sourceBitmap

                // 缩小图像以加速处理
                val scaleFactor = 0.5f
                val scaledWidth = (sourceBitmap.width * scaleFactor).toInt()
                val scaledHeight = (sourceBitmap.height * scaleFactor).toInt()
                val scaledBitmap = Bitmap.createScaledBitmap(sourceBitmap, scaledWidth, scaledHeight, true)

                // 分割人物
                val inputImage = InputImage.fromBitmap(scaledBitmap, 0)
                val mask = segmentImage(segmenterInstance, inputImage)

                // 检查是否有人物
                if (!checkHasPerson(mask, MIN_CONFIDENCE_THRESHOLD)) {
                    scaledBitmap.recycle()
                    return@withLock sourceBitmap
                }

                // 创建模糊背景（使用缩小的图像）
                val blurredScaled = applyGaussianBlur(scaledBitmap, blurRadius.coerceIn(1f, 25f))

                // 合成图像
                val resultScaled = compositeImage(
                    foreground = scaledBitmap,
                    blurredBackground = blurredScaled,
                    mask = mask,
                    threshold = MIN_CONFIDENCE_THRESHOLD,
                    edgeSmooth = 0.3f
                )

                // 放大回原始尺寸
                val result = Bitmap.createScaledBitmap(
                    resultScaled,
                    sourceBitmap.width,
                    sourceBitmap.height,
                    true
                )

                // 清理临时资源
                scaledBitmap.recycle()
                if (blurredScaled != scaledBitmap) blurredScaled.recycle()
                if (resultScaled != scaledBitmap) resultScaled.recycle()

                val processingTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "quickPortraitBlur: 处理完成 耗时=${processingTime}ms")

                result

            } catch (e: Exception) {
                Log.e(TAG, "quickPortraitBlur: 处理失败 ${e.message}", e)
                sourceBitmap
            }
        }
    }

    /**
     * 使用 ML Kit 分割图像
     *
     * @param segmenter Segmenter 实例
     * @param inputImage 输入图像
     * @return 分割蒙版
     */
    private suspend fun segmentImage(
        segmenter: Segmenter,
        inputImage: InputImage
    ): SegmentationMask = suspendCancellableCoroutine { continuation ->
        segmenter.process(inputImage)
            .addOnSuccessListener { mask ->
                continuation.resume(mask)
            }
            .addOnFailureListener { e ->
                continuation.resumeWithException(e)
            }
    }

    /**
     * 检查分割蒙版中是否有人物
     *
     * @param mask 分割蒙版
     * @param threshold 阈值
     * @return 是否检测到人物
     */
    private fun checkHasPerson(mask: SegmentationMask, threshold: Float): Boolean {
        val buffer = mask.buffer
        buffer.rewind()

        var foregroundPixelCount = 0
        val totalPixels = mask.width * mask.height

        while (buffer.hasRemaining()) {
            val confidence = buffer.float
            if (confidence >= threshold) {
                foregroundPixelCount++
            }
        }

        buffer.rewind()

        // 至少5%的像素被检测为前景才认为有人物
        val foregroundRatio = foregroundPixelCount.toFloat() / totalPixels
        Log.d(TAG, "checkHasPerson: 前景比例=${foregroundRatio * 100}%")
        return foregroundRatio > 0.05f
    }

    /**
     * 应用高斯模糊
     *
     * 使用 BlurProvider 进行高效模糊处理（自动选择最佳实现）
     *
     * @param sourceBitmap 源图像
     * @param radius 模糊半径（1-25）
     * @return 模糊后的图像
     */
    private fun applyGaussianBlur(sourceBitmap: Bitmap, radius: Float): Bitmap {
        // 获取或创建 BlurProvider
        val provider = blurProvider ?: BlurProviderFactory.create(context).also { blurProvider = it }

        Log.d(TAG, "applyGaussianBlur: 使用 ${provider.name} 进行模糊处理 radius=$radius")

        // 使用 BlurProvider 进行模糊处理
        return provider.blur(sourceBitmap, radius)
    }

    /**
     * 合成图像（前景 + 模糊背景）
     *
     * 使用分割蒙版将清晰的前景（人物）与模糊的背景合成
     *
     * @param foreground 清晰的前景图像（原图）
     * @param blurredBackground 模糊的背景图像
     * @param mask 分割蒙版
     * @param threshold 前景阈值
     * @param edgeSmooth 边缘平滑度
     * @return 合成后的图像
     */
    private fun compositeImage(
        foreground: Bitmap,
        blurredBackground: Bitmap,
        mask: SegmentationMask,
        threshold: Float,
        edgeSmooth: Float
    ): Bitmap {
        val width = foreground.width
        val height = foreground.height

        // 创建结果位图
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // 绘制模糊背景
        canvas.drawBitmap(blurredBackground, 0f, 0f, null)

        // 创建蒙版位图
        val maskBitmap = createMaskBitmap(mask, width, height, threshold, edgeSmooth)

        // 使用 PorterDuff 模式绘制前景
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER)

        // 创建带蒙版的前景
        val maskedForeground = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val maskedCanvas = Canvas(maskedForeground)
        maskedCanvas.drawBitmap(foreground, 0f, 0f, null)
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        maskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        maskedCanvas.drawBitmap(maskBitmap, 0f, 0f, maskPaint)

        // 将带蒙版的前景绘制到结果上
        canvas.drawBitmap(maskedForeground, 0f, 0f, null)

        // 清理
        maskBitmap.recycle()
        maskedForeground.recycle()

        return result
    }

    /**
     * 从分割蒙版创建位图蒙版
     *
     * @param mask 分割蒙版
     * @param targetWidth 目标宽度
     * @param targetHeight 目标高度
     * @param threshold 前景阈值
     * @param edgeSmooth 边缘平滑度
     * @return 蒙版位图（白色=前景，黑色=背景）
     */
    private fun createMaskBitmap(
        mask: SegmentationMask,
        targetWidth: Int,
        targetHeight: Int,
        threshold: Float,
        edgeSmooth: Float
    ): Bitmap {
        val maskWidth = mask.width
        val maskHeight = mask.height

        // 创建原始大小的蒙版
        val rawMask = Bitmap.createBitmap(maskWidth, maskHeight, Bitmap.Config.ARGB_8888)
        val buffer = mask.buffer
        buffer.rewind()

        val pixels = IntArray(maskWidth * maskHeight)
        for (i in pixels.indices) {
            val confidence = buffer.float

            // 使用平滑过渡
            val alpha = when {
                confidence >= threshold + edgeSmooth * 0.5f -> 255
                confidence <= threshold - edgeSmooth * 0.5f -> 0
                else -> {
                    // 平滑过渡区域
                    val range = edgeSmooth
                    val normalized = (confidence - (threshold - range * 0.5f)) / range
                    (normalized * 255).toInt().coerceIn(0, 255)
                }
            }

            pixels[i] = Color.argb(alpha, 255, 255, 255)
        }

        rawMask.setPixels(pixels, 0, maskWidth, 0, 0, maskWidth, maskHeight)
        buffer.rewind()

        // 缩放到目标大小
        return if (maskWidth != targetWidth || maskHeight != targetHeight) {
            val scaled = Bitmap.createScaledBitmap(rawMask, targetWidth, targetHeight, true)
            rawMask.recycle()
            scaled
        } else {
            rawMask
        }
    }

    /**
     * 检查处理器是否已初始化
     */
    fun isReady(): Boolean = isInitialized

    /**
     * 获取处理统计信息
     */
    fun getStatistics(): String {
        return "总计: $totalProcessCount, 成功: $successCount, 无人物: $noPersonCount, 错误: $errorCount"
    }

    /**
     * 释放所有资源
     *
     * 在不再使用时调用（如相机关闭时）
     */
    fun release() {
        Log.d(TAG, "release: 开始释放资源")

        segmenter?.close()
        segmenter = null

        blurProvider?.release()
        blurProvider = null

        isInitialized = false
        _processingProgress.value = null

        Log.d(TAG, "release: 资源释放完成 统计: ${getStatistics()}")
    }
}
