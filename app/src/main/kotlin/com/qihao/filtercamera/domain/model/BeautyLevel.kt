/**
 * BeautyLevel.kt - 美颜等级枚举
 *
 * 定义美颜强度等级（0-10级）
 * 使用GPU加速的美颜算法，支持实时预览
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.domain.model

/**
 * 美颜等级枚举
 *
 * @param level 等级数值（0-10）
 * @param displayName 显示名称
 * @param intensity 实际强度（0.0-1.0）
 */
enum class BeautyLevel(
    val level: Int,
    val displayName: String,
    val intensity: Float
) {
    OFF(0, "关闭", 0.0f),           // 关闭美颜
    LEVEL_1(1, "1级", 0.1f),        // 自然美颜
    LEVEL_2(2, "2级", 0.2f),        // 轻度美颜
    LEVEL_3(3, "3级", 0.3f),        // 自然偏轻
    LEVEL_4(4, "4级", 0.4f),        // 中度美颜
    LEVEL_5(5, "5级", 0.5f),        // 标准美颜
    LEVEL_6(6, "6级", 0.6f),        // 中度偏强
    LEVEL_7(7, "7级", 0.7f),        // 明显美颜
    LEVEL_8(8, "8级", 0.8f),        // 强度美颜
    LEVEL_9(9, "9级", 0.9f),        // 高度美颜
    LEVEL_10(10, "10级", 1.0f);     // 最强美颜

    companion object {
        /**
         * 从数值获取美颜等级
         */
        fun fromLevel(level: Int): BeautyLevel =
            entries.find { it.level == level } ?: OFF

        /**
         * 从强度值获取最接近的美颜等级
         */
        fun fromIntensity(intensity: Float): BeautyLevel =
            entries.minByOrNull { kotlin.math.abs(it.intensity - intensity) } ?: OFF

        /**
         * 默认美颜等级（5级，标准美颜）
         */
        val DEFAULT = LEVEL_5

        /**
         * 获取所有可用等级列表
         */
        fun getAllLevels(): List<BeautyLevel> = entries.toList()

        /**
         * 获取等级总数（不含关闭）
         */
        fun getLevelCount(): Int = entries.size - 1
    }
}
