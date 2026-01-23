// FilterCamera - 实时滤镜相机应用
// 模块配置文件 (Kotlin DSL)

pluginManagement {
    repositories {
        // Google 优先（KSP等插件）
        google()
        gradlePluginPortal()
        mavenCentral()
        // 阿里云镜像备用
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 阿里云镜像加速
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        google()
        mavenCentral()
        // JitPack（用于 RenderScript Toolkit 等 GitHub 项目）
        maven { url = uri("https://jitpack.io") }
    }
}

// 项目名称
rootProject.name = "FilterCamera"

// 包含的模块
include(":app")
include(":core:filter")
