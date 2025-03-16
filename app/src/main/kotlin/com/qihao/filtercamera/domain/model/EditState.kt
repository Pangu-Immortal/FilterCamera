/**
 * EditState.kt - 图片编辑状态模型
 *
 * 管理图片编辑器的所有状态
 * 包含：当前编辑模式、调整参数、裁剪参数、滤镜选择等
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.domain.model

import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri

/**
 * 编辑模式枚举
 *
 * 定义编辑器的三种主要模式
 */
enum class EditMode(val displayName: String) {
    ADJUST("调整"),                                                           // 调整模式：亮度/对比度/饱和度等
    CROP("裁剪"),                                                             // 裁剪模式：自由裁剪/固定比例
    FILTER("滤镜");                                                           // 滤镜模式：应用滤镜效果

    companion object {
        fun getAll(): List<EditMode> = entries.toList()
    }
}

/**
 * 裁剪比例枚举
 *
 * 定义常用的裁剪比例
 */
enum class CropRatio(val displayName: String, val widthRatio: Float, val heightRatio: Float) {
    FREE("自由", 0f, 0f),                                                     // 自由裁剪
    RATIO_1_1("1:1", 1f, 1f),                                                 // 正方形
    RATIO_4_3("4:3", 4f, 3f),                                                 // 标准相机
    RATIO_3_4("3:4", 3f, 4f),                                                 // 竖版
    RATIO_16_9("16:9", 16f, 9f),                                              // 宽屏
    RATIO_9_16("9:16", 9f, 16f);                                              // 竖版宽屏

    /**
     * 计算比例值
     * @return 宽高比，FREE返回0表示不限制
     */
    fun getAspectRatio(): Float {
        return if (this == FREE) 0f else widthRatio / heightRatio
    }

    companion object {
        fun getAll(): List<CropRatio> = entries.toList()
    }
}

/**
 * 图片调整参数
 *
 * 所有参数范围：-1.0 ~ 1.0，0为原始值
 */
data class AdjustParams(
    val brightness: Float = 0f,                                               // 亮度 (-1.0 ~ 1.0)
    val contrast: Float = 0f,                                                 // 对比度 (-1.0 ~ 1.0)
    val saturation: Float = 0f,                                               // 饱和度 (-1.0 ~ 1.0)
    val sharpness: Float = 0f,                                                // 锐度 (-1.0 ~ 1.0)
    val warmth: Float = 0f,                                                   // 色温 (-1.0 ~ 1.0)
    val vignette: Float = 0f,                                                 // 暗角 (0 ~ 1.0)
    val highlights: Float = 0f,                                               // 高光 (-1.0 ~ 1.0)
    val shadows: Float = 0f                                                   // 阴影 (-1.0 ~ 1.0)
) {
    /**
     * 检查是否有任何调整
     */
    fun hasAdjustments(): Boolean {
        return brightness != 0f || contrast != 0f || saturation != 0f ||
               sharpness != 0f || warmth != 0f || vignette != 0f ||
               highlights != 0f || shadows != 0f
    }

    /**
     * 重置所有参数
     */
    fun reset(): AdjustParams = AdjustParams()
}

/**
 * 调整类型枚举
 *
 * 用于UI显示和参数选择
 */
enum class AdjustType(val displayName: String, val icon: String) {
    BRIGHTNESS("亮度", "☀"),
    CONTRAST("对比度", "◐"),
    SATURATION("饱和度", "🎨"),
    SHARPNESS("锐度", "△"),
    WARMTH("色温", "🌡"),
    VIGNETTE("暗角", "⬤"),
    HIGHLIGHTS("高光", "◑"),
    SHADOWS("阴影", "◒");

    companion object {
        fun getAll(): List<AdjustType> = entries.toList()
    }
}

/**
 * 裁剪状态
 *
 * 包含裁剪框位置、比例等信息
 */
data class CropState(
    val cropRect: RectF = RectF(),                                            // 裁剪框矩形（相对于图片的比例坐标）
    val cropRatio: CropRatio = CropRatio.FREE,                                // 裁剪比例
    val rotation: Float = 0f,                                                 // 旋转角度（0, 90, 180, 270）
    val isFlippedHorizontal: Boolean = false,                                 // 水平翻转
    val isFlippedVertical: Boolean = false                                    // 垂直翻转
) {
    /**
     * 检查是否有任何变换
     */
    fun hasTransforms(): Boolean {
        return rotation != 0f || isFlippedHorizontal || isFlippedVertical ||
               !cropRect.isEmpty
    }
}

/**
 * 编辑历史记录项
 *
 * 用于撤销/重做功能
 */
data class EditHistoryItem(
    val adjustParams: AdjustParams,
    val cropState: CropState,
    val filterType: FilterType,
    val filterIntensity: Float,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 图片编辑状态
 *
 * 管理编辑器的完整状态
 */
data class EditState(
    // 源图片信息
    val sourceUri: Uri? = null,                                               // 源图片URI
    val sourceBitmap: Bitmap? = null,                                         // 源图片Bitmap（原始未编辑）
    val previewBitmap: Bitmap? = null,                                        // 预览Bitmap（应用编辑效果后）

    // 当前编辑模式
    val currentMode: EditMode = EditMode.ADJUST,                              // 当前编辑模式
    val selectedAdjustType: AdjustType = AdjustType.BRIGHTNESS,               // 当前选中的调整类型

    // 编辑参数
    val adjustParams: AdjustParams = AdjustParams(),                          // 调整参数
    val cropState: CropState = CropState(),                                   // 裁剪状态
    val filterType: FilterType = FilterType.NONE,                             // 当前滤镜
    val filterIntensity: Float = 1.0f,                                        // 滤镜强度

    // UI状态
    val isLoading: Boolean = false,                                           // 是否正在加载
    val isSaving: Boolean = false,                                            // 是否正在保存
    val isComparing: Boolean = false,                                         // 是否正在对比原图
    val showFilterSelector: Boolean = false,                                  // 是否显示滤镜选择器

    // 历史记录（用于撤销/重做）
    val historyIndex: Int = -1,                                               // 当前历史索引
    val history: List<EditHistoryItem> = emptyList(),                         // 历史记录列表

    // 错误信息
    val errorMessage: String? = null                                          // 错误消息
) {
    /**
     * 检查是否可以撤销
     */
    fun canUndo(): Boolean = historyIndex > 0

    /**
     * 检查是否可以重做
     */
    fun canRedo(): Boolean = historyIndex < history.size - 1

    /**
     * 检查是否有任何编辑
     */
    fun hasEdits(): Boolean {
        return adjustParams.hasAdjustments() ||
               cropState.hasTransforms() ||
               filterType != FilterType.NONE
    }
}

/**
 * 编辑事件
 *
 * 用于一次性事件（如保存成功、错误提示）
 */
sealed class EditEvent {
    data class SaveSuccess(val outputUri: Uri) : EditEvent()                  // 保存成功
    data class SaveFailed(val message: String) : EditEvent()                  // 保存失败
    data class Error(val message: String) : EditEvent()                       // 其他错误
    data object LoadSuccess : EditEvent()                                     // 加载成功
}
