/**
 * Screen.kt - 导航路由定义
 *
 * 定义应用内所有页面的导航路由
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.presentation.navigation

import android.net.Uri
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * 导航路由密封类
 *
 * @param route 路由路径
 */
sealed class Screen(val route: String) {
    /**
     * 相机页面
     */
    data object Camera : Screen("camera")

    /**
     * 相册页面
     */
    data object Gallery : Screen("gallery")

    /**
     * 设置页面
     */
    data object Settings : Screen("settings")

    /**
     * 图片编辑页面
     *
     * 路由格式: edit/{imageUri}
     * 参数 imageUri 需要URL编码
     */
    data object Edit : Screen("edit/{imageUri}") {
        /**
         * 创建带参数的路由
         * @param imageUri 图片Uri（会自动URL编码）
         */
        fun createRoute(imageUri: Uri): String {
            val encodedUri = URLEncoder.encode(imageUri.toString(), StandardCharsets.UTF_8.toString())
            return "edit/$encodedUri"
        }
    }
}
