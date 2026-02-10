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
import com.qihao.filtercamera.data.processor.FaceTrackingFocusCallback
import com.qihao.filtercamera.data.processor.MLKitDocumentScanner
import com.qihao.filtercamera.data.processor.PortraitBlurProcessor
import com.qihao.filtercamera.data.processor.ScanState
import com.qihao.filtercamera.data.processor.TimelapseEngine
import com.qihao.filtercamera.data.processor.TimelapseState
import androidx.compose.ui.geometry.Offset
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
import com.qihao.filtercamera.domain.model.NightMode
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "CameraViewModel"  // 日志标签

/**
 * 相机ViewModel
 *
 * @param useCase 统一相机用例 - 处理所有相机操作
 * @param faceDetectionProcessor 人脸检测处理器（人像模式）
 * @param documentScanProcessor 文档扫描处理器（文档模式）
 * @param mlKitDocumentScanner ML Kit文档扫描器（高级文档扫描）
 * @param popupStateHolder 弹窗状态管理器（统一管理所有弹窗互斥）
 */
@HiltViewModel
class CameraViewModel @Inject constructor(
    private val useCase: CameraUseCase,
    private val faceDetectionProcessor: FaceDetectionProcessor,
    private val documentScanProcessor: DocumentScanProcessor,
    private val timelapseEngine: TimelapseEngine,                     // 延时摄影引擎
    private val portraitBlurProcessor: PortraitBlurProcessor,         // 人像虚化处理器
    val mlKitDocumentScanner: MLKitDocumentScanner,                   // ML Kit文档扫描器
    val popupStateHolder: PopupStateHolder                            // 弹窗状态管理器
) : ViewModel() {

    // ==================== 核心UI状态 ====================

    // UI状态
    private val _uiState = MutableStateFlow(CameraState())
    val uiState: StateFlow<CameraState> = _uiState.asStateFlow()

    // 单次事件（拍照完成、录像完成、错误等）
    private val _events = MutableSharedFlow<CameraEvent>()
    val events: SharedFlow<CameraEvent> = _events.asSharedFlow()

    // 滤镜帧流（用于实时预览）- 修复：使用正确的私有MutableStateFlow模式
    private val _filteredFrame = MutableStateFlow<Bitmap?>(null)
    val filteredFrame: StateFlow<Bitmap?> = _filteredFrame.asStateFlow()

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

    // 对焦超时Job（3秒后自动隐藏对焦指示器）
    private var focusTimeoutJob: kotlinx.coroutines.Job? = null

    // 延时摄影帧捕获定时Job
    private var timelapseJob: kotlinx.coroutines.Job? = null

    // 对焦超时时间（毫秒）
    private companion object FocusConfig {
        const val FOCUS_TIMEOUT_MS = 3000L                            // 对焦指示器显示时间
    }

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
        observeFaceTrackingState()                                    // 观察人脸追踪状态
        observeDocumentBounds()                                       // 观察文档边界检测结果
        observeZoomRange()                                            // 观察变焦范围
        observeStateHolders()                                         // 观察状态管理器变化
        observeNightProcessingProgress()                              // 观察夜景处理进度
        observeTimelapseProgress()                                    // 观察延时摄影进度
        observePortraitBlurProgress()                                 // 观察人像虚化处理进度
        loadInitialGalleryThumbnail()                                 // 加载初始相册缩略图
    }

    /**
     * 加载初始相册缩略图
     *
     * 在ViewModel初始化时从MediaStore查询最近的照片，
     * 用于左下角相册预览按钮显示
     */
    private fun loadInitialGalleryThumbnail() = viewModelScope.launch(Dispatchers.IO) {
        try {
            Log.d(TAG, "loadInitialGalleryThumbnail: 开始加载初始相册缩略图")
            val thumbnail = useCase.getLatestGalleryThumbnail()       // 查询最新照片缩略图
            if (thumbnail != null) {
                _galleryThumbnail.value = thumbnail                   // 更新UI状态
                Log.d(TAG, "loadInitialGalleryThumbnail: 加载成功 ${thumbnail.width}x${thumbnail.height}")
            } else {
                Log.d(TAG, "loadInitialGalleryThumbnail: 相册为空或无权限")
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadInitialGalleryThumbnail: 加载失败", e)
        }
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
     * 观察夜景处理进度变化
     *
     * 当夜景处理器正在处理时，更新UI显示进度
     */
    private fun observeNightProcessingProgress() = viewModelScope.launch {
        useCase.getNightProcessingProgress().collect { progress ->
            val isProcessing = progress != null
            val (stageName, progressValue) = progress ?: ("" to 0f)
            Log.d(TAG, "observeNightProcessingProgress: isProcessing=$isProcessing, stage=$stageName, progress=$progressValue")
            _uiState.update { state ->
                state.copy(
                    isNightProcessing = isProcessing,
                    nightProcessingProgress = progressValue
                )
            }
        }
    }

    /**
     * 观察延时摄影进度变化
     *
     * 更新UI显示已捕获帧数、已用时间、编码进度
     */
    private fun observeTimelapseProgress() = viewModelScope.launch {
        useCase.getTimelapseProgress().collect { (framesCaptured, elapsedMs, encodingProgress) ->
            val isRecording = useCase.isTimelapseRecording()
            val isEncoding = encodingProgress > 0f && encodingProgress < 1f
            Log.d(TAG, "observeTimelapseProgress: frames=$framesCaptured, elapsed=$elapsedMs, encoding=$encodingProgress")
            _uiState.update { state ->
                state.copy(
                    isTimelapseRecording = isRecording,
                    timelapseFramesCaptured = framesCaptured,
                    timelapseElapsedMs = elapsedMs,
                    isTimelapseEncoding = isEncoding,
                    timelapseEncodingProgress = encodingProgress
                )
            }
        }
    }

    /**
     * 观察人像虚化处理进度
     *
     * 当人像虚化处理器正在处理时，更新UI显示进度
     * 用于拍照时显示虚化处理状态
     * 注意：只在实际处理图片时显示进度（不包含初始化阶段）
     */
    private fun observePortraitBlurProgress() = viewModelScope.launch {
        portraitBlurProcessor.processingProgress.collect { progress ->
            val (stageName, progressValue) = progress ?: ("" to 0f)

            // 修复：只在实际虚化处理阶段（非初始化）才显示进度
            // 初始化阶段的关键词：正在初始化、ML Kit、初始化完成
            val isInitializationPhase = stageName.contains("初始化") ||
                                        stageName.contains("ML Kit") ||
                                        stageName.isEmpty()

            // 只有在实际处理阶段（分割、模糊、合成）才显示处理中状态
            val isProcessing = progress != null && !isInitializationPhase

            Log.d(TAG, "observePortraitBlurProgress: isProcessing=$isProcessing, stage=$stageName, progress=$progressValue (init=$isInitializationPhase)")
            _uiState.update { state ->
                state.copy(
                    isPortraitBlurProcessing = isProcessing,
                    portraitBlurProgress = if (isProcessing) progressValue else 0f
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
            _filteredFrame.value = bitmap  // 修复：使用私有_filteredFrame

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
            // 修复：添加isRecycled检查和异常处理
            if (bitmap != null && !bitmap.isRecycled) {
                try {
                    // 修复内存泄漏：回收旧预览帧
                    currentPreviewFrame?.let { oldFrame ->
                        if (!oldFrame.isRecycled) oldFrame.recycle()
                    }
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
                } catch (e: Exception) {
                    Log.e(TAG, "observeRawPreviewFrame: 处理预览帧异常", e)
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
     * 观察人脸追踪状态（人像模式自动对焦）
     *
     * 追踪状态用于UI显示当前追踪状况：
     * - IDLE: 未检测到人脸
     * - TRACKING: 正在追踪人脸
     * - LOST: 人脸追踪丢失
     */
    private fun observeFaceTrackingState() = viewModelScope.launch {
        faceDetectionProcessor.trackingState.collect { state ->
            Log.d(TAG, "observeFaceTrackingState: 追踪状态变化 state=$state")
            _uiState.update { it.copy(faceTrackingState = state) }
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
        // 修复：添加isRecycled检查和异常处理
        if (bitmap.isRecycled) {
            Log.w(TAG, "updatePreviewFrame: Bitmap已回收，跳过")
            return
        }
        try {
            // 修复内存泄漏：回收旧预览帧
            currentPreviewFrame?.let { oldFrame ->
                if (!oldFrame.isRecycled) oldFrame.recycle()
            }
            currentPreviewFrame = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
            filterSelectorState.updatePreviewFrame(bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "updatePreviewFrame: 复制Bitmap异常", e)
        }
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
                    // 更新相册缩略图（使用当前预览帧作为临时缩略图）- 修复：添加isRecycled检查
                    currentPreviewFrame?.let { frame ->
                        if (!frame.isRecycled) {
                            try {
                                val thumbnail = Bitmap.createScaledBitmap(frame, 100, 100, true)
                                _galleryThumbnail.value = thumbnail
                            } catch (e: Exception) {
                                Log.e(TAG, "executePhoto: 创建缩略图失败", e)
                            }
                        }
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
                faceDetectionProcessor.disableTrackingFocus()         // 禁用人脸追踪对焦
                _uiState.update { it.copy(
                    detectedFaces = emptyList(),
                    faceTrackingState = com.qihao.filtercamera.data.processor.FaceTrackingState.IDLE,
                    isPortraitBlurProcessing = false,
                    portraitBlurProgress = 0f
                ) }
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
                proModeState.hidePanel()  // 退出PRO模式时隐藏面板
            }
            CameraMode.TIMELAPSE -> {
                // 退出延时摄影模式：取消正在进行的延时摄影
                if (_uiState.value.isTimelapseRecording) {
                    cancelTimelapse()
                    Log.d(TAG, "selectMode: 已取消延时摄影")
                }
            }
            else -> {}
        }

        // 启用新模式的处理器
        when (mode) {
            CameraMode.PORTRAIT -> {
                faceDetectionProcessor.enable()
                // 启用人脸追踪对焦：检测到人脸时自动触发对焦
                faceDetectionProcessor.enableTrackingFocus { x, y ->
                    Log.d(TAG, "selectMode: 人脸追踪触发对焦 x=$x, y=$y")
                    onPreviewTouchFocus(x, y)
                }
                // 初始化人像虚化处理器
                initializePortraitBlurProcessor()
                // 重置人像模式覆盖层可见性
                _uiState.update { it.copy(isPortraitOverlayVisible = true) }
                Log.d(TAG, "selectMode: 启用人脸检测、追踪对焦和人像虚化")
            }
            CameraMode.DOCUMENT -> {
                documentScanProcessor.enable()
                // 设置自动捕获回调（仅当启用自动捕获时触发）
                documentScanProcessor.setOnAutoCaptureCallback {
                    if (_uiState.value.isDocumentAutoCapture) {
                        Log.d(TAG, "selectMode: 文档边界稳定，触发自动捕获")
                        // 必须在主线程调用拍照（回调来自后台线程）
                        viewModelScope.launch(Dispatchers.Main) {
                            takePhoto()
                        }
                    }
                }
                Log.d(TAG, "selectMode: 启用文档检测")
            }
            CameraMode.NIGHT -> {
                // 夜景模式：配置低光优化参数
                configureNightMode()
                Log.d(TAG, "selectMode: 启用夜景模式优化")
            }
            CameraMode.PRO -> {
                proModeState.showPanel()  // PRO模式默认显示面板，用户可立即调节参数
                Log.d(TAG, "selectMode: 进入专业模式（显示控制面板）")
            }
            CameraMode.TIMELAPSE -> {
                // 延时摄影模式：准备延时摄影引擎
                Log.d(TAG, "selectMode: 进入延时摄影模式")
                _uiState.update { state ->
                    state.copy(
                        isTimelapseRecording = false,
                        timelapseFramesCaptured = 0,
                        timelapseElapsedMs = 0L,
                        isTimelapseEncoding = false,
                        timelapseEncodingProgress = 0f
                    )
                }
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
     * 1. 启用CameraX Night扩展或软件夜景处理
     * 2. 提高曝光补偿（使画面更亮）
     * 3. 启用HDR（如果支持）以增强动态范围
     */
    private fun configureNightMode() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "configureNightMode: 开始配置夜景模式参数")

                // 启用夜景模式（硬件或软件）
                useCase.setNightMode(NightMode.ON)
                    .onSuccess {
                        Log.d(TAG, "configureNightMode: 夜景模式启用成功")
                    }
                    .onFailure { error ->
                        Log.e(TAG, "configureNightMode: 夜景模式启用失败", error)
                    }

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
                        currentNightMode = NightMode.ON,
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

                // 关闭夜景模式
                useCase.setNightMode(NightMode.OFF)
                    .onSuccess {
                        Log.d(TAG, "resetNightModeSettings: 夜景模式已关闭")
                    }
                    .onFailure { error ->
                        Log.e(TAG, "resetNightModeSettings: 关闭夜景模式失败", error)
                    }

                // 重置曝光补偿为0
                useCase.setExposureCompensation(0)

                // 关闭HDR（恢复自动）
                useCase.setHdrMode(HdrMode.OFF)

                // 更新状态
                _uiState.update { state ->
                    state.copy(
                        currentNightMode = NightMode.OFF,
                        isNightProcessing = false,
                        nightProcessingProgress = 0f,
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
     * 使用PopupStateHolder实现互斥
     */
    fun toggleZoomSlider() {
        Log.d(TAG, "toggleZoomSlider: 切换变焦滑块")
        popupStateHolder.toggleZoomSlider()
        // 同步更新CameraState（保持向后兼容）
        syncPopupStateToCameraState()
    }

    /**
     * 切换模式菜单可见性
     * 使用PopupStateHolder实现互斥
     * 模式菜单整合了HDR、定时器、画幅比例、滤镜等功能入口
     */
    fun toggleModeMenu() {
        Log.d(TAG, "toggleModeMenu: 切换模式菜单")
        popupStateHolder.toggleModeMenu()
        syncPopupStateToCameraState()
    }

    /**
     * 隐藏模式菜单
     */
    fun hideModeMenu() {
        Log.d(TAG, "hideModeMenu: 隐藏模式菜单")
        if (popupStateHolder.isModeMenuVisible) {
            popupStateHolder.hide()
            syncPopupStateToCameraState()
        }
    }

    /**
     * 隐藏变焦滑块
     */
    fun hideZoomSlider() {
        Log.d(TAG, "hideZoomSlider: 隐藏变焦滑块")
        if (popupStateHolder.isZoomSliderVisible) {
            popupStateHolder.hide()
            syncPopupStateToCameraState()
        }
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
     * 使用PopupStateHolder实现互斥
     */
    fun toggleFilterSelector() {
        Log.d(TAG, "toggleFilterSelector: 切换滤镜选择器")
        popupStateHolder.toggleFilterSelector()
        // 同步更新状态管理器和CameraState
        if (popupStateHolder.isFilterSelectorVisible) {
            filterSelectorState.showPanel()
        } else {
            filterSelectorState.hidePanel()
        }
        syncPopupStateToCameraState()
    }

    /**
     * 隐藏滤镜选择器
     */
    fun hideFilterSelector() {
        Log.d(TAG, "hideFilterSelector: 隐藏滤镜选择器")
        if (popupStateHolder.isFilterSelectorVisible) {
            popupStateHolder.hide()
            filterSelectorState.hidePanel()
            syncPopupStateToCameraState()
        }
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

    /**
     * 循环切换美颜等级
     *
     * 切换顺序：OFF -> LEVEL_1 -> LEVEL_2 -> ... -> LEVEL_10 -> OFF
     */
    fun toggleBeautyLevel() {
        val currentLevel = _uiState.value.beautyLevel
        val allLevels = BeautyLevel.entries.toList()                         // 获取所有等级
        val currentIndex = allLevels.indexOf(currentLevel)
        val nextLevel = allLevels[(currentIndex + 1) % allLevels.size]
        Log.d(TAG, "toggleBeautyLevel: ${currentLevel.displayName} -> ${nextLevel.displayName}")
        setBeautyLevel(nextLevel)
    }

    // ==================== 高级设置（HDR/微距/画幅/光圈/变焦） ====================

    /**
     * 切换设置面板展开状态
     * 使用PopupStateHolder实现互斥
     */
    fun toggleSettingsPanel() {
        Log.d(TAG, "toggleSettingsPanel: 切换设置面板")
        popupStateHolder.toggleSettingsPanel()
        syncPopupStateToCameraState()
    }

    /**
     * 隐藏设置面板
     */
    fun hideSettingsPanel() {
        Log.d(TAG, "hideSettingsPanel: 隐藏设置面板")
        if (popupStateHolder.isSettingsPanelExpanded) {
            popupStateHolder.hide()
            syncPopupStateToCameraState()
        }
    }

    /**
     * 关闭所有弹窗/面板
     * 使用PopupStateHolder统一管理
     *
     * 包括：设置面板、滤镜选择器、专业模式面板、变焦滑块
     * 调用场景：点击屏幕空白处、切换模式、点击其他功能按钮
     */
    fun dismissAllPopups() {
        Log.d(TAG, "dismissAllPopups: 关闭所有弹窗")
        // 使用PopupStateHolder统一隐藏
        popupStateHolder.hideAll()
        // 同步其他状态管理器
        filterSelectorState.hidePanel()
        if (_uiState.value.mode != CameraMode.PRO) {
            proModeState.hidePanel()
        }
        // 同步CameraState
        syncPopupStateToCameraState()
    }

    /**
     * 同步PopupStateHolder状态到CameraState
     * 保持向后兼容性
     * 注意：isProPanelVisible由proModeState独立管理，通过observeStateHolders同步
     */
    private fun syncPopupStateToCameraState() {
        _uiState.update {
            it.copy(
                isFilterSelectorVisible = popupStateHolder.isFilterSelectorVisible,
                // isProPanelVisible 由 proModeState 通过 observeStateHolders 管理，避免双重来源冲突
                isZoomSliderVisible = popupStateHolder.isZoomSliderVisible,
                isSettingsPanelExpanded = popupStateHolder.isSettingsPanelExpanded,
                isDocumentScanModeSelectorVisible = popupStateHolder.isDocumentScanModeSelectorVisible,
                isPortraitOverlayVisible = popupStateHolder.isPortraitOverlayVisible,
                isModeMenuVisible = popupStateHolder.isModeMenuVisible
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
     * 更新对焦指示器位置（仅视觉效果，不触发实际对焦）
     *
     * 用于手指拖动时实时跟随手指位置显示对焦框
     * 实际对焦操作在手指抬起时通过 onPreviewTouchFocus 触发
     *
     * @param x 归一化X坐标 (0.0~1.0)
     * @param y 归一化Y坐标 (0.0~1.0)
     */
    fun updateFocusPointPreview(x: Float, y: Float) {
        Log.d(TAG, "updateFocusPointPreview: 更新对焦预览位置 x=$x, y=$y")
        // 仅更新视觉位置，不触发对焦
        _uiState.update {
            it.copy(
                focusPoint = Offset(x, y),
                isFocusing = false                                            // 预览状态，不是正在对焦
            )
        }
    }

    /**
     * 触摸对焦 - 手指点击屏幕任意位置进行对焦
     *
     * 核心功能：用户点击预览区域的任意位置，相机将在该位置进行对焦和测光
     * 同时显示对焦指示器动画，3秒后自动消失
     *
     * @param x 归一化X坐标 (0.0~1.0，0为左边缘，1为右边缘)
     * @param y 归一化Y坐标 (0.0~1.0，0为上边缘，1为下边缘)
     */
    fun onPreviewTouchFocus(x: Float, y: Float) {
        Log.d(TAG, "onPreviewTouchFocus: 触摸对焦 x=$x, y=$y")

        // 关闭所有弹窗
        dismissAllPopups()

        // 取消之前的对焦超时Job
        focusTimeoutJob?.cancel()

        // 更新对焦状态：显示对焦点，标记正在对焦
        _uiState.update {
            it.copy(
                focusPoint = Offset(x, y),
                isFocusing = true
            )
        }

        // 执行实际对焦操作
        viewModelScope.launch {
            try {
                useCase.focusAtPoint(x, y)
                    .onSuccess {
                        Log.d(TAG, "onPreviewTouchFocus: 对焦成功")
                        // 对焦成功后，短暂保持指示器（给用户反馈）
                        _uiState.update { it.copy(isFocusing = false) }
                    }
                    .onFailure { error ->
                        Log.e(TAG, "onPreviewTouchFocus: 对焦失败", error)
                        _uiState.update { it.copy(isFocusing = false) }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "onPreviewTouchFocus: 对焦异常", e)
                _uiState.update { it.copy(isFocusing = false) }
            }
        }

        // 设置对焦超时：3秒后自动隐藏对焦指示器
        focusTimeoutJob = viewModelScope.launch {
            kotlinx.coroutines.delay(FOCUS_TIMEOUT_MS)
            Log.d(TAG, "onPreviewTouchFocus: 对焦超时，隐藏指示器")
            _uiState.update {
                it.copy(
                    focusPoint = null,
                    isFocusing = false
                )
            }
        }
    }

    /**
     * 清除对焦指示器
     * 用于手动清除对焦点显示
     */
    fun clearFocusIndicator() {
        Log.d(TAG, "clearFocusIndicator: 清除对焦指示器")
        focusTimeoutJob?.cancel()
        _uiState.update {
            it.copy(
                focusPoint = null,
                isFocusing = false,
                focusExposureCompensation = 0f                        // 重置曝光补偿
            )
        }
    }

    /**
     * 设置触摸对焦曝光补偿
     *
     * 用于聚焦框右侧的亮度调节滑块
     * 范围：-1.0（最暗）到 1.0（最亮）
     *
     * @param value 曝光补偿值（-1.0 ~ 1.0）
     */
    fun setFocusExposureCompensation(value: Float) {
        Log.d(TAG, "setFocusExposureCompensation: value=$value")
        val clampedValue = value.coerceIn(-1f, 1f)                    // 限制范围
        _uiState.update { it.copy(focusExposureCompensation = clampedValue) }

        // 应用到相机曝光（将-1~1映射到相机的曝光补偿范围）
        viewModelScope.launch {
            try {
                val (minEv, maxEv, _) = useCase.getExposureCompensationRange()
                val mappedEv = if (clampedValue >= 0) {
                    (clampedValue * maxEv).toInt()                    // 正值映射到 0~maxEv
                } else {
                    (clampedValue * -minEv).toInt()                   // 负值映射到 minEv~0
                }
                useCase.setExposureCompensation(mappedEv)
                Log.d(TAG, "setFocusExposureCompensation: 映射后EV=$mappedEv")
            } catch (e: Exception) {
                Log.e(TAG, "setFocusExposureCompensation: 设置失败", e)
            }
        }
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
     * 设置夜景模式
     *
     * 夜景模式会触发：
     * - 硬件支持时：使用CameraX Night扩展（设备原生夜景算法）
     * - 硬件不支持时：使用软件多帧合成（NightModeProcessor）
     * - AUTO模式：根据环境光自动判断是否启用
     *
     * @param mode 夜景模式（OFF/ON/AUTO）
     */
    fun setNightMode(mode: NightMode) {
        Log.d(TAG, "setNightMode: mode=${mode.displayName}")
        _uiState.update {
            it.copy(currentNightMode = mode)
        }
        // 应用到相机
        viewModelScope.launch {
            useCase.setNightMode(mode)
                .onSuccess {
                    Log.d(TAG, "setNightMode: 设置成功")
                }
                .onFailure { error ->
                    Log.e(TAG, "setNightMode: 设置失败", error)
                    _events.emit(CameraEvent.Error("夜景模式设置失败: ${error.message}"))
                }
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

    // ==================== 人像虚化控制 ====================

    /**
     * 初始化人像虚化处理器
     *
     * 在进入人像模式时调用，准备虚化处理资源
     */
    private fun initializePortraitBlurProcessor() {
        viewModelScope.launch {
            portraitBlurProcessor.initialize()
                .onSuccess {
                    Log.d(TAG, "initializePortraitBlurProcessor: 人像虚化处理器初始化成功")
                }
                .onFailure { error ->
                    Log.e(TAG, "initializePortraitBlurProcessor: 初始化失败", error)
                }
        }
    }

    /**
     * 设置人像虚化等级
     *
     * @param level 虚化等级（NONE/LIGHT/MEDIUM/HEAVY）
     */
    fun setPortraitBlurLevel(level: com.qihao.filtercamera.domain.model.PortraitBlurLevel) {
        Log.d(TAG, "setPortraitBlurLevel: 设置虚化等级=${level.displayName}")
        _uiState.update { it.copy(portraitBlurLevel = level) }
        // 同步到Repository
        viewModelScope.launch {
            useCase.setPortraitBlurLevel(level)
                .onFailure { error ->
                    Log.e(TAG, "setPortraitBlurLevel: 设置失败", error)
                    _events.emit(CameraEvent.Error("虚化等级设置失败: ${error.message}"))
                }
        }
    }

    /**
     * 获取当前人像虚化等级
     *
     * @return 当前虚化等级
     */
    fun getPortraitBlurLevel(): com.qihao.filtercamera.domain.model.PortraitBlurLevel =
        _uiState.value.portraitBlurLevel

    /**
     * 切换人像模式覆盖层可见性
     * 在人像模式下，点击屏幕可隐藏覆盖层，再次点击可显示
     */
    fun togglePortraitOverlay() {
        val newVisible = !_uiState.value.isPortraitOverlayVisible
        Log.d(TAG, "togglePortraitOverlay: 切换人像覆盖层可见性=$newVisible")
        _uiState.update { it.copy(isPortraitOverlayVisible = newVisible) }
    }

    /**
     * 显示人像模式覆盖层
     */
    fun showPortraitOverlay() {
        Log.d(TAG, "showPortraitOverlay: 显示人像覆盖层")
        _uiState.update { it.copy(isPortraitOverlayVisible = true) }
    }

    /**
     * 循环切换人像虚化等级
     *
     * 切换顺序：NONE -> LIGHT -> MEDIUM -> HEAVY -> NONE
     */
    fun togglePortraitBlurLevel() {
        val currentLevel = _uiState.value.portraitBlurLevel
        val levels = com.qihao.filtercamera.domain.model.PortraitBlurLevel.getAll()
        val currentIndex = levels.indexOf(currentLevel)
        val nextLevel = levels[(currentIndex + 1) % levels.size]
        Log.d(TAG, "togglePortraitBlurLevel: ${currentLevel.displayName} -> ${nextLevel.displayName}")
        setPortraitBlurLevel(nextLevel)
    }

    /**
     * 对Bitmap应用人像虚化效果
     *
     * 用于拍照后处理或实时预览
     *
     * @param bitmap 源图像
     * @return 虚化处理后的图像
     */
    suspend fun applyPortraitBlur(bitmap: Bitmap): Bitmap {
        val blurLevel = _uiState.value.portraitBlurLevel
        if (blurLevel == com.qihao.filtercamera.domain.model.PortraitBlurLevel.NONE) {
            Log.d(TAG, "applyPortraitBlur: 虚化等级为NONE，跳过处理")
            return bitmap
        }

        Log.d(TAG, "applyPortraitBlur: 应用人像虚化 level=${blurLevel.displayName}")
        val result = portraitBlurProcessor.processPortraitBlur(
            bitmap,
            com.qihao.filtercamera.data.processor.PortraitBlurConfig(
                blurRadius = blurLevel.blurRadius,
                edgeSmooth = 0.5f,
                foregroundThreshold = 0.5f
            )
        )

        return if (result.success && result.hasPerson) {
            Log.d(TAG, "applyPortraitBlur: 虚化处理成功 耗时=${result.processingTimeMs}ms")
            result.bitmap
        } else {
            Log.d(TAG, "applyPortraitBlur: 未检测到人物或处理失败，返回原图")
            bitmap
        }
    }

    /**
     * 检查人像虚化处理器是否就绪
     *
     * @return true表示处理器已初始化就绪
     */
    fun isPortraitBlurReady(): Boolean = portraitBlurProcessor.isReady()

    // ==================== 人脸追踪对焦控制 ====================

    /**
     * 获取当前人脸追踪状态
     *
     * @return 当前追踪状态（IDLE/TRACKING/LOST）
     */
    fun getFaceTrackingState(): com.qihao.filtercamera.data.processor.FaceTrackingState =
        _uiState.value.faceTrackingState

    /**
     * 检查是否正在追踪人脸
     *
     * @return true表示正在追踪人脸
     */
    fun isTrackingFace(): Boolean =
        _uiState.value.faceTrackingState == com.qihao.filtercamera.data.processor.FaceTrackingState.TRACKING

    /**
     * 手动启用人脸追踪对焦
     *
     * 在人像模式下启用自动追踪人脸并对焦
     * 注意：进入PORTRAIT模式时会自动启用，通常无需手动调用
     */
    fun enableFaceTrackingFocus() {
        Log.d(TAG, "enableFaceTrackingFocus: 手动启用人脸追踪对焦")
        faceDetectionProcessor.enableTrackingFocus { x, y ->
            Log.d(TAG, "enableFaceTrackingFocus: 追踪对焦触发 x=$x, y=$y")
            onPreviewTouchFocus(x, y)
        }
    }

    /**
     * 手动禁用人脸追踪对焦
     *
     * 禁用后将不再自动追踪人脸对焦，但仍会检测人脸用于UI显示
     */
    fun disableFaceTrackingFocus() {
        Log.d(TAG, "disableFaceTrackingFocus: 手动禁用人脸追踪对焦")
        faceDetectionProcessor.disableTrackingFocus()
        _uiState.update { it.copy(faceTrackingState = com.qihao.filtercamera.data.processor.FaceTrackingState.IDLE) }
    }

    /**
     * 切换人脸追踪对焦开关
     *
     * 用于UI上的追踪对焦开关按钮
     *
     * @return 切换后的状态（true=已启用，false=已禁用）
     */
    fun toggleFaceTrackingFocus(): Boolean {
        val wasTracking = _uiState.value.faceTrackingState != com.qihao.filtercamera.data.processor.FaceTrackingState.IDLE
        return if (wasTracking) {
            disableFaceTrackingFocus()
            false
        } else {
            enableFaceTrackingFocus()
            true
        }
    }

    // ==================== 延时摄影控制 ====================

    /**
     * 开始延时摄影录制
     *
     * 使用默认配置（3秒间隔，30fps输出，1080p）开始延时摄影
     * 自动启动帧捕获定时器
     *
     * @param settings 延时摄影配置，null使用默认配置
     */
    fun startTimelapse(settings: com.qihao.filtercamera.domain.model.TimelapseSettings? = null) {
        if (_uiState.value.isTimelapseRecording) {
            Log.w(TAG, "startTimelapse: 已在延时摄影录制中，忽略请求")
            return
        }

        val config = settings ?: com.qihao.filtercamera.domain.model.TimelapseSettings.PRESET_STANDARD
        Log.d(TAG, "startTimelapse: 开始延时摄影 interval=${config.captureIntervalSec}s, fps=${config.outputFps}")

        viewModelScope.launch {
            useCase.startTimelapse(config)
                .onSuccess {
                    Log.d(TAG, "startTimelapse: 延时摄影引擎启动成功")
                    _uiState.update { state ->
                        state.copy(
                            isTimelapseRecording = true,
                            timelapseFramesCaptured = 0,
                            timelapseElapsedMs = 0L,
                            isTimelapseEncoding = false,
                            timelapseEncodingProgress = 0f
                        )
                    }
                    // 启动帧捕获定时器
                    startTimelapseFrameCapture(config.captureIntervalMs)
                }
                .onFailure { error ->
                    Log.e(TAG, "startTimelapse: 延时摄影启动失败", error)
                    _events.emit(CameraEvent.Error("延时摄影启动失败: ${error.message}"))
                }
        }
    }

    /**
     * 启动延时摄影帧捕获定时器
     *
     * 按配置间隔捕获预览帧并添加到引擎
     *
     * @param intervalMs 捕获间隔（毫秒）
     */
    private fun startTimelapseFrameCapture(intervalMs: Long) {
        // 取消之前的Job
        timelapseJob?.cancel()

        timelapseJob = viewModelScope.launch {
            Log.d(TAG, "startTimelapseFrameCapture: 启动帧捕获定时器 interval=${intervalMs}ms")
            val startTime = System.currentTimeMillis()

            while (_uiState.value.isTimelapseRecording) {
                // 捕获当前预览帧
                val frame = currentPreviewFrame
                if (frame != null) {
                    val frameCopy = frame.copy(frame.config ?: Bitmap.Config.ARGB_8888, false)
                    val addResult = timelapseEngine.addFrame(frameCopy)
                    if (addResult.isSuccess) {
                        val elapsed = System.currentTimeMillis() - startTime
                        _uiState.update { state ->
                            state.copy(
                                timelapseFramesCaptured = state.timelapseFramesCaptured + 1,
                                timelapseElapsedMs = elapsed
                            )
                        }
                        Log.d(TAG, "startTimelapseFrameCapture: 帧捕获成功 frames=${_uiState.value.timelapseFramesCaptured}")
                    }
                } else {
                    Log.w(TAG, "startTimelapseFrameCapture: 当前无预览帧可捕获")
                }

                // 等待下一次捕获
                kotlinx.coroutines.delay(intervalMs)
            }
            Log.d(TAG, "startTimelapseFrameCapture: 帧捕获定时器已停止")
        }
    }

    /**
     * 停止延时摄影并编码输出视频
     *
     * 停止帧捕获，将帧序列编码为MP4视频
     */
    fun stopTimelapse() {
        if (!_uiState.value.isTimelapseRecording) {
            Log.w(TAG, "stopTimelapse: 未在延时摄影录制中，忽略请求")
            return
        }

        Log.d(TAG, "stopTimelapse: 停止延时摄影并编码")
        // 停止帧捕获
        timelapseJob?.cancel()
        timelapseJob = null

        // 更新状态为编码中
        _uiState.update { state ->
            state.copy(
                isTimelapseRecording = false,
                isTimelapseEncoding = true,
                timelapseEncodingProgress = 0f
            )
        }

        viewModelScope.launch {
            useCase.stopTimelapse()
                .onSuccess { videoPath ->
                    Log.d(TAG, "stopTimelapse: 延时摄影视频编码完成 path=$videoPath")
                    _uiState.update { state ->
                        state.copy(
                            isTimelapseEncoding = false,
                            timelapseEncodingProgress = 1f
                        )
                    }
                    _events.emit(CameraEvent.VideoRecorded(videoPath))
                }
                .onFailure { error ->
                    Log.e(TAG, "stopTimelapse: 延时摄影编码失败", error)
                    _uiState.update { state ->
                        state.copy(
                            isTimelapseEncoding = false,
                            timelapseEncodingProgress = 0f
                        )
                    }
                    _events.emit(CameraEvent.Error("延时摄影编码失败: ${error.message}"))
                }
        }
    }

    /**
     * 取消延时摄影（不生成视频）
     *
     * 丢弃已捕获的帧，不生成输出视频
     */
    fun cancelTimelapse() {
        Log.d(TAG, "cancelTimelapse: 取消延时摄影")
        // 停止帧捕获
        timelapseJob?.cancel()
        timelapseJob = null

        // 重置状态
        _uiState.update { state ->
            state.copy(
                isTimelapseRecording = false,
                timelapseFramesCaptured = 0,
                timelapseElapsedMs = 0L,
                isTimelapseEncoding = false,
                timelapseEncodingProgress = 0f
            )
        }

        viewModelScope.launch {
            useCase.cancelTimelapse()
                .onFailure { error ->
                    Log.e(TAG, "cancelTimelapse: 取消延时摄影失败", error)
                }
        }
    }

    /**
     * 暂停延时摄影
     *
     * 暂停帧捕获，保留已捕获的帧
     */
    fun pauseTimelapse() {
        if (!_uiState.value.isTimelapseRecording) {
            Log.w(TAG, "pauseTimelapse: 未在延时摄影录制中，忽略请求")
            return
        }

        Log.d(TAG, "pauseTimelapse: 暂停延时摄影")
        // 停止帧捕获定时器（但不清除已捕获的帧）
        timelapseJob?.cancel()
        timelapseJob = null

        viewModelScope.launch {
            useCase.pauseTimelapse()
                .onSuccess {
                    Log.d(TAG, "pauseTimelapse: 暂停成功")
                }
                .onFailure { error ->
                    Log.e(TAG, "pauseTimelapse: 暂停失败", error)
                }
        }
    }

    /**
     * 恢复延时摄影
     *
     * 恢复帧捕获
     */
    fun resumeTimelapse(intervalMs: Long = 3000L) {
        if (_uiState.value.isTimelapseRecording && timelapseJob?.isActive == true) {
            Log.w(TAG, "resumeTimelapse: 已在录制中，忽略请求")
            return
        }

        Log.d(TAG, "resumeTimelapse: 恢复延时摄影")
        viewModelScope.launch {
            useCase.resumeTimelapse()
                .onSuccess {
                    Log.d(TAG, "resumeTimelapse: 恢复成功")
                    _uiState.update { it.copy(isTimelapseRecording = true) }
                    // 重新启动帧捕获定时器
                    startTimelapseFrameCapture(intervalMs)
                }
                .onFailure { error ->
                    Log.e(TAG, "resumeTimelapse: 恢复失败", error)
                    _events.emit(CameraEvent.Error("延时摄影恢复失败: ${error.message}"))
                }
        }
    }

    /**
     * 切换延时摄影录制状态
     *
     * 便捷方法：如果正在录制则停止，否则开始录制
     */
    fun toggleTimelapse() {
        if (_uiState.value.isTimelapseRecording) {
            stopTimelapse()
        } else {
            startTimelapse()
        }
    }

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

    // ==================== 文档扫描模式控制 ====================

    /**
     * 设置文档扫描模式
     *
     * 设置文档扫描时使用的效果模式
     * 更新状态并应用到文档处理器
     *
     * @param mode 文档扫描模式
     */
    fun setDocumentScanMode(mode: com.qihao.filtercamera.data.processor.DocumentScanMode) {
        Log.d(TAG, "setDocumentScanMode: 切换扫描模式 -> ${mode.displayName}")
        _uiState.update { it.copy(documentScanMode = mode) }
        // 通知文档处理器切换模式（如果有实时预览需求）
        documentScanProcessor.setDefaultScanMode(mode)
    }

    /**
     * 切换到下一个文档扫描模式
     *
     * 循环切换：彩色 -> 灰度 -> 黑白 -> 自动增强 -> OCR就绪 -> 彩色
     *
     * @return 新的扫描模式
     */
    fun toggleDocumentScanMode(): com.qihao.filtercamera.data.processor.DocumentScanMode {
        val currentMode = _uiState.value.documentScanMode
        val modes = com.qihao.filtercamera.data.processor.DocumentScanMode.entries.toList()
        val currentIndex = modes.indexOf(currentMode)
        val nextIndex = (currentIndex + 1) % modes.size
        val nextMode = modes[nextIndex]
        Log.d(TAG, "toggleDocumentScanMode: ${currentMode.displayName} -> ${nextMode.displayName}")
        setDocumentScanMode(nextMode)
        return nextMode
    }

    /**
     * 获取当前文档扫描模式
     *
     * @return 当前扫描模式
     */
    fun getDocumentScanMode(): com.qihao.filtercamera.data.processor.DocumentScanMode =
        _uiState.value.documentScanMode

    /**
     * 切换文档自动捕获开关
     *
     * 开启时：检测到稳定的文档边界后自动拍照
     * 关闭时：手动点击拍照按钮
     *
     * @return 新的自动捕获状态
     */
    fun toggleDocumentAutoCapture(): Boolean {
        val newState = !_uiState.value.isDocumentAutoCapture
        Log.d(TAG, "toggleDocumentAutoCapture: 自动捕获 -> $newState")
        _uiState.update { it.copy(isDocumentAutoCapture = newState) }
        return newState
    }

    /**
     * 设置文档自动捕获状态
     *
     * @param enabled 是否启用自动捕获
     */
    fun setDocumentAutoCapture(enabled: Boolean) {
        Log.d(TAG, "setDocumentAutoCapture: 自动捕获 -> $enabled")
        _uiState.update { it.copy(isDocumentAutoCapture = enabled) }
    }

    /**
     * 获取文档自动捕获状态
     *
     * @return 是否启用自动捕获
     */
    fun isDocumentAutoCaptureEnabled(): Boolean =
        _uiState.value.isDocumentAutoCapture

    /**
     * 显示/隐藏文档扫描模式选择器
     *
     * @param visible 是否可见
     */
    fun showDocumentScanModeSelector(visible: Boolean) {
        Log.d(TAG, "showDocumentScanModeSelector: 选择器可见性 -> $visible")
        _uiState.update { it.copy(isDocumentScanModeSelectorVisible = visible) }
    }

    /**
     * 切换文档扫描模式选择器可见性
     *
     * @return 新的可见性状态
     */
    fun toggleDocumentScanModeSelector(): Boolean {
        val newState = !_uiState.value.isDocumentScanModeSelectorVisible
        Log.d(TAG, "toggleDocumentScanModeSelector: 选择器可见性 -> $newState")
        _uiState.update { it.copy(isDocumentScanModeSelectorVisible = newState) }
        return newState
    }

    // ==================== ML Kit 文档扫描 ====================

    /**
     * ML Kit 扫描状态
     *
     * 暴露给 UI 层观察扫描进度和结果
     */
    val mlKitScanState: StateFlow<ScanState> = mlKitDocumentScanner.scanState

    /**
     * 处理 ML Kit 扫描结果
     *
     * 由 Activity Result 回调时调用
     *
     * @param resultCode Activity 结果码
     * @param data Intent 数据
     */
    fun handleMLKitScanResult(resultCode: Int, data: android.content.Intent?) {
        Log.d(TAG, "handleMLKitScanResult: 处理ML Kit扫描结果 resultCode=$resultCode")
        mlKitDocumentScanner.handleScanResult(resultCode, data)
    }

    /**
     * 重置 ML Kit 扫描状态
     *
     * 在下次扫描前调用
     */
    fun resetMLKitScanState() {
        Log.d(TAG, "resetMLKitScanState: 重置ML Kit扫描状态")
        mlKitDocumentScanner.resetState()
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
        focusTimeoutJob?.cancel()                                         // 取消对焦超时Job
        timelapseJob?.cancel()                                            // 取消延时摄影帧捕获Job
        faceDetectionProcessor.release()
        documentScanProcessor.release()
        portraitBlurProcessor.release()                                   // 释放人像虚化处理器
        mlKitDocumentScanner.release()                                    // 释放ML Kit文档扫描器
        filterSelectorState.release()                                     // 释放滤镜选择器资源
        Log.d(TAG, "onCleared: 状态管理器摘要 - Pro: ${proModeState.getSettingsSummary()}")
        Log.d(TAG, "onCleared: 状态管理器摘要 - Filter: ${filterSelectorState.getStateSummary()}")
        Log.d(TAG, "onCleared: 人像虚化统计 - ${portraitBlurProcessor.getStatistics()}")
    }
}
