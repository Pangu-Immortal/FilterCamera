/**
 * FaceDetectionProcessor.kt - 人脸检测与追踪对焦处理器
 *
 * 使用ML Kit进行实时人脸检测，支持自动追踪对焦
 * 用于人像模式的自动人脸识别、追踪和聚焦
 *
 * 功能：
 * - 实时检测相机预览中的人脸
 * - 返回人脸边界框用于UI绘制
 * - 计算人脸中心点用于自动对焦
 * - 人脸追踪对焦（自动跟踪主人脸并触发对焦）
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.data.processor

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.qihao.filtercamera.domain.model.FaceInfo
import com.qihao.filtercamera.domain.model.NormalizedRect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * 追踪对焦回调接口
 *
 * 当需要触发对焦时调用
 */
fun interface FaceTrackingFocusCallback {
    /**
     * 触发对焦到指定位置
     *
     * @param x 归一化X坐标 (0.0~1.0)
     * @param y 归一化Y坐标 (0.0~1.0)
     */
    fun onFocusAt(x: Float, y: Float)
}

/**
 * 人脸追踪状态
 */
enum class FaceTrackingState {
    IDLE,           // 未追踪
    TRACKING,       // 正在追踪
    LOST            // 目标丢失
}

/**
 * 人脸检测与追踪对焦处理器
 *
 * 使用ML Kit Face Detection API检测人脸
 * 支持自动追踪最大人脸并触发对焦
 */
@Singleton
class FaceDetectionProcessor @Inject constructor() {

    companion object {
        private const val TAG = "FaceDetectionProcessor"              // 日志标签
        private const val MIN_FACE_SIZE = 0.1f                        // 最小人脸大小（相对于图像）
        private const val TRACKING_FOCUS_INTERVAL = 1000L             // 追踪对焦间隔（毫秒）
        private const val FACE_MOVE_THRESHOLD = 0.05f                 // 人脸移动阈值（触发对焦）
        private const val FACE_LOST_TIMEOUT = 500L                    // 人脸丢失超时（毫秒）
    }

    // 人脸检测器
    private val detector: FaceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)       // 快速模式
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)             // 不检测特征点
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)               // 不检测轮廓
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE) // 不分类
            .setMinFaceSize(MIN_FACE_SIZE)                                       // 最小人脸大小
            .enableTracking()                                                    // 启用人脸追踪
            .build()
        FaceDetection.getClient(options)
    }

    // 检测到的人脸列表
    private val _detectedFaces = MutableStateFlow<List<FaceInfo>>(emptyList())
    val detectedFaces: StateFlow<List<FaceInfo>> = _detectedFaces.asStateFlow()

    // 追踪状态
    private val _trackingState = MutableStateFlow(FaceTrackingState.IDLE)
    val trackingState: StateFlow<FaceTrackingState> = _trackingState.asStateFlow()

    // 是否启用检测
    private var isEnabled = false

    // 是否启用追踪对焦
    private var isTrackingFocusEnabled = false

    // 追踪对焦回调
    private var focusCallback: FaceTrackingFocusCallback? = null

    // 上次检测时间（用于降低频率）
    private var lastDetectionTime = 0L
    private val detectionInterval = 100L                              // 检测间隔100ms

    // 追踪相关状态
    private var lastFocusTime = 0L                                    // 上次对焦时间
    private var lastTrackedFaceCenter: Pair<Float, Float>? = null     // 上次追踪的人脸中心
    private var lastFaceDetectedTime = 0L                             // 上次检测到人脸的时间
    private var trackedFaceId: Int? = null                            // 追踪的人脸ID

    /**
     * 启用人脸检测
     */
    fun enable() {
        Log.d(TAG, "enable: 启用人脸检测")
        isEnabled = true
    }

    /**
     * 禁用人脸检测
     */
    fun disable() {
        Log.d(TAG, "disable: 禁用人脸检测")
        isEnabled = false
        _detectedFaces.value = emptyList()                           // 清空检测结果
        resetTrackingState()                                         // 重置追踪状态
    }

    /**
     * 启用人脸追踪对焦
     *
     * 启用后会自动追踪最大人脸并在人脸移动时触发对焦
     *
     * @param callback 对焦回调
     */
    fun enableTrackingFocus(callback: FaceTrackingFocusCallback) {
        Log.d(TAG, "enableTrackingFocus: 启用人脸追踪对焦")
        isTrackingFocusEnabled = true
        focusCallback = callback
        _trackingState.value = FaceTrackingState.IDLE
    }

    /**
     * 禁用人脸追踪对焦
     */
    fun disableTrackingFocus() {
        Log.d(TAG, "disableTrackingFocus: 禁用人脸追踪对焦")
        isTrackingFocusEnabled = false
        focusCallback = null
        resetTrackingState()
    }

    /**
     * 重置追踪状态
     */
    private fun resetTrackingState() {
        _trackingState.value = FaceTrackingState.IDLE
        lastTrackedFaceCenter = null
        trackedFaceId = null
        lastFocusTime = 0L
    }

    /**
     * 检查是否需要触发追踪对焦
     *
     * @param faceInfoList 当前检测到的人脸列表
     */
    private fun checkTrackingFocus(faceInfoList: List<FaceInfo>) {
        if (!isTrackingFocusEnabled || focusCallback == null) return

        val currentTime = System.currentTimeMillis()

        if (faceInfoList.isEmpty()) {
            // 检测人脸丢失
            if (_trackingState.value == FaceTrackingState.TRACKING &&
                currentTime - lastFaceDetectedTime > FACE_LOST_TIMEOUT
            ) {
                Log.d(TAG, "checkTrackingFocus: 人脸丢失")
                _trackingState.value = FaceTrackingState.LOST
                lastTrackedFaceCenter = null
                trackedFaceId = null
            }
            return
        }

        // 更新人脸检测时间
        lastFaceDetectedTime = currentTime

        // 获取主要人脸（最大的）
        val primaryFace = faceInfoList.maxByOrNull {
            it.boundingBox.width * it.boundingBox.height
        } ?: return

        val faceCenter = Pair(primaryFace.centerX, primaryFace.centerY)

        // 判断是否需要触发对焦
        val shouldFocus = when {
            // 首次检测到人脸，立即对焦
            _trackingState.value != FaceTrackingState.TRACKING -> {
                Log.d(TAG, "checkTrackingFocus: 首次检测到人脸，触发对焦")
                _trackingState.value = FaceTrackingState.TRACKING
                true
            }
            // 人脸从丢失状态恢复
            _trackingState.value == FaceTrackingState.LOST -> {
                Log.d(TAG, "checkTrackingFocus: 人脸恢复追踪，触发对焦")
                _trackingState.value = FaceTrackingState.TRACKING
                true
            }
            // 定期对焦检查
            currentTime - lastFocusTime > TRACKING_FOCUS_INTERVAL -> {
                // 检查人脸是否移动超过阈值
                lastTrackedFaceCenter?.let { lastCenter ->
                    val dx = abs(faceCenter.first - lastCenter.first)
                    val dy = abs(faceCenter.second - lastCenter.second)
                    if (dx > FACE_MOVE_THRESHOLD || dy > FACE_MOVE_THRESHOLD) {
                        Log.d(TAG, "checkTrackingFocus: 人脸移动较大 dx=$dx dy=$dy，触发对焦")
                        true
                    } else {
                        false
                    }
                } ?: false
            }
            else -> false
        }

        // 执行对焦
        if (shouldFocus) {
            lastFocusTime = currentTime
            lastTrackedFaceCenter = faceCenter
            focusCallback?.onFocusAt(faceCenter.first, faceCenter.second)
            Log.d(TAG, "checkTrackingFocus: 触发对焦 at (${faceCenter.first}, ${faceCenter.second})")
        } else {
            // 更新追踪位置（不触发对焦）
            lastTrackedFaceCenter = faceCenter
        }
    }

    /**
     * 处理相机帧进行人脸检测
     *
     * @param imageProxy 相机帧
     * @param rotationDegrees 旋转角度
     * @param isFrontCamera 是否前置摄像头
     */
    @androidx.camera.core.ExperimentalGetImage
    fun processImage(
        imageProxy: ImageProxy,
        rotationDegrees: Int,
        isFrontCamera: Boolean
    ) {
        if (!isEnabled) {
            imageProxy.close()
            return
        }

        // 控制检测频率
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastDetectionTime < detectionInterval) {
            imageProxy.close()
            return
        }
        lastDetectionTime = currentTime

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        // 创建ML Kit输入图像
        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)
        val imageWidth = inputImage.width
        val imageHeight = inputImage.height

        // 执行人脸检测
        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                val faceInfoList = faces.map { face ->
                    convertToFaceInfo(face, imageWidth, imageHeight, isFrontCamera)
                }
                _detectedFaces.value = faceInfoList
                if (faceInfoList.isNotEmpty()) {
                    Log.d(TAG, "processImage: 检测到 ${faceInfoList.size} 张人脸")
                }
                // 检查人脸追踪对焦
                checkTrackingFocus(faceInfoList)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "processImage: 人脸检测失败", e)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    /**
     * 处理Bitmap进行人脸检测
     *
     * @param bitmap 图像
     * @param rotationDegrees 旋转角度
     * @param isFrontCamera 是否前置摄像头
     */
    fun processBitmap(
        bitmap: Bitmap,
        rotationDegrees: Int = 0,
        isFrontCamera: Boolean = false
    ) {
        if (!isEnabled) return

        // 控制检测频率
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastDetectionTime < detectionInterval) {
            return
        }
        lastDetectionTime = currentTime

        // 创建ML Kit输入图像
        val inputImage = InputImage.fromBitmap(bitmap, rotationDegrees)
        val imageWidth = inputImage.width
        val imageHeight = inputImage.height

        // 执行人脸检测
        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                val faceInfoList = faces.map { face ->
                    convertToFaceInfo(face, imageWidth, imageHeight, isFrontCamera)
                }
                _detectedFaces.value = faceInfoList
                if (faceInfoList.isNotEmpty()) {
                    Log.d(TAG, "processBitmap: 检测到 ${faceInfoList.size} 张人脸")
                }
                // 检查人脸追踪对焦
                checkTrackingFocus(faceInfoList)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "processBitmap: 人脸检测失败", e)
            }
    }

    /**
     * 将ML Kit的Face转换为FaceInfo
     *
     * @param face ML Kit人脸对象
     * @param imageWidth 图像宽度
     * @param imageHeight 图像高度
     * @param isFrontCamera 是否前置摄像头（需要镜像）
     * @return FaceInfo对象
     */
    private fun convertToFaceInfo(
        face: Face,
        imageWidth: Int,
        imageHeight: Int,
        isFrontCamera: Boolean
    ): FaceInfo {
        val bounds = face.boundingBox

        // 归一化坐标
        var left = bounds.left.toFloat() / imageWidth
        var right = bounds.right.toFloat() / imageWidth
        val top = bounds.top.toFloat() / imageHeight
        val bottom = bounds.bottom.toFloat() / imageHeight

        // 前置摄像头需要水平镜像
        if (isFrontCamera) {
            val tempLeft = left
            left = 1f - right
            right = 1f - tempLeft
        }

        // 计算中心点
        val centerX = (left + right) / 2f
        val centerY = (top + bottom) / 2f

        return FaceInfo(
            boundingBox = NormalizedRect(left, top, right, bottom),
            centerX = centerX,
            centerY = centerY
        )
    }

    /**
     * 获取主要人脸（最大的人脸）
     *
     * @return 最大人脸的FaceInfo，无人脸时返回null
     */
    fun getPrimaryFace(): FaceInfo? {
        val faces = _detectedFaces.value
        if (faces.isEmpty()) return null

        // 返回面积最大的人脸
        return faces.maxByOrNull { face ->
            face.boundingBox.width * face.boundingBox.height
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        Log.d(TAG, "release: 释放人脸检测器资源")
        detector.close()
        _detectedFaces.value = emptyList()
    }
}
