/**
 * GalleryScreen.kt - 相册页面
 *
 * 展示应用拍摄的照片和视频
 * 支持分页加载、类型筛选、多选删除、详情查看
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.presentation.gallery

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.qihao.filtercamera.domain.repository.MediaFile
import com.qihao.filtercamera.domain.repository.MediaType

/**
 * 相册页面入口
 *
 * @param onNavigateBack 返回回调
 * @param onNavigateToEdit 导航到编辑页面回调（传入图片Uri）
 * @param viewModel 视图模型
 */
@Composable
fun GalleryScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Uri) -> Unit = {},
    viewModel: GalleryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // 显示错误消息
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    // 详情页面
    if (uiState.selectedMedia != null) {
        MediaDetailScreen(
            media = uiState.selectedMedia!!,
            isFavorite = uiState.isFavorite(uiState.selectedMedia!!.uri),
            onBack = { viewModel.selectMedia(null) },
            onDelete = { viewModel.deleteMedia(uiState.selectedMedia!!.uri) },
            onEdit = {
                // 只有图片才能编辑
                if (!uiState.selectedMedia!!.isVideo) {
                    onNavigateToEdit(uiState.selectedMedia!!.uri)
                }
            },
            onToggleFavorite = { viewModel.toggleFavorite(uiState.selectedMedia!!.uri) },
            formatFileSize = viewModel::formatFileSize,
            formatDuration = viewModel::formatDuration
        )
    } else {
        GalleryContent(
            uiState = uiState,
            snackbarHostState = snackbarHostState,
            onNavigateBack = onNavigateBack,
            onRefresh = viewModel::refresh,
            onLoadMore = viewModel::loadMore,
            onMediaTypeChange = viewModel::setMediaType,
            onMediaClick = viewModel::selectMedia,
            onMediaLongClick = { uri ->
                if (!uiState.isSelectionMode) {
                    viewModel.setSelectionMode(true)
                }
                viewModel.toggleMediaSelection(uri)
            },
            onToggleSelection = viewModel::toggleMediaSelection,
            onSelectAll = viewModel::selectAll,
            onExitSelectionMode = { viewModel.setSelectionMode(false) },
            onDeleteSelected = viewModel::deleteSelected,
            formatDuration = viewModel::formatDuration,
            onSearchActiveChange = viewModel::setSearchActive,
            onSearchQueryChange = viewModel::search,
            onClearSearch = viewModel::clearSearch,
            onToggleFavoritesFilter = viewModel::toggleShowFavoritesOnly,
            onToggleFavorite = viewModel::toggleFavorite
        )
    }
}

/**
 * 相册主要内容
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryContent(
    uiState: GalleryUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onMediaTypeChange: (MediaType) -> Unit,
    onMediaClick: (MediaFile) -> Unit,
    onMediaLongClick: (Uri) -> Unit,
    onToggleSelection: (Uri) -> Unit,
    onSelectAll: () -> Unit,
    onExitSelectionMode: () -> Unit,
    onDeleteSelected: () -> Unit,
    formatDuration: (Long) -> String,
    onSearchActiveChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onToggleFavoritesFilter: () -> Unit,
    onToggleFavorite: (Uri) -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (uiState.isSelectionMode) {
                // 多选模式顶栏
                SelectionTopBar(
                    selectedCount = uiState.selectedUris.size,
                    onClose = onExitSelectionMode,
                    onSelectAll = onSelectAll,
                    onDelete = { showDeleteDialog = true }
                )
            } else if (uiState.isSearchActive) {
                // 搜索模式顶栏
                SearchTopBar(
                    query = uiState.searchQuery,
                    onQueryChange = onSearchQueryChange,
                    onClose = onClearSearch
                )
            } else {
                // 普通模式顶栏
                TopAppBar(
                    title = {
                        Text(
                            if (uiState.showFavoritesOnly) "收藏夹 (${uiState.favoriteUris.size})"
                            else "相册 (${uiState.totalCount})"
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        // 收藏夹筛选按钮
                        IconButton(onClick = onToggleFavoritesFilter) {
                            Icon(
                                if (uiState.showFavoritesOnly) Icons.Default.Favorite
                                else Icons.Default.FavoriteBorder,
                                contentDescription = if (uiState.showFavoritesOnly) "显示全部" else "只看收藏",
                                tint = if (uiState.showFavoritesOnly) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { onSearchActiveChange(true) }) {
                            Icon(Icons.Default.Search, contentDescription = "搜索")
                        }
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 类型筛选器（搜索时不显示）
            if (!uiState.isSearchActive) {
                MediaTypeFilter(
                    selectedType = uiState.selectedMediaType,
                    onTypeChange = onMediaTypeChange,
                    enabled = !uiState.isSelectionMode
                )
            }

            // 搜索结果计数
            if (uiState.isSearchActive && uiState.searchQuery.isNotBlank()) {
                Text(
                    text = "找到 ${uiState.filteredMediaFiles.size} 个结果",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // 媒体网格
            Box(modifier = Modifier.fillMaxSize()) {
                val displayFiles = uiState.displayMediaFiles
                if (uiState.isLoading && displayFiles.isEmpty()) {
                    // 初始加载中
                    LoadingIndicator()
                } else if (displayFiles.isEmpty()) {
                    // 空状态
                    if (uiState.isSearchActive && uiState.searchQuery.isNotBlank()) {
                        SearchEmptyState(query = uiState.searchQuery)
                    } else if (uiState.showFavoritesOnly) {
                        FavoritesEmptyState()
                    } else {
                        EmptyState(type = uiState.selectedMediaType)
                    }
                } else {
                    // 媒体网格
                    MediaGrid(
                        mediaFiles = displayFiles,
                        isSelectionMode = uiState.isSelectionMode,
                        selectedUris = uiState.selectedUris,
                        favoriteUris = uiState.favoriteUris,
                        isLoadingMore = uiState.isLoadingMore && !uiState.isSearchActive,
                        hasMoreData = uiState.hasMoreData && !uiState.isSearchActive,
                        onMediaClick = { media ->
                            if (uiState.isSelectionMode) {
                                onToggleSelection(media.uri)
                            } else {
                                onMediaClick(media)
                            }
                        },
                        onMediaLongClick = onMediaLongClick,
                        onLoadMore = onLoadMore,
                        onToggleFavorite = onToggleFavorite,
                        formatDuration = formatDuration
                    )
                }
            }
        }
    }

    // 删除确认对话框
    if (showDeleteDialog) {
        DeleteConfirmDialog(
            count = uiState.selectedUris.size,
            onConfirm = {
                showDeleteDialog = false
                onDeleteSelected()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

/**
 * 多选模式顶栏
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit
) {
    TopAppBar(
        title = { Text("已选择 $selectedCount 项") },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "关闭")
            }
        },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Default.SelectAll, contentDescription = "全选")
            }
            IconButton(
                onClick = onDelete,
                enabled = selectedCount > 0
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = if (selectedCount > 0) MaterialTheme.colorScheme.error else Color.Gray
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    )
}

/**
 * 搜索模式顶栏
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    TopAppBar(
        title = {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("搜索文件名...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                ),
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "清除")
                        }
                    }
                }
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

/**
 * 媒体类型筛选器
 */
@Composable
private fun MediaTypeFilter(
    selectedType: MediaType,
    onTypeChange: (MediaType) -> Unit,
    enabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MediaType.entries.forEach { type ->
            FilterChip(
                selected = type == selectedType,
                onClick = { if (enabled) onTypeChange(type) },
                label = {
                    Text(
                        text = when (type) {
                            MediaType.ALL -> "全部"
                            MediaType.PHOTO -> "照片"
                            MediaType.VIDEO -> "视频"
                        }
                    )
                },
                enabled = enabled
            )
        }
    }
}

/**
 * 媒体网格
 */
@Composable
private fun MediaGrid(
    mediaFiles: List<MediaFile>,
    isSelectionMode: Boolean,
    selectedUris: Set<Uri>,
    favoriteUris: Set<String>,
    isLoadingMore: Boolean,
    hasMoreData: Boolean,
    onMediaClick: (MediaFile) -> Unit,
    onMediaLongClick: (Uri) -> Unit,
    onLoadMore: () -> Unit,
    onToggleFavorite: (Uri) -> Unit,
    formatDuration: (Long) -> String
) {
    val gridState = rememberLazyGridState()

    // 检测滚动到底部加载更多
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem != null && lastVisibleItem.index >= mediaFiles.size - 6
        }
    }

    LaunchedEffect(shouldLoadMore) {
        snapshotFlow { shouldLoadMore }
            .collect { shouldLoad ->
                if (shouldLoad && hasMoreData && !isLoadingMore) {
                    onLoadMore()
                }
            }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = gridState,
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            items = mediaFiles,
            key = { it.uri.toString() }
        ) { media ->
            MediaThumbnail(
                media = media,
                isSelectionMode = isSelectionMode,
                isSelected = media.uri in selectedUris,
                isFavorite = media.uri.toString() in favoriteUris,
                onClick = { onMediaClick(media) },
                onLongClick = { onMediaLongClick(media.uri) },
                onToggleFavorite = { onToggleFavorite(media.uri) },
                formatDuration = formatDuration
            )
        }

        // 加载更多指示器
        if (isLoadingMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

/**
 * 媒体缩略图
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaThumbnail(
    media: MediaFile,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    formatDuration: (Long) -> String
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        // 缩略图
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(media.uri)
                .crossfade(true)
                .build(),
            contentDescription = media.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // 视频时长标签
        if (media.isVideo && media.duration > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = formatDuration(media.duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }

        // 选择指示器
        AnimatedVisibility(
            visible = isSelectionMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else Color.White.copy(alpha = 0.7f)
                    )
                    .clickable { onClick() },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "已选中",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 收藏指示器（非选择模式时显示在左上角）
        if (!isSelectionMode && isFavorite) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { onToggleFavorite() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = "已收藏",
                    tint = Color.Red,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * 加载指示器
 */
@Composable
private fun LoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

/**
 * 空状态
 */
@Composable
private fun EmptyState(type: MediaType) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = when (type) {
                    MediaType.ALL -> "暂无媒体文件"
                    MediaType.PHOTO -> "暂无照片"
                    MediaType.VIDEO -> "暂无视频"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "使用相机拍摄后将在这里显示",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * 搜索无结果状态
 */
@Composable
private fun SearchEmptyState(query: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "未找到匹配的文件",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "没有找到包含 \"$query\" 的文件",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

/**
 * 收藏夹空状态
 */
@Composable
private fun FavoritesEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.FavoriteBorder,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "暂无收藏",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "点击照片上的爱心图标添加收藏",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * 删除确认对话框
 */
@Composable
private fun DeleteConfirmDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除确认") },
        text = { Text("确定要删除选中的 $count 个文件吗？此操作无法撤销。") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 媒体详情页面
 *
 * @param media 媒体文件
 * @param isFavorite 是否已收藏
 * @param onBack 返回回调
 * @param onDelete 删除回调
 * @param onEdit 编辑回调
 * @param onToggleFavorite 切换收藏回调
 * @param formatFileSize 格式化文件大小函数
 * @param formatDuration 格式化时长函数
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaDetailScreen(
    media: MediaFile,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onToggleFavorite: () -> Unit,
    formatFileSize: (Long) -> String,
    formatDuration: (Long) -> String
) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = media.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 收藏按钮
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (isFavorite) "取消收藏" else "添加收藏",
                            tint = if (isFavorite) Color.Red else Color.White
                        )
                    }
                    // 只有图片才显示编辑按钮
                    if (!media.isVideo) {
                        IconButton(onClick = onEdit) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "编辑",
                                tint = Color.White
                            )
                        }
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 媒体预览
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(media.uri)
                        .crossfade(true)
                        .build(),
                    contentDescription = media.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )

                // 视频播放图标
                if (media.isVideo) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "播放",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }

            // 媒体信息
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(16.dp)
            ) {
                // 文件信息行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    InfoItem(label = "大小", value = formatFileSize(media.size))
                    if (media.width > 0 && media.height > 0) {
                        InfoItem(label = "尺寸", value = "${media.width} × ${media.height}")
                    }
                    if (media.isVideo && media.duration > 0) {
                        InfoItem(label = "时长", value = formatDuration(media.duration))
                    }
                }
            }
        }
    }

    // 删除确认对话框
    if (showDeleteDialog) {
        DeleteConfirmDialog(
            count = 1,
            onConfirm = {
                showDeleteDialog = false
                onDelete()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

/**
 * 信息项
 */
@Composable
private fun InfoItem(
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )
    }
}
