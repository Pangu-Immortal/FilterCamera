/**
 * FilterRegistrations.kt - 滤镜注册定义
 *
 * 集中定义所有滤镜的注册信息
 * 将原 GPUImageFilterFactory 中的大型 switch 语句转换为声明式注册
 *
 * 新增滤镜步骤：
 * 1. 在对应分组的 register*Filters() 函数中添加注册代码
 * 2. 无需修改其他文件
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filter.registry

import android.util.Log
import jp.co.cyberagent.android.gpuimage.filter.*

/**
 * 滤镜注册器
 *
 * 负责初始化并注册所有内置滤镜
 */
object FilterRegistrations {

    private const val TAG = "FilterRegistrations"

    /**
     * 初始化所有滤镜注册
     *
     * 应用启动时调用一次
     */
    fun initializeAll() {
        if (FilterRegistry.isInitialized()) {
            Log.w(TAG, "initializeAll: 已经初始化，跳过")
            return
        }

        Log.i(TAG, "initializeAll: 开始注册滤镜")
        val startTime = System.currentTimeMillis()

        // 按分组注册
        registerOriginalFilters()                                   // 原图
        registerStyleFilters()                                      // 风格滤镜
        registerEffectFilters()                                     // 特效滤镜
        registerInstagramFilters()                                  // Instagram 滤镜
        registerWatermarkFilters()                                  // 水印滤镜
        registerAdjustFilters()                                     // 调整滤镜

        FilterRegistry.markInitialized()

        val elapsed = System.currentTimeMillis() - startTime
        Log.i(TAG, "initializeAll: 注册完成 耗时=${elapsed}ms\n${FilterRegistry.getStatistics()}")
    }

    // ==================== 原图 ====================

    private fun registerOriginalFilters() {
        FilterRegistry.register(
            FilterInfo(
                id = "NONE",
                displayName = "原图",
                category = FilterCategory.ORIGINAL,
                useGpu = false,
                factory = { GPUImageFilter() }
            )
        )
    }

    // ==================== 风格滤镜 ====================

    private fun registerStyleFilters() {
        val styleFilters = listOf(
            // 复古风格
            FilterInfo(
                id = "ANTIQUE",
                displayName = "复古",
                category = FilterCategory.STYLE,
                factory = { createAntiqueFilter() }
            ),
            FilterInfo(
                id = "NOSTALGIA",
                displayName = "怀旧",
                category = FilterCategory.STYLE,
                factory = { createNostalgiaFilter() }
            ),
            FilterInfo(
                id = "LOMO",
                displayName = "LOMO",
                category = FilterCategory.STYLE,
                factory = { createLomoFilter() }
            ),

            // 色调风格
            FilterInfo(
                id = "WARM",
                displayName = "暖色",
                category = FilterCategory.STYLE,
                factory = { createWarmFilter() }
            ),
            FilterInfo(
                id = "COOL",
                displayName = "冷色",
                category = FilterCategory.STYLE,
                factory = { createCoolFilter() }
            ),
            FilterInfo(
                id = "CALM",
                displayName = "淡雅",
                category = FilterCategory.STYLE,
                factory = { createCalmFilter() }
            ),
            FilterInfo(
                id = "TENDER",
                displayName = "温柔",
                category = FilterCategory.STYLE,
                factory = { createTenderFilter() }
            ),

            // 主题风格
            FilterInfo(
                id = "SUNRISE",
                displayName = "日出",
                category = FilterCategory.STYLE,
                factory = { createSunriseFilter() }
            ),
            FilterInfo(
                id = "SUNSET",
                displayName = "日落",
                category = FilterCategory.STYLE,
                factory = { createSunsetFilter() }
            ),
            FilterInfo(
                id = "SAKURA",
                displayName = "樱花",
                category = FilterCategory.STYLE,
                factory = { createSakuraFilter() }
            ),
            FilterInfo(
                id = "ROMANCE",
                displayName = "浪漫",
                category = FilterCategory.STYLE,
                factory = { createRomanceFilter() }
            ),
            FilterInfo(
                id = "FAIRYTALE",
                displayName = "童话",
                category = FilterCategory.STYLE,
                factory = { createFairytaleFilter() }
            ),

            // 特殊风格
            FilterInfo(
                id = "HEALTHY",
                displayName = "健康",
                category = FilterCategory.STYLE,
                factory = { createHealthyFilter() }
            ),
            FilterInfo(
                id = "EMERALD",
                displayName = "翡翠",
                category = FilterCategory.STYLE,
                factory = { createEmeraldFilter() }
            ),
            FilterInfo(
                id = "EVERGREEN",
                displayName = "常青",
                category = FilterCategory.STYLE,
                factory = { createEvergreenFilter() }
            ),
            FilterInfo(
                id = "LATTE",
                displayName = "拿铁",
                category = FilterCategory.STYLE,
                factory = { createLatteFilter() }
            ),
            FilterInfo(
                id = "SWEETS",
                displayName = "甜美",
                category = FilterCategory.STYLE,
                factory = { createSweetsFilter() }
            ),
            FilterInfo(
                id = "FREUD",
                displayName = "弗洛伊德",
                category = FilterCategory.STYLE,
                factory = { createFreudFilter() }
            ),
            FilterInfo(
                id = "PIXAR",
                displayName = "皮克斯",
                category = FilterCategory.STYLE,
                factory = { createPixarFilter() }
            )
        )

        FilterRegistry.registerAll(styleFilters)
    }

    // ==================== 特效滤镜 ====================

    private fun registerEffectFilters() {
        val effectFilters = listOf(
            // 黑白系
            FilterInfo(
                id = "BLACKCAT",
                displayName = "黑猫",
                category = FilterCategory.EFFECT,
                factory = { createBlackCatFilter() }
            ),
            FilterInfo(
                id = "WHITECAT",
                displayName = "白猫",
                category = FilterCategory.EFFECT,
                factory = { createWhiteCatFilter() }
            ),
            FilterInfo(
                id = "GRAYSCALE",
                displayName = "黑白",
                category = FilterCategory.EFFECT,
                factory = { GPUImageGrayscaleFilter() }
            ),
            FilterInfo(
                id = "MONOCHROME",
                displayName = "单色",
                category = FilterCategory.EFFECT,
                factory = { GPUImageMonochromeFilter() }
            ),

            // 艺术效果
            FilterInfo(
                id = "SKETCH",
                displayName = "素描",
                category = FilterCategory.EFFECT,
                factory = { GPUImageSketchFilter() }
            ),
            FilterInfo(
                id = "CRAYON",
                displayName = "蜡笔",
                category = FilterCategory.EFFECT,
                factory = { createCrayonFilter() }
            ),
            FilterInfo(
                id = "TOON",
                displayName = "卡通",
                category = FilterCategory.EFFECT,
                factory = { GPUImageToonFilter() }
            ),
            FilterInfo(
                id = "POSTERIZE",
                displayName = "海报",
                category = FilterCategory.EFFECT,
                factory = { GPUImagePosterizeFilter() }
            ),

            // 模糊效果
            FilterInfo(
                id = "GAUSSIAN_BLUR",
                displayName = "高斯模糊",
                category = FilterCategory.EFFECT,
                factory = { GPUImageGaussianBlurFilter().apply { setBlurSize(2.0f) } }
            ),
            FilterInfo(
                id = "BOX_BLUR",
                displayName = "方框模糊",
                category = FilterCategory.EFFECT,
                factory = { GPUImageBoxBlurFilter().apply { setBlurSize(2.0f) } }
            ),

            // 锐化效果
            FilterInfo(
                id = "SHARPEN",
                displayName = "锐化",
                category = FilterCategory.EFFECT,
                factory = { GPUImageSharpenFilter().apply { setSharpness(1.5f) } }
            ),

            // 色彩效果
            FilterInfo(
                id = "SEPIA",
                displayName = "棕褐",
                category = FilterCategory.EFFECT,
                factory = { GPUImageSepiaToneFilter() }
            ),
            FilterInfo(
                id = "INVERT",
                displayName = "反色",
                category = FilterCategory.EFFECT,
                factory = { GPUImageColorInvertFilter() }
            ),
            FilterInfo(
                id = "SOLARIZE",
                displayName = "曝光过度",
                category = FilterCategory.EFFECT,
                factory = { GPUImageSolarizeFilter() }
            ),

            // 边缘效果
            FilterInfo(
                id = "SOBEL_EDGE",
                displayName = "边缘检测",
                category = FilterCategory.EFFECT,
                factory = { GPUImageSobelEdgeDetectionFilter() }
            ),
            FilterInfo(
                id = "EMBOSS",
                displayName = "浮雕",
                category = FilterCategory.EFFECT,
                factory = { GPUImageEmbossFilter() }
            ),

            // 像素效果
            FilterInfo(
                id = "PIXELATION",
                displayName = "像素化",
                category = FilterCategory.EFFECT,
                factory = { GPUImagePixelationFilter().apply { setPixel(10f) } }
            ),
            FilterInfo(
                id = "HALFTONE",
                displayName = "半调",
                category = FilterCategory.EFFECT,
                factory = { GPUImageHalftoneFilter() }
            ),
            FilterInfo(
                id = "CROSSHATCH",
                displayName = "交叉线",
                category = FilterCategory.EFFECT,
                factory = { GPUImageCrosshatchFilter() }
            ),

            // 晕影效果
            FilterInfo(
                id = "VIGNETTE",
                displayName = "晕影",
                category = FilterCategory.EFFECT,
                factory = {
                    GPUImageVignetteFilter().apply {
                        setVignetteStart(0.3f)
                        setVignetteEnd(0.75f)
                    }
                }
            ),

            // 玻璃效果
            FilterInfo(
                id = "GLASS_SPHERE",
                displayName = "玻璃球",
                category = FilterCategory.EFFECT,
                factory = { GPUImageGlassSphereFilter() }
            )
        )

        FilterRegistry.registerAll(effectFilters)
    }

    // ==================== Instagram 滤镜 ====================

    private fun registerInstagramFilters() {
        val insFilters = listOf(
            FilterInfo(
                id = "INS_AMARO",
                displayName = "Amaro",
                category = FilterCategory.INSTAGRAM,
                factory = { createAmaroFilter() }
            ),
            FilterInfo(
                id = "INS_RISE",
                displayName = "Rise",
                category = FilterCategory.INSTAGRAM,
                factory = { createRiseFilter() }
            ),
            FilterInfo(
                id = "INS_HUDSON",
                displayName = "Hudson",
                category = FilterCategory.INSTAGRAM,
                factory = { createHudsonFilter() }
            ),
            FilterInfo(
                id = "INS_XPROII",
                displayName = "X-Pro II",
                category = FilterCategory.INSTAGRAM,
                factory = { createXproIIFilter() }
            ),
            FilterInfo(
                id = "INS_SIERRA",
                displayName = "Sierra",
                category = FilterCategory.INSTAGRAM,
                factory = { createSierraFilter() }
            ),
            FilterInfo(
                id = "INS_LOFI",
                displayName = "Lo-Fi",
                category = FilterCategory.INSTAGRAM,
                factory = { createLoFiFilter() }
            ),
            FilterInfo(
                id = "INS_EARLYBIRD",
                displayName = "Earlybird",
                category = FilterCategory.INSTAGRAM,
                factory = { createEarlybirdFilter() }
            ),
            FilterInfo(
                id = "INS_SUTRO",
                displayName = "Sutro",
                category = FilterCategory.INSTAGRAM,
                factory = { createSutroFilter() }
            ),
            FilterInfo(
                id = "INS_TOASTER",
                displayName = "Toaster",
                category = FilterCategory.INSTAGRAM,
                factory = { createToasterFilter() }
            ),
            FilterInfo(
                id = "INS_BRANNAN",
                displayName = "Brannan",
                category = FilterCategory.INSTAGRAM,
                factory = { createBrannanFilter() }
            ),
            FilterInfo(
                id = "INS_INKWELL",
                displayName = "Inkwell",
                category = FilterCategory.INSTAGRAM,
                factory = { createInkwellFilter() }
            ),
            FilterInfo(
                id = "INS_WALDEN",
                displayName = "Walden",
                category = FilterCategory.INSTAGRAM,
                factory = { createWaldenFilter() }
            ),
            FilterInfo(
                id = "INS_HEFE",
                displayName = "Hefe",
                category = FilterCategory.INSTAGRAM,
                factory = { createHefeFilter() }
            ),
            FilterInfo(
                id = "INS_VALENCIA",
                displayName = "Valencia",
                category = FilterCategory.INSTAGRAM,
                factory = { createValenciaFilter() }
            ),
            FilterInfo(
                id = "INS_NASHVILLE",
                displayName = "Nashville",
                category = FilterCategory.INSTAGRAM,
                factory = { createNashvilleFilter() }
            ),
            FilterInfo(
                id = "INS_1977",
                displayName = "1977",
                category = FilterCategory.INSTAGRAM,
                factory = { create1977Filter() }
            ),
            FilterInfo(
                id = "INS_KEVIN",
                displayName = "Kevin",
                category = FilterCategory.INSTAGRAM,
                factory = { createKevinFilter() }
            ),
            FilterInfo(
                id = "INS_BROOKLYN",
                displayName = "Brooklyn",
                category = FilterCategory.INSTAGRAM,
                factory = { createBrooklynFilter() }
            ),
            FilterInfo(
                id = "INS_SKINWHITEN",
                displayName = "美白",
                category = FilterCategory.INSTAGRAM,
                factory = { createSkinWhitenFilter() }
            ),
            FilterInfo(
                id = "INS_BEAUTY",
                displayName = "美颜",
                category = FilterCategory.INSTAGRAM,
                factory = { createBeautyFilter() }
            )
        )

        FilterRegistry.registerAll(insFilters)
    }

    // ==================== 水印滤镜 ====================

    private fun registerWatermarkFilters() {
        // 水印滤镜不使用 GPU 处理，通过 Canvas 绘制
        val watermarkFilters = listOf(
            FilterInfo(
                id = "WATERMARK_TIME",
                displayName = "时间水印",
                category = FilterCategory.WATERMARK,
                useGpu = false,
                factory = { GPUImageFilter() }                      // 占位，实际在 Canvas 层处理
            ),
            FilterInfo(
                id = "WATERMARK_LOCATION",
                displayName = "位置水印",
                category = FilterCategory.WATERMARK,
                useGpu = false,
                factory = { GPUImageFilter() }
            ),
            FilterInfo(
                id = "WATERMARK_CUSTOM",
                displayName = "自定义水印",
                category = FilterCategory.WATERMARK,
                useGpu = false,
                factory = { GPUImageFilter() }
            ),
            FilterInfo(
                id = "WATERMARK_LOGO",
                displayName = "Logo水印",
                category = FilterCategory.WATERMARK,
                useGpu = false,
                factory = { GPUImageFilter() }
            )
        )

        FilterRegistry.registerAll(watermarkFilters)
    }

    // ==================== 调整滤镜 ====================

    private fun registerAdjustFilters() {
        val adjustFilters = listOf(
            FilterInfo(
                id = "ADJUST_BRIGHTNESS",
                displayName = "亮度",
                category = FilterCategory.ADJUST,
                factory = { GPUImageBrightnessFilter() }
            ),
            FilterInfo(
                id = "ADJUST_CONTRAST",
                displayName = "对比度",
                category = FilterCategory.ADJUST,
                factory = { GPUImageContrastFilter() }
            ),
            FilterInfo(
                id = "ADJUST_SATURATION",
                displayName = "饱和度",
                category = FilterCategory.ADJUST,
                factory = { GPUImageSaturationFilter() }
            ),
            FilterInfo(
                id = "ADJUST_EXPOSURE",
                displayName = "曝光",
                category = FilterCategory.ADJUST,
                factory = { GPUImageExposureFilter() }
            ),
            FilterInfo(
                id = "ADJUST_HIGHLIGHTS",
                displayName = "高光",
                category = FilterCategory.ADJUST,
                factory = { GPUImageHighlightShadowFilter() }
            ),
            FilterInfo(
                id = "ADJUST_SHADOWS",
                displayName = "阴影",
                category = FilterCategory.ADJUST,
                factory = { GPUImageHighlightShadowFilter() }
            ),
            FilterInfo(
                id = "ADJUST_HUE",
                displayName = "色相",
                category = FilterCategory.ADJUST,
                factory = { GPUImageHueFilter() }
            )
        )

        FilterRegistry.registerAll(adjustFilters)
    }

    // ==================== 滤镜工厂方法（复杂滤镜） ====================

    // 风格滤镜工厂
    private fun createAntiqueFilter(): GPUImageFilter =
        GPUImageColorMatrixFilter(1.0f, floatArrayOf(
            0.6f, 0.2f, 0.2f, 0f,
            0.2f, 0.6f, 0.2f, 0f,
            0.2f, 0.2f, 0.5f, 0f,
            0f, 0f, 0f, 1f
        ))

    private fun createNostalgiaFilter(): GPUImageFilter =
        GPUImageColorMatrixFilter(1.0f, floatArrayOf(
            0.5f, 0.5f, 0.3f, 0f,
            0.3f, 0.5f, 0.2f, 0f,
            0.2f, 0.3f, 0.4f, 0f,
            0f, 0f, 0f, 1f
        ))

    private fun createLomoFilter(): GPUImageFilter =
        GPUImageFilterGroup().apply {
            addFilter(GPUImageContrastFilter(1.2f))
            addFilter(GPUImageSaturationFilter(1.2f))
            addFilter(GPUImageVignetteFilter().apply {
                setVignetteStart(0.2f)
                setVignetteEnd(0.85f)
            })
        }

    private fun createWarmFilter(): GPUImageFilter =
        GPUImageColorMatrixFilter(1.0f, floatArrayOf(
            1.2f, 0f, 0f, 0f,
            0f, 1.0f, 0f, 0f,
            0f, 0f, 0.8f, 0f,
            0f, 0f, 0f, 1f
        ))

    private fun createCoolFilter(): GPUImageFilter =
        GPUImageColorMatrixFilter(1.0f, floatArrayOf(
            0.8f, 0f, 0f, 0f,
            0f, 1.0f, 0f, 0f,
            0f, 0f, 1.2f, 0f,
            0f, 0f, 0f, 1f
        ))

    private fun createCalmFilter(): GPUImageFilter =
        GPUImageFilterGroup().apply {
            addFilter(GPUImageSaturationFilter(0.7f))
            addFilter(GPUImageBrightnessFilter(0.05f))
        }

    private fun createTenderFilter(): GPUImageFilter =
        GPUImageFilterGroup().apply {
            addFilter(GPUImageSaturationFilter(0.8f))
            addFilter(GPUImageBrightnessFilter(0.1f))
            addFilter(GPUImageContrastFilter(0.9f))
        }

    private fun createSunriseFilter(): GPUImageFilter =
        GPUImageFilterGroup().apply {
            addFilter(GPUImageColorMatrixFilter(1.0f, floatArrayOf(
                1.1f, 0.1f, 0f, 0f,
                0f, 1.0f, 0f, 0f,
                0f, 0f, 0.9f, 0f,
                0f, 0f, 0f, 1f
            )))
            addFilter(GPUImageBrightnessFilter(0.1f))
        }

    private fun createSunsetFilter(): GPUImageFilter =
        GPUImageFilterGroup().apply {
            addFilter(GPUImageColorMatrixFilter(1.0f, floatArrayOf(
                1.2f, 0.1f, 0f, 0f,
                0.1f, 1.0f, 0f, 0f,
                0f, 0f, 0.8f, 0f,
                0f, 0f, 0f, 1f
            )))
            addFilter(GPUImageSaturationFilter(1.1f))
        }

    private fun createSakuraFilter(): GPUImageFilter =
        GPUImageFilterGroup().apply {
            addFilter(GPUImageColorMatrixFilter(1.0f, floatArrayOf(
                1.1f, 0.1f, 0.1f, 0f,
                0f, 1.0f, 0.1f, 0f,
                0.1f, 0f, 1.0f, 0f,
                0f, 0f, 0f, 1f
            )))
            addFilter(GPUImageSaturationFilter(1.1f))
        }

    private fun createRomanceFilter(): GPUImageFilter =
        GPUImageFilterGroup().apply {
            addFilter(GPUImageSaturationFilter(1.2f))
            addFilter(GPUImageBrightnessFilter(0.1f))
            addFilter(GPUImageColorMatrixFilter(1.0f, floatArrayOf(
                1.1f, 0.05f, 0.05f, 0f,
                0.05f, 1.0f, 0.05f, 0f,
                0.05f, 0.05f, 1.0f, 0f,
                0f, 0f, 0f, 1f
            )))
        }

    private fun createFairytaleFilter(): GPUImageFilter =
        GPUImageFilterGroup().apply {
            addFilter(GPUImageSaturationFilter(1.3f))
            addFilter(GPUImageBrightnessFilter(0.15f))
            addFilter(GPUImageContrastFilter(1.1f))
        }

    private fun createHealthyFilter(): GPUImageFilter =
        GPUImageFilterGroup().apply {
            addFilter(GPUImageSaturationFilter(1.1f))
            addFilter(GPUImageBrightnessFilter(0.05f))
            addFilter(GPUImageContrastFilter(1.05f))
        }

    private fun createEmeraldFilter(): GPUImageFilter =
        GPUImageColorMatrixFilter(1.0f, floatArrayOf(
            0.9f, 0f, 0f, 0f,
            0f, 1.1f, 0f, 0f,
            0f, 0f, 0.9f, 0f,
            0f, 0f, 0f, 1f
        ))

    private fun createEvergreenFilter(): GPUImageFilter =
        GPUImageFilterGroup().apply {
            addFilter(GPUImageColorMatrixFilter(1.0f, floatArrayOf(
                0.95f, 0f, 0f, 0f,
                0f, 1.05f, 0f, 0f,
                0f, 0f, 0.95f, 0f,
                0f, 0f, 0f, 1f
            )))
            addFilter(GPUImageSaturationFilter(0.9f))
        }

    private fun createLatteFilter(): GPUImageFilter =
        GPUImageFilterGroup().apply {
            addFilter(GPUImageSaturationFilter(0.85f))
            addFilter(GPUImageColorMatrixFilter(1.0f, floatArrayOf(
                1.05f, 0.05f, 0f, 0f,
                0.05f, 1.0f, 0f, 0f,
                0f, 0f, 0.95f, 0f,
                0f, 0f, 0f, 1f
            )))
        }

    private fun createSweetsFilter(): GPUImageFilter =
        GPUImageFilterGroup().apply {
            addFilter(GPUImageSaturationFilter(1.15f))
            addFilter(GPUImageBrightnessFilter(0.1f))
        }

    private fun createFreudFilter(): GPUImageFilter =
        GPUImageFilterGroup().apply {
            addFilter(GPUImageContrastFilter(1.1f))
            addFilter(GPUImageSaturationFilter(0.9f))
            addFilter(GPUImageVignetteFilter().apply {
                setVignetteStart(0.3f)
                setVignetteEnd(0.8f)
            })
        }

    private fun createPixarFilter(): GPUImageFilter =
        GPUImageFilterGroup().apply {
            addFilter(GPUImageSaturationFilter(1.25f))
            addFilter(GPUImageContrastFilter(1.15f))
            addFilter(GPUImageBrightnessFilter(0.05f))
        }

    // 特效滤镜工厂
    private fun createBlackCatFilter(): GPUImageFilter =
        GPUImageFilterGroup().apply {
            addFilter(GPUImageContrastFilter(1.3f))
            addFilter(GPUImageBrightnessFilter(-0.1f))
        }

    private fun createWhiteCatFilter(): GPUImageFilter =
        GPUImageFilterGroup().apply {
            addFilter(GPUImageContrastFilter(1.2f))
            addFilter(GPUImageBrightnessFilter(0.1f))
        }

    private fun createCrayonFilter(): GPUImageFilter =
        GPUImageFilterGroup().apply {
            addFilter(GPUImageSobelEdgeDetectionFilter())
            addFilter(GPUImageColorInvertFilter())
        }

    // Instagram 滤镜工厂（简化版实现）
    private fun createAmaroFilter(): GPUImageFilter =
        GPUImageFilterGroup().apply {
            addFilter(GPUImageSaturationFilter(1.3f))
            addFilter(GPUImageBrightnessFilter(0.1f))
            addFilter(GPUImageVignetteFilter().apply {
                setVignetteStart(0.3f)
                setVignetteEnd(0.85f)
            })
        }

    private fun createRiseFilter(): GPUImageFilter =
        GPUImageFilterGroup().apply {
            addFilter(GPUImageBrightnessFilter(0.1f))
            addFilter(GPUImageSaturationFilter(1.1f))
            addFilter(GPUImageContrastFilter(0.9f))
        }

    private fun createHudsonFilter(): GPUImageFilter =
        GPUImageFilterGroup().apply {
            addFilter(GPUImageColorMatrixFilter(1.0f, floatArrayOf(
                0.9f, 0f, 0f, 0f,
                0f, 0.9f, 0f, 0f,
                0f, 0f, 1.2f, 0f,
                0f, 0f, 0f, 1f
            )))
            addFilter(GPUImageContrastFilter(1.1f))
        }

    private fun createXproIIFilter(): GPUImageFilter =
        GPUImageFilterGroup().apply {
            addFilter(GPUImageContrastFilter(1.3f))
            addFilter(GPUImageSaturationFilter(1.2f))
            addFilter(GPUImageVignetteFilter().apply {
                setVignetteStart(0.2f)
                setVignetteEnd(0.9f)
            })
        }

    private fun createSierraFilter(): GPUImageFilter =
        GPUImageFilterGroup().apply {
            addFilter(GPUImageSaturationFilter(0.8f))
            addFilter(GPUImageContrastFilter(1.1f))
            addFilter(GPUImageVignetteFilter().apply {
                setVignetteStart(0.35f)
                setVignetteEnd(0.8f)
            })
        }

    private fun createLoFiFilter(): GPUImageFilter =
        GPUImageFilterGroup().apply {
            addFilter(GPUImageContrastFilter(1.4f))
            addFilter(GPUImageSaturationFilter(1.3f))
        }

    private fun createEarlybirdFilter(): GPUImageFilter =
        GPUImageFilterGroup().apply {
            addFilter(GPUImageSepiaToneFilter(0.3f))
            addFilter(GPUImageContrastFilter(1.1f))
            addFilter(GPUImageVignetteFilter())
        }

    private fun createSutroFilter(): GPUImageFilter =
        GPUImageFilterGroup().apply {
            addFilter(GPUImageContrastFilter(1.2f))
            addFilter(GPUImageSaturationFilter(0.8f))
            addFilter(GPUImageBrightnessFilter(-0.1f))
            addFilter(GPUImageVignetteFilter().apply {
                setVignetteStart(0.2f)
                setVignetteEnd(0.85f)
            })
        }

    private fun createToasterFilter(): GPUImageFilter =
        GPUImageFilterGroup().apply {
            addFilter(GPUImageContrastFilter(1.2f))
            addFilter(GPUImageColorMatrixFilter(1.0f, floatArrayOf(
                1.1f, 0.1f, 0f, 0f,
                0f, 1.0f, 0f, 0f,
                0f, 0f, 0.9f, 0f,
                0f, 0f, 0f, 1f
            )))
            addFilter(GPUImageVignetteFilter())
        }

    private fun createBrannanFilter(): GPUImageFilter =
        GPUImageFilterGroup().apply {
            addFilter(GPUImageContrastFilter(1.3f))
            addFilter(GPUImageSaturationFilter(0.7f))
            addFilter(GPUImageColorMatrixFilter(1.0f, floatArrayOf(
                1.1f, 0.1f, 0.05f, 0f,
                0.1f, 1.0f, 0.05f, 0f,
                0.05f, 0.05f, 0.9f, 0f,
                0f, 0f, 0f, 1f
            )))
        }

    private fun createInkwellFilter(): GPUImageFilter =
        GPUImageGrayscaleFilter()

    private fun createWaldenFilter(): GPUImageFilter =
        GPUImageFilterGroup().apply {
            addFilter(GPUImageSaturationFilter(0.9f))
            addFilter(GPUImageBrightnessFilter(0.1f))
            addFilter(GPUImageColorMatrixFilter(1.0f, floatArrayOf(
                1.0f, 0.1f, 0f, 0f,
                0f, 1.0f, 0.05f, 0f,
                0.05f, 0f, 1.0f, 0f,
                0f, 0f, 0f, 1f
            )))
        }

    private fun createHefeFilter(): GPUImageFilter =
        GPUImageFilterGroup().apply {
            addFilter(GPUImageSaturationFilter(1.2f))
            addFilter(GPUImageContrastFilter(1.1f))
            addFilter(GPUImageVignetteFilter().apply {
                setVignetteStart(0.35f)
                setVignetteEnd(0.85f)
            })
        }

    private fun createValenciaFilter(): GPUImageFilter =
        GPUImageFilterGroup().apply {
            addFilter(GPUImageColorMatrixFilter(1.0f, floatArrayOf(
                1.1f, 0.05f, 0f, 0f,
                0f, 1.05f, 0f, 0f,
                0f, 0f, 0.9f, 0f,
                0f, 0f, 0f, 1f
            )))
            addFilter(GPUImageSaturationFilter(1.1f))
        }

    private fun createNashvilleFilter(): GPUImageFilter =
        GPUImageFilterGroup().apply {
            addFilter(GPUImageColorMatrixFilter(1.0f, floatArrayOf(
                1.2f, 0.1f, 0f, 0f,
                0f, 1.0f, 0.1f, 0f,
                0.1f, 0f, 0.8f, 0f,
                0f, 0f, 0f, 1f
            )))
            addFilter(GPUImageSaturationFilter(1.1f))
            addFilter(GPUImageContrastFilter(1.1f))
        }

    private fun create1977Filter(): GPUImageFilter =
        GPUImageFilterGroup().apply {
            addFilter(GPUImageSaturationFilter(1.2f))
            addFilter(GPUImageContrastFilter(0.9f))
            addFilter(GPUImageColorMatrixFilter(1.0f, floatArrayOf(
                1.1f, 0.1f, 0.1f, 0f,
                0f, 1.0f, 0f, 0f,
                0f, 0f, 0.9f, 0f,
                0f, 0f, 0f, 1f
            )))
        }

    private fun createKevinFilter(): GPUImageFilter =
        GPUImageFilterGroup().apply {
            addFilter(GPUImageColorMatrixFilter(1.0f, floatArrayOf(
                1.1f, 0f, 0f, 0f,
                0f, 1.1f, 0f, 0f,
                0f, 0f, 0.9f, 0f,
                0f, 0f, 0f, 1f
            )))
            addFilter(GPUImageContrastFilter(1.1f))
        }

    private fun createBrooklynFilter(): GPUImageFilter =
        GPUImageFilterGroup().apply {
            addFilter(GPUImageSaturationFilter(0.9f))
            addFilter(GPUImageContrastFilter(1.1f))
            addFilter(GPUImageColorMatrixFilter(1.0f, floatArrayOf(
                0.9f, 0f, 0f, 0f,
                0f, 1.0f, 0f, 0f,
                0f, 0f, 1.1f, 0f,
                0f, 0f, 0f, 1f
            )))
        }

    private fun createSkinWhitenFilter(): GPUImageFilter =
        GPUImageFilterGroup().apply {
            addFilter(GPUImageBrightnessFilter(0.15f))
            addFilter(GPUImageSaturationFilter(0.9f))
        }

    private fun createBeautyFilter(): GPUImageFilter =
        GPUImageFilterGroup().apply {
            addFilter(GPUImageBilateralBlurFilter().apply { setDistanceNormalizationFactor(4f) })
            addFilter(GPUImageBrightnessFilter(0.05f))
            addFilter(GPUImageSaturationFilter(1.05f))
        }
}
