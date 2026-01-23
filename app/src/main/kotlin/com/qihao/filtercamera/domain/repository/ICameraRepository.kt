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
import com.qihao.filtercamera.domain.model.NightMode
import com.qihao.filtercamera.domain.model.PortraitBlurLevel
import com.qihao.filtercamera.domain.model.TimelapseSettings
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
     * 触摸对焦 - 在指定坐标点进行对焦和测光
     *
     * 使用CameraX的FocusMeteringAction在指定位置触发对焦
     * 坐标为归一化值，相对于预览区域
     *
     * @param x 归一化X坐标 (0.0~1.0，0为左边缘，1为右边缘)
     * @param y 归一化Y坐标 (0.0~1.0，0为上边缘，1为下边缘)
     * @return 操作结果，包含对焦是否成功
     */
    suspend fun focusAtPoint(x: Float, y: Float): Result<Unit>

    /**
     * 设置HDR模式
     * @param mode HDR模式
     * @return 操作结果
     */
    suspend fun setHdrMode(mode: HdrMode): Result<Unit>

    /**
     * 设置夜景模式
     *
     * 夜景模式启用后：
     * - 硬件支持时使用CameraX Night扩展（设备原生夜景）
     * - 硬件不支持时使用软件多帧合成算法
     * - AUTO模式下自动检测低光环境启用
     *
     * @param mode 夜景模式
     * @return 操作结果
     */
    suspend fun setNightMode(mode: NightMode): Result<Unit>

    /**
     * 获取夜景处理进度流
     *
     * 当夜景模式处理图像时，返回当前处理阶段和进度
     * 用于UI显示处理进度
     *
     * @return Pair<处理阶段名称, 进度(0.0~1.0)>，null表示未在处理中
     */
    fun getNightProcessingProgress(): Flow<Pair<String, Float>?>

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
     * 设置快门速度（曝光时间）
     *
     * 使用Camera2 SENSOR_EXPOSURE_TIME实现真实快门速度控制
     * 快门速度单位为秒，内部转换为纳秒传递给Camera2
     *
     * 注意：
     * - 设置手动快门速度需要禁用自动曝光（AE_MODE_OFF）
     * - 快门速度与ISO联动，需要配合调整防止过曝/欠曝
     * - 设备有最小/最大曝光时间限制
     *
     * @param speed 快门速度（秒），null表示恢复自动曝光
     *              例如：1/4000s = 0.00025f, 1s = 1.0f
     * @return 操作结果
     */
    suspend fun setShutterSpeed(speed: Float?): Result<Unit>

    /**
     * 获取设备支持的快门速度范围
     *
     * @return Pair(最小曝光时间纳秒, 最大曝光时间纳秒)，设备不支持时返回null
     */
    fun getExposureTimeRange(): Pair<Long, Long>?

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

    // ==================== 延时摄影控制 ====================

    /**
     * 开始延时摄影录制
     *
     * 初始化延时摄影引擎，准备开始捕获帧
     *
     * @param settings 延时摄影配置
     * @return 操作结果
     */
    suspend fun startTimelapse(settings: TimelapseSettings): Result<Unit>

    /**
     * 停止延时摄影并编码输出视频
     *
     * 停止帧捕获，将帧序列编码为MP4视频
     *
     * @return 输出视频文件路径
     */
    suspend fun stopTimelapse(): Result<String>

    /**
     * 取消延时摄影（不生成视频）
     *
     * @return 操作结果
     */
    suspend fun cancelTimelapse(): Result<Unit>

    /**
     * 暂停延时摄影
     *
     * @return 操作结果
     */
    suspend fun pauseTimelapse(): Result<Unit>

    /**
     * 恢复延时摄影
     *
     * @return 操作结果
     */
    suspend fun resumeTimelapse(): Result<Unit>

    /**
     * 获取延时摄影进度流
     *
     * @return Triple(已捕获帧数, 已用时间毫秒, 编码进度0.0~1.0)
     */
    fun getTimelapseProgress(): Flow<Triple<Int, Long, Float>>

    /**
     * 检查是否处于延时摄影录制状态
     *
     * @return true表示正在录制
     */
    fun isTimelapseRecording(): Boolean

    // ==================== 人像虚化控制 ====================

    /**
     * 设置人像虚化等级
     *
     * 应用于PORTRAIT模式的拍照
     *
     * @param level 虚化等级
     * @return 操作结果
     */
    suspend fun setPortraitBlurLevel(level: PortraitBlurLevel): Result<Unit>

    /**
     * 获取当前人像虚化等级
     *
     * @return 当前虚化等级
     */
    fun getPortraitBlurLevel(): PortraitBlurLevel
}
