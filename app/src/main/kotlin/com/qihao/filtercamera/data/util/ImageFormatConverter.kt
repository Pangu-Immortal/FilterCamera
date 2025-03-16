/**
 * ImageFormatConverter.kt - 图像格式转换工具类
 *
 * 提供 YUV_420_888、JPEG 等格式到 Bitmap 的转换功能
 * 统一处理图像旋转、镜像等变换操作
 *
 * 功能：
 * - YUV_420_888 转 NV21 字节数组
 * - NV21 压缩为 JPEG 并解码为 Bitmap
 * - ImageProxy 转 Bitmap（支持 JPEG/YUV 格式）
 * - 图像旋转和镜像处理
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.data.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

/**
 * 图像格式转换工具类
 *
 * 单例对象，提供线程安全的图像转换方法
 */
object ImageFormatConverter {

    private const val TAG = "ImageFormatConverter"                    // 日志标签

    /**
     * YUV 转换配置
     *
     * @param quality JPEG 压缩质量（0-100）
     * @param handleRotation 是否处理旋转
     * @param handleMirror 是否处理镜像（前置摄像头）
     */
    data class ConversionConfig(
        val quality: Int = 85,                                        // 默认压缩质量
        val handleRotation: Boolean = true,                           // 默认处理旋转
        val handleMirror: Boolean = false                             // 默认不镜像
    ) {
        companion object {
            /** 预览配置：中等质量，处理旋转 */
            val PREVIEW = ConversionConfig(quality = 85, handleRotation = true)

            /** 拍照配置：高质量，处理旋转 */
            val CAPTURE = ConversionConfig(quality = 95, handleRotation = true)

            /** 缩略图配置：低质量，快速处理 */
            val THUMBNAIL = ConversionConfig(quality = 60, handleRotation = false)
        }
    }

    /**
     * 将 YUV_420_888 格式的 ImageProxy 转换为 NV21 字节数组
     *
     * 正确处理 rowStride 和 pixelStride，支持各种设备
     *
     * @param imageProxy YUV_420_888 格式的图像
     * @return NV21 格式的字节数组
     */
    fun yuvImageProxyToNv21(imageProxy: ImageProxy): ByteArray {
        val width = imageProxy.width
        val height = imageProxy.height

        // 获取 YUV 平面
        val yPlane = imageProxy.planes[0]
        val uPlane = imageProxy.planes[1]
        val vPlane = imageProxy.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        // 获取步长信息
        val yRowStride = yPlane.rowStride
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        // 创建 NV21 格式的字节数组（Y + VU 交错）
        val nv21 = ByteArray(width * height * 3 / 2)

        // 复制 Y 平面（考虑 rowStride）
        var pos = 0
        if (yRowStride == width) {
            // rowStride 等于 width，直接复制
            yBuffer.get(nv21, 0, width * height)
            pos = width * height
        } else {
            // rowStride 大于 width，需要逐行复制
            for (row in 0 until height) {
                yBuffer.position(row * yRowStride)
                yBuffer.get(nv21, pos, width)
                pos += width
            }
        }

        // 复制 UV 平面（交错为 NV21 格式：VUVU...）
        val uvHeight = height / 2
        val uvWidth = width / 2

        for (row in 0 until uvHeight) {
            for (col in 0 until uvWidth) {
                val uvIndex = row * uvRowStride + col * uvPixelStride
                // NV21 格式：先 V 后 U
                nv21[pos++] = vBuffer.get(uvIndex)
                nv21[pos++] = uBuffer.get(uvIndex)
            }
        }

        return nv21
    }

    /**
     * 将 NV21 字节数组压缩为 JPEG 并解码为 Bitmap
     *
     * @param nv21 NV21 格式的字节数组
     * @param width 图像宽度
     * @param height 图像高度
     * @param quality JPEG 压缩质量（0-100）
     * @return Bitmap，失败返回 null
     */
    fun nv21ToBitmap(nv21: ByteArray, width: Int, height: Int, quality: Int = 85): Bitmap? {
        return try {
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            ByteArrayOutputStream().use { out ->                      // 自动关闭流
                yuvImage.compressToJpeg(Rect(0, 0, width, height), quality, out)
                val jpegBytes = out.toByteArray()
                BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            }
        } catch (e: Exception) {
            Log.e(TAG, "nv21ToBitmap: 转换失败", e)
            null
        }
    }

    /**
     * 将 YUV_420_888 格式的 ImageProxy 转换为 Bitmap
     *
     * 内部调用 yuvImageProxyToNv21 和 nv21ToBitmap
     *
     * @param imageProxy YUV_420_888 格式的图像
     * @param config 转换配置
     * @param isFrontCamera 是否前置摄像头（用于镜像处理）
     * @return Bitmap，失败返回 null
     */
    fun yuvImageProxyToBitmap(
        imageProxy: ImageProxy,
        config: ConversionConfig = ConversionConfig.PREVIEW,
        isFrontCamera: Boolean = false
    ): Bitmap? {
        return try {
            val nv21 = yuvImageProxyToNv21(imageProxy)
            val bitmap = nv21ToBitmap(nv21, imageProxy.width, imageProxy.height, config.quality)
                ?: return null

            // 处理旋转和镜像
            if (config.handleRotation) {
                val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                applyTransformation(bitmap, rotationDegrees, isFrontCamera && config.handleMirror)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "yuvImageProxyToBitmap: 转换失败", e)
            null
        }
    }

    /**
     * 将 JPEG 格式的 ImageProxy 转换为 Bitmap
     *
     * @param imageProxy JPEG 格式的图像
     * @param handleRotation 是否处理旋转
     * @param isFrontCamera 是否前置摄像头
     * @return Bitmap，失败返回 null
     */
    fun jpegImageProxyToBitmap(
        imageProxy: ImageProxy,
        handleRotation: Boolean = true,
        isFrontCamera: Boolean = false
    ): Bitmap? {
        return try {
            val buffer = imageProxy.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return null

            if (handleRotation) {
                val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                applyTransformation(bitmap, rotationDegrees, isFrontCamera)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "jpegImageProxyToBitmap: 转换失败", e)
            null
        }
    }

    /**
     * 将任意格式的 ImageProxy 转换为 Bitmap
     *
     * 自动检测格式并调用对应的转换方法
     *
     * @param imageProxy 图像代理
     * @param config 转换配置
     * @param isFrontCamera 是否前置摄像头
     * @return Bitmap，失败返回 null
     */
    fun imageProxyToBitmap(
        imageProxy: ImageProxy,
        config: ConversionConfig = ConversionConfig.CAPTURE,
        isFrontCamera: Boolean = false
    ): Bitmap? {
        return when (imageProxy.format) {
            ImageFormat.JPEG -> {
                Log.d(TAG, "imageProxyToBitmap: JPEG 格式 ${imageProxy.width}x${imageProxy.height}")
                jpegImageProxyToBitmap(imageProxy, config.handleRotation, isFrontCamera)
            }
            ImageFormat.YUV_420_888 -> {
                Log.d(TAG, "imageProxyToBitmap: YUV_420_888 格式 ${imageProxy.width}x${imageProxy.height}")
                yuvImageProxyToBitmap(imageProxy, config, isFrontCamera)
            }
            else -> {
                Log.w(TAG, "imageProxyToBitmap: 不支持的格式 ${imageProxy.format}")
                null
            }
        }
    }

    /**
     * 应用图像变换（旋转和镜像）
     *
     * @param bitmap 原始 Bitmap
     * @param rotationDegrees 旋转角度
     * @param mirror 是否镜像
     * @return 变换后的 Bitmap
     */
    fun applyTransformation(bitmap: Bitmap, rotationDegrees: Int, mirror: Boolean): Bitmap {
        if (rotationDegrees == 0 && !mirror) {
            return bitmap                                             // 无需变换
        }

        val matrix = Matrix()
        if (rotationDegrees != 0) {
            matrix.postRotate(rotationDegrees.toFloat())
        }
        if (mirror) {
            matrix.postScale(-1f, 1f)                                 // 水平镜像
        }

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
