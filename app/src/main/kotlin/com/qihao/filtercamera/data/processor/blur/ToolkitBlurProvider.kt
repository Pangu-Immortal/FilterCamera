/**
 * ToolkitBlurProvider.kt - RenderScript Toolkit 模糊实现
 *
 * 使用 Google RenderScript Toolkit 实现高斯模糊
 * 这是 RenderScript 的官方替代方案，支持 Android 7+
 *
 * 优点：
 * - 不依赖已弃用的 RenderScript
 * - 跨版本兼容 (API 24+)
 * - 性能接近原生 RenderScript
 * - 纯 Kotlin/Java 实现，无 Native 依赖
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.data.processor.blur

import android.graphics.Bitmap
import android.util.Log
import com.google.android.renderscript.Toolkit

/**
 * RenderScript Toolkit 模糊实现
 *
 * 使用 Google 官方的 RenderScript Toolkit 库
 */
class ToolkitBlurProvider : BlurProvider {

    companion object {
        private const val TAG = "ToolkitBlurProvider"
        private const val MIN_RADIUS = 1                             // 最小模糊半径
        private const val MAX_RADIUS = 25                            // 最大模糊半径
    }

    override val name: String = "RenderScript Toolkit"

    // 是否已初始化
    private var initialized = false

    init {
        try {
            // 验证 Toolkit 类可用
            Class.forName("com.google.android.renderscript.Toolkit")
            initialized = true
            Log.d(TAG, "init: RenderScript Toolkit 初始化成功")
        } catch (e: ClassNotFoundException) {
            Log.e(TAG, "init: RenderScript Toolkit 不可用", e)
            initialized = false
        }
    }

    /**
     * 应用高斯模糊
     *
     * @param source 源图像
     * @param radius 模糊半径 (1-25)
     * @return 模糊后的图像
     */
    override fun blur(source: Bitmap, radius: Float): Bitmap {
        val startTime = System.currentTimeMillis()

        // 检查可用性
        if (!initialized) {
            Log.w(TAG, "blur: Toolkit 未初始化，返回原图副本")
            return source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }

        // 限制模糊半径范围
        val clampedRadius = radius.toInt().coerceIn(MIN_RADIUS, MAX_RADIUS)

        return try {
            // 确保输入是 ARGB_8888 格式
            val inputBitmap = if (source.config == Bitmap.Config.ARGB_8888) {
                source
            } else {
                source.copy(Bitmap.Config.ARGB_8888, false)
            }

            // 使用 Toolkit 进行模糊处理
            val result = Toolkit.blur(inputBitmap, clampedRadius)

            // 如果创建了副本，回收它
            if (inputBitmap !== source) {
                inputBitmap.recycle()
            }

            val processingTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "blur: 处理完成 radius=$clampedRadius time=${processingTime}ms")

            result
        } catch (e: Exception) {
            Log.e(TAG, "blur: 处理失败", e)
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }

    /**
     * 检查是否可用
     */
    override fun isAvailable(): Boolean = initialized

    /**
     * 释放资源
     *
     * Toolkit 是静态方法，无需释放
     */
    override fun release() {
        Log.d(TAG, "release: Toolkit 无需释放资源")
        // Toolkit 使用静态方法，无需释放
    }
}
