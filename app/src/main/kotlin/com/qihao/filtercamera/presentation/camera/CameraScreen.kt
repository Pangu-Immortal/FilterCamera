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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.qihao.filtercamera.presentation.camera.components.BeautySlider
import com.qihao.filtercamera.presentation.camera.components.CameraTopSection
import com.qihao.filtercamera.presentation.camera.components.CompactHistogramView
import com.qihao.filtercamera.presentation.camera.components.DocumentBoundsOverlay
import com.qihao.filtercamera.presentation.camera.components.DocumentModeHint
import com.qihao.filtercamera.presentation.camera.components.FaceDetectionOverlay
import com.qihao.filtercamera.presentation.camera.components.NightModeHint
import com.qihao.filtercamera.presentation.camera.components.PermissionRequest
import com.qihao.filtercamera.presentation.camera.components.PortraitModeHint
import com.qihao.filtercamera.presentation.camera.components.ProModeControlPanel
import com.qihao.filtercamera.presentation.camera.components.TimerCountdownOverlay
import com.qihao.filtercamera.presentation.camera.components.XiaomiBottomControls
import com.qihao.filtercamera.presentation.camera.components.XiaomiCaptureFlash
import com.qihao.filtercamera.presentation.camera.components.XiaomiFilterButton
import com.qihao.filtercamera.presentation.camera.components.ZoomIndicator
import com.qihao.filtercamera.presentation.camera.components.ZoomSlider
import com.qihao.filtercamera.presentation.camera.components.iOSFilterSelector
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
            .background(Color.Black)                                      // 黑色背景
    ) {
        // 1. 相机预览 - 全屏
        CameraPreview(
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize()
        )

        // 2. 滤镜预览叠加层（当有滤镜时显示处理后的帧）
        filteredFrame?.let { bitmap ->
            FilteredFrameOverlay(
                bitmap = bitmap,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 2.5 透明点击层 - 点击预览区域空白处关闭所有弹窗
        // 仅当有弹窗展开时才显示此层
        if (uiState.isSettingsPanelExpanded || uiState.isFilterSelectorVisible ||
            uiState.isZoomSliderVisible ||                                       // 新增：变焦滑块展开时
            (uiState.mode == CameraMode.PRO && uiState.isProPanelVisible)) {
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

        // 3. 顶部区域：设置面板（箭头展开）
        CameraTopSection(
            isPanelExpanded = uiState.isSettingsPanelExpanded,
            settings = uiState.advancedSettings,
            timerMode = uiState.timerMode,
            onTogglePanel = viewModel::toggleSettingsPanel,
            onFlashModeChanged = viewModel::setFlashMode,                // 闪光灯控制回调
            onHdrModeChanged = viewModel::setHdrMode,
            onMacroModeChanged = viewModel::setMacroMode,
            onAspectRatioChanged = viewModel::setAspectRatio,
            onApertureModeChanged = viewModel::setApertureMode,
            onTimerModeChanged = viewModel::setTimerMode,
            onSettingsClick = onNavigateToSettings,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
        )

        // 4. 变焦指示器（始终显示的胶囊）+ 可收回变焦滑块
        // 指示器位于预览区域中下方
        ZoomIndicator(
            currentZoom = uiState.advancedSettings.zoomLevel,
            isExpanded = uiState.isZoomSliderVisible,
            onClick = viewModel::toggleZoomSlider,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 270.dp)                                            // 在模式标签上方
        )

        // 完整变焦滑块（AnimatedVisibility展开）
        AnimatedVisibility(
            visible = uiState.isZoomSliderVisible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(200)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(250, easing = FastOutLinearInEasing)
            ) + fadeOut(animationSpec = tween(150)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 200.dp)                                            // 在指示器上方
        ) {
            ZoomSlider(
                currentZoom = uiState.advancedSettings.zoomLevel,
                zoomRange = uiState.zoomRange,
                onZoomChanged = viewModel::setZoomLevel
            )
        }

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

        // 7. 拍照闪屏动画（覆盖全屏）
        XiaomiCaptureFlash(
            isVisible = uiState.isCaptureFlashVisible,
            modifier = Modifier.fillMaxSize()
        )

        // 8. 人像模式提示（顶部状态栏下方）
        if (uiState.mode == CameraMode.PORTRAIT) {
            PortraitModeHint(
                faceCount = uiState.detectedFaces.size,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 80.dp)                                 // 在设置面板下方
            )

            // 人像模式美颜滑块（预览区左侧）
            BeautySlider(
                currentLevel = uiState.beautyLevel,
                onLevelChanged = viewModel::setBeautyLevel,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp)
                    .width(200.dp)
            )
        }

        // 9. 文档模式提示（顶部状态栏下方）
        if (uiState.mode == CameraMode.DOCUMENT) {
            DocumentModeHint(
                isDetected = uiState.documentBounds != null,
                confidence = uiState.documentBounds?.confidence ?: 0f,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 16.dp)
            )
        }

        // 10. 夜景模式提示（顶部状态栏下方）
        if (uiState.mode == CameraMode.NIGHT) {
            NightModeHint(
                isOptimizing = uiState.isCapturing,                       // 拍摄时显示优化中
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 16.dp)
            )
        }

        // 11. 实时直方图显示（专业模式右上角）
        AnimatedVisibility(
            visible = uiState.isHistogramVisible && uiState.mode == CameraMode.PRO,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(150)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 80.dp, end = 16.dp)                        // 在设置面板下方
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
                        Color(0xFFFFCC00).copy(alpha = 0.8f)              // 激活时黄色
                    else
                        Color.Black.copy(alpha = 0.5f)                    // 未激活时半透明黑色
                ),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = if (uiState.isHistogramVisible) 150.dp else 80.dp, end = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.BarChart,
                    contentDescription = "切换直方图",
                    tint = Color.White
                )
            }
        }

        // 8. 预览区右侧中间：滤镜按钮（魔法棒图标）
        XiaomiFilterButton(
            isActive = uiState.filterType != FilterType.NONE,
            onClick = viewModel::toggleFilterSelector,
            modifier = Modifier
                .align(Alignment.CenterEnd)                               // 右侧居中
                .padding(end = 16.dp)                                     // 右边距
        )

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
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(200)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(250, easing = FastOutLinearInEasing)
                ) + fadeOut(animationSpec = tween(150))
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

            // 滤镜选择器（从下往上弹出动画）
            AnimatedVisibility(
                visible = uiState.isFilterSelectorVisible,
                enter = slideInVertically(                                // 从底部滑入
                    initialOffsetY = { it },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(200)),
                exit = slideOutVertically(                                // 向底部滑出
                    targetOffsetY = { it },
                    animationSpec = tween(250, easing = FastOutLinearInEasing)
                ) + fadeOut(animationSpec = tween(150))
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

            // 小米风格底部控制栏
            XiaomiBottomControls(
                currentMode = uiState.mode,
                isRecording = uiState.isRecording,
                isCapturing = uiState.isCapturing,
                galleryThumbnail = galleryThumbnail,
                onModeSelected = viewModel::selectMode,
                onCapture = {
                    if (CameraMode.isVideoMode(uiState.mode)) {
                        viewModel.toggleRecording()
                    } else {
                        viewModel.takePhoto()
                    }
                },
                onGalleryClick = {                                        // 打开系统相册
                    try {
                        // 方式1：尝试打开系统相册应用
                        val intent = Intent(Intent.ACTION_PICK).apply {
                            setDataAndType(
                                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                "image/*"
                            )
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // 方式2：如果失败，尝试打开任意图片查看器
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
                onSwitchCamera = viewModel::switchCamera
            )
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
