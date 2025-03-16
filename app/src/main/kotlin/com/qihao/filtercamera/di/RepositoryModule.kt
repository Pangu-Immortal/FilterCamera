/**
 * RepositoryModule.kt - 仓库Hilt模块
 *
 * 提供仓库接口的绑定
 * 将接口绑定到具体实现
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.di

import com.qihao.filtercamera.data.repository.CameraRepositoryImpl
import com.qihao.filtercamera.data.repository.FavoritesRepositoryImpl
import com.qihao.filtercamera.data.repository.FilterRepositoryImpl
import com.qihao.filtercamera.data.repository.MediaRepositoryImpl
import com.qihao.filtercamera.data.repository.SettingsRepositoryImpl
import com.qihao.filtercamera.domain.repository.ICameraRepository
import com.qihao.filtercamera.domain.repository.IFavoritesRepository
import com.qihao.filtercamera.domain.repository.IFilterRepository
import com.qihao.filtercamera.domain.repository.IMediaRepository
import com.qihao.filtercamera.domain.repository.ISettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 仓库Hilt模块
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    /**
     * 绑定相机仓库
     */
    @Binds
    @Singleton
    abstract fun bindCameraRepository(
        impl: CameraRepositoryImpl
    ): ICameraRepository

    /**
     * 绑定滤镜仓库
     */
    @Binds
    @Singleton
    abstract fun bindFilterRepository(
        impl: FilterRepositoryImpl
    ): IFilterRepository

    /**
     * 绑定媒体仓库
     */
    @Binds
    @Singleton
    abstract fun bindMediaRepository(
        impl: MediaRepositoryImpl
    ): IMediaRepository

    /**
     * 绑定设置仓库
     */
    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: SettingsRepositoryImpl
    ): ISettingsRepository

    /**
     * 绑定收藏夹仓库
     */
    @Binds
    @Singleton
    abstract fun bindFavoritesRepository(
        impl: FavoritesRepositoryImpl
    ): IFavoritesRepository
}
