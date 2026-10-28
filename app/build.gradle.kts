plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.huijui.su.next"
    compileSdk = 36 // 🚀 满足 activity 1.11.0 与 SDK 36 的强制要求

    defaultConfig {
        applicationId = "com.huijui.su.next"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "vNext-Edition"
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
        // 与 Kotlin 2.2.20 对齐的 Compose 编译器版本
        kotlinCompilerExtensionVersion = "1.5.15"
    }
}

dependencies {
    // 🚀 升级 Compose BOM 以匹配 Haze 1.7.0 所需的 Compose 1.9.4[reference:2]
    val composeBom = platform("androidx.compose:compose-bom:2025.12.01")
    implementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.navigation:navigation-compose:2.8.0")

    // 视频引擎
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    // 🚀 液态玻璃库
    implementation("dev.chrisbanes.haze:haze:1.7.0")
}
