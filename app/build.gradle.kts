import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    ndkVersion = "26.1.10909125"
    namespace = "com.huijui.sunext.edition"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.huijui.sunext.edition"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "v2"
        
        // 🚀 核心改动：定义编译出来的 APK 基础文件名
        setProperty("archivesBaseName", "HuiJuiSU-Next-Edition-$versionName")
    }

    // 动态配置签名
    signingConfigs {
        create("release") {
            val keyStoreFile = file(System.getenv("KEYSTORE_FILE") ?: "huijui_release.jks")
            if (keyStoreFile.exists()) {
                storeFile = keyStoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: ""
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    androidResources {
        noCompress += "mp4"
    }
    externalNativeBuild {
        cmake {
            path = file("../core/root/native/CMakeLists.txt")
        }
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    implementation("dev.chrisbanes.haze:haze:1.2.2")
    implementation("dev.chrisbanes.haze:haze-materials:1.2.2")

    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("io.coil-kt:coil-gif:2.6.0")

    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    // 折叠屏适配
    implementation("androidx.compose.material3:material3-window-size-class:1.3.1")
    implementation("androidx.compose.material3.adaptive:adaptive:1.2.0")
    implementation("androidx.window:window:1.3.0")
}
