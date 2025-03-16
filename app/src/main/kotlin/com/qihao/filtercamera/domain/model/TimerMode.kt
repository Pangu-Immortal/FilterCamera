/**
 * TimerMode.kt - 定时拍照模式枚举
 *
 * 定义相机定时拍照的倒计时模式
 * 支持关闭、3秒、5秒、10秒四种模式
 *
 * 功能说明：
 * - 用户在设置面板选择定时模式
 * - 点击快门后显示倒计时
 * - 倒计时归零时自动拍照
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.domain.model

/**
 * 定时拍照模式枚举
 *
 * @param displayName 显示名称（中文）
 * @param seconds 倒计时秒数，0表示关闭
 * @param iconName Material Icons图标名称
 */
enum class TimerMode(
    val displayName: String,
    val seconds: Int,
    val iconName: String
) {
    OFF("关闭", 0, "timer_off"),         // 关闭定时拍照
    TIMER_3S("3秒", 3, "timer_3"),       // 3秒倒计时
    TIMER_5S("5秒", 5, "timer"),         // 5秒倒计时（默认图标）
    TIMER_10S("10秒", 10, "timer_10");   // 10秒倒计时

    companion object {
        /**
         * 获取所有定时模式
         */
        fun getAllModes(): List<TimerMode> = entries.toList()

        /**
         * 获取默认模式
         */
        fun getDefault(): TimerMode = OFF

        /**
         * 循环切换到下一个模式
         * OFF -> 3S -> 5S -> 10S -> OFF
         */
        fun TimerMode.next(): TimerMode {
            val modes = entries.toList()                           // 获取所有模式
            val currentIndex = modes.indexOf(this)                 // 当前索引
            val nextIndex = (currentIndex + 1) % modes.size        // 下一个索引（循环）
            return modes[nextIndex]                                // 返回下一个模式
        }

        /**
         * 判断是否启用定时拍照
         */
        fun isTimerEnabled(mode: TimerMode): Boolean = mode != OFF
    }
}
