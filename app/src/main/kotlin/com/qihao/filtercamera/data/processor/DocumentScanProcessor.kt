/**
 * DocumentScanProcessor.kt - 文档扫描处理器
 *
 * 实现文档边缘检测、透视校正和多种扫描效果
 * 用于文档模式的自动文档识别和优化
 *
 * 功能：
 * - 边缘检测识别文档边界
 * - 透视变换校正文档
 * - 多种扫描模式（原色/灰度/黑白/自动增强）
 * - 自适应阈值二值化（适合OCR）
 * - 去除阴影和纸张背景杂色
 * - 文档白平衡和色彩校正
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.data.processor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.util.Log
import com.qihao.filtercamera.domain.model.DocumentBounds
import com.qihao.filtercamera.domain.model.NormalizedPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 文档扫描模式枚举
 *
 * 定义不同的扫描处理模式，满足不同场景需求
 *
 * @param displayName 显示名称
 */
enum class DocumentScanMode(val displayName: String) {
    COLOR("彩色"),           // 保留原色，仅增强对比度
    GRAYSCALE("灰度"),       // 转为灰度，适合通用文档
    BLACK_WHITE("黑白"),     // 高对比度黑白，适合文字文档
    AUTO_ENHANCE("自动增强"), // 自动检测并优化（去阴影、白平衡）
    OCR_READY("OCR就绪")     // 为OCR优化的高对比度二值化
}

/**
 * 文档扫描结果
 *
 * @param bitmap 处理后的图像
 * @param processingTimeMs 处理耗时
 * @param mode 使用的扫描模式
 * @param isCorected 是否进行了透视校正
 */
data class DocumentScanResult(
    val bitmap: Bitmap,
    val processingTimeMs: Long,
    val mode: DocumentScanMode,
    val isCorected: Boolean = false
)

/**
 * 文档扫描处理器
 *
 * 使用图像处理算法检测文档边界并优化
 */
@Singleton
class DocumentScanProcessor @Inject constructor() {

    companion object {
        private const val TAG = "DocumentScanProcessor"               // 日志标签
        private const val EDGE_THRESHOLD = 50                         // 边缘检测阈值
        private const val MIN_AREA_RATIO = 0.1f                       // 最小面积比例
        private const val MAX_AREA_RATIO = 0.95f                      // 最大面积比例
    }

    // 检测到的文档边界
    private val _documentBounds = MutableStateFlow<DocumentBounds?>(null)
    val documentBounds: StateFlow<DocumentBounds?> = _documentBounds.asStateFlow()

    // 边界稳定性状态（用于自动锁定和自动捕获）
    private val _isDocumentStable = MutableStateFlow(false)
    val isDocumentStable: StateFlow<Boolean> = _isDocumentStable.asStateFlow()

    // 是否启用检测
    private var isEnabled = false

    // 上次检测时间（用于降低频率）
    private var lastDetectionTime = 0L
    private val detectionInterval = 200L                              // 检测间隔200ms

    // 边界稳定性追踪
    private var consecutiveStableFrames = 0                           // 连续稳定帧计数
    private var lastStableBounds: DocumentBounds? = null              // 上一帧的边界
    private val stableThreshold = 5                                   // 连续稳定帧阈值（5帧≈1秒）
    private val boundsChangeThreshold = 0.03f                         // 边界变化阈值（3%）

    // 自动捕获回调
    private var onAutoCapture: (() -> Unit)? = null

    /**
     * 设置自动捕获回调
     *
     * 当文档边界稳定后自动触发回调
     *
     * @param callback 自动捕获回调
     */
    fun setOnAutoCaptureCallback(callback: (() -> Unit)?) {
        Log.d(TAG, "setOnAutoCaptureCallback: 设置自动捕获回调")
        onAutoCapture = callback
    }

    // 默认扫描模式（由UI选择器设置）
    private var _defaultScanMode = DocumentScanMode.AUTO_ENHANCE

    /**
     * 设置默认扫描模式
     *
     * 由UI选择器调用，设置下次扫描使用的模式
     *
     * @param mode 扫描模式
     */
    fun setDefaultScanMode(mode: DocumentScanMode) {
        Log.d(TAG, "setDefaultScanMode: 设置默认扫描模式 -> ${mode.displayName}")
        _defaultScanMode = mode
    }

    /**
     * 获取当前默认扫描模式
     *
     * @return 当前设置的默认扫描模式
     */
    fun getDefaultScanMode(): DocumentScanMode = _defaultScanMode

    /**
     * 启用文档检测
     */
    fun enable() {
        Log.d(TAG, "enable: 启用文档检测")
        isEnabled = true
    }

    /**
     * 禁用文档检测
     */
    fun disable() {
        Log.d(TAG, "disable: 禁用文档检测")
        isEnabled = false
        _documentBounds.value = null                                  // 清空检测结果
        _isDocumentStable.value = false                               // 重置稳定状态
        consecutiveStableFrames = 0                                   // 重置稳定帧计数
        lastStableBounds = null                                       // 清空上一帧边界
    }

    /**
     * 处理Bitmap进行文档边缘检测
     *
     * @param bitmap 图像
     */
    suspend fun processBitmap(bitmap: Bitmap) = withContext(Dispatchers.Default) {
        if (!isEnabled) return@withContext

        // 控制检测频率
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastDetectionTime < detectionInterval) {
            return@withContext
        }
        lastDetectionTime = currentTime

        try {
            // 缩小图像提高性能
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 320, 240, true)
            val bounds = detectDocumentBounds(scaledBitmap)
            _documentBounds.value = bounds

            // 追踪边界稳定性
            trackBoundaryStability(bounds)

            if (bounds != null) {
                Log.d(TAG, "processBitmap: 检测到文档边界 confidence=${bounds.confidence} stable=$consecutiveStableFrames")
            }
            scaledBitmap.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "processBitmap: 文档检测失败", e)
        }
    }

    /**
     * 追踪边界稳定性
     *
     * 检测边界是否稳定（连续多帧边界变化很小）
     * 稳定后触发自动捕获回调
     *
     * @param currentBounds 当前检测到的边界
     */
    private fun trackBoundaryStability(currentBounds: DocumentBounds?) {
        if (currentBounds == null || currentBounds.confidence < 0.5f) {
            // 未检测到或置信度低，重置稳定计数
            consecutiveStableFrames = 0
            lastStableBounds = null
            _isDocumentStable.value = false
            return
        }

        val previousBounds = lastStableBounds
        if (previousBounds == null) {
            // 第一次检测到高置信度边界
            lastStableBounds = currentBounds
            consecutiveStableFrames = 1
            return
        }

        // 计算边界变化量
        val change = calculateBoundsChange(previousBounds, currentBounds)
        if (change < boundsChangeThreshold) {
            // 边界稳定
            consecutiveStableFrames++
            Log.d(TAG, "trackBoundaryStability: 边界稳定 frames=$consecutiveStableFrames change=$change")

            if (consecutiveStableFrames >= stableThreshold && !_isDocumentStable.value) {
                // 达到稳定阈值，标记为稳定并触发回调
                _isDocumentStable.value = true
                Log.d(TAG, "trackBoundaryStability: 文档边界已稳定，触发自动捕获")
                onAutoCapture?.invoke()
            }
        } else {
            // 边界变化较大，重置计数
            consecutiveStableFrames = 1
            _isDocumentStable.value = false
        }

        // 更新上一帧边界（使用当前边界，允许缓慢漂移）
        lastStableBounds = currentBounds
    }

    /**
     * 计算两个边界之间的变化量
     *
     * @param a 边界A
     * @param b 边界B
     * @return 平均变化量（归一化坐标）
     */
    private fun calculateBoundsChange(a: DocumentBounds, b: DocumentBounds): Float {
        val changes = listOf(
            pointDistance(a.topLeft, b.topLeft),
            pointDistance(a.topRight, b.topRight),
            pointDistance(a.bottomLeft, b.bottomLeft),
            pointDistance(a.bottomRight, b.bottomRight)
        )
        return changes.average().toFloat()
    }

    /**
     * 计算两点之间的欧氏距离（稳定性检测用）
     */
    private fun pointDistance(p1: NormalizedPoint, p2: NormalizedPoint): Float {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        return sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    /**
     * 检测文档边界
     *
     * 使用边缘检测和轮廓分析识别文档
     *
     * @param bitmap 输入图像
     * @return 文档边界，未检测到返回null
     */
    private fun detectDocumentBounds(bitmap: Bitmap): DocumentBounds? {
        val width = bitmap.width
        val height = bitmap.height

        // 转换为灰度图
        val grayPixels = IntArray(width * height)
        bitmap.getPixels(grayPixels, 0, width, 0, 0, width, height)

        // 转为灰度值
        val gray = IntArray(width * height)
        for (i in grayPixels.indices) {
            val pixel = grayPixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            gray[i] = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        }

        // Sobel边缘检测
        val edges = sobelEdgeDetection(gray, width, height)

        // 查找最大矩形轮廓
        val contour = findLargestRectangularContour(edges, width, height)

        return if (contour != null) {
            // 归一化坐标
            DocumentBounds(
                topLeft = NormalizedPoint(contour[0].x / width, contour[0].y / height),
                topRight = NormalizedPoint(contour[1].x / width, contour[1].y / height),
                bottomRight = NormalizedPoint(contour[2].x / width, contour[2].y / height),
                bottomLeft = NormalizedPoint(contour[3].x / width, contour[3].y / height)
            )
        } else {
            // 默认返回一个内缩的矩形区域作为引导
            DocumentBounds(
                topLeft = NormalizedPoint(0.1f, 0.1f),
                topRight = NormalizedPoint(0.9f, 0.1f),
                bottomRight = NormalizedPoint(0.9f, 0.9f),
                bottomLeft = NormalizedPoint(0.1f, 0.9f),
                confidence = 0.3f                                     // 低置信度表示是默认值
            )
        }
    }

    /**
     * Sobel边缘检测
     */
    private fun sobelEdgeDetection(gray: IntArray, width: Int, height: Int): IntArray {
        val edges = IntArray(width * height)

        // Sobel算子
        val gx = intArrayOf(-1, 0, 1, -2, 0, 2, -1, 0, 1)
        val gy = intArrayOf(-1, -2, -1, 0, 0, 0, 1, 2, 1)

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var sumX = 0
                var sumY = 0

                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val idx = (y + ky) * width + (x + kx)
                        val kidx = (ky + 1) * 3 + (kx + 1)
                        sumX += gray[idx] * gx[kidx]
                        sumY += gray[idx] * gy[kidx]
                    }
                }

                val magnitude = sqrt((sumX * sumX + sumY * sumY).toDouble()).toInt()
                edges[y * width + x] = if (magnitude > EDGE_THRESHOLD) 255 else 0
            }
        }

        return edges
    }

    /**
     * 查找最大矩形轮廓
     *
     * @return 四个角点的数组，顺序：左上、右上、右下、左下
     */
    private fun findLargestRectangularContour(
        edges: IntArray,
        width: Int,
        height: Int
    ): Array<PointF>? {
        // 简化实现：使用边缘像素统计找到大致的矩形区域
        var minX = width
        var maxX = 0
        var minY = height
        var maxY = 0
        var edgeCount = 0

        for (y in 0 until height) {
            for (x in 0 until width) {
                if (edges[y * width + x] > 0) {
                    minX = min(minX, x)
                    maxX = max(maxX, x)
                    minY = min(minY, y)
                    maxY = max(maxY, y)
                    edgeCount++
                }
            }
        }

        // 检查是否找到有效区域
        val areaRatio = (maxX - minX).toFloat() * (maxY - minY) / (width * height)
        if (areaRatio < MIN_AREA_RATIO || areaRatio > MAX_AREA_RATIO || edgeCount < 100) {
            return null
        }

        // 返回四个角点
        return arrayOf(
            PointF(minX.toFloat(), minY.toFloat()),                   // 左上
            PointF(maxX.toFloat(), minY.toFloat()),                   // 右上
            PointF(maxX.toFloat(), maxY.toFloat()),                   // 右下
            PointF(minX.toFloat(), maxY.toFloat())                    // 左下
        )
    }

    /**
     * 应用文档扫描效果（旧版兼容）
     *
     * 增强对比度、锐化、可选黑白化
     *
     * @param bitmap 原始图像
     * @param enhanceContrast 是否增强对比度
     * @param blackWhite 是否转为黑白
     * @return 处理后的图像
     */
    suspend fun applyScanEffect(
        bitmap: Bitmap,
        enhanceContrast: Boolean = true,
        blackWhite: Boolean = false
    ): Bitmap = withContext(Dispatchers.Default) {
        val mode = when {
            blackWhite -> DocumentScanMode.BLACK_WHITE
            enhanceContrast -> DocumentScanMode.AUTO_ENHANCE
            else -> DocumentScanMode.COLOR
        }
        applyScanMode(bitmap, mode).bitmap
    }

    /**
     * 应用指定扫描模式
     *
     * 支持多种扫描模式，满足不同场景需求
     *
     * @param bitmap 原始图像
     * @param mode 扫描模式
     * @return 文档扫描结果
     */
    suspend fun applyScanMode(
        bitmap: Bitmap,
        mode: DocumentScanMode
    ): DocumentScanResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "applyScanMode: 应用扫描模式 mode=${mode.displayName}")

        val result = when (mode) {
            DocumentScanMode.COLOR -> applyColorEnhancement(bitmap)
            DocumentScanMode.GRAYSCALE -> applyGrayscale(bitmap)
            DocumentScanMode.BLACK_WHITE -> applyBlackWhite(bitmap)
            DocumentScanMode.AUTO_ENHANCE -> applyAutoEnhance(bitmap)
            DocumentScanMode.OCR_READY -> applyOcrReady(bitmap)
        }

        val processingTime = System.currentTimeMillis() - startTime
        Log.d(TAG, "applyScanMode: 处理完成 耗时=${processingTime}ms")

        DocumentScanResult(
            bitmap = result,
            processingTimeMs = processingTime,
            mode = mode
        )
    }

    /**
     * 彩色增强模式
     *
     * 保留原色，增强对比度和清晰度
     */
    private fun applyColorEnhancement(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint()

        // 增强对比度和饱和度
        val colorMatrix = ColorMatrix()

        // 轻微增强饱和度
        colorMatrix.setSaturation(1.1f)

        // 增强对比度
        val contrast = 1.2f
        val brightness = -15f
        val contrastMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, brightness,
            0f, contrast, 0f, 0f, brightness,
            0f, 0f, contrast, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        ))
        colorMatrix.postConcat(contrastMatrix)

        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        return result
    }

    /**
     * 灰度模式
     *
     * 转为灰度图，适合通用文档
     */
    private fun applyGrayscale(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint()

        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(0f)

        // 增强对比度
        val contrast = 1.25f
        val brightness = -20f
        val contrastMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, brightness,
            0f, contrast, 0f, 0f, brightness,
            0f, 0f, contrast, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        ))
        colorMatrix.postConcat(contrastMatrix)

        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        return result
    }

    /**
     * 黑白模式
     *
     * 高对比度黑白，适合文字文档和复印效果
     */
    private fun applyBlackWhite(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint()

        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(0f)

        // 高对比度
        val contrast = 1.5f
        val brightness = -40f
        val contrastMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, brightness,
            0f, contrast, 0f, 0f, brightness,
            0f, 0f, contrast, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        ))
        colorMatrix.postConcat(contrastMatrix)

        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        return result
    }

    /**
     * 自动增强模式
     *
     * 自动白平衡、去阴影、增强清晰度
     * 智能处理，适合大多数文档
     */
    private fun applyAutoEnhance(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        // 获取像素数据
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // 分析图像亮度分布
        val histogram = IntArray(256)
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val brightness = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            histogram[brightness]++
        }

        // 计算亮度分位数（用于自动调整）
        val totalPixels = width * height
        var lowPercentile = 0
        var highPercentile = 255
        var count = 0

        // 找到 5% 分位数（暗部）
        for (i in 0..255) {
            count += histogram[i]
            if (count >= totalPixels * 0.05) {
                lowPercentile = i
                break
            }
        }

        // 找到 95% 分位数（亮部）
        count = 0
        for (i in 255 downTo 0) {
            count += histogram[i]
            if (count >= totalPixels * 0.05) {
                highPercentile = i
                break
            }
        }

        // 计算拉伸因子
        val range = (highPercentile - lowPercentile).coerceAtLeast(1)
        val scale = 255f / range

        // 应用自动对比度拉伸和白平衡
        val result = IntArray(width * height)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val a = (pixel shr 24) and 0xFF
            var r = (pixel shr 16) and 0xFF
            var g = (pixel shr 8) and 0xFF
            var b = pixel and 0xFF

            // 对比度拉伸
            r = ((r - lowPercentile) * scale).toInt().coerceIn(0, 255)
            g = ((g - lowPercentile) * scale).toInt().coerceIn(0, 255)
            b = ((b - lowPercentile) * scale).toInt().coerceIn(0, 255)

            // 轻微增强亮度（让文档更白）
            val brightnessBoost = 10
            r = (r + brightnessBoost).coerceIn(0, 255)
            g = (g + brightnessBoost).coerceIn(0, 255)
            b = (b + brightnessBoost).coerceIn(0, 255)

            result[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        resultBitmap.setPixels(result, 0, width, 0, 0, width, height)

        return resultBitmap
    }

    /**
     * OCR就绪模式
     *
     * 使用自适应阈值二值化，优化文字识别效果
     * 高对比度黑白，清晰的文字边缘
     */
    private fun applyOcrReady(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        // 获取像素并转为灰度
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val gray = IntArray(width * height)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            gray[i] = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        }

        // 自适应阈值二值化（使用局部均值）
        val windowSize = 15                                          // 局部窗口大小
        val halfWindow = windowSize / 2
        val threshold = 10                                           // 阈值偏移

        val result = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                // 计算局部均值
                var sum = 0
                var count = 0
                for (wy in -halfWindow..halfWindow) {
                    for (wx in -halfWindow..halfWindow) {
                        val nx = (x + wx).coerceIn(0, width - 1)
                        val ny = (y + wy).coerceIn(0, height - 1)
                        sum += gray[ny * width + nx]
                        count++
                    }
                }
                val localMean = sum / count

                // 二值化：像素值低于局部均值-阈值 则为黑色
                val pixelValue = gray[y * width + x]
                val binaryValue = if (pixelValue < localMean - threshold) 0 else 255

                result[y * width + x] = (0xFF shl 24) or (binaryValue shl 16) or (binaryValue shl 8) or binaryValue
            }
        }

        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        resultBitmap.setPixels(result, 0, width, 0, 0, width, height)

        return resultBitmap
    }

    /**
     * 完整文档扫描处理
     *
     * 组合透视校正 + 扫描效果处理
     *
     * @param bitmap 原始图像
     * @param bounds 文档边界（可选，为null则跳过透视校正）
     * @param mode 扫描模式（默认使用UI选择的默认模式）
     * @return 完整处理后的结果
     */
    suspend fun processDocument(
        bitmap: Bitmap,
        bounds: DocumentBounds? = null,
        mode: DocumentScanMode = _defaultScanMode
    ): DocumentScanResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "processDocument: 开始完整文档处理 mode=${mode.displayName}")

        // 步骤1：透视校正（如果提供了边界）
        val correctedBitmap = if (bounds != null && bounds.confidence > 0.5f) {
            Log.d(TAG, "processDocument: 执行透视校正")
            perspectiveCorrection(bitmap, bounds)
        } else {
            bitmap
        }

        // 步骤2：应用扫描效果
        val scanResult = applyScanMode(correctedBitmap, mode)

        // 清理中间结果
        if (correctedBitmap != bitmap && correctedBitmap != scanResult.bitmap) {
            correctedBitmap.recycle()
        }

        val totalTime = System.currentTimeMillis() - startTime
        Log.d(TAG, "processDocument: 文档处理完成 总耗时=${totalTime}ms")

        DocumentScanResult(
            bitmap = scanResult.bitmap,
            processingTimeMs = totalTime,
            mode = mode,
            isCorected = bounds != null && bounds.confidence > 0.5f
        )
    }

    /**
     * 透视校正
     *
     * 将检测到的四边形文档校正为矩形
     *
     * @param bitmap 原始图像
     * @param bounds 文档边界
     * @return 校正后的图像
     */
    suspend fun perspectiveCorrection(
        bitmap: Bitmap,
        bounds: DocumentBounds
    ): Bitmap = withContext(Dispatchers.Default) {
        Log.d(TAG, "perspectiveCorrection: 开始透视校正")

        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()

        // 源点（文档四角的实际像素坐标）
        val srcPoints = floatArrayOf(
            bounds.topLeft.x * width, bounds.topLeft.y * height,
            bounds.topRight.x * width, bounds.topRight.y * height,
            bounds.bottomRight.x * width, bounds.bottomRight.y * height,
            bounds.bottomLeft.x * width, bounds.bottomLeft.y * height
        )

        // 计算目标尺寸（保持纵横比）
        val topWidth = distance(bounds.topLeft, bounds.topRight) * width
        val bottomWidth = distance(bounds.bottomLeft, bounds.bottomRight) * width
        val leftHeight = distance(bounds.topLeft, bounds.bottomLeft) * height
        val rightHeight = distance(bounds.topRight, bounds.bottomRight) * height

        val targetWidth = max(topWidth, bottomWidth).toInt()
        val targetHeight = max(leftHeight, rightHeight).toInt()

        // 目标点（矩形四角）
        val dstPoints = floatArrayOf(
            0f, 0f,                                                    // 左上
            targetWidth.toFloat(), 0f,                                 // 右上
            targetWidth.toFloat(), targetHeight.toFloat(),             // 右下
            0f, targetHeight.toFloat()                                 // 左下
        )

        // 创建变换矩阵
        val matrix = Matrix()
        matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)

        // 创建输出Bitmap
        val result = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // 应用变换
        canvas.drawBitmap(bitmap, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))

        Log.d(TAG, "perspectiveCorrection: 透视校正完成 size=${targetWidth}x${targetHeight}")
        result
    }

    /**
     * 计算两点间距离
     */
    private fun distance(p1: NormalizedPoint, p2: NormalizedPoint): Float {
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * 释放资源
     */
    fun release() {
        Log.d(TAG, "release: 释放文档扫描处理器资源")
        _documentBounds.value = null
    }
}
