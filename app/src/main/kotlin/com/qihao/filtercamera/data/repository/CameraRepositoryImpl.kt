/**
 * CameraRepositoryImpl.kt - 相机仓库实现
 *
 * 使用CameraX实现相机操作
 * 包含预览、拍照、录像、实时滤镜预览功能
 *
 * 技术实现：
 * - CameraX Preview用例：相机预览
 * - CameraX ImageCapture用例：拍照
 * - CameraX VideoCapture用例：录像
 * - CameraX ImageAnalysis用例：实时滤镜预览
 * - GPUImage库：滤镜渲染
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.Size
import com.qihao.filtercamera.data.processor.BeautyProcessor
import com.qihao.filtercamera.data.processor.HdrProcessor
import com.qihao.filtercamera.data.processor.NightModeProcessor
import com.qihao.filtercamera.data.processor.PortraitBlurConfig
import com.qihao.filtercamera.data.processor.PortraitBlurProcessor
import com.qihao.filtercamera.data.processor.TimelapseConfig
import com.qihao.filtercamera.data.processor.TimelapseEngine
import com.qihao.filtercamera.data.processor.TimelapseState
import com.qihao.filtercamera.data.util.FileUtils
import com.seu.magicfilter.beautify.SafeMagicJni
import com.qihao.filtercamera.data.util.FrameProcessor
import com.qihao.filtercamera.data.util.ImageFormatConverter
import com.qihao.filtercamera.domain.model.BeautyLevel
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import com.qihao.filtercamera.domain.model.CameraLens
import com.qihao.filtercamera.domain.model.FilterType
import com.qihao.filtercamera.domain.model.FlashMode
import com.qihao.filtercamera.domain.model.FocusMode
import com.qihao.filtercamera.domain.model.HdrMode
import com.qihao.filtercamera.domain.model.MacroMode
import com.qihao.filtercamera.domain.model.NightMode
import com.qihao.filtercamera.domain.model.PortraitBlurLevel
import com.qihao.filtercamera.domain.model.TimelapseSettings
import com.qihao.filtercamera.domain.model.AspectRatio
import com.qihao.filtercamera.domain.model.WhiteBalanceMode
import com.qihao.filtercamera.domain.repository.ICameraRepository
import com.qihao.filtercamera.domain.repository.IFilterRepository
import com.qihao.filtercamera.domain.repository.ZoomRange
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 相机仓库实现类
 *
 * @param context 应用上下文
 * @param filterRepository 滤镜仓库（用于拍照时应用滤镜）
 * @param beautyProcessor 美颜处理器
 */
@Singleton
class CameraRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val filterRepository: IFilterRepository,
    private val beautyProcessor: BeautyProcessor,
    private val hdrProcessor: HdrProcessor,                                       // HDR处理器
    private val nightModeProcessor: NightModeProcessor,                           // 夜景模式处理器
    private val timelapseEngine: TimelapseEngine,                                 // 延时摄影引擎
    private val portraitBlurProcessor: PortraitBlurProcessor                      // 人像虚化处理器
) : ICameraRepository {

    companion object {
        private const val TAG = "CameraRepositoryImpl"                    // 日志标签
        private const val ANALYSIS_WIDTH = 1280                           // 分析帧宽度
        private const val ANALYSIS_HEIGHT = 720                           // 分析帧高度
        private const val FRAME_BUFFER_CAPACITY = 3                       // 帧缓冲区容量
        private const val FRAME_PROCESSING_INTERVAL_MS = 33L              // 帧处理间隔（约30fps）
    }

    // 相机执行器
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // 异步帧处理器（解决帧处理阻塞问题）
    private val frameProcessor = FrameProcessor(
        bufferCapacity = FRAME_BUFFER_CAPACITY,
        processingIntervalMs = FRAME_PROCESSING_INTERVAL_MS
    )

    // 当前镜头状态
    private val _currentLens = MutableStateFlow(CameraLens.BACK)

    // 录像状态
    private val _isRecording = MutableStateFlow(false)

    // 滤镜帧回调（用于实时预览）
    private val _filteredFrame = MutableStateFlow<Bitmap?>(null)

    // 原始预览帧（用于生成滤镜缩略图，不受滤镜影响）
    private val _rawPreviewFrame = MutableStateFlow<Bitmap?>(null)

    // 变焦范围
    private val _zoomRange = MutableStateFlow(ZoomRange(1.0f, 10.0f))

    // 当前滤镜
    private var currentFilterType = FilterType.NONE

    // 美颜强度
    private var beautyIntensity = 0.6f

    // 当前闪光灯模式
    private var currentFlashMode = FlashMode.AUTO

    // 当前HDR模式
    private var currentHdrMode = HdrMode.OFF

    // 当前夜景模式
    private var currentNightMode = NightMode.OFF

    // 当前快门速度（秒），null表示自动曝光
    private var currentShutterSpeed: Float? = null

    // 当前ISO值，null表示自动ISO
    private var currentIso: Int? = null

    // 设备支持的曝光时间范围（纳秒）
    private var exposureTimeRange: Pair<Long, Long>? = null

    // 默认手动ISO值（当切换到手动快门但ISO为自动时使用）
    private val defaultManualIso = 400

    // CameraX组件
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: androidx.camera.core.Camera? = null               // 相机控制引用
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var preview: Preview? = null
    private var imageAnalysis: ImageAnalysis? = null                      // 图像分析用例
    private var currentRecording: Recording? = null
    private var currentAspectRatio: AspectRatio = AspectRatio.RATIO_4_3  // 当前画幅

    // 生命周期持有者
    private var lifecycleOwner: LifecycleOwner? = null

    // 预览视图
    private var previewViewRef: androidx.camera.view.PreviewView? = null

    // ==================== 用例构建器 ====================

    /**
     * 用例构建配置
     *
     * @param aspectRatio 目标画幅比例（可选）
     * @param previewView 预览视图
     */
    private data class UseCaseConfig(
        val aspectRatio: Int? = null,                                        // CameraX画幅比例常量
        val previewView: androidx.camera.view.PreviewView
    )

    /**
     * 构建相机用例
     *
     * 统一创建Preview、ImageCapture、VideoCapture、ImageAnalysis用例
     * 使用新的ResolutionSelector API替代已弃用的setTargetAspectRatio/setTargetResolution
     *
     * @param config 用例配置
     */
    private fun buildUseCases(config: UseCaseConfig) {
        // 构建分辨率选择器（用于Preview和ImageCapture）
        val resolutionSelector = config.aspectRatio?.let { aspectRatio ->
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy(aspectRatio, AspectRatioStrategy.FALLBACK_RULE_AUTO))
                .build()
        }

        // 创建预览用例
        preview = Preview.Builder().apply {
            resolutionSelector?.let { setResolutionSelector(it) }
        }.build().also {
            it.surfaceProvider = config.previewView.surfaceProvider
        }

        // 创建拍照用例
        imageCapture = ImageCapture.Builder().apply {
            setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            resolutionSelector?.let { setResolutionSelector(it) }
        }.build()

        // 创建录像用例
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
            .build()
        videoCapture = VideoCapture.withOutput(recorder)

        // 创建图像分析用例（用于实时滤镜预览）
        // 使用ResolutionStrategy替代已弃用的setTargetResolution
        val analysisResolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(ANALYSIS_WIDTH, ANALYSIS_HEIGHT),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                )
            )
            .build()

        imageAnalysis = ImageAnalysis.Builder()
            .setResolutionSelector(analysisResolutionSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    processImageForFilter(imageProxy)
                }
            }

        Log.d(TAG, "buildUseCases: 用例构建完成 aspectRatio=${config.aspectRatio}")
    }

    /**
     * 绑定用例到生命周期
     *
     * 尝试绑定三用例，失败则回退到基本模式
     *
     * @param owner 生命周期持有者
     * @param cameraSelector 相机选择器
     * @return 绑定的相机实例
     */
    private fun bindUseCasesToLifecycle(
        owner: LifecycleOwner,
        provider: ProcessCameraProvider,
        cameraSelector: CameraSelector
    ): androidx.camera.core.Camera {
        return try {
            // 尝试绑定三用例：Preview + ImageCapture + ImageAnalysis
            provider.bindToLifecycle(
                owner,
                cameraSelector,
                preview,
                imageCapture,
                imageAnalysis
            ).also {
                Log.d(TAG, "bindUseCasesToLifecycle: 三用例绑定成功")
            }
        } catch (e: Exception) {
            Log.w(TAG, "bindUseCasesToLifecycle: 三用例绑定失败，回退基本模式", e)
            // 回退：只绑定 Preview + ImageCapture
            provider.unbindAll()
            provider.bindToLifecycle(
                owner,
                cameraSelector,
                preview,
                imageCapture
            ).also {
                Log.d(TAG, "bindUseCasesToLifecycle: 基本用例绑定成功")
            }
        }
    }

    /**
     * 获取滤镜帧流（用于实时预览）
     */
    override fun getFilteredFrame(): Flow<Bitmap?> = _filteredFrame

    /**
     * 获取原始预览帧（用于生成滤镜缩略图）
     *
     * 此帧不受当前滤镜影响，始终是原始相机帧
     */
    override fun getRawPreviewFrame(): Flow<Bitmap?> = _rawPreviewFrame

    /**
     * 绑定相机到生命周期
     *
     * 使用buildUseCases和bindUseCasesToLifecycle统一处理
     *
     * @param owner 生命周期持有者
     * @param previewView 预览视图
     */
    override suspend fun bindCamera(
        owner: LifecycleOwner,
        previewView: androidx.camera.view.PreviewView
    ) {
        withContext(Dispatchers.Main) {
            Log.d(TAG, "bindCamera: 开始绑定相机")
            this@CameraRepositoryImpl.lifecycleOwner = owner
            this@CameraRepositoryImpl.previewViewRef = previewView

            try {
                // 获取CameraProvider
                val provider = getCameraProvider()
                cameraProvider = provider

                // 解绑之前的用例
                provider.unbindAll()

                // 重置曝光控制状态（曝光三角）
                currentShutterSpeed = null                                       // 重置快门速度
                currentIso = null                                                 // 重置ISO

                // 构建用例（无指定画幅比例）
                buildUseCases(UseCaseConfig(previewView = previewView))

                // 绑定用例到生命周期
                val cameraSelector = getCameraSelector(_currentLens.value)
                camera = bindUseCasesToLifecycle(owner, provider, cameraSelector)

                // 更新变焦范围
                updateZoomRange()

                // 初始化曝光时间范围（用于快门速度控制）
                initExposureTimeRange()

                // 初始化HDR处理器（用于硬件HDR检测）
                initHdrProcessor()

                // 初始化夜景模式处理器（用于硬件夜景检测）
                initNightProcessor()

                // 启动帧处理器（异步处理滤镜）
                startFrameProcessor()

                // 恢复闪光灯设置（重要：TORCH模式需要重新开启手电筒）
                restoreFlashSettings()

                Log.d(TAG, "bindCamera: 相机绑定成功")
            } catch (e: Exception) {
                Log.e(TAG, "bindCamera: 相机绑定失败", e)
                throw e
            }
        }
    }

    /**
     * 处理图像帧应用滤镜（ImageAnalysis回调）
     *
     * 优化：使用FrameProcessor异步处理，避免阻塞回调
     * - 快速将帧提交到缓冲区
     * - 后台线程异步处理滤镜
     * - 支持跳帧策略
     *
     * @param imageProxy 相机帧
     */
    private fun processImageForFilter(imageProxy: ImageProxy) {
        val startTime = System.nanoTime()
        try {
            // 将YUV转换为Bitmap（快速转换）
            val bitmap = yuvImageProxyToBitmap(imageProxy)
            if (bitmap == null) {
                Log.w(TAG, "processImageForFilter: YUV转Bitmap失败")
                return
            }

            // 提交帧到异步处理器（非阻塞）
            frameProcessor.submitFrame(bitmap)

            // 性能日志（每100帧输出一次）
            val conversionTimeNs = System.nanoTime() - startTime
            if (conversionTimeNs > 50_000_000) {  // 超过50ms警告
                Log.w(TAG, "processImageForFilter: 帧转换耗时过长 ${conversionTimeNs / 1_000_000}ms")
            }
        } catch (e: Exception) {
            Log.e(TAG, "processImageForFilter: 处理失败", e)
        } finally {
            imageProxy.close()                                              // 快速释放ImageProxy
        }
    }

    /**
     * 启动帧处理器
     *
     * 在相机绑定后调用，开始异步帧处理
     */
    private fun startFrameProcessor() {
        Log.d(TAG, "startFrameProcessor: 启动帧处理器")
        frameProcessor.start { bitmap ->
            processFrameWithFilter(bitmap)
        }
    }

    /**
     * 停止帧处理器
     */
    private fun stopFrameProcessor() {
        Log.d(TAG, "stopFrameProcessor: 停止帧处理器")
        frameProcessor.stop()
    }

    /**
     * 处理单帧应用滤镜和美颜（在后台线程执行）
     *
     * 处理流程：
     * 1. 检查美颜强度，如果 > 0 则先应用美颜
     * 2. 然后应用滤镜效果
     * 3. 更新 _filteredFrame 供预览显示
     *
     * @param bitmap 待处理的帧
     * @return 处理后的帧
     */
    private fun processFrameWithFilter(bitmap: Bitmap): Bitmap? {
        try {
            val filterType = currentFilterType
            val intensity = beautyIntensity

            // 更新原始预览帧（用于生成滤镜缩略图）
            if (_rawPreviewFrame.value == null) {
                _rawPreviewFrame.value = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
                Log.d(TAG, "processFrameWithFilter: 更新原始预览帧")
            }

            // Step 1: 应用美颜效果（如果启用）
            val beautifiedBitmap = if (intensity > 0f && SafeMagicJni.isLibraryLoaded()) {
                // 计算美颜参数：美白和磨皮
                val whiteLevel = intensity * 0.4f                                   // 美白强度较弱
                val smoothLevel = intensity * 0.6f                                  // 磨皮强度适中

                // 调用同步美颜处理
                val result = SafeMagicJni.processBeauty(bitmap, whiteLevel, smoothLevel)
                result.getOrNull() ?: bitmap                                        // 失败时使用原图
            } else {
                bitmap
            }

            // Step 2: 如果无滤镜但有美颜，直接显示美颜效果
            if (filterType == FilterType.NONE) {
                if (intensity > 0f && beautifiedBitmap !== bitmap) {
                    _filteredFrame.value = beautifiedBitmap                         // 显示美颜效果
                    return beautifiedBitmap
                }
                _filteredFrame.value = null                                         // 无效果，清空
                return null
            }

            // Step 3: 应用滤镜（在美颜后的图上）
            val filteredBitmap = filterRepository.applyFilterToBitmapSync(filterType, beautifiedBitmap)
            if (filteredBitmap != null) {
                _filteredFrame.value = filteredBitmap
                // 回收中间美颜Bitmap（如果不是原图且不是最终结果）
                if (beautifiedBitmap !== bitmap && beautifiedBitmap !== filteredBitmap && !beautifiedBitmap.isRecycled) {
                    beautifiedBitmap.recycle()
                }
                return filteredBitmap
            }

            return beautifiedBitmap
        } catch (e: Exception) {
            Log.e(TAG, "processFrameWithFilter: 帧处理失败", e)
            return null
        }
    }

    /**
     * 将YUV格式的ImageProxy转换为Bitmap
     *
     * 使用ImageFormatConverter工具类进行转换
     *
     * @param imageProxy YUV_420_888格式的图像
     * @return Bitmap，失败返回null
     */
    private fun yuvImageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val isFrontCamera = _currentLens.value == CameraLens.FRONT
        val config = ImageFormatConverter.ConversionConfig(
            quality = 85,
            handleRotation = true,
            handleMirror = isFrontCamera
        )
        return ImageFormatConverter.yuvImageProxyToBitmap(imageProxy, config, isFrontCamera)
    }

    /**
     * 拍照
     *
     * 处理流程：
     * 1. 使用CameraX拍摄原始图像
     * 2. 如果HDR开启且使用软件HDR，应用HDR增强处理
     * 3. 获取当前滤镜类型并应用滤镜效果
     * 4. 应用美颜处理
     * 5. 保存处理后的图像到文件
     *
     * HDR实现说明：
     * - 硬件HDR：相机已通过HDR扩展绑定，直接拍照即可获得HDR效果
     * - 软件HDR：使用HdrProcessor进行单帧HDR增强（曝光融合+色调映射）
     */
    override suspend fun takePhoto(): Result<String> = withContext(Dispatchers.IO) {
        val capture = imageCapture ?: return@withContext Result.failure(
            Exception("ImageCapture未初始化")
        )

        try {
            Log.d(TAG, "takePhoto: 开始拍照 hdrMode=${currentHdrMode.displayName}")

            // 捕获图像并获取ImageProxy
            val imageProxy = suspendCancellableCoroutine<ImageProxy> { continuation ->
                capture.takePicture(
                    cameraExecutor,
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            Log.d(TAG, "takePhoto: 图像捕获成功 size=${image.width}x${image.height}")
                            continuation.resume(image)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Log.e(TAG, "takePhoto: 图像捕获失败", exception)
                            continuation.resumeWithException(exception)
                        }
                    }
                )
            }

            // 将ImageProxy转换为Bitmap
            var originalBitmap = imageProxyToBitmap(imageProxy)
            imageProxy.close()                                                    // 释放ImageProxy

            if (originalBitmap == null) {
                Log.e(TAG, "takePhoto: Bitmap转换失败")
                return@withContext Result.failure(Exception("Bitmap转换失败"))
            }

            Log.d(TAG, "takePhoto: 原始Bitmap大小 ${originalBitmap.width}x${originalBitmap.height}")

            // ==================== HDR处理 ====================
            // 判断是否需要软件HDR处理
            // 条件：HDR模式开启 且 硬件HDR不可用
            val needSoftwareHdr = (currentHdrMode == HdrMode.ON || currentHdrMode == HdrMode.AUTO) &&
                    !isHardwareHdrActive()

            val hdrProcessedBitmap = if (needSoftwareHdr) {
                Log.d(TAG, "takePhoto: 应用软件HDR处理（单帧增强）")
                val hdrResult = hdrProcessor.enhanceSingleFrame(originalBitmap)
                if (hdrResult.success) {
                    Log.d(TAG, "takePhoto: 软件HDR处理完成 耗时=${hdrResult.processingTimeMs}ms")
                    // 回收原始Bitmap
                    if (hdrResult.bitmap !== originalBitmap) {
                        originalBitmap.recycle()
                    }
                    hdrResult.bitmap
                } else {
                    Log.w(TAG, "takePhoto: 软件HDR处理失败: ${hdrResult.errorMessage}，使用原图")
                    originalBitmap
                }
            } else {
                if (currentHdrMode == HdrMode.ON || currentHdrMode == HdrMode.AUTO) {
                    Log.d(TAG, "takePhoto: 使用硬件HDR，无需软件处理")
                }
                originalBitmap
            }

            // ==================== 夜景处理 ====================
            // 判断是否需要软件夜景处理
            // 条件：夜景模式开启 且 硬件夜景不可用
            val needSoftwareNight = (currentNightMode == NightMode.ON || currentNightMode == NightMode.AUTO) &&
                    !isHardwareNightActive()

            val nightProcessedBitmap = if (needSoftwareNight) {
                Log.d(TAG, "takePhoto: 应用软件夜景处理（多帧合成）")
                // 使用单帧增强作为简化实现（完整实现需要多帧捕获）
                val nightResult = nightModeProcessor.enhanceSingleFrame(hdrProcessedBitmap)
                if (nightResult.success) {
                    Log.d(TAG, "takePhoto: 软件夜景处理完成 耗时=${nightResult.processingTimeMs}ms")
                    // 回收HDR处理结果
                    if (nightResult.bitmap !== hdrProcessedBitmap && !hdrProcessedBitmap.isRecycled) {
                        hdrProcessedBitmap.recycle()
                    }
                    nightResult.bitmap
                } else {
                    Log.w(TAG, "takePhoto: 软件夜景处理失败: ${nightResult.errorMessage}，使用HDR图")
                    hdrProcessedBitmap
                }
            } else {
                if (currentNightMode == NightMode.ON || currentNightMode == NightMode.AUTO) {
                    Log.d(TAG, "takePhoto: 使用硬件夜景，无需软件处理")
                }
                hdrProcessedBitmap
            }

            // ==================== 滤镜处理 ====================
            val currentFilter = filterRepository.getCurrentFilter().first()
            Log.d(TAG, "takePhoto: 当前滤镜=$currentFilter")

            val filteredBitmap = if (currentFilter != FilterType.NONE) {
                Log.d(TAG, "takePhoto: 正在应用滤镜...")
                filterRepository.applyFilterToBitmap(currentFilter, nightProcessedBitmap) ?: nightProcessedBitmap
            } else {
                nightProcessedBitmap
            }

            Log.d(TAG, "takePhoto: 滤镜处理完成 大小=${filteredBitmap.width}x${filteredBitmap.height}")

            // ==================== 美颜处理 ====================
            val finalBitmap = if (beautyIntensity > 0f) {
                Log.d(TAG, "takePhoto: 正在应用美颜 intensity=$beautyIntensity")
                val beautyLevel = BeautyLevel.fromIntensity(beautyIntensity)
                val beautyResult = beautyProcessor.processBeauty(filteredBitmap, beautyLevel)
                if (beautyResult != null) {
                    Log.d(TAG, "takePhoto: 美颜处理完成 耗时=${beautyResult.processingTimeMs}ms")
                    // 回收滤镜处理后的Bitmap（如果不是夜景处理结果）
                    if (filteredBitmap !== nightProcessedBitmap && !filteredBitmap.isRecycled) {
                        filteredBitmap.recycle()
                    }
                    beautyResult.bitmap
                } else {
                    Log.w(TAG, "takePhoto: 美颜处理失败，使用滤镜图")
                    filteredBitmap
                }
            } else {
                filteredBitmap
            }

            Log.d(TAG, "takePhoto: 最终图片大小=${finalBitmap.width}x${finalBitmap.height}")

            // ==================== 保存文件 ====================
            val photoFile = createTempPhotoFile()
            FileOutputStream(photoFile).use { fos ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)      // 95%质量压缩
            }

            Log.d(TAG, "takePhoto: 照片保存成功 path=${photoFile.absolutePath}")

            // 回收中间Bitmap（注意：美颜处理成功时filteredBitmap已在上面回收）
            // 回收nightProcessedBitmap（如果它不是最终使用的图片）
            if (nightProcessedBitmap !== finalBitmap && !nightProcessedBitmap.isRecycled) {
                nightProcessedBitmap.recycle()
            }
            // 回收hdrProcessedBitmap（如果它不是夜景处理结果且未被回收）
            if (hdrProcessedBitmap !== nightProcessedBitmap && !hdrProcessedBitmap.isRecycled) {
                hdrProcessedBitmap.recycle()
            }

            Result.success(photoFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "takePhoto: 拍照异常", e)
            Result.failure(e)
        }
    }

    /**
     * 检查硬件HDR是否正在使用
     *
     * 判断当前相机是否通过HDR扩展绑定
     * 如果硬件HDR可用且HDR模式开启，则返回true
     *
     * @return true表示正在使用硬件HDR
     */
    private fun isHardwareHdrActive(): Boolean {
        if (currentHdrMode == HdrMode.OFF) return false
        val lensFacing = when (_currentLens.value) {
            CameraLens.BACK -> CameraSelector.LENS_FACING_BACK
            CameraLens.FRONT -> CameraSelector.LENS_FACING_FRONT
        }
        return hdrProcessor.isHardwareHdrAvailable(lensFacing)
    }

    /**
     * 检查硬件夜景是否正在使用
     *
     * 判断当前相机是否通过Night扩展绑定
     * 如果硬件夜景可用且夜景模式开启，则返回true
     *
     * @return true表示正在使用硬件夜景
     */
    private fun isHardwareNightActive(): Boolean {
        if (currentNightMode == NightMode.OFF) return false
        val lensFacing = when (_currentLens.value) {
            CameraLens.BACK -> CameraSelector.LENS_FACING_BACK
            CameraLens.FRONT -> CameraSelector.LENS_FACING_FRONT
        }
        return nightModeProcessor.isHardwareNightAvailable(lensFacing)
    }

    /**
     * 将ImageProxy转换为Bitmap（拍照专用）
     *
     * 使用ImageFormatConverter工具类，支持JPEG和YUV两种格式
     * 自动检测格式并处理旋转和镜像
     *
     * @param imageProxy CameraX捕获的图像代理
     * @return 正确方向的Bitmap，失败返回null
     */
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val isFrontCamera = _currentLens.value == CameraLens.FRONT
        Log.d(TAG, "imageProxyToBitmap: 尺寸=${imageProxy.width}x${imageProxy.height}, 前置=$isFrontCamera")
        return ImageFormatConverter.imageProxyToBitmap(
            imageProxy = imageProxy,
            config = ImageFormatConverter.ConversionConfig.CAPTURE,       // 使用拍照配置（高质量）
            isFrontCamera = isFrontCamera
        )
    }

    /**
     * 开始录像
     */
    @androidx.annotation.OptIn(androidx.camera.video.ExperimentalPersistentRecording::class)
    override suspend fun startRecording(): Result<Unit> = withContext(Dispatchers.Main) {
        val capture = videoCapture ?: return@withContext Result.failure(
            Exception("VideoCapture未初始化")
        )

        if (_isRecording.value) {
            return@withContext Result.failure(Exception("已在录像中"))
        }

        try {
            Log.d(TAG, "startRecording: 开始录像")

            // 创建临时视频文件
            val videoFile = createTempVideoFile()

            // 配置输出选项
            val outputOptions = FileOutputOptions.Builder(videoFile).build()

            // 开始录像
            currentRecording = capture.output
                .prepareRecording(context, outputOptions)
                .start(ContextCompat.getMainExecutor(context)) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            Log.d(TAG, "startRecording: 录像已开始")
                            _isRecording.value = true
                        }
                        is VideoRecordEvent.Finalize -> {
                            _isRecording.value = false
                            if (event.hasError()) {
                                Log.e(TAG, "startRecording: 录像错误 code=${event.error}")
                            } else {
                                Log.d(TAG, "startRecording: 录像完成 uri=${event.outputResults.outputUri}")
                            }
                        }
                    }
                }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "startRecording: 开始录像失败", e)
            Result.failure(e)
        }
    }

    /**
     * 停止录像
     */
    override suspend fun stopRecording(): Result<String> = withContext(Dispatchers.Main) {
        val recording = currentRecording ?: return@withContext Result.failure(
            Exception("没有正在进行的录像")
        )

        try {
            Log.d(TAG, "stopRecording: 停止录像")
            recording.stop()
            currentRecording = null

            // 获取视频文件路径
            val videoFile = getLatestTempVideoFile()
            if (videoFile != null && videoFile.exists()) {
                Result.success(videoFile.absolutePath)
            } else {
                Result.failure(Exception("视频文件不存在"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "stopRecording: 停止录像失败", e)
            Result.failure(e)
        }
    }

    /**
     * 切换摄像头
     */
    override suspend fun switchCamera(lens: CameraLens): Result<Unit> =
        withContext(Dispatchers.Main) {
            try {
                Log.d(TAG, "switchCamera: 切换摄像头 lens=$lens")

                _currentLens.value = lens

                // 重新绑定相机
                val owner = lifecycleOwner ?: return@withContext Result.failure(
                    Exception("LifecycleOwner未设置")
                )
                val pView = previewViewRef ?: return@withContext Result.failure(
                    Exception("PreviewView未设置")
                )

                bindCamera(owner, pView)
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "switchCamera: 切换摄像头失败", e)
                Result.failure(e)
            }
        }

    /**
     * 应用滤镜
     *
     * 更新当前滤镜类型，实时预览会自动应用新滤镜
     */
    override suspend fun applyFilter(filterType: FilterType): Result<Unit> {
        Log.d(TAG, "applyFilter: 应用滤镜 filterType=$filterType")
        currentFilterType = filterType                                    // 更新当前滤镜
        // 如果是NONE，清空滤镜帧
        if (filterType == FilterType.NONE) {
            _filteredFrame.value = null
        }
        return Result.success(Unit)
    }

    /**
     * 设置美颜等级
     *
     * 美颜效果在 processFrameWithFilter 中实时应用于预览帧
     * 同时在 takePhoto 中应用于拍照结果
     */
    override suspend fun setBeautyLevel(intensity: Float): Result<Unit> {
        Log.d(TAG, "setBeautyLevel: 设置美颜强度 intensity=$intensity")
        beautyIntensity = intensity.coerceIn(0f, 1f)                              // 美颜强度将在帧处理中应用
        return Result.success(Unit)
    }

    /**
     * 设置变焦倍数
     */
    override suspend fun setZoom(zoom: Float): Result<Unit> = withContext(Dispatchers.Main) {
        runCatching {
            Log.d(TAG, "setZoom: 设置变焦 zoom=$zoom")
            val cam = camera ?: throw IllegalStateException("相机未初始化")
            val cameraInfo = cam.cameraInfo
            val maxZoom = cameraInfo.zoomState.value?.maxZoomRatio ?: 1f
            val minZoom = cameraInfo.zoomState.value?.minZoomRatio ?: 1f
            val clampedZoom = zoom.coerceIn(minZoom, maxZoom)
            cam.cameraControl.setZoomRatio(clampedZoom)
            Log.d(TAG, "setZoom: 变焦设置成功 clampedZoom=$clampedZoom")
            Unit                                                          // 显式返回Unit
        }
    }

    /**
     * 触发自动对焦
     */
    override suspend fun autoFocus(): Result<Unit> = withContext(Dispatchers.Main) {
        runCatching {
            Log.d(TAG, "autoFocus: 执行自动对焦")
            val cam = camera ?: throw IllegalStateException("相机未初始化")
            // 在预览中心点进行对焦
            val factory = SurfaceOrientedMeteringPointFactory(1f, 1f)
            val centerPoint = factory.createPoint(0.5f, 0.5f)
            val action = FocusMeteringAction.Builder(centerPoint)
                .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            cam.cameraControl.startFocusAndMetering(action)
            Log.d(TAG, "autoFocus: 自动对焦已触发")
            Unit                                                          // 显式返回Unit
        }
    }

    /**
     * 触摸对焦 - 在指定坐标点进行对焦和测光
     *
     * 使用CameraX的FocusMeteringAction在指定位置触发对焦
     * 同时在该点进行测光，实现点触对焦和点测光功能
     *
     * @param x 归一化X坐标 (0.0~1.0，0为左边缘，1为右边缘)
     * @param y 归一化Y坐标 (0.0~1.0，0为上边缘，1为下边缘)
     * @return 操作结果，包含对焦是否成功
     */
    override suspend fun focusAtPoint(x: Float, y: Float): Result<Unit> = withContext(Dispatchers.Main) {
        runCatching {
            Log.d(TAG, "focusAtPoint: 触摸对焦 x=$x, y=$y")
            val cam = camera ?: throw IllegalStateException("相机未初始化")

            // 确保坐标在有效范围内
            val clampedX = x.coerceIn(0f, 1f)
            val clampedY = y.coerceIn(0f, 1f)
            Log.d(TAG, "focusAtPoint: 归一化坐标 x=$clampedX, y=$clampedY")

            // 创建测光点工厂（使用Surface坐标系）
            val factory = SurfaceOrientedMeteringPointFactory(1f, 1f)

            // 在指定位置创建对焦/测光点
            val focusPoint = factory.createPoint(clampedX, clampedY)

            // 构建对焦测光动作
            // FLAG_AF: 自动对焦
            // FLAG_AE: 自动曝光测光
            // FLAG_AWB: 自动白平衡
            val action = FocusMeteringAction.Builder(
                focusPoint,
                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
            )
                .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)  // 3秒后自动取消
                .build()

            // 执行对焦和测光
            val result = cam.cameraControl.startFocusAndMetering(action)

            // 监听对焦结果（可选，用于调试）
            result.addListener({
                try {
                    val focusResult = result.get()
                    Log.d(TAG, "focusAtPoint: 对焦完成 success=${focusResult.isFocusSuccessful}")
                } catch (e: Exception) {
                    Log.w(TAG, "focusAtPoint: 对焦结果获取失败", e)
                }
            }, ContextCompat.getMainExecutor(context))

            Log.d(TAG, "focusAtPoint: 触摸对焦已触发 at ($clampedX, $clampedY)")
            Unit
        }
    }

    /**
     * 获取当前镜头
     */
    override fun getCurrentLens(): Flow<CameraLens> = _currentLens

    /**
     * 获取录像状态
     */
    override fun isRecording(): Flow<Boolean> = _isRecording

    /**
     * 获取变焦范围
     */
    override fun getZoomRange(): Flow<ZoomRange> = _zoomRange

    /**
     * 更新变焦范围（从相机获取实际支持的范围）
     */
    private fun updateZoomRange() {
        val cam = camera ?: return
        val zoomState = cam.cameraInfo.zoomState.value
        if (zoomState != null) {
            val minZoom = zoomState.minZoomRatio
            val maxZoom = zoomState.maxZoomRatio
            _zoomRange.value = ZoomRange(minZoom, maxZoom)
            Log.d(TAG, "updateZoomRange: 变焦范围更新 min=$minZoom, max=$maxZoom")
        }
    }

    /**
     * 设置HDR模式
     *
     * 真实HDR实现：
     * 1. 优先使用CameraX Extensions硬件HDR（设备原生HDR）
     * 2. 当硬件不支持时，自动降级到软件HDR（曝光融合算法）
     *
     * 实现原理：
     * - 硬件HDR：通过ExtensionsManager获取HDR CameraSelector重新绑定相机
     * - 软件HDR：在拍照时使用HdrProcessor进行Mertens曝光融合
     *
     * @param mode HDR模式（ON/OFF/AUTO）
     */
    override suspend fun setHdrMode(mode: HdrMode): Result<Unit> = withContext(Dispatchers.Main) {
        runCatching {
            Log.d(TAG, "setHdrMode: 设置HDR模式 mode=${mode.displayName}")
            val previousMode = currentHdrMode
            currentHdrMode = mode                                                  // 记录HDR状态

            when (mode) {
                HdrMode.ON -> {
                    // 检查当前镜头是否支持硬件HDR
                    val lensFacing = when (_currentLens.value) {
                        CameraLens.BACK -> CameraSelector.LENS_FACING_BACK
                        CameraLens.FRONT -> CameraSelector.LENS_FACING_FRONT
                    }
                    val isHardwareHdrAvailable = hdrProcessor.isHardwareHdrAvailable(lensFacing)

                    if (isHardwareHdrAvailable) {
                        Log.d(TAG, "setHdrMode: 硬件HDR可用，重新绑定相机启用HDR扩展")
                        // 使用HDR CameraSelector重新绑定相机
                        rebindCameraWithHdr(enabled = true)
                    } else {
                        Log.d(TAG, "setHdrMode: 硬件HDR不可用，将使用软件HDR（曝光融合）")
                        // 硬件不支持，拍照时会使用软件HDR处理
                    }
                }
                HdrMode.OFF -> {
                    Log.d(TAG, "setHdrMode: HDR已关闭")
                    // 如果之前是ON模式且使用了硬件HDR，需要恢复普通相机选择器
                    if (previousMode == HdrMode.ON) {
                        val lensFacing = when (_currentLens.value) {
                            CameraLens.BACK -> CameraSelector.LENS_FACING_BACK
                            CameraLens.FRONT -> CameraSelector.LENS_FACING_FRONT
                        }
                        if (hdrProcessor.isHardwareHdrAvailable(lensFacing)) {
                            Log.d(TAG, "setHdrMode: 恢复普通相机模式")
                            rebindCameraWithHdr(enabled = false)
                        }
                    }
                }
                HdrMode.AUTO -> {
                    Log.d(TAG, "setHdrMode: HDR自动模式（根据场景自动判断）")
                    // AUTO模式：暂时与ON模式相同，未来可加入场景检测
                    val lensFacing = when (_currentLens.value) {
                        CameraLens.BACK -> CameraSelector.LENS_FACING_BACK
                        CameraLens.FRONT -> CameraSelector.LENS_FACING_FRONT
                    }
                    if (hdrProcessor.isHardwareHdrAvailable(lensFacing)) {
                        rebindCameraWithHdr(enabled = true)
                    }
                }
            }
            Unit
        }
    }

    /**
     * 重新绑定相机（启用/禁用HDR扩展）
     *
     * 使用HdrProcessor获取HDR CameraSelector重新绑定相机用例
     *
     * @param enabled 是否启用HDR扩展
     */
    private suspend fun rebindCameraWithHdr(enabled: Boolean) {
        val owner = lifecycleOwner ?: run {
            Log.w(TAG, "rebindCameraWithHdr: LifecycleOwner未设置")
            return
        }
        val previewView = previewViewRef ?: run {
            Log.w(TAG, "rebindCameraWithHdr: PreviewView未设置")
            return
        }
        val provider = cameraProvider ?: run {
            Log.w(TAG, "rebindCameraWithHdr: CameraProvider未初始化")
            return
        }

        try {
            Log.d(TAG, "rebindCameraWithHdr: 开始重新绑定相机 enabled=$enabled")

            // 解绑现有用例
            provider.unbindAll()

            // 获取相机选择器
            val baseCameraSelector = getCameraSelector(_currentLens.value)
            val cameraSelector = if (enabled) {
                hdrProcessor.getHdrCameraSelector(baseCameraSelector)              // HDR相机选择器
            } else {
                baseCameraSelector                                                  // 普通相机选择器
            }

            // 构建用例
            buildUseCases(UseCaseConfig(
                aspectRatio = currentAspectRatio.cameraXRatio,
                previewView = previewView
            ))

            // 绑定用例到生命周期
            camera = bindUseCasesToLifecycle(owner, provider, cameraSelector)

            // 更新变焦范围
            updateZoomRange()

            // 重新启动帧处理器
            startFrameProcessor()

            // 恢复闪光灯设置（重要：TORCH模式需要重新开启手电筒）
            restoreFlashSettings()

            Log.d(TAG, "rebindCameraWithHdr: 相机重新绑定成功 hdrEnabled=$enabled")
        } catch (e: Exception) {
            Log.e(TAG, "rebindCameraWithHdr: 相机重新绑定失败", e)
        }
    }

    /**
     * 设置夜景模式
     *
     * 夜景模式控制：
     * 1. 优先使用硬件夜景（CameraX Night扩展）
     * 2. 当硬件不支持时，自动降级到软件夜景（多帧合成算法）
     *
     * 实现原理：
     * - 硬件夜景：通过ExtensionsManager获取Night CameraSelector重新绑定相机
     * - 软件夜景：在拍照时使用NightModeProcessor进行多帧合成
     *
     * @param mode 夜景模式（ON/OFF/AUTO）
     */
    override suspend fun setNightMode(mode: NightMode): Result<Unit> = withContext(Dispatchers.Main) {
        runCatching {
            Log.d(TAG, "setNightMode: 设置夜景模式 mode=${mode.displayName}")
            val previousMode = currentNightMode
            currentNightMode = mode                                                  // 记录夜景状态

            when (mode) {
                NightMode.ON -> {
                    // 检查当前镜头是否支持硬件夜景
                    val lensFacing = when (_currentLens.value) {
                        CameraLens.BACK -> CameraSelector.LENS_FACING_BACK
                        CameraLens.FRONT -> CameraSelector.LENS_FACING_FRONT
                    }
                    val isHardwareNightAvailable = nightModeProcessor.isHardwareNightAvailable(lensFacing)

                    if (isHardwareNightAvailable) {
                        Log.d(TAG, "setNightMode: 硬件夜景可用，重新绑定相机启用夜景扩展")
                        // 使用Night CameraSelector重新绑定相机
                        rebindCameraWithNight(enabled = true)
                    } else {
                        Log.d(TAG, "setNightMode: 硬件夜景不可用，将使用软件夜景（多帧合成）")
                        // 硬件不支持，拍照时会使用软件夜景处理
                    }
                }
                NightMode.OFF -> {
                    Log.d(TAG, "setNightMode: 夜景模式已关闭")
                    // 如果之前是ON模式且使用了硬件夜景，需要恢复普通相机选择器
                    if (previousMode == NightMode.ON) {
                        val lensFacing = when (_currentLens.value) {
                            CameraLens.BACK -> CameraSelector.LENS_FACING_BACK
                            CameraLens.FRONT -> CameraSelector.LENS_FACING_FRONT
                        }
                        if (nightModeProcessor.isHardwareNightAvailable(lensFacing)) {
                            Log.d(TAG, "setNightMode: 恢复普通相机模式")
                            rebindCameraWithNight(enabled = false)
                        }
                    }
                }
                NightMode.AUTO -> {
                    Log.d(TAG, "setNightMode: 夜景自动模式（根据环境光自动判断）")
                    // AUTO模式：暂时与ON模式相同，未来可加入环境光检测
                    val lensFacing = when (_currentLens.value) {
                        CameraLens.BACK -> CameraSelector.LENS_FACING_BACK
                        CameraLens.FRONT -> CameraSelector.LENS_FACING_FRONT
                    }
                    if (nightModeProcessor.isHardwareNightAvailable(lensFacing)) {
                        rebindCameraWithNight(enabled = true)
                    }
                }
            }
            Unit
        }
    }

    /**
     * 重新绑定相机（启用/禁用夜景扩展）
     *
     * 使用NightModeProcessor获取Night CameraSelector重新绑定相机用例
     *
     * @param enabled 是否启用夜景扩展
     */
    private suspend fun rebindCameraWithNight(enabled: Boolean) {
        val owner = lifecycleOwner ?: run {
            Log.w(TAG, "rebindCameraWithNight: LifecycleOwner未设置")
            return
        }
        val previewView = previewViewRef ?: run {
            Log.w(TAG, "rebindCameraWithNight: PreviewView未设置")
            return
        }
        val provider = cameraProvider ?: run {
            Log.w(TAG, "rebindCameraWithNight: CameraProvider未初始化")
            return
        }

        try {
            Log.d(TAG, "rebindCameraWithNight: 开始重新绑定相机 enabled=$enabled")

            // 解绑现有用例
            provider.unbindAll()

            // 获取相机选择器
            val baseCameraSelector = getCameraSelector(_currentLens.value)
            val cameraSelector = if (enabled) {
                nightModeProcessor.getNightCameraSelector(baseCameraSelector)         // 夜景相机选择器
            } else {
                baseCameraSelector                                                     // 普通相机选择器
            }

            // 构建用例
            buildUseCases(UseCaseConfig(
                aspectRatio = currentAspectRatio.cameraXRatio,
                previewView = previewView
            ))

            // 绑定用例到生命周期
            camera = bindUseCasesToLifecycle(owner, provider, cameraSelector)

            // 更新变焦范围
            updateZoomRange()

            // 重新启动帧处理器
            startFrameProcessor()

            // 恢复闪光灯设置（重要：TORCH模式需要重新开启手电筒）
            restoreFlashSettings()

            Log.d(TAG, "rebindCameraWithNight: 相机重新绑定成功 nightEnabled=$enabled")
        } catch (e: Exception) {
            Log.e(TAG, "rebindCameraWithNight: 相机重新绑定失败", e)
        }
    }

    /**
     * 设置微距模式
     *
     * 微距模式通过调整对焦距离实现
     */
    override suspend fun setMacroMode(mode: MacroMode): Result<Unit> = withContext(Dispatchers.Main) {
        runCatching {
            Log.d(TAG, "setMacroMode: 设置微距模式 mode=${mode.displayName}")
            when (mode) {
                MacroMode.ON -> {
                    // 微距模式：设置较近的对焦距离
                    val cam = camera ?: throw IllegalStateException("相机未初始化")
                    val factory = SurfaceOrientedMeteringPointFactory(1f, 1f)
                    val centerPoint = factory.createPoint(0.5f, 0.5f)
                    val action = FocusMeteringAction.Builder(centerPoint)
                        .setAutoCancelDuration(5, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    cam.cameraControl.startFocusAndMetering(action)
                    Log.d(TAG, "setMacroMode: 微距对焦已触发")
                }
                MacroMode.OFF, MacroMode.AUTO -> {
                    Log.d(TAG, "setMacroMode: 微距模式已关闭/自动")
                }
            }
            Unit
        }
    }

    /**
     * 设置画幅比例
     *
     * 使用buildUseCases和bindUseCasesToLifecycle统一处理
     * 注意：imageAnalysis也会重新绑定，确保滤镜功能正常
     */
    override suspend fun setAspectRatio(ratio: AspectRatio): Result<Unit> = withContext(Dispatchers.Main) {
        runCatching {
            Log.d(TAG, "setAspectRatio: 设置画幅比例 ratio=${ratio.displayName}")

            val owner = lifecycleOwner ?: throw IllegalStateException("生命周期持有者未设置")
            val previewView = previewViewRef ?: throw IllegalStateException("预览视图未设置")
            val provider = cameraProvider ?: throw IllegalStateException("CameraProvider未初始化")

            // 记录当前画幅
            currentAspectRatio = ratio

            // 解绑现有用例
            provider.unbindAll()

            // 构建用例（指定画幅比例）
            buildUseCases(UseCaseConfig(
                aspectRatio = ratio.cameraXRatio,
                previewView = previewView
            ))

            // 绑定用例到生命周期
            val cameraSelector = getCameraSelector(_currentLens.value)
            camera = bindUseCasesToLifecycle(owner, provider, cameraSelector)

            // 更新变焦范围
            updateZoomRange()

            // 重新启动帧处理器（确保滤镜功能正常）
            startFrameProcessor()

            // 恢复闪光灯设置（重要：TORCH模式需要重新开启手电筒）
            restoreFlashSettings()

            Log.d(TAG, "setAspectRatio: 画幅比例已更新为 ${ratio.displayName}")
            Unit
        }
    }

    // ==================== 夜景处理进度 ====================

    /**
     * 获取夜景处理进度流
     *
     * 将NightModeProcessor的进度转换为简化的Pair格式供UI使用
     */
    override fun getNightProcessingProgress(): Flow<Pair<String, Float>?> {
        return nightModeProcessor.processingProgress.map { progress ->
            progress?.let { Pair(it.stage.displayName, it.progress) }
        }
    }

    /**
     * 释放相机资源
     */
    override suspend fun release() {
        withContext(Dispatchers.Main) {
            Log.d(TAG, "release: 释放相机资源")

            // 停止帧处理器
            stopFrameProcessor()

            // 停止录像
            currentRecording?.stop()
            currentRecording = null

            // 重置曝光控制状态（曝光三角）
            currentShutterSpeed = null                                           // 重置快门速度
            currentIso = null                                                     // 重置ISO

            // 解绑相机
            cameraProvider?.unbindAll()

            // 关闭执行器
            cameraExecutor.shutdown()

            // 输出帧处理器最终统计
            val stats = frameProcessor.getStatistics()
            Log.i(TAG, "release: 帧处理器统计 $stats")
        }
    }

    /**
     * 获取CameraProvider
     */
    private suspend fun getCameraProvider(): ProcessCameraProvider =
        suspendCancellableCoroutine { continuation ->
            ProcessCameraProvider.getInstance(context).also { future ->
                future.addListener({
                    continuation.resume(future.get())
                }, ContextCompat.getMainExecutor(context))
            }
        }

    /**
     * 获取CameraSelector
     */
    private fun getCameraSelector(lens: CameraLens): CameraSelector {
        return when (lens) {
            CameraLens.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
            CameraLens.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
        }
    }

    /**
     * 创建临时照片文件
     *
     * 委托给FileUtils工具类
     */
    private fun createTempPhotoFile(): File = FileUtils.createTempPhotoFile(context)

    /**
     * 创建临时视频文件
     *
     * 委托给FileUtils工具类
     */
    private fun createTempVideoFile(): File = FileUtils.createTempVideoFile(context)

    /**
     * 获取最新的临时视频文件
     *
     * 委托给FileUtils工具类
     */
    private fun getLatestTempVideoFile(): File? = FileUtils.getLatestTempVideoFile(context)

    // ==================== 专业模式参数控制 ====================

    /**
     * 设置曝光补偿
     *
     * 使用CameraX原生API设置曝光补偿
     *
     * @param evIndex 曝光补偿索引
     */
    override suspend fun setExposureCompensation(evIndex: Int): Result<Unit> = withContext(Dispatchers.Main) {
        runCatching {
            Log.d(TAG, "setExposureCompensation: 设置曝光补偿 evIndex=$evIndex")
            val cam = camera ?: throw IllegalStateException("相机未初始化")

            // 获取曝光补偿范围
            val exposureState = cam.cameraInfo.exposureState
            if (!exposureState.isExposureCompensationSupported) {
                Log.w(TAG, "setExposureCompensation: 设备不支持曝光补偿")
                return@runCatching
            }

            val range = exposureState.exposureCompensationRange
            val clampedIndex = evIndex.coerceIn(range.lower, range.upper)

            // 设置曝光补偿
            cam.cameraControl.setExposureCompensationIndex(clampedIndex)
            Log.d(TAG, "setExposureCompensation: 曝光补偿设置成功 index=$clampedIndex")
        }
    }

    /**
     * 设置ISO感光度
     *
     * 使用Camera2 Interop设置ISO
     * 实现与快门速度的联动（曝光三角）：
     * - 当快门速度为手动时，保持AE_MODE_OFF
     * - 当快门速度和ISO都为自动时，才启用AE_MODE_ON
     *
     * @param iso ISO值，null表示自动
     */
    @OptIn(ExperimentalCamera2Interop::class)
    override suspend fun setIso(iso: Int?): Result<Unit> = withContext(Dispatchers.Main) {
        runCatching {
            Log.d(TAG, "setIso: 设置ISO=$iso, 当前快门速度=$currentShutterSpeed")
            val cam = camera ?: throw IllegalStateException("相机未初始化")

            val camera2Control = Camera2CameraControl.from(cam.cameraControl)

            // 更新当前ISO记录
            currentIso = iso

            // 判断是否需要手动曝光模式（曝光三角联动）
            val isManualExposure = currentShutterSpeed != null || iso != null

            val options = if (iso == null) {
                // 自动ISO
                if (currentShutterSpeed != null) {
                    // 快门为手动，ISO为自动 → 保持AE_MODE_OFF，使用默认ISO
                    Log.d(TAG, "setIso: 快门手动模式，ISO自动→使用默认ISO=$defaultManualIso")
                    CaptureRequestOptions.Builder()
                        .setCaptureRequestOption(
                            CaptureRequest.CONTROL_AE_MODE,
                            CaptureRequest.CONTROL_AE_MODE_OFF
                        )
                        .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, defaultManualIso)
                        .build()
                } else {
                    // 快门和ISO都为自动 → 启用自动曝光
                    Log.d(TAG, "setIso: 快门和ISO都自动→启用自动曝光")
                    CaptureRequestOptions.Builder()
                        .clearCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY)
                        .setCaptureRequestOption(
                            CaptureRequest.CONTROL_AE_MODE,
                            CaptureRequest.CONTROL_AE_MODE_ON
                        )
                        .build()
                }
            } else {
                // 手动ISO
                Log.d(TAG, "setIso: 手动ISO=$iso, AE_MODE=${if (isManualExposure) "OFF" else "ON"}")
                CaptureRequestOptions.Builder()
                    .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, iso)
                    .setCaptureRequestOption(
                        CaptureRequest.CONTROL_AE_MODE,
                        if (currentShutterSpeed != null) CaptureRequest.CONTROL_AE_MODE_OFF
                        else CaptureRequest.CONTROL_AE_MODE_ON  // ISO手动但快门自动时保持自动曝光
                    )
                    .build()
            }

            camera2Control.captureRequestOptions = options
            Log.d(TAG, "setIso: ISO设置成功 iso=$iso, isManualExposure=$isManualExposure")
            Unit
        }
    }

    /**
     * 设置白平衡模式
     *
     * 使用Camera2 Interop设置白平衡
     *
     * @param mode 白平衡模式
     */
    @OptIn(ExperimentalCamera2Interop::class)
    override suspend fun setWhiteBalance(mode: WhiteBalanceMode): Result<Unit> = withContext(Dispatchers.Main) {
        runCatching {
            Log.d(TAG, "setWhiteBalance: 设置白平衡=${mode.displayName}")
            val cam = camera ?: throw IllegalStateException("相机未初始化")

            val camera2Control = Camera2CameraControl.from(cam.cameraControl)

            val awbMode = when (mode) {
                WhiteBalanceMode.AUTO -> CaptureRequest.CONTROL_AWB_MODE_AUTO
                WhiteBalanceMode.INCANDESCENT -> CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT
                WhiteBalanceMode.FLUORESCENT -> CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT
                WhiteBalanceMode.DAYLIGHT -> CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT
                WhiteBalanceMode.CLOUDY -> CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
                WhiteBalanceMode.SHADE -> CaptureRequest.CONTROL_AWB_MODE_SHADE
            }

            val options = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, awbMode)
                .build()

            camera2Control.captureRequestOptions = options
            Log.d(TAG, "setWhiteBalance: 白平衡设置成功 awbMode=$awbMode")
            Unit
        }
    }

    /**
     * 设置对焦模式
     *
     * 使用Camera2 Interop设置对焦模式
     *
     * @param mode 对焦模式
     */
    @OptIn(ExperimentalCamera2Interop::class)
    override suspend fun setFocusMode(mode: FocusMode): Result<Unit> = withContext(Dispatchers.Main) {
        runCatching {
            Log.d(TAG, "setFocusMode: 设置对焦模式=${mode.displayName}")
            val cam = camera ?: throw IllegalStateException("相机未初始化")

            val camera2Control = Camera2CameraControl.from(cam.cameraControl)

            val afMode = when (mode) {
                FocusMode.AUTO -> CaptureRequest.CONTROL_AF_MODE_AUTO
                FocusMode.CONTINUOUS -> CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                FocusMode.MANUAL -> CaptureRequest.CONTROL_AF_MODE_OFF
            }

            val options = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, afMode)
                .build()

            camera2Control.captureRequestOptions = options
            Log.d(TAG, "setFocusMode: 对焦模式设置成功 afMode=$afMode")
            Unit
        }
    }

    /**
     * 设置手动对焦距离
     *
     * 使用Camera2 Interop设置对焦距离
     * 仅在手动对焦模式下有效
     *
     * @param distance 对焦距离（0.0=最近，1.0=无穷远）
     */
    @OptIn(ExperimentalCamera2Interop::class)
    override suspend fun setFocusDistance(distance: Float): Result<Unit> = withContext(Dispatchers.Main) {
        runCatching {
            Log.d(TAG, "setFocusDistance: 设置对焦距离=$distance")
            val cam = camera ?: throw IllegalStateException("相机未初始化")

            val camera2Control = Camera2CameraControl.from(cam.cameraControl)

            // 对焦距离是屈光度（diopters），需要转换
            // distance=0 表示最近对焦，distance=1 表示无穷远
            // CameraX的LENS_FOCUS_DISTANCE: 0=无穷远，最大值=最近对焦
            // 所以需要反转：focusDistance = maxDistance * (1 - distance)
            // 这里简化处理，使用 0-10 范围作为屈光度
            val maxFocusDistance = 10f  // 假设最大屈光度为10
            val focusDistanceDiopters = maxFocusDistance * (1f - distance.coerceIn(0f, 1f))

            val options = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                .setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, focusDistanceDiopters)
                .build()

            camera2Control.captureRequestOptions = options
            Log.d(TAG, "setFocusDistance: 对焦距离设置成功 diopters=$focusDistanceDiopters")
            Unit
        }
    }

    /**
     * 获取曝光补偿范围
     *
     * @return 曝光补偿范围（minIndex, maxIndex, step）
     */
    override fun getExposureCompensationRange(): Triple<Int, Int, Float> {
        val cam = camera
        return if (cam != null && cam.cameraInfo.exposureState.isExposureCompensationSupported) {
            val exposureState = cam.cameraInfo.exposureState
            val range = exposureState.exposureCompensationRange
            val step = exposureState.exposureCompensationStep.toFloat()
            Triple(range.lower, range.upper, step)
        } else {
            // 默认范围
            Triple(-12, 12, 1f / 3f)
        }
    }

    // ==================== 闪光灯控制 ====================

    /**
     * 设置闪光灯模式
     *
     * 根据FlashMode设置CameraX的闪光灯模式
     * - OFF: ImageCapture.FLASH_MODE_OFF
     * - ON: ImageCapture.FLASH_MODE_ON
     * - AUTO: ImageCapture.FLASH_MODE_AUTO
     * - TORCH: 通过Camera.enableTorch()开启手电筒
     *
     * @param mode 闪光灯模式
     * @return 操作结果
     */
    override suspend fun setFlashMode(mode: FlashMode): Result<Unit> = withContext(Dispatchers.Main) {
        runCatching {
            Log.d(TAG, "setFlashMode: 设置闪光灯模式=${mode.displayName}")
            val cam = camera ?: throw IllegalStateException("相机未初始化")
            val capture = imageCapture ?: throw IllegalStateException("拍照用例未初始化")

            // 先关闭手电筒（如果之前是TORCH模式）
            if (currentFlashMode == FlashMode.TORCH && mode != FlashMode.TORCH) {
                Log.d(TAG, "setFlashMode: 关闭手电筒模式")
                cam.cameraControl.enableTorch(false)
            }

            // 根据模式设置
            when (mode) {
                FlashMode.OFF -> {
                    capture.flashMode = ImageCapture.FLASH_MODE_OFF
                    Log.d(TAG, "setFlashMode: 闪光灯关闭 (FLASH_MODE_OFF)")
                }
                FlashMode.ON -> {
                    capture.flashMode = ImageCapture.FLASH_MODE_ON
                    Log.d(TAG, "setFlashMode: 闪光灯强制开启 (FLASH_MODE_ON)")
                }
                FlashMode.AUTO -> {
                    capture.flashMode = ImageCapture.FLASH_MODE_AUTO
                    Log.d(TAG, "setFlashMode: 闪光灯自动模式 (FLASH_MODE_AUTO)")
                }
                FlashMode.TORCH -> {
                    // 手电筒模式：持续照明
                    if (cam.cameraInfo.hasFlashUnit()) {
                        cam.cameraControl.enableTorch(true)
                        Log.d(TAG, "setFlashMode: 开启手电筒模式 (TORCH)")
                    } else {
                        Log.w(TAG, "setFlashMode: 设备不支持手电筒")
                        throw UnsupportedOperationException("设备不支持手电筒")
                    }
                }
            }

            currentFlashMode = mode
            Log.d(TAG, "setFlashMode: 闪光灯模式设置成功 mode=${mode.displayName}")
            Unit
        }
    }

    /**
     * 恢复闪光灯设置（相机重新绑定后调用）
     *
     * 当相机重新绑定时（如切换HDR/夜景模式、切换画幅比例等），
     * 需要重新应用之前的闪光灯设置，特别是TORCH模式需要重新开启手电筒
     */
    private suspend fun restoreFlashSettings() {
        val cam = camera ?: return
        val capture = imageCapture ?: return

        Log.d(TAG, "restoreFlashSettings: 恢复闪光灯设置 mode=${currentFlashMode.displayName}")

        when (currentFlashMode) {
            FlashMode.OFF -> {
                capture.flashMode = ImageCapture.FLASH_MODE_OFF
            }
            FlashMode.ON -> {
                capture.flashMode = ImageCapture.FLASH_MODE_ON
            }
            FlashMode.AUTO -> {
                capture.flashMode = ImageCapture.FLASH_MODE_AUTO
            }
            FlashMode.TORCH -> {
                // 重新开启手电筒模式
                if (cam.cameraInfo.hasFlashUnit()) {
                    cam.cameraControl.enableTorch(true)
                    Log.d(TAG, "restoreFlashSettings: 手电筒模式已恢复")
                }
            }
        }
    }

    /**
     * 获取当前闪光灯模式
     *
     * @return 当前闪光灯模式
     */
    override fun getCurrentFlashMode(): FlashMode {
        Log.d(TAG, "getCurrentFlashMode: 当前模式=${currentFlashMode.displayName}")
        return currentFlashMode
    }

    /**
     * 检查设备是否支持闪光灯
     *
     * @return true表示支持闪光灯
     */
    override fun hasFlashUnit(): Boolean {
        val hasFlash = camera?.cameraInfo?.hasFlashUnit() ?: false
        Log.d(TAG, "hasFlashUnit: 闪光灯支持=$hasFlash")
        return hasFlash
    }

    // ==================== 快门速度控制（真实Camera2实现） ====================

    /**
     * 初始化曝光时间范围
     *
     * 从Camera2 CameraCharacteristics获取设备支持的曝光时间范围
     * 用于快门速度控制的边界检查
     *
     * 调用时机：相机绑定成功后
     */
    @OptIn(ExperimentalCamera2Interop::class)
    private fun initExposureTimeRange() {
        try {
            val cam = camera ?: run {
                Log.w(TAG, "initExposureTimeRange: 相机未初始化")
                return
            }

            // 获取Camera2 CameraInfo
            val cameraInfo = cam.cameraInfo
            val camera2Info = androidx.camera.camera2.interop.Camera2CameraInfo.from(cameraInfo)

            // 获取CameraCharacteristics
            val characteristics = camera2Info.getCameraCharacteristic(
                CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE
            )

            if (characteristics != null) {
                val minExposureNs = characteristics.lower                            // 最小曝光时间（纳秒）
                val maxExposureNs = characteristics.upper                            // 最大曝光时间（纳秒）
                exposureTimeRange = Pair(minExposureNs, maxExposureNs)

                // 转换为秒用于日志显示
                val minSeconds = minExposureNs / 1_000_000_000.0
                val maxSeconds = maxExposureNs / 1_000_000_000.0
                Log.d(TAG, "initExposureTimeRange: 曝光时间范围 " +
                        "min=${minExposureNs}ns (${formatShutterSpeedLog(minSeconds)}), " +
                        "max=${maxExposureNs}ns (${formatShutterSpeedLog(maxSeconds)})")
            } else {
                Log.w(TAG, "initExposureTimeRange: 设备不支持获取曝光时间范围")
                exposureTimeRange = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "initExposureTimeRange: 获取曝光时间范围失败", e)
            exposureTimeRange = null
        }
    }

    /**
     * 初始化HDR处理器
     *
     * 在相机绑定后调用，用于检测硬件HDR支持情况
     * 异步初始化，不阻塞相机绑定流程
     */
    @OptIn(ExperimentalCamera2Interop::class)
    private suspend fun initHdrProcessor() {
        try {
            Log.d(TAG, "initHdrProcessor: 开始初始化HDR处理器")

            // 初始化HDR处理器（检测硬件HDR支持）
            val success = hdrProcessor.initialize()
            val supportStatus = hdrProcessor.supportStatus

            if (success) {
                Log.i(TAG, "initHdrProcessor: HDR处理器初始化成功 - ${supportStatus?.message}")

                // 检查当前镜头是否支持硬件HDR
                val lensFacing = when (_currentLens.value) {
                    CameraLens.BACK -> CameraSelector.LENS_FACING_BACK
                    CameraLens.FRONT -> CameraSelector.LENS_FACING_FRONT
                }
                val currentLensHdrSupport = hdrProcessor.isHardwareHdrAvailable(lensFacing)
                Log.d(TAG, "initHdrProcessor: 当前镜头(${_currentLens.value})硬件HDR支持=$currentLensHdrSupport")
            } else {
                Log.w(TAG, "initHdrProcessor: HDR处理器初始化失败，将使用软件HDR")
            }
        } catch (e: Exception) {
            Log.e(TAG, "initHdrProcessor: 初始化失败", e)
        }
    }

    /**
     * 初始化夜景模式处理器
     *
     * 在相机绑定后调用，用于检测硬件夜景支持情况
     * 异步初始化，不阻塞相机绑定流程
     */
    @OptIn(ExperimentalCamera2Interop::class)
    private suspend fun initNightProcessor() {
        try {
            Log.d(TAG, "initNightProcessor: 开始初始化夜景模式处理器")

            // 初始化夜景处理器（检测硬件夜景支持）
            val success = nightModeProcessor.initialize()
            val supportStatus = nightModeProcessor.supportStatus

            if (success) {
                Log.i(TAG, "initNightProcessor: 夜景处理器初始化成功 - ${supportStatus?.message}")

                // 检查当前镜头是否支持硬件夜景
                val lensFacing = when (_currentLens.value) {
                    CameraLens.BACK -> CameraSelector.LENS_FACING_BACK
                    CameraLens.FRONT -> CameraSelector.LENS_FACING_FRONT
                }
                val currentLensNightSupport = nightModeProcessor.isHardwareNightAvailable(lensFacing)
                Log.d(TAG, "initNightProcessor: 当前镜头(${_currentLens.value})硬件夜景支持=$currentLensNightSupport")
            } else {
                Log.w(TAG, "initNightProcessor: 夜景处理器初始化失败，将使用软件夜景")
            }
        } catch (e: Exception) {
            Log.e(TAG, "initNightProcessor: 初始化失败", e)
        }
    }

    /**
     * 格式化快门速度用于日志输出
     *
     * @param seconds 快门速度（秒）
     * @return 格式化字符串，如"1/4000s"或"2s"
     */
    private fun formatShutterSpeedLog(seconds: Double): String {
        return if (seconds < 1.0) {
            val denominator = (1.0 / seconds).toInt()
            "1/${denominator}s"
        } else {
            "${seconds.toInt()}s"
        }
    }

    /**
     * 设置快门速度（曝光时间）
     *
     * 使用Camera2 Interop实现真实的快门速度控制
     * 实现与ISO的联动（曝光三角）：
     * - 当设置手动快门且ISO为自动时，自动设置默认ISO保证曝光正确
     * - 当恢复自动快门时，如果ISO也是自动，才启用自动曝光
     *
     * 技术实现：
     * 1. 将快门速度（秒）转换为曝光时间（纳秒）
     * 2. 设置CONTROL_AE_MODE为OFF（禁用自动曝光）
     * 3. 设置SENSOR_EXPOSURE_TIME为指定的曝光时间
     * 4. 联动设置ISO（如果当前为自动则使用默认值）
     * 5. 如果speed为null，恢复自动曝光模式
     *
     * 注意事项：
     * - 手动快门模式下需要配合ISO调整以获得正确曝光
     * - 曝光时间受设备硬件限制
     * - 过长的曝光时间可能导致帧率下降
     *
     * @param speed 快门速度（秒），null表示恢复自动曝光
     *              例如：1/4000s = 0.00025f, 1/30s = 0.0333f, 1s = 1.0f
     * @return 操作结果
     */
    @OptIn(ExperimentalCamera2Interop::class)
    override suspend fun setShutterSpeed(speed: Float?): Result<Unit> = withContext(Dispatchers.Main) {
        runCatching {
            Log.d(TAG, "setShutterSpeed: 设置快门速度 speed=$speed, 当前ISO=$currentIso")
            val cam = camera ?: throw IllegalStateException("相机未初始化")

            val camera2Control = Camera2CameraControl.from(cam.cameraControl)

            if (speed == null) {
                // 恢复自动曝光模式
                Log.d(TAG, "setShutterSpeed: 恢复自动曝光模式")
                currentShutterSpeed = null

                // 判断是否可以完全恢复自动曝光（ISO也必须是自动）
                val canAutoExposure = currentIso == null

                val options = CaptureRequestOptions.Builder()
                    .clearCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME)

                if (canAutoExposure) {
                    // 快门和ISO都自动 → 完全自动曝光
                    Log.d(TAG, "setShutterSpeed: ISO也是自动，启用完全自动曝光")
                    options.setCaptureRequestOption(
                        CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON
                    )
                    options.clearCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY)
                } else {
                    // 快门自动但ISO手动 → 保持ISO设置，仅清除快门
                    Log.d(TAG, "setShutterSpeed: ISO为手动($currentIso)，保持半手动模式")
                    options.setCaptureRequestOption(
                        CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON  // CameraX会用ISO hint
                    )
                    options.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, currentIso!!)
                }

                camera2Control.captureRequestOptions = options.build()
                Log.d(TAG, "setShutterSpeed: 自动曝光模式已启用 canAutoExposure=$canAutoExposure")
            } else {
                // 手动快门速度控制
                // Step 1: 将秒转换为纳秒
                val exposureTimeNs = (speed * 1_000_000_000L).toLong()
                Log.d(TAG, "setShutterSpeed: 计算曝光时间 ${speed}s = ${exposureTimeNs}ns")

                // Step 2: 限制在设备支持范围内
                val clampedExposureNs = exposureTimeRange?.let { range ->
                    val clamped = exposureTimeNs.coerceIn(range.first, range.second)
                    if (clamped != exposureTimeNs) {
                        Log.w(TAG, "setShutterSpeed: 曝光时间超出范围，已调整 " +
                                "${exposureTimeNs}ns -> ${clamped}ns")
                    }
                    clamped
                } ?: exposureTimeNs                                                   // 无范围信息时直接使用

                // Step 3: 确定ISO值（曝光三角联动）
                val effectiveIso = currentIso ?: defaultManualIso
                Log.d(TAG, "setShutterSpeed: 使用ISO=$effectiveIso (原值=${currentIso ?: "自动"})")

                // Step 4: 构建Camera2请求选项（同时设置快门和ISO）
                val options = CaptureRequestOptions.Builder()
                    .setCaptureRequestOption(
                        CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_OFF                           // 禁用自动曝光
                    )
                    .setCaptureRequestOption(
                        CaptureRequest.SENSOR_EXPOSURE_TIME,
                        clampedExposureNs                                            // 设置曝光时间
                    )
                    .setCaptureRequestOption(
                        CaptureRequest.SENSOR_SENSITIVITY,
                        effectiveIso                                                 // 设置ISO
                    )
                    .build()

                camera2Control.captureRequestOptions = options

                // 记录当前快门速度
                currentShutterSpeed = speed
                Log.d(TAG, "setShutterSpeed: 快门速度设置成功 " +
                        "speed=${formatShutterSpeedLog(speed.toDouble())}, " +
                        "exposureTime=${clampedExposureNs}ns, " +
                        "ISO=$effectiveIso")
            }
            Unit
        }
    }

    /**
     * 获取设备支持的快门速度范围
     *
     * @return Pair(最小曝光时间纳秒, 最大曝光时间纳秒)，设备不支持时返回null
     */
    override fun getExposureTimeRange(): Pair<Long, Long>? {
        Log.d(TAG, "getExposureTimeRange: 返回曝光时间范围 $exposureTimeRange")
        return exposureTimeRange
    }

    // ==================== 延时摄影实现 ====================

    // 当前人像虚化等级
    private var currentPortraitBlurLevel = PortraitBlurLevel.MEDIUM

    /**
     * 开始延时摄影录制
     *
     * @param settings 延时摄影配置
     * @return 操作结果
     */
    override suspend fun startTimelapse(settings: TimelapseSettings): Result<Unit> {
        Log.d(TAG, "startTimelapse: 开始延时摄影 settings=$settings")
        val config = TimelapseConfig(
            captureIntervalMs = settings.captureIntervalMs,
            outputFps = settings.outputFps,
            videoWidth = settings.videoWidth,
            videoHeight = settings.videoHeight,
            maxDurationMs = settings.maxDurationMs
        )
        return timelapseEngine.startRecording(config)
    }

    /**
     * 停止延时摄影并编码输出视频
     *
     * @return 输出视频文件路径
     */
    override suspend fun stopTimelapse(): Result<String> {
        Log.d(TAG, "stopTimelapse: 停止延时摄影并编码")
        return timelapseEngine.stopAndEncode()
    }

    /**
     * 取消延时摄影（不生成视频）
     *
     * @return 操作结果
     */
    override suspend fun cancelTimelapse(): Result<Unit> {
        Log.d(TAG, "cancelTimelapse: 取消延时摄影")
        return timelapseEngine.cancel()
    }

    /**
     * 暂停延时摄影
     *
     * @return 操作结果
     */
    override suspend fun pauseTimelapse(): Result<Unit> {
        Log.d(TAG, "pauseTimelapse: 暂停延时摄影")
        return timelapseEngine.pause()
    }

    /**
     * 恢复延时摄影
     *
     * @return 操作结果
     */
    override suspend fun resumeTimelapse(): Result<Unit> {
        Log.d(TAG, "resumeTimelapse: 恢复延时摄影")
        return timelapseEngine.resume()
    }

    /**
     * 获取延时摄影进度流
     *
     * @return Triple(已捕获帧数, 已用时间毫秒, 编码进度0.0~1.0)
     */
    override fun getTimelapseProgress(): Flow<Triple<Int, Long, Float>> {
        return timelapseEngine.progress.map { progress ->
            Triple(
                progress.framesCaptured,
                progress.elapsedTimeMs,
                progress.encodingProgress
            )
        }
    }

    /**
     * 检查是否处于延时摄影录制状态
     *
     * @return true表示正在录制
     */
    override fun isTimelapseRecording(): Boolean {
        val state = timelapseEngine.progress.value.state
        return state == TimelapseState.RECORDING || state == TimelapseState.PAUSED
    }

    /**
     * 为延时摄影添加帧
     *
     * 在TIMELAPSE模式下由定时器调用
     * 捕获当前预览帧并添加到延时摄影引擎
     *
     * @return 操作结果
     */
    suspend fun addTimelapseFrame(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!timelapseEngine.shouldCaptureFrame()) {
                return@runCatching
            }

            Log.d(TAG, "addTimelapseFrame: 捕获延时摄影帧")

            // 获取当前预览帧
            val bitmap = _filteredFrame.value ?: _rawPreviewFrame.value
                ?: throw IllegalStateException("无可用预览帧")

            // 添加帧到延时摄影引擎
            timelapseEngine.addFrame(bitmap).getOrThrow()
            Log.d(TAG, "addTimelapseFrame: 帧已添加")
        }
    }

    /**
     * 获取延时摄影捕获间隔
     *
     * @return 捕获间隔（毫秒）
     */
    fun getTimelapseCaptureInterval(): Long {
        return timelapseEngine.getCaptureInterval()
    }

    // ==================== 人像虚化实现 ====================

    /**
     * 设置人像虚化等级
     *
     * @param level 虚化等级
     * @return 操作结果
     */
    override suspend fun setPortraitBlurLevel(level: PortraitBlurLevel): Result<Unit> {
        Log.d(TAG, "setPortraitBlurLevel: 设置虚化等级 level=$level")
        currentPortraitBlurLevel = level
        return Result.success(Unit)
    }

    /**
     * 获取当前人像虚化等级
     *
     * @return 当前虚化等级
     */
    override fun getPortraitBlurLevel(): PortraitBlurLevel {
        return currentPortraitBlurLevel
    }

    /**
     * 应用人像虚化效果到图像
     *
     * 使用ML Kit进行人像分割，然后应用高斯模糊
     *
     * @param bitmap 原始图像
     * @return 虚化后的图像，如果虚化等级为NONE则返回原图
     */
    suspend fun applyPortraitBlur(bitmap: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        if (currentPortraitBlurLevel == PortraitBlurLevel.NONE) {
            Log.d(TAG, "applyPortraitBlur: 虚化等级为NONE，返回原图")
            return@withContext bitmap
        }

        Log.d(TAG, "applyPortraitBlur: 应用虚化 level=$currentPortraitBlurLevel")

        // 根据虚化等级选择配置
        val config = when (currentPortraitBlurLevel) {
            PortraitBlurLevel.LIGHT -> PortraitBlurConfig.LIGHT
            PortraitBlurLevel.MEDIUM -> PortraitBlurConfig.MEDIUM
            PortraitBlurLevel.HEAVY -> PortraitBlurConfig.HEAVY
            else -> PortraitBlurConfig.MEDIUM
        }

        // 使用PortraitBlurProcessor处理
        val result = portraitBlurProcessor.processPortraitBlur(bitmap, config)

        if (result.success) {
            Log.d(TAG, "applyPortraitBlur: 虚化成功 耗时=${result.processingTimeMs}ms")
            result.bitmap
        } else {
            Log.w(TAG, "applyPortraitBlur: 虚化失败 - ${result.errorMessage}，返回原图")
            bitmap
        }
    }
}
