/**
 * TextureRenderer.kt - OpenGL ES 纹理渲染器
 *
 * 用于将 Bitmap 正确渲染到 EGL Surface（如 MediaCodec 输入 Surface）
 * 核心功能：
 * - 创建和管理 OpenGL 纹理
 * - 编译和链接着色器程序
 * - 绘制带纹理的四边形
 *
 * 技术实现：
 * - OpenGL ES 2.0 纹理渲染
 * - 顶点着色器 + 片段着色器
 * - 正交投影矩阵
 *
 * 使用场景：
 * - 延时摄影视频编码（将 Bitmap 帧渲染到 MediaCodec Surface）
 * - 任何需要将 Bitmap 渲染到 EGL Surface 的场景
 *
 * @author qihao
 * @since 2.0.0
 */
package com.qihao.filtercamera.data.processor.render

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * OpenGL ES 纹理渲染器
 *
 * 将 Bitmap 渲染到当前 EGL Surface
 */
class TextureRenderer {

    companion object {
        private const val TAG = "TextureRenderer"

        // 顶点着色器代码
        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = aTexCoord;
            }
        """

        // 片段着色器代码
        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexture;
            varying vec2 vTexCoord;
            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord);
            }
        """

        // 顶点坐标（全屏四边形）
        private val VERTEX_COORDS = floatArrayOf(
            -1.0f, -1.0f,  // 左下
             1.0f, -1.0f,  // 右下
            -1.0f,  1.0f,  // 左上
             1.0f,  1.0f   // 右上
        )

        // 纹理坐标（翻转Y轴以匹配 Bitmap 坐标系）
        private val TEXTURE_COORDS = floatArrayOf(
            0.0f, 1.0f,  // 左下
            1.0f, 1.0f,  // 右下
            0.0f, 0.0f,  // 左上
            1.0f, 0.0f   // 右上
        )

        // 每个顶点的坐标数
        private const val COORDS_PER_VERTEX = 2
        private const val VERTEX_STRIDE = COORDS_PER_VERTEX * 4  // 4 bytes per float
    }

    // 着色器程序 ID
    private var programId = 0

    // 属性和 uniform 位置
    private var positionHandle = 0
    private var texCoordHandle = 0
    private var textureHandle = 0

    // 纹理 ID
    private var textureId = 0

    // 顶点缓冲区
    private var vertexBuffer: FloatBuffer
    private var texCoordBuffer: FloatBuffer

    // 是否已初始化
    private var initialized = false

    init {
        // 初始化顶点缓冲区
        vertexBuffer = ByteBuffer.allocateDirect(VERTEX_COORDS.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(VERTEX_COORDS)
        vertexBuffer.position(0)

        // 初始化纹理坐标缓冲区
        texCoordBuffer = ByteBuffer.allocateDirect(TEXTURE_COORDS.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(TEXTURE_COORDS)
        texCoordBuffer.position(0)
    }

    /**
     * 初始化渲染器
     *
     * 必须在 EGL 上下文创建后调用
     */
    fun initialize() {
        if (initialized) {
            Log.d(TAG, "initialize: 已初始化，跳过")
            return
        }

        Log.d(TAG, "initialize: 开始初始化 TextureRenderer")

        // 编译着色器
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)

        // 创建程序
        programId = GLES20.glCreateProgram()
        checkGlError("glCreateProgram")

        GLES20.glAttachShader(programId, vertexShader)
        checkGlError("glAttachShader vertex")

        GLES20.glAttachShader(programId, fragmentShader)
        checkGlError("glAttachShader fragment")

        GLES20.glLinkProgram(programId)
        checkGlError("glLinkProgram")

        // 检查链接状态
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            val errorLog = GLES20.glGetProgramInfoLog(programId)
            GLES20.glDeleteProgram(programId)
            throw RuntimeException("程序链接失败: $errorLog")
        }

        // 获取属性位置
        positionHandle = GLES20.glGetAttribLocation(programId, "aPosition")
        texCoordHandle = GLES20.glGetAttribLocation(programId, "aTexCoord")
        textureHandle = GLES20.glGetUniformLocation(programId, "uTexture")

        Log.d(TAG, "initialize: 属性位置 position=$positionHandle, texCoord=$texCoordHandle, texture=$textureHandle")

        // 创建纹理
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        checkGlError("glGenTextures")

        // 配置纹理参数
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        checkGlError("glTexParameteri")

        // 删除着色器（已链接到程序，不再需要）
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)

        initialized = true
        Log.d(TAG, "initialize: TextureRenderer 初始化完成")
    }

    /**
     * 渲染 Bitmap 到当前 EGL Surface
     *
     * @param bitmap 要渲染的 Bitmap
     * @param viewportWidth 视口宽度
     * @param viewportHeight 视口高度
     */
    fun drawFrame(bitmap: Bitmap, viewportWidth: Int, viewportHeight: Int) {
        if (!initialized) {
            Log.w(TAG, "drawFrame: 渲染器未初始化，自动初始化")
            initialize()
        }

        // 设置视口
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)

        // 清屏
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        // 使用程序
        GLES20.glUseProgram(programId)
        checkGlError("glUseProgram")

        // 绑定纹理并上传 Bitmap
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        checkGlError("texImage2D")

        // 设置纹理单元
        GLES20.glUniform1i(textureHandle, 0)

        // 启用顶点属性数组
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glEnableVertexAttribArray(texCoordHandle)

        // 设置顶点坐标
        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(
            positionHandle,
            COORDS_PER_VERTEX,
            GLES20.GL_FLOAT,
            false,
            VERTEX_STRIDE,
            vertexBuffer
        )

        // 设置纹理坐标
        texCoordBuffer.position(0)
        GLES20.glVertexAttribPointer(
            texCoordHandle,
            COORDS_PER_VERTEX,
            GLES20.GL_FLOAT,
            false,
            VERTEX_STRIDE,
            texCoordBuffer
        )

        // 绘制三角形带
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        checkGlError("glDrawArrays")

        // 禁用顶点属性数组
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)

        // 解绑纹理
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    /**
     * 编译着色器
     *
     * @param type 着色器类型 (GL_VERTEX_SHADER 或 GL_FRAGMENT_SHADER)
     * @param source 着色器源代码
     * @return 着色器 ID
     */
    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        checkGlError("glCreateShader type=$type")

        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)

        // 检查编译状态
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] != GLES20.GL_TRUE) {
            val errorLog = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw RuntimeException("着色器编译失败: $errorLog")
        }

        val typeStr = if (type == GLES20.GL_VERTEX_SHADER) "顶点" else "片段"
        Log.d(TAG, "compileShader: ${typeStr}着色器编译成功")

        return shader
    }

    /**
     * 检查 OpenGL 错误
     */
    private fun checkGlError(operation: String) {
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            Log.e(TAG, "checkGlError: $operation 失败，错误码=$error")
            throw RuntimeException("OpenGL 错误: $operation, 错误码=$error")
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        Log.d(TAG, "release: 释放 TextureRenderer 资源")

        if (textureId != 0) {
            val textures = intArrayOf(textureId)
            GLES20.glDeleteTextures(1, textures, 0)
            textureId = 0
        }

        if (programId != 0) {
            GLES20.glDeleteProgram(programId)
            programId = 0
        }

        initialized = false
        Log.d(TAG, "release: TextureRenderer 资源释放完成")
    }

    /**
     * 检查是否已初始化
     */
    fun isInitialized(): Boolean = initialized
}
