/**
 * PopupStateHolder.kt - 弹窗状态统一管理
 *
 * 解决多个弹窗同时显示导致的UI冲突问题
 * 实现互斥逻辑：同时只能显示一个弹窗
 *
 * 设计原则：
 * - 单一职责：仅管理弹窗可见性
 * - 互斥逻辑：显示新弹窗自动关闭其他弹窗
 * - 可观察：使用StateFlow便于Compose订阅
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.presentation.camera

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 弹窗类型枚举
 *
 * 定义所有可能的弹窗类型
 * 使用密封类确保类型安全
 */
sealed class PopupType {
    /** 无弹窗 */
    object None : PopupType() {
        override fun toString(): String = "None"
    }

    /** 滤镜选择器 */
    object FilterSelector : PopupType() {
        override fun toString(): String = "FilterSelector"
    }

    /** 专业模式控制面板 */
    object ProPanel : PopupType() {
        override fun toString(): String = "ProPanel"
    }

    /** 变焦滑块 */
    object ZoomSlider : PopupType() {
        override fun toString(): String = "ZoomSlider"
    }

    /** 设置面板 */
    object SettingsPanel : PopupType() {
        override fun toString(): String = "SettingsPanel"
    }

    /** 文档扫描模式选择器 */
    object DocumentScanMode : PopupType() {
        override fun toString(): String = "DocumentScanMode"
    }

    /** 人像模式覆盖层（美颜/虚化控制） */
    object PortraitOverlay : PopupType() {
        override fun toString(): String = "PortraitOverlay"
    }

    /** 模式菜单（新增：整合HDR/定时器/画幅/滤镜） */
    object ModeMenu : PopupType() {
        override fun toString(): String = "ModeMenu"
    }
}

/**
 * 弹窗状态数据类
 *
 * @param activePopup 当前活动的弹窗类型
 * @param previousPopup 上一个弹窗（用于返回逻辑）
 */
data class PopupState(
    val activePopup: PopupType = PopupType.None,
    val previousPopup: PopupType = PopupType.None
)

/**
 * 弹窗状态管理器
 *
 * 提供统一的弹窗显示/隐藏控制
 * 确保同时只有一个弹窗可见
 */
@Singleton
class PopupStateHolder @Inject constructor() {

    companion object {
        private const val TAG = "PopupStateHolder"        // 日志标签
    }

    // 内部状态
    private val _popupState = MutableStateFlow(PopupState())

    /** 弹窗状态（只读） */
    val popupState: StateFlow<PopupState> = _popupState.asStateFlow()

    /** 当前活动弹窗类型 */
    val activePopup: PopupType
        get() = _popupState.value.activePopup

    // ==================== 便捷属性 ====================

    /** 滤镜选择器是否可见 */
    val isFilterSelectorVisible: Boolean
        get() = _popupState.value.activePopup == PopupType.FilterSelector

    /** 专业模式面板是否可见 */
    val isProPanelVisible: Boolean
        get() = _popupState.value.activePopup == PopupType.ProPanel

    /** 变焦滑块是否可见 */
    val isZoomSliderVisible: Boolean
        get() = _popupState.value.activePopup == PopupType.ZoomSlider

    /** 设置面板是否展开 */
    val isSettingsPanelExpanded: Boolean
        get() = _popupState.value.activePopup == PopupType.SettingsPanel

    /** 文档扫描模式选择器是否可见 */
    val isDocumentScanModeSelectorVisible: Boolean
        get() = _popupState.value.activePopup == PopupType.DocumentScanMode

    /** 人像模式覆盖层是否可见 */
    val isPortraitOverlayVisible: Boolean
        get() = _popupState.value.activePopup == PopupType.PortraitOverlay

    /** 模式菜单是否可见 */
    val isModeMenuVisible: Boolean
        get() = _popupState.value.activePopup == PopupType.ModeMenu

    /** 是否有任何弹窗可见 */
    val hasActivePopup: Boolean
        get() = _popupState.value.activePopup != PopupType.None

    // ==================== 核心方法 ====================

    /**
     * 显示指定弹窗（互斥：自动关闭其他弹窗）
     *
     * @param popup 要显示的弹窗类型
     */
    fun show(popup: PopupType) {
        Log.d(TAG, "show: $popup")
        _popupState.update { current ->
            current.copy(
                previousPopup = current.activePopup,
                activePopup = popup
            )
        }
    }

    /**
     * 隐藏当前弹窗
     */
    fun hide() {
        Log.d(TAG, "hide: current=${_popupState.value.activePopup}")
        _popupState.update { current ->
            current.copy(
                previousPopup = current.activePopup,
                activePopup = PopupType.None
            )
        }
    }

    /**
     * 隐藏所有弹窗（与hide相同，语义更清晰）
     */
    fun hideAll() {
        hide()
    }

    /**
     * 切换弹窗可见性
     *
     * 如果当前弹窗是指定类型则隐藏，否则显示
     *
     * @param popup 要切换的弹窗类型
     */
    fun toggle(popup: PopupType) {
        Log.d(TAG, "toggle: $popup, current=${_popupState.value.activePopup}")
        if (_popupState.value.activePopup == popup) {
            hide()
        } else {
            show(popup)
        }
    }

    /**
     * 返回上一个弹窗
     *
     * 如果没有上一个弹窗则隐藏所有
     */
    fun back() {
        Log.d(TAG, "back: previous=${_popupState.value.previousPopup}")
        _popupState.update { current ->
            if (current.previousPopup != PopupType.None) {
                current.copy(
                    activePopup = current.previousPopup,
                    previousPopup = PopupType.None
                )
            } else {
                current.copy(
                    activePopup = PopupType.None,
                    previousPopup = PopupType.None
                )
            }
        }
    }

    // ==================== 便捷切换方法 ====================

    /** 切换滤镜选择器 */
    fun toggleFilterSelector() = toggle(PopupType.FilterSelector)

    /** 切换专业模式面板 */
    fun toggleProPanel() = toggle(PopupType.ProPanel)

    /** 切换变焦滑块 */
    fun toggleZoomSlider() = toggle(PopupType.ZoomSlider)

    /** 切换设置面板 */
    fun toggleSettingsPanel() = toggle(PopupType.SettingsPanel)

    /** 切换文档扫描模式选择器 */
    fun toggleDocumentScanModeSelector() = toggle(PopupType.DocumentScanMode)

    /** 切换人像模式覆盖层 */
    fun togglePortraitOverlay() = toggle(PopupType.PortraitOverlay)

    /** 切换模式菜单 */
    fun toggleModeMenu() = toggle(PopupType.ModeMenu)

    // ==================== 显示方法（语义化） ====================

    /** 显示滤镜选择器 */
    fun showFilterSelector() = show(PopupType.FilterSelector)

    /** 显示专业模式面板 */
    fun showProPanel() = show(PopupType.ProPanel)

    /** 显示变焦滑块 */
    fun showZoomSlider() = show(PopupType.ZoomSlider)

    /** 显示设置面板 */
    fun showSettingsPanel() = show(PopupType.SettingsPanel)

    /** 显示人像模式覆盖层 */
    fun showPortraitOverlay() = show(PopupType.PortraitOverlay)

    /** 显示模式菜单 */
    fun showModeMenu() = show(PopupType.ModeMenu)
}
