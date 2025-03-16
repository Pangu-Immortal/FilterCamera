/**
 * HistogramComponents.kt - 实时直方图组件
 *
 * 提供相机预览的实时直方图显示功能
 * 支持RGB三通道和亮度直方图
 *
 * 功能：
 * - 计算图像的RGB直方图数据
 * - 可视化显示直方图曲线
 * - 支持专业模式下的实时预览
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.presentation.camera.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * 直方图数据类
 *
 * @param red 红色通道直方图数据（256个值）
 * @param green 绿色通道直方图数据（256个值）
 * @param blue 蓝色通道直方图数据（256个值）
 * @param luminance 亮度直方图数据（256个值）
 */
data class HistogramData(
    val red: IntArray = IntArray(256),                                     // 红色通道
    val green: IntArray = IntArray(256),                                   // 绿色通道
    val blue: IntArray = IntArray(256),                                    // 蓝色通道
    val luminance: IntArray = IntArray(256)                                // 亮度通道
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as HistogramData
        return red.contentEquals(other.red) &&
               green.contentEquals(other.green) &&
               blue.contentEquals(other.blue) &&
               luminance.contentEquals(other.luminance)
    }

    override fun hashCode(): Int {
        var result = red.contentHashCode()
        result = 31 * result + green.contentHashCode()
        result = 31 * result + blue.contentHashCode()
        result = 31 * result + luminance.contentHashCode()
        return result
    }
}

/**
 * 直方图显示模式枚举
 */
enum class HistogramMode {
    RGB,                                                                    // RGB三通道叠加
    LUMINANCE,                                                              // 仅亮度
    SEPARATE                                                                // 分离显示
}

/**
 * 直方图计算器对象
 *
 * 提供从Bitmap计算直方图数据的静态方法
 */
object HistogramCalculator {

    /**
     * 从Bitmap计算直方图数据
     *
     * @param bitmap 源图像（建议使用缩小后的图像以提高性能）
     * @return 直方图数据
     */
    fun calculate(bitmap: Bitmap?): HistogramData {
        if (bitmap == null) return HistogramData()

        val red = IntArray(256)                                             // 红色直方图
        val green = IntArray(256)                                           // 绿色直方图
        val blue = IntArray(256)                                            // 蓝色直方图
        val luminance = IntArray(256)                                       // 亮度直方图

        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)                               // 像素数组

        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)             // 获取所有像素

        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF                                 // 提取红色分量
            val g = (pixel shr 8) and 0xFF                                  // 提取绿色分量
            val b = pixel and 0xFF                                          // 提取蓝色分量

            red[r]++                                                        // 累计红色
            green[g]++                                                      // 累计绿色
            blue[b]++                                                       // 累计蓝色

            // 计算亮度（使用标准公式：Y = 0.299R + 0.587G + 0.114B）
            val lum = ((0.299 * r) + (0.587 * g) + (0.114 * b)).toInt()
                .coerceIn(0, 255)
            luminance[lum]++                                                // 累计亮度
        }

        return HistogramData(red, green, blue, luminance)
    }

    /**
     * 从Bitmap计算直方图（带采样以提高性能）
     *
     * @param bitmap 源图像
     * @param sampleSize 采样间隔（每sampleSize个像素采样一个）
     * @return 直方图数据
     */
    fun calculateSampled(bitmap: Bitmap?, sampleSize: Int = 4): HistogramData {
        if (bitmap == null) return HistogramData()

        val red = IntArray(256)
        val green = IntArray(256)
        val blue = IntArray(256)
        val luminance = IntArray(256)

        val width = bitmap.width
        val height = bitmap.height

        // 带采样的遍历
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                red[r]++
                green[g]++
                blue[b]++

                val lum = ((0.299 * r) + (0.587 * g) + (0.114 * b)).toInt()
                    .coerceIn(0, 255)
                luminance[lum]++

                x += sampleSize
            }
            y += sampleSize
        }

        return HistogramData(red, green, blue, luminance)
    }
}

/**
 * 实时直方图显示组件
 *
 * @param histogramData 直方图数据
 * @param mode 显示模式（RGB/亮度/分离）
 * @param modifier 修饰符
 */
@Composable
fun HistogramView(
    histogramData: HistogramData,
    mode: HistogramMode = HistogramMode.RGB,
    modifier: Modifier = Modifier
) {
    // 计算各通道的最大值用于归一化
    val maxRed = remember(histogramData.red) { histogramData.red.maxOrNull() ?: 1 }
    val maxGreen = remember(histogramData.green) { histogramData.green.maxOrNull() ?: 1 }
    val maxBlue = remember(histogramData.blue) { histogramData.blue.maxOrNull() ?: 1 }
    val maxLum = remember(histogramData.luminance) { histogramData.luminance.maxOrNull() ?: 1 }
    val maxAll = remember(maxRed, maxGreen, maxBlue) { max(max(maxRed, maxGreen), maxBlue) }

    Box(
        modifier = modifier
            .width(120.dp)
            .height(80.dp)
            .background(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(4.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val barWidth = canvasWidth / 256f                               // 每个柱的宽度

            when (mode) {
                HistogramMode.RGB -> {
                    // RGB三通道叠加显示
                    drawHistogramPath(histogramData.red, maxAll, canvasWidth, canvasHeight, Color.Red.copy(alpha = 0.5f), barWidth)
                    drawHistogramPath(histogramData.green, maxAll, canvasWidth, canvasHeight, Color.Green.copy(alpha = 0.5f), barWidth)
                    drawHistogramPath(histogramData.blue, maxAll, canvasWidth, canvasHeight, Color.Blue.copy(alpha = 0.5f), barWidth)
                }
                HistogramMode.LUMINANCE -> {
                    // 仅亮度显示
                    drawHistogramPath(histogramData.luminance, maxLum, canvasWidth, canvasHeight, Color.White.copy(alpha = 0.8f), barWidth)
                }
                HistogramMode.SEPARATE -> {
                    // 分离显示（仅显示亮度，其他模式可扩展）
                    drawHistogramPath(histogramData.luminance, maxLum, canvasWidth, canvasHeight, Color.White.copy(alpha = 0.8f), barWidth)
                }
            }

            // 绘制边框
            drawRect(
                color = Color.White.copy(alpha = 0.3f),
                style = Stroke(width = 1f)
            )
        }
    }
}

/**
 * Canvas扩展函数：绘制直方图路径
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHistogramPath(
    data: IntArray,
    maxValue: Int,
    width: Float,
    height: Float,
    color: Color,
    barWidth: Float
) {
    if (maxValue == 0) return

    val path = Path()
    path.moveTo(0f, height)                                                  // 起点在左下角

    for (i in data.indices) {
        val x = i * barWidth
        val normalizedValue = data[i].toFloat() / maxValue
        val y = height - (normalizedValue * height * 0.95f)                  // 留5%边距
        if (i == 0) {
            path.lineTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }

    path.lineTo(width, height)                                               // 闭合到右下角
    path.close()

    // 填充区域
    drawPath(
        path = path,
        color = color
    )

    // 绘制轮廓线
    val outlinePath = Path()
    outlinePath.moveTo(0f, height)
    for (i in data.indices) {
        val x = i * barWidth
        val normalizedValue = data[i].toFloat() / maxValue
        val y = height - (normalizedValue * height * 0.95f)
        outlinePath.lineTo(x, y)
    }

    drawPath(
        path = outlinePath,
        color = color.copy(alpha = 1f),
        style = Stroke(width = 1f, cap = StrokeCap.Round)
    )
}

/**
 * 紧凑型直方图组件（用于相机预览角落）
 *
 * @param histogramData 直方图数据
 * @param modifier 修饰符
 */
@Composable
fun CompactHistogramView(
    histogramData: HistogramData,
    modifier: Modifier = Modifier
) {
    HistogramView(
        histogramData = histogramData,
        mode = HistogramMode.RGB,
        modifier = modifier
            .width(100.dp)
            .height(60.dp)
    )
}
