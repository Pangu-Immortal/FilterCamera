/**
 * ResponsiveDimens.kt - 响应式尺寸系统
 *
 * 根据设备屏幕宽度自动选择合适的尺寸，实现多设备适配
 * 支持四种屏幕类别：小屏手机、标准手机、大屏手机、平板
 *
 * 设计原则：
 * - 8点网格系统：所有间距为8的倍数
 * - 响应式缩放：根据屏幕大小自动调整组件尺寸
 * - 可访问性：确保触摸目标最小48dp
 *
 * @author qihao
 * @since 3.0.0
 */
package com.qihao.filtercamera.presentation.common.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ==================== 屏幕分类常量 ====================

/**
 * 屏幕类别枚举
 * 用于判断当前设备属于哪种屏幕类型
 */
enum class ScreenCategory {
    SMALL,      // 小屏手机 (width < 360dp)
    MEDIUM,     // 标准手机 (360dp - 411dp)
    LARGE,      // 大屏手机 (412dp - 599dp)
    TABLET      // 平板设备 (width >= 600dp)
}

// ==================== 间距系统（8点网格） ====================

/**
 * 间距值定义
 * 基于8点网格系统，确保视觉一致性
 */
@Immutable
data class SpacingValues(
    val xs: Dp = 4.dp,      // 紧凑间距（图标与文字间隙）
    val sm: Dp = 8.dp,      // 小间距（相邻元素）
    val md: Dp = 12.dp,     // 中间距（组内元素）
    val lg: Dp = 16.dp,     // 大间距（组间分隔）
    val xl: Dp = 24.dp,     // 超大间距（区域分隔）
    val xxl: Dp = 32.dp     // 特大间距（模块分隔）
)

// ==================== 圆角系统 ====================

/**
 * 圆角值定义
 * 统一的圆角规范，确保视觉一致性
 */
@Immutable
data class RadiusValues(
    val xxs: Dp = 2.dp,         // 微小圆角（网格线、装饰元素）
    val xs: Dp = 4.dp,          // 超小圆角（徽章、标签）
    val small: Dp = 8.dp,       // 小圆角（按钮、输入框）
    val medium: Dp = 16.dp,     // 中圆角（卡片、面板）
    val large: Dp = 24.dp,      // 大圆角（弹窗、底部表单）
    val extraLarge: Dp = 32.dp, // 超大圆角（特殊容器）
    val full: Dp = 100.dp       // 完全圆形（圆形按钮）
)

// ==================== 动画时长系统 ====================

/**
 * 动画时长定义（毫秒）
 * 统一的动画规范，确保交互一致性
 */
@Immutable
data class AnimationDurations(
    val instant: Int = 100,     // 即时反馈
    val fast: Int = 150,        // 快速动画
    val normal: Int = 300,      // 标准动画
    val slow: Int = 500,        // 缓慢动画
    val emphasis: Int = 800     // 强调动画
)

// ==================== 响应式尺寸定义 ====================

/**
 * 响应式尺寸数据类
 * 包含所有需要响应式调整的UI尺寸
 */
@Immutable
data class ResponsiveDimens(
    // 屏幕类别
    val screenCategory: ScreenCategory,

    // 快门按钮尺寸
    val shutterButtonSize: Dp,          // 快门按钮外圈尺寸
    val shutterInnerSize: Dp,           // 快门按钮内圈尺寸
    val shutterStrokeWidth: Dp,         // 快门按钮边框宽度

    // 底部控件
    val bottomBarHeight: Dp,            // 底部栏高度
    val bottomBarPadding: PaddingValues, // 底部栏内边距
    val bottomControlSpacing: Dp,       // 底部控件间距

    // 模式选择器
    val modeSelectorHeight: Dp,         // 模式选择器高度
    val modeItemMinWidth: Dp,           // 模式项最小宽度
    val modeItemPadding: PaddingValues, // 模式项内边距
    val modeIndicatorHeight: Dp,        // 模式指示器高度

    // 顶部栏
    val topBarHeight: Dp,               // 顶部栏高度
    val topBarIconSize: Dp,             // 顶部栏图标尺寸
    val topBarPadding: PaddingValues,   // 顶部栏内边距

    // 滤镜选择器
    val filterItemSize: Dp,             // 滤镜项尺寸
    val filterListHeight: Dp,           // 滤镜列表高度
    val filterListPadding: Dp,          // 滤镜列表水平边距
    val filterItemSpacing: Dp,          // 滤镜项间距

    // 对焦指示器
    val focusIndicatorSize: Dp,         // 对焦指示器尺寸
    val focusCornerLength: Dp,          // 对焦角标长度
    val focusStrokeWidth: Dp,           // 对焦线条宽度

    // 变焦控制
    val zoomSliderHeight: Dp,           // 变焦滑块高度
    val zoomSliderWidth: Dp,            // 变焦滑块宽度
    val zoomButtonSize: Dp,             // 变焦按钮尺寸

    // 设置面板
    val settingsPanelMaxWidth: Dp,      // 设置面板最大宽度
    val settingsItemHeight: Dp,         // 设置项高度
    val settingsIconSize: Dp,           // 设置图标尺寸

    // 直方图
    val histogramWidth: Dp,             // 直方图宽度
    val histogramHeight: Dp,            // 直方图高度

    // 覆盖层控件
    val overlayButtonSize: Dp,          // 覆盖层按钮尺寸
    val overlayIconSize: Dp,            // 覆盖层图标尺寸
    val overlayLabelHeight: Dp,         // 覆盖层标签高度

    // 通用尺寸
    val minTouchTarget: Dp = 48.dp,     // 最小触摸目标（可访问性）
    val iconSizeSmall: Dp,              // 小图标尺寸
    val iconSizeMedium: Dp,             // 中图标尺寸
    val iconSizeLarge: Dp,              // 大图标尺寸

    // 间距系统
    val spacing: SpacingValues = SpacingValues(),
    // 圆角系统
    val radius: RadiusValues = RadiusValues(),
    // 动画时长
    val animation: AnimationDurations = AnimationDurations()
)

// ==================== 预定义尺寸配置 ====================

/**
 * 小屏手机尺寸配置
 * 适用于屏幕宽度 < 360dp 的设备
 */
val SmallScreenDimens = ResponsiveDimens(
    screenCategory = ScreenCategory.SMALL,
    // 快门按钮 - 紧凑尺寸
    shutterButtonSize = 64.dp,
    shutterInnerSize = 52.dp,
    shutterStrokeWidth = 4.dp,
    // 底部控件 - 紧凑布局
    bottomBarHeight = 100.dp,
    bottomBarPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    bottomControlSpacing = 16.dp,
    // 模式选择器 - 紧凑布局
    modeSelectorHeight = 36.dp,
    modeItemMinWidth = 48.dp,
    modeItemPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
    modeIndicatorHeight = 2.dp,
    // 顶部栏 - 紧凑布局
    topBarHeight = 48.dp,
    topBarIconSize = 20.dp,
    topBarPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
    // 滤镜选择器 - 紧凑布局
    filterItemSize = 52.dp,
    filterListHeight = 80.dp,
    filterListPadding = 8.dp,
    filterItemSpacing = 8.dp,
    // 对焦指示器
    focusIndicatorSize = 56.dp,
    focusCornerLength = 12.dp,
    focusStrokeWidth = 2.dp,
    // 变焦控制
    zoomSliderHeight = 160.dp,
    zoomSliderWidth = 36.dp,
    zoomButtonSize = 32.dp,
    // 设置面板
    settingsPanelMaxWidth = 280.dp,
    settingsItemHeight = 44.dp,
    settingsIconSize = 20.dp,
    // 直方图
    histogramWidth = 100.dp,
    histogramHeight = 60.dp,
    // 覆盖层控件
    overlayButtonSize = 36.dp,
    overlayIconSize = 18.dp,
    overlayLabelHeight = 20.dp,
    // 通用尺寸
    iconSizeSmall = 16.dp,
    iconSizeMedium = 20.dp,
    iconSizeLarge = 24.dp
)

/**
 * 标准手机尺寸配置
 * 适用于屏幕宽度 360dp - 411dp 的设备
 */
val MediumScreenDimens = ResponsiveDimens(
    screenCategory = ScreenCategory.MEDIUM,
    // 快门按钮 - 标准尺寸
    shutterButtonSize = 72.dp,
    shutterInnerSize = 58.dp,
    shutterStrokeWidth = 4.dp,
    // 底部控件 - 标准布局
    bottomBarHeight = 120.dp,
    bottomBarPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    bottomControlSpacing = 24.dp,
    // 模式选择器 - 标准布局
    modeSelectorHeight = 40.dp,
    modeItemMinWidth = 56.dp,
    modeItemPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    modeIndicatorHeight = 3.dp,
    // 顶部栏 - 标准布局
    topBarHeight = 56.dp,
    topBarIconSize = 24.dp,
    topBarPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    // 滤镜选择器 - 标准布局
    filterItemSize = 60.dp,
    filterListHeight = 100.dp,
    filterListPadding = 12.dp,
    filterItemSpacing = 12.dp,
    // 对焦指示器
    focusIndicatorSize = 64.dp,
    focusCornerLength = 14.dp,
    focusStrokeWidth = 2.dp,
    // 变焦控制
    zoomSliderHeight = 180.dp,
    zoomSliderWidth = 40.dp,
    zoomButtonSize = 36.dp,
    // 设置面板
    settingsPanelMaxWidth = 320.dp,
    settingsItemHeight = 48.dp,
    settingsIconSize = 24.dp,
    // 直方图
    histogramWidth = 120.dp,
    histogramHeight = 72.dp,
    // 覆盖层控件
    overlayButtonSize = 40.dp,
    overlayIconSize = 20.dp,
    overlayLabelHeight = 24.dp,
    // 通用尺寸
    iconSizeSmall = 18.dp,
    iconSizeMedium = 24.dp,
    iconSizeLarge = 28.dp
)

/**
 * 大屏手机尺寸配置
 * 适用于屏幕宽度 412dp - 599dp 的设备
 */
val LargeScreenDimens = ResponsiveDimens(
    screenCategory = ScreenCategory.LARGE,
    // 快门按钮 - 大尺寸
    shutterButtonSize = 80.dp,
    shutterInnerSize = 64.dp,
    shutterStrokeWidth = 5.dp,
    // 底部控件 - 宽松布局
    bottomBarHeight = 140.dp,
    bottomBarPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
    bottomControlSpacing = 32.dp,
    // 模式选择器 - 宽松布局
    modeSelectorHeight = 44.dp,
    modeItemMinWidth = 64.dp,
    modeItemPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    modeIndicatorHeight = 3.dp,
    // 顶部栏 - 宽松布局
    topBarHeight = 60.dp,
    topBarIconSize = 26.dp,
    topBarPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    // 滤镜选择器 - 宽松布局
    filterItemSize = 68.dp,
    filterListHeight = 110.dp,
    filterListPadding = 16.dp,
    filterItemSpacing = 14.dp,
    // 对焦指示器
    focusIndicatorSize = 72.dp,
    focusCornerLength = 16.dp,
    focusStrokeWidth = 2.5f.dp,
    // 变焦控制
    zoomSliderHeight = 200.dp,
    zoomSliderWidth = 44.dp,
    zoomButtonSize = 40.dp,
    // 设置面板
    settingsPanelMaxWidth = 360.dp,
    settingsItemHeight = 52.dp,
    settingsIconSize = 26.dp,
    // 直方图
    histogramWidth = 140.dp,
    histogramHeight = 84.dp,
    // 覆盖层控件
    overlayButtonSize = 44.dp,
    overlayIconSize = 22.dp,
    overlayLabelHeight = 28.dp,
    // 通用尺寸
    iconSizeSmall = 20.dp,
    iconSizeMedium = 26.dp,
    iconSizeLarge = 32.dp
)

/**
 * 平板尺寸配置
 * 适用于屏幕宽度 >= 600dp 的设备
 */
val TabletDimens = ResponsiveDimens(
    screenCategory = ScreenCategory.TABLET,
    // 快门按钮 - 平板尺寸
    shutterButtonSize = 88.dp,
    shutterInnerSize = 72.dp,
    shutterStrokeWidth = 6.dp,
    // 底部控件 - 平板布局
    bottomBarHeight = 160.dp,
    bottomBarPadding = PaddingValues(horizontal = 32.dp, vertical = 20.dp),
    bottomControlSpacing = 40.dp,
    // 模式选择器 - 平板布局
    modeSelectorHeight = 48.dp,
    modeItemMinWidth = 80.dp,
    modeItemPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
    modeIndicatorHeight = 4.dp,
    // 顶部栏 - 平板布局
    topBarHeight = 64.dp,
    topBarIconSize = 28.dp,
    topBarPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    // 滤镜选择器 - 平板布局
    filterItemSize = 80.dp,
    filterListHeight = 130.dp,
    filterListPadding = 20.dp,
    filterItemSpacing = 16.dp,
    // 对焦指示器
    focusIndicatorSize = 80.dp,
    focusCornerLength = 18.dp,
    focusStrokeWidth = 3.dp,
    // 变焦控制
    zoomSliderHeight = 240.dp,
    zoomSliderWidth = 52.dp,
    zoomButtonSize = 48.dp,
    // 设置面板
    settingsPanelMaxWidth = 400.dp,
    settingsItemHeight = 56.dp,
    settingsIconSize = 28.dp,
    // 直方图
    histogramWidth = 160.dp,
    histogramHeight = 96.dp,
    // 覆盖层控件
    overlayButtonSize = 52.dp,
    overlayIconSize = 26.dp,
    overlayLabelHeight = 32.dp,
    // 通用尺寸
    iconSizeSmall = 22.dp,
    iconSizeMedium = 28.dp,
    iconSizeLarge = 36.dp
)

// ==================== Composable 辅助函数 ====================

/**
 * 获取当前屏幕的响应式尺寸
 *
 * 根据设备屏幕宽度自动选择合适的尺寸配置
 * 使用 remember 缓存结果，避免重复计算
 *
 * @return ResponsiveDimens 当前屏幕对应的尺寸配置
 */
@Composable
fun rememberResponsiveDimens(): ResponsiveDimens {
    val configuration = LocalConfiguration.current                              // 获取当前配置
    val screenWidthDp = configuration.screenWidthDp.dp                          // 获取屏幕宽度

    return remember(screenWidthDp) {                                            // 缓存计算结果
        when {
            screenWidthDp < 360.dp -> SmallScreenDimens                         // 小屏手机
            screenWidthDp < 412.dp -> MediumScreenDimens                        // 标准手机
            screenWidthDp < 600.dp -> LargeScreenDimens                         // 大屏手机
            else -> TabletDimens                                                 // 平板设备
        }
    }
}

/**
 * 获取当前屏幕类别
 *
 * @return ScreenCategory 当前屏幕类别
 */
@Composable
fun rememberScreenCategory(): ScreenCategory {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp

    return remember(screenWidthDp) {
        when {
            screenWidthDp < 360.dp -> ScreenCategory.SMALL
            screenWidthDp < 412.dp -> ScreenCategory.MEDIUM
            screenWidthDp < 600.dp -> ScreenCategory.LARGE
            else -> ScreenCategory.TABLET
        }
    }
}

/**
 * 判断当前是否为横屏模式
 *
 * @return Boolean 是否为横屏
 */
@Composable
fun isLandscape(): Boolean {
    val configuration = LocalConfiguration.current
    return configuration.screenWidthDp > configuration.screenHeightDp
}

/**
 * 判断当前是否为平板设备
 *
 * @return Boolean 是否为平板
 */
@Composable
fun isTablet(): Boolean {
    return rememberScreenCategory() == ScreenCategory.TABLET
}

// ==================== CompositionLocal ====================

/**
 * CompositionLocal 用于在组合树中提供 ResponsiveDimens
 * 允许子组件直接访问尺寸配置而无需参数传递
 */
val LocalResponsiveDimens = staticCompositionLocalOf { MediumScreenDimens }
