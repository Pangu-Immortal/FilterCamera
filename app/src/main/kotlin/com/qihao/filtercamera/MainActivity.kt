/**
 * MainActivity.kt - 应用主Activity
 *
 * 职责：
 * - 单Activity架构入口
 * - 设置Compose主题和导航
 * - 处理系统UI配置（状态栏、导航栏）
 * - 管理应用级主题设置
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.qihao.filtercamera.domain.repository.ISettingsRepository
import com.qihao.filtercamera.domain.repository.ThemeMode
import com.qihao.filtercamera.presentation.common.theme.FilterCameraTheme
import com.qihao.filtercamera.presentation.navigation.NavGraph
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 应用主Activity
 * 使用@AndroidEntryPoint注解启用Hilt注入
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** 设置仓库 - 用于读取主题设置 */
    @Inject
    lateinit var settingsRepository: ISettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 启用边到边显示（沉浸式状态栏）
        enableEdgeToEdge()

        setContent {
            // 收集主题设置状态
            val themeMode by settingsRepository.getThemeMode().collectAsState(initial = ThemeMode.SYSTEM)

            // 根据主题模式确定是否使用深色主题
            val isDarkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()                     // 跟随系统
                ThemeMode.LIGHT -> false                                       // 强制浅色
                ThemeMode.DARK -> true                                         // 强制深色
            }

            FilterCameraTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // 导航图 - 管理所有页面跳转
                    NavGraph()
                }
            }
        }
    }
}
