/**
 * FilterType.kt - 滤镜类型枚举
 *
 * 定义所有支持的滤镜类型，包含72种滤镜效果
 * 支持分组显示：风格、特效、Instagram、水印、调整
 *
 * 技术实现：
 * - 所有滤镜基于GPU Fragment Shader实现
 * - 参考GPUImage开源库的GLSL实现
 * - 支持实时预览和拍照/录像
 *
 * 特效滤镜参考：
 * - GPUImage: https://github.com/BradLarson/GPUImage
 * - GPUPixel: https://gpupixel.pixpark.net/reference/filter-list
 * - android-gpuimage: https://github.com/cats-oss/android-gpuimage
 *
 * 水印功能参考：
 * - 小米/华为/OPPO/vivo相机水印设计
 * - GPS Map Camera等专业水印应用
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.domain.model

/**
 * 滤镜分组枚举
 *
 * @param displayName 分组显示名称
 * @param icon 分组图标名称（Material Icons）
 */
enum class FilterGroup(
    val displayName: String,
    val icon: String
) {
    ORIGINAL("原图", "photo"),              // 原图（无滤镜）
    STYLE("风格", "palette"),               // 风格滤镜（色调调整）
    EFFECT("特效", "auto_awesome"),         // GPU特效滤镜
    INSTAGRAM("INS", "camera"),             // Instagram经典风格（独立分类）
    WATERMARK("水印", "badge"),             // 水印相机（时间/位置/天气等）
    ADJUST("调整", "tune");                 // 图像调整参数

    companion object {
        /**
         * 获取相机可用的分组（不包含调整）
         */
        fun getCameraGroups(): List<FilterGroup> = listOf(
            ORIGINAL, STYLE, EFFECT, INSTAGRAM, WATERMARK
        )

        /**
         * 获取所有分组
         */
        fun getAllGroups(): List<FilterGroup> = entries.toList()

        /**
         * 获取GPU渲染分组（不含水印，水印使用Canvas绘制）
         */
        fun getGpuRenderGroups(): List<FilterGroup> = listOf(
            ORIGINAL, STYLE, EFFECT, INSTAGRAM
        )
    }
}

/**
 * 滤镜类型枚举
 *
 * @param displayName 显示名称（中文）
 * @param group 所属分组
 * @param thumbnailRes 缩略图资源ID（可选）
 * @param useGpu 是否使用GPU渲染（默认true）
 */
enum class FilterType(
    val displayName: String,
    val group: FilterGroup,
    val thumbnailRes: Int = 0,
    val useGpu: Boolean = true
) {
    // ========== 原图（无滤镜）==========
    NONE("原图", FilterGroup.ORIGINAL),

    // ========== 风格滤镜（19种）- GPU LUT/ColorMatrix ==========
    FAIRYTALE("童话", FilterGroup.STYLE),              // 梦幻色调
    SUNRISE("日出", FilterGroup.STYLE),                // 暖金色调
    SUNSET("日落", FilterGroup.STYLE),                 // 橙红色调
    WHITECAT("白猫", FilterGroup.STYLE),               // 高光增强
    BLACKCAT("黑猫", FilterGroup.STYLE),               // 暗部增强
    SKINWHITEN("美白", FilterGroup.STYLE),             // 肤色美白
    HEALTHY("健康", FilterGroup.STYLE),                // 健康肤色
    SWEETS("甜蜜", FilterGroup.STYLE),                 // 粉嫩色调
    ROMANCE("浪漫", FilterGroup.STYLE),                // 浪漫紫粉
    SAKURA("樱花", FilterGroup.STYLE),                 // 樱花粉色
    WARM("温暖", FilterGroup.STYLE),                   // 暖色调
    ANTIQUE("复古", FilterGroup.STYLE),                // 复古褐色
    NOSTALGIA("怀旧", FilterGroup.STYLE),              // 怀旧胶片
    CALM("平静", FilterGroup.STYLE),                   // 淡雅色调
    LATTE("拿铁", FilterGroup.STYLE),                  // 咖啡色调
    TENDER("柔和", FilterGroup.STYLE),                 // 柔和色调
    COOL("清凉", FilterGroup.STYLE),                   // 冷色调
    EMERALD("翡翠", FilterGroup.STYLE),                // 翠绿色调
    EVERGREEN("常青", FilterGroup.STYLE),              // 常青绿色

    // ========== 特效滤镜（22种）- GPU Fragment Shader ==========
    // 原有特效
    CRAYON("蜡笔", FilterGroup.EFFECT),                // GPU: 边缘检测 + 颜色量化
    SKETCH("素描", FilterGroup.EFFECT),                // GPU: Sobel边缘 + 反色

    // 扭曲类特效（5种）- GPU: 极坐标/径向UV变形
    SWIRL("漩涡", FilterGroup.EFFECT),                 // GPU: 极坐标旋转
    BULGE("鱼眼", FilterGroup.EFFECT),                 // GPU: 径向凸出
    PINCH("捏缩", FilterGroup.EFFECT),                 // GPU: 径向收缩
    STRETCH("拉伸", FilterGroup.EFFECT),               // GPU: 方向拉伸
    GLASS_SPHERE("玻璃球", FilterGroup.EFFECT),        // GPU: 球面折射

    // 像素化类特效（5种）- GPU: 采样/网格映射
    PIXELATE("像素化", FilterGroup.EFFECT),            // GPU: 方块采样
    HALFTONE("半色调", FilterGroup.EFFECT),            // GPU: 网点图案
    CROSSHATCH("交叉线", FilterGroup.EFFECT),          // GPU: 线条密度映射
    POLKA_DOT("波点", FilterGroup.EFFECT),             // GPU: 规则圆点网格
    MOSAIC("马赛克", FilterGroup.EFFECT),              // GPU: 瓷砖纹理

    // 艺术类特效（5种）- GPU: 滤波/边缘检测
    KUWAHARA("油画", FilterGroup.EFFECT),              // GPU: Kuwahara滤波
    POSTERIZE("色调分离", FilterGroup.EFFECT),         // GPU: 颜色量化
    EMBOSS("浮雕", FilterGroup.EFFECT),                // GPU: 卷积浮雕
    TOON("卡通", FilterGroup.EFFECT),                  // GPU: Sobel + 量化
    SMOOTH_TOON("平滑卡通", FilterGroup.EFFECT),       // GPU: 高斯 + 卡通

    // 模糊/边缘类特效（5种）- GPU: 高斯/方向模糊
    TILT_SHIFT("移轴", FilterGroup.EFFECT),            // GPU: 选择性高斯
    MOTION_BLUR("动态模糊", FilterGroup.EFFECT),       // GPU: 方向模糊
    ZOOM_BLUR("缩放模糊", FilterGroup.EFFECT),         // GPU: 径向模糊
    VIGNETTE("暗角", FilterGroup.EFFECT),              // GPU: 边缘衰减
    SOBEL_EDGE("边缘检测", FilterGroup.EFFECT),        // GPU: Sobel算子

    // ========== Instagram风格滤镜（20种）- GPU LUT ==========
    // Instagram经典滤镜，使用LUT查找表实现，独立分类便于快速访问
    AMARO("Amaro", FilterGroup.INSTAGRAM),             // 增加曝光，柔化高光
    BRANNAN("Brannan", FilterGroup.INSTAGRAM),         // 灰色调，金属感
    BROOKLYN("Brooklyn", FilterGroup.INSTAGRAM),       // 淡褪色，黄绿色调
    EARLYBIRD("Earlybird", FilterGroup.INSTAGRAM),     // 复古黄棕，暗角
    FREUD("Freud", FilterGroup.INSTAGRAM),             // 冷色调，蓝紫
    HEFE("Hefe", FilterGroup.INSTAGRAM),               // 高对比，暗角
    HUDSON("Hudson", FilterGroup.INSTAGRAM),           // 冷色，阴影蓝
    INKWELL("Inkwell", FilterGroup.INSTAGRAM),         // 经典黑白
    KEVIN("Kevin", FilterGroup.INSTAGRAM),             // 黄绿色调
    LOMO("Lomo", FilterGroup.INSTAGRAM),               // LOMO胶片风格
    N1977("1977", FilterGroup.INSTAGRAM),              // 1977年复古
    NASHVILLE("Nashville", FilterGroup.INSTAGRAM),     // 温暖粉色
    PIXAR("Pixar", FilterGroup.INSTAGRAM),             // 动画风格
    RISE("Rise", FilterGroup.INSTAGRAM),               // 柔和暖色
    SIERRA("Sierra", FilterGroup.INSTAGRAM),           // 柔和对比
    SUTRO("Sutro", FilterGroup.INSTAGRAM),             // 紫褐色调
    TOASTER("Toaster", FilterGroup.INSTAGRAM),         // 老照片，暗角
    VALENCIA("Valencia", FilterGroup.INSTAGRAM),       // 褪色暖色
    WALDEN("Walden", FilterGroup.INSTAGRAM),           // 黄色调增强
    XPROII("X-Pro II", FilterGroup.INSTAGRAM),         // 高对比，暗角

    // ========== 水印相机（4种）- Canvas绘制 ==========
    // 参考数码相机时间戳水印设计
    // 水印使用Canvas绘制，不使用GPU Shader
    WATERMARK_TIMESTAMP("时间戳", FilterGroup.WATERMARK, useGpu = false),         // 日期时间水印（数码相机风格）
    WATERMARK_DATE("日期", FilterGroup.WATERMARK, useGpu = false),                // 仅日期（不含时间）
    WATERMARK_DEVICE("设备信息", FilterGroup.WATERMARK, useGpu = false),          // 设备型号+镜头（类似徕卡水印）
    WATERMARK_CUSTOM("自定义", FilterGroup.WATERMARK, useGpu = false),            // 用户自定义文字

    // ========== 图像调整（7种）- GPU Shader参数 ==========
    CONTRAST("对比度", FilterGroup.ADJUST),            // GPU: 对比度调整
    BRIGHTNESS("亮度", FilterGroup.ADJUST),            // GPU: 亮度调整
    EXPOSURE("曝光", FilterGroup.ADJUST),              // GPU: 曝光调整
    HUE("色调", FilterGroup.ADJUST),                   // GPU: 色调旋转
    SATURATION("饱和度", FilterGroup.ADJUST),          // GPU: 饱和度调整
    SHARPEN("锐度", FilterGroup.ADJUST),               // GPU: 锐化滤波
    IMAGE_ADJUST("图像调整", FilterGroup.ADJUST);      // 综合调整面板

    companion object {
        /**
         * 根据分组获取滤镜列表
         */
        fun getFiltersByGroup(group: FilterGroup): List<FilterType> =
            entries.filter { it.group == group }

        /**
         * 获取所有可用的相机滤镜（不包含调整类型）
         */
        fun getCameraFilters(): List<FilterType> = entries.filter {
            it.group != FilterGroup.ADJUST
        }

        /**
         * 获取所有GPU渲染的滤镜
         */
        fun getGpuFilters(): List<FilterType> = entries.filter { it.useGpu }

        /**
         * 获取所有调整类型滤镜
         */
        fun getAdjustFilters(): List<FilterType> = entries.filter {
            it.group == FilterGroup.ADJUST && it != IMAGE_ADJUST
        }

        /**
         * 获取风格滤镜
         */
        fun getStyleFilters(): List<FilterType> = getFiltersByGroup(FilterGroup.STYLE)

        /**
         * 获取特效滤镜（GPU Shader）
         */
        fun getEffectFilters(): List<FilterType> = getFiltersByGroup(FilterGroup.EFFECT)

        /**
         * 获取Instagram风格滤镜
         */
        fun getInstagramFilters(): List<FilterType> = getFiltersByGroup(FilterGroup.INSTAGRAM)

        /**
         * 获取水印滤镜
         */
        fun getWatermarkFilters(): List<FilterType> = getFiltersByGroup(FilterGroup.WATERMARK)

        /**
         * 判断是否为水印类型
         */
        fun isWatermarkType(type: FilterType): Boolean = type.group == FilterGroup.WATERMARK
    }
}
