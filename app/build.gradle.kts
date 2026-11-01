import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.security.MessageDigest

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

        // App 自己有整套语言系统（HuiStrings），系统资源只用于 AndroidX 组件，
        // 因此不需要把 AndroidX 里几十种语言的字符串全打进包。
        // 只保留简中 / 繁中 / 英文，能省掉一部分体积。
        resourceConfigurations += listOf("zh", "zh-rTW", "en")
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
        noCompress.add("huijuiinit")
        noCompress.add("ko")
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


    implementation("io.coil-kt:coil-compose:2.6.0")

    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    // 折叠屏适配
    implementation("androidx.compose.material3:material3-window-size-class:1.3.1")
    implementation("androidx.compose.material3.adaptive:adaptive:1.2.0")
    implementation("androidx.window:window:1.3.0")
}


// ============================================================================
// 构建期校验内置内核模块
//
// 起因：assets/exploit 曾经被回滚成「7 份同一个 5.10 二进制 + 1 份 6.12」，
// 文件名写着 8 个内核分支，实际只有 2 个是真的。这种回归在运行期极难发现
// （用户只看到"刷入成功但未工作"），所以把它挡在编译阶段。
// ============================================================================
val verifyKoAssets = tasks.register("verifyKoAssets") {
    group = "verification"
    description = "校验 assets/exploit 下的 8 个内核模块是否真实且互不相同"

    val assetDir = layout.projectDirectory.dir("src/main/assets/exploit").asFile

    doLast {
        val expectedBranches = listOf(
            "android12-5.10", "android13-5.10", "android13-5.15", "android14-5.15",
            "android14-6.1", "android15-6.6", "android16-6.12", "android17-6.18",
        )
        val problems = mutableListOf<String>()
        val digests = mutableMapOf<String, String>()

        for (branch in expectedBranches) {
            val name = "HuiJuiPhantom_${branch}_arm64.ko"
            val file = assetDir.resolve(name)
            if (!file.isFile) {
                problems.add("缺少 $name")
                continue
            }
            if (file.length() < 4096L) {
                problems.add("$name 只有 ${file.length()} 字节，明显是占位文件")
            }
            val bytes = file.readBytes()
            if (bytes.size < 4 ||
                bytes[0] != 0x7F.toByte() ||
                bytes[1] != 'E'.code.toByte() ||
                bytes[2] != 'L'.code.toByte() ||
                bytes[3] != 'F'.code.toByte()
            ) {
                problems.add("$name 不是 ELF 文件")
                continue
            }

            val digest = MessageDigest.getInstance("MD5")
                .digest(bytes)
                .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
            digests[name] = digest

            val kernelVersion = branch.substringAfter('-')
            val text = String(bytes, Charsets.ISO_8859_1)
            val marker = "vermagic="
            val at = text.indexOf(marker)
            val vermagic = if (at >= 0) {
                text.substring(at + marker.length).takeWhile { it != '\u0000' && it != '\n' }.trim()
            } else {
                ""
            }
            if (vermagic.isEmpty()) {
                problems.add("$name 里找不到 vermagic")
            } else if (!vermagic.startsWith(kernelVersion)) {
                problems.add("$name 的 vermagic 是「$vermagic」，与文件名要求的内核 $kernelVersion 不符")
            }
        }

        digests.entries.groupBy { it.value }.filterValues { it.size > 1 }.forEach { (_, group) ->
            problems.add("以下模块内容完全相同（同一个二进制换名字）：${group.joinToString { it.key }}")
        }

        if (problems.isNotEmpty()) {
            throw GradleException(
                "内核模块校验失败：\n" + problems.joinToString("\n") { "  · $it" }
            )
        }
        logger.lifecycle("✓ 8 个内核模块校验通过（互不相同 / ELF 合法 / vermagic 与命名一致）")
    }
}

tasks.named("preBuild") { dependsOn(verifyKoAssets) }
