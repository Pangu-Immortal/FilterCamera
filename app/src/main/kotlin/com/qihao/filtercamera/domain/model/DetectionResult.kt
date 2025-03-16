/**
 * DetectionResult.kt - 检测结果模型
 *
 * 定义人脸检测和文档检测的结果数据类
 * 属于Domain层，不依赖任何Android或第三方库
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.domain.model

/**
 * 归一化矩形区域（0-1坐标系）
 *
 * @param left 左边界（0-1）
 * @param top 上边界（0-1）
 * @param right 右边界（0-1）
 * @param bottom 下边界（0-1）
 */
data class NormalizedRect(
    val left: Float,                                                  // 左边界
    val top: Float,                                                   // 上边界
    val right: Float,                                                 // 右边界
    val bottom: Float                                                 // 下边界
) {
    val centerX: Float get() = (left + right) / 2f                   // 中心X
    val centerY: Float get() = (top + bottom) / 2f                   // 中心Y
    val width: Float get() = right - left                            // 宽度
    val height: Float get() = bottom - top                           // 高度
}

/**
 * 归一化点（0-1坐标系）
 *
 * @param x X坐标（0-1）
 * @param y Y坐标（0-1）
 */
data class NormalizedPoint(
    val x: Float,                                                     // X坐标
    val y: Float                                                      // Y坐标
)

/**
 * 人脸信息数据类
 *
 * @param boundingBox 人脸边界框（归一化坐标 0-1）
 * @param centerX 人脸中心X坐标（归一化）
 * @param centerY 人脸中心Y坐标（归一化）
 * @param confidence 检测置信度（0-1）
 */
data class FaceInfo(
    val boundingBox: NormalizedRect,                                  // 边界框（归一化坐标）
    val centerX: Float,                                               // 中心X
    val centerY: Float,                                               // 中心Y
    val confidence: Float = 1f                                        // 置信度
)

/**
 * 文档边界数据类
 *
 * @param topLeft 左上角点（归一化坐标）
 * @param topRight 右上角点（归一化坐标）
 * @param bottomRight 右下角点（归一化坐标）
 * @param bottomLeft 左下角点（归一化坐标）
 * @param confidence 检测置信度（0-1）
 */
data class DocumentBounds(
    val topLeft: NormalizedPoint,                                     // 左上角
    val topRight: NormalizedPoint,                                    // 右上角
    val bottomRight: NormalizedPoint,                                 // 右下角
    val bottomLeft: NormalizedPoint,                                  // 左下角
    val confidence: Float = 1f                                        // 置信度
) {
    /**
     * 获取边界矩形（用于简单绘制）
     */
    fun getBoundingRect(): NormalizedRect {
        val left = minOf(topLeft.x, bottomLeft.x, topRight.x, bottomRight.x)
        val right = maxOf(topLeft.x, bottomLeft.x, topRight.x, bottomRight.x)
        val top = minOf(topLeft.y, topRight.y, bottomLeft.y, bottomRight.y)
        val bottom = maxOf(topLeft.y, topRight.y, bottomLeft.y, bottomRight.y)
        return NormalizedRect(left, top, right, bottom)
    }
}
