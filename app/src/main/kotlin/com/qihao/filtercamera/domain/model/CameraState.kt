/**
 * CameraState.kt - 相机状态模型
 *
 * 定义相机的各种状态，用于ViewModel状态管理
 * 包括基础拍摄状态、模式状态、人像/文档/专业模式参数
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.domain.model

import androidx.compose.ui.geometry.Offset
import com.qihao.filtercamera.data.processor.DocumentScanMode
import com.qihao.filtercamera.data.processor.FaceTrackingState
import com.qihao.filtercamera.domain.repository.ZoomRange

/**
 * 相机模式枚举
 *
 * 参考小米相机的模式设计：
 * - 拍照：标准拍照模式
 * - 录像：视频录制模式
 * - 人像：人像美化模式（人脸检测+自动聚焦）
 * - 文档：文档扫描模式（边缘检测+自动校正+扫描效果）
 * - 专业：手动参数调节模式（ISO/快门/白平衡等）
 *
 * @param displayName 显示名称（中文）
 * @param iconName Material Icons图标名称
 */
enum class CameraMode(
    val displayName: String,
    val iconName: String
) {
    PRO("专业", "tune"),
    PORTRAIT("人像", "portrait"),
    PHOTO("拍照", "photo_camera"),
    VIDEO("录像", "videocam"),
    TIMELAPSE("延时", "timelapse"),
    DOCUMENT("文档", "document_scanner"),
    NIGHT("夜景", "nightlight");

    companion object {
        /**
         * 获取所有模式列表（按显示顺序）
         * 注：TIMELAPSE已移除，不在UI中显示
         */
        fun getAllModes(): List<CameraMode> = listOf(
            PRO, PORTRAIT, PHOTO, VIDEO, DOCUMENT, NIGHT
        )

        /**
         * 获取默认模式
         */
        fun getDefault(): CameraMode = PHOTO

        /**
         * 判断是否为视频类模式
         */
        fun isVideoMode(mode: CameraMode): Boolean = mode == VIDEO

        /**
         * 判断是否支持拍照
         */
        fun canTakePhoto(mode: CameraMode): Boolean =
            mode == PHOTO || mode == PORTRAIT || mode == DOCUMENT || mode == NIGHT || mode == PRO

        /**
         * 判断是否为专业模式
         */
        fun isProMode(mode: CameraMode): Boolean = mode == PRO

        /**
         * 判断是否为人像模式
         */
        fun isPortraitMode(mode: CameraMode): Boolean = mode == PORTRAIT

        /**
         * 判断是否为文档模式
         */
        fun isDocumentMode(mode: CameraMode): Boolean = mode == DOCUMENT

        /**
         * 判断是否为夜景模式
         */
        fun isNightMode(mode: CameraMode): Boolean = mode == NIGHT

        /**
         * 判断是否为延时摄影模式
         */
        fun isTimelapseMode(mode: CameraMode): Boolean = mode == TIMELAPSE
    }
}

/**
 * 相机镜头枚举
 */
enum class CameraLens {
    BACK,   // 后置摄像头
    FRONT   // 前置摄像头
}

/**
 * 相机状态数据类
 *
 * @param mode 当前模式（拍照/录像/人像/文档/专业）
 * @param lens 当前镜头（前置/后置）
 * @param filterType 当前滤镜类型
 * @param filterIntensity 滤镜强度（0.0~1.0，1.0为全强度）
 * @param selectedFilterGroup 当前选中的滤镜分组
 * @param beautyLevel 当前美颜等级
 * @param isRecording 是否正在录像
 * @param isCapturing 是否正在拍照
 * @param isFilterSelectorVisible 滤镜选择器是否可见
 * @param isCaptureFlashVisible 拍照闪屏是否可见
 * @param isProPanelVisible 专业模式控制面板是否可见
 * @param isSettingsPanelExpanded 设置面板是否展开
 * @param timerMode 定时拍照模式（OFF/3s/5s/10s）
 * @param countdownSeconds 当前倒计时剩余秒数（0表示未在倒计时）
 * @param isCountingDown 是否正在倒计时中
 * @param proSettings 专业模式参数设置
 * @param advancedSettings 高级设置（HDR/微距/画幅/光圈/变焦）
 * @param zoomRange 设备支持的变焦范围
 * @param detectedFaces 检测到的人脸列表（人像模式）
 * @param documentBounds 检测到的文档边界（文档模式）
 * @param isHistogramVisible 直方图是否可见（专业模式下显示实时直方图）
 * @param focusPoint 当前对焦点位置（归一化坐标0.0~1.0，null表示无对焦点显示）
 * @param isFocusing 是否正在对焦中（用于显示对焦动画）
 * @param errorMessage 错误信息（如有）
 */
data class CameraState(
    val mode: CameraMode = CameraMode.PHOTO,
    val lens: CameraLens = CameraLens.BACK,
    val filterType: FilterType = FilterType.NONE,
    val filterIntensity: Float = 1.0f,                                   // 滤镜强度（0.0~1.0）
    val selectedFilterGroup: FilterGroup = FilterGroup.ORIGINAL,
    val beautyLevel: BeautyLevel = BeautyLevel.DEFAULT,
    val isRecording: Boolean = false,
    val isCapturing: Boolean = false,
    val isFilterSelectorVisible: Boolean = false,
    val isCaptureFlashVisible: Boolean = false,
    val isProPanelVisible: Boolean = false,                           // 专业模式面板可见性
    val isZoomSliderVisible: Boolean = false,                         // 变焦滑块可见性（默认收起）
    val isSettingsPanelExpanded: Boolean = false,                     // 设置面板展开状态
    val timerMode: TimerMode = TimerMode.OFF,                         // 定时拍照模式
    val countdownSeconds: Int = 0,                                    // 倒计时剩余秒数
    val isCountingDown: Boolean = false,                              // 是否正在倒计时
    val currentNightMode: NightMode = NightMode.OFF,                  // 当前夜景模式状态
    val isNightProcessing: Boolean = false,                           // 是否正在夜景处理中
    val nightProcessingProgress: Float = 0f,                          // 夜景处理进度（0.0~1.0）
    val isTimelapseRecording: Boolean = false,                        // 是否正在延时摄影录制
    val timelapseFramesCaptured: Int = 0,                             // 延时摄影已捕获帧数
    val timelapseElapsedMs: Long = 0L,                                // 延时摄影已用时间（毫秒）
    val isTimelapseEncoding: Boolean = false,                         // 是否正在延时摄影编码中
    val timelapseEncodingProgress: Float = 0f,                        // 延时摄影编码进度（0.0~1.0）
    val portraitBlurLevel: PortraitBlurLevel = PortraitBlurLevel.MEDIUM, // 人像虚化等级
    val isPortraitOverlayVisible: Boolean = true,                        // 人像模式覆盖层是否可见
    val isPortraitBlurProcessing: Boolean = false,                    // 是否正在人像虚化处理中
    val portraitBlurProgress: Float = 0f,                             // 人像虚化处理进度（0.0~1.0）
    val proSettings: ProModeSettings = ProModeSettings(),             // 专业模式参数
    val advancedSettings: CameraAdvancedSettings = CameraAdvancedSettings(), // 高级设置
    val zoomRange: ZoomRange = ZoomRange(),                           // 设备变焦范围
    val detectedFaces: List<FaceInfo> = emptyList(),                  // 人像模式人脸检测
    val faceTrackingState: FaceTrackingState = FaceTrackingState.IDLE, // 人脸追踪状态
    val documentBounds: DocumentBounds? = null,                       // 文档模式边界检测
    val documentScanMode: DocumentScanMode = DocumentScanMode.AUTO_ENHANCE, // 文档扫描模式
    val isDocumentAutoCapture: Boolean = false,                       // 是否启用文档自动捕获
    val isDocumentScanModeSelectorVisible: Boolean = false,           // 文档扫描模式选择器可见性
    val isHistogramVisible: Boolean = false,                          // 直方图是否可见（专业模式）
    val focusPoint: Offset? = null,                                   // 触摸对焦点位置（归一化坐标）
    val isFocusing: Boolean = false,                                  // 是否正在对焦中
    val focusExposureCompensation: Float = 0f,                        // 触摸对焦曝光补偿（-1.0到1.0）
    val errorMessage: String? = null
)

/**
 * 相机事件密封类
 * 用于ViewModel -> UI的单次事件
 */
sealed class CameraEvent {
    data class PhotoCaptured(val filePath: String) : CameraEvent()        // 拍照完成
    data class VideoRecorded(val filePath: String) : CameraEvent()        // 录像完成
    data class Error(val message: String) : CameraEvent()                 // 错误事件
    data object CameraSwitched : CameraEvent()                            // 镜头切换完成
}
