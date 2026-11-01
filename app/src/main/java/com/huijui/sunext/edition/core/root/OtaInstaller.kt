package com.huijui.sunext.edition.core.root

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.util.concurrent.TimeUnit

object OtaInstaller {

    sealed class OtaState {
        data class Progress(val message: String) : OtaState()
        data class Success(val message: String) : OtaState()
        data class Error(val errorMsg: String) : OtaState()
    }

    private suspend fun suExec(cmd: String, timeoutMs: Long = 60000L): String = kotlinx.coroutines.withContext(Dispatchers.IO) {
        try {
            val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
            val out = StringBuilder()
            val reader = Thread { p.inputStream.bufferedReader().forEachLine { out.appendLine(it) } }
            reader.start()
            
            val finished = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) { p.destroyForcibly(); return@withContext "TIMEOUT" }
            reader.join(1000)
            out.toString()
        } catch (e: Exception) { "ERROR: ${e.message}" }
    }

    fun install(context: Context, localImgUri: android.net.Uri? = null): Flow<OtaState> = flow {
        try {
            emit(OtaState.Progress("[-] 正在探测 OTA 槽位..."))
            
            val currentSlot = suExec("getprop ro.boot.slot_suffix").trim()
            if (currentSlot.isBlank()) {
                emit(OtaState.Error("❌ 当前设备不是 A/B 分区架构，无法使用 OTA 槽位更新！"))
                return@flow
            }

            // 精准推导：当前是 _a，目标就是 _b；当前是 _b，目标就是 _a
            val targetSlot = if (currentSlot == "_a") "_b" else "_a"
            emit(OtaState.Progress("[-] 当前槽位: $currentSlot，目标 OTA 槽位: $targetSlot"))

            // 探测目标分区
            var targetPartition = "init_boot"
            var blockDevice = "/dev/block/by-name/init_boot$targetSlot"
            if (suExec("ls $blockDevice").contains("No such file")) {
                targetPartition = "boot"
                blockDevice = "/dev/block/by-name/boot$targetSlot"
                if (suExec("ls $blockDevice").contains("No such file")) {
                    emit(OtaState.Error("❌ 无法找到目标分区 $targetPartition$targetSlot，设备不匹配！"))
                    return@flow
                }
            }

            val workDir = File(context.cacheDir, "ota_install").apply { mkdirs() }
            val stockImg = File(workDir, "stock_ota_${targetPartition}.img")
            
            if (localImgUri != null) {
                emit(OtaState.Progress("[-] 正在读取本地镜像..."))
                try {
                    context.contentResolver.openInputStream(localImgUri)?.use { input ->
                        stockImg.outputStream().use { output -> input.copyTo(output) }
                    }
                } catch (e: Exception) {
                    emit(OtaState.Error("❌ 读取本地镜像失败: ${e.message}"))
                    return@flow
                }
            } else {
                emit(OtaState.Progress("[-] 正在提取目标槽位原厂镜像..."))
                suExec("dd if=$blockDevice of=${stockImg.absolutePath} bs=1M > /dev/null 2>&1")
                if (!stockImg.exists() || stockImg.length() < 1024) {
                    emit(OtaState.Error("❌ 提取目标分区镜像失败！"))
                    return@flow
                }
            }
            
            emit(OtaState.Progress("✅ 提取成功: ${stockImg.length() / 1024 / 1024} MB"))
            
            // 精准匹配内核
            val realKernel = suExec("uname -r").trim()
            val gkiInfo = KernelVersionMatcher.matchKmi(realKernel, "arm64")
            if (gkiInfo == null) {
                emit(OtaState.Error("❌ 无法识别内核版本 ($realKernel)！"))
                return@flow
            }
            val exactKoName = "HuiJuiPhantom_${gkiInfo.branch}_${gkiInfo.arch}.ko"
            emit(OtaState.Progress("[-] 匹配内核模块: $exactKoName"))

            executeOtaPatchAndFlash(context, stockImg.absolutePath, blockDevice, targetSlot, exactKoName).collect { emit(it) }
            
        } catch (e: Throwable) {
            emit(OtaState.Error("❌ 执行异常: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    private fun executeOtaPatchAndFlash(
        context: Context, 
        stockImgPath: String, 
        blockDevice: String,
        targetSlot: String,
        targetKoName: String
    ): Flow<OtaState> = flow {
        try {
            emit(OtaState.Progress("[-] 正在唤醒 OTA 专属引擎..."))
            
            val workDir = File(context.cacheDir, "ota_install").apply { mkdirs() }
            val backupDir = "/sdcard/Download/HuiJuiSU_Backup"
            val backupName = "ota_backup_${System.currentTimeMillis()}$targetSlot.img"
            
            // 🚀 备份
            emit(OtaState.Progress("[-] 正在备份目标槽位镜像..."))
            suExec("mkdir -p $backupDir")
            suExec("cp '$stockImgPath' '$backupDir/$backupName'")
            emit(OtaState.Progress("✅ 已备份至: $backupDir/$backupName"))

            val remoteDir = "/data/local/tmp/huijui_ota"
            
            // 提取工具
            val hsudBin = File(workDir, "hsud_cli")
            val huijuiinit = File(workDir, "huijuiinit")
            val huijuisud = File(workDir, "huijuisud")
            val koFile = File(workDir, "huijui.ko")
            
            context.assets.open("exploit/hsud_cli").use { it.copyTo(hsudBin.outputStream()) }
            context.assets.open("exploit/huijuiinit").use { it.copyTo(huijuiinit.outputStream()) }
            context.assets.open("exploit/huijuisud").use { it.copyTo(huijuisud.outputStream()) }
            context.assets.open("exploit/$targetKoName").use { it.copyTo(koFile.outputStream()) }

            // 推送到独立目录
            suExec("rm -rf $remoteDir && mkdir -p $remoteDir && chmod 777 $remoteDir")
            suExec("cp '$stockImgPath' $remoteDir/stock.img")
            suExec("cp '${hsudBin.absolutePath}' $remoteDir/hsud_cli")
            suExec("cp '${huijuiinit.absolutePath}' $remoteDir/huijuiinit")
            suExec("cp '${huijuisud.absolutePath}' $remoteDir/huijuisud")
            suExec("cp '${koFile.absolutePath}' $remoteDir/huijui.ko")
            suExec("chmod 755 $remoteDir/hsud_cli $remoteDir/huijuiinit $remoteDir/huijuisud")

            // 独立脚本
            emit(OtaState.Progress("[-] 正在执行底层注入..."))
            val scriptLines = listOf(
                "#!/system/bin/sh",
                "cd $remoteDir",
                "./hsud_cli unpack stock.img",
                "./hsud_cli cpio ramdisk.cpio mv init init.real add 0755 init huijuiinit add 0755 huijuisud huijuisud add 0644 huijui.ko huijui.ko",
                "./hsud_cli repack stock.img patched.img",
                "echo DONE > status.txt"
            )
            File(workDir, "ota_patch.sh").writeText(scriptLines.joinToString("\n"))
            suExec("cp '${File(workDir, "ota_patch.sh").absolutePath}' $remoteDir/ota_patch.sh")
            suExec("chmod 755 $remoteDir/ota_patch.sh")
            suExec("sh $remoteDir/ota_patch.sh > /dev/null 2>&1")
            
            if (!suExec("cat $remoteDir/status.txt").contains("DONE")) {
                emit(OtaState.Error("❌ OTA 引擎修补失败！"))
                return@flow
            }

            // 🚀 物理容量校验
            emit(OtaState.Progress("[-] 正在校验目标分区容量..."))
            val partSize = suExec("blockdev --getsize64 $blockDevice").trim().toLongOrNull() ?: 0L
            val patchSize = suExec("stat -c %s $remoteDir/patched.img").trim().toLongOrNull() ?: 0L
            
            if (patchSize <= 0L || patchSize > partSize) {
                emit(OtaState.Error("❌ 容量校验失败！镜像 ($patchSize) 超出分区 ($partSize)，拒绝写入。"))
                return@flow
            }

            // 写入底层
            emit(OtaState.Progress("[-] 正在写入 $blockDevice..."))
            suExec("dd if=$remoteDir/patched.img of=$blockDevice bs=4096 > /dev/null 2>&1")

            // 回读校验
            emit(OtaState.Progress("[-] 正在回读校验..."))
            val verifyHash = suExec("dd if=$blockDevice bs=4096 count=1 2>/dev/null | md5sum").trim()
            val sourceHash = suExec("dd if=$remoteDir/patched.img bs=4096 count=1 2>/dev/null | md5sum").trim()

            if (verifyHash.isBlank() || verifyHash != sourceHash) {
                emit(OtaState.Error("❌ 写入校验失败！"))
                return@flow
            }

            // 🚀 处理目标槽位的 vbmeta
            val vbmetaDevice = "/dev/block/by-name/vbmeta$targetSlot"
            if (suExec("ls $vbmetaDevice").contains("No such file") == false) {
                emit(OtaState.Progress("[-] 正在处理目标槽位 vbmeta 校验..."))
                val emptyVbmeta = File(workDir, "empty_ota_vbmeta.img")
                emptyVbmeta.writeBytes(ByteArray(4096))
                suExec("cp '${emptyVbmeta.absolutePath}' /data/local/tmp/empty_ota_vbmeta.img")
                suExec("dd if=/data/local/tmp/empty_ota_vbmeta.img of=$vbmetaDevice bs=4096 > /dev/null 2>&1")
                suExec("rm -f /data/local/tmp/empty_ota_vbmeta.img")
            }

            suExec("rm -rf $remoteDir")
            emit(OtaState.Success("✅ OTA 槽位更新成功！\\n\\n系统重启后将自动切换到 $targetSlot 槽位，并加载 HUI 引擎。\\n\\n🛡️ 备份位于: $backupDir"))

        } catch (e: Throwable) {
            emit(OtaState.Error("❌ OTA 闭环异常: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)
}
