/**
 * NightModeProcessor.kt - 夜景模式处理器
 *
 * 提供夜景拍照功能的完整实现：
 * 1. 硬件夜景：使用CameraX Extensions实现设备原生夜景模式
 * 2. 软件夜景：当硬件不支持时，使用多帧合成+降噪算法
 *
 * 软件夜景算法流程：
 * - 多帧捕获（6-12帧）
 * - 帧对齐（光流法/特征点匹配简化版）
 * - 时域降噪（帧堆栈加权平均）
 * - 空域降噪（双边滤波）
 * - 局部色调映射（动态范围增强）
 *
 * 设计原则：
 * - 优先使用硬件夜景模式（性能更好、质量更高）
 * - 自动降级到软件夜景（兼容性更广）
 * - 支持处理进度回调
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.data.processor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 夜景处理结果
 *
 * @param bitmap 处理后的Bitmap
 * @param processingTimeMs 处理耗时（毫秒）
 * @param isHardwareNight 是否使用硬件夜景模式
 * @param frameCount 使用的帧数
 * @param success 处理是否成功
 * @param errorMessage 错误信息
 */
data class NightResult(
    val bitmap: Bitmap,
    val processingTimeMs: Long,
    val isHardwareNight: Boolean,
    val frameCount: Int = 1,
    val success: Boolean = true,
    val errorMessage: String? = null
)

/**
 * 夜景扩展支持状态
 */
data class NightSupportStatus(
    val isHardwareNightAvailable: Boolean,                                      // 硬件夜景是否可用
    val isSoftwareNightAvailable: Boolean = true,                               // 软件夜景始终可用
    val supportedCameraLens: List<Int> = emptyList(),                           // 支持夜景的摄像头
    val message: String                                                          // 状态描述
)

/**
 * 夜景处理进度
 */
data class NightProcessingProgress(
    val stage: NightProcessingStage,                                            // 当前处理阶段
    val progress: Float,                                                         // 进度（0.0-1.0）
    val message: String                                                          // 进度描述
)

/**
 * 夜景处理阶段
 */
enum class NightProcessingStage(val displayName: String) {
    CAPTURING("捕获中"),                                                          // 多帧捕获
    ALIGNING("对齐中"),                                                           // 帧对齐
    DENOISING("降噪中"),                                                          // 时域/空域降噪
    TONE_MAPPING("色调映射"),                                                     // 色调映射
    FINALIZING("完成中")                                                          // 最终处理
}

/**
 * 夜景模式处理器
 *
 * 整合硬件夜景和软件夜景的完整实现
 */
@Singleton
class NightModeProcessor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "NightModeProcessor"

        // 多帧捕获参数
        private const val DEFAULT_FRAME_COUNT = 8                               // 默认捕获帧数
        private const val MIN_FRAME_COUNT = 4                                   // 最小帧数
        private const val MAX_FRAME_COUNT = 12                                  // 最大帧数

        // 双边滤波参数
        private const val BILATERAL_SIGMA_SPACE = 10f                           // 空间域标准差
        private const val BILATERAL_SIGMA_COLOR = 30f                           // 颜色域标准差
        private const val BILATERAL_KERNEL_SIZE = 5                             // 滤波核大小

        // 帧对齐参数
        private const val ALIGNMENT_BLOCK_SIZE = 16                             // 对齐块大小
        private const val ALIGNMENT_SEARCH_RANGE = 8                            // 搜索范围

        // 降噪权重
        private const val TEMPORAL_WEIGHT = 0.7f                                // 时域降噪权重
        private const val SPATIAL_WEIGHT = 0.3f                                 // 空域降噪权重
    }

    // 线程安全锁
    private val mutex = Mutex()

    // CameraX Extensions管理器
    private var extensionsManager: ExtensionsManager? = null
    private var cameraProvider: ProcessCameraProvider? = null

    // 夜景支持状态缓存
    private var _supportStatus: NightSupportStatus? = null
    val supportStatus: NightSupportStatus? get() = _supportStatus

    // 处理进度（用于UI显示）
    private val _processingProgress = MutableStateFlow<NightProcessingProgress?>(null)
    val processingProgress: StateFlow<NightProcessingProgress?> = _processingProgress.asStateFlow()

    // 处理中标志
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    // ==================== 初始化 ====================

    /**
     * 初始化ExtensionsManager
     *
     * 必须在使用硬件夜景前调用
     *
     * @return 初始化是否成功
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.Main) {
        mutex.withLock {
            try {
                Log.d(TAG, "initialize: 开始初始化夜景模式处理器")

                // 获取CameraProvider
                cameraProvider = getCameraProvider()
                Log.d(TAG, "initialize: CameraProvider获取成功")

                // 获取ExtensionsManager
                extensionsManager = getExtensionsManager(cameraProvider!!)
                Log.d(TAG, "initialize: ExtensionsManager获取成功")

                // 检测夜景支持状态
                _supportStatus = checkNightSupport()
                Log.i(TAG, "initialize: 初始化完成 - ${_supportStatus?.message}")

                true
            } catch (e: Exception) {
                Log.e(TAG, "initialize: 初始化失败", e)
                _supportStatus = NightSupportStatus(
                    isHardwareNightAvailable = false,
                    message = "初始化失败: ${e.message}"
                )
                false
            }
        }
    }

    /**
     * 获取CameraProvider
     */
    private suspend fun getCameraProvider(): ProcessCameraProvider =
        suspendCancellableCoroutine { continuation ->
            ProcessCameraProvider.getInstance(context).also { future ->
                future.addListener({
                    try {
                        continuation.resume(future.get())
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                }, { it.run() })
            }
        }

    /**
     * 获取ExtensionsManager
     */
    private suspend fun getExtensionsManager(provider: ProcessCameraProvider): ExtensionsManager =
        suspendCancellableCoroutine { continuation ->
            ExtensionsManager.getInstanceAsync(context, provider).also { future ->
                future.addListener({
                    try {
                        continuation.resume(future.get())
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                }, { it.run() })
            }
        }

    // ==================== 夜景支持检测 ====================

    /**
     * 检测夜景扩展支持状态
     *
     * @return 夜景支持状态
     */
    private fun checkNightSupport(): NightSupportStatus {
        val extensions = extensionsManager ?: return NightSupportStatus(
            isHardwareNightAvailable = false,
            message = "ExtensionsManager未初始化"
        )

        val supportedLenses = mutableListOf<Int>()

        // 检查后置摄像头夜景支持
        val backSelector = CameraSelector.DEFAULT_BACK_CAMERA
        val backNightSupported = try {
            extensions.isExtensionAvailable(backSelector, ExtensionMode.NIGHT)
        } catch (e: Exception) {
            Log.w(TAG, "checkNightSupport: 后置摄像头夜景检测失败", e)
            false
        }
        if (backNightSupported) {
            supportedLenses.add(CameraSelector.LENS_FACING_BACK)
            Log.d(TAG, "checkNightSupport: 后置摄像头支持硬件夜景")
        }

        // 检查前置摄像头夜景支持
        val frontSelector = CameraSelector.DEFAULT_FRONT_CAMERA
        val frontNightSupported = try {
            extensions.isExtensionAvailable(frontSelector, ExtensionMode.NIGHT)
        } catch (e: Exception) {
            Log.w(TAG, "checkNightSupport: 前置摄像头夜景检测失败", e)
            false
        }
        if (frontNightSupported) {
            supportedLenses.add(CameraSelector.LENS_FACING_FRONT)
            Log.d(TAG, "checkNightSupport: 前置摄像头支持硬件夜景")
        }

        val isHardwareAvailable = supportedLenses.isNotEmpty()
        val message = if (isHardwareAvailable) {
            "硬件夜景模式可用 (${supportedLenses.size}个摄像头支持)"
        } else {
            "硬件夜景不可用，将使用软件夜景模式"
        }

        return NightSupportStatus(
            isHardwareNightAvailable = isHardwareAvailable,
            supportedCameraLens = supportedLenses,
            message = message
        )
    }

    /**
     * 检查指定摄像头是否支持硬件夜景
     *
     * @param lensFacing 摄像头方向
     * @return 是否支持硬件夜景
     */
    fun isHardwareNightAvailable(lensFacing: Int): Boolean {
        return _supportStatus?.supportedCameraLens?.contains(lensFacing) == true
    }

    // ==================== 夜景 CameraSelector ====================

    /**
     * 获取夜景扩展的CameraSelector
     *
     * @param baseCameraSelector 基础CameraSelector
     * @return 可能启用夜景的CameraSelector
     */
    fun getNightCameraSelector(baseCameraSelector: CameraSelector): CameraSelector {
        val extensions = extensionsManager ?: run {
            Log.w(TAG, "getNightCameraSelector: ExtensionsManager未初始化")
            return baseCameraSelector
        }

        return try {
            if (extensions.isExtensionAvailable(baseCameraSelector, ExtensionMode.NIGHT)) {
                val nightSelector = extensions.getExtensionEnabledCameraSelector(
                    baseCameraSelector,
                    ExtensionMode.NIGHT
                )
                Log.d(TAG, "getNightCameraSelector: 返回硬件夜景CameraSelector")
                nightSelector
            } else {
                Log.d(TAG, "getNightCameraSelector: 硬件夜景不可用，返回普通选择器")
                baseCameraSelector
            }
        } catch (e: Exception) {
            Log.e(TAG, "getNightCameraSelector: 获取夜景选择器失败", e)
            baseCameraSelector
        }
    }

    // ==================== 软件夜景处理 ====================

    /**
     * 软件夜景处理 - 多帧合成+降噪
     *
     * 处理流程：
     * 1. 帧对齐 - 校正帧间位移
     * 2. 时域降噪 - 帧堆栈加权平均
     * 3. 空域降噪 - 双边滤波
     * 4. 色调映射 - 增强动态范围
     *
     * @param frames 多帧图像列表
     * @param onProgress 进度回调
     * @return 夜景处理结果
     */
    suspend fun processSoftwareNight(
        frames: List<Bitmap>,
        onProgress: ((NightProcessingProgress) -> Unit)? = null
    ): NightResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        _isProcessing.value = true

        try {
            Log.d(TAG, "processSoftwareNight: 开始软件夜景处理，帧数=${frames.size}")

            if (frames.isEmpty()) {
                throw IllegalArgumentException("至少需要1帧图像")
            }

            // 单帧情况：只进行降噪和增强
            if (frames.size == 1) {
                Log.d(TAG, "processSoftwareNight: 单帧模式，应用降噪增强")
                updateProgress(NightProcessingStage.DENOISING, 0.3f, "单帧降噪处理中...")
                onProgress?.invoke(_processingProgress.value!!)

                val denoised = applyBilateralFilter(frames[0])

                updateProgress(NightProcessingStage.TONE_MAPPING, 0.7f, "色调映射中...")
                onProgress?.invoke(_processingProgress.value!!)

                val enhanced = applyNightToneMapping(denoised)

                updateProgress(NightProcessingStage.FINALIZING, 1.0f, "处理完成")
                onProgress?.invoke(_processingProgress.value!!)

                return@withContext NightResult(
                    bitmap = enhanced,
                    processingTimeMs = System.currentTimeMillis() - startTime,
                    isHardwareNight = false,
                    frameCount = 1,
                    success = true
                )
            }

            val referenceFrame = frames[frames.size / 2]                        // 使用中间帧作为参考
            val width = referenceFrame.width
            val height = referenceFrame.height

            // Step 1: 帧对齐
            updateProgress(NightProcessingStage.ALIGNING, 0.1f, "对齐帧 0/${frames.size}")
            onProgress?.invoke(_processingProgress.value!!)

            val alignedFrames = mutableListOf<Bitmap>()
            frames.forEachIndexed { index, frame ->
                val aligned = if (frame === referenceFrame) {
                    frame
                } else {
                    alignFrame(frame, referenceFrame)
                }
                alignedFrames.add(aligned)

                val alignProgress = 0.1f + (0.2f * (index + 1) / frames.size)
                updateProgress(NightProcessingStage.ALIGNING, alignProgress, "对齐帧 ${index + 1}/${frames.size}")
                onProgress?.invoke(_processingProgress.value!!)
            }
            Log.d(TAG, "processSoftwareNight: 帧对齐完成")

            // Step 2: 时域降噪（帧堆栈加权平均）
            updateProgress(NightProcessingStage.DENOISING, 0.35f, "时域降噪中...")
            onProgress?.invoke(_processingProgress.value!!)

            val temporalDenoised = applyTemporalDenoising(alignedFrames)
            Log.d(TAG, "processSoftwareNight: 时域降噪完成")

            // Step 3: 空域降噪（双边滤波）
            updateProgress(NightProcessingStage.DENOISING, 0.55f, "空域降噪中...")
            onProgress?.invoke(_processingProgress.value!!)

            val spatialDenoised = applyBilateralFilter(temporalDenoised)
            Log.d(TAG, "processSoftwareNight: 空域降噪完成")

            // 混合时域和空域降噪结果
            val denoisedBitmap = blendBitmaps(temporalDenoised, spatialDenoised, TEMPORAL_WEIGHT)

            // Step 4: 色调映射
            updateProgress(NightProcessingStage.TONE_MAPPING, 0.75f, "色调映射中...")
            onProgress?.invoke(_processingProgress.value!!)

            val toneMapped = applyNightToneMapping(denoisedBitmap)
            Log.d(TAG, "processSoftwareNight: 色调映射完成")

            // Step 5: 最终处理
            updateProgress(NightProcessingStage.FINALIZING, 0.95f, "完成处理中...")
            onProgress?.invoke(_processingProgress.value!!)

            // 回收中间Bitmap
            if (temporalDenoised !== denoisedBitmap) temporalDenoised.recycle()
            if (spatialDenoised !== denoisedBitmap) spatialDenoised.recycle()
            alignedFrames.forEach { if (it !== referenceFrame && !it.isRecycled) it.recycle() }

            updateProgress(NightProcessingStage.FINALIZING, 1.0f, "处理完成")
            onProgress?.invoke(_processingProgress.value!!)

            val processingTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "processSoftwareNight: 软件夜景处理完成，耗时=${processingTime}ms")

            NightResult(
                bitmap = toneMapped,
                processingTimeMs = processingTime,
                isHardwareNight = false,
                frameCount = frames.size,
                success = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "processSoftwareNight: 处理失败", e)
            NightResult(
                bitmap = frames.firstOrNull() ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
                processingTimeMs = System.currentTimeMillis() - startTime,
                isHardwareNight = false,
                frameCount = frames.size,
                success = false,
                errorMessage = e.message
            )
        } finally {
            _isProcessing.value = false
            _processingProgress.value = null
        }
    }

    /**
     * 更新处理进度
     */
    private fun updateProgress(stage: NightProcessingStage, progress: Float, message: String) {
        _processingProgress.value = NightProcessingProgress(stage, progress, message)
    }

    // ==================== 帧对齐算法 ====================

    /**
     * 帧对齐 - 块匹配法（批量像素操作优化版）
     *
     * 使用块匹配算法对齐帧，校正帧间位移
     * 简化实现：计算全局位移并应用
     * 性能优化：使用批量getPixels/setPixels代替逐像素操作
     *
     * @param frame 待对齐的帧
     * @param reference 参考帧
     * @return 对齐后的帧
     */
    private fun alignFrame(frame: Bitmap, reference: Bitmap): Bitmap {
        val width = frame.width
        val height = frame.height
        val pixelCount = width * height

        // 计算全局位移（简化：使用中心区域块匹配）
        val (offsetX, offsetY) = calculateGlobalOffset(frame, reference)

        if (offsetX == 0 && offsetY == 0) {
            return frame.copy(frame.config ?: Bitmap.Config.ARGB_8888, true)
        }

        // 使用批量像素操作（比逐像素快10倍以上）
        val srcPixels = IntArray(pixelCount)
        frame.getPixels(srcPixels, 0, width, 0, 0, width, height)

        val dstPixels = IntArray(pixelCount)

        // 应用位移（批量处理）
        for (y in 0 until height) {
            for (x in 0 until width) {
                val srcX = (x + offsetX).coerceIn(0, width - 1)
                val srcY = (y + offsetY).coerceIn(0, height - 1)
                dstPixels[y * width + x] = srcPixels[srcY * width + srcX]
            }
        }

        val aligned = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        aligned.setPixels(dstPixels, 0, width, 0, 0, width, height)

        return aligned
    }

    /**
     * 计算全局位移（批量像素操作优化版）
     *
     * 使用块匹配在搜索范围内找到最佳位移
     * 性能优化：预加载中心区域像素到数组
     *
     * @param frame 待对齐帧
     * @param reference 参考帧
     * @return 位移(offsetX, offsetY)
     */
    private fun calculateGlobalOffset(frame: Bitmap, reference: Bitmap): Pair<Int, Int> {
        val width = frame.width
        val height = frame.height

        // 使用中心区域进行匹配
        val centerX = width / 2
        val centerY = height / 2
        val blockSize = ALIGNMENT_BLOCK_SIZE

        // 预加载中心区域像素（批量操作优化）
        val blockWidth = blockSize * 2 + 1 + ALIGNMENT_SEARCH_RANGE * 2
        val blockHeight = blockSize * 2 + 1 + ALIGNMENT_SEARCH_RANGE * 2
        val startX = (centerX - blockSize - ALIGNMENT_SEARCH_RANGE).coerceIn(0, width - blockWidth)
        val startY = (centerY - blockSize - ALIGNMENT_SEARCH_RANGE).coerceIn(0, height - blockHeight)

        val refBlockPixels = IntArray(blockWidth * blockHeight)
        val frameBlockPixels = IntArray(blockWidth * blockHeight)

        reference.getPixels(refBlockPixels, 0, blockWidth, startX, startY, blockWidth, blockHeight)
        frame.getPixels(frameBlockPixels, 0, blockWidth, startX, startY, blockWidth, blockHeight)

        // 预计算灰度值
        val refGray = IntArray(blockWidth * blockHeight) { i ->
            val pixel = refBlockPixels[i]
            (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
        }
        val frameGray = IntArray(blockWidth * blockHeight) { i ->
            val pixel = frameBlockPixels[i]
            (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
        }

        var bestOffsetX = 0
        var bestOffsetY = 0
        var minSAD = Long.MAX_VALUE                                             // Sum of Absolute Differences

        val localCenterX = centerX - startX
        val localCenterY = centerY - startY

        // 在搜索范围内找最佳位移
        for (dy in -ALIGNMENT_SEARCH_RANGE..ALIGNMENT_SEARCH_RANGE) {
            for (dx in -ALIGNMENT_SEARCH_RANGE..ALIGNMENT_SEARCH_RANGE) {
                var sad = 0L

                // 计算块的SAD（使用预加载的灰度数组）
                for (by in -blockSize..blockSize) {
                    for (bx in -blockSize..blockSize) {
                        val refLocalX = localCenterX + bx
                        val refLocalY = localCenterY + by
                        val frameLocalX = localCenterX + bx + dx
                        val frameLocalY = localCenterY + by + dy

                        if (refLocalX in 0 until blockWidth && refLocalY in 0 until blockHeight &&
                            frameLocalX in 0 until blockWidth && frameLocalY in 0 until blockHeight) {
                            val refIdx = refLocalY * blockWidth + refLocalX
                            val frameIdx = frameLocalY * blockWidth + frameLocalX
                            sad += abs(refGray[refIdx] - frameGray[frameIdx])
                        }
                    }
                }

                if (sad < minSAD) {
                    minSAD = sad
                    bestOffsetX = dx
                    bestOffsetY = dy
                }
            }
        }

        Log.d(TAG, "calculateGlobalOffset: 最佳位移 ($bestOffsetX, $bestOffsetY), SAD=$minSAD")
        return Pair(bestOffsetX, bestOffsetY)
    }

    // ==================== 时域降噪 ====================

    /**
     * 时域降噪 - 帧堆栈加权平均（批量像素操作优化版）
     *
     * 对多帧进行加权平均，利用帧间冗余信息减少噪声
     * 权重基于像素与参考帧的相似度
     * 性能优化：预加载所有帧像素到数组，批量处理
     *
     * @param frames 对齐后的帧列表
     * @return 时域降噪后的图像
     */
    private fun applyTemporalDenoising(frames: List<Bitmap>): Bitmap {
        if (frames.isEmpty()) {
            throw IllegalArgumentException("帧列表不能为空")
        }
        if (frames.size == 1) {
            return frames[0].copy(frames[0].config ?: Bitmap.Config.ARGB_8888, true)
        }

        val width = frames[0].width
        val height = frames[0].height
        val pixelCount = width * height

        // 预加载所有帧的像素数组（批量操作优化）
        val framePixels = frames.map { frame ->
            IntArray(pixelCount).also { pixels ->
                frame.getPixels(pixels, 0, width, 0, 0, width, height)
            }
        }

        val referenceIndex = frames.size / 2                                    // 参考帧索引
        val refPixels = framePixels[referenceIndex]

        val resultPixels = IntArray(pixelCount)

        // 批量处理每个像素
        for (i in 0 until pixelCount) {
            val refPixel = refPixels[i]
            val refR = Color.red(refPixel)
            val refG = Color.green(refPixel)
            val refB = Color.blue(refPixel)

            var totalWeight = 0f
            var sumR = 0f
            var sumG = 0f
            var sumB = 0f

            for (frameIdx in framePixels.indices) {
                val pixel = framePixels[frameIdx][i]
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                // 计算与参考帧的相似度权重（颜色差异越小，权重越大）
                val colorDiff = sqrt(
                    ((r - refR) * (r - refR) +
                     (g - refG) * (g - refG) +
                     (b - refB) * (b - refB)).toFloat()
                )
                val weight = exp(-colorDiff / 50f)                              // 高斯权重

                sumR += r * weight
                sumG += g * weight
                sumB += b * weight
                totalWeight += weight
            }

            // 归一化
            if (totalWeight > 0) {
                val finalR = (sumR / totalWeight).toInt().coerceIn(0, 255)
                val finalG = (sumG / totalWeight).toInt().coerceIn(0, 255)
                val finalB = (sumB / totalWeight).toInt().coerceIn(0, 255)
                resultPixels[i] = Color.rgb(finalR, finalG, finalB)
            } else {
                resultPixels[i] = refPixel
            }
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(resultPixels, 0, width, 0, 0, width, height)

        return result
    }

    // ==================== 空域降噪（双边滤波） ====================

    /**
     * 双边滤波 - 边缘保持降噪（批量像素操作优化版）
     *
     * 同时考虑空间距离和颜色相似度的滤波器
     * 可以有效去除噪声同时保留边缘
     * 性能优化：
     * 1. 对大图进行缩放处理（防止OOM/ANR）
     * 2. 使用批量getPixels/setPixels代替逐像素操作
     *
     * @param bitmap 输入图像
     * @return 降噪后的图像
     */
    private fun applyBilateralFilter(bitmap: Bitmap): Bitmap {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height

        // 性能优化：对大图进行缩放处理（防止ANR）
        val maxProcessSize = 1280                                               // 双边滤波计算量大，使用较小尺寸
        val needsDownscale = originalWidth > maxProcessSize || originalHeight > maxProcessSize
        val workingBitmap = if (needsDownscale) {
            val scale = maxProcessSize.toFloat() / max(originalWidth, originalHeight)
            val scaledWidth = (originalWidth * scale).toInt()
            val scaledHeight = (originalHeight * scale).toInt()
            Log.d(TAG, "applyBilateralFilter: 缩放至 ${scaledWidth}x${scaledHeight} 进行处理")
            Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        } else {
            bitmap
        }

        val width = workingBitmap.width
        val height = workingBitmap.height
        val pixelCount = width * height

        // 批量加载像素数据
        val srcPixels = IntArray(pixelCount)
        workingBitmap.getPixels(srcPixels, 0, width, 0, 0, width, height)

        // 预计算RGB分量（避免重复Color.red/green/blue调用）
        val srcR = IntArray(pixelCount) { Color.red(srcPixels[it]) }
        val srcG = IntArray(pixelCount) { Color.green(srcPixels[it]) }
        val srcB = IntArray(pixelCount) { Color.blue(srcPixels[it]) }

        val resultPixels = IntArray(pixelCount)

        val halfKernel = BILATERAL_KERNEL_SIZE / 2
        val sigmaSpace2 = 2 * BILATERAL_SIGMA_SPACE * BILATERAL_SIGMA_SPACE
        val sigmaColor2 = 2 * BILATERAL_SIGMA_COLOR * BILATERAL_SIGMA_COLOR

        // 批量处理每个像素
        for (y in 0 until height) {
            for (x in 0 until width) {
                val centerIdx = y * width + x
                val centerR = srcR[centerIdx]
                val centerG = srcG[centerIdx]
                val centerB = srcB[centerIdx]

                var sumR = 0f
                var sumG = 0f
                var sumB = 0f
                var sumWeight = 0f

                // 遍历滤波核
                for (ky in -halfKernel..halfKernel) {
                    for (kx in -halfKernel..halfKernel) {
                        val nx = (x + kx).coerceIn(0, width - 1)
                        val ny = (y + ky).coerceIn(0, height - 1)
                        val neighborIdx = ny * width + nx

                        val neighborR = srcR[neighborIdx]
                        val neighborG = srcG[neighborIdx]
                        val neighborB = srcB[neighborIdx]

                        // 空间权重
                        val spatialDist2 = (kx * kx + ky * ky).toFloat()
                        val spatialWeight = exp(-spatialDist2 / sigmaSpace2)

                        // 颜色权重
                        val colorDist2 = ((neighborR - centerR) * (neighborR - centerR) +
                                         (neighborG - centerG) * (neighborG - centerG) +
                                         (neighborB - centerB) * (neighborB - centerB)).toFloat()
                        val colorWeight = exp(-colorDist2 / sigmaColor2)

                        // 组合权重
                        val weight = spatialWeight * colorWeight

                        sumR += neighborR * weight
                        sumG += neighborG * weight
                        sumB += neighborB * weight
                        sumWeight += weight
                    }
                }

                // 归一化
                if (sumWeight > 0) {
                    val finalR = (sumR / sumWeight).toInt().coerceIn(0, 255)
                    val finalG = (sumG / sumWeight).toInt().coerceIn(0, 255)
                    val finalB = (sumB / sumWeight).toInt().coerceIn(0, 255)
                    resultPixels[centerIdx] = Color.rgb(finalR, finalG, finalB)
                } else {
                    resultPixels[centerIdx] = srcPixels[centerIdx]
                }
            }
        }

        // 创建结果Bitmap
        val processedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        processedBitmap.setPixels(resultPixels, 0, width, 0, 0, width, height)

        // 如果进行了缩放，放大回原尺寸
        return if (needsDownscale) {
            val upscaled = Bitmap.createScaledBitmap(processedBitmap, originalWidth, originalHeight, true)
            processedBitmap.recycle()
            if (workingBitmap !== bitmap) {
                workingBitmap.recycle()
            }
            upscaled
        } else {
            processedBitmap
        }
    }

    // ==================== 色调映射 ====================

    /**
     * 夜景色调映射
     *
     * 针对夜景的色调映射，增强暗部细节并控制高光
     * 使用自适应的Reinhard算法变体
     *
     * @param bitmap 输入图像
     * @return 色调映射后的图像
     */
    private fun applyNightToneMapping(bitmap: Bitmap): Bitmap {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height

        // 性能优化：对大图进行缩放处理
        val maxProcessSize = 1920
        val needsDownscale = originalWidth > maxProcessSize || originalHeight > maxProcessSize
        val workingBitmap = if (needsDownscale) {
            val scale = maxProcessSize.toFloat() / max(originalWidth, originalHeight)
            val scaledWidth = (originalWidth * scale).toInt()
            val scaledHeight = (originalHeight * scale).toInt()
            Log.d(TAG, "applyNightToneMapping: 缩放至 ${scaledWidth}x${scaledHeight} 进行处理")
            Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        } else {
            bitmap
        }

        val width = workingBitmap.width
        val height = workingBitmap.height
        val pixelCount = width * height

        // 使用批量像素操作（比逐像素操作快10倍以上）
        val pixels = IntArray(pixelCount)
        workingBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // 计算平均亮度
        var totalLuminance = 0f
        for (pixel in pixels) {
            totalLuminance += (0.299f * Color.red(pixel) +
                            0.587f * Color.green(pixel) +
                            0.114f * Color.blue(pixel)) / 255f
        }

        val avgLuminance = totalLuminance / pixelCount
        val key = 0.18f / max(avgLuminance, 0.001f)                             // 场景键值
        val nightBoost = 1.5f                                                    // 夜景提亮系数
        val saturationBoost = 1.2f                                               // 饱和度增强

        // 批量应用色调映射
        for (i in pixels.indices) {
            val pixel = pixels[i]
            var r = Color.red(pixel) / 255f
            var g = Color.green(pixel) / 255f
            var b = Color.blue(pixel) / 255f

            // 暗部提升
            val luminance = 0.299f * r + 0.587f * g + 0.114f * b
            val shadowBoost = if (luminance < 0.3f) {
                1f + (0.3f - luminance) * nightBoost
            } else {
                1f
            }

            // 缩放并应用场景键值
            r = r * key * shadowBoost
            g = g * key * shadowBoost
            b = b * key * shadowBoost

            // Reinhard色调映射
            r = r / (1f + r)
            g = g / (1f + g)
            b = b / (1f + b)

            // 伽马校正
            r = r.pow(1f / 2.2f)
            g = g.pow(1f / 2.2f)
            b = b.pow(1f / 2.2f)

            // 饱和度增强
            val gray = 0.299f * r + 0.587f * g + 0.114f * b
            r = gray + (r - gray) * saturationBoost
            g = gray + (g - gray) * saturationBoost
            b = gray + (b - gray) * saturationBoost

            // 写入结果数组
            pixels[i] = Color.rgb(
                (r * 255).toInt().coerceIn(0, 255),
                (g * 255).toInt().coerceIn(0, 255),
                (b * 255).toInt().coerceIn(0, 255)
            )
        }

        // 创建结果Bitmap
        val processedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        processedBitmap.setPixels(pixels, 0, width, 0, 0, width, height)

        // 如果进行了缩放，放大回原尺寸
        return if (needsDownscale) {
            val upscaled = Bitmap.createScaledBitmap(processedBitmap, originalWidth, originalHeight, true)
            processedBitmap.recycle()
            if (workingBitmap !== bitmap) {
                workingBitmap.recycle()
            }
            upscaled
        } else {
            processedBitmap
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 混合两个Bitmap（批量像素操作优化版）
     *
     * 使用批量getPixels/setPixels代替逐像素操作
     * 性能提升约10倍
     *
     * @param bitmap1 第一个Bitmap
     * @param bitmap2 第二个Bitmap
     * @param weight1 第一个Bitmap的权重
     * @return 混合后的Bitmap
     */
    private fun blendBitmaps(bitmap1: Bitmap, bitmap2: Bitmap, weight1: Float): Bitmap {
        val width = bitmap1.width
        val height = bitmap1.height
        val pixelCount = width * height
        val weight2 = 1f - weight1

        // 批量加载像素数据
        val pixels1 = IntArray(pixelCount)
        val pixels2 = IntArray(pixelCount)
        bitmap1.getPixels(pixels1, 0, width, 0, 0, width, height)
        bitmap2.getPixels(pixels2, 0, width, 0, 0, width, height)

        val resultPixels = IntArray(pixelCount)

        // 批量处理混合
        for (i in 0 until pixelCount) {
            val pixel1 = pixels1[i]
            val pixel2 = pixels2[i]

            val r = (Color.red(pixel1) * weight1 + Color.red(pixel2) * weight2).toInt().coerceIn(0, 255)
            val g = (Color.green(pixel1) * weight1 + Color.green(pixel2) * weight2).toInt().coerceIn(0, 255)
            val b = (Color.blue(pixel1) * weight1 + Color.blue(pixel2) * weight2).toInt().coerceIn(0, 255)

            resultPixels[i] = Color.rgb(r, g, b)
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(resultPixels, 0, width, 0, 0, width, height)

        return result
    }

    // ==================== 单帧夜景增强 ====================

    /**
     * 单帧夜景增强
     *
     * 当无法获取多帧时使用，仅进行降噪和增强
     *
     * @param bitmap 输入图像
     * @param onProgress 进度回调
     * @return 增强后的图像
     */
    suspend fun enhanceSingleFrame(
        bitmap: Bitmap,
        onProgress: ((NightProcessingProgress) -> Unit)? = null
    ): NightResult = withContext(Dispatchers.Default) {
        processSoftwareNight(listOf(bitmap), onProgress)
    }

    // ==================== 资源管理 ====================

    /**
     * 释放资源
     */
    suspend fun release() = mutex.withLock {
        Log.d(TAG, "release: 释放夜景处理器资源")
        extensionsManager = null
        cameraProvider = null
        _supportStatus = null
        _processingProgress.value = null
        _isProcessing.value = false
    }

    /**
     * 检查是否已初始化
     */
    fun isInitialized(): Boolean = extensionsManager != null
}
