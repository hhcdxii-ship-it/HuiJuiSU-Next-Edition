package com.huijui.sunext.edition.core.root

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 🚀 HUI 配置方舟 (Config Ark)
 * 
 * 独一无二的环境级快照引擎：
 * 1. 一键打包底层模块 + HUI 授权列表 + 性能配置 + 隐藏策略
 * 2. 支持跨设备迁移环境，新机秒还原
 * 3. 支持模块冲突后的紧急回滚
 */
object ArkSnapshotManager {

    const val ARK_DIR = "/sdcard/HuiJuiSU/Ark"

    data class SnapshotInfo(
        val fileName: String,
        val path: String,
        val timestamp: Long,
        val sizeMb: String,
        val hasConfig: Boolean
    )

    /**
     * 获取所有快照列表
     */
    suspend fun listSnapshots(): List<SnapshotInfo> = withContext(Dispatchers.IO) {
        val list = mutableListOf<SnapshotInfo>()
        HuijuiShell.exec("mkdir -p $ARK_DIR")
        
        val result = HuijuiShell.exec("ls -l $ARK_DIR/*.huiark 2>/dev/null")
        if (!result.isSuccess) return@withContext list

        val timeFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        
        for (line in result.out) {
            val parts = line.split("\\s+".toRegex())
            if (parts.size >= 8) {
                val sizeBytes = parts[4].toLongOrNull() ?: 0L
                val sizeMb = "%.1f MB".format(sizeBytes / 1024f / 1024f)
                val fileName = parts.last().substringAfterLast("/")
                val path = "$ARK_DIR/$fileName"
                
                // 从文件名解析时间
                var timestamp = 0L
                val dateStr = fileName.removePrefix("ark_").removeSuffix(".huiark")
                runCatching { timestamp = timeFormat.parse(dateStr)?.time ?: 0L }

                // 简单判断是否包含配置（如果是完整包，体积通常 > 2MB，或者我们可以通过内部判断，这里简化为体积判断）
                val hasConfig = sizeBytes > 1024 * 1024 * 2

                list.add(SnapshotInfo(fileName, path, timestamp, sizeMb, hasConfig))
            }
        }
        list.sortedByDescending { it.timestamp }
    }

    /**
     * 创建快照
     * @param includeConfig 是否包含 HUI 的偏好设置和 Root 授权
     */
    suspend fun createSnapshot(context: Context, includeConfig: Boolean): Result<String> = withContext(Dispatchers.IO) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "ark_$timestamp.huiark"
            val arkPath = "$ARK_DIR/$fileName"
            val tmpDir = "/data/local/tmp/hui_ark_tmp"

            // 1. 准备临时目录
            HuijuiShell.exec("rm -rf $tmpDir && mkdir -p $tmpDir/data")

            // 2. 打包模块目录
            HuijuiShell.exec("tar -czf $tmpDir/data/modules.tar.gz -C /data/adb modules huijuisu/modules 2>/dev/null")

            // 3. 打包配置 (SharedPreferences)
            if (includeConfig) {
                val prefsPath = context.filesDir.parentFile?.absolutePath + "/shared_prefs/huijui_next_prefs.xml"
                HuijuiShell.exec("cp $prefsPath $tmpDir/data/ 2>/dev/null")
                HuijuiShell.exec("echo 'true' > $tmpDir/data/has_config")
            }

            // 4. 打包成最终的 .huiark
            HuijuiShell.exec("mkdir -p $ARK_DIR")
            val buildResult = HuijuiShell.exec("tar -czf $arkPath -C $tmpDir data 2>&1")
            HuijuiShell.exec("rm -rf $tmpDir")

            if (buildResult.isSuccess) Result.success(fileName)
            else Result.failure(Exception("打包失败：${buildResult.err.joinToString("")}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 还原快照
     */
    suspend fun restoreSnapshot(arkPath: String, context: Context): Result<String> = withContext(Dispatchers.IO) {
        try {
            val tmpDir = "/data/local/tmp/hui_ark_restore"
            HuijuiShell.exec("rm -rf $tmpDir && mkdir -p $tmpDir")

            // 1. 解包
            val unpackRes = HuijuiShell.exec("tar -xzf $arkPath -C $tmpDir 2>&1")
            if (!unpackRes.isSuccess) return@withContext Result.failure(Exception("解包失败"))

            // 2. 还原模块
            HuijuiShell.exec("tar -xzf $tmpDir/data/modules.tar.gz -C /data/adb 2>&1")

            // 3. 还原配置
            val hasConfig = HuijuiShell.exec("test -f $tmpDir/data/has_config && echo yes").out.joinToString("").contains("yes")
            if (hasConfig) {
                val targetPrefs = context.filesDir.parentFile?.absolutePath + "/shared_prefs/"
                HuijuiShell.exec("mkdir -p $targetPrefs")
                HuijuiShell.exec("cp $tmpDir/data/huijui_next_prefs.xml $targetPrefs 2>/dev/null")
                HuijuiShell.exec("chown -R $(stat -c '%u:%g' $targetPrefs) $targetPrefs")
            }

            // 4. 触发内核热重载
            HuijuiShell.exec("echo 'RELOAD_MODULES' > /proc/hui_config")
            HuijuiShell.exec("rm -rf $tmpDir")

            Result.success("方舟还原成功！已恢复模块与配置，建议重启设备以完全生效。")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 删除快照
     */
    suspend fun deleteSnapshot(path: String): Boolean = withContext(Dispatchers.IO) {
        HuijuiShell.exec("rm -f '$path' && echo ok").out.joinToString("").contains("ok")
    }
}
