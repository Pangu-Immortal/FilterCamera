/**
 * FavoritesRepositoryImpl.kt - 收藏夹仓库实现
 *
 * 使用 DataStore Preferences 实现收藏媒体的持久化存储
 * 使用 StringSet 存储收藏的媒体 URI 字符串
 *
 * 技术实现：
 * - DataStore stringSetPreferencesKey 存储 URI 集合
 * - Flow 实现响应式数据流
 * - Uri.toString() 与 Uri.parse() 进行序列化/反序列化
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.qihao.filtercamera.data.local.SettingsKeys
import com.qihao.filtercamera.domain.repository.IFavoritesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Context 扩展属性 - 收藏夹专用 DataStore 实例
 *
 * 独立于设置的 DataStore，避免数据耦合
 */
private val Context.favoritesDataStore by preferencesDataStore(
    name = "filter_camera_favorites"                                          // DataStore 文件名
)

/**
 * 收藏夹仓库实现类
 *
 * @param context 应用上下文
 */
@Singleton
class FavoritesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : IFavoritesRepository {

    companion object {
        private const val TAG = "FavoritesRepositoryImpl"                     // 日志标签
    }

    /** DataStore 实例 */
    private val dataStore = context.favoritesDataStore

    /**
     * 获取所有收藏媒体的 URI 集合
     */
    override fun getFavoriteUris(): Flow<Set<String>> = dataStore.data
        .catch { exception ->                                                 // 处理读取异常
            Log.e(TAG, "getFavoriteUris: 读取失败", exception)
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[SettingsKeys.FAVORITE_MEDIA_URIS] ?: emptySet()       // 返回收藏集合或空集
        }

    /**
     * 检查指定媒体是否已收藏
     */
    override suspend fun isFavorite(uri: Uri): Boolean {
        val uriString = uri.toString()
        val favorites = getFavoriteUris().first()
        val isFav = uriString in favorites
        Log.d(TAG, "isFavorite: uri=$uriString, result=$isFav")
        return isFav
    }

    /**
     * 添加媒体到收藏
     */
    override suspend fun addFavorite(uri: Uri) {
        val uriString = uri.toString()
        Log.d(TAG, "addFavorite: uri=$uriString")
        dataStore.edit { preferences ->
            val current = preferences[SettingsKeys.FAVORITE_MEDIA_URIS] ?: emptySet()
            preferences[SettingsKeys.FAVORITE_MEDIA_URIS] = current + uriString
        }
    }

    /**
     * 从收藏中移除媒体
     */
    override suspend fun removeFavorite(uri: Uri) {
        val uriString = uri.toString()
        Log.d(TAG, "removeFavorite: uri=$uriString")
        dataStore.edit { preferences ->
            val current = preferences[SettingsKeys.FAVORITE_MEDIA_URIS] ?: emptySet()
            preferences[SettingsKeys.FAVORITE_MEDIA_URIS] = current - uriString
        }
    }

    /**
     * 切换媒体收藏状态
     */
    override suspend fun toggleFavorite(uri: Uri): Boolean {
        val uriString = uri.toString()
        var newState = false
        dataStore.edit { preferences ->
            val current = preferences[SettingsKeys.FAVORITE_MEDIA_URIS] ?: emptySet()
            newState = uriString !in current                                  // 切换后的状态
            preferences[SettingsKeys.FAVORITE_MEDIA_URIS] = if (newState) {
                current + uriString                                           // 添加到收藏
            } else {
                current - uriString                                           // 从收藏移除
            }
        }
        Log.d(TAG, "toggleFavorite: uri=$uriString, newState=$newState")
        return newState
    }

    /**
     * 清空所有收藏
     */
    override suspend fun clearAllFavorites() {
        Log.d(TAG, "clearAllFavorites: 清空所有收藏")
        dataStore.edit { preferences ->
            preferences[SettingsKeys.FAVORITE_MEDIA_URIS] = emptySet()
        }
    }
}
