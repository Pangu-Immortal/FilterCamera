/**
 * ProModeState.kt - 专业模式状态管理
 *
 * 独立管理专业模式（PRO Mode）的状态和逻辑
 * 从CameraViewModel抽取，降低ViewModel复杂度
 *
 * 包含功能：
 * - ISO感光度控制
 * - 快门速度控制
 * - 曝光补偿控制
 * - 白平衡模式选择
 * - 对焦模式和手动对焦距离
 * - 面板显示状态
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.presentation.camera.state

import android.util.Log
import com.qihao.filtercamera.domain.model.FocusMode
import com.qihao.filtercamera.domain.model.ProModeSettings
import com.qihao.filtercamera.domain.model.WhiteBalanceMode
import com.qihao.filtercamera.domain.usecase.CameraUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 专业模式UI状态
 *
 * @param settings 专业模式参数设置
 * @param isPanelVisible 控制面板是否可见
 * @param currentParameterType 当前选中的参数类型（用于高亮显示）
 */
data class ProModeUiState(
    val settings: ProModeSettings = ProModeSettings(),
    val isPanelVisible: Boolean = false,
    val currentParameterType: ProParameterType = ProParameterType.ISO,
    val exposureRange: Triple<Int, Int, Float> = Triple(-12, 12, 1f / 3f)  // 曝光补偿范围
)

/**
 * 专业模式参数类型
 */
enum class ProParameterType(val displayName: String) {
    ISO("ISO"),
    SHUTTER("快门"),
    EV("曝光补偿"),
    WB("白平衡"),
    FOCUS("对焦")
}

/**
 * 专业模式状态管理器
 *
 * @param useCase 相机用例
 * @param scope 协程作用域
 * @param onError 错误回调
 */
class ProModeStateHolder(
    private val useCase: CameraUseCase,
    private val scope: CoroutineScope,
    private val onError: suspend (String) -> Unit
) {
    companion object {
        private const val TAG = "ProModeStateHolder"
    }

    // UI状态
    private val _state = MutableStateFlow(ProModeUiState())
    val state: StateFlow<ProModeUiState> = _state.asStateFlow()

    // 快捷访问
    val settings: ProModeSettings get() = _state.value.settings
    val isPanelVisible: Boolean get() = _state.value.isPanelVisible

    init {
        Log.d(TAG, "init: 专业模式状态管理器初始化")
        // 获取曝光补偿范围
        val range = useCase.getExposureCompensationRange()
        _state.update { it.copy(exposureRange = range) }
        Log.d(TAG, "init: 曝光补偿范围 min=${range.first}, max=${range.second}, step=${range.third}")
    }

    // ==================== 面板控制 ====================

    /**
     * 切换面板可见性
     */
    fun togglePanel() {
        val isVisible = !_state.value.isPanelVisible
        Log.d(TAG, "togglePanel: isVisible=$isVisible")
        _state.update { it.copy(isPanelVisible = isVisible) }
    }

    /**
     * 显示面板
     */
    fun showPanel() {
        Log.d(TAG, "showPanel")
        _state.update { it.copy(isPanelVisible = true) }
    }

    /**
     * 隐藏面板
     */
    fun hidePanel() {
        Log.d(TAG, "hidePanel")
        _state.update { it.copy(isPanelVisible = false) }
    }

    /**
     * 选择参数类型
     */
    fun selectParameter(type: ProParameterType) {
        Log.d(TAG, "selectParameter: type=${type.displayName}")
        _state.update { it.copy(currentParameterType = type) }
    }

    // ==================== ISO控制 ====================

    /**
     * 设置ISO
     * @param iso ISO值，null表示自动
     */
    fun setIso(iso: Int?) {
        Log.d(TAG, "setIso: iso=$iso")
        _state.update { it.copy(settings = it.settings.copy(iso = iso)) }
        scope.launch {
            useCase.setIso(iso).onFailure { error ->
                Log.e(TAG, "setIso: 设置失败", error)
                onError("ISO设置失败: ${error.message}")
            }
        }
    }

    /**
     * 重置ISO为自动
     */
    fun resetIso() {
        Log.d(TAG, "resetIso: 重置为自动")
        setIso(null)
    }

    // ==================== 快门控制 ====================

    /**
     * 设置快门速度
     * @param speed 快门速度，null表示自动
     */
    fun setShutterSpeed(speed: Float?) {
        Log.d(TAG, "setShutterSpeed: speed=$speed")
        _state.update { it.copy(settings = it.settings.copy(shutterSpeed = speed)) }
        // 快门速度通过曝光补偿间接影响
    }

    // ==================== 曝光补偿控制 ====================

    /**
     * 设置曝光补偿
     * @param ev 曝光补偿值（EV）
     */
    fun setExposureCompensation(ev: Float) {
        Log.d(TAG, "setExposureCompensation: ev=$ev")
        _state.update { it.copy(settings = it.settings.copy(exposureCompensation = ev)) }
        scope.launch {
            // 将EV值转换为索引（假设步进为1/3 EV）
            val step = _state.value.exposureRange.third
            val evIndex = (ev / step).toInt()
            useCase.setExposureCompensation(evIndex).onFailure { error ->
                Log.e(TAG, "setExposureCompensation: 设置失败", error)
                onError("曝光补偿设置失败: ${error.message}")
            }
        }
    }

    /**
     * 重置曝光补偿
     */
    fun resetExposureCompensation() {
        Log.d(TAG, "resetExposureCompensation: 重置为0")
        setExposureCompensation(0f)
    }

    // ==================== 白平衡控制 ====================

    /**
     * 设置白平衡模式
     */
    fun setWhiteBalance(mode: WhiteBalanceMode) {
        Log.d(TAG, "setWhiteBalance: mode=${mode.displayName}")
        _state.update { it.copy(settings = it.settings.copy(whiteBalance = mode)) }
        scope.launch {
            useCase.setWhiteBalance(mode).onFailure { error ->
                Log.e(TAG, "setWhiteBalance: 设置失败", error)
                onError("白平衡设置失败: ${error.message}")
            }
        }
    }

    /**
     * 重置白平衡为自动
     */
    fun resetWhiteBalance() {
        Log.d(TAG, "resetWhiteBalance: 重置为自动")
        setWhiteBalance(WhiteBalanceMode.AUTO)
    }

    // ==================== 对焦控制 ====================

    /**
     * 设置对焦模式
     */
    fun setFocusMode(mode: FocusMode) {
        Log.d(TAG, "setFocusMode: mode=${mode.displayName}")
        _state.update { it.copy(settings = it.settings.copy(focusMode = mode)) }
        scope.launch {
            useCase.setFocusMode(mode).onFailure { error ->
                Log.e(TAG, "setFocusMode: 设置失败", error)
                onError("对焦模式设置失败: ${error.message}")
            }
        }
    }

    /**
     * 设置手动对焦距离
     * @param distance 对焦距离（0.0=最近，1.0=无穷远）
     */
    fun setFocusDistance(distance: Float) {
        Log.d(TAG, "setFocusDistance: distance=$distance")
        _state.update { it.copy(settings = it.settings.copy(focusDistance = distance)) }
        scope.launch {
            useCase.setFocusDistance(distance).onFailure { error ->
                Log.e(TAG, "setFocusDistance: 设置失败", error)
                onError("对焦距离设置失败: ${error.message}")
            }
        }
    }

    // ==================== 重置 ====================

    /**
     * 重置所有专业模式参数
     */
    fun resetAll() {
        Log.d(TAG, "resetAll: 重置所有参数")
        _state.update { it.copy(settings = ProModeSettings()) }
        scope.launch {
            // 批量重置
            useCase.setIso(null)
            useCase.setExposureCompensation(0)
            useCase.setWhiteBalance(WhiteBalanceMode.AUTO)
            useCase.setFocusMode(FocusMode.CONTINUOUS)
        }
    }

    /**
     * 获取当前设置的摘要（用于显示）
     */
    fun getSettingsSummary(): String {
        val s = _state.value.settings
        return buildString {
            append("ISO: ${s.iso ?: "自动"}")
            append(" | EV: ${if (s.exposureCompensation >= 0) "+" else ""}${s.exposureCompensation}")
            append(" | WB: ${s.whiteBalance.displayName}")
            append(" | AF: ${s.focusMode.displayName}")
        }
    }
}
