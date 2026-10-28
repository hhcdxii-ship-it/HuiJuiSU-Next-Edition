plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.huijui.su.next"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.huijui.su.next"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "vNext-Edition-Demo"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    // 🚀 新增：导航与页面动画
    implementation("androidx.navigation:navigation-compose:2.7.7")
    // 🚀 新增：液态/毛玻璃效果 (Haze 稳定版)
    implementation("dev.chrisbanes.haze:haze:0.6.0")
    implementation("dev.chrisbanes.haze:haze-blur:0.6.0")

    // 1. 核心 Compose 基础 (纯血，无 Material3 界面依赖，但保留底层)
    implementation("androidx.compose.foundation:foundation:1.6.3")
    implementation("androidx.compose.ui:ui:1.6.3")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.3")
    implementation("androidx.activity:activity-compose:1.8.2")
    
    // 2. ViewModel & 协程
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // 3. 动态壁纸引擎 (60帧 硬件加速)
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")

    // 4. 图标库 (补齐咱们用到的 TwoTone 图标)
    implementation("androidx.compose.material:material-icons-extended:1.6.3")
}
