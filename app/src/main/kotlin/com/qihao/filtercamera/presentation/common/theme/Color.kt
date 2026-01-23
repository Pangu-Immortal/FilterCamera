/**
 * Color.kt - 相机应用统一颜色与主题系统
 *
 * 整合所有UI颜色定义为统一的CameraTheme对象
 * 提供：
 * - 主色调系统
 * - 功能色系统
 * - 控件色系统
 * - 兼容旧版颜色定义
 *
 * @author qihao
 * @since 3.0.0
 */
package com.qihao.filtercamera.presentation.common.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// ==================== CameraTheme 统一主题系统 ====================

/**
 * CameraTheme - 相机应用统一主题
 *
 * 集中管理所有颜色定义，确保视觉一致性
 * 使用时通过 CameraTheme.Colors.xxx 访问
 */
object CameraTheme {

    // ==================== 颜色系统 ====================

    /**
     * 统一颜色定义
     */
    object Colors {
        // ---------- 主色调 ----------
        val primary = Color(0xFFFFD700)                     // 金色主色（品牌色）
        val primaryVariant = Color(0xFFD8AE31)              // 深金色变体
        val onPrimary = Color.Black                          // 主色上的文字

        // ---------- 背景色 ----------
        val background = Color(0xFF000000)                   // 纯黑背景（相机标准）
        val backgroundDark = Color(0xFF121212)              // 深色背景（设置页面）
        val surface = Color(0xFF1E1E1E)                     // 卡片表面
        val surfaceVariant = Color(0x4D000000)              // 半透明表面（30%黑）
        val surfaceElevated = Color(0xFF2C2C2C)             // 提升表面

        // ---------- 控件背景色 ----------
        val controlBackground = Color(0x66000000)           // 控件半透明背景（40%黑）
        val controlBackgroundDark = Color(0xCC000000)       // 深色控件背景（80%黑）
        val controlBackgroundLight = Color(0x33FFFFFF)      // 浅色控件背景（20%白）

        // ---------- 文字色 ----------
        val textPrimary = Color(0xFFFFFFFF)                 // 主要文字（白色）
        val textSecondary = Color(0xB3FFFFFF)               // 次要文字（70%白）
        val textTertiary = Color(0x80FFFFFF)                // 三级文字（50%白）
        val textDisabled = Color(0x4DFFFFFF)                // 禁用文字（30%白）

        // ---------- 图标色 ----------
        val iconActive = Color(0xFFFFFFFF)                  // 激活图标（白色）
        val iconInactive = Color(0x99FFFFFF)                // 非激活图标（60%白）
        val iconAccent = Color(0xFFFFD700)                  // 强调图标（金色）

        // ---------- 状态色 ----------
        val recording = Color(0xFFFF4444)                   // 录制红
        val success = Color(0xFF4CAF50)                     // 成功绿
        val warning = Color(0xFFFF9800)                     // 警告橙
        val error = Color(0xFFFF3B30)                       // 错误红
        val info = Color(0xFF2196F3)                        // 信息蓝

        // ---------- 分割线 ----------
        val divider = Color(0x33FFFFFF)                     // 分割线（20%白）
        val dividerLight = Color(0x1AFFFFFF)                // 浅分割线（10%白）

        // ---------- 阴影 ----------
        val shadow = Color(0x40000000)                      // 阴影色（25%黑）
        val shadowDark = Color(0x80000000)                  // 深阴影（50%黑）
    }

    // ==================== 控件专用颜色 ====================

    /**
     * 快门按钮颜色
     */
    object Shutter {
        val outer = Color(0xFFFFFFFF)                       // 快门外圈（白色）
        val inner = Color(0xFFFFFFFF)                       // 快门内圈（白色）
        val recording = Color(0xFFFF4444)                   // 录制状态（红色）
        val border = Color(0x40FFFFFF)                      // 快门边框（25%白）
    }

    /**
     * 模式选择器颜色
     */
    object ModeSelector {
        val active = Color(0xFFFFD700)                      // 激活模式（金色）
        val inactive = Color(0x99FFFFFF)                    // 未激活模式（60%白）
        val indicator = Color(0xFFFFD700)                   // 指示器（金色）
        val background = Color(0x00000000)                  // 背景（透明）
    }

    /**
     * 滤镜选择器颜色
     */
    object FilterSelector {
        val background = Color(0xE6000000)                  // 滤镜栏背景（90%黑）
        val itemSelected = Color(0xFFFFD700)                // 选中边框（金色）
        val itemUnselected = Color(0x33FFFFFF)              // 未选中边框（20%白）
        val groupActive = Color(0xFFFFD700)                 // 激活分组（金色）
        val groupInactive = Color(0x99FFFFFF)               // 未激活分组（60%白）
        val labelText = Color(0xFFFFFFFF)                   // 标签文字（白色）
    }

    /**
     * 对焦指示器颜色
     */
    object FocusIndicator {
        val border = Color(0xFFFFD700)                      // 对焦框边框（金色）
        val corner = Color(0xFFFFD700)                      // 对焦角标（金色）
        val tracking = Color(0xFF4CAF50)                    // 追踪状态（绿色）
        val failed = Color(0xFFFF4444)                      // 失败状态（红色）
    }

    /**
     * 变焦控制颜色
     */
    object ZoomControl {
        val buttonActive = Color(0xFFFFD700)                // 按钮激活（金色）
        val buttonInactive = Color(0xFFFFFFFF)              // 按钮未激活（白色）
        val indicator = Color(0xFFFFD700)                   // 指示器（金色）
        val tickMajor = Color(0xFFFFFFFF)                   // 主刻度（白色）
        val tickMinor = Color(0x80FFFFFF)                   // 次刻度（50%白）
        val pillBackground = Color(0xCC000000)              // 胶囊背景（80%黑）
        val pillBorder = Color(0x40FFFFFF)                  // 胶囊边框（25%白）
    }

    /**
     * 顶部栏颜色
     */
    object TopBar {
        val background = Color(0x00000000)                  // 背景（透明）
        val backgroundGradient = Color(0x80000000)          // 渐变背景（50%黑）
        val iconDefault = Color(0xFFFFFFFF)                 // 默认图标（白色）
        val iconActive = Color(0xFFFFD700)                  // 激活图标（金色）
    }

    /**
     * 底部栏颜色
     */
    object BottomBar {
        val background = Color(0xE6000000)                  // 背景（90%黑）
        val backgroundGradient = Color(0x99000000)          // 渐变背景（60%黑）
        val divider = Color(0x33FFFFFF)                     // 分割线（20%白）
    }

    /**
     * 设置面板颜色
     */
    object SettingsPanel {
        val background = Color(0xF2000000)                  // 面板背景（95%黑）
        val itemBackground = Color(0x1AFFFFFF)              // 项目背景（10%白）
        val itemBackgroundPressed = Color(0x33FFFFFF)       // 按下背景（20%白）
        val header = Color(0xFFFFFFFF)                      // 标题文字（白色）
        val subtitle = Color(0x99FFFFFF)                    // 副标题（60%白）
        val toggle = Color(0xFFFFD700)                      // 开关激活色（金色）
    }

    /**
     * 直方图颜色
     */
    object Histogram {
        val background = Color(0x80000000)                  // 背景（50%黑）
        val red = Color(0xFFFF6B6B)                         // 红色通道
        val green = Color(0xFF69DB7C)                       // 绿色通道
        val blue = Color(0xFF74C0FC)                        // 蓝色通道
        val luminance = Color(0xFFFFFFFF)                   // 亮度通道
    }

    /**
     * 覆盖层颜色
     */
    object Overlay {
        val background = Color(0xCC000000)                  // 覆盖层背景（80%黑）
        val backgroundLight = Color(0x66000000)             // 浅覆盖层（40%黑）
        val button = Color(0x66000000)                      // 按钮背景（40%黑）
        val buttonPressed = Color(0x99000000)               // 按钮按下（60%黑）
        val badge = Color(0xFFFFD700)                       // 徽章背景（金色）
        val badgeText = Color.Black                         // 徽章文字（黑色）
    }

    /**
     * 文档扫描颜色
     */
    object DocumentScan {
        val frameBorder = Color(0xFF4CAF50)                 // 检测框边框（绿色）
        val frameCorner = Color(0xFFFFD700)                 // 检测框角标（金色）
        val gridLine = Color(0x40FFFFFF)                    // 网格线（25%白）
        val scanLine = Color(0xFFFFD700)                    // 扫描线（金色）
    }

    /**
     * 人脸检测颜色
     */
    object FaceDetection {
        val frameBorder = Color(0xFF4CAF50)                 // 人脸框边框（绿色）
        val frameCornerTracking = Color(0xFFFFD700)         // 追踪状态角标（金色）
        val eyeIndicator = Color(0xFF2196F3)                // 眼睛指示器（蓝色）
    }

    /**
     * 夜景模式颜色
     */
    object NightMode {
        val progressBackground = Color(0x99000000)          // 进度背景（60%黑）
        val progressForeground = Color(0xFFFFD700)          // 进度前景（金色）
        val hint = Color(0xFFFFFFFF)                        // 提示文字（白色）
    }

    /**
     * 延时摄影颜色
     */
    object Timelapse {
        val indicator = Color(0xFFFF4444)                   // 录制指示器（红色）
        val counter = Color(0xFFFFFFFF)                     // 计数器（白色）
        val progressTrack = Color(0x40FFFFFF)               // 进度轨道（25%白）
        val progressFill = Color(0xFFFFD700)                // 进度填充（金色）
    }
}

// ==================== 使用说明 ====================
// 所有颜色访问通过 CameraTheme 统一入口
// 示例: CameraTheme.Colors.primary, CameraTheme.Shutter.outer
// 旧版兼容变量已移除（2026-01-20清理）
