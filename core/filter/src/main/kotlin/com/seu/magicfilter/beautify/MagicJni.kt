/**
 * MagicJni.kt - 美颜JNI接口
 *
 * 提供Native层美颜算法的Kotlin封装
 * 包含美白、磨皮等功能的JNI调用
 *
 * 注意：包名必须与Native层JNI函数名匹配
 *
 * @author qihao
 * @since 2.0.0
 */
package com.seu.magicfilter.beautify

import android.graphics.Bitmap
import android.util.Log
import java.nio.ByteBuffer

/**
 * 美颜JNI接口类
 *
 * 封装Native层美颜算法调用
 * 使用单例模式确保Native资源正确管理
 */
object MagicJni {

    private const val TAG = "MagicJni"                    // 日志标签

    init {
        try {
            System.loadLibrary("filter")                   // 加载Native库
            Log.d(TAG, "Native库加载成功")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native库加载失败", e)
        }
    }

    // ==================== JNI Native方法声明 ====================

    /**
     * 初始化美颜处理器
     *
     * @param handler Bitmap数据句柄（来自jniStoreBitmapData）
     */
    external fun jniInitMagicBeautify(handler: ByteBuffer)

    /**
     * 执行美白处理
     *
     * @param whiteLevel 美白等级 (0.0 - 1.0)
     */
    external fun jniStartWhiteSkin(whiteLevel: Float)

    /**
     * 执行磨皮处理
     *
     * @param obj 保留参数
     * @param denoiseLevel 磨皮等级 (0.0 - 1.0)
     */
    external fun jniStartSkinSmooth(obj: Any?, denoiseLevel: Float)

    /**
     * 释放美颜处理器资源
     */
    external fun jniUnInitMagicBeautify()

    /**
     * 存储Bitmap数据到Native层
     *
     * @param bitmap 源Bitmap
     * @return 数据句柄（ByteBuffer）
     */
    external fun jniStoreBitmapData(bitmap: Bitmap): ByteBuffer?

    /**
     * 释放Native层Bitmap数据
     *
     * @param handle 数据句柄
     */
    external fun jniFreeBitmapData(handle: ByteBuffer)

    /**
     * 从Native层获取处理后的Bitmap
     *
     * @param handle 数据句柄
     * @return 处理后的Bitmap
     */
    external fun jniGetBitmapFromStoredBitmapData(handle: ByteBuffer): Bitmap?
}
