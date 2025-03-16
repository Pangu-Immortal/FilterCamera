/**
 * FilterRegistry.kt - 滤镜注册表
 *
 * 使用注册表模式管理所有滤镜，替代传统的大型 switch 语句
 * 提供统一的滤镜注册、查询和创建接口
 *
 * 设计优势：
 * - 开闭原则：新增滤镜只需注册，无需修改工厂代码
 * - 单一职责：每个滤镜的创建逻辑独立封装
 * - 可扩展性：支持动态注册/注销滤镜
 * - 元数据管理：集中管理滤镜的显示名、分组、图标等
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filter.registry

import android.util.Log
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import java.util.concurrent.ConcurrentHashMap

/**
 * 滤镜分组枚举
 *
 * 定义滤镜的分类，用于 UI 展示和筛选
 */
enum class FilterCategory(val displayName: String, val order: Int) {
    ORIGINAL("原图", 0),                    // 原图/无滤镜
    STYLE("风格", 1),                       // 风格滤镜
    EFFECT("特效", 2),                      // 特效滤镜
    INSTAGRAM("INS", 3),                    // Instagram 风格
    WATERMARK("水印", 4),                   // 水印滤镜
    ADJUST("调整", 5);                      // 图像调整

    companion object {
        /**
         * 获取相机页面可用的分组（排除调整分组）
         */
        fun getCameraCategories(): List<FilterCategory> =
            entries.filter { it != ADJUST }.sortedBy { it.order }

        /**
         * 获取所有分组
         */
        fun getAllCategories(): List<FilterCategory> =
            entries.sortedBy { it.order }
    }
}

/**
 * 滤镜信息数据类
 *
 * 封装滤镜的完整元数据
 *
 * @param id 滤镜唯一标识（与 FilterType 枚举名对应）
 * @param displayName 显示名称
 * @param category 所属分组
 * @param useGpu 是否使用 GPU 处理
 * @param thumbnailRes 缩略图资源 ID（可选）
 * @param description 滤镜描述（可选）
 * @param factory 滤镜创建工厂函数
 */
data class FilterInfo(
    val id: String,
    val displayName: String,
    val category: FilterCategory,
    val useGpu: Boolean = true,
    val thumbnailRes: Int? = null,
    val description: String = "",
    val factory: () -> GPUImageFilter
)

/**
 * 滤镜注册表
 *
 * 单例模式，管理所有注册的滤镜
 */
object FilterRegistry {

    private const val TAG = "FilterRegistry"

    // 滤镜注册表：ID -> FilterInfo
    private val registry = ConcurrentHashMap<String, FilterInfo>()

    // 分组索引：Category -> List<FilterInfo>
    private val categoryIndex = ConcurrentHashMap<FilterCategory, MutableList<FilterInfo>>()

    // 注册状态标志
    @Volatile
    private var initialized = false

    /**
     * 注册滤镜
     *
     * @param info 滤镜信息
     * @return 是否注册成功
     */
    fun register(info: FilterInfo): Boolean {
        if (registry.containsKey(info.id)) {
            Log.w(TAG, "register: 滤镜已存在，覆盖注册 id=${info.id}")
        }

        registry[info.id] = info

        // 更新分组索引
        categoryIndex.getOrPut(info.category) { mutableListOf() }.apply {
            removeIf { it.id == info.id }                           // 移除旧条目
            add(info)
        }

        Log.d(TAG, "register: 注册滤镜 id=${info.id}, category=${info.category.displayName}")
        return true
    }

    /**
     * 批量注册滤镜
     *
     * @param filters 滤镜信息列表
     */
    fun registerAll(filters: List<FilterInfo>) {
        filters.forEach { register(it) }
        Log.i(TAG, "registerAll: 批量注册完成 count=${filters.size}")
    }

    /**
     * 注销滤镜
     *
     * @param id 滤镜 ID
     * @return 是否注销成功
     */
    fun unregister(id: String): Boolean {
        val info = registry.remove(id) ?: return false

        // 更新分组索引
        categoryIndex[info.category]?.removeIf { it.id == id }

        Log.d(TAG, "unregister: 注销滤镜 id=$id")
        return true
    }

    /**
     * 获取滤镜信息
     *
     * @param id 滤镜 ID
     * @return 滤镜信息，不存在返回 null
     */
    fun getFilterInfo(id: String): FilterInfo? = registry[id]

    /**
     * 创建滤镜实例
     *
     * @param id 滤镜 ID
     * @return 滤镜实例，不存在返回默认滤镜
     */
    fun createFilter(id: String): GPUImageFilter {
        val info = registry[id]
        return if (info != null) {
            try {
                info.factory()
            } catch (e: Exception) {
                Log.e(TAG, "createFilter: 创建滤镜失败 id=$id", e)
                GPUImageFilter()                                     // 返回默认滤镜
            }
        } else {
            Log.w(TAG, "createFilter: 滤镜未注册 id=$id，返回默认滤镜")
            GPUImageFilter()
        }
    }

    /**
     * 获取指定分组的所有滤镜
     *
     * @param category 滤镜分组
     * @return 滤镜信息列表
     */
    fun getFiltersByCategory(category: FilterCategory): List<FilterInfo> =
        categoryIndex[category]?.toList() ?: emptyList()

    /**
     * 获取所有已注册的滤镜
     *
     * @return 滤镜信息列表
     */
    fun getAllFilters(): List<FilterInfo> = registry.values.toList()

    /**
     * 获取所有滤镜 ID
     *
     * @return ID 列表
     */
    fun getAllFilterIds(): List<String> = registry.keys.toList()

    /**
     * 检查滤镜是否已注册
     *
     * @param id 滤镜 ID
     * @return 是否已注册
     */
    fun isRegistered(id: String): Boolean = registry.containsKey(id)

    /**
     * 获取注册的滤镜数量
     */
    fun getFilterCount(): Int = registry.size

    /**
     * 获取各分组的滤镜数量
     */
    fun getCategoryCounts(): Map<FilterCategory, Int> =
        categoryIndex.mapValues { it.value.size }

    /**
     * 清空注册表
     */
    fun clear() {
        registry.clear()
        categoryIndex.clear()
        initialized = false
        Log.w(TAG, "clear: 注册表已清空")
    }

    /**
     * 标记初始化完成
     */
    fun markInitialized() {
        initialized = true
        Log.i(TAG, "markInitialized: 注册表初始化完成 totalFilters=${registry.size}")
    }

    /**
     * 检查是否已初始化
     */
    fun isInitialized(): Boolean = initialized

    /**
     * 获取注册表统计信息
     */
    fun getStatistics(): String = buildString {
        append("【滤镜注册表统计】\n")
        append("总滤镜数: ${registry.size}\n")
        append("分组统计:\n")
        FilterCategory.entries.forEach { category ->
            val count = categoryIndex[category]?.size ?: 0
            append("  - ${category.displayName}: $count\n")
        }
        append("初始化状态: ${if (initialized) "已完成" else "未完成"}")
    }
}
