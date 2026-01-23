/**
 * LegacyRenderScriptBlurProvider.kt - 传统 RenderScript 模糊实现
 *
 * 使用传统 Android RenderScript 实现高斯模糊
 * 这是作为 Toolkit 不可用时的回退方案
 *
 * 注意：
 * - RenderScript 在 Android 12 (API 31) 已被弃用
 * - 此实现仅作为兼容性回退，不推荐长期使用
 * - 在 Android 12+ 设备上可能有兼容性问题
 *
 * @author qihao
 * @since 2.0.0
 */
@file:Suppress("DEPRECATION")

package com.qihao.filtercamera.data.processor.blur

import android.content.Context
import android.graphics.Bitmap
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.util.Log

/**
 * 传统 RenderScript 模糊实现
 *
 * 使用已弃用的 RenderScript API，作为回退方案
 */
class LegacyRenderScriptBlurProvider(
    private val context: Context
) : BlurProvider {

    companion object {
        private const val TAG = "LegacyRenderScriptBlur"
        private const val MIN_RADIUS = 1f                            // 最小模糊半径
        private const val MAX_RADIUS = 25f                           // 最大模糊半径
    }

    override val name: String = "Legacy RenderScript"

    // RenderScript 实例
    private var renderScript: RenderScript? = null
    private var blurScript: ScriptIntrinsicBlur? = null

    // 是否已初始化
    private var initialized = false

    init {
        try {
            renderScript = RenderScript.create(context)
            blurScript = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript))
            initialized = true
            Log.d(TAG, "init: RenderScript 初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "init: RenderScript 初始化失败", e)
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
        if (!initialized || renderScript == null || blurScript == null) {
            Log.w(TAG, "blur: RenderScript 未初始化，返回原图副本")
            return source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }

        // 限制模糊半径范围
        val clampedRadius = radius.coerceIn(MIN_RADIUS, MAX_RADIUS)

        return try {
            val rs = renderScript!!
            val blur = blurScript!!

            // 确保输入是 ARGB_8888 格式
            val inputBitmap = if (source.config == Bitmap.Config.ARGB_8888) {
                source.copy(Bitmap.Config.ARGB_8888, true)
            } else {
                source.copy(Bitmap.Config.ARGB_8888, true)
            }

            val outputBitmap = Bitmap.createBitmap(
                inputBitmap.width,
                inputBitmap.height,
                Bitmap.Config.ARGB_8888
            )

            // 创建 Allocation
            val inputAllocation = Allocation.createFromBitmap(rs, inputBitmap)
            val outputAllocation = Allocation.createFromBitmap(rs, outputBitmap)

            // 设置模糊半径并执行
            blur.setRadius(clampedRadius)
            blur.setInput(inputAllocation)
            blur.forEach(outputAllocation)

            // 获取结果
            outputAllocation.copyTo(outputBitmap)

            // 释放 Allocation
            inputAllocation.destroy()
            outputAllocation.destroy()

            // 回收输入副本
            inputBitmap.recycle()

            val processingTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "blur: 处理完成 radius=$clampedRadius time=${processingTime}ms")

            outputBitmap
        } catch (e: Exception) {
            Log.e(TAG, "blur: 处理失败", e)
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }

    /**
     * 检查是否可用
     */
    override fun isAvailable(): Boolean = initialized && renderScript != null

    /**
     * 释放资源
     */
    override fun release() {
        Log.d(TAG, "release: 释放 RenderScript 资源")
        try {
            blurScript?.destroy()
            blurScript = null

            renderScript?.destroy()
            renderScript = null

            initialized = false
        } catch (e: Exception) {
            Log.e(TAG, "release: 释放资源失败", e)
        }
    }
}
