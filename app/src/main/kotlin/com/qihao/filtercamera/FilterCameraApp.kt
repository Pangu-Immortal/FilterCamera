/**
 * FilterCameraApp.kt - 应用程序入口类
 *
 * 职责：
 * - 初始化Hilt依赖注入框架
 * - 全局应用配置
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * FilterCamera应用程序类
 * 使用@HiltAndroidApp注解启用Hilt依赖注入
 */
@HiltAndroidApp
class FilterCameraApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // 全局初始化配置
    }
}
