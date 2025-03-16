/**
 * DocumentScanProcessor.kt - 文档扫描处理器
 *
 * 实现文档边缘检测、透视校正和扫描效果
 * 用于文档模式的自动文档识别和优化
 *
 * 功能：
 * - 边缘检测识别文档边界
 * - 透视变换校正文档
 * - 应用扫描效果（对比度增强、锐化）
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

    // 是否启用检测
    private var isEnabled = false

    // 上次检测时间（用于降低频率）
    private var lastDetectionTime = 0L
    private val detectionInterval = 200L                              // 检测间隔200ms

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
            if (bounds != null) {
                Log.d(TAG, "processBitmap: 检测到文档边界")
            }
            scaledBitmap.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "processBitmap: 文档检测失败", e)
        }
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
     * 应用文档扫描效果
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
        Log.d(TAG, "applyScanEffect: 应用扫描效果 enhanceContrast=$enhanceContrast, blackWhite=$blackWhite")

        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint()

        // 构建颜色矩阵
        val colorMatrix = ColorMatrix()

        if (blackWhite) {
            // 黑白效果
            colorMatrix.setSaturation(0f)
        }

        if (enhanceContrast) {
            // 增强对比度
            val contrast = 1.3f                                       // 对比度倍数
            val brightness = -30f                                     // 亮度偏移
            val contrastMatrix = ColorMatrix(floatArrayOf(
                contrast, 0f, 0f, 0f, brightness,
                0f, contrast, 0f, 0f, brightness,
                0f, 0f, contrast, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            ))
            colorMatrix.postConcat(contrastMatrix)
        }

        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        Log.d(TAG, "applyScanEffect: 扫描效果应用完成")
        result
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
