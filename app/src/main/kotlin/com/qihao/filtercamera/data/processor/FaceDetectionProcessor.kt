/**
 * FaceDetectionProcessor.kt - 人脸检测处理器
 *
 * 使用ML Kit进行实时人脸检测
 * 用于人像模式的自动人脸识别和聚焦
 *
 * 功能：
 * - 实时检测相机预览中的人脸
 * - 返回人脸边界框用于UI绘制
 * - 计算人脸中心点用于自动对焦
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

/**
 * 人脸检测处理器
 *
 * 使用ML Kit Face Detection API检测人脸
 */
@Singleton
class FaceDetectionProcessor @Inject constructor() {

    companion object {
        private const val TAG = "FaceDetectionProcessor"              // 日志标签
        private const val MIN_FACE_SIZE = 0.1f                        // 最小人脸大小（相对于图像）
    }

    // 人脸检测器
    private val detector: FaceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)       // 快速模式
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)             // 不检测特征点
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)               // 不检测轮廓
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE) // 不分类
            .setMinFaceSize(MIN_FACE_SIZE)                                       // 最小人脸大小
            .build()
        FaceDetection.getClient(options)
    }

    // 检测到的人脸列表
    private val _detectedFaces = MutableStateFlow<List<FaceInfo>>(emptyList())
    val detectedFaces: StateFlow<List<FaceInfo>> = _detectedFaces.asStateFlow()

    // 是否启用检测
    private var isEnabled = false

    // 上次检测时间（用于降低频率）
    private var lastDetectionTime = 0L
    private val detectionInterval = 100L                              // 检测间隔100ms

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
