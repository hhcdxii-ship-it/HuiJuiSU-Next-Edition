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
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    // 🚀 1. 官方 BOM 统一控版本，彻底消灭冲突
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)

    // 2. 核心 Compose 库
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.8.2")

    // 3. 官方导航
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // 4. 官方视频引擎 (ExoPlayer)
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")

    // 5. 专业图片加载 (Coil)
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("io.coil-kt:coil-gif:2.6.0")

    // 6. 专业液态玻璃库 (Haze 0.7.3 兼容 Kotlin 1.9)
    implementation("dev.chrisbanes.haze:haze:0.7.3")
}
