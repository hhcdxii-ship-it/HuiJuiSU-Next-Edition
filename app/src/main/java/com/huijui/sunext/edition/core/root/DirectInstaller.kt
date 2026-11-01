package com.huijui.sunext.edition.core.root

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.util.concurrent.TimeUnit

object DirectInstaller {
    sealed class InstallState {
        data class Progress(val message: String) : InstallState()
        data class Success(val message: String) : InstallState()
        data class Error(val errorMsg: String) : InstallState()
    }

    private suspend fun suExec(cmd: String, timeoutMs: Long = 60000L): String = kotlinx.coroutines.withContext(Dispatchers.IO) {
        try {
            val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
            val out = StringBuilder()
            val reader = Thread { p.inputStream.bufferedReader().forEachLine { out.appendLine(it) } }
            reader.start()
            if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                p.destroyForcibly(); return@withContext "TIMEOUT"
            }
            reader.join(1000)
            out.toString()
        } catch (e: Exception) { return@withContext "ERROR: ${e.message}" }
    }

    fun install(context: Context, localImgUri: android.net.Uri? = null): Flow<InstallState> = flow {
        try {
            emit(InstallState.Progress("[-] 正在检查 Root 环境..."))
            if (!RootEngineVerifier.checkStatus().isWorking) {
                emit(InstallState.Error("❌ 未检测到 Root 环境！"))
                return@flow
            }

            val slotRes = suExec("getprop ro.boot.slot_suffix").trim()
            val isA14Plus = android.os.Build.VERSION.SDK_INT >= 34
            
            // 双分区设备路径
            val bootDevice = "/dev/block/by-name/boot$slotRes"
            val initBootDevice = "/dev/block/by-name/init_boot$slotRes"

            val workDir = File(context.cacheDir, "direct_install").apply { mkdirs() }
            val stockBoot = File(workDir, "stock_boot.img")
            val stockInitBoot = File(workDir, "stock_init_boot.img")

            emit(InstallState.Progress("[-] 正在提取原厂 boot 与 init_boot (双路备份)..."))
            suExec("dd if=$bootDevice of=${stockBoot.absolutePath} bs=1M > /dev/null 2>&1")
            
            var targetDevice = bootDevice
            var targetImg = stockBoot
            
            // 如果有 init_boot，就用 init_boot
            if (!suExec("ls $initBootDevice").contains("No such file")) {
                suExec("dd if=$initBootDevice of=${stockInitBoot.absolutePath} bs=1M > /dev/null 2>&1")
                targetDevice = initBootDevice
                targetImg = stockInitBoot
            }

            if (!targetImg.exists() || targetImg.length() < 1024) {
                emit(InstallState.Error("❌ 提取镜像失败！请检查 Root 权限。"))
                return@flow
            }

            val realKernel = suExec("uname -r").trim()
            val gkiInfo = KernelVersionMatcher.matchKmi(realKernel, "arm64")
            if (gkiInfo == null) {
                emit(InstallState.Error("❌ 无法识别内核版本 ($realKernel)！"))
                return@flow
            }
            val exactKoName = "HuiJuiPhantom_${gkiInfo.branch}_${gkiInfo.arch}.ko"

            doIndependentPatchAndFlash(context, stockBoot.absolutePath, stockInitBoot.absolutePath, bootDevice, targetDevice, exactKoName).collect { emit(it) }

        } catch (e: Throwable) {
            emit(InstallState.Error("❌ 执行异常: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    private fun doIndependentPatchAndFlash(
        context: Context, stockBootPath: String, stockInitBootPath: String, 
        bootDevice: String, targetDevice: String, targetKoName: String
    ): Flow<InstallState> = flow {
        try {
            val workDir = File(context.cacheDir, "direct_install").apply { mkdirs() }
            
            val backupDir = "/sdcard/Download/HuiJuiSU_Backup"
            val slot = suExec("getprop ro.boot.slot_suffix").trim()
            val time = System.currentTimeMillis()
            suExec("mkdir -p $backupDir && cp '$stockBootPath' '$backupDir/boot_${time}$slot.img'")
            suExec("cp '$stockInitBootPath' '$backupDir/init_boot_${time}$slot.img'")

            val remoteDir = "/data/local/tmp/huijui_direct"
            val hsudBin = File(workDir, "hsud_cli")
            val huijuiinit = File(workDir, "huijuiinit")
            val huijuisud = File(workDir, "huijuisud")
            val koFile = File(workDir, "huijui.ko")

            context.assets.open("exploit/hsud_cli").use { it.copyTo(hsudBin.outputStream()) }
            context.assets.open("exploit/huijuiinit").use { it.copyTo(huijuiinit.outputStream()) }
            context.assets.open("exploit/huijuisud").use { it.copyTo(huijuisud.outputStream()) }
            context.assets.open("exploit/$targetKoName").use { it.copyTo(koFile.outputStream()) }

            suExec("rm -rf $remoteDir && mkdir -p $remoteDir && chmod 777 $remoteDir")
            suExec("cp '$stockBootPath' $remoteDir/stock_boot.img")
            suExec("cp '$stockInitBootPath' $remoteDir/stock_init_boot.img")
            suExec("cp '${hsudBin.absolutePath}' $remoteDir/hsud_cli")
            suExec("cp '${huijuiinit.absolutePath}' $remoteDir/huijuiinit")
            suExec("cp '${huijuisud.absolutePath}' $remoteDir/huijuisud")
            suExec("cp '${koFile.absolutePath}' $remoteDir/huijui.ko")
            suExec("chmod 755 $remoteDir/hsud_cli $remoteDir/huijuiinit $remoteDir/huijuisud")

            // 纯血欺骗脚本
            val rcContent = "on post-fs-data\n" +
                    "    cp /huijui.ko /dev/huijui.ko\n" +
                    "    chmod 0644 /dev/huijui.ko\n" +
                    "    insmod /dev/huijui.ko\n" +
                    "    chmod 0666 /dev/huijui\n" +
                    "    chcon u:object_r:device:s0 /dev/huijui\n" +
                    "    chmod 0755 /huijuisud\n" +
                    "    start huijui_daemon\n\n" +
                    "service huijui_daemon /huijuisud\n" +
                    "    class late_start\n" +
                    "    user root\n" +
                    "    group root\n" +
                    "    seclabel u:r:su:s0\n"
            val rcFile = File(workDir, "huijui.rc")
            rcFile.writeText(rcContent)
            suExec("cp '${rcFile.absolutePath}' $remoteDir/huijui.rc")

            emit(InstallState.Progress("[-] 正在执行双路底层注入 (纯血欺骗)..."))
            
            // 判断修补目标
            val isInitBoot = targetDevice.contains("init_boot")
            val targetImageName = if (isInitBoot) "stock_init_boot.img" else "stock_boot.img"

            val scriptLines = listOf(
                "#!/system/bin/sh",
                "cd $remoteDir",
                "./hsud_cli unpack $targetImageName",
                
                // fstab 欺骗
                "./hsud_cli cpio ramdisk.cpio extract fstab.qcom 2>/dev/null || ./hsud_cli cpio ramdisk.cpio extract fstab.mt6899 2>/dev/null || ./hsud_cli cpio ramdisk.cpio extract fstab.mt6989 2>/dev/null",
                "if [ -f fstab.qcom ]; then sed -i 's/,avb=vbmeta_system//g; s/,avb_keys=[^ ]*//g; s/,verify//g; s/forceencrypt/encryptable/g' fstab.qcom; ./hsud_cli cpio ramdisk.cpio add 0750 fstab.qcom fstab.qcom; fi",
                "if [ -f fstab.mt6899 ]; then sed -i 's/,avb=vbmeta_system//g; s/,avb_keys=[^ ]*//g; s/,verify//g; s/forceencrypt/encryptable/g' fstab.mt6899; ./hsud_cli cpio ramdisk.cpio add 0750 fstab.mt6899 fstab.mt6899; fi",
                "if [ -f fstab.mt6989 ]; then sed -i 's/,avb=vbmeta_system//g; s/,avb_keys=[^ ]*//g; s/,verify//g; s/forceencrypt/encryptable/g' fstab.mt6989; ./hsud_cli cpio ramdisk.cpio add 0750 fstab.mt6989 fstab.mt6989; fi",
                
                // 注入 init 接管逻辑
                "./hsud_cli cpio ramdisk.cpio extract init.rc",
                "echo '\\nimport /huijui.rc' >> init.rc",
                "./hsud_cli cpio ramdisk.cpio add 0750 init.rc init.rc",
                "./hsud_cli cpio ramdisk.cpio mv init init.real",
                "./hsud_cli cpio ramdisk.cpio add 0755 init huijuiinit",
                "./hsud_cli cpio ramdisk.cpio add 0755 huijuisud huijuisud",
                "./hsud_cli cpio ramdisk.cpio add 0644 huijui.ko huijui.ko",
                "./hsud_cli cpio ramdisk.cpio add 0644 huijui.rc huijui.rc",
                
                // 重打包
                "./hsud_cli repack $targetImageName patched.img",
                "echo DONE > status.txt"
            )
            val scriptFile = File(workDir, "direct_patch.sh")
            scriptFile.writeText(scriptLines.joinToString("\n"))

            suExec("cp '${scriptFile.absolutePath}' $remoteDir/direct_patch.sh")
            suExec("chmod 755 $remoteDir/direct_patch.sh && sh $remoteDir/direct_patch.sh > /dev/null 2>&1")

            if (!suExec("cat $remoteDir/status.txt").contains("DONE")) {
                emit(InstallState.Error("❌ 引擎修补失败！"))
                return@flow
            }

            // 容量校验
            val partSize = (suExec("blockdev --getsize64 $targetDevice").trim().toLongOrNull() ?: 0L)
            val patchSize = (suExec("stat -c %s $remoteDir/patched.img").trim().toLongOrNull() ?: 0L)

            if (patchSize <= 0L || partSize <= 0L || patchSize > partSize) {
                emit(InstallState.Error("❌ 容量异常，终止防砖。"))
                return@flow
            }

            emit(InstallState.Progress("[-] 正在双路写入底层分区..."))
            // 成对写入
            suExec("dd if=$remoteDir/stock_boot.img of=$bootDevice bs=4096 > /dev/null 2>&1")
            if (targetDevice.contains("init_boot")) {
                suExec("dd if=$remoteDir/patched.img of=$targetDevice bs=4096 > /dev/null 2>&1")
            } else {
                suExec("dd if=$remoteDir/patched.img of=$bootDevice bs=4096 > /dev/null 2>&1")
            }

            suExec("rm -rf $remoteDir")
            emit(InstallState.Success("✅ 纯血双路注入成功！\n请重启手机，看到『工作中（HUI）』即代表成功！"))

        } catch (e: Throwable) {
            emit(InstallState.Error("❌ 闭环异常: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)
}
