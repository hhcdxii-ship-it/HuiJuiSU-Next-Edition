package com.huijui.sunext.edition.core.root

import android.net.Uri
import android.os.Build
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object HsudInstaller {

    suspend fun installLkm(
        context: Context,
        installToInactiveSlot: Boolean,
        localImgUri: Uri? = null,
        onLog: (String) -> Unit,
        customKoUri: android.net.Uri? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            onLog("> [初始化] 正在检测底层环境...")
            
            // 1. 获取环境信息
            val kernelVer = getKernelVersion()
            val androidVer = Build.VERSION.RELEASE.toIntOrNull() ?: 0
            val arch = getArchitecture()
            
            val (mainVer, subVer) = parseKernelVersion(kernelVer)
            if (mainVer < 5 || (mainVer == 5 && subVer < 10)) {
                onLog("❌ 致命拦截：非 GKI 老内核 (${mainVer}.${subVer})，拒绝操作。")
                return@withContext false
            }

            val isAndroid14Plus = androidVer >= 14
            val daemonName = if (isAndroid14Plus) "hsud" else "hud"
            val targetPartitionBase = if (isAndroid14Plus) "init_boot" else "boot"
            
            onLog("> [环境] Android $androidVer | 内核 $kernelVer | 架构 $arch")
            onLog("> [引擎] 激活纯血 $daemonName 引擎，接管 $targetPartitionBase")

            // 🚀 工业级防护：电量检测
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            val batteryLevel = batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (batteryLevel in 0..20) {
                onLog("❌ 致命拦截：当前电量 $batteryLevel% 过低，刷机途中断电会导致设备变砖，请充电至 30% 以上。")
                return@withContext false
            }
            onLog("> [环境] 当前电量 $batteryLevel%，满足刷机安全要求。")

            // 2. A/B 槽位判定
            var targetPartition = targetPartitionBase
            val slotSuffix = getSlotSuffix()
            if (slotSuffix.isNotEmpty() && localImgUri == null) {
                if (installToInactiveSlot) {
                    val inactiveSlot = if (slotSuffix == "_a") "_b" else "_a"
                    targetPartition += inactiveSlot
                    onLog("> [目标] A/B 设备，注入未使用槽位: $targetPartition")
                } else {
                    targetPartition += slotSuffix
                    onLog("> [目标] A/B 设备，注入当前活动槽位: $targetPartition")
                }
            } else if (localImgUri == null) {
                onLog("> [目标] 非 A/B 设备，注入分区: $targetPartition")
            }

            // 3. 模块匹配与工具解压
            val workDir = File(context.cacheDir, "huijui_patch")
            if (workDir.exists()) workDir.deleteRecursively()
            workDir.mkdirs()

            val cacheKo: File
            if (customKoUri != null) {
                // 🚀 使用用户手动选择的 .ko
                onLog("> [模块] 正在读取用户提供的自定义内核模块...")
                cacheKo = File(workDir, "custom_huijui.ko")
                if (!copyUriToFile(context, customKoUri, cacheKo)) {
                    onLog("❌ 错误：无法读取自定义模块文件。")
                    return@withContext false
                }
            } else {
                // 🚀 使用 APK 内置的 .ko
                val gkiBranch = matchGkiBranch(mainVer, subVer)
                val koName = "HuiJuiPhantom_${gkiBranch}_${arch}.ko"
                onLog("> [模块] 匹配内置纯血内核: $koName")
                cacheKo = File(workDir, koName)
                if (!extractAsset(context, "exploit/$koName", cacheKo)) {
                    onLog("❌ 错误：APK 中缺失 $koName，请尝试手动选择本地模块。")
                    return@withContext false
                }
            }

            val cacheDaemon = File(context.applicationInfo.nativeLibraryDir, "lib${daemonName}.so")

            // 4. 准备原始镜像 (核心：加入原厂备份机制)
            val targetImgFile = File(workDir, "stock_boot.img")
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val backupDir = File("/sdcard/Download/HuiJui_Backup")
            val backupFilePath = File(backupDir, "原厂_${targetPartition}_$timeStamp.img")

            if (localImgUri != null) {
                onLog("> [读取] 正在读取用户提供的本地镜像...")
                if (!copyUriToFile(context, localImgUri, targetImgFile)) {
                    onLog("❌ 错误：无法读取本地镜像文件。")
                    return@withContext false
                }
            } else {
                onLog("> [提取] 正在提取底层分区 $targetPartition ...")
                // 提取原厂镜像
                val ddExtractCmd = "dd if=/dev/block/by-name/$targetPartition of=${targetImgFile.absolutePath}"
                if (!runShellCommandSync(ddExtractCmd)) {
                    onLog("❌ 错误：提取分区失败，请检查 Root 权限。")
                    return@withContext false
                }
                
                // 备份原厂镜像到 Download 目录
                onLog("> [防砖] 正在将原厂镜像备份到 Download/HuiJui_Backup ...")
                backupDir.mkdirs()
                runShellCommandSync("cp ${targetImgFile.absolutePath} ${backupFilePath.absolutePath}")
                onLog("> [防砖] 备份成功: ${backupFilePath.name}")
            }

            // 5. 流式执行修补流程
            onLog("> [修补] 开始执行镜像解包与重打包...")
            
            var scriptCmd = "set -e\ncd ${workDir.absolutePath}\n"
            scriptCmd += "${cacheDaemon.absolutePath} unpack stock_boot.img\n"

            // 提取纯血守护进程
            val cacheSud = File(context.applicationInfo.nativeLibraryDir, "libhuijuisud.so")

            scriptCmd += "mkdir -p huijui_files\n"
            scriptCmd += "cp ${cacheKo.absolutePath} huijui_files/huijui_phantom.ko\n"
            scriptCmd += "cp ${cacheSud.absolutePath} huijui_files/huijuisud\n"

            // 🚀 合体大招：双路智能分流
            if (isAndroid14Plus) {
                // ==========================================
                // 路线 A：Android 14+ (GKI 架构，纯血内核劫持)
                // ==========================================
                scriptCmd += """
                    # 1. 塞入底层内核模块和守护进程
                    ${cacheDaemon.absolutePath} cpio ramdisk.cpio "mkdir 0755 sbin"
                    ${cacheDaemon.absolutePath} cpio ramdisk.cpio "add 0644 sbin/huijui_phantom.ko huijui_files/huijui_phantom.ko"
                    ${cacheDaemon.absolutePath} cpio ramdisk.cpio "add 0755 sbin/huijuisud huijui_files/huijuisud"

                    # 2. 🚀 生成开机启动脚本（真正让模块生效的关键！）
                    echo 'on post-fs-data' > huijui_files/huijui.rc
                    echo '    insmod /sbin/huijui_phantom.ko' >> huijui_files/huijui.rc
                    echo '    exec - root -- /sbin/huijuisud' >> huijui_files/huijui.rc

                    # 3. 将脚本注入到原厂 init 会自动读取的目录
                    ${cacheDaemon.absolutePath} cpio ramdisk.cpio "mkdir 0755 system"
                    ${cacheDaemon.absolutePath} cpio ramdisk.cpio "mkdir 0755 system/etc"
                    ${cacheDaemon.absolutePath} cpio ramdisk.cpio "mkdir 0755 system/etc/init"
                    ${cacheDaemon.absolutePath} cpio ramdisk.cpio "add 0750 system/etc/init/huijui.rc huijui_files/huijui.rc"
                """.trimIndent()
            } else {
                // ==========================================
                // 路线 B：Android 13- (传统架构，脚本注入)
                // ==========================================
                scriptCmd += """
                    # 塞入守护进程 (老机型不依赖 kprobe 内核模块)
                    ${cacheDaemon.absolutePath} cpio ramdisk.cpio "mkdir 0755 sbin"
                    ${cacheDaemon.absolutePath} cpio ramdisk.cpio "add 0755 sbin/huijuisud huijui_files/huijuisud"
                    
                    # 提取原厂 init.rc 并追加启动脚本
                    ${cacheDaemon.absolutePath} cpio ramdisk.cpio "extract init.rc init.rc.orig" || true
                    if [ -f init.rc.orig ]; then
                        echo 'on post-fs-data' >> init.rc.orig
                        echo '    exec - root -- /sbin/huijuisud' >> init.rc.orig
                        ${cacheDaemon.absolutePath} cpio ramdisk.cpio "add 0750 init.rc init.rc.orig"
                    fi
                    rm -f init.rc.orig
                """.trimIndent()
            }

            scriptCmd += "\n${cacheDaemon.absolutePath} repack stock_boot.img new_boot.img\n"
            
            if (localImgUri != null) {
                scriptCmd += "cp new_boot.img /sdcard/Download/HuiJuiPhantom_patched_${targetPartition}.img\n"
                scriptCmd += "echo 'PATCH_SUCCESS_LOCAL'\n"
            } else {
                onLog("> [写入] 正在写回分区 $targetPartition ...")
                scriptCmd += "dd if=new_boot.img of=/dev/block/by-name/$targetPartition\n"
                scriptCmd += "echo 'PATCH_SUCCESS_LIVE'\n"
            }

            // 🚀 使用流式读取执行命令，实时更新到 UI
            val isSuccess = runShellCommandStreaming(scriptCmd, onLog)

            // 额外兜底：repack 是 void 函数，失败时可能静默返回（退出码仍为 0），
            // 检查输出镜像是否存在且非空，防止误判成功
            if (isSuccess) {
                val newBootFile = File(workDir, "new_boot.img")
                if (!newBootFile.exists() || newBootFile.length() == 0L) {
                    onLog("❌ [失败] repack 未生成有效镜像（文件不存在或为空）")
                    return@withContext false
                }
            }

            if (isSuccess) {
                if (localImgUri != null) {
                    onLog("✅ [成功] 本地镜像修补完成！")
                } else {
                    onLog("✅ [成功] 纯血内核注入完成！请立即重启设备。")
                }
                true
            } else {
                onLog("❌ [失败] 修补进程异常退出，请检查上方日志。")
                false
            }

        } catch (e: Exception) {
            onLog("❌ 系统异常: ${e.message}")
            false
        }
    }

    // --- 核心底层函数 ---

    // 流式执行 su 命令（退出码 + 结构化日志，替代 PATCH_SUCCESS 字符串匹配）
    private fun runShellCommandStreaming(cmd: String, onLog: (String) -> Unit): Boolean {
        return try {
            // 🚀 智能判断：本地修补无需 Root，直接执行；提取/刷入分区才需要 su
            val commandPrefix = if (cmd.contains("unpack") || cmd.contains("repack") || cmd.contains("ramdisk.cpio")) {
                arrayOf("sh", "-c", cmd)
            } else {
                arrayOf("su", "-c", cmd)
            }
            val process = ProcessBuilder(*commandPrefix).redirectErrorStream(true).start()
            val reader = process.inputStream.bufferedReader()
            var line: String?
            var hadError = false

            while (reader.readLine().also { line = it } != null) {
                val text = line ?: continue
                if (text.contains("[HSUD:error]") || text.contains("result=err")) {
                    hadError = true
                }
                if (text.isNotBlank() && !text.startsWith("Unpacking") && !text.startsWith("Repacking")) {
                    onLog("  [hsud] $text")
                }
            }

            val exit = process.waitFor()
            if (exit != 0) {
                onLog("  [hsud] ⚠️ 引擎退出码 $exit：${hsudErrorText(exit)}")
                return false
            }
            if (hadError) {
                onLog("  [hsud] ⚠️ 引擎报告错误但退出码为 0，判定失败")
                return false
            }
            true
        } catch (e: Exception) {
            onLog("  [hsud] ⚠️ 执行异常: ${e.message}")
            false
        }
    }

    // 错误码映射（与 hsud 侧 HSUD_E_* 对齐，后续 hsud 侧细化时同步更新）
    private fun hsudErrorText(code: Int): String = when (code) {
        1 -> "镜像格式无法识别"
        2 -> "boot header 字段非法"
        3 -> "压缩/解压失败"
        4 -> "cpio 归档解析失败"
        5 -> "AVB 校验失败"
        6 -> "文件读写失败"
        7 -> "参数错误"
        else -> "未知错误 (exit=$code)"
    }

    private fun runShellCommandGetOutput(cmd: String): String {
        return try {
            // 🚀 智能判断：本地修补无需 Root，直接执行；提取/刷入分区才需要 su
            val commandPrefix = if (cmd.contains("unpack") || cmd.contains("repack") || cmd.contains("ramdisk.cpio")) {
                arrayOf("sh", "-c", cmd)
            } else {
                arrayOf("su", "-c", cmd)
            }
            val process = ProcessBuilder(*commandPrefix).redirectErrorStream(true).start()
            val out = process.inputStream.bufferedReader().readText()
            process.waitFor()
            out
        } catch (e: Exception) { "" }
    }

    private fun runShellCommandSync(cmd: String): Boolean {
        return try {
            val commandPrefix = if (cmd.contains("unpack") || cmd.contains("repack") || cmd.contains("ramdisk.cpio")) arrayOf("sh", "-c", cmd) else arrayOf("su", "-c", cmd)
            val process = ProcessBuilder(*commandPrefix).start()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    // 以下辅助函数保持不变
    private fun getKernelVersion(): String = try { Runtime.getRuntime().exec("uname -r").inputStream.bufferedReader().readText().trim() } catch (e: Exception) { "" }
    private fun getArchitecture(): String { val abis = Build.SUPPORTED_ABIS; return if (abis.contains("arm64-v8a")) "arm64" else "x86_64" }
    private fun parseKernelVersion(version: String): Pair<Int, Int> { val match = Regex("""(\d+)\.(\d+)""").find(version); return Pair(match?.groupValues?.get(1)?.toIntOrNull() ?: 0, match?.groupValues?.get(2)?.toIntOrNull() ?: 0) }
    private fun getSlotSuffix(): String = try { Runtime.getRuntime().exec("getprop ro.boot.slot_suffix").inputStream.bufferedReader().readText().trim() } catch (e: Exception) { "" }
    private fun matchGkiBranch(main: Int, sub: Int): String = when { main >= 6 && sub >= 18 -> "android17-6.18"; main >= 6 && sub >= 12 -> "android16-6.12"; main >= 6 && sub >= 6 -> "android15-6.6"; main >= 6 && sub >= 1 -> "android14-6.1"; main == 5 && sub >= 15 -> "android14-5.15"; main == 5 && sub >= 10 -> "android12-5.10"; else -> "android12-5.10" }
    private fun extractAsset(context: Context, assetPath: String, destFile: File): Boolean = try { context.assets.open(assetPath).use { input -> FileOutputStream(destFile).use { input.copyTo(it) } }; true } catch (e: Exception) { false }
    private fun copyUriToFile(context: Context, uri: Uri, destFile: File): Boolean = try { val input: InputStream? = context.contentResolver.openInputStream(uri); input?.use { FileOutputStream(destFile).use { out -> it.copyTo(out) } }; true } catch (e: Exception) { false }
}
