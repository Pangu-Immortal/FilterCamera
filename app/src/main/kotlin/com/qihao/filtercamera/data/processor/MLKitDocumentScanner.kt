/**
 * MLKitDocumentScanner.kt - ML Kit 文档扫描器包装类
 *
 * 封装 Google ML Kit Document Scanner API，提供高级文档扫描功能
 * 支持自动边缘检测、透视校正、阴影去除、PDF导出等
 *
 * 功能特点：
 * - 高精度文档边缘检测（基于机器学习）
 * - 内置用户确认UI界面
 * - 自动阴影去除
 * - 支持PDF和JPEG输出
 * - 支持单页和多页扫描
 *
 * 使用要求：
 * - Android API 21+
 * - 最小1.7GB RAM
 * - Google Play Services
 *
 * @author qihao
 * @since 3.0.0
 */
package com.qihao.filtercamera.data.processor

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 文档扫描器配置
 *
 * @param pageLimit 最大扫描页数（0表示无限制）
 * @param galleryImportAllowed 是否允许从相册导入
 * @param enableAutoCapture 是否启用自动捕获（边缘稳定后自动拍照）
 * @param resultFormats 输出格式（JPEG、PDF或两者）
 * @param scannerMode 扫描模式（完整功能/仅基础/仅带滤镜基础）
 */
data class DocumentScannerConfig(
    val pageLimit: Int = 0,                                            // 多页扫描（0=无限制）
    val galleryImportAllowed: Boolean = true,                          // 允许从相册导入
    val enableAutoCapture: Boolean = true,                             // 启用自动捕获
    val resultFormats: Set<ResultFormat> = setOf(ResultFormat.JPEG, ResultFormat.PDF),  // JPEG+PDF输出
    val scannerMode: ScannerMode = ScannerMode.FULL                    // 完整模式
)

/**
 * 扫描结果格式
 */
enum class ResultFormat {
    JPEG,      // JPEG图片格式
    PDF        // PDF文档格式
}

/**
 * 扫描器模式
 */
enum class ScannerMode {
    FULL,                // 完整功能：边缘检测+滤镜+手动调整
    BASE,                // 基础模式：边缘检测+手动调整（无滤镜）
    BASE_WITH_FILTER     // 带滤镜的基础模式：边缘检测+滤镜（无手动调整）
}

/**
 * 文档扫描结果
 *
 * @param pageCount 扫描的页数
 * @param pages 每页的Uri列表（JPEG格式）
 * @param pdfUri PDF文件Uri（如果请求了PDF格式）
 */
data class MLKitScanResult(
    val pageCount: Int,
    val pages: List<Uri>,
    val pdfUri: Uri?
)

/**
 * 扫描状态
 */
sealed class ScanState {
    object Idle : ScanState()                                          // 空闲
    object Scanning : ScanState()                                      // 扫描中
    data class Success(val result: MLKitScanResult) : ScanState()      // 成功
    data class Error(val message: String) : ScanState()                // 错误
    object Cancelled : ScanState()                                     // 用户取消
}

/**
 * ML Kit 文档扫描器
 *
 * 提供高级文档扫描功能，基于 Google ML Kit Document Scanner API
 */
@Singleton
class MLKitDocumentScanner @Inject constructor() {

    companion object {
        private const val TAG = "MLKitDocumentScanner"                 // 日志标签

        /**
         * 检查设备是否支持 ML Kit Document Scanner
         *
         * @param context 上下文
         * @return 是否支持
         */
        fun isSupported(context: Context): Boolean {
            return try {
                // ML Kit Document Scanner 需要 Google Play Services
                // 通过尝试获取客户端来检测支持性
                val options = GmsDocumentScannerOptions.Builder()
                    .setGalleryImportAllowed(false)
                    .setPageLimit(1)
                    .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE)
                    .build()
                GmsDocumentScanning.getClient(options)
                true
            } catch (e: Exception) {
                Log.w(TAG, "isSupported: ML Kit Document Scanner 不可用", e)
                false
            }
        }
    }

    // 扫描状态
    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    // 当前扫描器实例
    private var currentScanner: GmsDocumentScanner? = null

    // 当前配置
    private var currentConfig: DocumentScannerConfig = DocumentScannerConfig()

    /**
     * 配置扫描器
     *
     * @param config 扫描配置
     */
    fun configure(config: DocumentScannerConfig) {
        Log.d(TAG, "configure: 配置扫描器 pageLimit=${config.pageLimit} " +
                "formats=${config.resultFormats} mode=${config.scannerMode}")
        currentConfig = config
        currentScanner = null                                          // 清除旧实例，使用新配置
    }

    /**
     * 获取或创建扫描器实例
     */
    private fun getScanner(): GmsDocumentScanner {
        currentScanner?.let { return it }

        val optionsBuilder = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(currentConfig.galleryImportAllowed)

        // 设置页数限制
        if (currentConfig.pageLimit > 0) {
            optionsBuilder.setPageLimit(currentConfig.pageLimit)
        }

        // 设置输出格式
        var formatFlags = 0
        if (ResultFormat.JPEG in currentConfig.resultFormats) {
            formatFlags = formatFlags or GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
        }
        if (ResultFormat.PDF in currentConfig.resultFormats) {
            formatFlags = formatFlags or GmsDocumentScannerOptions.RESULT_FORMAT_PDF
        }
        if (formatFlags == 0) {
            formatFlags = GmsDocumentScannerOptions.RESULT_FORMAT_JPEG // 默认JPEG
        }
        optionsBuilder.setResultFormats(formatFlags)

        // 设置扫描模式
        val mode = when (currentConfig.scannerMode) {
            ScannerMode.FULL -> GmsDocumentScannerOptions.SCANNER_MODE_FULL
            ScannerMode.BASE -> GmsDocumentScannerOptions.SCANNER_MODE_BASE
            ScannerMode.BASE_WITH_FILTER -> GmsDocumentScannerOptions.SCANNER_MODE_BASE_WITH_FILTER
        }
        optionsBuilder.setScannerMode(mode)

        val scanner = GmsDocumentScanning.getClient(optionsBuilder.build())
        currentScanner = scanner
        Log.d(TAG, "getScanner: 创建新扫描器实例")
        return scanner
    }

    /**
     * 启动文档扫描
     *
     * 使用 Activity Result API 启动 ML Kit 的扫描界面
     *
     * @param activity Activity 实例
     * @param launcher ActivityResultLauncher（需要在 Activity/Fragment 中注册）
     */
    fun startScan(
        activity: Activity,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ) {
        Log.d(TAG, "startScan: 启动文档扫描")
        _scanState.value = ScanState.Scanning

        val scanner = getScanner()

        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                Log.d(TAG, "startScan: 获取扫描Intent成功，启动扫描界面")
                val request = IntentSenderRequest.Builder(intentSender).build()
                launcher.launch(request)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "startScan: 启动扫描失败", e)
                _scanState.value = ScanState.Error(e.message ?: "启动扫描失败")
            }
    }

    /**
     * 处理扫描结果
     *
     * 在 ActivityResultCallback 中调用此方法处理返回结果
     *
     * @param resultCode Activity 结果码
     * @param data Intent 数据
     */
    fun handleScanResult(resultCode: Int, data: Intent?) {
        Log.d(TAG, "handleScanResult: 处理扫描结果 resultCode=$resultCode")

        when (resultCode) {
            Activity.RESULT_OK -> {
                val result = GmsDocumentScanningResult.fromActivityResultIntent(data)
                if (result != null) {
                    val pages = result.pages?.mapNotNull { it.imageUri } ?: emptyList()
                    val pdfUri = result.pdf?.uri

                    Log.d(TAG, "handleScanResult: 扫描成功 pages=${pages.size} hasPdf=${pdfUri != null}")

                    _scanState.value = ScanState.Success(
                        MLKitScanResult(
                            pageCount = pages.size,
                            pages = pages,
                            pdfUri = pdfUri
                        )
                    )
                } else {
                    Log.w(TAG, "handleScanResult: 结果为空")
                    _scanState.value = ScanState.Error("扫描结果为空")
                }
            }
            Activity.RESULT_CANCELED -> {
                Log.d(TAG, "handleScanResult: 用户取消扫描")
                _scanState.value = ScanState.Cancelled
            }
            else -> {
                Log.w(TAG, "handleScanResult: 未知结果码 $resultCode")
                _scanState.value = ScanState.Error("扫描失败: 未知错误 ($resultCode)")
            }
        }
    }

    /**
     * 重置状态
     *
     * 在下次扫描前调用，清除上次的状态
     */
    fun resetState() {
        Log.d(TAG, "resetState: 重置扫描状态")
        _scanState.value = ScanState.Idle
    }

    /**
     * 释放资源
     */
    fun release() {
        Log.d(TAG, "release: 释放资源")
        currentScanner = null
        _scanState.value = ScanState.Idle
    }
}
