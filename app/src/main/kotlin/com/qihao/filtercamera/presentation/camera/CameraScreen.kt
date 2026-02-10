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
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.qihao.filtercamera.presentation.camera.components.CameraModeSelector
import com.qihao.filtercamera.presentation.camera.components.CompactCameraTopBar
import com.qihao.filtercamera.presentation.camera.components.CompactHistogramView
import com.qihao.filtercamera.presentation.camera.components.DocumentBoundsOverlay
import com.qihao.filtercamera.presentation.camera.components.DocumentModeHint
import com.qihao.filtercamera.presentation.camera.components.DocumentScanModeSelector
import com.qihao.filtercamera.presentation.camera.components.DocumentScanModeUI
import com.qihao.filtercamera.presentation.camera.components.FaceDetectionOverlay
import com.qihao.filtercamera.presentation.camera.components.FaceTrackingStateIndicator
import com.qihao.filtercamera.presentation.camera.components.FocusIndicator
import com.qihao.filtercamera.presentation.camera.components.NewCameraBottomControls
import com.qihao.filtercamera.presentation.camera.components.NightModeHint
import com.qihao.filtercamera.presentation.camera.components.NightProcessingIndicator
import com.qihao.filtercamera.presentation.camera.components.PermissionRequest
import com.qihao.filtercamera.presentation.camera.components.PortraitBlurProcessingIndicator
import com.qihao.filtercamera.presentation.camera.components.CompactPortraitControls
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
import com.qihao.filtercamera.data.processor.ScanState
import com.qihao.filtercamera.data.processor.MLKitScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    // 权限请求 - 根据Android版本请求不同的存储权限
    val permissionsState = rememberMultiplePermissionsState(
        permissions = buildList {
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.RECORD_AUDIO)
            // Android 13+ 使用细粒度媒体权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
            } else {
                // Android 12及以下使用传统存储权限
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
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

    // ML Kit 文档扫描器 ActivityResultLauncher
    val mlKitScannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        Log.d(TAG, "ML Kit Scanner result: resultCode=${result.resultCode}")
        viewModel.handleMLKitScanResult(result.resultCode, result.data)
    }

    // 观察 ML Kit 扫描状态
    val mlKitScanState by viewModel.mlKitScanState.collectAsState()

    // 协程作用域用于保存文件
    val scope = rememberCoroutineScope()

    // 处理 ML Kit 扫描结果
    LaunchedEffect(mlKitScanState) {
        when (val state = mlKitScanState) {
            is ScanState.Success -> {
                val result = state.result
                Log.d(TAG, "ML Kit 扫描成功: pages=${result.pageCount} hasPdf=${result.pdfUri != null}")

                // 保存扫描的图片到相册
                scope.launch {
                    try {
                        var savedCount = 0
                        for ((index, pageUri) in result.pages.withIndex()) {
                            val saved = saveScannedImageToGallery(context, pageUri, index + 1)
                            if (saved) savedCount++
                        }

                        // 保存 PDF（如果有）
                        var pdfSaved = false
                        if (result.pdfUri != null) {
                            pdfSaved = saveScannedPdfToDocuments(context, result.pdfUri)
                        }

                        withContext(Dispatchers.Main) {
                            val message = buildString {
                                append("扫描完成！")
                                if (savedCount > 0) append(" $savedCount 张图片已保存到相册")
                                if (pdfSaved) append("，PDF已保存到文档")
                            }
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "保存扫描结果失败", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                viewModel.resetMLKitScanState()                              // 重置状态
            }
            is ScanState.Error -> {
                Log.e(TAG, "ML Kit 扫描错误: ${state.message}")
                Toast.makeText(context, "扫描失败: ${state.message}", Toast.LENGTH_SHORT).show()
                viewModel.resetMLKitScanState()
            }
            is ScanState.Cancelled -> {
                Log.d(TAG, "ML Kit 扫描取消")
                viewModel.resetMLKitScanState()
            }
            else -> { /* Idle 或 Scanning 状态不处理 */ }
        }
    }

    // 主界面容器
    Box(modifier = Modifier.fillMaxSize()) {
        // 修复：使用firstOrNull避免NoSuchElementException
        val hasCameraPermission = permissionsState.permissions
            .firstOrNull { it.permission == Manifest.permission.CAMERA }
            ?.status?.isGranted ?: false

        if (hasCameraPermission) {
            CameraContent(                                                // 相机内容
                uiState = uiState,
                viewModel = viewModel,
                onNavigateToGallery = onNavigateToGallery,
                onNavigateToSettings = onNavigateToSettings,
                mlKitScannerLauncher = mlKitScannerLauncher               // ML Kit 扫描器
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
    mlKitScannerLauncher: androidx.activity.result.ActivityResultLauncher<IntentSenderRequest>,
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

            // 1.3 触摸对焦检测层 - 限制在预览区域内，仅响应预览区域的触摸
            // 修复：将触摸检测移到预览Box内，解决画幅外可点击对焦的问题
            if (!uiState.isSettingsPanelExpanded && !uiState.isFilterSelectorVisible &&
                !uiState.isZoomSliderVisible && !uiState.isModeMenuVisible &&
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
        }

        // 2. 对焦指示器覆盖层 - 显示黄色对焦框 + 亮度调节滑块
        FocusIndicator(
            focusPoint = uiState.focusPoint,
            isFocusing = uiState.isFocusing,
            exposureCompensation = uiState.focusExposureCompensation, // 曝光补偿值
            showExposureSlider = uiState.focusPoint != null,         // 有对焦点时显示滑块
            onExposureChange = viewModel::setFocusExposureCompensation, // 曝光调节回调
            modifier = Modifier.fillMaxSize()
        )

        // 2.1 透明点击层 - 点击预览区域空白处关闭所有弹窗
        // 仅当有弹窗展开时才显示此层
        if (uiState.isSettingsPanelExpanded || uiState.isFilterSelectorVisible ||
            uiState.isZoomSliderVisible || uiState.isModeMenuVisible ||              // 变焦滑块/模式菜单展开时
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

        // 3. 精简版 TopBar - 整合功能到模式菜单，根据模式显示不同功能
        CompactCameraTopBar(
            cameraMode = uiState.mode,                                    // 新增：传递当前相机模式
            flashMode = uiState.advancedSettings.flashMode,
            hdrMode = uiState.advancedSettings.hdrMode,
            timerMode = uiState.timerMode,
            aspectRatio = uiState.advancedSettings.aspectRatio,
            isFilterActive = uiState.filterType != FilterType.NONE,       // 滤镜非NONE时激活
            isModeMenuVisible = uiState.isModeMenuVisible,
            onFlashClick = { viewModel.toggleFlashMode() },
            onModeMenuClick = { viewModel.toggleModeMenu() },
            onHdrClick = { viewModel.setHdrMode(if (uiState.advancedSettings.hdrMode == HdrMode.ON) HdrMode.OFF else HdrMode.ON) },
            onTimerClick = { viewModel.toggleTimerMode() },
            onAspectRatioClick = {
                // 使用AspectRatio.next()循环切换所有画幅比例(4:3→16:9→全屏→1:1→3:2→4:3)
                viewModel.setAspectRatio(AspectRatio.next(uiState.advancedSettings.aspectRatio))
            },
            onFilterClick = { viewModel.toggleFilterSelector() },       // 滤镜按钮点击
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

            // 人像模式紧凑控制条（底部模式选择器上方）- 简洁设计，不遮挡预览
            CompactPortraitControls(
                beautyLevel = uiState.beautyLevel,
                blurLevel = uiState.portraitBlurLevel,
                onBeautyToggle = viewModel::toggleBeautyLevel,
                onBlurToggle = viewModel::togglePortraitBlurLevel,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = dimens.modeSelectorHeight + dimens.bottomBarHeight + dimens.spacing.xl)
            )

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
                    },
                    onAdvancedScanClick = {
                        // 启动 ML Kit 高级文档扫描
                        val activity = context as? Activity
                        if (activity != null) {
                            viewModel.showDocumentScanModeSelector(false) // 关闭选择器面板
                            viewModel.mlKitDocumentScanner.startScan(
                                activity = activity,
                                launcher = mlKitScannerLauncher
                            )
                        } else {
                            Log.w(TAG, "无法获取Activity实例")
                        }
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

// ==================== ML Kit 扫描结果保存辅助函数 ====================

/**
 * 保存扫描的图片到相册
 *
 * 将 ML Kit 扫描结果的图片 Uri 复制到系统相册
 *
 * @param context 上下文
 * @param sourceUri ML Kit 返回的图片 Uri
 * @param pageIndex 页码（用于文件命名）
 * @return 是否保存成功
 */
private suspend fun saveScannedImageToGallery(
    context: android.content.Context,
    sourceUri: Uri,
    pageIndex: Int
): Boolean = withContext(Dispatchers.IO) {
    try {
        // 生成文件名
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "SCAN_${timestamp}_P${pageIndex}.jpg"

        // 使用 MediaStore API 保存到相册
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/FilterCamera/Scans")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return@withContext false

        // 复制图片数据
        resolver.openInputStream(sourceUri)?.use { input ->
            resolver.openOutputStream(uri)?.use { output ->
                input.copyTo(output)
            }
        }

        // Android Q+ 标记完成
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }

        Log.d(TAG, "saveScannedImageToGallery: 图片保存成功 $fileName")
        true
    } catch (e: Exception) {
        Log.e(TAG, "saveScannedImageToGallery: 保存失败", e)
        false
    }
}

/**
 * 保存扫描的 PDF 到文档目录
 *
 * 将 ML Kit 扫描结果的 PDF Uri 复制到 Documents 目录
 *
 * @param context 上下文
 * @param sourceUri ML Kit 返回的 PDF Uri
 * @return 是否保存成功
 */
private suspend fun saveScannedPdfToDocuments(
    context: android.content.Context,
    sourceUri: Uri
): Boolean = withContext(Dispatchers.IO) {
    try {
        // 生成文件名
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "SCAN_${timestamp}.pdf"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android Q+ 使用 MediaStore
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/FilterCamera/Scans")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: return@withContext false

            // 复制 PDF 数据
            resolver.openInputStream(sourceUri)?.use { input ->
                resolver.openOutputStream(uri)?.use { output ->
                    input.copyTo(output)
                }
            }

            // 标记完成
            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)

            Log.d(TAG, "saveScannedPdfToDocuments: PDF 保存成功 (MediaStore) $fileName")
        } else {
            // Android Q 以下使用传统文件操作
            val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val scanDir = File(documentsDir, "FilterCamera/Scans")
            if (!scanDir.exists()) scanDir.mkdirs()

            val outputFile = File(scanDir, fileName)
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            }

            Log.d(TAG, "saveScannedPdfToDocuments: PDF 保存成功 (File) ${outputFile.absolutePath}")
        }
        true
    } catch (e: Exception) {
        Log.e(TAG, "saveScannedPdfToDocuments: 保存失败", e)
        false
    }
}
