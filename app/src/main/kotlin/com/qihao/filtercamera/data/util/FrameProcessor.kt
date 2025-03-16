/**
 * FrameProcessor.kt - 异步帧处理器
 *
 * 在后台线程异步处理相机帧，避免阻塞ImageAnalysis回调
 * 使用FrameRingBuffer实现跳帧策略
 *
 * 设计原则：
 * - 生产者-消费者模式：ImageAnalysis生产帧，处理器消费帧
 * - 跳帧策略：处理不过来时自动丢弃旧帧
 * - 性能监控：实时输出处理耗时和帧率
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.data.util

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 帧处理结果
 */
sealed class FrameProcessResult {
    /** 处理成功 */
    data class Success(val bitmap: Bitmap, val processingTimeMs: Long) : FrameProcessResult()

    /** 处理跳过（无新帧） */
    object Skipped : FrameProcessResult()

    /** 处理失败 */
    data class Error(val message: String, val cause: Throwable? = null) : FrameProcessResult()
}

/**
 * 帧处理回调接口
 */
fun interface FrameProcessCallback {
    /**
     * 处理单帧
     *
     * @param bitmap 待处理的帧
     * @return 处理后的帧，null表示处理失败
     */
    fun process(bitmap: Bitmap): Bitmap?
}

/**
 * 异步帧处理器
 *
 * @param bufferCapacity 帧缓冲区容量
 * @param processingIntervalMs 处理间隔（毫秒），控制处理帧率
 */
class FrameProcessor(
    private val bufferCapacity: Int = 3,
    private val processingIntervalMs: Long = 33L              // 默认30fps
) {

    companion object {
        private const val TAG = "FrameProcessor"
        private const val STATS_LOG_INTERVAL = 100            // 每100帧输出一次统计
    }

    // 帧缓冲区
    private val frameBuffer = FrameRingBuffer(bufferCapacity)

    // 处理器协程作用域
    private var processorScope: CoroutineScope? = null
    private var processorJob: Job? = null

    // 处理回调
    private var processCallback: FrameProcessCallback? = null

    // 处理结果Flow
    private val _processedFrame = MutableStateFlow<Bitmap?>(null)
    val processedFrame: StateFlow<Bitmap?> = _processedFrame

    // 原始帧Flow（用于缩略图生成）
    private val _rawFrame = MutableStateFlow<Bitmap?>(null)
    val rawFrame: StateFlow<Bitmap?> = _rawFrame

    // 状态标志
    private val isRunning = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)

    // 性能统计
    private val processedCount = AtomicLong(0)
    private val totalProcessingTimeMs = AtomicLong(0)
    private var lastStatsTime = System.currentTimeMillis()
    private var lastStatsFrameCount = 0L

    /**
     * 启动帧处理器
     *
     * @param callback 帧处理回调
     */
    fun start(callback: FrameProcessCallback) {
        if (isRunning.getAndSet(true)) {
            Log.w(TAG, "start: 处理器已在运行")
            return
        }

        Log.d(TAG, "start: 启动帧处理器 bufferCapacity=$bufferCapacity, interval=${processingIntervalMs}ms")
        processCallback = callback

        // 创建处理器协程作用域
        processorScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        processorJob = processorScope?.launch {
            processLoop()
        }
    }

    /**
     * 停止帧处理器
     */
    fun stop() {
        if (!isRunning.getAndSet(false)) {
            Log.w(TAG, "stop: 处理器未运行")
            return
        }

        Log.d(TAG, "stop: 停止帧处理器")

        // 取消协程
        processorJob?.cancel()
        processorJob = null
        processorScope?.cancel()
        processorScope = null

        // 清空缓冲区并回收Bitmap
        val bitmaps = frameBuffer.clear()
        bitmaps.forEach { if (!it.isRecycled) it.recycle() }

        // 输出最终统计
        logFinalStatistics()

        // 清空回调
        processCallback = null
    }

    /**
     * 暂停处理
     */
    fun pause() {
        Log.d(TAG, "pause: 暂停帧处理")
        isPaused.set(true)
    }

    /**
     * 恢复处理
     */
    fun resume() {
        Log.d(TAG, "resume: 恢复帧处理")
        isPaused.set(false)
    }

    /**
     * 提交新帧到缓冲区
     *
     * 此方法由ImageAnalysis回调调用，必须快速返回
     *
     * @param bitmap 相机帧（调用者负责拷贝，此处直接使用）
     */
    fun submitFrame(bitmap: Bitmap) {
        if (!isRunning.get()) {
            Log.w(TAG, "submitFrame: 处理器未运行，丢弃帧")
            bitmap.recycle()
            return
        }

        // 更新原始帧（用于缩略图生成，只在首次或为空时更新）
        if (_rawFrame.value == null) {
            _rawFrame.value = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
            Log.d(TAG, "submitFrame: 更新原始帧用于缩略图")
        }

        // 写入缓冲区，回收被覆盖的旧帧
        val oldBitmap = frameBuffer.write(bitmap)
        oldBitmap?.let {
            if (!it.isRecycled) it.recycle()
        }
    }

    /**
     * 处理循环（运行在后台线程）
     */
    private suspend fun processLoop() {
        Log.d(TAG, "processLoop: 处理循环开始")

        while (isRunning.get()) {
            // 检查暂停状态
            if (isPaused.get()) {
                delay(processingIntervalMs)
                continue
            }

            // 从缓冲区获取最新帧
            val frameData = frameBuffer.peekLatest()
            if (frameData == null) {
                delay(processingIntervalMs / 2)             // 无帧时短暂等待
                continue
            }

            // 处理帧
            val startTime = System.currentTimeMillis()
            try {
                val callback = processCallback
                if (callback != null) {
                    val result = callback.process(frameData.bitmap)
                    if (result != null) {
                        _processedFrame.value = result
                    }
                }

                // 更新统计
                val processingTime = System.currentTimeMillis() - startTime
                processedCount.incrementAndGet()
                totalProcessingTimeMs.addAndGet(processingTime)

                // 定期输出统计
                logStatisticsIfNeeded()

            } catch (e: Exception) {
                Log.e(TAG, "processLoop: 处理帧异常", e)
            }

            // 控制处理帧率
            val elapsed = System.currentTimeMillis() - startTime
            val sleepTime = processingIntervalMs - elapsed
            if (sleepTime > 0) {
                delay(sleepTime)
            }
        }

        Log.d(TAG, "processLoop: 处理循环结束")
    }

    /**
     * 定期输出统计信息
     */
    private fun logStatisticsIfNeeded() {
        val count = processedCount.get()
        if (count > 0 && count % STATS_LOG_INTERVAL == 0L) {
            val currentTime = System.currentTimeMillis()
            val elapsedTime = currentTime - lastStatsTime
            val framesDelta = count - lastStatsFrameCount

            if (elapsedTime > 0 && framesDelta > 0) {
                val fps = framesDelta * 1000.0 / elapsedTime
                val avgProcessingTime = totalProcessingTimeMs.get().toDouble() / count
                val bufferStats = frameBuffer.getStatistics()

                Log.d(TAG, "【帧处理统计】FPS=${String.format("%.1f", fps)}, " +
                        "平均耗时=${String.format("%.1f", avgProcessingTime)}ms, " +
                        "丢帧率=${String.format("%.1f", bufferStats.dropRate)}%")
            }

            lastStatsTime = currentTime
            lastStatsFrameCount = count
        }
    }

    /**
     * 输出最终统计信息
     */
    private fun logFinalStatistics() {
        val count = processedCount.get()
        if (count > 0) {
            val avgProcessingTime = totalProcessingTimeMs.get().toDouble() / count
            val bufferStats = frameBuffer.getStatistics()

            Log.i(TAG, "【帧处理最终统计】" +
                    "总处理帧数=$count, " +
                    "平均耗时=${String.format("%.1f", avgProcessingTime)}ms, " +
                    "缓冲区统计=$bufferStats")
        }
    }

    /**
     * 获取当前统计信息
     */
    fun getStatistics(): FrameProcessorStatistics {
        val count = processedCount.get()
        val avgTime = if (count > 0) totalProcessingTimeMs.get().toDouble() / count else 0.0
        val bufferStats = frameBuffer.getStatistics()

        return FrameProcessorStatistics(
            processedCount = count,
            averageProcessingTimeMs = avgTime,
            bufferStatistics = bufferStats,
            isRunning = isRunning.get(),
            isPaused = isPaused.get()
        )
    }

    /**
     * 检查处理器是否正在运行
     */
    fun isRunning(): Boolean = isRunning.get()
}

/**
 * 帧处理器统计信息
 */
data class FrameProcessorStatistics(
    val processedCount: Long,
    val averageProcessingTimeMs: Double,
    val bufferStatistics: FrameBufferStatistics,
    val isRunning: Boolean,
    val isPaused: Boolean
) {
    override fun toString(): String {
        return "FrameProcessorStatistics(" +
                "已处理=$processedCount, " +
                "平均耗时=${String.format("%.1f", averageProcessingTimeMs)}ms, " +
                "运行=$isRunning, 暂停=$isPaused, " +
                "缓冲区=$bufferStatistics)"
    }
}
