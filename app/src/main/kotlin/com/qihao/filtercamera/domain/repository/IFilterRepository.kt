/**
 * IFilterRepository.kt - 滤镜仓库接口
 *
 * 定义滤镜操作的抽象接口
 * 包含滤镜加载、应用和管理功能
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.domain.repository

import android.graphics.Bitmap
import com.qihao.filtercamera.domain.model.FilterType
import kotlinx.coroutines.flow.Flow

/**
 * 滤镜仓库接口
 * 管理滤镜资源和效果应用
 */
interface IFilterRepository {

    /**
     * 获取所有可用滤镜列表
     * @return 滤镜类型列表
     */
    fun getAvailableFilters(): List<FilterType>

    /**
     * 获取当前选中的滤镜
     * @return 当前滤镜类型Flow
     */
    fun getCurrentFilter(): Flow<FilterType>

    /**
     * 设置当前滤镜
     * @param filterType 滤镜类型
     */
    suspend fun setCurrentFilter(filterType: FilterType)

    /**
     * 设置滤镜强度
     *
     * 控制滤镜效果的强度（0.0~1.0）
     * 实现原理：将原图与滤镜处理后的图混合
     * intensity=0.0 表示完全原图，intensity=1.0 表示完全滤镜效果
     *
     * @param intensity 滤镜强度（0.0~1.0）
     */
    suspend fun setFilterIntensity(intensity: Float)

    /**
     * 获取当前滤镜强度
     * @return 当前强度值（0.0~1.0）
     */
    fun getCurrentIntensity(): Float

    /**
     * 获取滤镜预览缩略图
     * @param filterType 滤镜类型
     * @param sourceBitmap 源图像
     * @return 应用滤镜后的缩略图
     */
    suspend fun getFilterThumbnail(filterType: FilterType, sourceBitmap: Bitmap): Bitmap?

    /**
     * 应用滤镜到图像
     * @param filterType 滤镜类型
     * @param sourceBitmap 源图像
     * @return 应用滤镜后的图像
     */
    suspend fun applyFilterToBitmap(filterType: FilterType, sourceBitmap: Bitmap): Bitmap?

    /**
     * 同步应用滤镜到图像（非挂起版本）
     *
     * 用于需要在非协程上下文中调用的场景
     * 注意：此方法会在调用线程同步执行，可能会阻塞
     *
     * @param filterType 滤镜类型
     * @param sourceBitmap 源图像
     * @return 应用滤镜后的图像
     */
    fun applyFilterToBitmapSync(filterType: FilterType, sourceBitmap: Bitmap): Bitmap?

    /**
     * 初始化滤镜引擎
     * @param width 渲染宽度
     * @param height 渲染高度
     */
    suspend fun initFilterEngine(width: Int, height: Int)

    /**
     * 释放滤镜资源
     */
    suspend fun releaseFilterEngine()
}
