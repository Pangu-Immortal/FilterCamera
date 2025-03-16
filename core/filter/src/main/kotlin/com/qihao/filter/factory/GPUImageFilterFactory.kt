/**
 * GPUImageFilterFactory.kt - GPUImage滤镜工厂
 *
 * 将FilterType枚举映射到GPUImage库的具体滤镜类
 * 支持72种滤镜效果的创建和参数配置
 *
 * 技术实现：
 * - 使用GPUImage库的预置滤镜
 * - 部分滤镜使用LUT(查找表)实现
 * - 支持滤镜参数动态调整
 * - 优先使用 FilterRegistry 注册表模式
 *
 * 架构说明：
 * - 新增滤镜请在 FilterRegistrations.kt 中注册
 * - 此文件的 switch 语句作为向后兼容的 fallback
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filter.factory

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.qihao.filter.registry.FilterRegistrations
import com.qihao.filter.registry.FilterRegistry
import jp.co.cyberagent.android.gpuimage.filter.*

/**
 * GPUImage滤镜工厂类
 *
 * 负责根据FilterType创建对应的GPUImageFilter实例
 * 优先从 FilterRegistry 获取，不存在时使用本地 switch 语句
 */
object GPUImageFilterFactory {

    private const val TAG = "GPUImageFilterFactory"  // 日志标签

    /**
     * 确保注册表已初始化
     */
    private fun ensureInitialized() {
        if (!FilterRegistry.isInitialized()) {
            Log.d(TAG, "ensureInitialized: 初始化滤镜注册表")
            FilterRegistrations.initializeAll()
        }
    }

    /**
     * 根据滤镜类型名称创建GPUImage滤镜
     *
     * @param filterTypeName FilterType枚举的name属性
     * @param context 上下文，用于加载LUT资源（可选）
     * @return GPUImageFilter实例
     */
    fun createFilter(filterTypeName: String, context: Context? = null): GPUImageFilter {
        Log.d(TAG, "createFilter: 创建滤镜 filterTypeName=$filterTypeName")

        // 1. 确保注册表已初始化
        ensureInitialized()

        // 2. 优先从注册表获取
        if (FilterRegistry.isRegistered(filterTypeName)) {
            Log.d(TAG, "createFilter: 从注册表创建滤镜")
            return FilterRegistry.createFilter(filterTypeName)
        }

        // 3. Fallback: 使用本地 switch 语句（向后兼容）
        Log.d(TAG, "createFilter: 注册表未找到，使用 fallback 逻辑")

        return when (filterTypeName) {
            // ========== 原图（无滤镜）==========
            "NONE" -> GPUImageFilter()                                         // 原图，无任何处理

            // ========== 风格滤镜（19种）==========
            "FAIRYTALE" -> createFairytaleFilter()                             // 童话：梦幻色调
            "SUNRISE" -> createSunriseFilter()                                 // 日出：暖金色调
            "SUNSET" -> createSunsetFilter()                                   // 日落：橙红色调
            "WHITECAT" -> createWhitecatFilter()                               // 白猫：高光增强
            "BLACKCAT" -> createBlackcatFilter()                               // 黑猫：暗部增强
            "SKINWHITEN" -> createSkinWhitenFilter()                           // 美白：肤色美白
            "HEALTHY" -> createHealthyFilter()                                 // 健康：健康肤色
            "SWEETS" -> createSweetsFilter()                                   // 甜蜜：粉嫩色调
            "ROMANCE" -> createRomanceFilter()                                 // 浪漫：浪漫紫粉
            "SAKURA" -> createSakuraFilter()                                   // 樱花：樱花粉色
            "WARM" -> createWarmFilter()                                       // 温暖：暖色调
            "ANTIQUE" -> createAntiqueFilter()                                 // 复古：复古褐色
            "NOSTALGIA" -> createNostalgiaFilter()                             // 怀旧：怀旧胶片
            "CALM" -> createCalmFilter()                                       // 平静：淡雅色调
            "LATTE" -> createLatteFilter()                                     // 拿铁：咖啡色调
            "TENDER" -> createTenderFilter()                                   // 柔和：柔和色调
            "COOL" -> createCoolFilter()                                       // 清凉：冷色调
            "EMERALD" -> createEmeraldFilter()                                 // 翡翠：翠绿色调
            "EVERGREEN" -> createEvergreenFilter()                             // 常青：常青绿色

            // ========== 特效滤镜（22种）==========
            "CRAYON" -> createCrayonFilter()                                   // 蜡笔：边缘检测+颜色量化
            "SKETCH" -> GPUImageSketchFilter()                                 // 素描：Sobel边缘+反色
            "SWIRL" -> GPUImageSwirlFilter().apply {                           // 漩涡：极坐标旋转
                setAngle(1.0f)
                setRadius(0.5f)
            }
            "BULGE" -> GPUImageBulgeDistortionFilter().apply {                 // 鱼眼：径向凸出
                setRadius(0.25f)
                setScale(0.5f)
            }
            "PINCH" -> GPUImageSphereRefractionFilter().apply {                // 捏缩：使用球面折射模拟
                setRadius(0.25f)
                setRefractiveIndex(0.5f)
            }
            "STRETCH" -> GPUImageSwirlFilter().apply {                         // 拉伸：使用漩涡模拟
                setAngle(0.3f)
                setRadius(0.8f)
            }
            "GLASS_SPHERE" -> GPUImageGlassSphereFilter().apply {              // 玻璃球：球面折射
                setRadius(0.25f)
                setRefractiveIndex(0.71f)
            }
            "PIXELATE" -> GPUImagePixelationFilter().apply {                   // 像素化：方块采样
                setPixel(10f)
            }
            "HALFTONE" -> GPUImageHalftoneFilter().apply {                     // 半色调：网点图案
                setFractionalWidthOfAPixel(0.01f)
            }
            "CROSSHATCH" -> GPUImageCrosshatchFilter().apply {                 // 交叉线：线条密度映射
                setCrossHatchSpacing(0.03f)
                setLineWidth(0.003f)
            }
            "POLKA_DOT" -> GPUImageHalftoneFilter().apply {                    // 波点：使用半色调模拟
                setFractionalWidthOfAPixel(0.02f)
            }
            "MOSAIC" -> createMosaicFilter()                                   // 马赛克：瓷砖纹理
            "KUWAHARA" -> GPUImageKuwaharaFilter().apply {                     // 油画：Kuwahara滤波
                setRadius(6)
            }
            "POSTERIZE" -> GPUImagePosterizeFilter().apply {                   // 色调分离：颜色量化
                setColorLevels(10)
            }
            "EMBOSS" -> GPUImageEmbossFilter().apply {                         // 浮雕：卷积浮雕
                setIntensity(1.0f)
            }
            "TOON" -> GPUImageToonFilter().apply {                             // 卡通：Sobel+量化
                setThreshold(0.2f)
                setQuantizationLevels(10.0f)
            }
            "SMOOTH_TOON" -> GPUImageSmoothToonFilter().apply {                // 平滑卡通：高斯+卡通
                setBlurSize(0.5f)
                setThreshold(0.2f)
                setQuantizationLevels(10.0f)
            }
            "TILT_SHIFT" -> GPUImageGaussianBlurFilter().apply {               // 移轴：使用高斯模糊模拟
                setBlurSize(2.0f)
            }
            "MOTION_BLUR" -> createMotionBlurFilter()                          // 动态模糊：方向模糊
            "ZOOM_BLUR" -> GPUImageZoomBlurFilter().apply {                    // 缩放模糊：径向模糊
                setBlurSize(2.0f)
            }
            "VIGNETTE" -> GPUImageVignetteFilter().apply {                     // 暗角：轻微边缘衰减
                setVignetteStart(0.5f)                                        // 增大起始值，减少暗角范围
                setVignetteEnd(0.95f)                                         // 增大结束值，使暗角更柔和
            }
            "SOBEL_EDGE" -> GPUImageSobelEdgeDetectionFilter()                 // 边缘检测：Sobel算子

            // ========== Instagram风格滤镜（20种）==========
            "AMARO" -> createAmaroFilter()                                     // Amaro
            "BRANNAN" -> createBrannanFilter()                                 // Brannan
            "BROOKLYN" -> createBrooklynFilter()                               // Brooklyn
            "EARLYBIRD" -> createEarlybirdFilter()                             // Earlybird
            "FREUD" -> createFreudFilter()                                     // Freud
            "HEFE" -> createHefeFilter()                                       // Hefe
            "HUDSON" -> createHudsonFilter()                                   // Hudson
            "INKWELL" -> GPUImageGrayscaleFilter()                             // Inkwell：经典黑白
            "KEVIN" -> createKevinFilter()                                     // Kevin
            "LOMO" -> createLomoFilter()                                       // Lomo
            "N1977" -> create1977Filter()                                      // 1977
            "NASHVILLE" -> createNashvilleFilter()                             // Nashville
            "PIXAR" -> createPixarFilter()                                     // Pixar
            "RISE" -> createRiseFilter()                                       // Rise
            "SIERRA" -> createSierraFilter()                                   // Sierra
            "SUTRO" -> createSutroFilter()                                     // Sutro
            "TOASTER" -> createToasterFilter()                                 // Toaster
            "VALENCIA" -> createValenciaFilter()                               // Valencia
            "WALDEN" -> createWaldenFilter()                                   // Walden
            "XPROII" -> createXProIIFilter()                                   // X-Pro II

            // ========== 图像调整（7种）==========
            "CONTRAST" -> GPUImageContrastFilter().apply { setContrast(1.5f) } // 对比度
            "BRIGHTNESS" -> GPUImageBrightnessFilter().apply { setBrightness(0.1f) } // 亮度
            "EXPOSURE" -> GPUImageExposureFilter().apply { setExposure(0.5f) } // 曝光
            "HUE" -> GPUImageHueFilter().apply { setHue(90f) }                 // 色调
            "SATURATION" -> GPUImageSaturationFilter().apply { setSaturation(1.5f) } // 饱和度
            "SHARPEN" -> GPUImageSharpenFilter().apply { setSharpness(1.0f) }  // 锐度
            "IMAGE_ADJUST" -> GPUImageFilter()                                 // 综合调整（占位）

            // ========== 水印类型（不使用GPU，返回空滤镜）==========
            "WATERMARK_TIMESTAMP", "WATERMARK_DATE",
            "WATERMARK_DEVICE", "WATERMARK_CUSTOM" -> GPUImageFilter()        // 水印使用Canvas绘制

            // ========== 默认 ==========
            else -> {
                Log.w(TAG, "createFilter: 未知滤镜类型 $filterTypeName，返回原图滤镜")
                GPUImageFilter()
            }
        }
    }

    // ==================== 风格滤镜创建方法 ====================

    /**
     * 童话滤镜 - 梦幻色调
     * 实现：增加饱和度+柔光效果+粉色色调
     */
    private fun createFairytaleFilter(): GPUImageFilter {
        return GPUImageFilterGroup(listOf(
            GPUImageSaturationFilter().apply { setSaturation(1.3f) },          // 增加饱和度
            GPUImageRGBFilter().apply {                                        // 粉色色调
                setRed(1.1f)
                setGreen(1.0f)
                setBlue(1.1f)
            },
            GPUImageBrightnessFilter().apply { setBrightness(0.05f) }          // 轻微提亮
        ))
    }

    /**
     * 日出滤镜 - 暖金色调
     */
    private fun createSunriseFilter(): GPUImageFilter {
        return GPUImageFilterGroup(listOf(
            GPUImageRGBFilter().apply {
                setRed(1.2f)
                setGreen(1.1f)
                setBlue(0.9f)
            },
            GPUImageContrastFilter().apply { setContrast(1.1f) }
        ))
    }

    /**
     * 日落滤镜 - 橙红色调
     */
    private fun createSunsetFilter(): GPUImageFilter {
        return GPUImageFilterGroup(listOf(
            GPUImageRGBFilter().apply {
                setRed(1.3f)
                setGreen(1.0f)
                setBlue(0.8f)
            },
            GPUImageSaturationFilter().apply { setSaturation(1.2f) },
            GPUImageVignetteFilter().apply {
                setVignetteStart(0.4f)
                setVignetteEnd(0.85f)
            }
        ))
    }

    /**
     * 白猫滤镜 - 高光增强
     */
    private fun createWhitecatFilter(): GPUImageFilter {
        return GPUImageFilterGroup(listOf(
            GPUImageBrightnessFilter().apply { setBrightness(0.15f) },
            GPUImageContrastFilter().apply { setContrast(1.1f) },
            GPUImageHighlightShadowFilter().apply {
                setShadows(0.0f)
                setHighlights(1.0f)
            }
        ))
    }

    /**
     * 黑猫滤镜 - 暗部增强
     */
    private fun createBlackcatFilter(): GPUImageFilter {
        return GPUImageFilterGroup(listOf(
            GPUImageContrastFilter().apply { setContrast(1.3f) },
            GPUImageHighlightShadowFilter().apply {
                setShadows(1.0f)
                setHighlights(0.0f)
            }
        ))
    }

    /**
     * 美白滤镜 - 肤色美白
     */
    private fun createSkinWhitenFilter(): GPUImageFilter {
        return GPUImageFilterGroup(listOf(
            GPUImageBrightnessFilter().apply { setBrightness(0.1f) },
            GPUImageSaturationFilter().apply { setSaturation(0.9f) },
            GPUImageWhiteBalanceFilter().apply { setTemperature(5500f) }
        ))
    }

    /**
     * 健康滤镜 - 健康肤色
     */
    private fun createHealthyFilter(): GPUImageFilter {
        return GPUImageFilterGroup(listOf(
            GPUImageRGBFilter().apply {
                setRed(1.1f)
                setGreen(1.05f)
                setBlue(1.0f)
            },
            GPUImageSaturationFilter().apply { setSaturation(1.1f) }
        ))
    }

    /**
     * 甜蜜滤镜 - 粉嫩色调
     */
    private fun createSweetsFilter(): GPUImageFilter {
        return GPUImageFilterGroup(listOf(
            GPUImageRGBFilter().apply {
                setRed(1.15f)
                setGreen(1.0f)
                setBlue(1.1f)
            },
            GPUImageBrightnessFilter().apply { setBrightness(0.08f) },
            GPUImageSaturationFilter().apply { setSaturation(1.15f) }
        ))
    }

    /**
     * 浪漫滤镜 - 浪漫紫粉
     */
    private fun createRomanceFilter(): GPUImageFilter {
        return GPUImageFilterGroup(listOf(
            GPUImageRGBFilter().apply {
                setRed(1.1f)
                setGreen(0.95f)
                setBlue(1.15f)
            },
            GPUImageSaturationFilter().apply { setSaturation(1.1f) }
        ))
    }

    /**
     * 樱花滤镜 - 樱花粉色
     */
    private fun createSakuraFilter(): GPUImageFilter {
        return GPUImageFilterGroup(listOf(
            GPUImageRGBFilter().apply {
                setRed(1.2f)
                setGreen(1.0f)
                setBlue(1.1f)
            },
            GPUImageBrightnessFilter().apply { setBrightness(0.05f) }
        ))
    }

    /**
     * 温暖滤镜 - 暖色调
     */
    private fun createWarmFilter(): GPUImageFilter {
        return GPUImageWhiteBalanceFilter().apply { setTemperature(6500f) }
    }

    /**
     * 复古滤镜 - 复古褐色
     */
    private fun createAntiqueFilter(): GPUImageFilter {
        return GPUImageSepiaToneFilter().apply { setIntensity(0.6f) }
    }

    /**
     * 怀旧滤镜 - 怀旧胶片
     *
     * 使用棕褐色调+轻微颗粒感+褪色效果模拟老照片
     * 不使用暗角，与VIGNETTE滤镜区分开
     */
    private fun createNostalgiaFilter(): GPUImageFilter {
        return GPUImageFilterGroup(listOf(
            GPUImageSepiaToneFilter().apply { setIntensity(0.5f) },        // 增强棕褐色调
            GPUImageContrastFilter().apply { setContrast(1.15f) },         // 稍微提高对比度
            GPUImageSaturationFilter().apply { setSaturation(0.85f) },     // 轻微降低饱和度，营造褪色感
            GPUImageBrightnessFilter().apply { setBrightness(0.03f) }      // 轻微提亮，模拟老照片曝光
        ))
    }

    /**
     * 平静滤镜 - 淡雅色调
     */
    private fun createCalmFilter(): GPUImageFilter {
        return GPUImageFilterGroup(listOf(
            GPUImageSaturationFilter().apply { setSaturation(0.8f) },
            GPUImageBrightnessFilter().apply { setBrightness(0.05f) }
        ))
    }

    /**
     * 拿铁滤镜 - 咖啡色调
     */
    private fun createLatteFilter(): GPUImageFilter {
        return GPUImageFilterGroup(listOf(
            GPUImageSepiaToneFilter().apply { setIntensity(0.3f) },
            GPUImageContrastFilter().apply { setContrast(1.05f) }
        ))
    }

    /**
     * 柔和滤镜 - 柔和色调
     */
    private fun createTenderFilter(): GPUImageFilter {
        return GPUImageFilterGroup(listOf(
            GPUImageSaturationFilter().apply { setSaturation(0.85f) },
            GPUImageGaussianBlurFilter().apply { setBlurSize(0.3f) }
        ))
    }

    /**
     * 清凉滤镜 - 冷色调
     */
    private fun createCoolFilter(): GPUImageFilter {
        return GPUImageWhiteBalanceFilter().apply { setTemperature(4500f) }
    }

    /**
     * 翡翠滤镜 - 翠绿色调
     */
    private fun createEmeraldFilter(): GPUImageFilter {
        return GPUImageRGBFilter().apply {
            setRed(0.9f)
            setGreen(1.15f)
            setBlue(1.0f)
        }
    }

    /**
     * 常青滤镜 - 常青绿色
     */
    private fun createEvergreenFilter(): GPUImageFilter {
        return GPUImageFilterGroup(listOf(
            GPUImageRGBFilter().apply {
                setRed(0.95f)
                setGreen(1.1f)
                setBlue(0.95f)
            },
            GPUImageSaturationFilter().apply { setSaturation(1.1f) }
        ))
    }

    // ==================== 特效滤镜创建方法 ====================

    /**
     * 蜡笔滤镜 - 边缘检测+颜色量化
     */
    private fun createCrayonFilter(): GPUImageFilter {
        return GPUImageFilterGroup(listOf(
            GPUImagePosterizeFilter().apply { setColorLevels(8) },
            GPUImageSobelEdgeDetectionFilter()
        ))
    }

    /**
     * 马赛克滤镜 - 使用像素化模拟
     */
    private fun createMosaicFilter(): GPUImageFilter {
        return GPUImagePixelationFilter().apply { setPixel(15f) }
    }

    /**
     * 动态模糊滤镜 - 使用高斯模糊模拟
     */
    private fun createMotionBlurFilter(): GPUImageFilter {
        return GPUImageGaussianBlurFilter().apply { setBlurSize(2.0f) }
    }

    // ==================== Instagram风格滤镜创建方法 ====================

    /**
     * Amaro滤镜 - 增加曝光，柔化高光
     */
    private fun createAmaroFilter(): GPUImageFilter {
        return GPUImageFilterGroup(listOf(
            GPUImageExposureFilter().apply { setExposure(0.2f) },
            GPUImageSaturationFilter().apply { setSaturation(1.3f) },
            GPUImageVignetteFilter().apply {
                setVignetteStart(0.5f)
                setVignetteEnd(0.9f)
            }
        ))
    }

    /**
     * Brannan滤镜 - 灰色调，金属感
     */
    private fun createBrannanFilter(): GPUImageFilter {
        return GPUImageFilterGroup(listOf(
            GPUImageSaturationFilter().apply { setSaturation(0.7f) },
            GPUImageContrastFilter().apply { setContrast(1.3f) },
            GPUImageSepiaToneFilter().apply { setIntensity(0.15f) }
        ))
    }

    /**
     * Brooklyn滤镜 - 淡褪色，黄绿色调
     */
    private fun createBrooklynFilter(): GPUImageFilter {
        return GPUImageFilterGroup(listOf(
            GPUImageRGBFilter().apply {
                setRed(1.0f)
                setGreen(1.05f)
                setBlue(0.9f)
            },
            GPUImageSaturationFilter().apply { setSaturation(0.85f) }
        ))
    }

    /**
     * Earlybird滤镜 - 复古黄棕，暗角
     */
    private fun createEarlybirdFilter(): GPUImageFilter {
        return GPUImageFilterGroup(listOf(
            GPUImageSepiaToneFilter().apply { setIntensity(0.5f) },
            GPUImageContrastFilter().apply { setContrast(1.1f) },
            GPUImageVignetteFilter().apply {
                setVignetteStart(0.2f)
                setVignetteEnd(0.85f)
            }
        ))
    }

    /**
     * Freud滤镜 - 冷色调，蓝紫
     */
    private fun createFreudFilter(): GPUImageFilter {
        return GPUImageFilterGroup(listOf(
            GPUImageRGBFilter().apply {
                setRed(0.9f)
                setGreen(0.9f)
                setBlue(1.2f)
            },
            GPUImageSaturationFilter().apply { setSaturation(0.9f) }
        ))
    }

    /**
     * Hefe滤镜 - 高对比，暗角
     */
    private fun createHefeFilter(): GPUImageFilter {
        return GPUImageFilterGroup(listOf(
            GPUImageContrastFilter().apply { setContrast(1.3f) },
            GPUImageSaturationFilter().apply { setSaturation(1.2f) },
            GPUImageVignetteFilter().apply {
                setVignetteStart(0.3f)
                setVignetteEnd(0.8f)
            }
        ))
    }

    /**
     * Hudson滤镜 - 冷色，阴影蓝
     */
    private fun createHudsonFilter(): GPUImageFilter {
        return GPUImageFilterGroup(listOf(
            GPUImageWhiteBalanceFilter().apply { setTemperature(4800f) },
            GPUImageContrastFilter().apply { setContrast(1.1f) },
            GPUImageVignetteFilter().apply {
                setVignetteStart(0.4f)
                setVignetteEnd(0.9f)
            }
        ))
    }

    /**
     * Kevin滤镜 - 黄绿色调
     */
    private fun createKevinFilter(): GPUImageFilter {
        return GPUImageRGBFilter().apply {
            setRed(1.1f)
            setGreen(1.15f)
            setBlue(0.85f)
        }
    }

    /**
     * Lomo滤镜 - LOMO胶片风格
     */
    private fun createLomoFilter(): GPUImageFilter {
        return GPUImageFilterGroup(listOf(
            GPUImageContrastFilter().apply { setContrast(1.4f) },
            GPUImageSaturationFilter().apply { setSaturation(1.3f) },
            GPUImageVignetteFilter().apply {
                setVignetteStart(0.2f)
                setVignetteEnd(0.85f)
            }
        ))
    }

    /**
     * 1977滤镜 - 1977年复古
     */
    private fun create1977Filter(): GPUImageFilter {
        return GPUImageFilterGroup(listOf(
            GPUImageRGBFilter().apply {
                setRed(1.1f)
                setGreen(0.95f)
                setBlue(1.0f)
            },
            GPUImageSaturationFilter().apply { setSaturation(0.9f) },
            GPUImageContrastFilter().apply { setContrast(1.1f) }
        ))
    }

    /**
     * Nashville滤镜 - 温暖粉色
     */
    private fun createNashvilleFilter(): GPUImageFilter {
        return GPUImageFilterGroup(listOf(
            GPUImageRGBFilter().apply {
                setRed(1.15f)
                setGreen(1.0f)
                setBlue(0.95f)
            },
            GPUImageBrightnessFilter().apply { setBrightness(0.05f) },
            GPUImageSaturationFilter().apply { setSaturation(1.1f) }
        ))
    }

    /**
     * Pixar滤镜 - 动画风格
     */
    private fun createPixarFilter(): GPUImageFilter {
        return GPUImageFilterGroup(listOf(
            GPUImageSaturationFilter().apply { setSaturation(1.4f) },
            GPUImageContrastFilter().apply { setContrast(1.2f) },
            GPUImageBrightnessFilter().apply { setBrightness(0.05f) }
        ))
    }

    /**
     * Rise滤镜 - 柔和暖色
     */
    private fun createRiseFilter(): GPUImageFilter {
        return GPUImageFilterGroup(listOf(
            GPUImageWhiteBalanceFilter().apply { setTemperature(5800f) },
            GPUImageBrightnessFilter().apply { setBrightness(0.08f) },
            GPUImageSaturationFilter().apply { setSaturation(1.05f) }
        ))
    }

    /**
     * Sierra滤镜 - 柔和对比
     */
    private fun createSierraFilter(): GPUImageFilter {
        return GPUImageFilterGroup(listOf(
            GPUImageSaturationFilter().apply { setSaturation(0.85f) },
            GPUImageContrastFilter().apply { setContrast(1.15f) },
            GPUImageVignetteFilter().apply {
                setVignetteStart(0.4f)
                setVignetteEnd(0.9f)
            }
        ))
    }

    /**
     * Sutro滤镜 - 紫褐色调
     */
    private fun createSutroFilter(): GPUImageFilter {
        return GPUImageFilterGroup(listOf(
            GPUImageRGBFilter().apply {
                setRed(1.0f)
                setGreen(0.9f)
                setBlue(1.1f)
            },
            GPUImageSepiaToneFilter().apply { setIntensity(0.2f) },
            GPUImageVignetteFilter().apply {
                setVignetteStart(0.3f)
                setVignetteEnd(0.85f)
            }
        ))
    }

    /**
     * Toaster滤镜 - 老照片，暗角
     */
    private fun createToasterFilter(): GPUImageFilter {
        return GPUImageFilterGroup(listOf(
            GPUImageSepiaToneFilter().apply { setIntensity(0.4f) },
            GPUImageContrastFilter().apply { setContrast(1.2f) },
            GPUImageVignetteFilter().apply {
                setVignetteStart(0.15f)
                setVignetteEnd(0.8f)
            }
        ))
    }

    /**
     * Valencia滤镜 - 褪色暖色
     */
    private fun createValenciaFilter(): GPUImageFilter {
        return GPUImageFilterGroup(listOf(
            GPUImageWhiteBalanceFilter().apply { setTemperature(5600f) },
            GPUImageSaturationFilter().apply { setSaturation(0.9f) },
            GPUImageContrastFilter().apply { setContrast(1.05f) }
        ))
    }

    /**
     * Walden滤镜 - 黄色调增强
     */
    private fun createWaldenFilter(): GPUImageFilter {
        return GPUImageFilterGroup(listOf(
            GPUImageRGBFilter().apply {
                setRed(1.1f)
                setGreen(1.1f)
                setBlue(0.85f)
            },
            GPUImageBrightnessFilter().apply { setBrightness(0.05f) }
        ))
    }

    /**
     * X-Pro II滤镜 - 高对比，暗角
     */
    private fun createXProIIFilter(): GPUImageFilter {
        return GPUImageFilterGroup(listOf(
            GPUImageContrastFilter().apply { setContrast(1.4f) },
            GPUImageSaturationFilter().apply { setSaturation(1.3f) },
            GPUImageVignetteFilter().apply {
                setVignetteStart(0.2f)
                setVignetteEnd(0.75f)
            }
        ))
    }

    // ==================== 注册表辅助方法 ====================

    /**
     * 获取所有已注册滤镜的 ID
     *
     * @return 滤镜 ID 列表
     */
    fun getRegisteredFilterIds(): List<String> {
        ensureInitialized()
        return FilterRegistry.getAllFilterIds()
    }

    /**
     * 获取已注册滤镜数量
     *
     * @return 滤镜数量
     */
    fun getRegisteredFilterCount(): Int {
        ensureInitialized()
        return FilterRegistry.getFilterCount()
    }

    /**
     * 获取滤镜信息
     *
     * @param filterTypeName 滤镜类型名称
     * @return 滤镜信息，不存在返回 null
     */
    fun getFilterInfo(filterTypeName: String) = FilterRegistry.getFilterInfo(filterTypeName)

    /**
     * 获取注册表统计信息
     *
     * @return 统计信息字符串
     */
    fun getRegistryStatistics(): String {
        ensureInitialized()
        return FilterRegistry.getStatistics()
    }
}
