/**
 * Extensions.kt - Kotlin扩展函数
 *
 * 提供常用的扩展函数，简化代码编写
 * 包含：Result处理、Flow操作、Context扩展等
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.presentation.common

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

// ==================== Result 扩展 ====================

/**
 * Result成功时执行操作并返回自身（链式调用）
 */
inline fun <T> Result<T>.alsoOnSuccess(action: (T) -> Unit): Result<T> {
    onSuccess(action)
    return this
}

/**
 * Result失败时执行操作并返回自身（链式调用）
 */
inline fun <T> Result<T>.alsoOnFailure(action: (Throwable) -> Unit): Result<T> {
    onFailure(action)
    return this
}

/**
 * Result转换为Unit Result（忽略成功值）
 */
fun <T> Result<T>.toUnitResult(): Result<Unit> =
    map { }

// ==================== Context 扩展 ====================

/**
 * 显示短Toast
 */
fun Context.showToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

/**
 * 显示长Toast
 */
fun Context.showLongToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}

// ==================== Flow 扩展 ====================

/**
 * 在Composable中收集Flow最新值
 */
@Composable
fun <T> Flow<T>.CollectLatestEffect(action: suspend (T) -> Unit) {
    LaunchedEffect(this) {
        collectLatest(action)
    }
}

// ==================== String 扩展 ====================

/**
 * 安全截取字符串
 */
fun String.safeSubstring(startIndex: Int, endIndex: Int = length): String {
    val safeStart = startIndex.coerceIn(0, length)
    val safeEnd = endIndex.coerceIn(safeStart, length)
    return substring(safeStart, safeEnd)
}

/**
 * 截取前N个字符
 */
fun String.takeFirst(n: Int): String = take(n.coerceAtLeast(0))

// ==================== Number 扩展 ====================

/**
 * Float限制在0-1范围内
 */
fun Float.clampNormalized(): Float = coerceIn(0f, 1f)

/**
 * Int转换为百分比字符串
 */
fun Int.toPercentString(): String = "$this%"

/**
 * Float转换为百分比字符串
 */
fun Float.toPercentString(): String = "${(this * 100).toInt()}%"
