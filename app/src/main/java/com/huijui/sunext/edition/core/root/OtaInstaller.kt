package com.huijui.sunext.edition.core.root

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

/**
 * OTA 槽位安装（把 HUI 引擎写进**另一条** A/B 槽位，不动当前正在跑的系统）。
 *
 * 旧实现有四个问题，其中两个必炸：
 *
 * 1. 🔴 **写完之后根本不会切槽，但提示里写着"重启后将自动切换到 _b"**。
 *    Android 不会因为你往另一个槽写了东西就自动切换 —— 必须显式
 *    `bootctl set-active=<槽位>`。所以旧流程的"OTA 更新成功"是一个彻底的假象：
 *    用户重启，进的还是原来那个槽，什么都没变。
 *
 * 2. 🔴 **把 vbmeta 写成 4096 字节全零**：
 *    ```kotlin
 *    emptyVbmeta.writeBytes(ByteArray(4096))
 *    dd if=empty_ota_vbmeta.img of=$vbmetaDevice
 *    ```
 *    这不是"关闭校验"，这是把 AVB 头连签名一起抹掉。bootloader 校验直接失败，
 *    轻则拒绝启动，重则**硬砖**（部分机型连 fastboot 都进不去）。
 *    现在改成：优先用 `avbctl disable-verification`（非破坏性），没有就只提示，绝不写 vbmeta。
 *
 * 3. 判分区存在靠 `ls` 的输出里有没有 `No such file` —— 中文/其他 locale 设备上直接失效。
 *
 * 4. dd 不 fsync、不检查退出码、只比对 1 个 4KB 块当"回读校验"。
 */
object OtaInstaller {

    sealed class OtaState {
        data class Progress(val message: String) : OtaState()
        data class Success(val message: String) : OtaState()
        data class Error(val errorMsg: String) : OtaState()
    }

    private const val BACKUP_DIR = "/sdcard/Download/HuiJuiSU_Backup"
    private const val REMOTE_DIR = "/data/local/tmp/huijui_ota"

    /**
     * @param switchSlot 是否在写完后把启动槽切到目标槽。
     *        默认 **false**：往非活动槽写入本身是安全的，但自动切换有风险
     *        （目标槽的系统分区可能是旧的，或者写坏了就直接起不来）。
     *        想切的话由调用方显式传 true，并且应该先经过用户确认。
     */
    fun install(
        context: Context,
        localImgUri: android.net.Uri? = null,
        switchSlot: Boolean = false
    ): Flow<OtaState> = flow {
        try {
            emit(OtaState.Progress("[-] 检查 Root 环境…"))
            if (!RootEngineVerifier.checkStatus().isWorking) {
                emit(OtaState.Error("❌ 未检测到可用的 Root 环境"))
                return@flow
            }

            // ── 1. 槽位 ────────────────────────────────────────────
            // 前置检测：内核 KMI / 内置模块 / vermagic 全部对得上才允许动 OTA 槽位。
            // 直装路径已经接了，OTA 之前没接 —— 同样是往分区里写东西，一样会写坏。
            emit(OtaState.Progress("[-] 检测系统与内核版本…"))
            val pre = InstallPreflight.run(context)
            if (!pre.canProceed) {
                emit(OtaState.Error("❌ 前置检测未通过：${pre.blocking}"))
                return@flow
            }
            emit(OtaState.Progress("[-] 检测通过：${pre.kmiBranch}，模块 ${pre.koAsset}"))

            val currentSlot = InstallIo.currentSlot()
            if (currentSlot != "_a" && currentSlot != "_b") {
                emit(OtaState.Error("❌ 当前设备不是 A/B 分区架构（slot_suffix=\"$currentSlot\"），无法使用 OTA 槽位更新"))
                return@flow
            }
            val targetSlot = if (currentSlot == "_a") "_b" else "_a"
            emit(OtaState.Progress("[-] 当前槽位 $currentSlot → 目标槽位 $targetSlot"))

            // ── 2. 目标分区 ────────────────────────────────────────
            val targetName = if (InstallIo.exists("/dev/block/by-name/init_boot$targetSlot")) "init_boot" else "boot"
            val targetDevice = InstallIo.pickPartition(listOf("$targetName$targetSlot"))
                ?: run {
                    emit(OtaState.Error("❌ 找不到目标分区 /dev/block/by-name/$targetName$targetSlot"))
                    return@flow
                }
            val partSize = InstallIo.partitionSize(targetDevice)
            if (partSize <= 0L) {
                emit(OtaState.Error("❌ 无法读取目标分区容量，已终止"))
                return@flow
            }
            emit(OtaState.Progress("[-] 目标分区: $targetDevice（${InstallIo.mb(partSize)}MB）"))

            // ── 3. 取原厂镜像 ──────────────────────────────────────
            val workDir = File(context.cacheDir, "ota_install").apply { mkdirs() }
            val stockImg = File(workDir, "stock_ota_$targetName$targetSlot.img")

            if (localImgUri != null) {
                emit(OtaState.Progress("[-] 正在读取本地镜像…"))
                val ok = runCatching {
                    context.contentResolver.openInputStream(localImgUri)?.use { input ->
                        stockImg.outputStream().use { output -> input.copyTo(output) }
                    }
                    stockImg.length() > 1024L
                }.getOrDefault(false)
                if (!ok) {
                    emit(OtaState.Error("❌ 读取本地镜像失败"))
                    return@flow
                }
            } else {
                emit(OtaState.Progress("[-] 正在提取目标槽位原厂镜像…"))
                val extract = InstallIo.run(
                    "dd if='$targetDevice' of='${stockImg.absolutePath}' bs=1M conv=fsync 2>&1; sync",
                    5 * 60_000L
                )
                if (!extract.ok || stockImg.length() < 1024L) {
                    emit(OtaState.Error("❌ 提取目标分区失败：${extract.output.takeLast(300)}"))
                    return@flow
                }
            }
            emit(OtaState.Progress("✅ 镜像就绪：${InstallIo.mb(stockImg.length())}MB"))

            // ── 4. 备份 ────────────────────────────────────────────
            val backupPath = "$BACKUP_DIR/ota_${targetName}${targetSlot}_${System.currentTimeMillis()}.img"
            val backup = InstallIo.run("mkdir -p $BACKUP_DIR && cp '${stockImg.absolutePath}' '$backupPath'")
            if (!backup.ok || InstallIo.size(backupPath) < 1024L) {
                emit(OtaState.Error("❌ 备份失败，已中止（没有备份不允许刷写）"))
                return@flow
            }
            emit(OtaState.Progress("✅ 已备份 → $backupPath"))

            // ── 5. 匹配内核模块 ────────────────────────────────────
            val kernelRelease = KernelVersionMatcher.currentKernelRelease()
            val ko = InstallIo.resolveKo(context, kernelRelease)
            if (ko is InstallIo.KoPick.Missing) {
                emit(OtaState.Error("❌ ${ko.reason}"))
                return@flow
            }
            val koOk = ko as InstallIo.KoPick.Ok
            emit(OtaState.Progress("[-] 匹配内核模块: ${koOk.assetName}"))
            koOk.warning?.let { emit(OtaState.Progress(it)) }

            // ── 6. 投递 + 打补丁 ───────────────────────────────────
            emit(OtaState.Progress("[-] 正在投递底层工具…"))
            InstallIo.run("rm -rf $REMOTE_DIR && mkdir -p $REMOTE_DIR && chmod 755 $REMOTE_DIR")
            val injectError = RamdiskInjector.stageAll(context, REMOTE_DIR, koOk.assetName)
            if (injectError != null) {
                emit(OtaState.Error("❌ 投递失败: $injectError"))
                return@flow
            }
            InstallIo.run("cp '${stockImg.absolutePath}' $REMOTE_DIR/stock.img")

            emit(OtaState.Progress("[-] 正在执行底层注入（劫持 init）…"))
            val scriptFile = File(workDir, "ota_patch.sh")
            scriptFile.writeText(
                RamdiskInjector.buildScript(
                    remoteDir = REMOTE_DIR,
                    stockName = "stock.img",
                    outputName = "patched.img",
                    patchFstab = true
                )
            )
            InstallIo.run("cp '${scriptFile.absolutePath}' $REMOTE_DIR/ota_patch.sh && chmod 755 $REMOTE_DIR/ota_patch.sh")

            val patchOut = InstallIo.run("sh $REMOTE_DIR/ota_patch.sh", 5 * 60_000L)
            if (!patchOut.output.contains("PATCH_OK")) {
                emit(OtaState.Error("❌ OTA 引擎修补失败：\n${patchOut.output.takeLast(600)}"))
                return@flow
            }

            val patched = "$REMOTE_DIR/patched.img"
            val patchedSize = InstallIo.size(patched)
            if (patchedSize <= 0L || patchedSize > partSize) {
                emit(OtaState.Error("❌ 容量校验失败：${InstallIo.mb(patchedSize)}MB / ${InstallIo.mb(partSize)}MB，拒绝写入"))
                return@flow
            }

            // ── 7. 写入（含 fsync + 全长回读校验）──────────────────
            emit(OtaState.Progress("[-] 正在写入 $targetDevice…"))
            val write = InstallIo.writeImage(patched, targetDevice)
            if (!write.ok) {
                emit(OtaState.Error("❌ 写入失败：${write.output}\n\n🛡️ 备份：$backupPath"))
                return@flow
            }

            // ── 8. AVB：只做非破坏性处理 ───────────────────────────
            val avbNote = disableVerificationIfPossible()

            // ── 9. 切槽（可选，且如实报告）─────────────────────────
            val switchNote = if (switchSlot) switchActiveSlot(targetSlot) else {
                "ℹ️ 未切换启动槽位。当前仍从 $currentSlot 启动；如需启用请重启到更新流程或手动切换。"
            }

            InstallIo.run("rm -rf $REMOTE_DIR")
            runCatching { stockImg.delete() }

            emit(
                OtaState.Success(
                    buildString {
                        appendLine("✅ 已写入 $targetName$targetSlot 并校验通过")
                        appendLine()
                        appendLine(switchNote)
                        appendLine(avbNote)
                        appendLine()
                        appendLine("🛡️ 原厂镜像备份：$backupPath")
                    }.trim()
                )
            )
        } catch (e: Throwable) {
            emit(OtaState.Error("❌ 执行异常: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 关闭 AVB 校验。
     *
     * 只调用 `avbctl disable-verification`（它改的是 vbmeta 里的 flag，可逆、不破坏签名）。
     * 设备上没有 avbctl 时**什么都不做** —— 绝不重演旧版把 vbmeta 写成全零的行为。
     */
    private suspend fun disableVerificationIfPossible(): String {
        val has = InstallIo.run("command -v avbctl 2>/dev/null").output
        if (has.isBlank()) {
            return "⚠️ 未找到 avbctl，未改动 vbmeta。若目标槽位启用了 AVB 校验，可能需要手动关闭验证。"
        }
        val r = InstallIo.run("avbctl disable-verification 2>&1")
        return if (r.ok) {
            "✅ 已通过 avbctl 关闭验证（未改动 vbmeta 签名）"
        } else {
            "⚠️ avbctl 执行失败：${r.output.take(200)}"
        }
    }

    private suspend fun switchActiveSlot(target: String): String {
        // 不同机型/版本 bootctl 的子命令不一样，逐个试
        val attempts = listOf(
            "bootctl set-active=$target",
            "bootctl set-active-boot-slot $target",
            "bootctl set-active $target"
        )
        for (cmd in attempts) {
            val r = InstallIo.run("$cmd 2>&1")
            if (r.ok) return "✅ 启动槽位已切到 $target，重启后生效"
        }
        return "⚠️ 自动切换槽位失败（设备不支持 bootctl set-active）。请重启进入更新流程，或用 fastboot 手动切换。"
    }
}
