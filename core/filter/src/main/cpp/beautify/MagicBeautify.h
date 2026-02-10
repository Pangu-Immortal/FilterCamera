/**
 * MagicBeautify.h - C++美颜算法核心类
 *
 * 提供磨皮和美白功能的Native实现
 * 使用积分图实现快速均值滤波
 * 基于YCbCr色彩空间的肤色检测
 *
 * 安全特性 (v2.0.0):
 * - 图片尺寸安全检查，防止内存溢出
 * - 线程安全的单例模式
 * - 边界保护，避免数组越界
 * - 完善的内存管理
 *
 * @author qihao
 * @since 2.0.0
 */
#ifndef _MAGIC_BEAUTIFY_H_
#define _MAGIC_BEAUTIFY_H_

#include "../bitmap/JniBitmap.h"
#include <mutex>
#include <atomic>

// ==================== 安全限制常量 ====================
// 最大处理边长（超过此尺寸需先缩放）
#define MAX_BEAUTY_DIMENSION 2048
// 最大像素数（约400万像素，防止内存溢出）
#define MAX_BEAUTY_PIXELS 4000000
// 最小处理尺寸（过小图片跳过处理）
#define MIN_BEAUTY_DIMENSION 32

/**
 * 美颜处理结果枚举
 */
enum BeautyResult {
    BEAUTY_SUCCESS = 0,              // 处理成功
    BEAUTY_ERROR_NOT_INITIALIZED,    // 未初始化
    BEAUTY_ERROR_SIZE_TOO_LARGE,     // 图片过大
    BEAUTY_ERROR_SIZE_TOO_SMALL,     // 图片过小
    BEAUTY_ERROR_INVALID_DATA,       // 数据无效
    BEAUTY_ERROR_MEMORY_ALLOC,       // 内存分配失败
    BEAUTY_ERROR_PROCESSING          // 处理过程出错
};

/**
 * 美颜算法核心类
 *
 * 单例模式，线程安全
 * 提供美白和磨皮功能
 */
class MagicBeautify
{
public:
    /**
     * 获取单例实例（线程安全）
     */
    static MagicBeautify* getInstance();

    /**
     * 析构函数
     */
    ~MagicBeautify();

    /**
     * 初始化美颜处理器
     *
     * @param jniBitmap Bitmap数据句柄
     * @return BeautyResult 初始化结果
     */
    BeautyResult initMagicBeautify(JniBitmap* jniBitmap);

    /**
     * 释放美颜处理器资源
     */
    void unInitMagicBeautify();

    /**
     * 磨皮处理
     *
     * @param smoothlevel 磨皮强度 (10.0 - 510.0)
     * @return BeautyResult 处理结果
     */
    BeautyResult startSkinSmooth(float smoothlevel);

    /**
     * 美白处理
     *
     * @param whitenlevel 美白强度 (1.0 - 5.0)
     * @return BeautyResult 处理结果
     */
    BeautyResult startWhiteSkin(float whitenlevel);

    /**
     * 检查图片尺寸是否安全
     *
     * @param width 图片宽度
     * @param height 图片高度
     * @return true表示安全可处理
     */
    static bool isSizeSafe(int width, int height);

    /**
     * 检查是否已初始化
     */
    bool isInitialized() const { return mInitialized.load(); }

private:
    static MagicBeautify* instance;               // 单例实例
    static std::mutex instanceMutex;              // 单例锁

    MagicBeautify();                              // 私有构造函数

    std::mutex mProcessMutex;                     // 处理锁（线程安全）
    std::atomic<bool> mInitialized{false};        // 初始化状态

    uint64_t* mIntegralMatrix;                    // Y通道积分图
    uint64_t* mIntegralMatrixSqr;                 // Y通道平方积分图

    uint32_t* storedBitmapPixels;                 // 输出像素缓冲区
    uint32_t* mImageData_rgb;                     // RGB像素副本

    uint8_t* mImageData_yuv;                      // YCbCr数据
    uint8_t* mSkinMatrix;                         // 肤色蒙版

    int mImageWidth;                              // 图片宽度
    int mImageHeight;                             // 图片高度
    float mSmoothLevel;                           // 当前磨皮强度
    float mWhitenLevel;                           // 当前美白强度

    /**
     * 初始化积分图
     */
    void initIntegral();

    /**
     * 初始化肤色蒙版
     */
    void initSkinMatrix();

    /**
     * 执行美颜处理
     */
    BeautyResult _startBeauty(float smoothlevel, float whitenlevel);

    /**
     * 执行磨皮处理
     */
    void _startSkinSmooth(float smoothlevel);

    /**
     * 执行美白处理
     */
    void _startWhiteSkin(float whitenlevel);

    /**
     * 释放所有内存
     */
    void freeAllMemory();

    /**
     * 安全边界索引
     */
    inline int safeIndex(int x, int y) const {
        x = (x < 0) ? 0 : (x >= mImageWidth ? mImageWidth - 1 : x);
        y = (y < 0) ? 0 : (y >= mImageHeight ? mImageHeight - 1 : y);
        return y * mImageWidth + x;
    }
};
#endif
