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
    // 核心 Compose 基础 (不绑定 Material3，自由度高)
    implementation("androidx.compose.foundation:foundation:1.6.3")
    implementation("androidx.compose.ui:ui:1.6.3")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.3")
    implementation("androidx.activity:activity-compose:1.8.2")
    
    // ViewModel & 协程
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // 动态壁纸引擎
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")

    // Root 核心引擎 (libsu)
    implementation("com.github.topjohnwu.libsu:core:5.2.2")
}
