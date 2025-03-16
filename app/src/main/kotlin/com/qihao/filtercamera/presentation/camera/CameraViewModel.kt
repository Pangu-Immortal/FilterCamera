/**
 * CameraViewModel.kt - 相机ViewModel
 *
 * 管理相机页面的状态和业务逻辑
 * 使用统一的CameraUseCase处理所有相机操作
 * 支持人像/文档/专业模式的特殊处理
 *
 * 架构重构（v2.0.0）：
 * - 使用ProModeStateHolder管理专业模式状态
 * - 使用FilterSelectorStateHolder管理滤镜选择器状态
 * - 降低ViewModel复杂度，提高可测试性
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.presentation.camera

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qihao.filtercamera.data.processor.DocumentScanProcessor
import com.qihao.filtercamera.data.processor.FaceDetectionProcessor
import com.qihao.filtercamera.domain.model.AdaptiveFocusMode
import com.qihao.filtercamera.domain.model.ApertureMode
import com.qihao.filtercamera.domain.model.AspectRatio
import com.qihao.filtercamera.domain.model.BeautyLevel
import com.qihao.filtercamera.domain.model.CameraAdvancedSettings
import com.qihao.filtercamera.domain.model.CameraEvent
import com.qihao.filtercamera.domain.model.CameraLens
import com.qihao.filtercamera.domain.model.CameraMode
import com.qihao.filtercamera.domain.model.CameraState
import com.qihao.filtercamera.domain.model.FilterGroup
import com.qihao.filtercamera.domain.model.FilterType
import com.qihao.filtercamera.domain.model.FlashMode
import com.qihao.filtercamera.domain.model.FocusMode
import com.qihao.filtercamera.domain.model.HdrMode
import com.qihao.filtercamera.domain.model.MacroMode
import com.qihao.filtercamera.domain.model.ProModeSettings
import com.qihao.filtercamera.domain.model.TimerMode
import com.qihao.filtercamera.domain.model.WhiteBalanceMode
import com.qihao.filtercamera.domain.model.ZoomConfig
import com.qihao.filtercamera.domain.usecase.CameraUseCase
import com.qihao.filtercamera.presentation.camera.components.HistogramCalculator
import com.qihao.filtercamera.presentation.camera.components.HistogramData
import com.qihao.filtercamera.presentation.camera.state.FilterSelectorStateHolder
import com.qihao.filtercamera.presentation.camera.state.FilterSelectorUiState
import com.qihao.filtercamera.presentation.camera.state.ProModeStateHolder
import com.qihao.filtercamera.presentation.camera.state.ProModeUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "CameraViewModel"  // 日志标签

/**
 * 相机ViewModel
 *
 * @param useCase 统一相机用例 - 处理所有相机操作
 * @param faceDetectionProcessor 人脸检测处理器（人像模式）
 * @param documentScanProcessor 文档扫描处理器（文档模式）
 */
@HiltViewModel
class CameraViewModel @Inject constructor(
    private val useCase: CameraUseCase,
    private val faceDetectionProcessor: FaceDetectionProcessor,
    private val documentScanProcessor: DocumentScanProcessor
) : ViewModel() {

    // ==================== 核心UI状态 ====================

    // UI状态
    private val _uiState = MutableStateFlow(CameraState())
    val uiState: StateFlow<CameraState> = _uiState.asStateFlow()

    // 单次事件（拍照完成、录像完成、错误等）
    private val _events = MutableSharedFlow<CameraEvent>()
    val events: SharedFlow<CameraEvent> = _events.asSharedFlow()

    // 滤镜帧流（用于实时预览）
    val filteredFrame: StateFlow<Bitmap?> = MutableStateFlow<Bitmap?>(null)

    // 直方图数据流（用于专业模式实时显示）
    private val _histogramData = MutableStateFlow(HistogramData())
    val histogramData: StateFlow<HistogramData> = _histogramData.asStateFlow()

    // ==================== 状态管理器（重构抽取） ====================

    /**
     * 专业模式状态管理器
     * 管理ISO、快门、曝光补偿、白平衡、对焦等参数
     */
    val proModeState: ProModeStateHolder = ProModeStateHolder(
        useCase = useCase,
        scope = viewModelScope,
        onError = { message -> _events.emit(CameraEvent.Error(message)) }
    )

    /**
     * 滤镜选择器状态管理器
     * 管理滤镜选择、分组切换、缩略图生成
     */
    val filterSelectorState: FilterSelectorStateHolder = FilterSelectorStateHolder(
        useCase = useCase,
        scope = viewModelScope,
        onFilterChanged = { filterType ->
            _uiState.update { it.copy(filterType = filterType) }
        }
    )

    // 专业模式UI状态（便于UI层直接观察）
    val proModeUiState: StateFlow<ProModeUiState> = proModeState.state

    // 滤镜选择器UI状态（便于UI层直接观察）
    val filterSelectorUiState: StateFlow<FilterSelectorUiState> = filterSelectorState.state

    // ==================== 兼容性属性（保持向后兼容） ====================

    // 滤镜预览缩略图缓存（委托给状态管理器）
    val filterThumbnails: StateFlow<Map<FilterType, Bitmap?>> get() =
        MutableStateFlow(filterSelectorState.thumbnails)

    // 相册最新照片缩略图（小米风格左下角显示）
    private val _galleryThumbnail = MutableStateFlow<Bitmap?>(null)
    val galleryThumbnail: StateFlow<Bitmap?> = _galleryThumbnail.asStateFlow()

    // 当前预览帧（用于生成滤镜缩略图）
    private var currentPreviewFrame: Bitmap? = null

    // 定时拍照协程Job（用于取消倒计时）
    private var timerJob: kotlinx.coroutines.Job? = null

    // 可用滤镜分组（委托给状态管理器）
    val availableGroups: List<FilterGroup> get() = filterSelectorState.availableGroups

    // 可用滤镜列表（委托给状态管理器）
    val availableFilters: List<FilterType> get() = filterSelectorState.availableFilters

    // ==================== 初始化 ====================

    init {
        Log.d(TAG, "init: CameraViewModel初始化（使用状态管理器架构）")
        observeFilterChanges()                                        // 观察滤镜变化
        observeFilteredFrame()                                        // 观察滤镜帧
        observeRawPreviewFrame()                                      // 观察原始预览帧（用于缩略图生成）
        observeFaceDetection()                                        // 观察人脸检测结果
        observeDocumentBounds()                                       // 观察文档边界检测结果
        observeZoomRange()                                            // 观察变焦范围
        observeStateHolders()                                         // 观察状态管理器变化
    }

    /**
     * 观察变焦范围变化
     */
    private fun observeZoomRange() = viewModelScope.launch {
        useCase.getZoomRange().collect { range ->
            Log.d(TAG, "observeZoomRange: 变焦范围 min=${range.minZoom}, max=${range.maxZoom}")
            _uiState.update { state ->
                state.copy(
                    advancedSettings = state.advancedSettings.copy(
                        zoomLevel = state.advancedSettings.zoomLevel.coerceIn(range.minZoom, range.maxZoom)
                    ),
                    zoomRange = range
                )
            }
        }
    }

    /**
     * 观察状态管理器变化
     * 将状态管理器的状态同步到CameraState（保持向后兼容）
     */
    private fun observeStateHolders() {
        // 观察专业模式状态变化
        viewModelScope.launch {
            proModeState.state.collect { proState ->
                _uiState.update { state ->
                    state.copy(
                        isProPanelVisible = proState.isPanelVisible,
                        proSettings = proState.settings
                    )
                }
            }
        }

        // 观察滤镜选择器状态变化
        viewModelScope.launch {
            filterSelectorState.state.collect { filterState ->
                _uiState.update { state ->
                    state.copy(
                        isFilterSelectorVisible = filterState.isPanelVisible,
                        selectedFilterGroup = filterState.selectedGroup,
                        filterType = filterState.selectedFilter
                    )
                }
            }
        }
    }

    /**
     * 观察滤镜状态变化
     */
    private fun observeFilterChanges() = viewModelScope.launch {
        useCase.currentFilter().collect { filterType ->
            _uiState.update { it.copy(filterType = filterType) }
        }
    }

    /**
     * 观察滤镜帧变化（用于实时预览）
     */
    private fun observeFilteredFrame() = viewModelScope.launch {
        useCase.filteredFrame().collect { bitmap ->
            (filteredFrame as MutableStateFlow).value = bitmap

            // 如果是人像模式，对预览帧进行人脸检测
            if (_uiState.value.mode == CameraMode.PORTRAIT && bitmap != null) {
                val isFront = _uiState.value.lens == CameraLens.FRONT
                faceDetectionProcessor.processBitmap(bitmap, 0, isFront)
            }

            // 如果是文档模式，对预览帧进行边缘检测
            if (_uiState.value.mode == CameraMode.DOCUMENT && bitmap != null) {
                documentScanProcessor.processBitmap(bitmap)
            }
        }
    }

    /**
     * 观察原始预览帧变化（用于生成滤镜缩略图和直方图）
     *
     * 当首次获取到原始预览帧时，自动生成当前分组的滤镜缩略图
     * 如果直方图可见，同时计算直方图数据
     */
    private fun observeRawPreviewFrame() = viewModelScope.launch {
        useCase.rawPreviewFrame().collect { bitmap ->
            if (bitmap != null) {
                // 更新当前预览帧
                currentPreviewFrame = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)

                // 更新滤镜选择器状态管理器
                filterSelectorState.updatePreviewFrame(bitmap)

                // 如果直方图可见，计算直方图数据（使用采样提高性能）
                if (_uiState.value.isHistogramVisible) {
                    val histData = HistogramCalculator.calculateSampled(bitmap, 4)
                    _histogramData.value = histData
                }

                // 如果是人像模式，对原始帧进行人脸检测
                if (_uiState.value.mode == CameraMode.PORTRAIT) {
                    val isFront = _uiState.value.lens == CameraLens.FRONT
                    faceDetectionProcessor.processBitmap(bitmap, 0, isFront)
                }

                // 如果是文档模式，对原始帧进行边缘检测
                if (_uiState.value.mode == CameraMode.DOCUMENT) {
                    documentScanProcessor.processBitmap(bitmap)
                }
            }
        }
    }

    /**
     * 观察人脸检测结果（人像模式）
     */
    private fun observeFaceDetection() = viewModelScope.launch {
        faceDetectionProcessor.detectedFaces.collect { faces ->
            _uiState.update { it.copy(detectedFaces = faces) }
        }
    }

    /**
     * 观察文档边界检测结果（文档模式）
     */
    private fun observeDocumentBounds() = viewModelScope.launch {
        documentScanProcessor.documentBounds.collect { bounds ->
            _uiState.update { it.copy(documentBounds = bounds) }
        }
    }

    /**
     * 生成所有滤镜的预览缩略图
     * 委托给FilterSelectorStateHolder
     */
    fun generateFilterThumbnails() {
        val sourceFrame = currentPreviewFrame ?: return
        Log.d(TAG, "generateFilterThumbnails: 委托给状态管理器")
        filterSelectorState.generateThumbnails(sourceFrame)
    }

    /**
     * 更新预览帧（供外部调用）
     * 同时更新状态管理器
     */
    fun updatePreviewFrame(bitmap: Bitmap) {
        currentPreviewFrame = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
        filterSelectorState.updatePreviewFrame(bitmap)
    }

    // ==================== 相机绑定 ====================

    /**
     * 绑定相机到生命周期和预览视图
     * @param owner 生命周期持有者
     * @param previewView 预览视图
     */
    fun bindCamera(owner: LifecycleOwner, previewView: PreviewView) = viewModelScope.launch {
        try {
            Log.d(TAG, "bindCamera: 开始绑定相机")
            useCase.bindCamera(owner, previewView)
            Log.d(TAG, "bindCamera: 相机绑定成功")
        } catch (e: Exception) {
            Log.e(TAG, "bindCamera: 相机绑定失败", e)
            _events.emit(CameraEvent.Error(e.message ?: "相机绑定失败"))
        }
    }

    // ==================== 拍照/录像 ====================

    /**
     * 拍照
     *
     * 小米风格：
     * 1. 检查定时拍照模式
     * 2. 如果启用定时，开始倒计时
     * 3. 倒计时结束后执行实际拍照
     * 4. 如果未启用定时，直接拍照
     *
     * 文档模式：额外应用扫描效果
     */
    fun takePhoto() {
        if (_uiState.value.isCapturing || _uiState.value.isCountingDown) {
            Log.w(TAG, "takePhoto: 正在拍照或倒计时中，忽略请求")
            return
        }

        val timerMode = _uiState.value.timerMode
        if (TimerMode.isTimerEnabled(timerMode)) {
            // 启用定时拍照，开始倒计时
            Log.d(TAG, "takePhoto: 启动定时拍照 mode=${timerMode.displayName}")
            startCountdown(timerMode.seconds)
        } else {
            // 直接拍照
            executePhoto()
        }
    }

    /**
     * 启动倒计时
     *
     * @param seconds 倒计时秒数
     */
    private fun startCountdown(seconds: Int) {
        // 取消之前的倒计时（如果有）
        timerJob?.cancel()

        timerJob = viewModelScope.launch {
            Log.d(TAG, "startCountdown: 开始倒计时 seconds=$seconds")
            _uiState.update { it.copy(isCountingDown = true, countdownSeconds = seconds) }

            // 倒计时循环
            for (remaining in seconds downTo 1) {
                _uiState.update { it.copy(countdownSeconds = remaining) }
                Log.d(TAG, "startCountdown: 剩余 $remaining 秒")
                kotlinx.coroutines.delay(1000)  // 每秒更新
            }

            // 倒计时结束，执行拍照
            _uiState.update { it.copy(isCountingDown = false, countdownSeconds = 0) }
            executePhoto()
        }
    }

    /**
     * 取消倒计时
     */
    fun cancelCountdown() {
        Log.d(TAG, "cancelCountdown: 取消倒计时")
        timerJob?.cancel()
        timerJob = null
        _uiState.update { it.copy(isCountingDown = false, countdownSeconds = 0) }
    }

    /**
     * 执行实际拍照操作
     *
     * 小米风格：
     * 1. 关闭所有弹窗
     * 2. 显示黑色闪屏
     * 3. 拍照保存
     * 4. 更新相册缩略图
     * 5. 隐藏闪屏
     */
    private fun executePhoto() {
        viewModelScope.launch {
            val mode = _uiState.value.mode
            Log.d(TAG, "executePhoto: 开始拍照 mode=$mode")

            // 关闭所有弹窗，显示拍照闪屏
            _uiState.update {
                it.copy(
                    isCapturing = true,
                    isCaptureFlashVisible = true,
                    isSettingsPanelExpanded = false,
                    isFilterSelectorVisible = false
                )
            }

            // 延迟一点隐藏闪屏（让用户看到闪屏效果）
            kotlinx.coroutines.delay(100)
            _uiState.update { it.copy(isCaptureFlashVisible = false) }

            useCase.takePhoto()
                .onSuccess { uri ->
                    Log.d(TAG, "executePhoto: 拍照成功 uri=$uri")
                    _events.emit(CameraEvent.PhotoCaptured(uri.toString()))
                    // 更新相册缩略图（使用当前预览帧作为临时缩略图）
                    currentPreviewFrame?.let { frame ->
                        val thumbnail = Bitmap.createScaledBitmap(frame, 100, 100, true)
                        _galleryThumbnail.value = thumbnail
                    }
                }
                .onFailure { error ->
                    Log.e(TAG, "executePhoto: 拍照失败", error)
                    _events.emit(CameraEvent.Error(error.message ?: "拍照失败"))
                }
            _uiState.update { it.copy(isCapturing = false) }
        }
    }

    /**
     * 设置定时拍照模式
     *
     * @param mode 定时模式（OFF/3s/5s/10s）
     */
    fun setTimerMode(mode: TimerMode) {
        Log.d(TAG, "setTimerMode: 设置定时模式 mode=${mode.displayName}")
        _uiState.update { it.copy(timerMode = mode) }
    }

    /**
     * 循环切换定时拍照模式
     * OFF -> 3S -> 5S -> 10S -> OFF
     */
    fun toggleTimerMode() {
        val currentMode = _uiState.value.timerMode
        val nextMode = with(TimerMode.Companion) { currentMode.next() }
        Log.d(TAG, "toggleTimerMode: 切换定时模式 ${currentMode.displayName} -> ${nextMode.displayName}")
        setTimerMode(nextMode)
    }

    /**
     * 切换录像状态
     */
    fun toggleRecording() = viewModelScope.launch {
        if (_uiState.value.isRecording) stopRecording() else startRecording()
    }

    private suspend fun startRecording() {
        Log.d(TAG, "startRecording: 开始录像")
        useCase.startRecording()
            .onSuccess { _uiState.update { it.copy(isRecording = true) } }
            .onFailure { error ->
                Log.e(TAG, "startRecording: 开始录像失败", error)
                _events.emit(CameraEvent.Error(error.message ?: "开始录像失败"))
            }
    }

    private suspend fun stopRecording() {
        Log.d(TAG, "stopRecording: 停止录像")
        useCase.stopRecording()
            .onSuccess { uri ->
                Log.d(TAG, "stopRecording: 录像保存成功 uri=$uri")
                _uiState.update { it.copy(isRecording = false) }
                _events.emit(CameraEvent.VideoRecorded(uri.toString()))
            }
            .onFailure { error ->
                Log.e(TAG, "stopRecording: 停止录像失败", error)
                _uiState.update { it.copy(isRecording = false) }
                _events.emit(CameraEvent.Error(error.message ?: "停止录像失败"))
            }
    }

    // ==================== 摄像头切换 ====================

    /**
     * 切换摄像头（前后切换）
     * 同时关闭所有弹窗
     */
    fun switchCamera() = viewModelScope.launch {
        Log.d(TAG, "switchCamera: 切换摄像头")
        // 如果正在倒计时，先取消
        if (_uiState.value.isCountingDown) {
            cancelCountdown()
            Log.d(TAG, "switchCamera: 已取消定时拍照倒计时")
        }
        // 关闭所有弹窗
        _uiState.update {
            it.copy(
                isSettingsPanelExpanded = false,
                isFilterSelectorVisible = false
            )
        }
        useCase.toggleCamera()
            .onSuccess {
                val newLens = if (_uiState.value.lens == CameraLens.BACK)
                    CameraLens.FRONT else CameraLens.BACK
                _uiState.update { it.copy(lens = newLens) }
                _events.emit(CameraEvent.CameraSwitched)
                Log.d(TAG, "switchCamera: 切换成功 newLens=$newLens")
            }
            .onFailure { error ->
                Log.e(TAG, "switchCamera: 切换失败", error)
                _events.emit(CameraEvent.Error(error.message ?: "切换摄像头失败"))
            }
    }

    // ==================== 模式切换 ====================

    /**
     * 选择相机模式（小米风格：支持5种模式）
     *
     * 切换模式时会启用/禁用对应的处理器
     * 同时关闭所有非相关弹窗（设置面板、滤镜选择器、变焦滑块）
     *
     * 特殊处理：PRO模式下再次点击PRO标签会切换面板显示
     *
     * @param mode 目标模式
     */
    fun selectMode(mode: CameraMode) {
        val oldMode = _uiState.value.mode

        // PRO模式特殊处理：再次点击PRO标签切换面板
        if (mode == CameraMode.PRO && oldMode == CameraMode.PRO) {
            Log.d(TAG, "selectMode: PRO模式下再次点击，切换面板")
            toggleProPanel()
            return
        }

        Log.d(TAG, "selectMode: 切换模式 $oldMode -> ${mode.displayName}")

        // 如果正在倒计时，先取消
        if (_uiState.value.isCountingDown) {
            cancelCountdown()
            Log.d(TAG, "selectMode: 已取消定时拍照倒计时")
        }

        // 切换模式时关闭设置面板和滤镜选择器
        _uiState.update {
            it.copy(
                isSettingsPanelExpanded = false,
                isFilterSelectorVisible = false
            )
        }

        // 禁用旧模式的处理器
        when (oldMode) {
            CameraMode.PORTRAIT -> {
                faceDetectionProcessor.disable()
                _uiState.update { it.copy(detectedFaces = emptyList()) }
            }
            CameraMode.DOCUMENT -> {
                documentScanProcessor.disable()
                _uiState.update { it.copy(documentBounds = null) }
            }
            CameraMode.NIGHT -> {
                // 退出夜景模式：重置相机参数
                resetNightModeSettings()
                Log.d(TAG, "selectMode: 已退出夜景模式，重置参数")
            }
            CameraMode.PRO -> {
                _uiState.update { it.copy(isProPanelVisible = false) }
            }
            else -> {}
        }

        // 启用新模式的处理器
        when (mode) {
            CameraMode.PORTRAIT -> {
                faceDetectionProcessor.enable()
                Log.d(TAG, "selectMode: 启用人脸检测")
            }
            CameraMode.DOCUMENT -> {
                documentScanProcessor.enable()
                Log.d(TAG, "selectMode: 启用文档检测")
            }
            CameraMode.NIGHT -> {
                // 夜景模式：配置低光优化参数
                configureNightMode()
                Log.d(TAG, "selectMode: 启用夜景模式优化")
            }
            CameraMode.PRO -> {
                _uiState.update { it.copy(isProPanelVisible = false) }  // PRO模式默认收起面板
                Log.d(TAG, "selectMode: 进入专业模式（面板默认收起）")
            }
            else -> {}
        }

        _uiState.update { it.copy(mode = mode) }
    }

    // ==================== 夜景模式配置 ====================

    /**
     * 配置夜景模式参数
     *
     * 夜景模式优化策略：
     * 1. 提高曝光补偿（使画面更亮）
     * 2. 启用HDR（如果支持）以增强动态范围
     * 3. 可选：提高ISO感光度
     */
    private fun configureNightMode() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "configureNightMode: 开始配置夜景模式参数")

                // 提高曝光补偿至最大值的70%
                val (minEv, maxEv, _) = useCase.getExposureCompensationRange()
                val nightEv = ((maxEv - minEv) * 0.7f + minEv).toInt()
                useCase.setExposureCompensation(nightEv)
                Log.d(TAG, "configureNightMode: 设置曝光补偿=$nightEv")

                // 启用HDR（如果支持）
                useCase.setHdrMode(HdrMode.ON)
                Log.d(TAG, "configureNightMode: 启用HDR")

                // 更新状态中的高级设置
                _uiState.update { state ->
                    state.copy(
                        advancedSettings = state.advancedSettings.copy(
                            hdrMode = HdrMode.ON
                        ),
                        proSettings = state.proSettings.copy(
                            exposureCompensation = nightEv.toFloat()
                        )
                    )
                }

                Log.d(TAG, "configureNightMode: 夜景模式配置完成")
            } catch (e: Exception) {
                Log.e(TAG, "configureNightMode: 配置失败", e)
            }
        }
    }

    /**
     * 重置夜景模式参数
     *
     * 退出夜景模式时恢复默认相机参数
     */
    private fun resetNightModeSettings() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "resetNightModeSettings: 重置夜景模式参数")

                // 重置曝光补偿为0
                useCase.setExposureCompensation(0)

                // 关闭HDR（恢复自动）
                useCase.setHdrMode(HdrMode.OFF)

                // 更新状态
                _uiState.update { state ->
                    state.copy(
                        advancedSettings = state.advancedSettings.copy(
                            hdrMode = HdrMode.OFF
                        ),
                        proSettings = state.proSettings.copy(
                            exposureCompensation = 0f
                        )
                    )
                }

                Log.d(TAG, "resetNightModeSettings: 重置完成")
            } catch (e: Exception) {
                Log.e(TAG, "resetNightModeSettings: 重置失败", e)
            }
        }
    }

    /**
     * 切换相机模式（拍照/录像）
     * 保留旧方法兼容性
     */
    fun toggleMode() {
        val newMode = if (_uiState.value.mode == CameraMode.PHOTO)
            CameraMode.VIDEO else CameraMode.PHOTO
        Log.d(TAG, "toggleMode: 切换模式 newMode=$newMode")
        selectMode(newMode)
    }

    // ==================== 专业模式参数设置（委托给ProModeStateHolder） ====================

    /**
     * 设置ISO感光度
     * @param iso ISO值，null表示自动
     */
    fun setProIso(iso: Int?) {
        Log.d(TAG, "setProIso: 委托给状态管理器 iso=$iso")
        proModeState.setIso(iso)
    }

    /**
     * 设置快门速度
     * 注意：快门速度需要Camera2 Interop且与曝光模式相关
     * 当前通过曝光补偿间接控制曝光
     */
    fun setProShutterSpeed(speed: Float?) {
        Log.d(TAG, "setProShutterSpeed: 委托给状态管理器 speed=$speed")
        proModeState.setShutterSpeed(speed)
    }

    /**
     * 设置曝光补偿
     * @param ev 曝光补偿值（EV）
     */
    fun setProExposureCompensation(ev: Float) {
        Log.d(TAG, "setProExposureCompensation: 委托给状态管理器 ev=$ev")
        proModeState.setExposureCompensation(ev)
    }

    /**
     * 设置白平衡模式
     */
    fun setProWhiteBalance(mode: WhiteBalanceMode) {
        Log.d(TAG, "setProWhiteBalance: 委托给状态管理器 mode=${mode.displayName}")
        proModeState.setWhiteBalance(mode)
    }

    /**
     * 设置对焦模式
     */
    fun setProFocusMode(mode: FocusMode) {
        Log.d(TAG, "setProFocusMode: 委托给状态管理器 mode=${mode.displayName}")
        proModeState.setFocusMode(mode)
    }

    /**
     * 设置手动对焦距离
     * @param distance 对焦距离（0.0=最近，1.0=无穷远）
     */
    fun setProFocusDistance(distance: Float) {
        Log.d(TAG, "setProFocusDistance: 委托给状态管理器 distance=$distance")
        proModeState.setFocusDistance(distance)
    }

    /**
     * 切换专业模式控制面板
     */
    fun toggleProPanel() {
        Log.d(TAG, "toggleProPanel: 委托给状态管理器")
        proModeState.togglePanel()
    }

    /**
     * 切换直方图显示（专业模式）
     *
     * 直方图用于显示当前画面的亮度和颜色分布
     * 帮助摄影师判断曝光是否正确
     */
    fun toggleHistogram() {
        val isVisible = !_uiState.value.isHistogramVisible
        Log.d(TAG, "toggleHistogram: 切换直方图显示 isVisible=$isVisible")
        _uiState.update { it.copy(isHistogramVisible = isVisible) }

        // 如果启用直方图，立即计算一次
        if (isVisible && currentPreviewFrame != null) {
            val histData = HistogramCalculator.calculateSampled(currentPreviewFrame, 4)
            _histogramData.value = histData
        }
    }

    /**
     * 切换变焦滑块可见性
     * 展开时关闭其他弹窗（设置面板、滤镜选择器、PRO面板）
     */
    fun toggleZoomSlider() {
        val isVisible = !_uiState.value.isZoomSliderVisible
        Log.d(TAG, "toggleZoomSlider: 切换变焦滑块 isVisible=$isVisible")
        _uiState.update {
            it.copy(
                isZoomSliderVisible = isVisible,
                isSettingsPanelExpanded = if (isVisible) false else it.isSettingsPanelExpanded,
                isFilterSelectorVisible = if (isVisible) false else it.isFilterSelectorVisible,
                isProPanelVisible = if (isVisible) false else it.isProPanelVisible
            )
        }
    }

    /**
     * 隐藏变焦滑块
     */
    fun hideZoomSlider() {
        Log.d(TAG, "hideZoomSlider: 隐藏变焦滑块")
        _uiState.update { it.copy(isZoomSliderVisible = false) }
    }

    // ==================== 滤镜选择（委托给FilterSelectorStateHolder） ====================

    /**
     * 选择滤镜
     */
    fun selectFilter(filterType: FilterType) {
        Log.d(TAG, "selectFilter: 委托给状态管理器 filterType=$filterType")
        filterSelectorState.selectFilter(filterType)
    }

    /**
     * 设置滤镜强度
     *
     * @param intensity 滤镜强度（0.0=无滤镜，1.0=全强度）
     */
    fun setFilterIntensity(intensity: Float) {
        val clampedIntensity = intensity.coerceIn(0f, 1f)
        Log.d(TAG, "setFilterIntensity: 设置滤镜强度 intensity=$clampedIntensity")
        _uiState.update { it.copy(filterIntensity = clampedIntensity) }
        viewModelScope.launch {
            useCase.setFilterIntensity(clampedIntensity)
        }
    }

    /**
     * 选择滤镜分组
     */
    fun selectFilterGroup(group: FilterGroup) {
        Log.d(TAG, "selectFilterGroup: 委托给状态管理器 group=$group")
        filterSelectorState.selectGroup(group)
    }

    /**
     * 根据分组获取滤镜列表
     */
    fun getFiltersByGroup(group: FilterGroup): List<FilterType> =
        filterSelectorState.getFiltersByGroup(group)

    /**
     * 切换滤镜选择器可见性
     * 展开时关闭设置面板
     */
    fun toggleFilterSelector() {
        Log.d(TAG, "toggleFilterSelector: 委托给状态管理器")
        filterSelectorState.togglePanel()
        // 展开滤镜时关闭设置面板
        if (filterSelectorState.isPanelVisible) {
            _uiState.update { it.copy(isSettingsPanelExpanded = false) }
        }
    }

    /**
     * 隐藏滤镜选择器
     */
    fun hideFilterSelector() {
        Log.d(TAG, "hideFilterSelector: 委托给状态管理器")
        filterSelectorState.hidePanel()
    }

    // ==================== 美颜设置 ====================

    /**
     * 设置美颜等级
     */
    fun setBeautyLevel(level: BeautyLevel) = viewModelScope.launch {
        Log.d(TAG, "setBeautyLevel: 设置美颜等级 level=$level")
        _uiState.update { it.copy(beautyLevel = level) }
        useCase.setBeauty(level)
    }

    // ==================== 高级设置（HDR/微距/画幅/光圈/变焦） ====================

    /**
     * 切换设置面板展开状态
     * 展开时关闭滤镜选择器
     */
    fun toggleSettingsPanel() {
        val isExpanded = !_uiState.value.isSettingsPanelExpanded
        Log.d(TAG, "toggleSettingsPanel: isExpanded=$isExpanded")
        _uiState.update {
            it.copy(
                isSettingsPanelExpanded = isExpanded,
                isFilterSelectorVisible = if (isExpanded) false else it.isFilterSelectorVisible  // 展开设置时关闭滤镜选择器
            )
        }
    }

    /**
     * 隐藏设置面板
     */
    fun hideSettingsPanel() {
        Log.d(TAG, "hideSettingsPanel: 隐藏设置面板")
        _uiState.update { it.copy(isSettingsPanelExpanded = false) }
    }

    /**
     * 关闭所有弹窗/面板
     *
     * 包括：设置面板、滤镜选择器、专业模式面板、变焦滑块
     * 调用场景：点击屏幕空白处、切换模式、点击其他功能按钮
     */
    fun dismissAllPopups() {
        Log.d(TAG, "dismissAllPopups: 关闭所有弹窗")
        // 通过状态管理器隐藏面板
        filterSelectorState.hidePanel()
        if (_uiState.value.mode != CameraMode.PRO) {
            proModeState.hidePanel()
        }
        // 直接更新剩余状态
        _uiState.update {
            it.copy(
                isSettingsPanelExpanded = false,
                isZoomSliderVisible = false
            )
        }
    }

    /**
     * 点击预览区域（空白处）
     * 关闭所有弹窗
     */
    fun onPreviewTapped() {
        Log.d(TAG, "onPreviewTapped: 点击预览区域")
        dismissAllPopups()
    }

    /**
     * 设置HDR模式
     */
    fun setHdrMode(mode: HdrMode) {
        Log.d(TAG, "setHdrMode: mode=${mode.displayName}")
        _uiState.update {
            it.copy(advancedSettings = it.advancedSettings.copy(hdrMode = mode))
        }
        // 应用到相机
        viewModelScope.launch {
            useCase.setHdrMode(mode)
        }
    }

    /**
     * 设置超级微距模式
     */
    fun setMacroMode(mode: MacroMode) {
        Log.d(TAG, "setMacroMode: mode=${mode.displayName}")
        _uiState.update {
            it.copy(advancedSettings = it.advancedSettings.copy(macroMode = mode))
        }
        // 应用到相机
        viewModelScope.launch {
            useCase.setMacroMode(mode)
        }
        // 微距模式自动调整变焦
        if (mode == MacroMode.ON) {
            setZoomLevel(ZoomConfig.MACRO_ZOOM)
        }
    }

    /**
     * 设置画幅比例
     */
    fun setAspectRatio(ratio: AspectRatio) {
        Log.d(TAG, "setAspectRatio: ratio=${ratio.displayName}")
        _uiState.update {
            it.copy(advancedSettings = it.advancedSettings.copy(aspectRatio = ratio))
        }
        // 应用到相机预览
        viewModelScope.launch {
            useCase.setAspectRatio(ratio)
        }
    }

    /**
     * 设置光圈模式
     * 注意：光圈是物理参数，软件仅记录设置，可用于模拟虚化等后期效果
     */
    fun setApertureMode(mode: ApertureMode) {
        Log.d(TAG, "setApertureMode: mode=${mode.displayName}")
        _uiState.update {
            it.copy(advancedSettings = it.advancedSettings.copy(apertureMode = mode))
        }
        // 光圈主要用于模拟虚化，在人像模式下生效
    }

    /**
     * 设置变焦倍数
     */
    fun setZoomLevel(zoom: Float) {
        // 使用设备实际支持的范围
        val range = _uiState.value.zoomRange
        val clampedZoom = zoom.coerceIn(range.minZoom, range.maxZoom)
        Log.d(TAG, "setZoomLevel: zoom=$clampedZoom (range: ${range.minZoom}-${range.maxZoom})")
        _uiState.update {
            it.copy(advancedSettings = it.advancedSettings.copy(zoomLevel = clampedZoom))
        }
        // 应用变焦到相机
        viewModelScope.launch {
            useCase.setZoom(clampedZoom)
        }
    }

    // ==================== 闪光灯控制 ====================

    /**
     * 设置闪光灯模式
     *
     * @param mode 闪光灯模式（OFF/ON/AUTO/TORCH）
     */
    fun setFlashMode(mode: FlashMode) {
        Log.d(TAG, "setFlashMode: 设置闪光灯模式=${mode.displayName}")
        _uiState.update {
            it.copy(advancedSettings = it.advancedSettings.copy(flashMode = mode))
        }
        viewModelScope.launch {
            useCase.setFlashMode(mode)
                .onFailure { error ->
                    Log.e(TAG, "setFlashMode: 设置失败", error)
                    _events.emit(CameraEvent.Error("闪光灯设置失败: ${error.message}"))
                }
        }
    }

    /**
     * 循环切换闪光灯模式
     *
     * 切换顺序：OFF -> ON -> AUTO -> OFF
     * 用于快捷切换闪光灯
     */
    fun toggleFlashMode() {
        Log.d(TAG, "toggleFlashMode: 切换闪光灯模式")
        viewModelScope.launch {
            useCase.toggleFlashMode()
                .onSuccess { newMode ->
                    Log.d(TAG, "toggleFlashMode: 切换成功 newMode=${newMode.displayName}")
                    _uiState.update {
                        it.copy(advancedSettings = it.advancedSettings.copy(flashMode = newMode))
                    }
                }
                .onFailure { error ->
                    Log.e(TAG, "toggleFlashMode: 切换失败", error)
                    _events.emit(CameraEvent.Error("闪光灯切换失败: ${error.message}"))
                }
        }
    }

    /**
     * 检查设备是否支持闪光灯
     *
     * @return true表示支持闪光灯
     */
    fun hasFlashUnit(): Boolean = useCase.hasFlashUnit()

    /**
     * 获取当前闪光灯模式
     *
     * @return 当前闪光灯模式
     */
    fun getCurrentFlashMode(): FlashMode = useCase.getCurrentFlashMode()

    // ==================== 自适应对焦 ====================

    /**
     * 自适应对焦
     * 根据当前场景自动调整对焦模式
     */
    fun performAdaptiveFocus() = viewModelScope.launch {
        Log.d(TAG, "performAdaptiveFocus: 执行自适应对焦")
        val settings = _uiState.value.advancedSettings

        // 根据变焦和微距状态自动调整
        val focusMode = when {
            settings.isMacroActive -> AdaptiveFocusMode.MACRO
            settings.isTelephotoActive -> AdaptiveFocusMode.TELEPHOTO
            else -> AdaptiveFocusMode.AUTO
        }

        _uiState.update {
            it.copy(advancedSettings = it.advancedSettings.copy(adaptiveFocus = focusMode))
        }

        // 触发相机自动对焦
        useCase.autoFocus()
    }

    /**
     * 相机启动时自动对焦
     */
    fun autoFocusOnStart() = viewModelScope.launch {
        if (_uiState.value.advancedSettings.isAutoFocusOnStart) {
            Log.d(TAG, "autoFocusOnStart: 启动时自动对焦")
            kotlinx.coroutines.delay(500)  // 等待相机初始化
            performAdaptiveFocus()
        }
    }

    // ==================== 错误处理 ====================

    /**
     * 清除错误信息
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // ==================== 资源释放 ====================

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "onCleared: 释放资源")
        timerJob?.cancel()                                                // 取消定时拍照倒计时
        faceDetectionProcessor.release()
        documentScanProcessor.release()
        filterSelectorState.release()                                 // 释放滤镜选择器资源
        Log.d(TAG, "onCleared: 状态管理器摘要 - Pro: ${proModeState.getSettingsSummary()}")
        Log.d(TAG, "onCleared: 状态管理器摘要 - Filter: ${filterSelectorState.getStateSummary()}")
    }
}
