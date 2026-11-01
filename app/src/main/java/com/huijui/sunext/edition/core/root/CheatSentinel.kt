package com.huijui.sunext.edition.core.root

import android.content.Context
import com.huijui.sunext.edition.core.utils.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 作弊哨兵。
 *
 * 设计原则只有一条：**宁可漏判，绝不误伤。**
 *
 * 旧 `CheatDetector` 是关键词匹配，`GENERAL_CHEAT_KEYWORDS` 里有
 * "mod" / "esp" / "hack" —— 任何包装名带 mod 的应用加上 INTERNET 权限就会被判作弊，
 * 误伤面极大（`com.modern.xxx`、`xxx.module`、西语应用里的 "esp"…）。
 * 而且它只看「装没装」，不看「有没有真的用 Root 去做」。
 *
 * 这里改成多信号加权 + 强制上下文：
 *
 *   S1 作弊工具进程正在运行         权重 3
 *   S2 该作弊工具派生了 su 子进程    权重 3   ← 真正用了 Root 的硬证据
 *   S3 有联机游戏进程正在运行        权重 3   ← 强制上下文，没有游戏就不处罚
 *   S4 该作弊工具在 Root 授权名单里   权重 1
 *
 * 判定：分数 ≥ 9 且 同时具备 (S1 && S3 && (S2 || S4)) 才 GUILTY。
 * 也就是说必须「作弊工具在跑 + 游戏在跑 + 它确实拿到了 Root」三个条件同时成立。
 * 只装了性能模块、调温模块、帧率解锁的用户根本不满足 S1/S2/S4，永远不会被处罚。
 */
object CheatSentinel {

    data class Signal(val id: String, val weight: Int, val detail: String)

    enum class Verdict { CLEAR, WATCH, GUILTY }

    data class Report(
        val verdict: Verdict,
        val score: Int,
        val signals: List<Signal>,
        val target: String?
    ) {
        val isGuilty: Boolean get() = verdict == Verdict.GUILTY
    }

    /**
     * 具体的作弊工具特征。**只用限定包名/进程名，不用泛化的单词匹配。**
     * 泛化词是误伤的根源："mod" / "esp" / "hack" 这些在正常应用里太常见。
     */
    private val CHEAT_SIGNATURES = listOf(
        "com.zhiliao.gameguardian",   // GameGuardian
        "com.gameguardian",
        "ru.sxbuid.luckypatcher",     // Lucky Patcher
        "com.lucky.patcher",
        "org.frida.server",           // frida-server
        "frida-server",
        "com.roblox.scriptexecutor",
        "com.cheat.engine",
        "gg.modifier",
        "com.gamehacker",
        "com.aimbot",
        "com.wallhack",
        "modmenu",
        "cheatmenu",
    )

    /** 联机游戏特征：要么是已知包名，要么是已知发行商前缀。 */
    private val ONLINE_GAME_SIGNATURES = listOf(
        "com.tencent.tmgp",           // 腾讯手游（王者/和平等）
        "com.tencent.lolm",
        "com.miHoYo",                 // 米哈游
        "com.mihoyo",
        "com.netease.g",              // 网易
        "com.netease.moba",
        "com.supercell",
        "com.riotgames",
        "com.activision",
        "com.garena",
        "com.mobile.legends",
        "com.pubg",
        "com.epicgames",
        "com.ea.gp",
        "com.roblox.client",
    )

    /** 无论如何都不参与判定：管理器自身、系统、已知正常工具。 */
    private val NEVER_ACCUSE = setOf(
        "com.huijui.sunext.edition",
        "com.android.shell",
        "com.topjohnwu.magisk",
        "me.weishu.kernelsu",
        "com.termux",
    )

    private data class Proc(val pid: Int, val ppid: Int, val name: String)

    suspend fun inspect(context: Context): Report = withContext(Dispatchers.IO) {
        val procs = listProcesses()
        if (procs.isEmpty()) {
            return@withContext Report(Verdict.CLEAR, 0, emptyList(), null)
        }

        val granted = AppSettings.grantedApps

        val cheatProcs = procs.filter { p ->
            val n = p.name.lowercase()
            NEVER_ACCUSE.none { n.startsWith(it) } && CHEAT_SIGNATURES.any { n.contains(it) }
        }

        if (cheatProcs.isEmpty()) {
            return@withContext Report(Verdict.CLEAR, 0, emptyList(), null)
        }

        val gameProcs = procs.filter { p ->
            val n = p.name.lowercase()
            ONLINE_GAME_SIGNATURES.any { n.startsWith(it) || n.contains(it) }
        }

        // 真正用了 Root 的硬证据：su 进程的父进程就是作弊工具
        val cheatPids = cheatProcs.map { it.pid }.toSet()
        val suFromCheat = procs.filter {
            it.name.contains("su") && it.ppid in cheatPids
        }

        val grantedCheat = cheatProcs.firstOrNull { p ->
            (granted.firstOrNull { g -> p.name.startsWith(g) || g.startsWith(p.name) }) != null
        }

        val signals = mutableListOf<Signal>()
        val cheat = cheatProcs.first()

        signals += Signal("S1", 3, "作弊工具进程运行中：${cheat.name}")
        if (suFromCheat.isNotEmpty()) {
            signals += Signal("S2", 3, "该工具派生了 su 子进程（PID ${suFromCheat.first().pid}），确认已获取 Root")
        }
        if (gameProcs.isNotEmpty()) {
            signals += Signal("S3", 3, "联机游戏运行中：${gameProcs.first().name}")
        }
        if (grantedCheat != null) {
            signals += Signal("S4", 1, "该工具在 Root 授权名单里")
        }

        val score = signals.sumOf { it.weight }

        // 三条件缺一不可：作弊工具在跑 + 游戏在跑 + 确实拿到 Root
        val hasRootProof = suFromCheat.isNotEmpty() || grantedCheat != null
        val guilty = score >= 9 && gameProcs.isNotEmpty() && hasRootProof

        Report(
            verdict = when {
                guilty -> Verdict.GUILTY
                score >= 3 -> Verdict.WATCH
                else -> Verdict.CLEAR
            },
            score = score,
            signals = signals,
            target = cheat.name
        )
    }

    /**
     * 一次 `ps` 拿到全部进程与父子关系。
     * `-o ARGS` 而不是 `-o NAME`：comm 会被截断到 15 字符，包名基本都超了。
     */
    private suspend fun listProcesses(): List<Proc> {
        val result = HuijuiShell.exec("ps -A -o PID,PPID,ARGS 2>/dev/null", timeoutMs = 4_000L)
        if (result.out.isEmpty()) return emptyList()

        return result.out.mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"), limit = 3)
            if (parts.size < 2) return@mapNotNull null
            val pid = parts[0].toIntOrNull() ?: return@mapNotNull null
            val ppid = parts[1].toIntOrNull() ?: return@mapNotNull null
            val args = parts.getOrElse(2) { "" }
            val name = args.substringBefore(' ').trim()
            if (name.isEmpty()) null else Proc(pid, ppid, name)
        }
    }
}
