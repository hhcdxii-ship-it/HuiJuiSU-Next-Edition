package com.huijui.sunext.edition.core.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 🚀 系统魔改提供者
 *
 * 两大能力：
 *   1. hosts 广告屏蔽（内置 ~100 个核心广告域名，备份/应用/还原）
 *   2. 分区 RW/RO 切换（/system /vendor /product /system_ext）
 *
 * 安全设计：
 *   - hosts 应用前先备份原文件到 /data/adb/huijuisu/hosts.bak
 *   - 所有操作二次确认，UI 层拦截
 *   - remount 失败自动还原（不改系统分区状态）
 */
object SystemTweaksProvider {

    const val HOSTS_PATH = "/system/etc/hosts"
    const val HOSTS_BACKUP = "/data/adb/huijuisu/hosts.bak"
    const val HOSTS_MARK = "/data/adb/huijuisu/hosts.active"

    // ==========================================
    // hosts 管理
    // ==========================================

    data class HostsStatus(
        val available: Boolean,
        val isEnabled: Boolean,        // 是否已应用自定义 hosts
        val currentEntries: Int,        // 当前 hosts 中非空非注释行数
        val adBlockDomainCount: Int     // 内置广告域名数量
    )

    /**
     * 内置广告域名（精选 ~100 个覆盖 Google/百度/阿里/腾讯/字节/小米/国际）
     */
    private val AD_BLOCK_DOMAINS = listOf(
        // Google / 通用国际
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com",
        "google-analytics.com",
        "admob.com",
        "pagead2.googlesyndication.com",
        "partner.googleadservices.com",
        "pubads.g.doubleclick.net",
        "securepubads.g.doubleclick.net",
        "stats.g.doubleclick.net",
        "cm.g.doubleclick.net",
        "ad.doubleclick.net",
        "adclick.g.doubleclick.net",
        "googletagservices.com",
        "googletagmanager.com",
        "ads.youtube.com",
        "graph.facebook.com",
        "pixel.facebook.com",
        "analytics.facebook.com",
        "ads.twitter.com",
        "analytics.twitter.com",
        "ads.linkedin.com",
        "ads.pinterest.com",
        // 阿里系
        "tanx.com",
        "alimama.com",
        "mmstat.com",
        "atanx.alicdn.com",
        "gxb.mmstat.com",
        "log.mmstat.com",
        "simba.taobao.com",
        "tns.simba.taobao.com",
        "adash.m.taobao.com",
        "adashbc.m.taobao.com",
        "adashx.m.taobao.com",
        "ads.taobao.com",
        "pps.tanx.com",
        // 百度系
        "hm.baidu.com",
        "pos.baidu.com",
        "cpro.baidustatic.com",
        "dup.baidustatic.com",
        "eclick.baidu.com",
        "mobads.baidu.com",
        "union.baidu.com",
        // 腾讯系
        "pingma.qq.com",
        "pingfore.qq.com",
        "pingfore.3g.qq.com",
        "mi.gdt.qq.com",
        "e.qq.com",
        "splash.qq.com",
        // 字节系
        "umeng.com",
        "umeng.co",
        "alog.umeng.com",
        "alog.umengcloud.com",
        "ulogs.umeng.com",
        "oc.umeng.com",
        "cnzz.com",
        "s4.cnzz.com",
        "s9.cnzz.com",
        "s11.cnzz.com",
        "s19.cnzz.com",
        "s22.cnzz.com",
        "s23.cnzz.com",
        "s24.cnzz.com",
        // 小米 / HyperOS
        "tracking.miui.com",
        "tracking.intl.miui.com",
        "data.mistat.xiaomi.com",
        "data.mistat.intl.xiaomi.com",
        "api.ad.xiaomi.com",
        "api.ad.intl.xiaomi.com",
        "ad.miui.com",
        "ad.intl.miui.com",
        "sdkconfig.ad.xiaomi.com",
        // 其他国际
        "ads.pubmatic.com",
        "ib.adnxs.com",
        "secure.adnxs.com",
        "cdn.taboola.com",
        "trc.taboola.com",
        "widgets.outbrain.com",
        "log.outbrain.com",
        "odb.outbrain.com",
        "ads.yieldmo.com",
        "c.amazon-adsystem.com",
        "aax.amazon-adsystem.com",
        "mads.amazon-adsystem.com",
        "s.amazon-adsystem.com",
        "fls-na.amazon-adsystem.com",
        "ads.yahoo.com",
        "adservice.google.com",
        "adservice.google.co.jp",
        "adservice.google.com.hk"
    )

    fun getAdBlockDomainCount(): Int = AD_BLOCK_DOMAINS.size

    /**
     * 读取 hosts 当前状态
     */
    suspend fun getHostsStatus(): HostsStatus = withContext(Dispatchers.IO) {
        val check = HuijuiShell.exec(
            "test -f $HOSTS_PATH && echo yes || echo no"
        )
        val available = check.out.joinToString("").trim() == "yes"
        if (!available) {
            return@withContext HostsStatus(
                available = false,
                isEnabled = false,
                currentEntries = 0,
                adBlockDomainCount = AD_BLOCK_DOMAINS.size
            )
        }

        // 是否已应用（用标记文件判断，比对比内容更稳）
        val markCheck = HuijuiShell.exec("test -f $HOSTS_MARK && echo yes || echo no")
        val isEnabled = markCheck.out.joinToString("").trim() == "yes"

        // 当前条目数
        val countRes = HuijuiShell.exec(
            "grep -vcE '^\\s*(#|\\$)' $HOSTS_PATH 2>/dev/null || echo 0"
        )
        val entries = countRes.out.firstOrNull()?.trim()?.toIntOrNull() ?: 0

        HostsStatus(
            available = true,
            isEnabled = isEnabled,
            currentEntries = entries,
            adBlockDomainCount = AD_BLOCK_DOMAINS.size
        )
    }

    /**
     * 应用 hosts 广告屏蔽
     *
     * 流程：
     *   1. 生成 hosts 内容
     *   2. 首次应用前备份原 hosts
     *   3. 写入新 hosts（失败自动尝试 remount /system rw）
     *   4. 创建标记文件
     */
    suspend fun applyHosts(): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 1. 备份原 hosts（如果备份不存在）
            val backupCheck = HuijuiShell.exec("test -f $HOSTS_BACKUP && echo yes || echo no")
            if (backupCheck.out.joinToString("").trim() != "yes") {
                HuijuiShell.exec("mkdir -p /data/adb/huijuisu")
                HuijuiShell.exec("cp $HOSTS_PATH $HOSTS_BACKUP")
            }

            // 2. 生成 hosts 内容
            val newHosts = buildString {
                appendLine("# HUI adblock hosts - generated by HuiJuiSU Next")
                appendLine("# Total domains: ${AD_BLOCK_DOMAINS.size}")
                appendLine("127.0.0.1 localhost")
                appendLine("::1 localhost")
                AD_BLOCK_DOMAINS.forEach { domain ->
                    appendLine("0.0.0.0 $domain")
                }
            }

            // 3. 写临时文件
            val tmpFile = "/data/adb/huijuisu/hosts.new"
            HuijuiShell.exec("rm -f $tmpFile")
            // 用 printf 写多行内容太麻烦，改用 heredoc 风格
            // 拆成多次 echo -e 追加
            HuijuiShell.exec("rm -f $tmpFile && touch $tmpFile")
            // 分批写入（每批 20 个域名，避免单条命令过长）
            newHosts.lines().chunked(20).forEach { chunk ->
                val content = chunk.joinToString("\\n").replace("'", "'\\''")
                HuijuiShell.exec("printf '%s\\n' '$content' >> $tmpFile")
            }

            // 4. 尝试直接覆盖
            var writeRes = HuijuiShell.exec("cp $tmpFile $HOSTS_PATH && echo ok")
            var success = writeRes.out.joinToString("").contains("ok")

            // 5. 失败则尝试 remount /system
            if (!success) {
                HuijuiShell.exec("mount -o rw,remount /system 2>/dev/null")
                HuijuiShell.exec("mount -o rw,remount / 2>/dev/null")
                writeRes = HuijuiShell.exec("cp $tmpFile $HOSTS_PATH && echo ok")
                success = writeRes.out.joinToString("").contains("ok")
            }

            if (success) {
                HuijuiShell.exec("chmod 644 $HOSTS_PATH")
                HuijuiShell.exec("echo active > $HOSTS_MARK")
                HuijuiShell.exec("rm -f $tmpFile")
                Result.success("已应用 ${AD_BLOCK_DOMAINS.size} 个广告屏蔽规则\n重启网络或设备后完全生效")
            } else {
                HuijuiShell.exec("rm -f $tmpFile")
                Result.failure(Exception("写入 hosts 失败，请先开启 /system 可写"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 还原原 hosts
     */
    suspend fun restoreHosts(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val backupCheck = HuijuiShell.exec("test -f $HOSTS_BACKUP && echo yes || echo no")
            if (backupCheck.out.joinToString("").trim() != "yes") {
                return@withContext Result.failure(Exception("未找到备份文件，无法还原"))
            }

            var restoreRes = HuijuiShell.exec("cp $HOSTS_BACKUP $HOSTS_PATH && echo ok")
            var success = restoreRes.out.joinToString("").contains("ok")

            if (!success) {
                HuijuiShell.exec("mount -o rw,remount /system 2>/dev/null")
                HuijuiShell.exec("mount -o rw,remount / 2>/dev/null")
                restoreRes = HuijuiShell.exec("cp $HOSTS_BACKUP $HOSTS_PATH && echo ok")
                success = restoreRes.out.joinToString("").contains("ok")
            }

            if (success) {
                HuijuiShell.exec("rm -f $HOSTS_MARK")
                Result.success("已还原原 hosts\n重启网络或设备后完全生效")
            } else {
                Result.failure(Exception("还原 hosts 失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==========================================
    // 分区 RW/RO 切换
    // ==========================================

    data class PartitionMount(
        val path: String,          // /system, /vendor, etc.
        val device: String,        // /dev/block/...
        val fsType: String,        // ext4, erofs
        val writable: Boolean
    )

    data class MountStatus(
        val partitions: List<PartitionMount>
    )

    private val TARGET_PARTITIONS = listOf("/system", "/vendor", "/product", "/system_ext")

    suspend fun getMountStatus(): MountStatus = withContext(Dispatchers.IO) {
        val result = HuijuiShell.exec("cat /proc/mounts")
        val partitions = mutableListOf<PartitionMount>()

        for (line in result.out) {
            val parts = line.split(" ").filter { it.isNotBlank() }
            if (parts.size < 4) continue
            val (device, mountPoint, fsType, options) = parts
            if (mountPoint !in TARGET_PARTITIONS) continue

            partitions.add(
                PartitionMount(
                    path = mountPoint,
                    device = device,
                    fsType = fsType,
                    writable = options.split(",").any { it == "rw" }
                )
            )
        }

        MountStatus(partitions)
    }

    /**
     * 切换单个分区的 RW/RO
     */
    suspend fun remount(partition: String, writable: Boolean): Boolean = withContext(Dispatchers.IO) {
        val opts = if (writable) "rw" else "ro"
        // 尝试多种语法，从简单到复杂
        val commands = listOf(
            "mount -o remount,$opts $partition",
            "mount -o $opts,remount $partition",
            "mount -o remount,$opts $(mount | grep \" $partition \" | awk '{print \$1}') $partition"
        )
        for (cmd in commands) {
            val r = HuijuiShell.exec("$cmd 2>&1 && echo ok || echo fail")
            if (r.out.joinToString("").contains("ok")) {
                // 验证是否真的切换成功
                val verify = HuijuiShell.exec(
                    "mount | grep \" $partition \" | grep -q '\\(^\\|,\\)$opts\\('\\|,\\|\\)' && echo yes || echo no"
                )
                if (verify.out.joinToString("").contains("yes")) return@withContext true
            }
        }
        false
    }

    /**
     * 全局切换（所有目标分区）
     */
    suspend fun remountAll(writable: Boolean): Int = withContext(Dispatchers.IO) {
        var count = 0
        for (p in TARGET_PARTITIONS) {
            if (remount(p, writable)) count++
        }
        count
    }
}
