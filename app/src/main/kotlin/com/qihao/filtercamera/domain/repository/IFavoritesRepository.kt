/**
 * IFavoritesRepository.kt - 收藏夹仓库接口
 *
 * 定义收藏媒体文件的抽象接口
 * 使用 DataStore 持久化存储收藏的媒体 URI
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.domain.repository

import android.net.Uri
import kotlinx.coroutines.flow.Flow

/**
 * 收藏夹仓库接口
 * 负责收藏媒体的添加、删除和查询
 */
interface IFavoritesRepository {

    /**
     * 获取所有收藏媒体的 URI 集合
     * @return 收藏 URI 集合的 Flow（响应式更新）
     */
    fun getFavoriteUris(): Flow<Set<String>>

    /**
     * 检查指定媒体是否已收藏
     * @param uri 媒体 Uri
     * @return 是否已收藏
     */
    suspend fun isFavorite(uri: Uri): Boolean

    /**
     * 添加媒体到收藏
     * @param uri 媒体 Uri
     */
    suspend fun addFavorite(uri: Uri)

    /**
     * 从收藏中移除媒体
     * @param uri 媒体 Uri
     */
    suspend fun removeFavorite(uri: Uri)

    /**
     * 切换媒体收藏状态
     * @param uri 媒体 Uri
     * @return 切换后的收藏状态（true=已收藏，false=未收藏）
     */
    suspend fun toggleFavorite(uri: Uri): Boolean

    /**
     * 清空所有收藏
     */
    suspend fun clearAllFavorites()
}
