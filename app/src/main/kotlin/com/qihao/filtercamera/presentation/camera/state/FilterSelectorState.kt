/**
 * FilterSelectorState.kt - 滤镜选择器状态管理
 *
 * 独立管理滤镜选择UI的状态和逻辑
 * 从CameraViewModel抽取，降低ViewModel复杂度
 *
 * 包含功能：
 * - 滤镜分组切换
 * - 滤镜选择
 * - 缩略图生成和缓存
 * - 选择器面板显示状态
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.presentation.camera.state

import android.graphics.Bitmap
import android.util.Log
import com.qihao.filtercamera.domain.model.FilterGroup
import com.qihao.filtercamera.domain.model.FilterType
import com.qihao.filtercamera.domain.usecase.CameraUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 滤镜选择器UI状态
 *
 * @param selectedGroup 当前选中的滤镜分组
 * @param selectedFilter 当前选中的滤镜
 * @param thumbnails 滤镜缩略图缓存
 * @param isPanelVisible 选择器面板是否可见
 * @param isGeneratingThumbnails 是否正在生成缩略图
 */
data class FilterSelectorUiState(
    val selectedGroup: FilterGroup = FilterGroup.ORIGINAL,
    val selectedFilter: FilterType = FilterType.NONE,
    val thumbnails: Map<FilterType, Bitmap?> = emptyMap(),
    val isPanelVisible: Boolean = false,
    val isGeneratingThumbnails: Boolean = false
)

/**
 * 滤镜选择器状态管理器
 *
 * @param useCase 相机用例
 * @param scope 协程作用域
 * @param onFilterChanged 滤镜变化回调
 */
class FilterSelectorStateHolder(
    private val useCase: CameraUseCase,
    private val scope: CoroutineScope,
    private val onFilterChanged: (FilterType) -> Unit = {}
) {
    companion object {
        private const val TAG = "FilterSelectorState"
        private const val THUMBNAIL_SIZE = 60                        // 缩略图尺寸（像素）
    }

    // UI状态
    private val _state = MutableStateFlow(FilterSelectorUiState())
    val state: StateFlow<FilterSelectorUiState> = _state.asStateFlow()

    // 快捷访问
    val selectedGroup: FilterGroup get() = _state.value.selectedGroup
    val selectedFilter: FilterType get() = _state.value.selectedFilter
    val isPanelVisible: Boolean get() = _state.value.isPanelVisible
    val thumbnails: Map<FilterType, Bitmap?> get() = _state.value.thumbnails

    // 可用滤镜分组（相机页面不显示调整分组）- 修复：添加异常保护
    val availableGroups: List<FilterGroup> = runCatching {
        FilterGroup.getCameraGroups()
    }.getOrElse {
        Log.e(TAG, "获取滤镜分组失败", it)
        emptyList()
    }

    // 可用滤镜列表 - 修复：添加异常保护
    val availableFilters: List<FilterType> = runCatching {
        useCase.availableFilters()
    }.getOrElse {
        Log.e(TAG, "获取可用滤镜列表失败", it)
        emptyList()
    }

    // 当前预览帧（用于生成缩略图）
    private var currentPreviewFrame: Bitmap? = null

    init {
        Log.d(TAG, "init: 滤镜选择器状态管理器初始化")
        observeFilterChanges()
    }

    /**
     * 观察滤镜状态变化
     */
    private fun observeFilterChanges() = scope.launch {
        useCase.currentFilter().collect { filterType ->
            Log.d(TAG, "observeFilterChanges: 滤镜变化 filterType=$filterType")
            _state.update { it.copy(selectedFilter = filterType) }
            onFilterChanged(filterType)
        }
    }

    // ==================== 面板控制 ====================

    /**
     * 切换面板可见性
     */
    fun togglePanel() {
        val isVisible = !_state.value.isPanelVisible
        Log.d(TAG, "togglePanel: isVisible=$isVisible")
        _state.update { it.copy(isPanelVisible = isVisible) }
    }

    /**
     * 显示面板
     */
    fun showPanel() {
        Log.d(TAG, "showPanel")
        _state.update { it.copy(isPanelVisible = true) }
    }

    /**
     * 隐藏面板
     */
    fun hidePanel() {
        Log.d(TAG, "hidePanel")
        _state.update { it.copy(isPanelVisible = false) }
    }

    // ==================== 滤镜选择 ====================

    /**
     * 选择滤镜
     *
     * @param filter 目标滤镜
     */
    fun selectFilter(filter: FilterType) {
        Log.d(TAG, "selectFilter: filter=${filter.displayName}")
        scope.launch {
            useCase.applyFilter(filter)
        }
    }

    /**
     * 选择滤镜分组
     *
     * @param group 目标分组
     */
    fun selectGroup(group: FilterGroup) {
        Log.d(TAG, "selectGroup: group=${group.displayName}")
        _state.update { it.copy(selectedGroup = group) }

        // 切换分组后重新生成缩略图
        currentPreviewFrame?.let {
            generateThumbnails(it)
        }
    }

    /**
     * 获取指定分组的滤镜列表
     *
     * @param group 滤镜分组
     * @return 滤镜列表
     */
    fun getFiltersByGroup(group: FilterGroup): List<FilterType> =
        useCase.filtersByGroup(group)

    /**
     * 获取当前选中分组的滤镜列表
     */
    fun getCurrentGroupFilters(): List<FilterType> =
        getFiltersByGroup(_state.value.selectedGroup)

    // ==================== 缩略图生成 ====================

    /**
     * 更新预览帧（用于生成缩略图）
     *
     * @param bitmap 预览帧
     */
    fun updatePreviewFrame(bitmap: Bitmap) {
        currentPreviewFrame = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)

        // 首次获取时自动生成缩略图
        if (_state.value.thumbnails.isEmpty()) {
            Log.d(TAG, "updatePreviewFrame: 首次获取预览帧，开始生成缩略图")
            generateThumbnails(bitmap)
        }
    }

    /**
     * 生成当前分组的滤镜缩略图
     */
    fun generateThumbnails() {
        currentPreviewFrame?.let {
            generateThumbnails(it)
        } ?: Log.w(TAG, "generateThumbnails: 无可用预览帧")
    }

    /**
     * 生成指定分组的滤镜缩略图
     *
     * @param sourceFrame 源帧
     */
    fun generateThumbnails(sourceFrame: Bitmap) {
        if (_state.value.isGeneratingThumbnails) {
            Log.w(TAG, "generateThumbnails: 正在生成中，跳过")
            return
        }

        _state.update { it.copy(isGeneratingThumbnails = true) }
        Log.d(TAG, "generateThumbnails: 开始生成 group=${_state.value.selectedGroup}")

        scope.launch {
            try {
                // 缩放源图像
                val scaledBitmap = Bitmap.createScaledBitmap(
                    sourceFrame,
                    THUMBNAIL_SIZE,
                    THUMBNAIL_SIZE,
                    true
                )

                // 获取当前分组的滤镜列表
                val filtersInGroup = getCurrentGroupFilters()

                val thumbnails = mutableMapOf<FilterType, Bitmap?>()
                filtersInGroup.forEach { filterType ->
                    thumbnails[filterType] = when {
                        filterType == FilterType.NONE -> {
                            // 原图直接拷贝
                            scaledBitmap.copy(scaledBitmap.config ?: Bitmap.Config.ARGB_8888, false)
                        }
                        FilterType.isWatermarkType(filterType) -> {
                            // 水印类型直接拷贝（水印在Canvas层绘制）
                            scaledBitmap.copy(scaledBitmap.config ?: Bitmap.Config.ARGB_8888, false)
                        }
                        else -> {
                            // 其他滤镜通过UseCase生成
                            useCase.getFilterThumbnail(filterType, scaledBitmap)
                        }
                    }
                }

                _state.update { it.copy(
                    thumbnails = it.thumbnails + thumbnails,
                    isGeneratingThumbnails = false
                ) }
                Log.d(TAG, "generateThumbnails: 完成 count=${thumbnails.size}")

            } catch (e: Exception) {
                Log.e(TAG, "generateThumbnails: 生成失败", e)
                _state.update { it.copy(isGeneratingThumbnails = false) }
            }
        }
    }

    /**
     * 清除缩略图缓存
     */
    fun clearThumbnails() {
        Log.d(TAG, "clearThumbnails: 清除缩略图缓存")
        _state.value.thumbnails.values.forEach { bitmap ->
            bitmap?.let {
                if (!it.isRecycled) it.recycle()
            }
        }
        _state.update { it.copy(thumbnails = emptyMap()) }
    }

    // ==================== 重置 ====================

    /**
     * 重置为默认状态
     */
    fun reset() {
        Log.d(TAG, "reset: 重置滤镜选择器")
        scope.launch {
            useCase.applyFilter(FilterType.NONE)
        }
        _state.update {
            it.copy(
                selectedGroup = FilterGroup.ORIGINAL,
                isPanelVisible = false
            )
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        Log.d(TAG, "release: 释放资源")
        clearThumbnails()
        currentPreviewFrame?.let {
            if (!it.isRecycled) it.recycle()
            currentPreviewFrame = null
        }
    }

    /**
     * 获取当前状态摘要（用于调试）
     */
    fun getStateSummary(): String {
        return buildString {
            append("滤镜: ${_state.value.selectedFilter.displayName}")
            append(" | 分组: ${_state.value.selectedGroup.displayName}")
            append(" | 缩略图: ${_state.value.thumbnails.size}")
            append(" | 面板: ${if (_state.value.isPanelVisible) "展开" else "收起"}")
        }
    }
}
