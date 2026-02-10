// FilterCamera - 主应用模块构建配置 (Kotlin DSL)
// 实时滤镜相机应用

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.qihao.filtercamera"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.qihao.filtercamera"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // 处理native库冲突
        // gpuimage预编译的libyuv-decoder.so未对齐16KB
        // core:filter模块CMake编译了16KB对齐的替代版本
        // 使用pickFirsts让项目构建的版本优先
        jniLibs {
            pickFirsts += "**/libyuv-decoder.so"
        }
    }

    // Lint配置 - 处理实验性API警告
    lint {
        baseline = file("lint-baseline.xml")
        abortOnError = false                              // 不因警告中断构建
        warningsAsErrors = false                          // 警告不视为错误
        checkReleaseBuilds = true                         // 检查Release构建
        disable += setOf(
            "UnsafeOptInUsageError"                       // Camera2实验性API
        )
    }
}

dependencies {
    // 项目模块
    implementation(project(":core:filter"))

    // AndroidX核心库
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Jetpack Compose
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)

    // Compose调试工具
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // CameraX
    implementation(libs.bundles.camerax)

    // Hilt依赖注入A
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Kotlin协程
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Accompanist权限
    implementation(libs.accompanist.permissions)

    // GPUImage滤镜库 - 通过core:filter模块传递依赖
    // core:filter模块已包含16KB对齐的native库
    // 不需要在app中重复声明

    // ML Kit人脸检测（人像模式）
    implementation(libs.mlkit.face.detection)

    // ML Kit人物分割（人像虚化）
    implementation(libs.mlkit.selfie.segmentation)

    // ML Kit文档扫描器（文档模式高级功能）
    implementation(libs.mlkit.document.scanner)

    // DataStore持久化（设置存储）
    implementation(libs.androidx.datastore.preferences)

    // Coil图片加载（相册）
    implementation(libs.coil.compose)

    // RenderScript Toolkit (高斯模糊 - 替代已弃用的RenderScript)
    implementation(libs.renderscript.toolkit)

    // 测试
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
}
