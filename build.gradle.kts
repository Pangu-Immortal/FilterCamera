// FilterCamera - 根级构建配置 (Kotlin DSL)
// 配置所有子模块的公共构建选项

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ksp) apply false
}

// 清理任务
tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
