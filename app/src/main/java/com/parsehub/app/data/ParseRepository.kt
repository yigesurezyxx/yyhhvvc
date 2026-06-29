package com.parsehub.app.data

import android.util.Log
import com.chaquo.python.Python
import com.chaquo.python.PyObject
import org.json.JSONArray
import org.json.JSONObject

class ParseRepository {
    private val TAG = "ParseRepository"
    
    private val python: Python by lazy { Python.getInstance() }
    private val parseBridge: PyObject by lazy { python.getModule("parse_bridge") }
    
    private fun callPython(method: String, vararg args: Any?): String {
        return try {
            val result = parseBridge.callAttr(method, *args)
            result.toString()
        } catch (e: Exception) {
            Log.e(TAG, "调用 Python 方法 $method 失败", e)
            JSONObject().put("error", e.message ?: "未知错误").toString()
        }
    }
    
    fun getSupportedPlatforms(): List<PlatformInfo> {
        val json = callPython("get_supported_platforms")
        return try {
            val array = JSONArray(json)
            val list = mutableListOf<PlatformInfo>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    PlatformInfo(
                        id = obj.optString("id"),
                        name = obj.optString("name")
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "解析平台列表失败", e)
            emptyList()
        }
    }
    
    fun getPlatform(url: String): PlatformInfo? {
        val json = callPython("get_platform", url)
        return try {
            val obj = JSONObject(json)
            if (obj.has("error")) {
                null
            } else {
                PlatformInfo(
                    id = obj.getString("id"),
                    name = obj.getString("name")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "检测平台失败", e)
            null
        }
    }
    
    fun parse(url: String, cookie: String? = null, proxy: String? = null): ParseResult {
        val json = callPython("parse", url, cookie, proxy)
        return parseResultFromJson(json)
    }
    
    fun getRawUrl(url: String, proxy: String? = null, cleanAll: Boolean = true): String? {
        val json = callPython("get_raw_url", url, proxy, cleanAll)
        return try {
            val obj = JSONObject(json)
            if (obj.has("error")) {
                null
            } else {
                obj.getString("raw_url")
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取原始 URL 失败", e)
            null
        }
    }
    
    fun download(
        url: String,
        cookie: String? = null,
        proxy: String? = null,
        saveMetadata: Boolean = false
    ): DownloadResult {
        val json = callPython("download", url, cookie, proxy, saveMetadata)
        return try {
            val obj = JSONObject(json)
            if (obj.has("error")) {
                DownloadResult(
                    outputDir = "",
                    media = emptyList(),
                    error = obj.getString("error")
                )
            } else {
                val mediaArray = obj.getJSONArray("media")
                val mediaList = mutableListOf<MediaInfo>()
                for (i in 0 until mediaArray.length()) {
                    val mediaObj = mediaArray.getJSONObject(i)
                    mediaList.add(
                        MediaInfo(
                            type = mediaObj.optString("type"),
                            url = mediaObj.optString("url"),
                            thumbUrl = mediaObj.optString("thumb_url"),
                            width = mediaObj.optInt("width").takeIf { it > 0 },
                            height = mediaObj.optInt("height").takeIf { it > 0 },
                            duration = mediaObj.optInt("duration").takeIf { it > 0 },
                            size = mediaObj.optLong("size").takeIf { it > 0 },
                            ext = mediaObj.optString("ext"),
                            localPath = mediaObj.optString("path")
                        )
                    )
                }
                DownloadResult(
                    outputDir = obj.getString("output_dir"),
                    media = mediaList
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析下载结果失败", e)
            DownloadResult("", emptyList(), e.message)
        }
    }
    
    fun getDownloadDir(): String {
        return callPython("get_download_dir").removeSurrounding("\"")
    }
    
    fun clearDownloads(): Boolean {
        val json = callPython("clear_downloads")
        return try {
            val obj = JSONObject(json)
            obj.optBoolean("success", false)
        } catch (e: Exception) {
            false
        }
    }
    
    private fun parseResultFromJson(json: String): ParseResult {
        return try {
            val obj = JSONObject(json)
            if (obj.has("error")) {
                ParseResult(
                    platform = null,
                    type = null,
                    title = "",
                    content = "",
                    rawUrl = null,
                    author = null,
                    avatar = null,
                    media = emptyList(),
                    error = obj.getString("error")
                )
            } else {
                val mediaArray = obj.optJSONArray("media") ?: JSONArray()
                val mediaList = mutableListOf<MediaInfo>()
                for (i in 0 until mediaArray.length()) {
                    val mediaObj = mediaArray.getJSONObject(i)
                    mediaList.add(
                        MediaInfo(
                            type = mediaObj.optString("type"),
                            url = mediaObj.optString("url"),
                            thumbUrl = mediaObj.optString("thumb_url"),
                            width = mediaObj.optInt("width").takeIf { it > 0 },
                            height = mediaObj.optInt("height").takeIf { it > 0 },
                            duration = mediaObj.optInt("duration").takeIf { it > 0 },
                            size = mediaObj.optLong("size").takeIf { it > 0 },
                            ext = mediaObj.optString("ext")
                        )
                    )
                }
                
                ParseResult(
                    platform = obj.optString("platform"),
                    type = obj.optString("type"),
                    title = obj.optString("title"),
                    content = obj.optString("content"),
                    rawUrl = obj.optString("raw_url"),
                    author = obj.optString("author"),
                    avatar = obj.optString("avatar"),
                    media = mediaList,
                    markdownContent = obj.optString("markdown_content")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析结果失败", e)
            ParseResult(
                platform = null,
                type = null,
                title = "",
                content = "",
                rawUrl = null,
                author = null,
                avatar = null,
                media = emptyList(),
                error = e.message
            )
        }
    }
    
    companion object {
        @Volatile
        private var instance: ParseRepository? = null
        
        fun getInstance(): ParseRepository {
            return instance ?: synchronized(this) {
                instance ?: ParseRepository().also { instance = it }
            }
        }
    }
}
