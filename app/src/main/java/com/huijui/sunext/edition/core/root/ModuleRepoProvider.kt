package com.huijui.sunext.edition.core.root

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object ModuleRepoProvider {
    private const val TAG = "HuiModuleRepo"
    
    private val REPO_URLS = listOf(
        "https://raw.githubusercontent.com/HuijuiSU-NEXT/Huijui_Module_Repo/main/modules.json",
        "https://cdn.jsdelivr.net/gh/HuijuiSU-NEXT/Huijui_Module_Repo@main/modules.json",
        "https://raw.gitmirror.com/HuijuiSU-NEXT/Huijui_Module_Repo/main/modules.json",
        "https://ghproxy.net/https://raw.githubusercontent.com/HuijuiSU-NEXT/Huijui_Module_Repo/main/modules.json"
    )

    // 🚀 增加历史版本数据结构
    data class ReleaseInfo(
        val version: String,
        val zipUrl: String,
        val size: Long,
        val updatedAt: String
    )

    data class RemoteModule(
        val id: String,
        val name: String,
        val author: String,
        val version: String,
        val description: String,
        val downloadCount: Int,
        val stars: Int,
        val updatedAt: String,
        val zipUrl: String,
        val sizeBytes: Long,
        val category: String,
        val trustLevel: String,
        val moduleType: String,
        val hasWebUi: Boolean,
        val releases: List<ReleaseInfo> // 🚀 存所有版本
    )

    suspend fun fetchModules(): List<RemoteModule> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        for (url in REPO_URLS) {
            try {
                val result = withTimeoutOrNull(5000L) { tryFetch(URL(url)) }
                if (result != null && result.isNotEmpty()) {
                    return@withContext result
                }
            } catch (e: Exception) {
                lastException = e
            }
        }
        throw lastException ?: Exception("所有镜像源均无法连接")
    }

    private fun tryFetch(url: URL): List<RemoteModule> {
        val result = mutableListOf<RemoteModule>()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 4000
        conn.readTimeout = 4000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")

        try {
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val jsonStr = reader.readText()
                reader.close()

                val jsonArray = JSONArray(jsonStr)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val releasesArray = obj.optJSONArray("releases")
                    val releaseList = mutableListOf<ReleaseInfo>()
                    var latestZipUrl = ""
                    var latestSize = 0L
                    var latestVersion = obj.optString("version", "未知")

                    if (releasesArray != null && releasesArray.length() > 0) {
                        for (r in 0 until releasesArray.length()) {
                            val rel = releasesArray.getJSONObject(r)
                            val zUrl = rel.optString("zipUrl", "")
                            val sz = rel.optLong("size", 0L)
                            val uTime = rel.optString("updatedAt", "")
                            val vName = rel.optString("name", "未知")
                            
                            if (zUrl.isNotBlank()) {
                                releaseList.add(ReleaseInfo(vName, zUrl, sz, uTime))
                            }
                        }
                        
                        // 取第一个作为最新版
                        if (releaseList.isNotEmpty()) {
                            latestZipUrl = releaseList[0].zipUrl
                            latestSize = releaseList[0].size
                            if (latestVersion == "未知") latestVersion = releaseList[0].version
                        }
                    }

                    if (latestZipUrl.isNotBlank()) {
                        result.add(
                            RemoteModule(
                                id = obj.optString("id"),
                                name = obj.optString("name"),
                                author = obj.optString("author"),
                                version = latestVersion,
                                description = obj.optString("description").trim(),
                                downloadCount = obj.optInt("downloadCount", 0),
                                stars = obj.optInt("stars", 0),
                                updatedAt = obj.optString("updatedAt"),
                                zipUrl = latestZipUrl,
                                sizeBytes = latestSize,
                                category = obj.optString("category", "utility"),
                                trustLevel = obj.optString("trust_level", "UNVERIFIED"),
                                moduleType = obj.optString("module_type", "standard"),
                                hasWebUi = obj.optBoolean("has_webui", false),
                                releases = releaseList
                            )
                        )
                    }
                }
            } else {
                throw Exception("HTTP ${conn.responseCode}")
            }
        } finally {
            conn.disconnect()
        }
        return result
    }
}
