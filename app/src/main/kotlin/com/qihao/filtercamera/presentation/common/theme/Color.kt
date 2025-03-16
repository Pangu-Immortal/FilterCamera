/**
 * Color.kt - 应用颜色定义
 *
 * 定义Material 3主题颜色
 * 包含亮色和暗色主题
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.presentation.common.theme

import androidx.compose.ui.graphics.Color

// 主色调 - 深蓝色系
val Primary = Color(0xFF1976D2)              // 主色
val PrimaryVariant = Color(0xFF1565C0)       // 主色变体
val PrimaryDark = Color(0xFF0D47A1)          // 深色主色

// 次要色 - 青色系
val Secondary = Color(0xFF00BCD4)            // 次要色
val SecondaryVariant = Color(0xFF00ACC1)     // 次要色变体

// 背景色
val BackgroundLight = Color(0xFFFAFAFA)      // 亮色背景
val BackgroundDark = Color(0xFF121212)       // 暗色背景
val SurfaceLight = Color(0xFFFFFFFF)         // 亮色表面
val SurfaceDark = Color(0xFF1E1E1E)          // 暗色表面

// 文字色
val OnPrimaryLight = Color(0xFFFFFFFF)       // 主色上的文字（亮）
val OnPrimaryDark = Color(0xFFFFFFFF)        // 主色上的文字（暗）
val OnBackgroundLight = Color(0xFF212121)    // 背景上的文字（亮）
val OnBackgroundDark = Color(0xFFE0E0E0)     // 背景上的文字（暗）
val OnSurfaceLight = Color(0xFF212121)       // 表面上的文字（亮）
val OnSurfaceDark = Color(0xFFE0E0E0)        // 表面上的文字（暗）

// 功能色
val Error = Color(0xFFD32F2F)                // 错误色
val Success = Color(0xFF388E3C)              // 成功色
val Warning = Color(0xFFF57C00)              // 警告色
val Info = Color(0xFF1976D2)                 // 信息色

// 相机控制颜色
val CameraControlBackground = Color(0x80000000)  // 相机控制区背景（半透明黑）
val CaptureButtonColor = Color(0xFFFFFFFF)       // 拍照按钮颜色
val RecordingColor = Color(0xFFFF4444)           // 录像状态颜色

// 滤镜选择器颜色
val FilterSelectorBackground = Color(0xE6000000) // 滤镜选择器背景
val FilterItemSelected = Color(0xFF1976D2)       // 选中的滤镜边框
val FilterItemUnselected = Color(0x33FFFFFF)     // 未选中的滤镜边框

// ==================== iOS风格颜色 ====================

// iOS系统色
val iOSBlue = Color(0xFF007AFF)                  // iOS标准蓝色
val iOSGray = Color(0xFF8E8E93)                  // iOS灰色
val iOSGray2 = Color(0xFFAEAEB2)                 // iOS浅灰色
val iOSGray3 = Color(0xFFC7C7CC)                 // iOS更浅灰色
val iOSGray4 = Color(0xFFD1D1D6)                 // iOS极浅灰色
val iOSGray5 = Color(0xFFE5E5EA)                 // iOS背景灰
val iOSGray6 = Color(0xFFF2F2F7)                 // iOS最浅灰(背景)
val iOSYellow = Color(0xFFFFCC00)                // iOS黄色（快门按钮）
val iOSRed = Color(0xFFFF3B30)                   // iOS红色（录制）
val iOSGreen = Color(0xFF34C759)                 // iOS绿色
val iOSOrange = Color(0xFFFF9500)                // iOS橙色

// iOS毛玻璃背景
val BlurBackgroundDark = Color(0xCC1C1C1E)       // 深色毛玻璃背景(80%透明)
val BlurBackgroundLight = Color(0xCCF2F2F7)      // 浅色毛玻璃背景(80%透明)
val BlurBackgroundUltraDark = Color(0xE61C1C1E)  // 超深色毛玻璃(90%透明)

// iOS底部控制栏
val BottomBarBackground = Color(0xE6000000)      // 底部栏背景(90%黑)
val BottomBarDivider = Color(0x33FFFFFF)         // 底部栏分割线(20%白)

// iOS模式切换标签
val ModeTabActive = Color(0xFFFFCC00)            // 激活模式标签(黄色)
val ModeTabInactive = Color(0x99FFFFFF)          // 未激活模式标签(60%白)

// iOS快门按钮
val ShutterButtonOuter = Color(0xFFFFFFFF)       // 快门外圈(白色)
val ShutterButtonInner = Color(0xFFFFFFFF)       // 快门内圈(白色)
val ShutterButtonRecording = Color(0xFFFF3B30)   // 录制中内圈(红色)

// iOS滤镜选择器
val FilterSelectorBarBg = Color(0xE61C1C1E)      // 滤镜栏背景
val FilterGroupActive = Color(0xFFFFCC00)        // 激活分组(黄色)
val FilterGroupInactive = Color(0x99FFFFFF)      // 未激活分组(60%白)
val FilterThumbnailBorder = Color(0xFFFFCC00)    // 选中滤镜边框(黄色)
val FilterThumbnailText = Color(0xFFFFFFFF)      // 滤镜名称(白色)

// ==================== 小米相机风格颜色 ====================

// 小米底部控制栏
val XiaomiBottomBarBg = Color(0xFF000000)        // 小米底部栏纯黑背景
val XiaomiBottomBarGradient = Color(0x99000000)  // 小米底部栏渐变黑色

// 小米模式TAB
val XiaomiModeActive = Color(0xFFFFFFFF)         // 小米激活模式(纯白)
val XiaomiModeInactive = Color(0x80FFFFFF)       // 小米未激活模式(50%白)
val XiaomiModeIndicator = Color(0xFFFFCC00)      // 小米模式下划线指示器(黄色)

// 小米快门按钮
val XiaomiShutterOuter = Color(0xFFFFFFFF)       // 小米快门外圈(白色)
val XiaomiShutterInner = Color(0xFFFFFFFF)       // 小米快门内圈(白色)
val XiaomiShutterRecording = Color(0xFFFF4444)   // 小米录制状态(红色)

// 小米控制按钮
val XiaomiControlBg = Color(0x33FFFFFF)          // 小米控制按钮背景(20%白)
val XiaomiControlIcon = Color(0xFFFFFFFF)        // 小米控制按钮图标(白色)

// 小米相册缩略图
val XiaomiGalleryBorder = Color(0x66FFFFFF)      // 小米相册边框(40%白)
val XiaomiGalleryBorderActive = Color(0xFFFFFFFF) // 小米相册激活边框(白色)

// 小米滤镜按钮
val XiaomiFilterActive = Color(0xFFFFCC00)       // 小米滤镜激活(黄色)
val XiaomiFilterInactive = Color(0xFFFFFFFF)     // 小米滤镜未激活(白色)

// 小米闪屏动画
val XiaomiFlashOverlay = Color(0xFF000000)       // 小米拍照闪屏(纯黑)
