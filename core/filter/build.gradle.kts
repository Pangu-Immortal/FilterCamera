// core:filter 模块 - 滤镜引擎核心库
// 包含OpenGL渲染、滤镜实现、JNI美颜算法

import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

/**
 * Artifact Transform: 从 AAR 中移除 jniLibs
 * 用于过滤掉 gpuimage 中未对齐 16KB 的 libyuv-decoder.so
 */
@CacheableTransform
abstract class StripJniLibsTransform : TransformAction<TransformParameters.None> {
    @get:InputArtifact
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val inputFile = inputArtifact.get().asFile
        if (inputFile.extension == "aar") {
            val outputFile = outputs.file(inputFile.nameWithoutExtension + "-stripped.aar")
            ZipFile(inputFile).use { zipIn ->
                ZipOutputStream(outputFile.outputStream()).use { zipOut ->
                    zipIn.entries().asSequence().forEach { entry ->
                        // 跳过 jni 目录下的所有 .so 文件
                        if (!entry.name.startsWith("jni/") || !entry.name.endsWith(".so")) {
                            zipOut.putNextEntry(ZipEntry(entry.name))
                            if (!entry.isDirectory) {
                                zipIn.getInputStream(entry).copyTo(zipOut)
                            }
                            zipOut.closeEntry()
                        }
                    }
                }
            }
        } else {
            outputs.file(inputFile)
        }
    }
}

android {
    namespace = "com.qihao.filter"
    compileSdk = libs.versions.compileSdk.get().toInt()
    ndkVersion = "27.0.12077973"                             // 指定可用的NDK版本

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        // NDK配置 - Native代码编译
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DANDROID_ARM_NEON=TRUE"
                )
            }
        }

        // ABI配置 - 支持主流架构和模拟器
        // arm64-v8a: 现代ARM设备（主流）
        // armeabi-v7a: 旧ARM设备（兼容）
        // x86_64: 模拟器和x86设备（开发调试）
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }

        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Native代码构建配置
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // 打包配置 - 处理native库冲突
    // 排除gpuimage依赖的libyuv-decoder.so（未对齐16KB）
    // 保留本模块CMake编译的16KB对齐版本
    packaging {
        jniLibs {
            // 使用pickFirsts让项目构建的版本优先
            pickFirsts += "**/libyuv-decoder.so"
            // 同时设置keepDebugSymbols以确保正确处理
            keepDebugSymbols += "**/libyuv-decoder.so"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }
}

// 自定义属性标记：已剥离 JNI 的 AAR
val strippedJni = Attribute.of("stripped-jni", Boolean::class.javaObjectType)

// 注册 Artifact Transform
dependencies {
    attributesSchema {
        attribute(strippedJni)
    }

    // 注册 Transform: 将普通 AAR 转换为剥离 JNI 的 AAR
    registerTransform(StripJniLibsTransform::class) {
        from.attribute(strippedJni, false)
        to.attribute(strippedJni, true)
    }

    // AndroidX核心库
    implementation(libs.androidx.core.ktx)

    // Kotlin协程
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // GPUImage滤镜库（仅Java类，native库被Transform剥离）
    // gpuimage的libyuv-decoder.so未对齐16KB
    // 本模块CMake编译了16KB对齐的替代版本 (src/main/cpp/gpuimage/yuv-decoder.c)
    api(libs.gpuimage) {
        // 请求使用剥离 JNI 的版本
        attributes {
            attribute(strippedJni, true)
        }
    }
}
