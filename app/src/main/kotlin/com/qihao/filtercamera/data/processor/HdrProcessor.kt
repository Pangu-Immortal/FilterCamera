/**
 * HdrProcessor.kt - HDR高动态范围处理器
 *
 * 提供HDR拍照功能的完整实现：
 * 1. 硬件HDR：使用CameraX Extensions实现设备原生HDR
 * 2. 软件HDR：当硬件不支持时，使用多帧曝光融合算法
 *
 * 设计原则：
 * - 优先使用硬件HDR（性能更好、质量更高）
 * - 自动降级到软件HDR（兼容性更广）
 * - 线程安全的异步处理
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.data.processor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import com.qihao.filtercamera.domain.model.HdrMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * HDR处理结果
 *
 * @param bitmap 处理后的Bitmap
 * @param processingTimeMs 处理耗时（毫秒）
 * @param isHardwareHdr 是否使用硬件HDR
 * @param success 处理是否成功
 * @param errorMessage 错误信息
 */
data class HdrResult(
    val bitmap: Bitmap,
    val processingTimeMs: Long,
    val isHardwareHdr: Boolean,
    val success: Boolean = true,
    val errorMessage: String? = null
)

/**
 * HDR扩展支持状态
 */
data class HdrSupportStatus(
    val isHardwareHdrAvailable: Boolean,                                       // 硬件HDR是否可用
    val isSoftwareHdrAvailable: Boolean = true,                                // 软件HDR始终可用
    val supportedCameraLens: List<Int> = emptyList(),                          // 支持HDR的摄像头
    val message: String                                                         // 状态描述
)

/**
 * HDR高动态范围处理器
 *
 * 整合硬件HDR和软件HDR的完整实现
 */
@Singleton
class HdrProcessor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "HdrProcessor"

        // 软件HDR曝光包围参数
        private const val HDR_UNDEREXPOSED_EV = -2.0f                           // 欠曝EV
        private const val HDR_NORMAL_EV = 0.0f                                  // 正常EV
        private const val HDR_OVEREXPOSED_EV = 2.0f                             // 过曝EV

        // Mertens曝光融合权重参数
        private const val CONTRAST_WEIGHT = 1.0f                                // 对比度权重
        private const val SATURATION_WEIGHT = 1.0f                              // 饱和度权重
        private const val EXPOSURE_WEIGHT = 1.0f                                // 曝光权重
    }

    // 线程安全锁
    private val mutex = Mutex()

    // CameraX Extensions管理器（延迟初始化）
    private var extensionsManager: ExtensionsManager? = null
    private var cameraProvider: ProcessCameraProvider? = null

    // HDR支持状态缓存
    private var _supportStatus: HdrSupportStatus? = null
    val supportStatus: HdrSupportStatus? get() = _supportStatus

    // ==================== 初始化 ====================

    /**
     * 初始化ExtensionsManager
     *
     * 必须在使用硬件HDR前调用
     * 使用suspendCancellableCoroutine将回调转换为挂起函数
     *
     * @return 初始化是否成功
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.Main) {
        mutex.withLock {
            try {
                Log.d(TAG, "initialize: 开始初始化ExtensionsManager")

                // 获取CameraProvider
                cameraProvider = getCameraProvider()
                Log.d(TAG, "initialize: CameraProvider获取成功")

                // 获取ExtensionsManager
                extensionsManager = getExtensionsManager(cameraProvider!!)
                Log.d(TAG, "initialize: ExtensionsManager获取成功")

                // 检测HDR支持状态
                _supportStatus = checkHdrSupport()
                Log.i(TAG, "initialize: 初始化完成 - ${_supportStatus?.message}")

                true
            } catch (e: Exception) {
                Log.e(TAG, "initialize: 初始化失败", e)
                _supportStatus = HdrSupportStatus(
                    isHardwareHdrAvailable = false,
                    message = "初始化失败: ${e.message}"
                )
                false
            }
        }
    }

    /**
     * 获取CameraProvider（挂起函数）
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
     * 获取ExtensionsManager（挂起函数）
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

    // ==================== HDR支持检测 ====================

    /**
     * 检测HDR扩展支持状态
     *
     * 检查前后摄像头是否支持硬件HDR扩展
     *
     * @return HDR支持状态
     */
    private fun checkHdrSupport(): HdrSupportStatus {
        val extensions = extensionsManager ?: return HdrSupportStatus(
            isHardwareHdrAvailable = false,
            message = "ExtensionsManager未初始化"
        )

        val supportedLenses = mutableListOf<Int>()

        // 检查后置摄像头HDR支持
        val backSelector = CameraSelector.DEFAULT_BACK_CAMERA
        val backHdrSupported = try {
            extensions.isExtensionAvailable(backSelector, ExtensionMode.HDR)
        } catch (e: Exception) {
            Log.w(TAG, "checkHdrSupport: 后置摄像头HDR检测失败", e)
            false
        }
        if (backHdrSupported) {
            supportedLenses.add(CameraSelector.LENS_FACING_BACK)
            Log.d(TAG, "checkHdrSupport: 后置摄像头支持硬件HDR")
        }

        // 检查前置摄像头HDR支持
        val frontSelector = CameraSelector.DEFAULT_FRONT_CAMERA
        val frontHdrSupported = try {
            extensions.isExtensionAvailable(frontSelector, ExtensionMode.HDR)
        } catch (e: Exception) {
            Log.w(TAG, "checkHdrSupport: 前置摄像头HDR检测失败", e)
            false
        }
        if (frontHdrSupported) {
            supportedLenses.add(CameraSelector.LENS_FACING_FRONT)
            Log.d(TAG, "checkHdrSupport: 前置摄像头支持硬件HDR")
        }

        val isHardwareAvailable = supportedLenses.isNotEmpty()
        val message = if (isHardwareAvailable) {
            "硬件HDR可用 (${supportedLenses.size}个摄像头支持)"
        } else {
            "硬件HDR不可用，将使用软件HDR"
        }

        return HdrSupportStatus(
            isHardwareHdrAvailable = isHardwareAvailable,
            supportedCameraLens = supportedLenses,
            message = message
        )
    }

    /**
     * 检查指定摄像头是否支持硬件HDR
     *
     * @param lensFacing 摄像头方向 (CameraSelector.LENS_FACING_BACK/FRONT)
     * @return 是否支持硬件HDR
     */
    fun isHardwareHdrAvailable(lensFacing: Int): Boolean {
        return _supportStatus?.supportedCameraLens?.contains(lensFacing) == true
    }

    // ==================== HDR CameraSelector ====================

    /**
     * 获取HDR扩展的CameraSelector
     *
     * 当硬件支持HDR时，返回启用HDR扩展的CameraSelector
     * 当硬件不支持时，返回普通CameraSelector（使用软件HDR处理）
     *
     * @param baseCameraSelector 基础CameraSelector
     * @return 可能启用HDR的CameraSelector
     */
    fun getHdrCameraSelector(baseCameraSelector: CameraSelector): CameraSelector {
        val extensions = extensionsManager ?: run {
            Log.w(TAG, "getHdrCameraSelector: ExtensionsManager未初始化，返回普通选择器")
            return baseCameraSelector
        }

        return try {
            if (extensions.isExtensionAvailable(baseCameraSelector, ExtensionMode.HDR)) {
                val hdrSelector = extensions.getExtensionEnabledCameraSelector(
                    baseCameraSelector,
                    ExtensionMode.HDR
                )
                Log.d(TAG, "getHdrCameraSelector: 返回硬件HDR CameraSelector")
                hdrSelector
            } else {
                Log.d(TAG, "getHdrCameraSelector: 硬件HDR不可用，返回普通选择器")
                baseCameraSelector
            }
        } catch (e: Exception) {
            Log.e(TAG, "getHdrCameraSelector: 获取HDR选择器失败", e)
            baseCameraSelector
        }
    }

    // ==================== 软件HDR（曝光融合） ====================

    /**
     * 软件HDR处理 - Mertens曝光融合算法
     *
     * 当硬件HDR不可用时，使用此方法处理多帧曝光图像
     * 实现原理：
     * 1. 对每帧计算对比度、饱和度、曝光权重
     * 2. 归一化权重并融合像素
     * 3. 应用色调映射增强动态范围
     *
     * @param frames 不同曝光的图像帧列表（建议3帧：欠曝/正常/过曝）
     * @return HDR处理结果
     */
    suspend fun processSoftwareHdr(frames: List<Bitmap>): HdrResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()

        try {
            Log.d(TAG, "processSoftwareHdr: 开始软件HDR处理，帧数=${frames.size}")

            if (frames.isEmpty()) {
                throw IllegalArgumentException("至少需要1帧图像")
            }

            if (frames.size == 1) {
                // 单帧：应用局部色调映射增强
                Log.d(TAG, "processSoftwareHdr: 单帧模式，应用色调映射增强")
                val enhanced = applyLocalToneMapping(frames[0])
                return@withContext HdrResult(
                    bitmap = enhanced,
                    processingTimeMs = System.currentTimeMillis() - startTime,
                    isHardwareHdr = false,
                    success = true
                )
            }

            // 多帧曝光融合（Mertens算法）
            val width = frames[0].width
            val height = frames[0].height
            val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            // 计算每帧的权重图
            val weightMaps = frames.map { frame -> calculateWeightMap(frame) }

            // 融合像素
            for (y in 0 until height) {
                for (x in 0 until width) {
                    var totalWeight = 0f
                    var r = 0f
                    var g = 0f
                    var b = 0f

                    for (i in frames.indices) {
                        val weight = weightMaps[i][y * width + x]
                        val pixel = frames[i].getPixel(x, y)

                        r += Color.red(pixel) * weight
                        g += Color.green(pixel) * weight
                        b += Color.blue(pixel) * weight
                        totalWeight += weight
                    }

                    // 归一化并限制范围
                    if (totalWeight > 0) {
                        r = (r / totalWeight).coerceIn(0f, 255f)
                        g = (g / totalWeight).coerceIn(0f, 255f)
                        b = (b / totalWeight).coerceIn(0f, 255f)
                    }

                    resultBitmap.setPixel(x, y, Color.rgb(r.toInt(), g.toInt(), b.toInt()))
                }
            }

            // 应用局部色调映射增强
            val finalResult = applyLocalToneMapping(resultBitmap)

            val processingTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "processSoftwareHdr: 软件HDR处理完成，耗时=${processingTime}ms")

            HdrResult(
                bitmap = finalResult,
                processingTimeMs = processingTime,
                isHardwareHdr = false,
                success = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "processSoftwareHdr: 处理失败", e)
            HdrResult(
                bitmap = frames.firstOrNull() ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
                processingTimeMs = System.currentTimeMillis() - startTime,
                isHardwareHdr = false,
                success = false,
                errorMessage = e.message
            )
        }
    }

    /**
     * 计算像素权重图（Mertens算法）
     *
     * 对每个像素计算三个质量指标的加权乘积：
     * - 对比度：使用Laplacian滤波器
     * - 饱和度：RGB标准差
     * - 曝光良好度：高斯曲线，0.5为最佳
     *
     * @param bitmap 输入图像
     * @return 权重数组
     */
    private fun calculateWeightMap(bitmap: Bitmap): FloatArray {
        val width = bitmap.width
        val height = bitmap.height
        val weights = FloatArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel) / 255f
                val g = Color.green(pixel) / 255f
                val b = Color.blue(pixel) / 255f

                // 计算对比度（简化的Laplacian）
                val contrast = calculateLocalContrast(bitmap, x, y)

                // 计算饱和度（RGB标准差）
                val mean = (r + g + b) / 3f
                val saturation = kotlin.math.sqrt(
                    ((r - mean).pow(2) + (g - mean).pow(2) + (b - mean).pow(2)) / 3f
                )

                // 计算曝光良好度（高斯，0.5为最佳）
                val luminance = 0.299f * r + 0.587f * g + 0.114f * b
                val exposure = kotlin.math.exp(-0.5f * ((luminance - 0.5f) / 0.2f).pow(2))

                // 综合权重（三个指标的加权乘积）
                val weight = (contrast.pow(CONTRAST_WEIGHT) *
                             saturation.pow(SATURATION_WEIGHT) *
                             exposure.pow(EXPOSURE_WEIGHT))
                             .coerceIn(0.0001f, 1f)                              // 避免零权重

                weights[y * width + x] = weight
            }
        }

        return weights
    }

    /**
     * 计算局部对比度（3x3 Laplacian）
     */
    private fun calculateLocalContrast(bitmap: Bitmap, x: Int, y: Int): Float {
        val width = bitmap.width
        val height = bitmap.height

        // 3x3 Laplacian核
        val kernel = arrayOf(
            intArrayOf(0, 1, 0),
            intArrayOf(1, -4, 1),
            intArrayOf(0, 1, 0)
        )

        var sum = 0f
        for (dy in -1..1) {
            for (dx in -1..1) {
                val nx = (x + dx).coerceIn(0, width - 1)
                val ny = (y + dy).coerceIn(0, height - 1)
                val pixel = bitmap.getPixel(nx, ny)
                val gray = 0.299f * Color.red(pixel) + 0.587f * Color.green(pixel) + 0.114f * Color.blue(pixel)
                sum += gray * kernel[dy + 1][dx + 1]
            }
        }

        return kotlin.math.abs(sum / 255f)                                       // 归一化
    }

    /**
     * 应用局部色调映射增强动态范围
     *
     * 使用简化的Reinhard色调映射算法：
     * L_out = L_in / (1 + L_in)
     *
     * @param bitmap 输入图像
     * @return 色调映射后的图像
     */
    private fun applyLocalToneMapping(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        // 计算平均亮度用于自适应调整
        var totalLuminance = 0f
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                totalLuminance += (0.299f * Color.red(pixel) +
                                   0.587f * Color.green(pixel) +
                                   0.114f * Color.blue(pixel)) / 255f
            }
        }
        val avgLuminance = totalLuminance / (width * height)
        val key = 0.18f / max(avgLuminance, 0.001f)                              // 场景亮度键值

        // 应用色调映射
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                var r = Color.red(pixel) / 255f
                var g = Color.green(pixel) / 255f
                var b = Color.blue(pixel) / 255f

                // 缩放到场景亮度
                r *= key
                g *= key
                b *= key

                // Reinhard色调映射
                r = r / (1f + r)
                g = g / (1f + g)
                b = b / (1f + b)

                // 伽马校正
                r = r.pow(1f / 2.2f)
                g = g.pow(1f / 2.2f)
                b = b.pow(1f / 2.2f)

                // 限制范围并写入
                result.setPixel(x, y, Color.rgb(
                    (r * 255).toInt().coerceIn(0, 255),
                    (g * 255).toInt().coerceIn(0, 255),
                    (b * 255).toInt().coerceIn(0, 255)
                ))
            }
        }

        return result
    }

    // ==================== 单帧HDR增强 ====================

    /**
     * 单帧HDR增强（当无法获取多帧时使用）
     *
     * 使用局部对比度增强和色调映射来模拟HDR效果
     * 适用于静态图像后处理
     *
     * @param bitmap 输入图像
     * @return 增强后的图像
     */
    suspend fun enhanceSingleFrame(bitmap: Bitmap): HdrResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()

        try {
            Log.d(TAG, "enhanceSingleFrame: 开始单帧HDR增强")

            // 分离高光和暗部
            val width = bitmap.width
            val height = bitmap.height
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            for (y in 0 until height) {
                for (x in 0 until width) {
                    val pixel = bitmap.getPixel(x, y)
                    var r = Color.red(pixel) / 255f
                    var g = Color.green(pixel) / 255f
                    var b = Color.blue(pixel) / 255f

                    val luminance = 0.299f * r + 0.587f * g + 0.114f * b

                    // 暗部提亮（gamma < 1）
                    val shadowBoost = if (luminance < 0.3f) {
                        luminance.pow(0.7f) / max(luminance, 0.001f)
                    } else 1f

                    // 高光压缩（S曲线）
                    val highlightCompress = if (luminance > 0.7f) {
                        val x = (luminance - 0.7f) / 0.3f
                        1f - 0.3f * x * x                                        // 柔和压缩
                    } else 1f

                    r = (r * shadowBoost * highlightCompress).coerceIn(0f, 1f)
                    g = (g * shadowBoost * highlightCompress).coerceIn(0f, 1f)
                    b = (b * shadowBoost * highlightCompress).coerceIn(0f, 1f)

                    result.setPixel(x, y, Color.rgb(
                        (r * 255).toInt(),
                        (g * 255).toInt(),
                        (b * 255).toInt()
                    ))
                }
            }

            val processingTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "enhanceSingleFrame: 处理完成，耗时=${processingTime}ms")

            HdrResult(
                bitmap = result,
                processingTimeMs = processingTime,
                isHardwareHdr = false,
                success = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "enhanceSingleFrame: 处理失败", e)
            HdrResult(
                bitmap = bitmap,
                processingTimeMs = System.currentTimeMillis() - startTime,
                isHardwareHdr = false,
                success = false,
                errorMessage = e.message
            )
        }
    }

    // ==================== 资源管理 ====================

    /**
     * 释放资源
     */
    suspend fun release() = mutex.withLock {
        Log.d(TAG, "release: 释放HDR处理器资源")
        extensionsManager = null
        cameraProvider = null
        _supportStatus = null
    }

    /**
     * 检查是否已初始化
     */
    fun isInitialized(): Boolean = extensionsManager != null
}
