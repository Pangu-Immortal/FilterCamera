/**
 * FrameRingBuffer.kt - 帧环形缓冲区
 *
 * 用于存储最新的相机帧，支持跳帧策略
 * 当帧处理速度跟不上帧产生速度时，自动丢弃旧帧
 *
 * 设计原则：
 * - 线程安全：使用锁保护共享数据
 * - 无阻塞写入：生产者永远不会阻塞
 * - 内存高效：固定容量，循环利用
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.data.util

import android.graphics.Bitmap
import android.util.Log
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 帧数据包装类
 *
 * @param bitmap 帧图像
 * @param timestamp 帧时间戳（纳秒）
 * @param frameId 帧序号
 */
data class FrameData(
    val bitmap: Bitmap,
    val timestamp: Long,
    val frameId: Long
)

/**
 * 帧环形缓冲区
 *
 * 固定容量的环形缓冲区，当满时覆盖最旧的帧
 *
 * @param capacity 缓冲区容量（推荐3-5帧）
 */
class FrameRingBuffer(private val capacity: Int = 3) {

    companion object {
        private const val TAG = "FrameRingBuffer"
    }

    // 缓冲区数组
    private val buffer = arrayOfNulls<FrameData>(capacity)

    // 读写指针
    private var writeIndex = 0                             // 下一个写入位置
    private var readIndex = 0                              // 下一个读取位置
    private var size = 0                                   // 当前缓冲区大小

    // 访问锁
    private val lock = ReentrantLock()

    // 统计计数器
    private val totalWriteCount = AtomicLong(0)            // 总写入帧数
    private val totalDropCount = AtomicLong(0)             // 丢弃帧数
    private val totalReadCount = AtomicLong(0)             // 读取帧数

    /**
     * 写入帧（非阻塞）
     *
     * 如果缓冲区已满，覆盖最旧的帧
     *
     * @param bitmap 帧图像
     * @return 被丢弃的旧帧（用于回收），没有则返回null
     */
    fun write(bitmap: Bitmap): Bitmap? {
        val frameId = totalWriteCount.incrementAndGet()
        val timestamp = System.nanoTime()

        lock.withLock {
            // 获取被覆盖的旧帧（用于回收）
            val oldFrame = if (size == capacity) {
                totalDropCount.incrementAndGet()
                buffer[writeIndex]
            } else {
                null
            }

            // 写入新帧
            buffer[writeIndex] = FrameData(bitmap, timestamp, frameId)

            // 更新指针
            writeIndex = (writeIndex + 1) % capacity
            if (size < capacity) {
                size++
            } else {
                // 缓冲区满时，读指针跟随写指针
                readIndex = writeIndex
            }

            // 每100帧输出一次统计
            if (frameId % 100 == 0L) {
                Log.d(TAG, "write: 统计 total=$frameId, dropped=${totalDropCount.get()}, read=${totalReadCount.get()}")
            }

            return oldFrame?.bitmap
        }
    }

    /**
     * 读取最新帧（非阻塞）
     *
     * 返回最新的帧数据，但不从缓冲区移除
     *
     * @return 最新的帧数据，缓冲区为空返回null
     */
    fun peekLatest(): FrameData? {
        lock.withLock {
            if (size == 0) return null

            // 计算最新帧的位置
            val latestIndex = (writeIndex - 1 + capacity) % capacity
            return buffer[latestIndex]
        }
    }

    /**
     * 读取并移除最旧的帧
     *
     * @return 最旧的帧数据，缓冲区为空返回null
     */
    fun poll(): FrameData? {
        lock.withLock {
            if (size == 0) return null

            val frame = buffer[readIndex]
            buffer[readIndex] = null                       // 清除引用
            readIndex = (readIndex + 1) % capacity
            size--
            totalReadCount.incrementAndGet()

            return frame
        }
    }

    /**
     * 读取所有帧并清空缓冲区
     *
     * @return 所有帧列表（从旧到新）
     */
    fun pollAll(): List<FrameData> {
        lock.withLock {
            if (size == 0) return emptyList()

            val frames = mutableListOf<FrameData>()
            while (size > 0) {
                buffer[readIndex]?.let { frames.add(it) }
                buffer[readIndex] = null
                readIndex = (readIndex + 1) % capacity
                size--
            }
            totalReadCount.addAndGet(frames.size.toLong())

            return frames
        }
    }

    /**
     * 获取缓冲区当前大小
     */
    fun size(): Int = lock.withLock { size }

    /**
     * 检查缓冲区是否为空
     */
    fun isEmpty(): Boolean = lock.withLock { size == 0 }

    /**
     * 检查缓冲区是否已满
     */
    fun isFull(): Boolean = lock.withLock { size == capacity }

    /**
     * 清空缓冲区
     *
     * @return 被清空的所有Bitmap（用于回收）
     */
    fun clear(): List<Bitmap> {
        lock.withLock {
            val bitmaps = buffer.filterNotNull().map { it.bitmap }
            for (i in buffer.indices) {
                buffer[i] = null
            }
            writeIndex = 0
            readIndex = 0
            size = 0
            Log.d(TAG, "clear: 缓冲区已清空，回收${bitmaps.size}个Bitmap")
            return bitmaps
        }
    }

    /**
     * 获取统计信息
     *
     * @return 统计信息字符串
     */
    fun getStatistics(): FrameBufferStatistics {
        return FrameBufferStatistics(
            capacity = capacity,
            currentSize = size(),
            totalWriteCount = totalWriteCount.get(),
            totalDropCount = totalDropCount.get(),
            totalReadCount = totalReadCount.get(),
            dropRate = if (totalWriteCount.get() > 0) {
                totalDropCount.get().toFloat() / totalWriteCount.get() * 100
            } else 0f
        )
    }
}

/**
 * 帧缓冲区统计信息
 */
data class FrameBufferStatistics(
    val capacity: Int,
    val currentSize: Int,
    val totalWriteCount: Long,
    val totalDropCount: Long,
    val totalReadCount: Long,
    val dropRate: Float
) {
    override fun toString(): String {
        return "FrameBufferStatistics(容量=$capacity, 当前=$currentSize, " +
                "写入=$totalWriteCount, 丢弃=$totalDropCount, 读取=$totalReadCount, " +
                "丢帧率=${String.format("%.2f", dropRate)}%)"
    }
}
