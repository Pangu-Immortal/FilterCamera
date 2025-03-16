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
 */
private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    secondary = Secondary,
    background = BackgroundDark,
    surface = SurfaceDark,
    onPrimary = OnPrimaryDark,
    onSecondary = OnPrimaryDark,
    onBackground = OnBackgroundDark,
    onSurface = OnSurfaceDark,
    error = Error
)

/**
 * 亮色主题配色方案
 */
private val LightColorScheme = lightColorScheme(
    primary = Primary,
    secondary = Secondary,
    background = BackgroundLight,
    surface = SurfaceLight,
    onPrimary = OnPrimaryLight,
    onSecondary = OnPrimaryLight,
    onBackground = OnBackgroundLight,
    onSurface = OnSurfaceLight,
    error = Error
)

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
