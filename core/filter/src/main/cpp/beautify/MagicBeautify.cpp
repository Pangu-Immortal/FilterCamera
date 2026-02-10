/**
 * MagicBeautify.cpp - C++美颜算法实现
 *
 * 实现磨皮和美白功能
 * 使用积分图实现O(1)复杂度的均值滤波
 * 基于YCbCr色彩空间的肤色检测
 *
 * 安全增强 (v2.0.0):
 * - 线程安全的单例实现
 * - 图片尺寸安全检查
 * - 边界保护
 * - 内存泄漏修复
 *
 * @author qihao
 * @since 2.0.0
 */
#include "MagicBeautify.h"
#include <cmath>
#include <algorithm>
#include "../bitmap/BitmapOperation.h"
#include "../bitmap/Conversion.h"

#define LOG_TAG    "MagicBeautify"
#define LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...)  __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...)  __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...)  __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 辅助宏
#define div255(x) ((x) * 0.003921568627451F)  // x / 255.0
#define clamp(x, minVal, maxVal) (std::max((minVal), std::min((maxVal), (x))))

// 静态成员初始化
MagicBeautify* MagicBeautify::instance = nullptr;
std::mutex MagicBeautify::instanceMutex;

/**
 * 获取单例实例（线程安全的双检锁）
 */
MagicBeautify* MagicBeautify::getInstance() {
    if (instance == nullptr) {
        std::lock_guard<std::mutex> lock(instanceMutex);
        if (instance == nullptr) {
            instance = new MagicBeautify();
        }
    }
    return instance;
}

/**
 * 构造函数 - 初始化所有成员为nullptr
 */
MagicBeautify::MagicBeautify() {
    LOGI("MagicBeautify: 构造函数");
    mIntegralMatrix = nullptr;
    mIntegralMatrixSqr = nullptr;
    mImageData_yuv = nullptr;
    mSkinMatrix = nullptr;
    mImageData_rgb = nullptr;
    storedBitmapPixels = nullptr;
    mImageWidth = 0;
    mImageHeight = 0;
    mSmoothLevel = 0.0f;
    mWhitenLevel = 0.0f;
    mInitialized.store(false);
}

/**
 * 析构函数 - 释放所有资源
 */
MagicBeautify::~MagicBeautify() {
    LOGI("~MagicBeautify: 析构函数");
    freeAllMemory();
}

/**
 * 释放所有动态分配的内存
 */
void MagicBeautify::freeAllMemory() {
    if (mIntegralMatrix != nullptr) {
        delete[] mIntegralMatrix;
        mIntegralMatrix = nullptr;
    }
    if (mIntegralMatrixSqr != nullptr) {
        delete[] mIntegralMatrixSqr;
        mIntegralMatrixSqr = nullptr;
    }
    if (mImageData_yuv != nullptr) {
        delete[] mImageData_yuv;
        mImageData_yuv = nullptr;
    }
    if (mSkinMatrix != nullptr) {
        delete[] mSkinMatrix;
        mSkinMatrix = nullptr;
    }
    if (mImageData_rgb != nullptr) {
        delete[] mImageData_rgb;
        mImageData_rgb = nullptr;
    }
    // 注意：storedBitmapPixels 由外部 JniBitmap 管理，不在这里释放
}

/**
 * 检查图片尺寸是否安全
 */
bool MagicBeautify::isSizeSafe(int width, int height) {
    // 检查基本范围
    if (width < MIN_BEAUTY_DIMENSION || height < MIN_BEAUTY_DIMENSION) {
        LOGW("isSizeSafe: 图片过小 %dx%d < %d", width, height, MIN_BEAUTY_DIMENSION);
        return false;
    }

    if (width > MAX_BEAUTY_DIMENSION || height > MAX_BEAUTY_DIMENSION) {
        LOGW("isSizeSafe: 图片过大 %dx%d > %d", width, height, MAX_BEAUTY_DIMENSION);
        return false;
    }

    // 检查总像素数
    long long pixels = (long long)width * height;
    if (pixels > MAX_BEAUTY_PIXELS) {
        LOGW("isSizeSafe: 像素过多 %lld > %d", pixels, MAX_BEAUTY_PIXELS);
        return false;
    }

    return true;
}

/**
 * 初始化美颜处理器
 */
BeautyResult MagicBeautify::initMagicBeautify(JniBitmap* jniBitmap) {
    std::lock_guard<std::mutex> lock(mProcessMutex);
    LOGI("initMagicBeautify: 开始初始化");

    // 参数检查
    if (jniBitmap == nullptr) {
        LOGE("initMagicBeautify: jniBitmap为null");
        return BEAUTY_ERROR_INVALID_DATA;
    }

    if (jniBitmap->_storedBitmapPixels == nullptr) {
        LOGE("initMagicBeautify: 像素数据为null");
        return BEAUTY_ERROR_INVALID_DATA;
    }

    // 获取尺寸
    int width = jniBitmap->_bitmapInfo.width;
    int height = jniBitmap->_bitmapInfo.height;

    // 尺寸安全检查
    if (!isSizeSafe(width, height)) {
        LOGE("initMagicBeautify: 图片尺寸不安全 %dx%d", width, height);
        return BEAUTY_ERROR_SIZE_TOO_LARGE;
    }

    // 如果之前已初始化，先释放旧资源
    freeAllMemory();

    // 保存指针和尺寸
    storedBitmapPixels = jniBitmap->_storedBitmapPixels;
    mImageWidth = width;
    mImageHeight = height;

    long long pixelCount = (long long)mImageWidth * mImageHeight;
    LOGI("initMagicBeautify: 图片尺寸 %dx%d (%lld像素)", mImageWidth, mImageHeight, pixelCount);

    // 分配内存 - 使用try-catch捕获内存分配失败
    try {
        mImageData_rgb = new uint32_t[pixelCount];
        mImageData_yuv = new uint8_t[pixelCount * 3];
        mSkinMatrix = new uint8_t[pixelCount];
        mIntegralMatrix = new uint64_t[pixelCount];
        mIntegralMatrixSqr = new uint64_t[pixelCount];
    } catch (const std::bad_alloc& e) {
        LOGE("initMagicBeautify: 内存分配失败 - %s", e.what());
        freeAllMemory();
        return BEAUTY_ERROR_MEMORY_ALLOC;
    }

    // 复制像素数据
    memcpy(mImageData_rgb, jniBitmap->_storedBitmapPixels, sizeof(uint32_t) * pixelCount);

    // RGB转YCbCr
    Conversion::RGBToYCbCr((uint8_t*)mImageData_rgb, mImageData_yuv, pixelCount);

    // 初始化肤色蒙版和积分图
    initSkinMatrix();
    initIntegral();

    mInitialized.store(true);
    LOGI("initMagicBeautify: 初始化完成");
    return BEAUTY_SUCCESS;
}

/**
 * 释放美颜处理器资源
 */
void MagicBeautify::unInitMagicBeautify() {
    std::lock_guard<std::mutex> lock(instanceMutex);
    LOGI("unInitMagicBeautify: 释放资源");

    if (instance != nullptr) {
        instance->freeAllMemory();
        instance->mInitialized.store(false);
        instance->storedBitmapPixels = nullptr;
        instance->mImageWidth = 0;
        instance->mImageHeight = 0;
        delete instance;
        instance = nullptr;
    }
}

/**
 * 磨皮处理入口
 */
BeautyResult MagicBeautify::startSkinSmooth(float smoothlevel) {
    return _startBeauty(smoothlevel, mWhitenLevel);
}

/**
 * 美白处理入口
 */
BeautyResult MagicBeautify::startWhiteSkin(float whitenlevel) {
    return _startBeauty(mSmoothLevel, whitenlevel);
}

/**
 * 执行美颜处理
 */
BeautyResult MagicBeautify::_startBeauty(float smoothlevel, float whitenlevel) {
    std::lock_guard<std::mutex> lock(mProcessMutex);

    if (!mInitialized.load()) {
        LOGE("_startBeauty: 未初始化");
        return BEAUTY_ERROR_NOT_INITIALIZED;
    }

    LOGD("_startBeauty: smooth=%.2f, white=%.2f", smoothlevel, whitenlevel);

    // 磨皮处理
    if (smoothlevel >= 10.0f && smoothlevel <= 510.0f) {
        mSmoothLevel = smoothlevel;
        _startSkinSmooth(mSmoothLevel);
    }

    // 美白处理
    if (whitenlevel >= 1.0f && whitenlevel <= 5.0f) {
        mWhitenLevel = whitenlevel;
        _startWhiteSkin(mWhitenLevel);
    }

    return BEAUTY_SUCCESS;
}

/**
 * 美白处理实现
 * 使用对数曲线提亮肤色
 */
void MagicBeautify::_startWhiteSkin(float whitenlevel) {
    if (storedBitmapPixels == nullptr || mImageData_rgb == nullptr) {
        LOGE("_startWhiteSkin: 数据为null");
        return;
    }

    float a = logf(whitenlevel);
    if (a == 0.0f) {
        LOGW("_startWhiteSkin: 美白参数无效");
        return;
    }

    int pixelCount = mImageWidth * mImageHeight;
    for (int i = 0; i < pixelCount; i++) {
        ARGB RGB;
        BitmapOperation::convertIntToArgb(mImageData_rgb[i], &RGB);

        // 对数曲线美白
        float r = 255.0f * (logf(div255(RGB.red) * (whitenlevel - 1.0f) + 1.0f) / a);
        float g = 255.0f * (logf(div255(RGB.green) * (whitenlevel - 1.0f) + 1.0f) / a);
        float b = 255.0f * (logf(div255(RGB.blue) * (whitenlevel - 1.0f) + 1.0f) / a);

        // 限制在0-255范围内
        RGB.red = (uint8_t)clamp((int)r, 0, 255);
        RGB.green = (uint8_t)clamp((int)g, 0, 255);
        RGB.blue = (uint8_t)clamp((int)b, 0, 255);

        storedBitmapPixels[i] = BitmapOperation::convertArgbToInt(RGB);
    }

    LOGD("_startWhiteSkin: 完成");
}

/**
 * 磨皮处理实现
 * 使用积分图实现快速均值滤波（仅对肤色区域）
 */
void MagicBeautify::_startSkinSmooth(float smoothlevel) {
    if (mIntegralMatrix == nullptr || mIntegralMatrixSqr == nullptr ||
        mSkinMatrix == nullptr || mImageData_yuv == nullptr ||
        storedBitmapPixels == nullptr) {
        LOGE("_startSkinSmooth: 数据未初始化");
        return;
    }

    // 重新计算YCbCr数据（可能被美白处理修改了RGB）
    int pixelCount = mImageWidth * mImageHeight;
    Conversion::RGBToYCbCr((uint8_t*)mImageData_rgb, mImageData_yuv, pixelCount);

    // 计算模糊半径（图片越大，半径越大）
    int radius = (int)(std::max(mImageWidth, mImageHeight) * 0.02f);
    radius = clamp(radius, 2, 20);  // 限制半径范围

    LOGD("_startSkinSmooth: radius=%d, smoothlevel=%.2f", radius, smoothlevel);

    // 遍历每个像素
    for (int i = 1; i < mImageHeight - 1; i++) {
        for (int j = 1; j < mImageWidth - 1; j++) {
            int offset = i * mImageWidth + j;

            // 仅处理肤色区域
            if (mSkinMatrix[offset] != 255) {
                continue;
            }

            // 计算积分图区域边界（带边界保护）
            int iMax = std::min(i + radius, mImageHeight - 1);
            int jMax = std::min(j + radius, mImageWidth - 1);
            int iMin = std::max(i - radius, 1);
            int jMin = std::max(j - radius, 1);

            int area = (iMax - iMin + 1) * (jMax - jMin + 1);
            if (area <= 0) continue;

            // 积分图索引（使用安全索引）
            int idx4 = iMax * mImageWidth + jMax;
            int idx3 = (iMin - 1) * mImageWidth + (jMin - 1);
            int idx2 = iMax * mImageWidth + (jMin - 1);
            int idx1 = (iMin - 1) * mImageWidth + jMax;

            // 边界检查
            if (idx4 >= pixelCount || idx3 < 0 || idx2 >= pixelCount || idx1 < 0) {
                continue;
            }

            // 计算区域均值
            float m = (float)(mIntegralMatrix[idx4] + mIntegralMatrix[idx3]
                              - mIntegralMatrix[idx2] - mIntegralMatrix[idx1]) / area;

            // 计算区域方差
            float v = (float)(mIntegralMatrixSqr[idx4] + mIntegralMatrixSqr[idx3]
                              - mIntegralMatrixSqr[idx2] - mIntegralMatrixSqr[idx1]) / area - m * m;

            // 计算混合系数（方差越大保留越多细节）
            float k = v / (v + smoothlevel);

            // 应用磨皮（仅修改Y通道）
            float newY = m - k * m + k * (float)mImageData_yuv[offset * 3];
            mImageData_yuv[offset * 3] = (uint8_t)clamp((int)ceilf(newY), 0, 255);
        }
    }

    // YCbCr转回RGB
    Conversion::YCbCrToRGB(mImageData_yuv, (uint8_t*)storedBitmapPixels, pixelCount);

    LOGD("_startSkinSmooth: 完成");
}

/**
 * 初始化肤色蒙版
 * 使用RGB规则检测肤色区域
 */
void MagicBeautify::initSkinMatrix() {
    if (mSkinMatrix == nullptr || mImageData_rgb == nullptr) {
        LOGE("initSkinMatrix: 数据为null");
        return;
    }

    LOGD("initSkinMatrix: 开始");

    int pixelCount = mImageWidth * mImageHeight;
    for (int i = 0; i < pixelCount; i++) {
        ARGB RGB;
        BitmapOperation::convertIntToArgb(mImageData_rgb[i], &RGB);

        // 肤色检测规则（基于RGB）
        bool isSkin = false;

        // 规则1：典型肤色范围
        if (RGB.blue > 95 && RGB.green > 40 && RGB.red > 20 &&
            (int)RGB.blue - RGB.red > 15 && (int)RGB.blue - RGB.green > 15) {
            isSkin = true;
        }
        // 规则2：白皙肤色
        else if (RGB.blue > 200 && RGB.green > 210 && RGB.red > 170 &&
                 abs((int)RGB.blue - RGB.red) <= 15 &&
                 RGB.blue > RGB.red && RGB.green > RGB.red) {
            isSkin = true;
        }

        mSkinMatrix[i] = isSkin ? 255 : 0;
    }

    LOGD("initSkinMatrix: 完成");
}

/**
 * 初始化积分图
 * 用于快速计算任意矩形区域的和
 */
void MagicBeautify::initIntegral() {
    if (mIntegralMatrix == nullptr || mIntegralMatrixSqr == nullptr ||
        mImageData_yuv == nullptr) {
        LOGE("initIntegral: 数据为null");
        return;
    }

    LOGD("initIntegral: 开始");

    // 临时列累加数组
    uint64_t* columnSum = new uint64_t[mImageWidth]();
    uint64_t* columnSumSqr = new uint64_t[mImageWidth]();

    // 初始化第一行
    for (int j = 0; j < mImageWidth; j++) {
        uint8_t y = mImageData_yuv[j * 3];  // Y通道
        columnSum[j] = y;
        columnSumSqr[j] = (uint64_t)y * y;

        if (j == 0) {
            mIntegralMatrix[j] = columnSum[j];
            mIntegralMatrixSqr[j] = columnSumSqr[j];
        } else {
            mIntegralMatrix[j] = mIntegralMatrix[j - 1] + columnSum[j];
            mIntegralMatrixSqr[j] = mIntegralMatrixSqr[j - 1] + columnSumSqr[j];
        }
    }

    // 计算剩余行
    for (int i = 1; i < mImageHeight; i++) {
        int rowOffset = i * mImageWidth;

        for (int j = 0; j < mImageWidth; j++) {
            int offset = rowOffset + j;
            uint8_t y = mImageData_yuv[offset * 3];

            columnSum[j] += y;
            columnSumSqr[j] += (uint64_t)y * y;

            if (j == 0) {
                mIntegralMatrix[offset] = columnSum[j];
                mIntegralMatrixSqr[offset] = columnSumSqr[j];
            } else {
                mIntegralMatrix[offset] = mIntegralMatrix[offset - 1] + columnSum[j];
                mIntegralMatrixSqr[offset] = mIntegralMatrixSqr[offset - 1] + columnSumSqr[j];
            }
        }
    }

    delete[] columnSum;
    delete[] columnSumSqr;

    LOGD("initIntegral: 完成");
}
