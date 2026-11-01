package com.huijui.sunext.edition.core.root

import android.content.Context
import java.io.File

object HuijuiGodLoader {
    
    private const val TAG = "HuiJuiGodLoader"

    // 检查底层是否已经跑起来
    fun isKernelRunning(): Boolean {
        return File("/dev/huijui").exists()
    }

    // 🚀 二合一核心：LKM 直接热加载（无视GKI，无视Magisk）
    suspend fun loadLkmDirectly(context: Context): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            // 1. 根据内核版本匹配对应的 ko
            val koName = KernelVersionMatcher.getExpectedKoFileName() ?: return@withContext false
            val localKo = File(context.cacheDir, koName)
            val localSud = File(context.cacheDir, "huijuisud")

            // 2. 提取资产文件
            context.assets.open("exploit/$koName").use { input ->
                localKo.outputStream().use { output -> input.copyTo(output) }
            }
            context.assets.open("exploit/huijuisud").use { input ->
                localSud.outputStream().use { output -> input.copyTo(output) }
            }

            // 3. 核心加载命令（强行 insmod，不需要改 init_boot，不需要面具）
            val cmd = arrayOf(
                "su", "-c",
                "cp ${localKo.absolutePath} /data/local/tmp/ && " +
                "cp ${localSud.absolutePath} /data/local/tmp/ && " +
                "chmod 644 /data/local/tmp/$koName && " +
                "insmod /data/local/tmp/$koName && " +
                "chmod 755 /data/local/tmp/huijuisud && " +
                "nohup /data/local/tmp/huijuisud > /dev/null 2>&1 &"
            )
            
            val process = Runtime.getRuntime().exec(cmd)
            val exitCode = process.waitFor()
            
            // 验证是否成功拉起了 /dev/huijui 节点
            Thread.sleep(1000) // 给内核一点反应时间
            return@withContext exitCode == 0 && File("/dev/huijui").exists()
            
        } catch (e: Exception) {
            return@withContext false
        }
    }

    // 🚀 降级方案：如果没有 Root，则走镜像修补
    suspend fun fallbackToPatch(context: Context, bootImgPath: String): Boolean {
        // 这里直接调用你之前写好的 ImagePatcher 或 DirectInstaller
        // 因为那个过程比较长，我们只做状态返回
        return false 
    }

    // 统一入口
    suspend fun initGodMode(context: Context): String {
        if (isKernelRunning()) {
            return "✅ 内核模块已在工作中 [LKM]"
        }

        // 尝试 LKM 强行注入
        if (loadLkmDirectly(context)) {
            return "✅ 纯血 LKM 动态注入成功，God Mode 已启动！"
        }

        return "❌ 注入失败。请确认已获取 ReSukiSU/KernelSU Root 权限。"
    }
}
