/**
 * BlurProviderFactory.kt - 模糊处理提供者工厂
 *
 * 根据设备能力自动选择最佳的模糊处理实现：
 * 1. 首选 RenderScript Toolkit (跨版本兼容，性能好)
 * 2. 回退到传统 RenderScript (兼容老代码)
 *
 * 使用方式：
 * ```kotlin
 * val provider = BlurProviderFactory.create(context)
 * val blurredBitmap = provider.blur(sourceBitmap, 15f)
 * provider.release() // 使用完毕后释放
 * ```
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.data.processor.blur

import android.content.Context
import android.util.Log

/**
 * 模糊处理提供者工厂
 *
 * 单例模式，自动选择最佳实现
 */
object BlurProviderFactory {
    private const val TAG = "BlurProviderFactory"

    // 缓存的提供者实例
    @Volatile
    private var cachedProvider: BlurProvider? = null

    /**
     * 创建或获取模糊处理提供者
     *
     * 按优先级尝试创建：
     * 1. ToolkitBlurProvider (RenderScript Toolkit)
     * 2. LegacyRenderScriptBlurProvider (传统 RenderScript)
     *
     * @param context 应用上下文
     * @return 可用的模糊处理提供者
     */
    @Synchronized
    fun create(context: Context): BlurProvider {
        // 返回缓存的提供者（如果仍然可用）
        cachedProvider?.let {
            if (it.isAvailable()) {
                Log.d(TAG, "create: 返回缓存的提供者 ${it.name}")
                return it
            } else {
                Log.d(TAG, "create: 缓存的提供者不可用，重新创建")
                it.release()
            }
        }

        // 尝试创建 Toolkit 提供者
        val toolkitProvider = ToolkitBlurProvider()
        if (toolkitProvider.isAvailable()) {
            Log.i(TAG, "create: 使用 ToolkitBlurProvider")
            cachedProvider = toolkitProvider
            return toolkitProvider
        }

        // 回退到传统 RenderScript
        Log.w(TAG, "create: Toolkit 不可用，回退到 LegacyRenderScript")
        val legacyProvider = LegacyRenderScriptBlurProvider(context)
        cachedProvider = legacyProvider
        return legacyProvider
    }

    /**
     * 释放所有缓存的提供者
     *
     * 在应用退出或相机关闭时调用
     */
    @Synchronized
    fun release() {
        Log.d(TAG, "release: 释放所有提供者")
        cachedProvider?.release()
        cachedProvider = null
    }

    /**
     * 获取当前使用的提供者名称
     *
     * @return 提供者名称，未初始化时返回 "None"
     */
    fun getCurrentProviderName(): String {
        return cachedProvider?.name ?: "None"
    }
}
