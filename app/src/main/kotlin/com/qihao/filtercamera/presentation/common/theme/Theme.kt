/**
 * Theme.kt - 应用主题定义
 *
 * 定义Material 3主题
 * 支持亮色/暗色模式
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.presentation.common.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 暗色主题配色方案
 * 使用 CameraTheme 统一颜色系统
 */
private val DarkColorScheme = darkColorScheme(
    primary = CameraTheme.Colors.primary,                    // 金色主色
    secondary = CameraTheme.Colors.info,                     // 信息蓝作为次色
    background = CameraTheme.Colors.backgroundDark,          // 深色背景
    surface = CameraTheme.Colors.surface,                    // 卡片表面
    onPrimary = CameraTheme.Colors.onPrimary,                // 主色上文字（黑色）
    onSecondary = CameraTheme.Colors.textPrimary,            // 次色上文字（白色）
    onBackground = CameraTheme.Colors.textSecondary,         // 背景上文字（70%白）
    onSurface = CameraTheme.Colors.textSecondary,            // 表面上文字（70%白）
    error = CameraTheme.Colors.error                         // 错误红
)

/**
 * 亮色主题配色方案
 * 亮色模式专用颜色（相机应用主要使用暗色模式）
 */
private val LightColorScheme = lightColorScheme(
    primary = CameraTheme.Colors.primary,                    // 金色主色
    secondary = CameraTheme.Colors.info,                     // 信息蓝作为次色
    background = LightModeColors.background,                 // 亮色背景
    surface = LightModeColors.surface,                       // 亮色表面
    onPrimary = CameraTheme.Colors.onPrimary,                // 主色上文字（黑色）
    onSecondary = LightModeColors.onSurface,                 // 次色上文字
    onBackground = LightModeColors.onBackground,             // 背景上文字
    onSurface = LightModeColors.onSurface,                   // 表面上文字
    error = CameraTheme.Colors.error                         // 错误红
)

/**
 * 亮色模式专用颜色
 * 相机应用主要使用暗色模式，亮色模式仅用于设置等辅助页面
 */
private object LightModeColors {
    val background = androidx.compose.ui.graphics.Color(0xFFFAFAFA)  // 浅灰背景
    val surface = androidx.compose.ui.graphics.Color(0xFFFFFFFF)     // 纯白表面
    val onBackground = androidx.compose.ui.graphics.Color(0xFF212121) // 深灰文字
    val onSurface = androidx.compose.ui.graphics.Color(0xFF212121)    // 深灰文字
}

/**
 * FilterCamera应用主题
 *
 * @param darkTheme 是否使用暗色主题
 * @param dynamicColor 是否使用动态颜色（Android 12+）
 * @param content 内容
 */
@Composable
fun FilterCameraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    // 选择配色方案
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // 配置状态栏颜色（边缘到边缘模式下由Activity处理）
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // 使用WindowCompat API替代已弃用的statusBarColor
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
