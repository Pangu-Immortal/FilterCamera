/**
 * ICameraRepository.kt - 相机仓库接口
 *
 * 定义相机操作的抽象接口，遵循依赖倒置原则
 * 由data层实现具体的CameraX操作
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.domain.repository

import android.graphics.Bitmap
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.qihao.filtercamera.domain.model.AspectRatio
import com.qihao.filtercamera.domain.model.CameraLens
import com.qihao.filtercamera.domain.model.FilterType
import com.qihao.filtercamera.domain.model.FlashMode
import com.qihao.filtercamera.domain.model.FocusMode
import com.qihao.filtercamera.domain.model.HdrMode
import com.qihao.filtercamera.domain.model.MacroMode
import com.qihao.filtercamera.domain.model.WhiteBalanceMode
import kotlinx.coroutines.flow.Flow

/**
 * 变焦范围数据类
 *
 * @param minZoom 最小变焦倍数
 * @param maxZoom 最大变焦倍数
 */
data class ZoomRange(
    val minZoom: Float = 1.0f,
    val maxZoom: Float = 10.0f
)

/**
 * 相机仓库接口
 * 提供相机控制的核心操作
 */
interface ICameraRepository {

    /**
     * 绑定相机到生命周期和预览视图
     * @param owner 生命周期持有者
     * @param previewView 预览视图
     */
    suspend fun bindCamera(owner: LifecycleOwner, previewView: PreviewView)

    /**
     * 拍照
     * @return 照片保存路径的Flow
     */
    suspend fun takePhoto(): Result<String>

    /**
     * 开始录像
     * @return 操作结果
     */
    suspend fun startRecording(): Result<Unit>

    /**
     * 停止录像
     * @return 视频保存路径
     */
    suspend fun stopRecording(): Result<String>

    /**
     * 切换摄像头
     * @param lens 目标镜头
     * @return 操作结果
     */
    suspend fun switchCamera(lens: CameraLens): Result<Unit>

    /**
     * 应用滤镜
     * @param filterType 滤镜类型
     * @return 操作结果
     */
    suspend fun applyFilter(filterType: FilterType): Result<Unit>

    /**
     * 设置美颜等级
     * @param intensity 美颜强度（0.0-1.0）
     * @return 操作结果
     */
    suspend fun setBeautyLevel(intensity: Float): Result<Unit>

    /**
     * 设置变焦倍数
     * @param zoom 变焦倍数
     * @return 操作结果
     */
    suspend fun setZoom(zoom: Float): Result<Unit>

    /**
     * 触发自动对焦
     * 在预览中心点进行自动对焦
     * @return 操作结果
     */
    suspend fun autoFocus(): Result<Unit>

    /**
     * 设置HDR模式
     * @param mode HDR模式
     * @return 操作结果
     */
    suspend fun setHdrMode(mode: HdrMode): Result<Unit>

    /**
     * 设置微距模式
     * @param mode 微距模式
     * @return 操作结果
     */
    suspend fun setMacroMode(mode: MacroMode): Result<Unit>

    /**
     * 设置画幅比例
     * @param ratio 画幅比例
     * @return 操作结果
     */
    suspend fun setAspectRatio(ratio: AspectRatio): Result<Unit>

    /**
     * 获取当前镜头
     * @return 当前镜头状态Flow
     */
    fun getCurrentLens(): Flow<CameraLens>

    /**
     * 获取录像状态
     * @return 录像状态Flow
     */
    fun isRecording(): Flow<Boolean>

    /**
     * 获取滤镜帧流（用于实时预览）
     *
     * 当应用滤镜时，返回处理后的Bitmap帧
     * 当不应用滤镜（NONE）时，返回null
     *
     * @return 滤镜处理后的Bitmap流
     */
    fun getFilteredFrame(): Flow<Bitmap?>

    /**
     * 获取变焦范围
     * @return 设备支持的变焦范围Flow
     */
    fun getZoomRange(): Flow<ZoomRange>

    /**
     * 获取原始预览帧流（不带滤镜）
     *
     * 用于生成滤镜缩略图等需要原始帧的场景
     * 此帧不受当前滤镜影响，始终是原始相机帧
     *
     * @return 原始预览帧Bitmap流
     */
    fun getRawPreviewFrame(): Flow<Bitmap?>

    /**
     * 释放相机资源
     */
    suspend fun release()

    // ==================== 专业模式参数控制 ====================

    /**
     * 设置曝光补偿
     * @param evIndex 曝光补偿索引（设备范围内）
     * @return 操作结果
     */
    suspend fun setExposureCompensation(evIndex: Int): Result<Unit>

    /**
     * 设置ISO感光度
     * @param iso ISO值，null表示自动
     * @return 操作结果
     */
    suspend fun setIso(iso: Int?): Result<Unit>

    /**
     * 设置白平衡模式
     * @param mode 白平衡模式
     * @return 操作结果
     */
    suspend fun setWhiteBalance(mode: WhiteBalanceMode): Result<Unit>

    /**
     * 设置对焦模式
     * @param mode 对焦模式
     * @return 操作结果
     */
    suspend fun setFocusMode(mode: FocusMode): Result<Unit>

    /**
     * 设置手动对焦距离
     * @param distance 对焦距离（0.0=最近，1.0=无穷远）
     * @return 操作结果
     */
    suspend fun setFocusDistance(distance: Float): Result<Unit>

    /**
     * 获取曝光补偿范围
     * @return 曝光补偿范围（minIndex, maxIndex, step）
     */
    fun getExposureCompensationRange(): Triple<Int, Int, Float>

    // ==================== 闪光灯控制 ====================

    /**
     * 设置闪光灯模式
     *
     * 控制相机闪光灯的工作模式
     * - OFF: 关闭闪光灯
     * - ON: 拍照时强制开启
     * - AUTO: 根据环境光自动决定
     * - TORCH: 持续照明（手电筒模式）
     *
     * @param mode 闪光灯模式
     * @return 操作结果
     */
    suspend fun setFlashMode(mode: FlashMode): Result<Unit>

    /**
     * 获取当前闪光灯模式
     * @return 当前闪光灯模式
     */
    fun getCurrentFlashMode(): FlashMode

    /**
     * 检查设备是否支持闪光灯
     * @return true表示支持闪光灯
     */
    fun hasFlashUnit(): Boolean
}
