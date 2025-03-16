/**
 * NavGraph.kt - 应用导航图
 *
 * 配置应用内所有页面的导航逻辑
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.qihao.filtercamera.presentation.camera.CameraScreen
import com.qihao.filtercamera.presentation.edit.EditScreen
import com.qihao.filtercamera.presentation.gallery.GalleryScreen
import com.qihao.filtercamera.presentation.settings.SettingsScreen

/**
 * 应用导航图
 *
 * @param navController 导航控制器
 */
@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Camera.route
    ) {
        // 相机页面
        composable(route = Screen.Camera.route) {
            CameraScreen(
                onNavigateToGallery = {
                    navController.navigate(Screen.Gallery.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        // 相册页面
        composable(route = Screen.Gallery.route) {
            GalleryScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToEdit = { imageUri ->
                    navController.navigate(Screen.Edit.createRoute(imageUri))
                }
            )
        }

        // 设置页面
        composable(route = Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // 图片编辑页面
        composable(
            route = Screen.Edit.route,
            arguments = listOf(
                navArgument("imageUri") {
                    type = NavType.StringType                                     // URI作为字符串传递（URL编码）
                }
            )
        ) {
            EditScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
