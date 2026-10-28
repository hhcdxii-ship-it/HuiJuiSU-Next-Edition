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
    // 🚀 1. Compose BOM (统一版本管理，解决版本冲突的终极方案)
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)

    // 🚀 2. 核心 Compose 基础
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3") // 官方MD3组件
    implementation("androidx.activity:activity-compose:1.8.2")

    // 🚀 3. 导航与动画 (BOM 会自动管理动画库版本)
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // 🚀 4. ViewModel & 协程
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // 🚀 5. 动态壁纸引擎 (Media3 ExoPlayer 稳定版 1.3.1)
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")

    // 🚀 6. 图片加载 (Coil 稳定版 2.6.0，支持静态图和 GIF)
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("io.coil-kt:coil-gif:2.6.0")

    // 🚀 7. 图标库 (extended 版才包含 TwoTone 等图标)
    implementation("androidx.compose.material:material-icons-extended")
    // 🚀 注入 Haze 毛玻璃引擎
    implementation("dev.chrisbanes.haze:haze:1.2.2")
}
