/**
 * CameraScreen.kt - 相机页面（小米风格重构版）
 *
 * 相机主界面，组合各个UI组件
 * 包含：相机预览、小米风格底部控制栏、滤镜选择器、实时滤镜预览
 *
 * 设计特点：
 * - 全屏相机预览
 * - 实时滤镜预览叠加层
 * - 左下角：相册缩略图（显示最新照片，点击打开系统相册）
 * - 中间：拍照按钮
 * - 右下角：切换镜头按钮
 * - 上方：滚动模式TAB（拍照、录像、人像、文档、专业）
 * - 预览区右侧：滤镜按钮（魔法棒图标）
 * - 拍照闪屏动画
 * - 人像模式：人脸检测框
 * - 文档模式：文档边界框
 * - 专业模式：参数控制面板
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.presentation.camera

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.qihao.filtercamera.domain.model.CameraEvent
import com.qihao.filtercamera.domain.model.CameraMode
import com.qihao.filtercamera.domain.model.FilterType
import com.qihao.filtercamera.domain.model.AspectRatio
import com.qihao.filtercamera.domain.model.HdrMode
import com.qihao.filtercamera.presentation.camera.components.BeautySlider
import com.qihao.filtercamera.presentation.camera.components.CameraModeSelector
import com.qihao.filtercamera.presentation.camera.components.CompactHistogramView
import com.qihao.filtercamera.presentation.camera.components.DocumentBoundsOverlay
import com.qihao.filtercamera.presentation.camera.components.DocumentModeHint
import com.qihao.filtercamera.presentation.camera.components.DocumentScanModeSelector
import com.qihao.filtercamera.presentation.camera.components.DocumentScanModeUI
import com.qihao.filtercamera.presentation.camera.components.FaceDetectionOverlay
import com.qihao.filtercamera.presentation.camera.components.FaceTrackingStateIndicator
import com.qihao.filtercamera.presentation.camera.components.FocusIndicator
import com.qihao.filtercamera.presentation.camera.components.NewCameraBottomControls
import com.qihao.filtercamera.presentation.camera.components.NewCameraTopBar
import com.qihao.filtercamera.presentation.camera.components.NightModeHint
import com.qihao.filtercamera.presentation.camera.components.NightProcessingIndicator
import com.qihao.filtercamera.presentation.camera.components.PermissionRequest
import com.qihao.filtercamera.presentation.camera.components.PortraitBlurLevelSelector
import com.qihao.filtercamera.presentation.camera.components.PortraitBlurProcessingIndicator
import com.qihao.filtercamera.presentation.camera.components.PortraitModeHint
import com.qihao.filtercamera.presentation.camera.components.ProModeControlPanel
import com.qihao.filtercamera.presentation.camera.components.TimelapseControlPanel
import com.qihao.filtercamera.presentation.camera.components.TimelapseEncodingIndicator
import com.qihao.filtercamera.presentation.camera.components.TimelapseModeHint
import com.qihao.filtercamera.presentation.camera.components.TimerCountdownOverlay
import com.qihao.filtercamera.presentation.camera.components.ZoomIndicator
import com.qihao.filtercamera.presentation.camera.components.ZoomSlider
import com.qihao.filtercamera.presentation.camera.components.iOSFilterSelector
import com.qihao.filtercamera.presentation.common.theme.CameraTheme
import com.qihao.filtercamera.presentation.common.theme.rememberResponsiveDimens
import kotlinx.coroutines.flow.collectLatest

private const val TAG = "CameraScreen"  // 日志标签

/**
 * 相机页面
 *
 * @param viewModel 相机ViewModel
 * @param onNavigateToGallery 导航到相册回调（可选）
 * @param onNavigateToSettings 导航到设置页面回调（可选）
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    viewModel: CameraViewModel = hiltViewModel(),
    onNavigateToGallery: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // 权限请求
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    )

    // 处理事件（拍照完成、录像完成、错误等）
    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is CameraEvent.PhotoCaptured -> {                         // 拍照成功
                    Log.d(TAG, "PhotoCaptured: ${event.filePath}")
                    Toast.makeText(context, "照片已保存", Toast.LENGTH_SHORT).show()
                }
                is CameraEvent.VideoRecorded -> {                         // 录像成功
                    Log.d(TAG, "VideoRecorded: ${event.filePath}")
                    Toast.makeText(context, "视频已保存", Toast.LENGTH_SHORT).show()
                }
                is CameraEvent.Error -> {                                 // 错误
                    Log.e(TAG, "Error: ${event.message}")
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is CameraEvent.CameraSwitched -> {                        // 摄像头切换
                    Log.d(TAG, "CameraSwitched")
                }
            }
        }
    }

    // 请求权限
    LaunchedEffect(Unit) {
        if (!permissionsState.allPermissionsGranted) {
            permissionsState.launchMultiplePermissionRequest()
        }
    }

    // 主界面容器
    Box(modifier = Modifier.fillMaxSize()) {
        val hasCameraPermission = permissionsState.permissions
            .first { it.permission == Manifest.permission.CAMERA }
            .status.isGranted

        if (hasCameraPermission) {
            CameraContent(                                                // 相机内容
                uiState = uiState,
                viewModel = viewModel,
                onNavigateToGallery = onNavigateToGallery,
                onNavigateToSettings = onNavigateToSettings
            )
        } else {
            PermissionRequest(                                            // 权限请求UI
                onRequestPermission = {
                    permissionsState.launchMultiplePermissionRequest()
                },
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

/**
 * 相机内容（已授权后显示）
 *
 * 小米风格布局结构：
 * - 相机预览（全屏）
 * - 滤镜预览叠加层（当有滤镜时显示）
 * - 预览区右侧中间：滤镜按钮（魔法棒图标）
 * - 底部：模式TAB选择器（滚动）
 * - 底部：[相册] [快门] [切换镜头]
 * - 滤镜选择器（AnimatedVisibility从底部滑出）
 * - 拍照闪屏动画
 */
@Composable
private fun CameraContent(
    uiState: com.qihao.filtercamera.domain.model.CameraState,
    viewModel: CameraViewModel,
    onNavigateToGallery: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current                                    // 获取Context用于打开相册
    val dimens = rememberResponsiveDimens()                               // 响应式尺寸系统

    // 订阅滤镜帧
    val filteredFrame by viewModel.filteredFrame.collectAsState()

    // 订阅滤镜缩略图
    val filterThumbnails by viewModel.filterThumbnails.collectAsState()

    // 订阅相册缩略图
    val galleryThumbnail by viewModel.galleryThumbnail.collectAsState()

    // 订阅直方图数据（专业模式）
    val histogramData by viewModel.histogramData.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CameraTheme.Colors.background)                    // 使用主题背景色
    ) {
        // 1. 相机预览容器 - 根据画幅比例调整大小
        // 计算预览区域的修饰符
        val previewModifier = when (uiState.advancedSettings.aspectRatio) {
            AspectRatio.RATIO_1_1 -> Modifier
                .fillMaxWidth()
                .aspectRatio(1f)                                          // 1:1 正方形
                .align(Alignment.Center)
            AspectRatio.RATIO_3_2 -> Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)                                     // 3:2 竖屏 = 宽:高 = 2:3
                .align(Alignment.Center)
            AspectRatio.RATIO_4_3 -> Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)                                     // 4:3 竖屏 = 宽:高 = 3:4
                .align(Alignment.Center)
            AspectRatio.RATIO_16_9 -> Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)                                    // 16:9 竖屏 = 宽:高 = 9:16
                .align(Alignment.Center)
            AspectRatio.RATIO_FULL -> Modifier
                .fillMaxSize()                                            // 全屏填满
        }

        // 预览区域Box（包含相机预览和滤镜叠加层）
        Box(
            modifier = previewModifier,
            contentAlignment = Alignment.Center
        ) {
            // 1.1 相机预览
            CameraPreview(
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize()
            )

            // 1.2 滤镜预览叠加层（当有滤镜时显示处理后的帧）
            filteredFrame?.let { bitmap ->
                FilteredFrameOverlay(
                    bitmap = bitmap,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 2.3 触摸对焦检测层 - 仅在没有弹窗时响应触摸对焦
        // 用户触摸预览区域时，对焦框跟随手指移动，抬起时触发对焦
        if (!uiState.isSettingsPanelExpanded && !uiState.isFilterSelectorVisible &&
            !uiState.isZoomSliderVisible &&
            !(uiState.mode == CameraMode.PRO && uiState.isProPanelVisible)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            // 等待按下事件
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val downOffset = down.position
                            val normalizedX = downOffset.x / size.width
                            val normalizedY = downOffset.y / size.height
                            // 按下时立即显示对焦框
                            viewModel.updateFocusPointPreview(normalizedX, normalizedY)

                            var lastOffset = downOffset
                            // 追踪拖动过程
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull()
                                if (change != null && change.pressed) {
                                    // 手指移动时更新对焦框位置
                                    lastOffset = change.position
                                    val dragNormalizedX = lastOffset.x / size.width
                                    val dragNormalizedY = lastOffset.y / size.height
                                    viewModel.updateFocusPointPreview(dragNormalizedX, dragNormalizedY)
                                }
                            } while (event.changes.any { it.pressed })

                            // 手指抬起时触发实际对焦
                            val releaseNormalizedX = lastOffset.x / size.width
                            val releaseNormalizedY = lastOffset.y / size.height
                            Log.d(TAG, "触摸对焦: 抬起位置=($releaseNormalizedX, $releaseNormalizedY)")
                            viewModel.onPreviewTouchFocus(releaseNormalizedX, releaseNormalizedY)
                        }
                    }
            )
        }

        // 2.4 对焦指示器覆盖层 - 显示黄色对焦框 + 亮度调节滑块
        FocusIndicator(
            focusPoint = uiState.focusPoint,
            isFocusing = uiState.isFocusing,
            exposureCompensation = uiState.focusExposureCompensation, // 曝光补偿值
            showExposureSlider = uiState.focusPoint != null,         // 有对焦点时显示滑块
            onExposureChange = viewModel::setFocusExposureCompensation, // 曝光调节回调
            modifier = Modifier.fillMaxSize()
        )

        // 2.5 透明点击层 - 点击预览区域空白处关闭所有弹窗
        // 仅当有弹窗展开时才显示此层
        if (uiState.isSettingsPanelExpanded || uiState.isFilterSelectorVisible ||
            uiState.isZoomSliderVisible ||                                       // 变焦滑块展开时
            (uiState.mode == CameraMode.PRO && uiState.isProPanelVisible) ||
            (uiState.mode == CameraMode.PORTRAIT && uiState.isPortraitOverlayVisible)) { // 人像模式覆盖层可见时
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,                                    // 无点击涟漪效果
                        onClick = { viewModel.onPreviewTapped() }             // 关闭所有弹窗
                    )
            )
        }

        NewCameraTopBar(
            flashMode = uiState.advancedSettings.flashMode,
            hdrMode = uiState.advancedSettings.hdrMode,
            timerMode = uiState.timerMode,
            aspectRatio = uiState.advancedSettings.aspectRatio,
            isFilterActive = uiState.filterType != FilterType.NONE,       // 滤镜非NONE时激活
            onFlashClick = { viewModel.toggleFlashMode() },
            onHdrClick = { viewModel.setHdrMode(if (uiState.advancedSettings.hdrMode == HdrMode.ON) HdrMode.OFF else HdrMode.ON) },
            onTimerClick = { viewModel.toggleTimerMode() },
            onFilterClick = { viewModel.toggleFilterSelector() },       // 滤镜按钮点击
            onAspectRatioClick = {
                // 使用AspectRatio.next()循环切换所有画幅比例(4:3→16:9→全屏→1:1→3:2→4:3)
                viewModel.setAspectRatio(AspectRatio.next(uiState.advancedSettings.aspectRatio))
            },
            onSettingsClick = onNavigateToSettings,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = dimens.spacing.lg)                         // 响应式顶部间距
        )

        // 4. 变焦指示器和滑块已移至底部 Column 内部，位于滤镜选择器下方

        // 5. 人像模式：人脸检测框覆盖层
        if (uiState.mode == CameraMode.PORTRAIT && uiState.detectedFaces.isNotEmpty()) {
            FaceDetectionOverlay(
                faces = uiState.detectedFaces,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 6. 文档模式：文档边界框覆盖层
        if (uiState.mode == CameraMode.DOCUMENT) {
            DocumentBoundsOverlay(
                bounds = uiState.documentBounds,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 6.5. 定时拍照倒计时覆盖层（全屏显示大数字）
        TimerCountdownOverlay(
            isVisible = uiState.isCountingDown,
            countdownSeconds = uiState.countdownSeconds,
            onCancel = viewModel::cancelCountdown,
            modifier = Modifier.fillMaxSize()
        )


        // 8. 人像模式提示（顶部状态栏下方）
        if (uiState.mode == CameraMode.PORTRAIT) {
            PortraitModeHint(
                faceCount = uiState.detectedFaces.size,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = dimens.topBarHeight + dimens.spacing.xl) // 响应式：TopBar高度+间距
            )

            // 人像模式美颜滑块（预览区左侧）- 仅在覆盖层可见时显示
            if (uiState.isPortraitOverlayVisible) {
                BeautySlider(
                    currentLevel = uiState.beautyLevel,
                    onLevelChanged = viewModel::setBeautyLevel,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = dimens.spacing.lg)                   // 响应式间距
                        .width(dimens.zoomSliderHeight)                       // 响应式宽度（与变焦滑块高度一致）
                )

                // 人像模式虚化等级选择器（预览区右侧）
                PortraitBlurLevelSelector(
                    currentLevel = uiState.portraitBlurLevel,
                    onLevelSelected = viewModel::setPortraitBlurLevel,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = dimens.spacing.lg)                     // 响应式间距
                )
            }

            // 人脸追踪状态指示器（预览区右上角）
            FaceTrackingStateIndicator(
                trackingState = uiState.faceTrackingState,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = dimens.topBarHeight + dimens.spacing.xl, end = dimens.spacing.lg)
            )
        }

        // 8.5 人像虚化处理进度指示器（全屏遮罩，处理时显示）
        PortraitBlurProcessingIndicator(
            isProcessing = uiState.isPortraitBlurProcessing,              // 是否正在虚化处理
            progress = uiState.portraitBlurProgress,                      // 处理进度（0.0~1.0）
            modifier = Modifier.fillMaxSize()                             // 全屏覆盖
        )

        // 9. 文档模式提示（顶部状态栏下方）
        if (uiState.mode == CameraMode.DOCUMENT) {
            DocumentModeHint(
                isDetected = uiState.documentBounds != null,
                confidence = uiState.documentBounds?.confidence ?: 0f,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = dimens.spacing.lg)                     // 响应式间距
            )

            // 文档扫描模式切换按钮（预览区左侧中间）
            IconButton(
                onClick = viewModel::toggleDocumentScanModeSelector,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (uiState.isDocumentScanModeSelectorVisible)
                        CameraTheme.Colors.primary.copy(alpha = 0.8f)     // 激活时使用主题色
                    else
                        CameraTheme.Colors.controlBackground              // 未激活时使用控件背景色
                ),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = dimens.spacing.lg)
            ) {
                Icon(
                    imageVector = Icons.Filled.BarChart,
                    contentDescription = "切换扫描模式",
                    tint = CameraTheme.Colors.textPrimary                  // 使用主题文字色
                )
            }
        }

        // 10. 夜景模式提示（顶部状态栏下方）
        if (uiState.mode == CameraMode.NIGHT) {
            NightModeHint(
                isOptimizing = uiState.isCapturing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = dimens.spacing.lg)                     // 响应式间距
            )
        }

        // 10.5 夜景处理进度指示器（全屏遮罩，处理时显示）
        NightProcessingIndicator(
            isProcessing = uiState.isNightProcessing,                     // 是否正在夜景处理
            progress = uiState.nightProcessingProgress,                   // 处理进度（0.0~1.0）
            modifier = Modifier.fillMaxSize()                             // 全屏覆盖
        )

        // 10.6 延时摄影模式提示（顶部状态栏下方）
        if (uiState.mode == CameraMode.TIMELAPSE) {
            TimelapseModeHint(
                isRecording = uiState.isTimelapseRecording,
                framesCaptured = uiState.timelapseFramesCaptured,
                elapsedMs = uiState.timelapseElapsedMs,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = dimens.topBarHeight + dimens.spacing.xl) // 响应式：TopBar高度+间距
            )
        }

        // 10.7 延时摄影编码进度指示器（全屏遮罩，编码时显示）
        TimelapseEncodingIndicator(
            isEncoding = uiState.isTimelapseEncoding,                     // 是否正在编码
            progress = uiState.timelapseEncodingProgress,                 // 编码进度（0.0~1.0）
            modifier = Modifier.fillMaxSize()                             // 全屏覆盖
        )

        // 11. 实时直方图显示（专业模式右上角）
        AnimatedVisibility(
            visible = uiState.isHistogramVisible && uiState.mode == CameraMode.PRO,
            enter = fadeIn(animationSpec = tween(dimens.animation.fast)),
            exit = fadeOut(animationSpec = tween(dimens.animation.instant + 50)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = dimens.topBarHeight + dimens.spacing.xl, end = dimens.spacing.lg)
        ) {
            CompactHistogramView(
                histogramData = histogramData
            )
        }

        // 12. 直方图切换按钮（专业模式）
        if (uiState.mode == CameraMode.PRO) {
            IconButton(
                onClick = viewModel::toggleHistogram,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (uiState.isHistogramVisible)
                        CameraTheme.Colors.primary.copy(alpha = 0.8f)     // 激活时使用主题色
                    else
                        CameraTheme.Colors.controlBackground              // 未激活时使用控件背景色
                ),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(
                        top = if (uiState.isHistogramVisible)
                            dimens.topBarHeight + dimens.histogramHeight + dimens.spacing.xl + dimens.spacing.md
                        else
                            dimens.topBarHeight + dimens.spacing.xl,
                        end = dimens.spacing.lg
                    )
            ) {
                Icon(
                    imageVector = Icons.Filled.BarChart,
                    contentDescription = "切换直方图",
                    tint = CameraTheme.Colors.textPrimary                  // 使用主题文字色
                )
            }
        }

        // 9. 底部控制区域（包含专业模式面板、滤镜选择器和控制栏）
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()                                  // 导航栏安全区域
        ) {
            // 专业模式控制面板（从下往上弹出动画）
            AnimatedVisibility(
                visible = uiState.mode == CameraMode.PRO && uiState.isProPanelVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(dimens.animation.normal, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(dimens.animation.fast)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(dimens.animation.fast + 100, easing = FastOutLinearInEasing)
                ) + fadeOut(animationSpec = tween(dimens.animation.instant + 50))
            ) {
                ProModeControlPanel(
                    settings = uiState.proSettings,
                    onIsoChanged = viewModel::setProIso,
                    onShutterChanged = viewModel::setProShutterSpeed,
                    onEvChanged = viewModel::setProExposureCompensation,
                    onWbChanged = viewModel::setProWhiteBalance,
                    onFocusModeChanged = viewModel::setProFocusMode,
                    onFocusDistanceChanged = viewModel::setProFocusDistance
                )
            }

            // 延时摄影控制面板（从下往上弹出动画）
            AnimatedVisibility(
                visible = uiState.mode == CameraMode.TIMELAPSE,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(dimens.animation.normal, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(dimens.animation.fast)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(dimens.animation.fast + 100, easing = FastOutLinearInEasing)
                ) + fadeOut(animationSpec = tween(dimens.animation.instant + 50))
            ) {
                TimelapseControlPanel(
                    isRecording = uiState.isTimelapseRecording,           // 是否正在录制
                    framesCaptured = uiState.timelapseFramesCaptured,     // 已捕获帧数
                    elapsedMs = uiState.timelapseElapsedMs,               // 已用时间（毫秒）
                    onStart = viewModel::startTimelapse,                  // 开始录制回调
                    onStop = viewModel::stopTimelapse,                    // 停止录制回调
                    onCancel = viewModel::cancelTimelapse                 // 取消录制回调
                )
            }

            // 文档扫描模式选择器（从下往上弹出动画）
            AnimatedVisibility(
                visible = uiState.mode == CameraMode.DOCUMENT && uiState.isDocumentScanModeSelectorVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(dimens.animation.normal, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(dimens.animation.fast)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(dimens.animation.fast + 100, easing = FastOutLinearInEasing)
                ) + fadeOut(animationSpec = tween(dimens.animation.instant + 50))
            ) {
                DocumentScanModeSelector(
                    currentMode = DocumentScanModeUI.fromDataMode(uiState.documentScanMode),
                    onModeSelected = { uiMode ->
                        viewModel.setDocumentScanMode(uiMode.toDataMode())// 转换并设置
                        viewModel.showDocumentScanModeSelector(false)     // 选择后关闭面板
                    }
                )
            }

            // 滤镜选择器（从下往上弹出动画）
            AnimatedVisibility(
                visible = uiState.isFilterSelectorVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(dimens.animation.normal, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(dimens.animation.fast)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(dimens.animation.fast + 100, easing = FastOutLinearInEasing)
                ) + fadeOut(animationSpec = tween(dimens.animation.instant + 50))
            ) {
                iOSFilterSelector(
                    groups = viewModel.availableGroups,
                    selectedGroup = uiState.selectedFilterGroup,
                    filters = viewModel.getFiltersByGroup(uiState.selectedFilterGroup),
                    selectedFilter = uiState.filterType,
                    thumbnails = filterThumbnails,
                    filterIntensity = uiState.filterIntensity,
                    onGroupSelected = { group ->
                        viewModel.selectFilterGroup(group)
                        viewModel.generateFilterThumbnails()              // 切换分组时重新生成缩略图
                    },
                    onFilterSelected = viewModel::selectFilter,
                    onIntensityChanged = viewModel::setFilterIntensity
                )
            }

            // 变焦滑块（展开时显示，位于滤镜选择器下方）
            AnimatedVisibility(
                visible = uiState.isZoomSliderVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(dimens.animation.normal, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(dimens.animation.fast)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(dimens.animation.fast + 100, easing = FastOutLinearInEasing)
                ) + fadeOut(animationSpec = tween(dimens.animation.instant + 50))
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    ZoomSlider(
                        currentZoom = uiState.advancedSettings.zoomLevel,
                        zoomRange = uiState.zoomRange,
                        onZoomChanged = viewModel::setZoomLevel
                    )
                }
            }

            // 变焦指示器（位于滤镜选择器下方、模式选择器上方）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = dimens.spacing.sm),
                contentAlignment = Alignment.Center
            ) {
                ZoomIndicator(
                    currentZoom = uiState.advancedSettings.zoomLevel,
                    isExpanded = uiState.isZoomSliderVisible,
                    onClick = viewModel::toggleZoomSlider
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CameraModeSelector(
                    currentMode = uiState.mode,
                    onModeSelected = viewModel::selectMode
                )
                NewCameraBottomControls(
                    galleryThumbnail = galleryThumbnail,
                    onGalleryClick = {
                        try {
                            val intent = Intent(Intent.ACTION_PICK).apply {
                                setDataAndType(
                                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                    "image/*"
                                )
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            try {
                                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(
                                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                        "image/*"
                                    )
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(fallbackIntent)
                            } catch (e2: Exception) {
                                Log.e(TAG, "打开相册失败: ${e2.message}")
                                Toast.makeText(context, "无法打开相册", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onShutterClick = {
                        if (CameraMode.isVideoMode(uiState.mode)) {
                            viewModel.toggleRecording()
                        } else {
                            viewModel.takePhoto()
                        }
                    },
                    onSwitchCameraClick = viewModel::switchCamera
                )
            }
        }
    }
}

/**
 * 滤镜预览叠加层
 *
 * 显示应用滤镜后的Bitmap，覆盖在相机预览之上
 *
 * @param bitmap 滤镜处理后的Bitmap
 * @param modifier 修饰符
 */
@Composable
private fun FilteredFrameOverlay(
    bitmap: Bitmap,
    modifier: Modifier = Modifier
) {
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "滤镜预览",
        modifier = modifier,
        contentScale = ContentScale.Crop                                  // 裁剪填充，保持比例
    )
}

/**
 * 相机预览组件
 *
 * 使用CameraX PreviewView显示相机画面
 *
 * @param viewModel 相机ViewModel，用于绑定相机
 * @param modifier 修饰符
 */
@Composable
fun CameraPreview(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER                 // 填充中心，保持比例
        }
    }

    // 绑定相机到PreviewView
    LaunchedEffect(previewView) {
        Log.d(TAG, "CameraPreview: 开始绑定相机到PreviewView")
        viewModel.bindCamera(lifecycleOwner, previewView)
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}
