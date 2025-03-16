/**
 * GalleryViewModel.kt - 相册视图模型
 *
 * 管理相册页面的业务逻辑和UI状态
 * 支持分页加载、类型筛选、媒体删除
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.presentation.gallery

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qihao.filtercamera.domain.repository.IFavoritesRepository
import com.qihao.filtercamera.domain.repository.IMediaRepository
import com.qihao.filtercamera.domain.repository.MediaFile
import com.qihao.filtercamera.domain.repository.MediaType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 相册UI状态
 *
 * @param mediaFiles 媒体文件列表
 * @param filteredMediaFiles 过滤后的媒体文件列表（搜索结果）
 * @param selectedMediaType 当前选中的媒体类型
 * @param searchQuery 搜索关键词
 * @param isSearchActive 是否正在搜索
 * @param isLoading 是否正在加载
 * @param isLoadingMore 是否正在加载更多
 * @param hasMoreData 是否还有更多数据
 * @param totalCount 媒体总数
 * @param selectedMedia 当前选中查看的媒体（用于详情页）
 * @param isSelectionMode 是否处于多选模式
 * @param selectedUris 多选模式下选中的Uri集合
 * @param favoriteUris 收藏的媒体URI集合（字符串形式）
 * @param showFavoritesOnly 是否只显示收藏
 * @param error 错误信息
 */
data class GalleryUiState(
    val mediaFiles: List<MediaFile> = emptyList(),
    val filteredMediaFiles: List<MediaFile> = emptyList(),
    val selectedMediaType: MediaType = MediaType.ALL,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMoreData: Boolean = true,
    val totalCount: Int = 0,
    val selectedMedia: MediaFile? = null,
    val isSelectionMode: Boolean = false,
    val selectedUris: Set<Uri> = emptySet(),
    val favoriteUris: Set<String> = emptySet(),
    val showFavoritesOnly: Boolean = false,
    val error: String? = null
) {
    /**
     * 获取显示的媒体列表
     * 优先级：搜索 > 收藏过滤 > 全部
     */
    val displayMediaFiles: List<MediaFile>
        get() {
            val baseList = if (isSearchActive && searchQuery.isNotBlank()) {
                filteredMediaFiles                                            // 搜索结果
            } else {
                mediaFiles                                                    // 全部媒体
            }
            return if (showFavoritesOnly) {
                baseList.filter { it.uri.toString() in favoriteUris }         // 只显示收藏
            } else {
                baseList
            }
        }

    /**
     * 检查指定媒体是否已收藏
     */
    fun isFavorite(uri: Uri): Boolean = uri.toString() in favoriteUris
}

/**
 * 相册ViewModel
 *
 * @param mediaRepository 媒体仓库
 * @param favoritesRepository 收藏夹仓库
 */
@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val mediaRepository: IMediaRepository,
    private val favoritesRepository: IFavoritesRepository
) : ViewModel() {

    companion object {
        private const val TAG = "GalleryViewModel"      // 日志标签
        private const val PAGE_SIZE = 50                // 每页加载数量
    }

    // UI状态流
    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    // 当前页码
    private var currentPage = 0

    init {
        Log.d(TAG, "init: 初始化相册ViewModel")
        observeFavorites()                                                    // 监听收藏变化
        loadInitialData()
    }

    /**
     * 监听收藏夹变化
     */
    private fun observeFavorites() {
        viewModelScope.launch {
            favoritesRepository.getFavoriteUris().collect { favoriteUris ->
                Log.d(TAG, "observeFavorites: 收藏数量=${favoriteUris.size}")
                _uiState.update { it.copy(favoriteUris = favoriteUris) }
            }
        }
    }

    /**
     * 加载初始数据
     */
    private fun loadInitialData() {
        viewModelScope.launch {
            Log.d(TAG, "loadInitialData: 开始加载初始数据")
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                // 获取媒体总数
                val totalCount = mediaRepository.getMediaCount(_uiState.value.selectedMediaType)
                Log.d(TAG, "loadInitialData: 媒体总数=$totalCount")

                // 加载第一页数据
                val mediaFiles = mediaRepository.getMediaPaged(
                    type = _uiState.value.selectedMediaType,
                    offset = 0,
                    limit = PAGE_SIZE
                )
                Log.d(TAG, "loadInitialData: 加载了 ${mediaFiles.size} 个媒体文件")

                currentPage = 1
                _uiState.update {
                    it.copy(
                        mediaFiles = mediaFiles,
                        totalCount = totalCount,
                        hasMoreData = mediaFiles.size >= PAGE_SIZE && mediaFiles.size < totalCount,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadInitialData: 加载失败", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "加载失败: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * 加载更多数据（分页）
     */
    fun loadMore() {
        if (_uiState.value.isLoadingMore || !_uiState.value.hasMoreData) {
            Log.d(TAG, "loadMore: 跳过 - isLoadingMore=${_uiState.value.isLoadingMore}, hasMoreData=${_uiState.value.hasMoreData}")
            return
        }

        viewModelScope.launch {
            Log.d(TAG, "loadMore: 加载更多数据 page=$currentPage")
            _uiState.update { it.copy(isLoadingMore = true) }

            try {
                val offset = currentPage * PAGE_SIZE
                val mediaFiles = mediaRepository.getMediaPaged(
                    type = _uiState.value.selectedMediaType,
                    offset = offset,
                    limit = PAGE_SIZE
                )
                Log.d(TAG, "loadMore: 加载了 ${mediaFiles.size} 个媒体文件")

                currentPage++
                _uiState.update { state ->
                    state.copy(
                        mediaFiles = state.mediaFiles + mediaFiles,
                        hasMoreData = mediaFiles.size >= PAGE_SIZE,
                        isLoadingMore = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadMore: 加载失败", e)
                _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        error = "加载更多失败: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * 刷新数据
     */
    fun refresh() {
        Log.d(TAG, "refresh: 刷新数据")
        currentPage = 0
        loadInitialData()
    }

    /**
     * 切换媒体类型筛选
     *
     * @param type 媒体类型
     */
    fun setMediaType(type: MediaType) {
        if (type == _uiState.value.selectedMediaType) return

        Log.d(TAG, "setMediaType: 切换类型 $type")
        _uiState.update { it.copy(selectedMediaType = type) }
        currentPage = 0
        loadInitialData()
    }

    /**
     * 选择媒体查看详情
     *
     * @param media 媒体文件
     */
    fun selectMedia(media: MediaFile?) {
        Log.d(TAG, "selectMedia: 选择媒体 ${media?.name}")
        _uiState.update { it.copy(selectedMedia = media) }
    }

    /**
     * 进入/退出多选模式
     *
     * @param enabled 是否启用
     */
    fun setSelectionMode(enabled: Boolean) {
        Log.d(TAG, "setSelectionMode: enabled=$enabled")
        _uiState.update {
            it.copy(
                isSelectionMode = enabled,
                selectedUris = if (enabled) it.selectedUris else emptySet()
            )
        }
    }

    /**
     * 切换媒体选中状态
     *
     * @param uri 媒体Uri
     */
    fun toggleMediaSelection(uri: Uri) {
        Log.d(TAG, "toggleMediaSelection: uri=$uri")
        _uiState.update { state ->
            val newSelection = if (uri in state.selectedUris) {
                state.selectedUris - uri
            } else {
                state.selectedUris + uri
            }
            state.copy(selectedUris = newSelection)
        }
    }

    /**
     * 全选/取消全选
     */
    fun selectAll() {
        val allUris = _uiState.value.mediaFiles.map { it.uri }.toSet()
        val isAllSelected = _uiState.value.selectedUris.size == allUris.size

        Log.d(TAG, "selectAll: isAllSelected=$isAllSelected")
        _uiState.update {
            it.copy(selectedUris = if (isAllSelected) emptySet() else allUris)
        }
    }

    /**
     * 删除选中的媒体
     */
    fun deleteSelected() {
        val selectedUris = _uiState.value.selectedUris
        if (selectedUris.isEmpty()) return

        viewModelScope.launch {
            Log.d(TAG, "deleteSelected: 删除 ${selectedUris.size} 个媒体")
            _uiState.update { it.copy(isLoading = true) }

            var successCount = 0
            var failCount = 0

            for (uri in selectedUris) {
                val result = mediaRepository.deleteMedia(uri)
                if (result.isSuccess) {
                    successCount++
                } else {
                    failCount++
                    Log.e(TAG, "deleteSelected: 删除失败 uri=$uri")
                }
            }

            Log.d(TAG, "deleteSelected: 成功=$successCount, 失败=$failCount")

            // 退出多选模式并刷新
            _uiState.update {
                it.copy(
                    isSelectionMode = false,
                    selectedUris = emptySet(),
                    isLoading = false,
                    error = if (failCount > 0) "删除失败 $failCount 个文件" else null
                )
            }

            // 刷新列表
            refresh()
        }
    }

    /**
     * 删除单个媒体
     *
     * @param uri 媒体Uri
     */
    fun deleteMedia(uri: Uri) {
        viewModelScope.launch {
            Log.d(TAG, "deleteMedia: 删除媒体 uri=$uri")
            _uiState.update { it.copy(isLoading = true) }

            val result = mediaRepository.deleteMedia(uri)
            if (result.isSuccess) {
                Log.d(TAG, "deleteMedia: 删除成功")
                // 关闭详情页
                _uiState.update { it.copy(selectedMedia = null, isLoading = false) }
                // 刷新列表
                refresh()
            } else {
                Log.e(TAG, "deleteMedia: 删除失败", result.exceptionOrNull())
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "删除失败: ${result.exceptionOrNull()?.message}"
                    )
                }
            }
        }
    }

    /**
     * 清除错误信息
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ==================== 搜索功能 ====================

    /**
     * 切换搜索模式
     *
     * @param active 是否激活搜索
     */
    fun setSearchActive(active: Boolean) {
        Log.d(TAG, "setSearchActive: active=$active")
        _uiState.update {
            it.copy(
                isSearchActive = active,
                searchQuery = if (active) it.searchQuery else "",
                filteredMediaFiles = if (active) it.filteredMediaFiles else emptyList()
            )
        }
    }

    /**
     * 执行搜索
     *
     * @param query 搜索关键词
     */
    fun search(query: String) {
        Log.d(TAG, "search: query=$query")
        _uiState.update { state ->
            val filtered = if (query.isBlank()) {
                emptyList()
            } else {
                state.mediaFiles.filter { media ->
                    media.name.contains(query, ignoreCase = true)
                }
            }
            state.copy(
                searchQuery = query,
                filteredMediaFiles = filtered
            )
        }
    }

    /**
     * 清除搜索
     */
    fun clearSearch() {
        Log.d(TAG, "clearSearch: 清除搜索")
        _uiState.update {
            it.copy(
                searchQuery = "",
                filteredMediaFiles = emptyList(),
                isSearchActive = false
            )
        }
    }

    // ==================== 收藏夹功能 ====================

    /**
     * 切换媒体收藏状态
     *
     * @param uri 媒体Uri
     */
    fun toggleFavorite(uri: Uri) {
        viewModelScope.launch {
            val newState = favoritesRepository.toggleFavorite(uri)
            Log.d(TAG, "toggleFavorite: uri=$uri, newState=$newState")
        }
    }

    /**
     * 切换只显示收藏模式
     */
    fun toggleShowFavoritesOnly() {
        val newValue = !_uiState.value.showFavoritesOnly
        Log.d(TAG, "toggleShowFavoritesOnly: newValue=$newValue")
        _uiState.update { it.copy(showFavoritesOnly = newValue) }
    }

    /**
     * 设置只显示收藏模式
     *
     * @param show 是否只显示收藏
     */
    fun setShowFavoritesOnly(show: Boolean) {
        Log.d(TAG, "setShowFavoritesOnly: show=$show")
        _uiState.update { it.copy(showFavoritesOnly = show) }
    }

    /**
     * 获取收藏数量
     */
    fun getFavoritesCount(): Int = _uiState.value.favoriteUris.size

    /**
     * 格式化文件大小
     *
     * @param size 字节大小
     * @return 格式化后的字符串
     */
    fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "%.1f KB".format(size / 1024.0)
            size < 1024 * 1024 * 1024 -> "%.1f MB".format(size / (1024.0 * 1024.0))
            else -> "%.1f GB".format(size / (1024.0 * 1024.0 * 1024.0))
        }
    }

    /**
     * 格式化时长
     *
     * @param durationMs 毫秒
     * @return 格式化后的字符串 (mm:ss 或 hh:mm:ss)
     */
    fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }
}
