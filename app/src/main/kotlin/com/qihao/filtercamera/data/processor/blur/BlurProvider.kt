/**
 * BlurProvider.kt - 模糊处理提供者抽象层
 *
 * 定义统一的模糊处理接口，支持多种实现方式：
 * - ToolkitBlurProvider: 使用 Google RenderScript Toolkit (推荐)
 * - RenderScriptBlurProvider: 使用传统 RenderScript (已弃用，作为回退)
 * - RenderEffectBlurProvider: 使用 RenderEffect (Android 12+)
 *
 * 设计原则：
 * - 策略模式：运行时选择最佳实现
 * - 向后兼容：支持 Android 7-16
 * - 优雅降级：新方案不可用时自动回退
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.data.processor.blur

import android.graphics.Bitmap

/**
 * 模糊处理提供者接口
 *
 * 所有模糊实现必须实现此接口
 */
interface BlurProvider {

    /**
     * 应用高斯模糊
     *
     * @param source 源图像 (必须是 ARGB_8888 格式)
     * @param radius 模糊半径 (1-25，值越大越模糊)
     * @return 模糊后的图像，失败时返回原图副本
     */
    fun blur(source: Bitmap, radius: Float): Bitmap

    /**
     * 检查此提供者是否可用
     *
     * @return true 表示可用
     */
    fun isAvailable(): Boolean

    /**
     * 释放资源
     *
     * 在不再需要时调用，释放底层资源
     */
    fun release()

    /**
     * 获取提供者名称（用于日志）
     */
    val name: String
}

/**
 * 模糊处理结果
 *
 * @param bitmap 处理后的图像
 * @param success 是否成功
 * @param providerUsed 使用的提供者名称
 * @param processingTimeMs 处理耗时（毫秒）
 */
data class BlurResult(
    val bitmap: Bitmap,                                              // 处理后的Bitmap
    val success: Boolean,                                            // 是否成功
    val providerUsed: String,                                        // 使用的提供者
    val processingTimeMs: Long                                       // 处理耗时
)
