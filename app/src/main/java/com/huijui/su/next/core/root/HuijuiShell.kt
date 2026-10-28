package com.huijui.su.next.core.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * 🩸 纯血自研 Root 执行引擎
 * 绝对不依赖 libsu / Magisk / BakaSU 的任何 SDK
 * 直接通过 ProcessBuilder 拉起 su 进程，手写 IO 流进行通信
 */
object HuijuiShell {

    data class CommandResult(
        val isSuccess: Boolean,
        val out: List<String>,
        val err: List<String>
    )

    /**
     * 同步执行 Root 命令
     */
    suspend fun exec(command: String): CommandResult = withContext(Dispatchers.IO) {
        val outList = mutableListOf<String>()
        val errList = mutableListOf<String>()
        var exitCode = -1

        try {
            // 直接调用系统的 su，不经过任何第三方封装
            val process = ProcessBuilder("su").start()
            
            // 写入命令并关闭输入流
            val writer = OutputStreamWriter(process.outputStream)
            writer.write("$command\nexit\n")
            writer.flush()
            writer.close()

            // 读取标准输出
            val outReader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (outReader.readLine().also { line = it } != null) {
                line?.let { outList.add(it) }
            }
            outReader.close()

            // 读取错误输出
            val errReader = BufferedReader(InputStreamReader(process.errorStream))
            while (errReader.readLine().also { line = it } != null) {
                line?.let { errList.add(it) }
            }
            errReader.close()

            exitCode = process.waitFor()
        } catch (e: Exception) {
            errList.add(e.message ?: "Unknown Error")
        }

        return@withContext CommandResult(
            isSuccess = exitCode == 0,
            out = outList,
            err = errList
        )
    }

    /**
     * 检查当前 App 是否拥有 Root 权限
     * 原理：尝试执行 id 命令，看返回结果里有没有 uid=0(root)
     */
    suspend fun isRooted(): Boolean = withContext(Dispatchers.IO) {
        val result = exec("id")
        return@withContext result.isSuccess && result.out.any { it.contains("uid=0") }
    }
}
