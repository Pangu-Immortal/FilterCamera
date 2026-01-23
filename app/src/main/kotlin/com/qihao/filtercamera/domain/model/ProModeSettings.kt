/**
 * ProModeSettings.kt - 专业模式参数设置
 *
 * 定义专业相机模式的所有可调参数
 * 包括ISO、快门速度、白平衡、曝光补偿等
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.domain.model

import java.util.Locale

/**
 * 白平衡模式枚举
 */
enum class WhiteBalanceMode(
    val displayName: String,                                          // 显示名称
    val temperature: Int?                                             // 色温值（null表示自动）
) {
    AUTO("自动", null),                                                // 自动白平衡
    INCANDESCENT("白炽灯", 2700),                                      // 白炽灯（暖色）
    FLUORESCENT("荧光灯", 4000),                                       // 荧光灯
    DAYLIGHT("日光", 5500),                                            // 日光
    CLOUDY("阴天", 6500),                                              // 阴天
    SHADE("阴影", 7500);                                               // 阴影（冷色）

    companion object {
        fun getAll(): List<WhiteBalanceMode> = entries.toList()
    }
}

/**
 * 对焦模式枚举
 */
enum class FocusMode(val displayName: String) {
    AUTO("自动"),                                                       // 自动对焦
    CONTINUOUS("连续"),                                                 // 连续自动对焦
    MANUAL("手动");                                                     // 手动对焦

    companion object {
        fun getAll(): List<FocusMode> = entries.toList()
    }
}

/**
 * 测光模式枚举
 */
enum class MeteringMode(val displayName: String) {
    AVERAGE("平均"),                                                    // 平均测光
    CENTER_WEIGHTED("中央重点"),                                        // 中央重点测光
    SPOT("点测光");                                                     // 点测光

    companion object {
        fun getAll(): List<MeteringMode> = entries.toList()
    }
}

/**
 * 专业模式参数数据类
 *
 * @param iso ISO感光度（100-3200，null为自动）
 * @param shutterSpeed 快门速度（秒，null为自动）
 * @param exposureCompensation 曝光补偿（-3.0到+3.0 EV）
 * @param whiteBalance 白平衡模式
 * @param focusMode 对焦模式
 * @param focusDistance 手动对焦距离（0.0-1.0，仅手动模式有效）
 * @param meteringMode 测光模式
 */
data class ProModeSettings(
    val iso: Int? = null,                                             // ISO（null为自动）
    val shutterSpeed: Float? = null,                                  // 快门速度（null为自动）
    val exposureCompensation: Float = 0f,                             // 曝光补偿
    val whiteBalance: WhiteBalanceMode = WhiteBalanceMode.AUTO,       // 白平衡
    val focusMode: FocusMode = FocusMode.AUTO,                        // 对焦模式
    val focusDistance: Float = 0.5f,                                  // 对焦距离（0=最近，1=无穷远）
    val meteringMode: MeteringMode = MeteringMode.AVERAGE             // 测光模式
) {
    companion object {
        // ISO范围
        val ISO_VALUES = listOf(null, 100, 200, 400, 800, 1600, 3200) // null表示自动

        // 快门速度选项（秒）
        val SHUTTER_SPEEDS = listOf(
            null,                                                     // 自动
            1f / 4000,                                                // 1/4000s
            1f / 2000,                                                // 1/2000s
            1f / 1000,                                                // 1/1000s
            1f / 500,                                                 // 1/500s
            1f / 250,                                                 // 1/250s
            1f / 125,                                                 // 1/125s
            1f / 60,                                                  // 1/60s
            1f / 30,                                                  // 1/30s
            1f / 15,                                                  // 1/15s
            1f / 8,                                                   // 1/8s
            1f / 4,                                                   // 1/4s
            1f / 2,                                                   // 1/2s
            1f                                                        // 1s
        )

        // 曝光补偿范围
        const val EV_MIN = -3f
        const val EV_MAX = 3f
        const val EV_STEP = 0.5f

        /**
         * 格式化ISO显示
         */
        fun formatIso(iso: Int?): String = iso?.toString() ?: "自动"

        /**
         * 格式化快门速度显示
         */
        fun formatShutterSpeed(speed: Float?): String {
            return when {
                speed == null -> "自动"
                speed >= 1f -> "${speed.toInt()}s"
                speed >= 0.1f -> "1/${(1f / speed).toInt()}"
                else -> "1/${(1f / speed).toInt()}"
            }
        }

        /**
         * 格式化曝光补偿显示
         */
        fun formatEV(ev: Float): String {
            return when {
                ev > 0 -> "+${String.format(Locale.US, "%.1f", ev)} EV"
                ev < 0 -> "${String.format(Locale.US, "%.1f", ev)} EV"
                else -> "0 EV"
            }
        }
    }
}

/**
 * 专业模式参数变更事件
 */
sealed class ProModeEvent {
    data class IsoChanged(val iso: Int?) : ProModeEvent()
    data class ShutterSpeedChanged(val speed: Float?) : ProModeEvent()
    data class ExposureCompensationChanged(val ev: Float) : ProModeEvent()
    data class WhiteBalanceChanged(val mode: WhiteBalanceMode) : ProModeEvent()
    data class FocusModeChanged(val mode: FocusMode) : ProModeEvent()
    data class FocusDistanceChanged(val distance: Float) : ProModeEvent()
    data class MeteringModeChanged(val mode: MeteringMode) : ProModeEvent()
}
