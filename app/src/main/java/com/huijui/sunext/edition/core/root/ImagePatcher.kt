package com.huijui.sunext.edition.core.root

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

object ImagePatcher {
    sealed class PatchState {
        data class Progress(val message: String) : PatchState()
        data class Success(val outputPath: String, val isInitBoot: Boolean) : PatchState()
        data class Error(val errorMsg: String) : PatchState()
    }

    private const val REMOTE_WORK_DIR = "/data/local/tmp/huijui_patch"
    private const val REMOTE_HSUD_CLI = "$REMOTE_WORK_DIR/hsud_cli"

    fun patch(context: Context, srcImagePath: String, outputImagePath: String): Flow<PatchState> = flow {
        emit(PatchState.Progress("[-] 正在初始化镜像修补引擎..."))
        val localWorkDir = File(context.cacheDir, "patcher_env").apply { mkdirs() }

        try {
            val hsudBin = File(localWorkDir, "hsud_cli")
            if (!hsudBin.exists() || hsudBin.length() < 100000) {
                context.assets.open("exploit/hsud_cli").use { input ->
                    hsudBin.outputStream().use { output -> input.copyTo(output) }
                }
                hsudBin.setExecutable(true)
            }

            val huijuiinit = File(localWorkDir, "huijuiinit")
            if (!huijuiinit.exists()) {
                context.assets.open("exploit/huijuiinit").use { input ->
                    huijuiinit.outputStream().use { output -> input.copyTo(output) }
                }
                huijuiinit.setExecutable(true)
            }

            val huijuisud = File(localWorkDir, "huijuisud")
            if (!huijuisud.exists()) {
                context.assets.open("exploit/huijuisud").use { input ->
                    huijuisud.outputStream().use { output -> input.copyTo(output) }
                }
                huijuisud.setExecutable(true)
            }

            val koName = KernelVersionMatcher.getExpectedKoFileName()
            if (koName == null) {
                emit(PatchState.Error("❌ 无法识别当前设备的 Kernel 版本！"))
                return@flow
            }

            val koFile = File(localWorkDir, koName)
            if (!koFile.exists()) {
                context.assets.open("exploit/$koName").use { input ->
                    koFile.outputStream().use { output -> input.copyTo(output) }
                }
            }

            emit(PatchState.Progress("[-] 底层环境初始化完毕，即将开始校验镜像..."))
            executePatchFlow(context, localWorkDir, hsudBin, huijuiinit, huijuisud, koFile, srcImagePath, outputImagePath).collect { emit(it) }

        } catch (e: Throwable) {
            emit(PatchState.Error("❌ 初始化失败: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun suExec(cmd: String, timeoutMs: Long = 30_000L): String = kotlinx.coroutines.withContext(Dispatchers.IO) {
        try {
            val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            val finished = p.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (!finished) {
                p.destroyForcibly()
                return@withContext "TIMEOUT"
            }
            out
        } catch (e: Exception) {
            return@withContext "ERROR: ${e.message}"
        }
    }

    private fun executePatchFlow(
        context: Context, localWorkDir: File, hsudBin: File, huijuiinit: File,
        huijuisud: File, koFile: File, srcImagePath: String, outputImagePath: String
    ): Flow<PatchState> = flow {
        try {
            emit(PatchState.Progress("[-] 正在准备底层工作区..."))
            suExec("rm -rf $REMOTE_WORK_DIR && mkdir -p $REMOTE_WORK_DIR && chmod 777 $REMOTE_WORK_DIR")
            suExec("cp '${hsudBin.absolutePath}' $REMOTE_HSUD_CLI")
            suExec("cp '${huijuiinit.absolutePath}' $REMOTE_WORK_DIR/huijuiinit")
            suExec("cp '${huijuisud.absolutePath}' $REMOTE_WORK_DIR/huijuisud")
            suExec("cp '${koFile.absolutePath}' $REMOTE_WORK_DIR/huijui.ko")
            suExec("chmod 755 $REMOTE_HSUD_CLI $REMOTE_WORK_DIR/huijuiinit $REMOTE_WORK_DIR/huijuisud")
            suExec("cp \"$srcImagePath\" $REMOTE_WORK_DIR/stock.img")

            emit(PatchState.Progress("[-] 正在检测镜像格式与分区..."))
            val infoRes = suExec("cd $REMOTE_WORK_DIR && ./hsud_cli info stock.img")
            if (!infoRes.contains("HEADER_VER") || infoRes.contains("TIMEOUT") || infoRes.contains("ERROR")) {
                emit(PatchState.Error("❌ 镜像格式非法！底层日志: $infoRes"))
                return@flow
            }

            val isAndroid13Plus = android.os.Build.VERSION.SDK_INT >= 33
            val actualPartition = if (isAndroid13Plus) "init_boot" else "boot"
            emit(PatchState.Progress("[-] 检测通过：目标分区为 $actualPartition"))

            emit(PatchState.Progress("[-] 正在解包镜像内部结构..."))
            suExec("cd $REMOTE_WORK_DIR && rm -rf ramdisk.cpio && ./hsud_cli unpack stock.img")
            if (!suExec("ls $REMOTE_WORK_DIR/ramdisk.cpio").contains("ramdisk.cpio")) {
                emit(PatchState.Error("❌ 解包失败，ramdisk.cpio 不存在！"))
                return@flow
            }

            injectComponents(localWorkDir, REMOTE_WORK_DIR, outputImagePath, actualPartition).collect { emit(it) }

        } catch (e: Throwable) {
            emit(PatchState.Error("❌ 执行异常: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    private fun injectComponents(
        localWorkDir: File, remoteWorkDir: String, outputImagePath: String, partition: String
    ): Flow<PatchState> = flow {
        try {
            emit(PatchState.Progress("[-] 正在向 ramdisk 注入纯血组件..."))
            
            // 🚀 核心修复：把 ko 拷到 /dev 加载，规避只读；强刷 insmod
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

            val localRc = File(localWorkDir, "huijui.rc")
            localRc.writeText(rcContent)
            suExec("cp '${localRc.absolutePath}' $remoteWorkDir/huijui.rc")

            suExec("cd $remoteWorkDir && ./hsud_cli cpio ramdisk.cpio extract init.rc")
            suExec("echo '\nimport /huijui.rc' >> $remoteWorkDir/init.rc")
            suExec("cd $remoteWorkDir && ./hsud_cli cpio ramdisk.cpio add 0750 init.rc init.rc")

            // 拆解 cpio 指令
            suExec("cd $remoteWorkDir && ./hsud_cli cpio ramdisk.cpio mv init init.real")
            suExec("cd $remoteWorkDir && ./hsud_cli cpio ramdisk.cpio add 0755 init huijuiinit")
            suExec("cd $remoteWorkDir && ./hsud_cli cpio ramdisk.cpio add 0755 huijuisud huijuisud")
            suExec("cd $remoteWorkDir && ./hsud_cli cpio ramdisk.cpio add 0644 huijui.ko huijui.ko")
            suExec("cd $remoteWorkDir && ./hsud_cli cpio ramdisk.cpio add 0644 huijui.rc huijui.rc")

            emit(PatchState.Progress("[-] 正在重打包镜像并强制补齐尺寸..."))
            suExec("cd $remoteWorkDir && ./hsud_cli repack stock.img patched.img")

            val origSizeStr = suExec("stat -c %s $remoteWorkDir/stock.img").trim()
            val patchSizeStr = suExec("stat -c %s $remoteWorkDir/patched.img").trim()
            val origSize = origSizeStr.toLongOrNull() ?: 0L
            val patchSize = patchSizeStr.toLongOrNull() ?: 0L

            if (patchSize < origSize) {
                emit(PatchState.Progress("[-] 正在补齐镜像差异..."))
                val diff = origSize - patchSize
                suExec("cd $remoteWorkDir && dd if=/dev/zero bs=1 count=$diff >> patched.img 2>/dev/null")
            }

            emit(PatchState.Progress("[-] 正在执行产物完整性校验..."))
            val verifyInfo = suExec("cd $remoteWorkDir && ./hsud_cli info patched.img")
            if (!verifyInfo.contains("HEADER_VER") && !verifyInfo.contains("PAGESIZE")) {
                emit(PatchState.Error("❌ 校验失败：产物镜像结构已被破坏！"))
                suExec("rm -rf $remoteWorkDir")
                return@flow
            }

            suExec("cd $remoteWorkDir && rm -rf verify && mkdir verify && cd verify && ../hsud_cli unpack ../patched.img")
            val extractedCheck = suExec("cd $remoteWorkDir/verify && ../hsud_cli cpio ramdisk.cpio extract init huijuiinit huijuisud")
            val lsExtracted = suExec("ls $remoteWorkDir/verify/")

            if (!lsExtracted.contains("init") || !lsExtracted.contains("huijuiinit") || !lsExtracted.contains("huijuisud")) {
                emit(PatchState.Error("❌ 校验失败：纯血劫持组件未成功注入！"))
                suExec("rm -rf $remoteWorkDir")
                return@flow
            }
            emit(PatchState.Progress("[-] 产物完整性校验通过！"))

            emit(PatchState.Progress("[-] 正在将产物导出到 Download 目录..."))
            val exportRes = suExec("cp $remoteWorkDir/patched.img \"$outputImagePath\"")
            if (exportRes.contains("ERROR")) {
                emit(PatchState.Error("❌ 导出失败，可能是内部存储权限问题！"))
                return@flow
            }
            suExec("chmod 644 \"$outputImagePath\"")
            suExec("rm -rf $remoteWorkDir")
            emit(PatchState.Success(outputImagePath, partition == "init_boot"))

        } catch (e: Throwable) {
            emit(PatchState.Error("❌ 注入阶段异常: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)
}
