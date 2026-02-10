/**
 * MagicJni.cpp - JNI接口层
 *
 * 提供Java/Kotlin与C++美颜算法之间的桥接
 * 包含安全的异常处理和输入验证
 *
 * 安全特性 (v2.0.0):
 * - 完整的异常捕获，防止Native崩溃传递到JVM
 * - 输入参数验证
 * - 详细的错误日志
 *
 * @author qihao
 * @since 2.0.0
 */
#include <jni.h>
#include <android/log.h>
#include <stdio.h>
#include <exception>
#include "bitmap/BitmapOperation.h"
#include "beautify/MagicBeautify.h"

#define LOG_TAG    "MagicJni"
#define LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...)  __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...)  __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...)  __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#ifdef __cplusplus
extern "C" {
#endif

/**
 * 初始化美颜处理器
 *
 * @param env JNI环境
 * @param instance 调用对象
 * @param handler Bitmap数据句柄（DirectByteBuffer）
 */
JNIEXPORT void JNICALL
Java_com_seu_magicfilter_beautify_MagicJni_jniInitMagicBeautify(
        JNIEnv *env,
        jobject instance,
        jobject handler) {
    LOGI("jniInitMagicBeautify: 开始");

    try {
        // 参数验证
        if (handler == nullptr) {
            LOGE("jniInitMagicBeautify: handler为null");
            return;
        }

        // 获取JniBitmap指针
        JniBitmap *jniBitmap = (JniBitmap *) env->GetDirectBufferAddress(handler);
        if (jniBitmap == nullptr) {
            LOGE("jniInitMagicBeautify: 无法获取DirectBuffer地址");
            return;
        }

        if (jniBitmap->_storedBitmapPixels == nullptr) {
            LOGE("jniInitMagicBeautify: 像素数据为null");
            return;
        }

        // 初始化美颜处理器
        BeautyResult result = MagicBeautify::getInstance()->initMagicBeautify(jniBitmap);
        if (result != BEAUTY_SUCCESS) {
            LOGE("jniInitMagicBeautify: 初始化失败，错误码=%d", result);
        } else {
            LOGI("jniInitMagicBeautify: 初始化成功");
        }
    } catch (const std::exception& e) {
        LOGE("jniInitMagicBeautify: C++异常 - %s", e.what());
    } catch (...) {
        LOGE("jniInitMagicBeautify: 未知异常");
    }
}

/**
 * 执行美白处理
 *
 * @param env JNI环境
 * @param instance 调用对象
 * @param whiteLevel 美白强度 (0.0 - 1.0，内部转换为1.0 - 5.0)
 */
JNIEXPORT void JNICALL
Java_com_seu_magicfilter_beautify_MagicJni_jniStartWhiteSkin(
        JNIEnv *env,
        jobject instance,
        jfloat whiteLevel) {
    LOGD("jniStartWhiteSkin: level=%.2f", whiteLevel);

    try {
        // 参数转换：输入0.0-1.0 -> Native需要1.0-5.0
        float nativeLevel = 1.0f + whiteLevel * 4.0f;
        nativeLevel = std::max(1.0f, std::min(5.0f, nativeLevel));

        BeautyResult result = MagicBeautify::getInstance()->startWhiteSkin(nativeLevel);
        if (result != BEAUTY_SUCCESS) {
            LOGW("jniStartWhiteSkin: 处理返回错误码=%d", result);
        }
    } catch (const std::exception& e) {
        LOGE("jniStartWhiteSkin: C++异常 - %s", e.what());
    } catch (...) {
        LOGE("jniStartWhiteSkin: 未知异常");
    }
}

/**
 * 执行磨皮处理
 *
 * @param env JNI环境
 * @param instance 调用对象
 * @param obj 保留参数（未使用）
 * @param denoiseLevel 磨皮强度 (0.0 - 1.0，内部转换为10.0 - 510.0)
 */
JNIEXPORT void JNICALL
Java_com_seu_magicfilter_beautify_MagicJni_jniStartSkinSmooth(
        JNIEnv *env,
        jobject instance,
        jobject obj,
        jfloat denoiseLevel) {
    LOGD("jniStartSkinSmooth: level=%.2f", denoiseLevel);

    try {
        // 参数转换：输入0.0-1.0 -> 计算sigma -> Native需要10.0-510.0
        // sigema = 10 + DenoiseLevel * DenoiseLevel * 5
        // 当denoiseLevel=0时，sigema=10
        // 当denoiseLevel=1时，sigema=10+1*1*5=15（这个公式太弱了）
        // 调整为：sigema = 10 + denoiseLevel * 500
        float sigema = 10.0f + denoiseLevel * 500.0f;
        sigema = std::max(10.0f, std::min(510.0f, sigema));

        BeautyResult result = MagicBeautify::getInstance()->startSkinSmooth(sigema);
        if (result != BEAUTY_SUCCESS) {
            LOGW("jniStartSkinSmooth: 处理返回错误码=%d", result);
        }
    } catch (const std::exception& e) {
        LOGE("jniStartSkinSmooth: C++异常 - %s", e.what());
    } catch (...) {
        LOGE("jniStartSkinSmooth: 未知异常");
    }
}

/**
 * 释放美颜处理器资源
 *
 * @param env JNI环境
 * @param instance 调用对象
 */
JNIEXPORT void JNICALL
Java_com_seu_magicfilter_beautify_MagicJni_jniUnInitMagicBeautify(
        JNIEnv *env,
        jobject instance) {
    LOGI("jniUnInitMagicBeautify: 释放资源");

    try {
        MagicBeautify::getInstance()->unInitMagicBeautify();
        LOGI("jniUnInitMagicBeautify: 释放完成");
    } catch (const std::exception& e) {
        LOGE("jniUnInitMagicBeautify: C++异常 - %s", e.what());
    } catch (...) {
        LOGE("jniUnInitMagicBeautify: 未知异常");
    }
}

/**
 * 存储Bitmap数据到Native层
 *
 * @param env JNI环境
 * @param instance 调用对象
 * @param bitmap Java Bitmap对象
 * @return DirectByteBuffer句柄，失败返回null
 */
JNIEXPORT jobject JNICALL
Java_com_seu_magicfilter_beautify_MagicJni_jniStoreBitmapData(
        JNIEnv *env,
        jobject instance,
        jobject bitmap) {
    LOGI("jniStoreBitmapData: 开始");

    try {
        // 参数验证
        if (bitmap == nullptr) {
            LOGE("jniStoreBitmapData: bitmap为null");
            return nullptr;
        }

        jobject result = BitmapOperation::jniStoreBitmapData(env, instance, bitmap);
        if (result != nullptr) {
            LOGI("jniStoreBitmapData: 存储成功");
        } else {
            LOGE("jniStoreBitmapData: 存储失败");
        }
        return result;
    } catch (const std::exception& e) {
        LOGE("jniStoreBitmapData: C++异常 - %s", e.what());
        return nullptr;
    } catch (...) {
        LOGE("jniStoreBitmapData: 未知异常");
        return nullptr;
    }
}

/**
 * 释放Native层Bitmap数据
 *
 * @param env JNI环境
 * @param instance 调用对象
 * @param handle 数据句柄
 */
JNIEXPORT void JNICALL
Java_com_seu_magicfilter_beautify_MagicJni_jniFreeBitmapData(
        JNIEnv *env,
        jobject instance,
        jobject handle) {
    LOGI("jniFreeBitmapData: 释放数据");

    try {
        if (handle == nullptr) {
            LOGW("jniFreeBitmapData: handle为null，跳过");
            return;
        }

        BitmapOperation::jniFreeBitmapData(env, instance, handle);
        LOGI("jniFreeBitmapData: 释放完成");
    } catch (const std::exception& e) {
        LOGE("jniFreeBitmapData: C++异常 - %s", e.what());
    } catch (...) {
        LOGE("jniFreeBitmapData: 未知异常");
    }
}

/**
 * 从Native层获取处理后的Bitmap
 *
 * @param env JNI环境
 * @param instance 调用对象
 * @param handle 数据句柄
 * @return Java Bitmap对象，失败返回null
 */
JNIEXPORT jobject JNICALL
Java_com_seu_magicfilter_beautify_MagicJni_jniGetBitmapFromStoredBitmapData(
        JNIEnv *env,
        jobject instance,
        jobject handle) {
    LOGI("jniGetBitmapFromStoredBitmapData: 获取结果");

    try {
        if (handle == nullptr) {
            LOGE("jniGetBitmapFromStoredBitmapData: handle为null");
            return nullptr;
        }

        jobject result = BitmapOperation::jniGetBitmapFromStoredBitmapData(env, instance, handle);
        if (result != nullptr) {
            LOGI("jniGetBitmapFromStoredBitmapData: 获取成功");
        } else {
            LOGE("jniGetBitmapFromStoredBitmapData: 获取失败");
        }
        return result;
    } catch (const std::exception& e) {
        LOGE("jniGetBitmapFromStoredBitmapData: C++异常 - %s", e.what());
        return nullptr;
    } catch (...) {
        LOGE("jniGetBitmapFromStoredBitmapData: 未知异常");
        return nullptr;
    }
}

#ifdef __cplusplus
}
#endif
