package com.huijui.sunext.edition.core.root

import android.content.Context

/**
 * ramdisk 注入器 —— 劫持链路的唯一实现。
 *
 * 这一版修的是**整条劫持从来没生效过**的问题，一共四层：
 *
 * 1. 🔴 `hsud_cli cpio` 的每条命令必须是**一个带引号的整体参数**：
 *    ```sh
 *    # 错的（原代码）：会被拆成 5 个 token，add 需要 4 个 token 才成立，于是被静默忽略
 *    ./hsud_cli cpio ramdisk.cpio add 0755 init huijuiinit
 *    # 对的：
 *    ./hsud_cli cpio ramdisk.cpio "add 0755 init huijuiinit"
 *    ```
 *    原来那 6 条 cpio 命令**一条都没生效**，最后 repack 出来的是原封不动的 ramdisk。
 *
 * 2. 🔴 `huijuiinit.c` 里执行的是 `/force_insmod /huijui_phantom.ko`，
 *    而注入脚本塞的是 `huijui.ko`，`force_insmod` **压根没被注入**。
 *    于是 huijuiinit 一启动就 `_exit(1)`，模块永远不会被加载。
 *
 * 3. 🟠 原代码还额外注入了 `huijui.rc` 并在 init.rc 里 `import`，想用 `start huijui_daemon`
 *    拉起守护进程 —— 但 huijuiinit 自己已经 fork 了 huijuisud，等于双份守护进程抢同一个 socket。
 *    现在只保留 huijuiinit 这一条路径。
 *
 * 4. 🟠 repack 时没有设 `PATCHVBMETAFLAG=true`。这个环境变量是 hsud_cli 内建支持的，
 *    会把 boot 镜像自带 vbmeta 头里的校验标志位清掉（非破坏性、可逆），
 *    比旧 OTA 代码往 vbmeta 分区写 4096 字节零安全得多。
 */
object RamdiskInjector {

    /** huijuiinit.c 里写死的模块名，改这里必须同步改 C 源码。 */
    const val KO_ENTRY_NAME = "huijui_phantom.ko"

    /** 必须随 ramdisk 一起进根目录的可执行文件。 */
    private val TOOL_ASSETS = listOf(
        "exploit/hsud_cli" to "hsud_cli",
        "exploit/huijuiinit" to "huijuiinit",
        "exploit/huijuisud" to "huijuisud",
        "exploit/force_insmod" to "force_insmod",
    )

    /**
     * 把工具与内核模块投递到设备上的独立目录。
     * @return 失败时返回错误描述，成功返回 null
     */
    suspend fun stageAll(context: Context, remoteDir: String, koAssetName: String): String? {
        for ((asset, name) in TOOL_ASSETS) {
            if (!InstallIo.stageAsset(context, asset, "$remoteDir/$name", "0755")) {
                return "缺少或无法写入 $asset"
            }
        }
        if (!InstallIo.stageAsset(context, "exploit/$koAssetName", "$remoteDir/$KO_ENTRY_NAME", "0644")) {
            return "缺少内核模块 $koAssetName"
        }
        return null
    }

    /**
     * 生成注入脚本。
     *
     * 脚本做三件事：把 init 换成劫持壳、塞进四件套、重打包。
     * 每一步失败都会打印 `FAIL: ...` 并以非零码退出，调用方据此把真实原因抛给用户，
     * 而不是像旧代码那样 `> /dev/null 2>&1` 把一切吞掉然后只说一句"修补失败"。
     */
    fun buildScript(
        remoteDir: String,
        stockName: String,
        outputName: String,
        patchFstab: Boolean
    ): String = buildString {
        appendLine("#!/system/bin/sh")
        appendLine("set -e")
        appendLine("exec 2>&1")
        appendLine("cd $remoteDir")
        appendLine("HS=./hsud_cli")
        appendLine("")
        appendLine("echo '[-] unpack'")
        appendLine("\$HS unpack $stockName || { echo 'FAIL: unpack 失败，镜像格式不支持'; exit 11; }")
        appendLine("[ -f ramdisk.cpio ] || { echo 'FAIL: 没有 ramdisk.cpio（可能是选错了分区）'; exit 12; }")
        appendLine("")

        if (patchFstab) {
            appendLine("# fstab 去校验：找不到就跳过，不阻断")
            appendLine("for f in fstab.qcom fstab.mt6899 fstab.mt6989 fstab.mtk; do")
            appendLine("  if \$HS cpio ramdisk.cpio \"exists \$f\"; then")
            appendLine("    \$HS cpio ramdisk.cpio \"extract \$f \$f\"")
            appendLine("    sed -i 's/,avb=vbmeta_system//g; s/,avb_keys=[^ ]*//g; s/,verify//g; s/forceencrypt/encryptable/g' \"\$f\" || true")
            appendLine("    \$HS cpio ramdisk.cpio \"add 0750 \$f \$f\"")
            appendLine("    echo \"[-] fstab 已处理: \$f\"")
            appendLine("  fi")
            appendLine("done")
            appendLine("")
        }

        appendLine("# 1) 备份真 init，换成劫持壳")
        appendLine("if \$HS cpio ramdisk.cpio \"exists init.real\"; then")
        appendLine("  echo '[-] init.real 已存在，跳过改名（避免二次刷入丢失原 init）'")
        appendLine("else")
        appendLine("  \$HS cpio ramdisk.cpio \"exists init\" || { echo 'FAIL: ramdisk 里没有 init，这不是可劫持的启动镜像'; exit 13; }")
        appendLine("  \$HS cpio ramdisk.cpio \"mv init init.real\"")
        appendLine("fi")
        appendLine("\$HS cpio ramdisk.cpio \"add 0755 init huijuiinit\"")
        appendLine("")

        appendLine("# 2) 四件套：守护进程 / 强制 insmod / 内核模块")
        appendLine("\$HS cpio ramdisk.cpio \"add 0755 huijuisud huijuisud\"")
        appendLine("\$HS cpio ramdisk.cpio \"add 0755 force_insmod force_insmod\"")
        appendLine("\$HS cpio ramdisk.cpio \"add 0644 $KO_ENTRY_NAME $KO_ENTRY_NAME\"")
        appendLine("")

        appendLine("# 3) 校验：cpio 命令是「静默忽略」式的，不校验等于没做")
        appendLine("\$HS cpio ramdisk.cpio \"exists init.real\"   || { echo 'FAIL: init.real 未生成'; exit 14; }")
        // ⚠️ 这里必须校验 `init`，不是 `huijuiinit`。
        //    `add 0755 init huijuiinit` 的语义是「把文件 huijuiinit 添加为条目 init」，
        //    注入成功后 cpio 里存在的是 **init**（劫持壳），huijuiinit 这个名字从来没进过 ramdisk。
        //    之前写成 exists huijuiinit，导致每一步都成功、却在校验处必失败（exit 15），
        //    直接安装永远走不到写盘。真机复现确认过：
        //      Add entry [init] (0755)  -> EXIT=0
        //      exists huijuiinit        -> FAIL huijuiinit
        appendLine("\$HS cpio ramdisk.cpio \"exists init\"        || { echo 'FAIL: 劫持壳 init 未注入'; exit 15; }")
        appendLine("\$HS cpio ramdisk.cpio \"exists force_insmod\" || { echo 'FAIL: force_insmod 未注入'; exit 16; }")
        appendLine("\$HS cpio ramdisk.cpio \"exists $KO_ENTRY_NAME\" || { echo 'FAIL: 内核模块未注入'; exit 17; }")
        appendLine("")

        appendLine("# 4) 重打包：PATCHVBMETAFLAG 清掉 boot 镜像自带 vbmeta 的校验标志")
        appendLine("PATCHVBMETAFLAG=true \$HS repack $stockName $outputName || { echo 'FAIL: repack 失败'; exit 18; }")
        appendLine("[ -s $outputName ] || { echo 'FAIL: 输出镜像为空'; exit 19; }")
        appendLine("echo PATCH_OK")
    }
}
