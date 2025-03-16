/**
 * FilterRepositoryImpl.kt - 滤镜仓库实现
 *
 * 管理滤镜状态和滤镜应用逻辑
 * 使用GPUImage库实现GPU滤镜渲染
 *
 * 技术实现：
 * - GPUImage库进行GPU滤镜渲染
 * - 支持72种滤镜类型
 * - 支持实时预览和图片处理
 * - 提供同步和异步两种滤镜应用方法
 * - 支持滤镜强度控制（0.0~1.0）
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.Log
import com.qihao.filter.factory.GPUImageFilterFactory
import com.qihao.filter.watermark.WatermarkRenderer
import com.qihao.filtercamera.domain.model.FilterType
import com.qihao.filtercamera.domain.repository.IFilterRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 滤镜仓库实现类
 *
 * 使用GPUImage库实现滤镜渲染
 *
 * @param context 应用上下文
 */
@Singleton
class FilterRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : IFilterRepository {

    companion object {
        private const val TAG = "FilterRepositoryImpl"  // 日志标签
    }

    // 当前滤镜状态流
    private val _currentFilter = MutableStateFlow(FilterType.NONE)

    // 当前滤镜强度（0.0~1.0，默认1.0全强度）
    private var _currentIntensity: Float = 1.0f

    // GPUImage实例（用于图片处理）
    private var gpuImage: GPUImage? = null

    // 当前GPUImageFilter实例
    private var currentGpuFilter: GPUImageFilter? = null

    // 滤镜引擎是否已初始化
    private var isEngineInitialized = false

    // 渲染尺寸
    private var renderWidth = 0
    private var renderHeight = 0

    /**
     * 获取所有可用滤镜列表
     */
    override fun getAvailableFilters(): List<FilterType> {
        Log.d(TAG, "getAvailableFilters: 获取相机滤镜列表")
        return FilterType.getCameraFilters()
    }

    /**
     * 获取当前选中的滤镜
     */
    override fun getCurrentFilter(): Flow<FilterType> = _currentFilter

    /**
     * 设置当前滤镜
     *
     * 同时更新GPUImageFilter实例
     */
    override suspend fun setCurrentFilter(filterType: FilterType) {
        Log.d(TAG, "setCurrentFilter: 设置滤镜 $filterType")
        _currentFilter.value = filterType

        // 创建对应的GPUImageFilter
        currentGpuFilter = GPUImageFilterFactory.createFilter(filterType.name, context)

        // 如果GPUImage已初始化，更新滤镜
        gpuImage?.setFilter(currentGpuFilter)
        Log.d(TAG, "setCurrentFilter: GPUImageFilter已更新")
    }

    /**
     * 设置滤镜强度
     *
     * 控制滤镜效果的强度（0.0~1.0）
     * 0.0 = 完全原图
     * 1.0 = 完全滤镜效果
     *
     * @param intensity 滤镜强度（0.0~1.0）
     */
    override suspend fun setFilterIntensity(intensity: Float) {
        val clampedIntensity = intensity.coerceIn(0f, 1f)
        Log.d(TAG, "setFilterIntensity: 设置强度 $clampedIntensity")
        _currentIntensity = clampedIntensity
    }

    /**
     * 获取当前滤镜强度
     *
     * @return 当前强度值（0.0~1.0）
     */
    override fun getCurrentIntensity(): Float = _currentIntensity

    /**
     * 获取滤镜预览缩略图
     *
     * 使用GPUImage生成滤镜效果的缩略图
     *
     * @param filterType 滤镜类型
     * @param sourceBitmap 源图片
     * @return 应用滤镜后的缩略图
     */
    override suspend fun getFilterThumbnail(
        filterType: FilterType,
        sourceBitmap: Bitmap
    ): Bitmap? = withContext(Dispatchers.Default) {
        Log.d(TAG, "getFilterThumbnail: 生成滤镜缩略图 filterType=$filterType")
        applyGpuFilterCore(filterType, sourceBitmap).also { result ->
            if (result != null) {
                Log.d(TAG, "getFilterThumbnail: 缩略图生成成功")
            } else {
                Log.e(TAG, "getFilterThumbnail: 缩略图生成失败")
            }
        }
    }

    /**
     * 应用滤镜到图像
     *
     * 使用GPUImage将滤镜效果应用到Bitmap
     *
     * @param filterType 滤镜类型
     * @param sourceBitmap 源图片
     * @return 应用滤镜后的图片
     */
    override suspend fun applyFilterToBitmap(
        filterType: FilterType,
        sourceBitmap: Bitmap
    ): Bitmap? = withContext(Dispatchers.Default) {
        Log.d(TAG, "applyFilterToBitmap: 应用滤镜 filterType=$filterType")
        applyFilterInternal(filterType, sourceBitmap)
    }

    /**
     * 滤镜应用的内部实现（统一逻辑）
     *
     * 处理NONE滤镜、水印类型和GPU滤镜
     * 支持滤镜强度控制（0.0~1.0）
     *
     * @param filterType 滤镜类型
     * @param sourceBitmap 源图片
     * @return 应用滤镜后的图片
     */
    private fun applyFilterInternal(filterType: FilterType, sourceBitmap: Bitmap): Bitmap? {
        // 如果是原图滤镜，直接返回原图
        if (filterType == FilterType.NONE) {
            Log.d(TAG, "applyFilterInternal: 原图滤镜，直接返回")
            return sourceBitmap
        }

        // 如果强度为0，直接返回原图
        if (_currentIntensity <= 0f) {
            Log.d(TAG, "applyFilterInternal: 强度为0，返回原图")
            return sourceBitmap
        }

        // 如果是水印类型，使用Canvas绘制水印（水印不支持强度调节）
        if (FilterType.isWatermarkType(filterType)) {
            Log.d(TAG, "applyFilterInternal: 水印滤镜，应用Canvas水印")
            return applyWatermarkToBitmap(filterType, sourceBitmap)
        }

        // 应用GPU滤镜
        val filteredBitmap = applyGpuFilterCore(filterType, sourceBitmap) ?: return sourceBitmap

        // 如果强度为1，直接返回滤镜图
        if (_currentIntensity >= 1f) {
            Log.d(TAG, "applyFilterInternal: 强度为1，返回滤镜图")
            return filteredBitmap
        }

        // 强度混合：将原图和滤镜图按比例混合
        Log.d(TAG, "applyFilterInternal: 强度混合 intensity=$_currentIntensity")
        return blendBitmaps(sourceBitmap, filteredBitmap, _currentIntensity)
    }

    /**
     * 混合两张Bitmap
     *
     * 使用Canvas和Paint的alpha混合实现
     * result = original * (1 - intensity) + filtered * intensity
     *
     * @param original 原图
     * @param filtered 滤镜处理后的图
     * @param intensity 滤镜强度（0.0~1.0）
     * @return 混合后的图片
     */
    private fun blendBitmaps(original: Bitmap, filtered: Bitmap, intensity: Float): Bitmap {
        // 创建结果Bitmap（使用原图尺寸）
        val result = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // 先绘制原图（底层）
        canvas.drawBitmap(original, 0f, 0f, null)

        // 再绘制滤镜图（带透明度覆盖）
        val paint = Paint().apply {
            alpha = (intensity * 255).toInt()                          // 设置滤镜层透明度
        }
        canvas.drawBitmap(filtered, 0f, 0f, paint)

        Log.d(TAG, "blendBitmaps: 混合完成 intensity=$intensity alpha=${paint.alpha}")
        return result
    }

    /**
     * 带强度参数的滤镜应用（用于实时预览）
     *
     * 供CameraRepository调用，支持动态强度调整
     *
     * @param filterType 滤镜类型
     * @param sourceBitmap 源图片
     * @param intensity 滤镜强度（0.0~1.0）
     * @return 应用滤镜后的图片
     */
    fun applyFilterWithIntensity(filterType: FilterType, sourceBitmap: Bitmap, intensity: Float): Bitmap? {
        val originalIntensity = _currentIntensity
        _currentIntensity = intensity.coerceIn(0f, 1f)
        val result = applyFilterInternal(filterType, sourceBitmap)
        _currentIntensity = originalIntensity                          // 恢复原强度
        return result
    }

    /**
     * GPU滤镜应用核心方法
     *
     * 创建临时GPUImage实例应用滤镜，避免线程竞争
     *
     * @param filterType 滤镜类型
     * @param sourceBitmap 源图片
     * @return 应用滤镜后的图片，失败返回null
     */
    private fun applyGpuFilterCore(filterType: FilterType, sourceBitmap: Bitmap): Bitmap? {
        return try {
            val tempGpuImage = GPUImage(context)                              // 创建临时实例避免线程竞争
            tempGpuImage.setImage(sourceBitmap)
            val filter = GPUImageFilterFactory.createFilter(filterType.name, context)
            tempGpuImage.setFilter(filter)
            val result = tempGpuImage.bitmapWithFilterApplied
            Log.d(TAG, "applyGpuFilterCore: 滤镜应用成功 size=${result?.width}x${result?.height}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "applyGpuFilterCore: 滤镜应用失败", e)
            null
        }
    }

    /**
     * 初始化滤镜引擎
     *
     * 创建GPUImage实例，准备滤镜渲染
     */
    override suspend fun initFilterEngine(width: Int, height: Int) {
        withContext(Dispatchers.Main) {
            Log.d(TAG, "initFilterEngine: 初始化滤镜引擎 ${width}x${height}")
            renderWidth = width
            renderHeight = height

            // 初始化GPUImage
            if (gpuImage == null) {
                gpuImage = GPUImage(context)
            }

            // 设置默认滤镜
            currentGpuFilter = GPUImageFilterFactory.createFilter(FilterType.NONE.name, context)
            gpuImage?.setFilter(currentGpuFilter)

            isEngineInitialized = true
            Log.d(TAG, "initFilterEngine: 滤镜引擎初始化完成")
        }
    }

    /**
     * 释放滤镜资源
     */
    override suspend fun releaseFilterEngine() {
        withContext(Dispatchers.Main) {
            Log.d(TAG, "releaseFilterEngine: 释放滤镜引擎")

            // 清理GPUImage
            gpuImage?.deleteImage()
            gpuImage = null
            currentGpuFilter = null

            isEngineInitialized = false
            Log.d(TAG, "releaseFilterEngine: 滤镜引擎已释放")
        }
    }

    /**
     * 获取当前GPUImageFilter实例
     *
     * 供外部使用（如GPUImageView）
     */
    fun getCurrentGpuFilter(): GPUImageFilter? = currentGpuFilter

    /**
     * 获取GPUImage实例
     *
     * 供外部使用
     */
    fun getGpuImage(): GPUImage? = gpuImage

    /**
     * 同步应用滤镜到图像（用于实时预览，避免协程开销）
     *
     * 直接调用applyFilterInternal，无协程包装
     *
     * @param filterType 滤镜类型
     * @param sourceBitmap 源图片
     * @return 应用滤镜后的图片
     */
    override fun applyFilterToBitmapSync(filterType: FilterType, sourceBitmap: Bitmap): Bitmap? {
        Log.d(TAG, "applyFilterToBitmapSync: 同步应用滤镜 filterType=$filterType")
        return applyFilterInternal(filterType, sourceBitmap)
    }

    // ==================== 水印相关 ====================

    // 当前水印数据
    private var currentWatermarkData = WatermarkRenderer.WatermarkData()

    /**
     * 设置自定义水印文字
     *
     * @param text 自定义文字
     */
    fun setCustomWatermarkText(text: String) {
        currentWatermarkData = currentWatermarkData.copy(customText = text)
        Log.d(TAG, "setCustomWatermarkText: 自定义文字=$text")
    }

    /**
     * 应用水印到Bitmap
     *
     * 将FilterType转换为WatermarkType并调用WatermarkRenderer
     *
     * @param filterType 滤镜类型（必须是水印类型）
     * @param sourceBitmap 源图片
     * @return 带水印的图片
     */
    private fun applyWatermarkToBitmap(filterType: FilterType, sourceBitmap: Bitmap): Bitmap {
        // 更新时间戳为当前时间
        val data = currentWatermarkData.copy(timestamp = System.currentTimeMillis())

        // 转换FilterType到WatermarkType
        val watermarkType = filterTypeToWatermarkType(filterType)

        Log.d(TAG, "applyWatermarkToBitmap: 应用水印 type=$watermarkType")

        return WatermarkRenderer.applyWatermark(sourceBitmap, watermarkType, data)
    }

    /**
     * FilterType到WatermarkType的映射
     *
     * @param filterType 滤镜类型
     * @return 对应的水印类型
     */
    private fun filterTypeToWatermarkType(filterType: FilterType): WatermarkRenderer.WatermarkType {
        return when (filterType) {
            FilterType.WATERMARK_TIMESTAMP -> WatermarkRenderer.WatermarkType.TIMESTAMP
            FilterType.WATERMARK_DATE -> WatermarkRenderer.WatermarkType.DATE
            FilterType.WATERMARK_DEVICE -> WatermarkRenderer.WatermarkType.DEVICE
            FilterType.WATERMARK_CUSTOM -> WatermarkRenderer.WatermarkType.CUSTOM
            else -> WatermarkRenderer.WatermarkType.TIMESTAMP                 // 默认时间戳
        }
    }
}
