import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.huijui.su.next"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.huijui.su.next"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "vNext-Edition-${System.currentTimeMillis()}"
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
    // 🚀 降级到稳定兼容 compileSdk 36 的 BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    
    // 降级 activity 到稳定版
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    
    // 🚀 降级 Haze 到 1.x 稳定版（1.2.2 兼容 compileSdk 36）
    implementation("dev.chrisbanes.haze:haze:1.2.2")
    implementation("dev.chrisbanes.haze:haze-materials:1.2.2")
    
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("io.coil-kt:coil-gif:2.6.0")

    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
}
