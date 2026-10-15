// ============================================================================
//  LyricShareKit —— 歌词图片分享（Compose + Canvas 渲染）
//
//  可直接作为 Android Library 模块拖进 Melodia（或任意 Compose 工程）：
//      settings.gradle.kts:  include(":lyricshare")
//      app/build.gradle.kts: implementation(project(":lyricshare"))
//
//  版本刻意与 Melodia 1.12.1 对齐（Kotlin 2.2.10 / Compose BOM 2026.02.01 /
//  compileSdk 36 / minSdk 26 / zxing 3.5.3），避免依赖冲突。
//  最小依赖原则：除 Compose 与 ZXing 外不引入任何第三方库。
// ============================================================================
plugins {
    id("com.android.library")
    // AGP 9 自带 Kotlin 编译支持，不需要（也不能）再显式应用 org.jetbrains.kotlin.android
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.music.lyricshare"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Compose：版本交给宿主的 BOM 统一管理
    val composeBom = platform("androidx.compose:compose-bom:2026.02.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.activity:activity-compose:1.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // 二维码：Melodia 已依赖 zxing.core，这里保持同版本
    implementation("com.google.zxing:core:3.5.3")
}
