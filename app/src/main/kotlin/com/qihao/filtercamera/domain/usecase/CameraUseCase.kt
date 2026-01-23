/**
 * CameraUseCase.kt - 统一相机用例
 *
 * 合并所有相机相关操作到单一用例类，提供简洁优雅的API
 * 包含：拍照、录像、切换摄像头、滤镜应用、美颜设置
 *
 * 设计原则：
 * - 单一入口点，降低调用复杂度
 * - 使用扩展函数提供便捷操作
 * - 保持职责清晰，委托给对应Repository
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.domain.usecase

import android.graphics.Bitmap
import android.net.Uri
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.qihao.filtercamera.domain.model.BeautyLevel
import com.qihao.filtercamera.domain.model.CameraLens
import com.qihao.filtercamera.domain.model.FilterType
import com.qihao.filtercamera.domain.repository.ICameraRepository
import com.qihao.filtercamera.domain.repository.IFilterRepository
import com.qihao.filtercamera.domain.repository.IMediaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 统一相机用例
 *
 * 整合所有相机操作，提供简洁的API接口
 *
 * @param camera 相机仓库 - 负责CameraX操作
 * @param filter 滤镜仓库 - 负责滤镜状态管理
 * @param media 媒体仓库 - 负责文件存储
 */
@Singleton
class CameraUseCase @Inject constructor(
    private val camera: ICameraRepository,
    private val filter: IFilterRepository,
    private val media: IMediaRepository
) {
    // ==================== 相机绑定 ====================

    /**
     * 绑定相机到生命周期和预览视图
     * @param owner 生命周期持有者
     * @param previewView 预览视图
     */
    suspend fun bindCamera(owner: LifecycleOwner, previewView: PreviewView) =
        camera.bindCamera(owner, previewView)

    // ==================== 拍照操作 ====================

    /**
     * 执行拍照并保存到相册
     * @return 照片Uri的Result
     */
    suspend fun takePhoto(): Result<Uri> = runCatching {
        val photoPath = camera.takePhoto().getOrThrow()               // 调用CameraX拍照
        val photoFile = File(photoPath)                               // 获取临时文件
        require(photoFile.exists()) { "照片文件不存在: $photoPath" }   // 校验文件存在
        val imageData = photoFile.readBytes()                         // 读取图片数据
        val fileName = media.generatePhotoFileName()                  // 生成文件名
        val uri = media.savePhoto(imageData, fileName).getOrThrow()   // 保存到MediaStore
        photoFile.delete()                                            // 清理临时文件
        uri                                                           // 返回保存的Uri
    }

    // ==================== 录像操作 ====================

    /**
     * 开始录像
     * @return 操作结果
     */
    suspend fun startRecording(): Result<Unit> = camera.startRecording()

    /**
     * 停止录像并保存到相册
     * @return 视频Uri的Result
     */
    suspend fun stopRecording(): Result<Uri> = runCatching {
        val videoPath = camera.stopRecording().getOrThrow()           // 停止录像获取路径
        val fileName = media.generateVideoFileName()                  // 生成文件名
        media.saveVideo(videoPath, fileName).getOrThrow()             // 保存到MediaStore
    }

    /**
     * 获取录像状态Flow
     */
    fun isRecording(): Flow<Boolean> = camera.isRecording()

    // ==================== 摄像头操作 ====================

    /**
     * 切换到指定摄像头
     * @param lens 目标镜头
     */
    suspend fun switchTo(lens: CameraLens): Result<Unit> = camera.switchCamera(lens)

    /**
     * 前后摄像头切换
     * @return 操作结果
     */
    suspend fun toggleCamera(): Result<Unit> = runCatching {
        val current = camera.getCurrentLens().first()                 // 获取当前镜头
        val target = if (current == CameraLens.BACK) CameraLens.FRONT else CameraLens.BACK
        camera.switchCamera(target).getOrThrow()                      // 切换到目标镜头
    }

    /**
     * 获取当前镜头Flow
     */
    fun currentLens(): Flow<CameraLens> = camera.getCurrentLens()

    // ==================== 滤镜操作 ====================

    /**
     * 应用滤镜
     * @param type 滤镜类型
     */
    suspend fun applyFilter(type: FilterType): Result<Unit> = runCatching {
        filter.setCurrentFilter(type)                                 // 更新滤镜状态
        camera.applyFilter(type).getOrThrow()                         // 应用到相机预览
    }

    /**
     * 获取当前滤镜Flow
     */
    fun currentFilter(): Flow<FilterType> = filter.getCurrentFilter()

    /**
     * 获取所有可用滤镜
     */
    fun availableFilters(): List<FilterType> = filter.getAvailableFilters()

    /**
     * 获取指定分组的滤镜列表
     */
    fun filtersByGroup(group: com.qihao.filtercamera.domain.model.FilterGroup): List<FilterType> =
        FilterType.getFiltersByGroup(group)

    /**
     * 获取滤镜帧流（用于实时预览显示）
     *
     * 当应用滤镜时返回处理后的Bitmap，否则返回null
     */
    fun filteredFrame(): Flow<Bitmap?> = camera.getFilteredFrame()

    /**
     * 获取原始预览帧流（用于生成滤镜缩略图）
     *
     * 此帧不受当前滤镜影响，可用于生成各种滤镜的预览缩略图
     */
    fun rawPreviewFrame(): Flow<Bitmap?> = camera.getRawPreviewFrame()

    /**
     * 获取滤镜预览缩略图
     *
     * 使用GPUImage为指定滤镜生成预览缩略图
     *
     * @param filterType 滤镜类型
     * @param sourceBitmap 源图片（建议使用60x60小图）
     * @return 应用滤镜后的缩略图
     */
    suspend fun getFilterThumbnail(filterType: FilterType, sourceBitmap: Bitmap): Bitmap? =
        filter.getFilterThumbnail(filterType, sourceBitmap)

    /**
     * 设置滤镜强度
     *
     * 控制滤镜效果的强度（0.0~1.0）
     * 0.0 = 原图（无滤镜效果）
     * 1.0 = 全强度滤镜效果
     *
     * @param intensity 滤镜强度（0.0~1.0）
     */
    suspend fun setFilterIntensity(intensity: Float) {
        filter.setFilterIntensity(intensity.coerceIn(0f, 1f))
    }

    // ==================== 美颜操作 ====================

    /**
     * 设置美颜等级
     * @param level 美颜等级枚举
     */
    suspend fun setBeauty(level: BeautyLevel): Result<Unit> =
        camera.setBeautyLevel(level.intensity)

    /**
     * 设置美颜强度（直接数值）
     * @param intensity 强度值（0.0-1.0）
     */
    suspend fun setBeautyIntensity(intensity: Float): Result<Unit> =
        camera.setBeautyLevel(intensity.coerceIn(0f, 1f))

    // ==================== 变焦与对焦控制 ====================

    /**
     * 设置变焦倍数
     * @param zoom 变焦倍数
     */
    suspend fun setZoom(zoom: Float): Result<Unit> =
        camera.setZoom(zoom)

    /**
     * 触发自动对焦
     * 在预览中心点进行自动对焦
     */
    suspend fun autoFocus(): Result<Unit> =
        camera.autoFocus()

    /**
     * 触摸对焦 - 在指定坐标点进行对焦和测光
     *
     * 用户点击预览区域时调用此方法，实现点触对焦功能
     * 同时在该点进行测光，优化曝光效果
     *
     * @param x 归一化X坐标 (0.0~1.0，0为左边缘，1为右边缘)
     * @param y 归一化Y坐标 (0.0~1.0，0为上边缘，1为下边缘)
     * @return 操作结果
     */
    suspend fun focusAtPoint(x: Float, y: Float): Result<Unit> =
        camera.focusAtPoint(x, y)

    /**
     * 获取变焦范围
     * @return 设备支持的变焦范围Flow
     */
    fun getZoomRange(): Flow<com.qihao.filtercamera.domain.repository.ZoomRange> =
        camera.getZoomRange()

    // ==================== 高级设置控制 ====================

    /**
     * 设置HDR模式
     */
    suspend fun setHdrMode(mode: com.qihao.filtercamera.domain.model.HdrMode): Result<Unit> =
        camera.setHdrMode(mode)

    /**
     * 设置夜景模式
     *
     * 控制夜景拍摄功能：
     * - ON: 强制开启夜景模式
     * - OFF: 关闭夜景模式
     * - AUTO: 根据环境光自动判断是否启用
     *
     * @param mode 夜景模式
     * @return 操作结果
     */
    suspend fun setNightMode(mode: com.qihao.filtercamera.domain.model.NightMode): Result<Unit> =
        camera.setNightMode(mode)

    /**
     * 获取夜景处理进度流
     *
     * 返回当前夜景处理的阶段名称和进度（0.0~1.0）
     * null表示当前没有正在进行的夜景处理
     *
     * @return Pair<处理阶段名称, 进度>
     */
    fun getNightProcessingProgress(): Flow<Pair<String, Float>?> =
        camera.getNightProcessingProgress()

    /**
     * 设置微距模式
     */
    suspend fun setMacroMode(mode: com.qihao.filtercamera.domain.model.MacroMode): Result<Unit> =
        camera.setMacroMode(mode)

    /**
     * 设置画幅比例
     */
    suspend fun setAspectRatio(ratio: com.qihao.filtercamera.domain.model.AspectRatio): Result<Unit> =
        camera.setAspectRatio(ratio)

    // ==================== 专业模式参数控制 ====================

    /**
     * 设置曝光补偿
     * @param evIndex 曝光补偿索引（设备范围内的索引值）
     * @return 操作结果
     */
    suspend fun setExposureCompensation(evIndex: Int): Result<Unit> =
        camera.setExposureCompensation(evIndex)

    /**
     * 设置ISO感光度
     * @param iso ISO值，null表示自动ISO
     * @return 操作结果
     */
    suspend fun setIso(iso: Int?): Result<Unit> =
        camera.setIso(iso)

    /**
     * 设置白平衡模式
     * @param mode 白平衡模式枚举
     * @return 操作结果
     */
    suspend fun setWhiteBalance(mode: com.qihao.filtercamera.domain.model.WhiteBalanceMode): Result<Unit> =
        camera.setWhiteBalance(mode)

    /**
     * 设置对焦模式
     * @param mode 对焦模式枚举
     * @return 操作结果
     */
    suspend fun setFocusMode(mode: com.qihao.filtercamera.domain.model.FocusMode): Result<Unit> =
        camera.setFocusMode(mode)

    /**
     * 设置手动对焦距离
     * @param distance 对焦距离（0.0=最近，1.0=无穷远）
     * @return 操作结果
     */
    suspend fun setFocusDistance(distance: Float): Result<Unit> =
        camera.setFocusDistance(distance.coerceIn(0f, 1f))

    /**
     * 获取曝光补偿范围
     * @return Triple(最小索引, 最大索引, 步进值)
     */
    fun getExposureCompensationRange(): Triple<Int, Int, Float> =
        camera.getExposureCompensationRange()

    /**
     * 设置快门速度（曝光时间）
     *
     * 使用Camera2 SENSOR_EXPOSURE_TIME实现真实快门速度控制
     * 设置手动快门需要禁用自动曝光，建议配合ISO调整以获得正确曝光
     *
     * @param speed 快门速度（秒），null表示恢复自动曝光
     *              例如：1/4000s = 0.00025f, 1/30s = 0.0333f, 1s = 1.0f
     * @return 操作结果
     */
    suspend fun setShutterSpeed(speed: Float?): Result<Unit> =
        camera.setShutterSpeed(speed)

    /**
     * 获取设备支持的快门速度范围
     *
     * @return Pair(最小曝光时间纳秒, 最大曝光时间纳秒)，设备不支持时返回null
     */
    fun getExposureTimeRange(): Pair<Long, Long>? =
        camera.getExposureTimeRange()

    // ==================== 闪光灯控制 ====================

    /**
     * 设置闪光灯模式
     *
     * @param mode 闪光灯模式枚举
     * @return 操作结果
     */
    suspend fun setFlashMode(mode: com.qihao.filtercamera.domain.model.FlashMode): Result<Unit> =
        camera.setFlashMode(mode)

    /**
     * 获取当前闪光灯模式
     *
     * @return 当前闪光灯模式
     */
    fun getCurrentFlashMode(): com.qihao.filtercamera.domain.model.FlashMode =
        camera.getCurrentFlashMode()

    /**
     * 检查设备是否支持闪光灯
     *
     * @return true表示支持
     */
    fun hasFlashUnit(): Boolean =
        camera.hasFlashUnit()

    /**
     * 切换到下一个闪光灯模式
     *
     * 循环切换：OFF -> ON -> AUTO -> OFF
     *
     * @return 操作结果
     */
    suspend fun toggleFlashMode(): Result<com.qihao.filtercamera.domain.model.FlashMode> = runCatching {
        val current = camera.getCurrentFlashMode()
        val next = com.qihao.filtercamera.domain.model.FlashMode.next(current)
        camera.setFlashMode(next).getOrThrow()
        next
    }

    // ==================== 资源管理 ====================

    /**
     * 释放相机资源
     */
    suspend fun release() {
        camera.release()                                              // 释放CameraX资源
        filter.releaseFilterEngine()                                  // 释放滤镜引擎
    }

    // ==================== 延时摄影控制 ====================

    /**
     * 开始延时摄影录制
     *
     * @param settings 延时摄影配置
     * @return 操作结果
     */
    suspend fun startTimelapse(settings: com.qihao.filtercamera.domain.model.TimelapseSettings): Result<Unit> =
        camera.startTimelapse(settings)

    /**
     * 停止延时摄影并编码输出视频
     *
     * @return 输出视频文件路径Result
     */
    suspend fun stopTimelapse(): Result<String> =
        camera.stopTimelapse()

    /**
     * 取消延时摄影（不生成视频）
     *
     * @return 操作结果
     */
    suspend fun cancelTimelapse(): Result<Unit> =
        camera.cancelTimelapse()

    /**
     * 暂停延时摄影
     *
     * @return 操作结果
     */
    suspend fun pauseTimelapse(): Result<Unit> =
        camera.pauseTimelapse()

    /**
     * 恢复延时摄影
     *
     * @return 操作结果
     */
    suspend fun resumeTimelapse(): Result<Unit> =
        camera.resumeTimelapse()

    /**
     * 获取延时摄影进度流
     *
     * @return Triple(已捕获帧数, 已用时间毫秒, 编码进度0.0~1.0)
     */
    fun getTimelapseProgress(): Flow<Triple<Int, Long, Float>> =
        camera.getTimelapseProgress()

    /**
     * 检查是否处于延时摄影录制状态
     *
     * @return true表示正在录制
     */
    fun isTimelapseRecording(): Boolean =
        camera.isTimelapseRecording()

    // ==================== 人像虚化控制 ====================

    /**
     * 设置人像虚化等级
     *
     * @param level 虚化等级
     * @return 操作结果
     */
    suspend fun setPortraitBlurLevel(level: com.qihao.filtercamera.domain.model.PortraitBlurLevel): Result<Unit> =
        camera.setPortraitBlurLevel(level)

    /**
     * 获取当前人像虚化等级
     *
     * @return 当前虚化等级
     */
    fun getPortraitBlurLevel(): com.qihao.filtercamera.domain.model.PortraitBlurLevel =
        camera.getPortraitBlurLevel()

    // ==================== 相册操作 ====================

    /**
     * 获取最新的相册缩略图
     *
     * 从MediaStore查询最近的照片，生成缩略图供左下角预览显示
     * 如果相册为空则返回null
     *
     * @param width 缩略图宽度（默认100px）
     * @param height 缩略图高度（默认100px）
     * @return 缩略图Bitmap，相册为空时返回null
     */
    suspend fun getLatestGalleryThumbnail(width: Int = 100, height: Int = 100): Bitmap? {
        val recentMedia = media.getRecentMedia(limit = 1)       // 只查询最近1张照片
        if (recentMedia.isEmpty()) return null                   // 相册为空
        val latestUri = recentMedia.first().uri                  // 获取最新照片Uri
        return media.loadThumbnail(latestUri, width, height)     // 加载缩略图
    }
}
