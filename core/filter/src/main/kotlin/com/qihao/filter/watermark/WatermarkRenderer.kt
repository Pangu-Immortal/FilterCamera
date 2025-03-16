/**
 * WatermarkRenderer.kt - 水印渲染器
 *
 * 使用Canvas在Bitmap上绘制各种水印效果
 * 支持4种水印类型：时间戳、日期、设备信息、自定义
 *
 * 设计参考：
 * - 数码相机时间戳水印（橙色纯文字）
 * - 徕卡风格设备水印
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filter.watermark

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 水印渲染器
 *
 * 提供静态方法在Bitmap上绘制各种水印
 */
object WatermarkRenderer {

    private const val TAG = "WatermarkRenderer"                               // 日志标签

    // 水印样式常量
    private const val WATERMARK_PADDING_RATIO = 0.025f                        // 水印边距比例
    private const val WATERMARK_TEXT_SIZE_RATIO = 0.028f                      // 主文字大小比例
    private const val WATERMARK_SUBTITLE_SIZE_RATIO = 0.020f                  // 副文字大小比例

    // 数码相机水印颜色（橙黄色）
    private val DIGITAL_CAMERA_COLOR = Color.rgb(255, 165, 0)                 // 橙色

    /**
     * 水印类型枚举
     */
    enum class WatermarkType {
        TIMESTAMP,        // 时间戳（日期+时间）- 数码相机风格
        DATE,             // 仅日期 - 数码相机风格
        DEVICE,           // 设备信息（类似徕卡水印）
        CUSTOM            // 自定义文字
    }

    /**
     * 水印数据类
     *
     * 用于传递水印所需的各种信息
     */
    data class WatermarkData(
        val timestamp: Long = System.currentTimeMillis(),                     // 时间戳
        val customText: String = "",                                          // 自定义文字
        val deviceModel: String = Build.MODEL,                                // 设备型号
        val deviceBrand: String = Build.BRAND                                 // 设备品牌
    )

    /**
     * 应用水印到Bitmap
     *
     * @param sourceBitmap 源图片
     * @param watermarkType 水印类型
     * @param data 水印数据
     * @return 带水印的Bitmap（新创建的，不修改原图）
     */
    fun applyWatermark(
        sourceBitmap: Bitmap,
        watermarkType: WatermarkType,
        data: WatermarkData = WatermarkData()
    ): Bitmap {
        // 创建可编辑的副本
        val resultBitmap = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)

        // 根据水印类型绘制
        when (watermarkType) {
            WatermarkType.TIMESTAMP -> drawDigitalCameraTimestamp(canvas, resultBitmap, data)
            WatermarkType.DATE -> drawDigitalCameraDate(canvas, resultBitmap, data)
            WatermarkType.DEVICE -> drawDevice(canvas, resultBitmap, data)
            WatermarkType.CUSTOM -> drawCustom(canvas, resultBitmap, data)
        }

        return resultBitmap
    }

    /**
     * 绘制数码相机风格时间戳水印
     *
     * 格式：2026·01·16 14:30:25
     * 橙色纯文字，无边框背景
     */
    private fun drawDigitalCameraTimestamp(canvas: Canvas, bitmap: Bitmap, data: WatermarkData) {
        // 格式：2026·01·16 14:30:25
        val dateFormat = SimpleDateFormat("yyyy·MM·dd HH:mm:ss", Locale.getDefault())
        val text = dateFormat.format(Date(data.timestamp))
        drawDigitalCameraText(canvas, bitmap, text)
    }

    /**
     * 绘制数码相机风格日期水印
     *
     * 格式：2026·01·16
     * 橙色纯文字，无边框背景
     */
    private fun drawDigitalCameraDate(canvas: Canvas, bitmap: Bitmap, data: WatermarkData) {
        // 格式：2026·01·16
        val dateFormat = SimpleDateFormat("yyyy·MM·dd", Locale.getDefault())
        val text = dateFormat.format(Date(data.timestamp))
        drawDigitalCameraText(canvas, bitmap, text)
    }

    /**
     * 绘制数码相机风格文字（橙色纯文字，无背景）
     *
     * @param canvas 画布
     * @param bitmap Bitmap（用于计算尺寸）
     * @param text 要绘制的文字
     */
    private fun drawDigitalCameraText(canvas: Canvas, bitmap: Bitmap, text: String) {
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        val padding = (width * WATERMARK_PADDING_RATIO).coerceAtLeast(20f)
        val textSize = (width * WATERMARK_TEXT_SIZE_RATIO).coerceAtLeast(32f)

        // 数码相机风格画笔：橙色文字 + 黑色描边阴影
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = DIGITAL_CAMERA_COLOR                                      // 橙色
            this.textSize = textSize
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)     // 等宽字体，更像数码相机
            letterSpacing = 0.05f                                             // 轻微字间距
            setShadowLayer(3f, 2f, 2f, Color.argb(200, 0, 0, 0))              // 黑色阴影增强可读性
        }

        // 计算文字位置（右下角）
        val textWidth = textPaint.measureText(text)
        val x = width - padding - textWidth
        val y = height - padding

        // 绘制文字
        canvas.drawText(text, x, y, textPaint)
    }

    /**
     * 绘制设备信息水印（徕卡风格）
     *
     * 格式：
     * Shot on Xiaomi 14 Pro
     * LEICA VARIO-SUMMILUX 1:1.4-3.2/14-75 ASPH.
     */
    private fun drawDevice(canvas: Canvas, bitmap: Bitmap, data: WatermarkData) {
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        val padding = (width * WATERMARK_PADDING_RATIO).coerceAtLeast(20f)
        val textSize = (width * 0.035f).coerceAtLeast(36f)                    // 主标题大字号
        val subtitleSize = (width * 0.020f).coerceAtLeast(20f)                // 副标题原有尺寸

        // 主标题画笔（白色 + 黑色阴影）
        val mainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(6f, 3f, 3f, Color.argb(220, 0, 0, 0))              // 黑色阴影立体效果
        }

        // 副标题画笔（白色 + 黑色阴影）
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = subtitleSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            setShadowLayer(5f, 2f, 2f, Color.argb(200, 0, 0, 0))              // 黑色阴影立体效果
        }

        // 获取友好的设备名称
        val deviceName = getDeviceMarketingName(data.deviceBrand, data.deviceModel)

        // 主文字：Shot on Xiaomi 14 Pro
        val mainText = "Shot on $deviceName"

        // 副文字：模拟镜头信息
        val subText = getLensDescription(data.deviceBrand)

        // 计算位置（右下角，右对齐）
        val mainTextWidth = mainPaint.measureText(mainText)
        val subTextWidth = subPaint.measureText(subText)

        // 主文字右对齐
        val mainX = width - padding - mainTextWidth
        // 副文字右对齐
        val subX = width - padding - subTextWidth

        val y = height - padding - subtitleSize - 12f

        // 绘制文字
        canvas.drawText(mainText, mainX, y, mainPaint)
        canvas.drawText(subText, subX, y + textSize + 6f, subPaint)
    }

    /**
     * 绘制自定义文字水印
     *
     * 白色文字，带阴影
     */
    private fun drawCustom(canvas: Canvas, bitmap: Bitmap, data: WatermarkData) {
        val text = data.customText.ifEmpty { "FilterCamera" }

        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        val padding = (width * WATERMARK_PADDING_RATIO).coerceAtLeast(20f)
        val textSize = (width * WATERMARK_TEXT_SIZE_RATIO).coerceAtLeast(28f)

        // 白色文字画笔
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(4f, 2f, 2f, Color.argb(200, 0, 0, 0))
        }

        // 计算文字位置（右下角）
        val textWidth = textPaint.measureText(text)
        val x = width - padding - textWidth
        val y = height - padding

        // 绘制文字
        canvas.drawText(text, x, y, textPaint)
    }

    /**
     * 获取设备市场名称（友好名称）
     *
     * 将设备编码转换为用户友好的市场名称
     *
     * @param brand 品牌
     * @param model 型号（可能是设备编码）
     * @return 友好的设备名称
     */
    private fun getDeviceMarketingName(brand: String, model: String): String {
        val brandCapitalized = brand.lowercase().replaceFirstChar { it.uppercase() }

        // 小米设备编码映射（常见机型）
        val xiaomiModels = mapOf(
            "24031PN0DC" to "14 Pro",
            "2304FPN6DC" to "13 Ultra",
            "23046RP50C" to "13 Pro",
            "2211133C" to "13",
            "2203121C" to "12 Pro",
            "2201123C" to "12",
            "2112123AC" to "12X",
            "21091116AC" to "Civi",
            "M2102K1AC" to "11 Ultra",
            "M2011K2C" to "11 Pro",
            "M2001J2C" to "10 Pro",
            "M2007J3SC" to "10 Ultra",
            "23116PN5BC" to "14",
            "2310DRK48C" to "14 Ultra"
        )

        // 华为设备编码映射
        val huaweiModels = mapOf(
            "NOH-AN00" to "Mate 40 Pro",
            "OCE-AN10" to "Mate 40",
            "ELS-AN00" to "P40 Pro",
            "ANA-AN00" to "P40"
        )

        // OPPO设备编码映射
        val oppoModels = mapOf(
            "PHQ110" to "Find X6 Pro",
            "PGFM10" to "Find X5 Pro"
        )

        // vivo设备编码映射
        val vivoModels = mapOf(
            "V2227A" to "X90 Pro+",
            "V2242A" to "X90 Pro"
        )

        // 尝试从映射表获取友好名称
        val friendlyModel = when (brand.lowercase()) {
            "xiaomi", "redmi" -> xiaomiModels[model]
            "huawei", "honor" -> huaweiModels[model]
            "oppo", "oneplus" -> oppoModels[model]
            "vivo" -> vivoModels[model]
            else -> null
        }

        return if (friendlyModel != null) {
            "$brandCapitalized $friendlyModel"
        } else {
            // 如果没有映射，检查model是否已经是友好名称
            if (model.any { it.isLetter() } && !model.all { it.isUpperCase() || it.isDigit() }) {
                // model包含小写字母，可能已经是友好名称
                "$brandCapitalized $model"
            } else {
                // model是纯编码，使用品牌名
                brandCapitalized
            }
        }
    }

    /**
     * 获取镜头描述（模拟不同品牌的风格）
     *
     * @param brand 品牌名称
     * @return 镜头描述文字
     */
    private fun getLensDescription(brand: String): String {
        return when (brand.lowercase()) {
            "xiaomi", "redmi" -> "LEICA VARIO-SUMMILUX 1:1.4-3.2/14-75 ASPH."
            "huawei", "honor" -> "XMAGE ULTRA APERTURE F/1.4"
            "oppo" -> "HASSELBLAD CAMERA FOR MOBILE"
            "vivo" -> "ZEISS OPTICS T* COATING"
            "oneplus" -> "HASSELBLAD CAMERA SYSTEM"
            "samsung" -> "EXPERTRAW F/1.8 OIS"
            "google" -> "PIXEL CAMERA HDR+ ENHANCED"
            "apple" -> "MAIN CAMERA ƒ/1.5"
            "sony" -> "ZEISS OPTICS T* ƒ/1.7"
            else -> "AI CAMERA F/1.8"
        }
    }
}
