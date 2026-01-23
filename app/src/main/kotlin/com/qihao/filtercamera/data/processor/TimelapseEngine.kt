/**
 * TimelapseEngine.kt - 延时摄影引擎
 *
 * 提供延时摄影功能的完整实现：
 * 1. 按指定间隔自动拍摄帧
 * 2. 将帧序列合成为视频
 * 3. 支持暂停/恢复/取消操作
 * 4. 实时进度反馈
 *
 * 技术实现：
 * - 使用MediaCodec + MediaMuxer进行视频编码
 * - 支持H.264编码
 * - 自动管理临时帧文件
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.data.processor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.util.Log
import android.view.Surface
import com.qihao.filtercamera.data.processor.render.TextureRenderer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 延时摄影配置
 *
 * @param captureIntervalMs 帧捕获间隔（毫秒），默认3000ms（3秒/帧）
 * @param outputFps 输出视频帧率，默认30fps
 * @param maxDurationMs 最大拍摄时长（毫秒），0表示无限制
 * @param videoWidth 输出视频宽度
 * @param videoHeight 输出视频高度
 * @param bitRate 视频比特率（bps）
 */
data class TimelapseConfig(
    val captureIntervalMs: Long = 3000L,                                     // 每3秒拍摄一帧
    val outputFps: Int = 30,                                                  // 输出30fps
    val maxDurationMs: Long = 0L,                                             // 0=无限制
    val videoWidth: Int = 1920,                                               // 1080p宽度
    val videoHeight: Int = 1080,                                              // 1080p高度
    val bitRate: Int = 10_000_000                                             // 10Mbps
) {
    /**
     * 计算预期的加速倍数
     * 例如：3秒间隔 + 30fps输出 = 原速的90倍加速
     */
    val speedMultiplier: Float
        get() = (captureIntervalMs / 1000f) * outputFps

    /**
     * 计算每帧的持续时间（微秒）
     */
    val frameDurationUs: Long
        get() = (1_000_000L / outputFps)
}

/**
 * 延时摄影状态
 */
enum class TimelapseState {
    IDLE,                                                                     // 空闲
    RECORDING,                                                                // 录制中
    PAUSED,                                                                   // 已暂停
    ENCODING,                                                                 // 编码中
    COMPLETED,                                                                // 已完成
    ERROR                                                                     // 错误
}

/**
 * 延时摄影进度
 *
 * @param state 当前状态
 * @param framesCaptured 已捕获帧数
 * @param elapsedTimeMs 已用时间（毫秒）
 * @param encodingProgress 编码进度（0.0~1.0）
 * @param estimatedVideoLengthSec 预估输出视频时长（秒）
 * @param message 状态消息
 */
data class TimelapseProgress(
    val state: TimelapseState = TimelapseState.IDLE,
    val framesCaptured: Int = 0,
    val elapsedTimeMs: Long = 0L,
    val encodingProgress: Float = 0f,
    val estimatedVideoLengthSec: Float = 0f,
    val message: String = ""
)

/**
 * 延时摄影引擎
 *
 * 管理延时摄影的完整生命周期
 */
@Singleton
class TimelapseEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "TimelapseEngine"

        // MIME类型
        private const val MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC           // H.264

        // 临时目录名
        private const val TEMP_DIR_NAME = "timelapse_frames"

        // I帧间隔
        private const val I_FRAME_INTERVAL = 1                                  // 每秒一个I帧
    }

    // 线程安全锁
    private val mutex = Mutex()

    // 进度状态Flow
    private val _progress = MutableStateFlow(TimelapseProgress())
    val progress: StateFlow<TimelapseProgress> = _progress.asStateFlow()

    // 当前配置
    private var currentConfig: TimelapseConfig = TimelapseConfig()

    // 捕获任务
    private var captureJob: Job? = null

    // 帧缓存目录
    private var framesDir: File? = null

    // 帧计数器
    private var frameIndex = 0

    // 录制开始时间
    private var startTimeMs = 0L

    // ==================== 公开API ====================

    /**
     * 开始延时摄影录制
     *
     * @param config 延时摄影配置
     * @return 操作结果
     */
    suspend fun startRecording(config: TimelapseConfig = TimelapseConfig()): Result<Unit> = mutex.withLock {
        return@withLock try {
            Log.d(TAG, "startRecording: 配置=$config")

            // 检查状态
            if (_progress.value.state == TimelapseState.RECORDING ||
                _progress.value.state == TimelapseState.PAUSED) {
                return@withLock Result.failure(IllegalStateException("已在录制中"))
            }

            // 保存配置
            currentConfig = config

            // 创建临时目录
            framesDir = createTempFramesDir()
            Log.d(TAG, "startRecording: 临时目录=${framesDir?.absolutePath}")

            // 重置计数器
            frameIndex = 0
            startTimeMs = System.currentTimeMillis()

            // 更新状态
            _progress.value = TimelapseProgress(
                state = TimelapseState.RECORDING,
                message = "延时摄影已开始"
            )

            Log.i(TAG, "startRecording: 延时摄影开始，间隔=${config.captureIntervalMs}ms")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "startRecording: 启动失败", e)
            _progress.value = TimelapseProgress(
                state = TimelapseState.ERROR,
                message = "启动失败: ${e.message}"
            )
            Result.failure(e)
        }
    }

    /**
     * 添加一帧到延时摄影
     *
     * 由CameraRepository在拍摄时调用
     *
     * @param bitmap 帧图像
     * @return 操作结果
     */
    suspend fun addFrame(bitmap: Bitmap): Result<Unit> = mutex.withLock {
        return@withLock try {
            if (_progress.value.state != TimelapseState.RECORDING) {
                return@withLock Result.failure(IllegalStateException("未在录制状态"))
            }

            val dir = framesDir ?: return@withLock Result.failure(IllegalStateException("临时目录未初始化"))

            // 保存帧到临时文件
            withContext(Dispatchers.IO) {
                val frameFile = File(dir, "frame_${String.format(Locale.US, "%06d", frameIndex)}.jpg")
                FileOutputStream(frameFile).use { out ->
                    // 缩放到目标分辨率
                    val scaledBitmap = Bitmap.createScaledBitmap(
                        bitmap,
                        currentConfig.videoWidth,
                        currentConfig.videoHeight,
                        true
                    )
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    if (scaledBitmap != bitmap) {
                        scaledBitmap.recycle()
                    }
                }
                Log.d(TAG, "addFrame: 帧${frameIndex}已保存")
            }

            frameIndex++
            val elapsedTime = System.currentTimeMillis() - startTimeMs

            // 更新进度
            _progress.value = _progress.value.copy(
                framesCaptured = frameIndex,
                elapsedTimeMs = elapsedTime,
                estimatedVideoLengthSec = frameIndex.toFloat() / currentConfig.outputFps,
                message = "已捕获 $frameIndex 帧"
            )

            // 检查是否达到最大时长
            if (currentConfig.maxDurationMs > 0 && elapsedTime >= currentConfig.maxDurationMs) {
                Log.d(TAG, "addFrame: 达到最大时长，自动停止")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "addFrame: 添加帧失败", e)
            Result.failure(e)
        }
    }

    /**
     * 暂停延时摄影
     *
     * @return 操作结果
     */
    suspend fun pause(): Result<Unit> = mutex.withLock {
        return@withLock try {
            if (_progress.value.state != TimelapseState.RECORDING) {
                return@withLock Result.failure(IllegalStateException("未在录制状态"))
            }

            _progress.value = _progress.value.copy(
                state = TimelapseState.PAUSED,
                message = "延时摄影已暂停"
            )

            Log.d(TAG, "pause: 延时摄影已暂停")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "pause: 暂停失败", e)
            Result.failure(e)
        }
    }

    /**
     * 恢复延时摄影
     *
     * @return 操作结果
     */
    suspend fun resume(): Result<Unit> = mutex.withLock {
        return@withLock try {
            if (_progress.value.state != TimelapseState.PAUSED) {
                return@withLock Result.failure(IllegalStateException("未在暂停状态"))
            }

            _progress.value = _progress.value.copy(
                state = TimelapseState.RECORDING,
                message = "延时摄影已恢复"
            )

            Log.d(TAG, "resume: 延时摄影已恢复")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "resume: 恢复失败", e)
            Result.failure(e)
        }
    }

    /**
     * 停止延时摄影并开始编码
     *
     * @return 输出视频文件路径
     */
    suspend fun stopAndEncode(): Result<String> {
        mutex.withLock {
            if (_progress.value.state != TimelapseState.RECORDING &&
                _progress.value.state != TimelapseState.PAUSED) {
                return Result.failure(IllegalStateException("未在录制状态"))
            }

            _progress.value = _progress.value.copy(
                state = TimelapseState.ENCODING,
                message = "正在生成视频..."
            )
        }

        return try {
            Log.d(TAG, "stopAndEncode: 开始编码，共${frameIndex}帧")

            if (frameIndex < 2) {
                throw IllegalStateException("帧数不足，至少需要2帧")
            }

            // 生成输出文件路径
            val outputFile = File(
                context.cacheDir,
                "timelapse_${System.currentTimeMillis()}.mp4"
            )

            // 执行编码
            encodeFramesToVideo(outputFile)

            // 清理临时文件
            cleanupTempFiles()

            mutex.withLock {
                _progress.value = _progress.value.copy(
                    state = TimelapseState.COMPLETED,
                    encodingProgress = 1f,
                    message = "视频生成完成"
                )
            }

            Log.i(TAG, "stopAndEncode: 视频生成完成 - ${outputFile.absolutePath}")
            Result.success(outputFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "stopAndEncode: 编码失败", e)
            mutex.withLock {
                _progress.value = _progress.value.copy(
                    state = TimelapseState.ERROR,
                    message = "编码失败: ${e.message}"
                )
            }
            Result.failure(e)
        }
    }

    /**
     * 取消延时摄影（不生成视频）
     *
     * @return 操作结果
     */
    suspend fun cancel(): Result<Unit> = mutex.withLock {
        return@withLock try {
            Log.d(TAG, "cancel: 取消延时摄影")

            // 取消捕获任务
            captureJob?.cancelAndJoin()
            captureJob = null

            // 清理临时文件
            cleanupTempFiles()

            // 重置状态
            _progress.value = TimelapseProgress(
                state = TimelapseState.IDLE,
                message = "延时摄影已取消"
            )

            frameIndex = 0
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "cancel: 取消失败", e)
            Result.failure(e)
        }
    }

    /**
     * 获取当前捕获间隔应该拍摄的时机
     *
     * @return 是否应该拍摄
     */
    fun shouldCaptureFrame(): Boolean {
        return _progress.value.state == TimelapseState.RECORDING
    }

    /**
     * 获取捕获间隔
     *
     * @return 捕获间隔（毫秒）
     */
    fun getCaptureInterval(): Long = currentConfig.captureIntervalMs

    // ==================== 视频编码 ====================

    /**
     * 将帧序列编码为视频
     *
     * 使用MediaCodec + MediaMuxer进行H.264编码
     *
     * @param outputFile 输出文件
     */
    private suspend fun encodeFramesToVideo(outputFile: File) = withContext(Dispatchers.Default) {
        val dir = framesDir ?: throw IllegalStateException("帧目录不存在")

        // 获取帧文件列表
        val frameFiles = dir.listFiles { file ->
            file.name.startsWith("frame_") && file.name.endsWith(".jpg")
        }?.sortedBy { it.name } ?: throw IllegalStateException("没有找到帧文件")

        Log.d(TAG, "encodeFramesToVideo: 共${frameFiles.size}个帧文件")

        // 创建MediaFormat
        val format = MediaFormat.createVideoFormat(
            MIME_TYPE,
            currentConfig.videoWidth,
            currentConfig.videoHeight
        ).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, currentConfig.bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, currentConfig.outputFps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
        }

        // 创建编码器
        val encoder = MediaCodec.createEncoderByType(MIME_TYPE)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

        // 创建输入Surface
        val inputSurface = encoder.createInputSurface()
        encoder.start()

        // 创建Muxer
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false

        // 初始化EGL环境用于渲染到Surface
        val eglHelper = EglHelper()
        eglHelper.init(inputSurface)

        try {
            val bufferInfo = MediaCodec.BufferInfo()
            var presentationTimeUs = 0L

            for ((index, frameFile) in frameFiles.withIndex()) {
                if (!isActive) break

                // 加载并绘制帧
                val bitmap = BitmapFactory.decodeFile(frameFile.absolutePath)
                    ?: continue

                // 渲染到Surface
                eglHelper.drawFrame(bitmap, currentConfig.videoWidth, currentConfig.videoHeight)
                eglHelper.setPresentationTime(presentationTimeUs * 1000)  // 转换为纳秒
                eglHelper.swapBuffers()
                bitmap.recycle()

                // 处理编码输出
                drainEncoder(encoder, muxer, bufferInfo) { track, started ->
                    trackIndex = track
                    muxerStarted = started
                }

                presentationTimeUs += currentConfig.frameDurationUs

                // 更新进度
                val progress = (index + 1).toFloat() / frameFiles.size
                mutex.withLock {
                    _progress.value = _progress.value.copy(
                        encodingProgress = progress,
                        message = "编码中 ${(progress * 100).toInt()}%"
                    )
                }

                Log.d(TAG, "encodeFramesToVideo: 编码帧 ${index + 1}/${frameFiles.size}")
            }

            // 发送结束信号
            encoder.signalEndOfInputStream()

            // 排空编码器
            drainEncoder(encoder, muxer, bufferInfo, endOfStream = true) { track, started ->
                trackIndex = track
                muxerStarted = started
            }

        } finally {
            // 释放资源
            eglHelper.release()
            encoder.stop()
            encoder.release()
            if (muxerStarted) {
                muxer.stop()
            }
            muxer.release()
        }

        Log.d(TAG, "encodeFramesToVideo: 编码完成")
    }

    /**
     * 排空编码器输出
     */
    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean = false,
        onTrackAdded: (Int, Boolean) -> Unit
    ) {
        var trackIndex = -1
        var muxerStarted = false

        while (true) {
            val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)

            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) break
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val newFormat = encoder.outputFormat
                    trackIndex = muxer.addTrack(newFormat)
                    muxer.start()
                    muxerStarted = true
                    onTrackAdded(trackIndex, muxerStarted)
                }
                outputIndex >= 0 -> {
                    val outputBuffer = encoder.getOutputBuffer(outputIndex)
                        ?: continue

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size > 0 && muxerStarted) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                    }

                    encoder.releaseOutputBuffer(outputIndex, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        break
                    }
                }
            }
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建临时帧目录
     */
    private fun createTempFramesDir(): File {
        val dir = File(context.cacheDir, TEMP_DIR_NAME)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
        dir.mkdirs()
        return dir
    }

    /**
     * 清理临时文件
     */
    private fun cleanupTempFiles() {
        try {
            framesDir?.deleteRecursively()
            framesDir = null
            Log.d(TAG, "cleanupTempFiles: 临时文件已清理")
        } catch (e: Exception) {
            Log.w(TAG, "cleanupTempFiles: 清理失败", e)
        }
    }

    /**
     * 释放资源
     */
    suspend fun release() = mutex.withLock {
        Log.d(TAG, "release: 释放资源")
        captureJob?.cancelAndJoin()
        captureJob = null
        cleanupTempFiles()
        _progress.value = TimelapseProgress()
    }

    // ==================== EGL辅助类 ====================

    /**
     * EGL辅助类 - 用于渲染Bitmap到Surface
     *
     * 使用 TextureRenderer 进行正确的 OpenGL ES 纹理渲染
     */
    private inner class EglHelper {
        private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

        // OpenGL 纹理渲染器
        private var textureRenderer: TextureRenderer? = null

        /**
         * 初始化EGL环境
         */
        fun init(surface: Surface) {
            // 获取EGL display
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
                throw RuntimeException("无法获取EGL display")
            }

            // 初始化EGL
            val version = IntArray(2)
            if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
                throw RuntimeException("无法初始化EGL")
            }

            // 选择配置
            val configAttribs = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT or EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (!EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0)) {
                throw RuntimeException("无法选择EGL配置")
            }
            val config = configs[0] ?: throw RuntimeException("EGL配置为空")

            // 创建上下文
            val contextAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )
            eglContext = EGL14.eglCreateContext(eglDisplay, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
            if (eglContext == EGL14.EGL_NO_CONTEXT) {
                throw RuntimeException("无法创建EGL上下文")
            }

            // 创建Surface
            val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, config, surface, surfaceAttribs, 0)
            if (eglSurface == EGL14.EGL_NO_SURFACE) {
                throw RuntimeException("无法创建EGL Surface")
            }

            // 设为当前上下文
            if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                throw RuntimeException("无法设置EGL上下文")
            }

            // 初始化 TextureRenderer（必须在 EGL 上下文设置后）
            textureRenderer = TextureRenderer().apply {
                initialize()
            }
            Log.d(TAG, "EglHelper.init: TextureRenderer 初始化完成")
        }

        /**
         * 绘制帧到Surface
         *
         * 使用 TextureRenderer 进行正确的 OpenGL 纹理渲染
         */
        fun drawFrame(bitmap: Bitmap, width: Int, height: Int) {
            textureRenderer?.drawFrame(bitmap, width, height)
                ?: Log.w(TAG, "EglHelper.drawFrame: TextureRenderer 未初始化")
        }

        /**
         * 设置呈现时间戳
         */
        fun setPresentationTime(nsecs: Long) {
            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, nsecs)
        }

        /**
         * 交换缓冲区
         */
        fun swapBuffers(): Boolean {
            return EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        }

        /**
         * 释放资源
         */
        fun release() {
            // 释放 TextureRenderer
            textureRenderer?.release()
            textureRenderer = null

            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (eglSurface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(eglDisplay, eglSurface)
                }
                if (eglContext != EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(eglDisplay, eglContext)
                }
                EGL14.eglTerminate(eglDisplay)
            }
            eglDisplay = EGL14.EGL_NO_DISPLAY
            eglContext = EGL14.EGL_NO_CONTEXT
            eglSurface = EGL14.EGL_NO_SURFACE
            Log.d(TAG, "EglHelper.release: 资源释放完成")
        }
    }
}
