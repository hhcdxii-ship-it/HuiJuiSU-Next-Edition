package com.huijui.sunext.edition.core.root

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

/**
 * 直接安装（在本机把 HUI 引擎注入当前内核 ramdisk 并写回）。
 *
 * 重构前有两条会**直接变砖**的逻辑，都在旧 `doIndependentPatchAndFlash` 里：
 *
 * 1. 无条件回写 boot：
 *    ```kotlin
 *    suExec("dd if=$remoteDir/stock_boot.img of=$bootDevice ...")   // 不管目标是不是 init_boot
 *    ```
 *    A13+ 的纯 init_boot 机型上 `stock_boot.img` 根本没提取成功（boot 分区在读之前就 dd 失败），
 *    这个文件是 **0 字节**，于是"回写原厂 boot"实际是把 boot 分区写空 —— 直接黑砖。
 *
 * 2. 双写分支自相矛盾：
 *    ```kotlin
 *    if (targetDevice.contains("init_boot")) { dd patched -> init_boot }
 *    else { dd patched -> bootDevice }
 *    ```
 *    但上面第 1 条已经先把 boot 写了一遍，两条命令叠加，语义完全混乱。
 *
 * 现在的原则：**永远只动一个分区**。有 init_boot 就只改 init_boot，没有就只改 boot；
 * 原厂镜像只用于备份和回滚，绝不反向写回正在使用的分区。
 */
object DirectInstaller {

    sealed class InstallState {
        data class Progress(val message: String) : InstallState()
        data class Success(val message: String) : InstallState()
        data class Error(val errorMsg: String) : InstallState()
    }

    private const val BACKUP_DIR = "/sdcard/Download/HuiJuiSU_Backup"
    private const val REMOTE_DIR = "/data/local/tmp/huijui_direct"

    fun install(context: Context, localImgUri: android.net.Uri? = null): Flow<InstallState> = flow {
        try {
            emit(InstallState.Progress("[-] 检查 Root 环境…"))
            if (!RootEngineVerifier.checkStatus().isWorking) {
                emit(InstallState.Error("❌ 未检测到可用的 Root 环境，请先授权"))
                return@flow
            }

            // ── 1. 选定唯一的目标分区 ──────────────────────────────
            val slot = InstallIo.currentSlot()
            val hasInitBoot = InstallIo.exists("/dev/block/by-name/init_boot$slot")
            val targetName = if (hasInitBoot) "init_boot" else "boot"
            val targetDevice = InstallIo.pickPartition(listOf("$targetName$slot"))
                ?: run {
                    emit(InstallState.Error("❌ 找不到分区 /dev/block/by-name/$targetName$slot"))
                    return@flow
                }
            emit(InstallState.Progress("[-] 目标分区: $targetDevice（槽位 ${slot.ifBlank { "无（非 A/B）" }}）"))

            val partSize = InstallIo.partitionSize(targetDevice)
            if (partSize <= 0L) {
                emit(InstallState.Error("❌ 读取分区容量失败，已终止（防止写坏设备）"))
                return@flow
            }

            // ── 2. 提取原厂镜像 + 备份 ─────────────────────────────
            val workDir = File(context.cacheDir, "direct_install").apply { mkdirs() }
            val stockImg = File(workDir, "stock_$targetName.img")
            val extract = InstallIo.run(
                "dd if='$targetDevice' of='${stockImg.absolutePath}' bs=1M conv=fsync 2>&1; sync",
                5 * 60_000L
            )
            if (!extract.ok || stockImg.length() < 1024L) {
                emit(InstallState.Error("❌ 提取原厂镜像失败：${extract.output.takeLast(300)}"))
                return@flow
            }

            val stamp = System.currentTimeMillis()
            val backupPath = "$BACKUP_DIR/${targetName}${slot.ifBlank { "" }}_$stamp.img"
            val backedUp = InstallIo.run("mkdir -p $BACKUP_DIR && cp '${stockImg.absolutePath}' '$backupPath'")
            val backupSize = InstallIo.size(backupPath)
            if (!backedUp.ok || backupSize < 1024L) {
                emit(InstallState.Error("❌ 备份失败，已中止（没有备份不允许刷写）"))
                return@flow
            }
            emit(InstallState.Progress("✅ 已备份原厂镜像 → $backupPath"))

            // ── 3. 精确匹配内核模块 ────────────────────────────────
            val kernelRelease = KernelVersionMatcher.currentKernelRelease()
            val ko = InstallIo.resolveKo(context, kernelRelease)
            if (ko is InstallIo.KoPick.Missing) {
                emit(InstallState.Error("❌ ${ko.reason}"))
                return@flow
            }
            val koAsset = (ko as InstallIo.KoPick.Ok).assetName
            emit(InstallState.Progress("[-] 匹配内核模块: $koAsset"))

            // ── 4. 投递工具到独立目录 ──────────────────────────────
            emit(InstallState.Progress("[-] 正在投递底层工具…"))
            InstallIo.run("rm -rf $REMOTE_DIR && mkdir -p $REMOTE_DIR && chmod 755 $REMOTE_DIR")

            val staged = listOf(
                Triple("exploit/hsud_cli", "$REMOTE_DIR/hsud_cli", "0755"),
                Triple("exploit/huijuiinit", "$REMOTE_DIR/huijuiinit", "0755"),
                Triple("exploit/huijuisud", "$REMOTE_DIR/huijuisud", "0755"),
                Triple("exploit/$koAsset", "$REMOTE_DIR/huijui.ko", "0644"),
            )
            for ((asset, remote, mode) in staged) {
                if (!InstallIo.stageAsset(context, asset, remote, mode)) {
                    emit(InstallState.Error("❌ 投递失败: $asset"))
                    return@flow
                }
            }
            InstallIo.run("cp '${stockImg.absolutePath}' $REMOTE_DIR/stock.img")

            // ── 5. 打补丁 ─────────────────────────────────────────
            val rcContent = buildString {
                appendLine("on post-fs-data")
                appendLine("    cp /huijui.ko /dev/huijui.ko")
                appendLine("    chmod 0644 /dev/huijui.ko")
                appendLine("    insmod /dev/huijui.ko")
                appendLine("    chmod 0666 /dev/huijui")
                appendLine("    chcon u:object_r:device:s0 /dev/huijui")
                appendLine("    chmod 0755 /huijuisud")
                appendLine("    start huijui_daemon")
                appendLine("")
                appendLine("service huijui_daemon /huijuisud")
                appendLine("    class late_start")
                appendLine("    user root")
                appendLine("    group root")
                appendLine("    seclabel u:r:su:s0")
            }
            File(workDir, "huijui.rc").writeText(rcContent)
            InstallIo.run("cp '${File(workDir, "huijui.rc").absolutePath}' $REMOTE_DIR/huijui.rc")

            emit(InstallState.Progress("[-] 正在注入 HUI 引擎（解包 / 改写 / 重打包）…"))
            val script = buildString {
                appendLine("#!/system/bin/sh")
                appendLine("set -e")
                appendLine("exec 2>&1")
                appendLine("cd $REMOTE_DIR")
                appendLine("./hsud_cli unpack stock.img")
                // fstab：去掉校验与强制加密，按机型逐个尝试，找不到就跳过（不阻断）
                appendLine("for f in fstab.qcom fstab.mt6899 fstab.mt6989 fstab.mtk; do")
                appendLine("  if ./hsud_cli cpio ramdisk.cpio extract \$f 2>/dev/null; then")
                appendLine("    sed -i 's/,avb=vbmeta_system//g; s/,avb_keys=[^ ]*//g; s/,verify//g; s/forceencrypt/encryptable/g' \$f")
                appendLine("    ./hsud_cli cpio ramdisk.cpio add 0750 \$f \$f")
                appendLine("  fi")
                appendLine("done")
                appendLine("./hsud_cli cpio ramdisk.cpio extract init.rc")
                appendLine("echo '' >> init.rc")
                appendLine("echo 'import /huijui.rc' >> init.rc")
                appendLine("./hsud_cli cpio ramdisk.cpio add 0750 init.rc init.rc")
                appendLine("./hsud_cli cpio ramdisk.cpio mv init init.real")
                appendLine("./hsud_cli cpio ramdisk.cpio add 0755 init huijuiinit")
                appendLine("./hsud_cli cpio ramdisk.cpio add 0755 huijuisud huijuisud")
                appendLine("./hsud_cli cpio ramdisk.cpio add 0644 huijui.ko huijui.ko")
                appendLine("./hsud_cli cpio ramdisk.cpio add 0644 huijui.rc huijui.rc")
                appendLine("./hsud_cli repack stock.img patched.img")
                appendLine("echo PATCH_OK")
            }
            val scriptFile = File(workDir, "direct_patch.sh")
            scriptFile.writeText(script)
            InstallIo.run("cp '${scriptFile.absolutePath}' $REMOTE_DIR/direct_patch.sh && chmod 755 $REMOTE_DIR/direct_patch.sh")

            val patchOut = InstallIo.run("sh $REMOTE_DIR/direct_patch.sh", 5 * 60_000L)
            if (!patchOut.output.contains("PATCH_OK")) {
                emit(InstallState.Error("❌ 引擎修补失败：\n${patchOut.output.takeLast(600)}"))
                return@flow
            }

            val patched = "$REMOTE_DIR/patched.img"
            val patchedSize = InstallIo.size(patched)
            if (patchedSize <= 0L || patchedSize > partSize) {
                emit(InstallState.Error("❌ 容量校验失败：修补后 ${InstallIo.mb(patchedSize)}MB / 分区 ${InstallIo.mb(partSize)}MB"))
                return@flow
            }

            // ── 6. 写入唯一的那个分区 ──────────────────────────────
            emit(InstallState.Progress("[-] 正在写入 $targetDevice（${InstallIo.mb(patchedSize)}MB）…"))
            val write = InstallIo.writeImage(patched, targetDevice)
            if (!write.ok) {
                emit(InstallState.Error("❌ 写入失败：${write.output}\n\n🛡️ 原厂镜像备份在：$backupPath"))
                return@flow
            }

            InstallIo.run("rm -rf $REMOTE_DIR")
            runCatching { stockImg.delete() }

            emit(
                InstallState.Success(
                    "✅ HUI 引擎已注入 $targetName$slot\n\n" +
                        "重启后看到「工作中 (HUI)」即代表成功。\n" +
                        "🛡️ 原厂镜像备份：$backupPath"
                )
            )
        } catch (e: Throwable) {
            emit(InstallState.Error("❌ 执行异常: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)
}
