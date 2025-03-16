/**
 * CameraSettings.kt - 相机设置数据模型
 *
 * 定义相机高级设置选项
 * 包括：闪光灯、HDR、超级微距、画幅比例、光圈、变焦等
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.domain.model

/**
 * 闪光灯模式枚举
 *
 * 定义相机闪光灯的工作模式
 *
 * @param displayName UI显示名称
 * @param iconResName 图标资源名称
 */
enum class FlashMode(
    val displayName: String,
    val iconResName: String
) {
    OFF("关闭", "flash_off"),                                               // 关闭闪光灯
    ON("开启", "flash_on"),                                                 // 强制开启
    AUTO("自动", "flash_auto"),                                             // 自动闪光
    TORCH("常亮", "flash_torch");                                           // 手电筒模式（持续照明）

    companion object {
        /**
         * 获取所有闪光灯模式
         */
        fun getAll(): List<FlashMode> = entries.toList()

        /**
         * 获取默认闪光灯模式
         */
        fun getDefault(): FlashMode = AUTO

        /**
         * 循环切换到下一个模式
         * 用于点击闪光灯按钮时循环切换
         */
        fun next(current: FlashMode): FlashMode {
            val modes = listOf(OFF, ON, AUTO)  // 不包含TORCH（手电筒需要单独触发）
            val currentIndex = modes.indexOf(current)
            return if (currentIndex == -1 || currentIndex == modes.lastIndex) {
                modes.first()
            } else {
                modes[currentIndex + 1]
            }
        }
    }
}

/**
 * HDR模式枚举
 *
 * @param displayName 显示名称
 * @param isAuto 是否为自动模式
 */
enum class HdrMode(
    val displayName: String,
    val isAuto: Boolean = false
) {
    OFF("关闭", false),                                                    // 关闭HDR
    ON("开启", false),                                                     // 强制开启
    AUTO("自动", true);                                                    // 自动HDR

    companion object {
        fun getAll(): List<HdrMode> = entries.toList()
        fun getDefault(): HdrMode = AUTO
    }
}

/**
 * 超级微距模式枚举
 *
 * @param displayName 显示名称
 * @param isAuto 是否为自动模式
 */
enum class MacroMode(
    val displayName: String,
    val isAuto: Boolean = false
) {
    OFF("关闭", false),                                                    // 关闭微距
    ON("开启", false),                                                     // 强制开启
    AUTO("自动", true);                                                    // 自动检测切换

    companion object {
        fun getAll(): List<MacroMode> = entries.toList()
        fun getDefault(): MacroMode = AUTO
    }
}

/**
 * 画幅比例枚举
 *
 * 定义相机支持的画幅比例
 * 注意：CameraX原生仅支持4:3和16:9，其他比例通过预览裁剪实现
 *
 * @param displayName 显示名称（UI展示）
 * @param widthRatio 宽度比例因子
 * @param heightRatio 高度比例因子
 * @param cameraXRatio 对应的CameraX原生比例（用于相机配置）
 */
enum class AspectRatio(
    val displayName: String,
    val widthRatio: Int,
    val heightRatio: Int,
    val cameraXRatio: Int = androidx.camera.core.AspectRatio.RATIO_4_3  // 默认4:3
) {
    RATIO_4_3("4:3", 4, 3, androidx.camera.core.AspectRatio.RATIO_4_3),        // 标准4:3（CameraX原生）
    RATIO_16_9("16:9", 16, 9, androidx.camera.core.AspectRatio.RATIO_16_9),    // 宽屏16:9（CameraX原生）
    RATIO_FULL("全屏", 0, 0, androidx.camera.core.AspectRatio.RATIO_16_9);     // 全屏（使用16:9填充）

    companion object {
        /**
         * 获取所有可用画幅（UI选择用）
         */
        fun getAll(): List<AspectRatio> = entries.toList()

        /**
         * 获取默认画幅（4:3）
         */
        fun getDefault(): AspectRatio = RATIO_4_3

        /**
         * 计算实际宽高比数值
         *
         * @param ratio 画幅枚举
         * @return 宽高比浮点值（全屏返回0f表示使用设备比例）
         */
        fun getRatioValue(ratio: AspectRatio): Float {
            return if (ratio.heightRatio == 0) 0f
            else ratio.widthRatio.toFloat() / ratio.heightRatio.toFloat()
        }

        /**
         * 根据显示名称获取画幅
         *
         * @param displayName 显示名称
         * @return 对应的画幅枚举，未找到返回默认值
         */
        fun fromDisplayName(displayName: String): AspectRatio {
            return entries.find { it.displayName == displayName } ?: getDefault()
        }
    }
}

/**
 * 光圈模式枚举
 *
 * 模拟光圈效果（背景虚化程度）
 *
 * @param displayName 显示名称
 * @param fNumber 光圈值（f值，越小虚化越强）
 * @param isAuto 是否自动
 */
enum class ApertureMode(
    val displayName: String,
    val fNumber: Float,
    val isAuto: Boolean = false
) {
    AUTO("AUTO", 0f, true),                                               // 自动光圈
    F1_4("f/1.4", 1.4f, false),                                           // 大光圈（强虚化）
    F2_0("f/2.0", 2.0f, false),
    F2_8("f/2.8", 2.8f, false),
    F4_0("f/4.0", 4.0f, false),
    F5_6("f/5.6", 5.6f, false),
    F8_0("f/8.0", 8.0f, false),                                           // 小光圈（弱虚化）
    F16("f/16", 16f, false);                                              // 最小光圈

    companion object {
        fun getAll(): List<ApertureMode> = entries.toList()
        fun getDefault(): ApertureMode = AUTO
    }
}

/**
 * 变焦范围常量
 */
object ZoomConfig {
    const val MIN_ZOOM = 1.0f                                             // 最小变焦（1x）
    const val MAX_ZOOM = 10.0f                                            // 最大变焦（10x）
    const val DEFAULT_ZOOM = 1.0f                                         // 默认变焦
    const val MACRO_ZOOM = 2.0f                                           // 微距推荐变焦
    const val TELEPHOTO_THRESHOLD = 3.0f                                  // 长焦阈值

    // 快捷变焦档位
    val ZOOM_PRESETS = listOf(0.6f, 1.0f, 2.0f, 5.0f, 10.0f)             // 快捷档位

    /**
     * 格式化变焦倍数显示
     */
    fun formatZoom(zoom: Float): String {
        return if (zoom < 1f) {
            String.format("%.1fx", zoom)
        } else if (zoom == zoom.toInt().toFloat()) {
            "${zoom.toInt()}x"
        } else {
            String.format("%.1fx", zoom)
        }
    }
}

/**
 * 自适应对焦模式
 */
enum class AdaptiveFocusMode(val displayName: String) {
    AUTO("自动"),                                                          // 自动检测场景
    CLOSE_UP("近距离"),                                                    // 近距离优化
    TELEPHOTO("长焦"),                                                     // 长焦优化
    MACRO("超级微距"),                                                     // 微距优化
    LANDSCAPE("风景");                                                     // 风景（无穷远）

    companion object {
        fun getAll(): List<AdaptiveFocusMode> = entries.toList()
    }
}

/**
 * 相机高级设置数据类
 *
 * @param flashMode 闪光灯模式
 * @param hdrMode HDR模式
 * @param macroMode 超级微距模式
 * @param aspectRatio 画幅比例
 * @param apertureMode 光圈模式
 * @param zoomLevel 当前变焦倍数
 * @param adaptiveFocus 自适应对焦模式
 * @param isAutoFocusOnStart 启动时自动对焦
 */
data class CameraAdvancedSettings(
    val flashMode: FlashMode = FlashMode.AUTO,                                // 闪光灯模式
    val hdrMode: HdrMode = HdrMode.AUTO,
    val macroMode: MacroMode = MacroMode.AUTO,
    val aspectRatio: AspectRatio = AspectRatio.RATIO_4_3,                     // 默认4:3画幅
    val apertureMode: ApertureMode = ApertureMode.AUTO,
    val zoomLevel: Float = ZoomConfig.DEFAULT_ZOOM,
    val adaptiveFocus: AdaptiveFocusMode = AdaptiveFocusMode.AUTO,
    val isAutoFocusOnStart: Boolean = true
) {
    /**
     * 是否为微距模式激活状态
     */
    val isMacroActive: Boolean
        get() = macroMode == MacroMode.ON ||
                (macroMode == MacroMode.AUTO && zoomLevel >= ZoomConfig.MACRO_ZOOM)

    /**
     * 是否为长焦模式
     */
    val isTelephotoActive: Boolean
        get() = zoomLevel >= ZoomConfig.TELEPHOTO_THRESHOLD
}
