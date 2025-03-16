/**
 * SafeMagicJni.kt - 美颜JNI安全封装类
 *
 * 为MagicJni提供安全封装，所有JNI调用都返回Result类型
 * 避免Native层崩溃导致应用闪退
 *
 * 设计原则：
 * - 所有操作返回Result<T>，调用方可安全处理错误
 * - 自动检测Native库加载状态
 * - 提供详细的错误日志
 * - 支持优雅降级
 *
 * @author qihao
 * @since 2.0.0
 */
package com.seu.magicfilter.beautify

import android.graphics.Bitmap
import android.util.Log
import java.nio.ByteBuffer

/**
 * JNI操作错误类型
 */
sealed class JniError : Exception() {
    /** Native库未加载 */
    object LibraryNotLoaded : JniError() {
        private fun readResolve(): Any = LibraryNotLoaded
        override val message: String = "Native库未加载"
    }

    /** 参数无效 */
    data class InvalidParameter(override val message: String) : JniError()

    /** Native调用失败 */
    data class NativeCallFailed(override val message: String, override val cause: Throwable? = null) : JniError()

    /** 返回值为空 */
    data class NullResult(override val message: String) : JniError()
}

/**
 * 美颜JNI安全封装类
 *
 * 为所有Native美颜操作提供安全的Kotlin封装
 * 所有操作均返回Result类型，确保调用安全
 */
object SafeMagicJni {

    private const val TAG = "SafeMagicJni"               // 日志标签

    // Native库加载状态
    private var isLibraryLoaded = false

    init {
        isLibraryLoaded = try {
            System.loadLibrary("filter")                  // 尝试加载Native库
            Log.d(TAG, "Native库加载成功")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native库加载失败: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Native库加载异常: ${e.message}")
            false
        }
    }

    // ==================== 公共API ====================

    /**
     * 检查Native库是否已加载
     *
     * @return true表示库已成功加载
     */
    fun isLibraryLoaded(): Boolean = isLibraryLoaded

    /**
     * 安全存储Bitmap数据到Native层
     *
     * @param bitmap 源Bitmap（必须是有效的Bitmap）
     * @return 成功返回数据句柄，失败返回错误
     */
    fun storeBitmapData(bitmap: Bitmap?): Result<ByteBuffer> {
        Log.d(TAG, "storeBitmapData: 开始存储Bitmap")

        // 前置检查
        if (!isLibraryLoaded) {
            Log.e(TAG, "storeBitmapData: Native库未加载")
            return Result.failure(JniError.LibraryNotLoaded)
        }

        if (bitmap == null) {
            Log.e(TAG, "storeBitmapData: Bitmap为null")
            return Result.failure(JniError.InvalidParameter("Bitmap不能为null"))
        }

        if (bitmap.isRecycled) {
            Log.e(TAG, "storeBitmapData: Bitmap已回收")
            return Result.failure(JniError.InvalidParameter("Bitmap已被回收"))
        }

        // 执行Native调用
        return try {
            val handle = MagicJni.jniStoreBitmapData(bitmap)
            if (handle != null) {
                Log.d(TAG, "storeBitmapData: 存储成功")
                Result.success(handle)
            } else {
                Log.e(TAG, "storeBitmapData: Native返回null")
                Result.failure(JniError.NullResult("jniStoreBitmapData返回null"))
            }
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "storeBitmapData: JNI链接错误", e)
            isLibraryLoaded = false                       // 标记库状态异常
            Result.failure(JniError.NativeCallFailed("JNI链接错误", e))
        } catch (e: Exception) {
            Log.e(TAG, "storeBitmapData: Native调用异常", e)
            Result.failure(JniError.NativeCallFailed("Native调用失败: ${e.message}", e))
        }
    }

    /**
     * 安全初始化美颜处理器
     *
     * @param handle 数据句柄（来自storeBitmapData）
     * @return 成功返回Unit，失败返回错误
     */
    fun initBeautify(handle: ByteBuffer?): Result<Unit> {
        Log.d(TAG, "initBeautify: 初始化美颜处理器")

        if (!isLibraryLoaded) {
            return Result.failure(JniError.LibraryNotLoaded)
        }

        if (handle == null) {
            return Result.failure(JniError.InvalidParameter("句柄不能为null"))
        }

        return try {
            MagicJni.jniInitMagicBeautify(handle)
            Log.d(TAG, "initBeautify: 初始化成功")
            Result.success(Unit)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "initBeautify: JNI链接错误", e)
            isLibraryLoaded = false
            Result.failure(JniError.NativeCallFailed("JNI链接错误", e))
        } catch (e: Exception) {
            Log.e(TAG, "initBeautify: Native调用异常", e)
            Result.failure(JniError.NativeCallFailed("初始化失败: ${e.message}", e))
        }
    }

    /**
     * 安全执行美白处理
     *
     * @param whiteLevel 美白等级 (0.0 - 1.0)
     * @return 成功返回Unit，失败返回错误
     */
    fun applyWhiteSkin(whiteLevel: Float): Result<Unit> {
        Log.d(TAG, "applyWhiteSkin: 应用美白 level=$whiteLevel")

        if (!isLibraryLoaded) {
            return Result.failure(JniError.LibraryNotLoaded)
        }

        // 参数范围校验
        val safeLevel = whiteLevel.coerceIn(0f, 1f)
        if (safeLevel != whiteLevel) {
            Log.w(TAG, "applyWhiteSkin: 参数越界，已修正 $whiteLevel -> $safeLevel")
        }

        return try {
            MagicJni.jniStartWhiteSkin(safeLevel)
            Log.d(TAG, "applyWhiteSkin: 美白处理完成")
            Result.success(Unit)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "applyWhiteSkin: JNI链接错误", e)
            isLibraryLoaded = false
            Result.failure(JniError.NativeCallFailed("JNI链接错误", e))
        } catch (e: Exception) {
            Log.e(TAG, "applyWhiteSkin: Native调用异常", e)
            Result.failure(JniError.NativeCallFailed("美白处理失败: ${e.message}", e))
        }
    }

    /**
     * 安全执行磨皮处理
     *
     * @param smoothLevel 磨皮等级 (0.0 - 1.0)
     * @return 成功返回Unit，失败返回错误
     */
    fun applySkinSmooth(smoothLevel: Float): Result<Unit> {
        Log.d(TAG, "applySkinSmooth: 应用磨皮 level=$smoothLevel")

        if (!isLibraryLoaded) {
            return Result.failure(JniError.LibraryNotLoaded)
        }

        // 参数范围校验
        val safeLevel = smoothLevel.coerceIn(0f, 1f)
        if (safeLevel != smoothLevel) {
            Log.w(TAG, "applySkinSmooth: 参数越界，已修正 $smoothLevel -> $safeLevel")
        }

        return try {
            MagicJni.jniStartSkinSmooth(null, safeLevel)
            Log.d(TAG, "applySkinSmooth: 磨皮处理完成")
            Result.success(Unit)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "applySkinSmooth: JNI链接错误", e)
            isLibraryLoaded = false
            Result.failure(JniError.NativeCallFailed("JNI链接错误", e))
        } catch (e: Exception) {
            Log.e(TAG, "applySkinSmooth: Native调用异常", e)
            Result.failure(JniError.NativeCallFailed("磨皮处理失败: ${e.message}", e))
        }
    }

    /**
     * 安全获取处理后的Bitmap
     *
     * @param handle 数据句柄
     * @return 成功返回处理后的Bitmap，失败返回错误
     */
    fun getResultBitmap(handle: ByteBuffer?): Result<Bitmap> {
        Log.d(TAG, "getResultBitmap: 获取结果Bitmap")

        if (!isLibraryLoaded) {
            return Result.failure(JniError.LibraryNotLoaded)
        }

        if (handle == null) {
            return Result.failure(JniError.InvalidParameter("句柄不能为null"))
        }

        return try {
            val bitmap = MagicJni.jniGetBitmapFromStoredBitmapData(handle)
            if (bitmap != null) {
                Log.d(TAG, "getResultBitmap: 获取成功 ${bitmap.width}x${bitmap.height}")
                Result.success(bitmap)
            } else {
                Log.e(TAG, "getResultBitmap: Native返回null")
                Result.failure(JniError.NullResult("获取结果Bitmap失败"))
            }
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "getResultBitmap: JNI链接错误", e)
            isLibraryLoaded = false
            Result.failure(JniError.NativeCallFailed("JNI链接错误", e))
        } catch (e: Exception) {
            Log.e(TAG, "getResultBitmap: Native调用异常", e)
            Result.failure(JniError.NativeCallFailed("获取Bitmap失败: ${e.message}", e))
        }
    }

    /**
     * 安全释放美颜处理器资源
     *
     * @return 成功返回Unit，失败返回错误（但不中断流程）
     */
    fun uninitBeautify(): Result<Unit> {
        Log.d(TAG, "uninitBeautify: 释放美颜处理器")

        if (!isLibraryLoaded) {
            Log.w(TAG, "uninitBeautify: Native库未加载，跳过释放")
            return Result.success(Unit)                   // 未加载则无需释放
        }

        return try {
            MagicJni.jniUnInitMagicBeautify()
            Log.d(TAG, "uninitBeautify: 释放成功")
            Result.success(Unit)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "uninitBeautify: JNI链接错误", e)
            isLibraryLoaded = false
            Result.failure(JniError.NativeCallFailed("JNI链接错误", e))
        } catch (e: Exception) {
            Log.e(TAG, "uninitBeautify: Native调用异常", e)
            Result.failure(JniError.NativeCallFailed("释放失败: ${e.message}", e))
        }
    }

    /**
     * 安全释放Native层Bitmap数据
     *
     * @param handle 数据句柄
     * @return 成功返回Unit，失败返回错误（但不中断流程）
     */
    fun freeBitmapData(handle: ByteBuffer?): Result<Unit> {
        Log.d(TAG, "freeBitmapData: 释放Bitmap数据")

        if (!isLibraryLoaded) {
            Log.w(TAG, "freeBitmapData: Native库未加载，跳过释放")
            return Result.success(Unit)
        }

        if (handle == null) {
            Log.w(TAG, "freeBitmapData: 句柄为null，跳过释放")
            return Result.success(Unit)
        }

        return try {
            MagicJni.jniFreeBitmapData(handle)
            Log.d(TAG, "freeBitmapData: 释放成功")
            Result.success(Unit)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "freeBitmapData: JNI链接错误", e)
            isLibraryLoaded = false
            Result.failure(JniError.NativeCallFailed("JNI链接错误", e))
        } catch (e: Exception) {
            Log.e(TAG, "freeBitmapData: Native调用异常", e)
            Result.failure(JniError.NativeCallFailed("释放失败: ${e.message}", e))
        }
    }

    // ==================== 高级便捷方法 ====================

    /**
     * 一站式美颜处理
     *
     * 封装完整的美颜流程：存储 -> 初始化 -> 美白 -> 磨皮 -> 获取结果 -> 释放
     *
     * @param sourceBitmap 源Bitmap
     * @param whiteLevel 美白等级 (0.0 - 1.0)
     * @param smoothLevel 磨皮等级 (0.0 - 1.0)
     * @return 成功返回处理后的Bitmap，失败返回错误
     */
    fun processBeauty(
        sourceBitmap: Bitmap?,
        whiteLevel: Float,
        smoothLevel: Float
    ): Result<Bitmap> {
        Log.d(TAG, "processBeauty: 开始一站式美颜 white=$whiteLevel smooth=$smoothLevel")

        // 存储Bitmap
        val handleResult = storeBitmapData(sourceBitmap)
        if (handleResult.isFailure) {
            return Result.failure(handleResult.exceptionOrNull() ?: JniError.NativeCallFailed("存储失败"))
        }
        val handle = handleResult.getOrThrow()

        // 初始化
        val initResult = initBeautify(handle)
        if (initResult.isFailure) {
            freeBitmapData(handle)                        // 清理资源
            return Result.failure(initResult.exceptionOrNull() ?: JniError.NativeCallFailed("初始化失败"))
        }

        // 美白处理
        if (whiteLevel > 0f) {
            val whiteResult = applyWhiteSkin(whiteLevel)
            if (whiteResult.isFailure) {
                uninitBeautify()
                freeBitmapData(handle)
                return Result.failure(whiteResult.exceptionOrNull() ?: JniError.NativeCallFailed("美白失败"))
            }
        }

        // 磨皮处理
        if (smoothLevel > 0f) {
            val smoothResult = applySkinSmooth(smoothLevel)
            if (smoothResult.isFailure) {
                uninitBeautify()
                freeBitmapData(handle)
                return Result.failure(smoothResult.exceptionOrNull() ?: JniError.NativeCallFailed("磨皮失败"))
            }
        }

        // 获取结果
        val bitmapResult = getResultBitmap(handle)

        // 释放资源（无论成功失败都要释放）
        uninitBeautify()
        freeBitmapData(handle)

        if (bitmapResult.isFailure) {
            return Result.failure(bitmapResult.exceptionOrNull() ?: JniError.NativeCallFailed("获取结果失败"))
        }

        Log.d(TAG, "processBeauty: 一站式美颜完成")
        return bitmapResult
    }
}
