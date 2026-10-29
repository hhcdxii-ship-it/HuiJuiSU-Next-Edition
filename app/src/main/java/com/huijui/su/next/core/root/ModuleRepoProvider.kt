package com.huijui.su.next.core.root

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object ModuleRepoProvider {
    private const val TAG = "HuiModuleRepo"
    const val DEFAULT_REPO_URL = "https://raw.githubusercontent.com/HuijuiSU-NEXT/Huijui_Module_Repo/main/modules.json"

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
        val hasWebUi: Boolean
    )

    suspend fun fetchModules(repoUrl: String = DEFAULT_REPO_URL): List<RemoteModule> = withContext(Dispatchers.IO) {
        val result = mutableListOf<RemoteModule>()
        try {
            val url = URL(repoUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val jsonStr = reader.readText()
                reader.close()

                val jsonArray = JSONArray(jsonStr)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val releases = obj.optJSONArray("releases")
                    var zipUrl = ""
                    var size = 0L
                    var latestVersion = obj.optString("version", "未知")

                    if (releases != null && releases.length() > 0) {
                        val latestRelease = releases.getJSONObject(0)
                        zipUrl = latestRelease.optString("zipUrl", "")
                        size = latestRelease.optLong("size", 0L)
                        if (latestVersion == "未知") {
                            latestVersion = latestRelease.optString("name", "未知")
                        }
                    }

                    if (zipUrl.isNotBlank()) {
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
                                zipUrl = zipUrl,
                                sizeBytes = size,
                                category = obj.optString("category", "utility"),
                                trustLevel = obj.optString("trust_level", "UNVERIFIED"),
                                moduleType = obj.optString("module_type", "standard"),
                                hasWebUi = obj.optBoolean("has_webui", false)
                            )
                        )
                    }
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "拉取失败: ${e.message}")
        }
        result
    }
}
