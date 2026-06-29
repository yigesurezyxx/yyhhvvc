package com.parsehub.app.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class ParseRepository(private val context: Context) {
    private val TAG = "ParseRepository"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val userAgent =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1"

    suspend fun parse(url: String): ParseResult = withContext(Dispatchers.IO) {
        try {
            val platform = detectPlatform(url)
            when (platform) {
                "douyin" -> parseDouyin(url)
                "kuaishou" -> parseKuaishou(url)
                "bilibili" -> parseBilibili(url)
                "weibo" -> parseWeibo(url)
                else -> parseGeneric(url)
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析失败", e)
            ParseResult(
                platform = null,
                type = null,
                title = "",
                content = "",
                media = emptyList(),
                error = e.message ?: "解析失败"
            )
        }
    }

    private fun detectPlatform(url: String): String {
        return when {
            url.contains("douyin.com") || url.contains("iesdouyin.com") -> "douyin"
            url.contains("kuaishou.com") || url.contains("gifshow.com") -> "kuaishou"
            url.contains("bilibili.com") || url.contains("b23.tv") -> "bilibili"
            url.contains("weibo.com") || url.contains("weibo.cn") -> "weibo"
            url.contains("xiaohongshu.com") || url.contains("xhslink.com") -> "xiaohongshu"
            url.contains("youtube.com") || url.contains("youtu.be") -> "youtube"
            url.contains("twitter.com") || url.contains("x.com") -> "twitter"
            url.contains("tieba.baidu.com") -> "tieba"
            else -> "unknown"
        }
    }

    private fun parseDouyin(url: String): ParseResult {
        val shareUrl = getFinalUrl(url)
        val videoId = extractDouyinId(shareUrl) ?: return ParseResult(
            platform = "抖音",
            type = null, title = "", content = "",
            media = emptyList(), error = "无法提取视频ID"
        )

        val apiUrl = "https://www.iesdouyin.com/web/api/v2/aweme/iteminfo/?item_ids=$videoId"
        val json = fetchJson(apiUrl, mapOf("User-Agent" to userAgent))

        val itemList = json.optJSONArray("item_list")
        if (itemList == null || itemList.length() == 0) {
            return ParseResult(
                platform = "抖音",
                type = null, title = "", content = "",
                media = emptyList(), error = "未找到视频信息"
            )
        }

        val item = itemList.getJSONObject(0)
        val desc = item.optString("desc", "")
        val author = item.optJSONObject("author")?.optString("nickname", "")

        val mediaList = mutableListOf<MediaInfo>()

        val video = item.optJSONObject("video")
        if (video != null) {
            val playAddr = video.optJSONObject("play_addr")
            val urlList = playAddr?.optJSONArray("url_list")
            val videoUrl = if (urlList != null && urlList.length() > 0) urlList.getString(0) else null

            val cover = video.optJSONObject("cover")
            val coverUrl = cover?.optJSONArray("url_list")?.let {
                if (it.length() > 0) it.getString(0) else null
            }

            if (videoUrl != null) {
                mediaList.add(
                    MediaInfo(
                        type = "video",
                        url = videoUrl,
                        thumbUrl = coverUrl,
                        width = video.optInt("width"),
                        height = video.optInt("height"),
                        duration = item.optInt("duration"),
                        ext = "mp4"
                    )
                )
            }
        }

        val images = item.optJSONArray("images")
        if (images != null && images.length() > 0) {
            for (i in 0 until images.length()) {
                val img = images.getJSONObject(i)
                val urlList = img.optJSONArray("url_list")
                val imgUrl = if (urlList != null && urlList.length() > 0) urlList.getString(0) else null
                if (imgUrl != null) {
                    mediaList.add(
                        MediaInfo(
                            type = "image",
                            url = imgUrl,
                            thumbUrl = imgUrl,
                            width = img.optInt("width"),
                            height = img.optInt("height"),
                            ext = "jpg"
                        )
                    )
                }
            }
        }

        return ParseResult(
            platform = "抖音",
            type = if (images != null && images.length() > 0) "image" else "video",
            title = desc.take(50),
            content = desc,
            author = author,
            media = mediaList
        )
    }

    private fun parseKuaishou(url: String): ParseResult {
        return ParseResult(
            platform = "快手",
            type = "video",
            title = "快手视频",
            content = "",
            media = listOf(
                MediaInfo(
                    type = "video",
                    url = url,
                    thumbUrl = null,
                    ext = "mp4"
                )
            ),
            error = "快手解析需要专用接口，暂仅支持基础展示"
        )
    }

    private fun parseBilibili(url: String): ParseResult {
        val finalUrl = getFinalUrl(url)
        val bvid = extractBvid(finalUrl) ?: return ParseResult(
            platform = "B站",
            type = null, title = "", content = "",
            media = emptyList(), error = "无法提取BV号"
        )

        val apiUrl = "https://api.bilibili.com/x/web-interface/view?bvid=$bvid"
        val json = fetchJson(apiUrl, mapOf(
            "User-Agent" to userAgent,
            "Referer" to "https://www.bilibili.com/"
        ))

        val data = json.optJSONObject("data")
        if (data == null) {
            return ParseResult(
                platform = "B站",
                type = null, title = "", content = "",
                media = emptyList(), error = json.optString("message", "获取视频信息失败")
            )
        }

        val title = data.optString("title", "")
        val desc = data.optString("desc", "")
        val pic = data.optString("pic", "")
        val owner = data.optJSONObject("owner")?.optString("name", "")
        val duration = data.optInt("duration")

        val cid = data.optInt("cid")

        val playApi = "https://api.bilibili.com/x/player/playurl?bvid=$bvid&cid=$cid&qn=64"
        val playJson = fetchJson(playApi, mapOf(
            "User-Agent" to userAgent,
            "Referer" to "https://www.bilibili.com/"
        ))

        var videoUrl: String? = null
        val playData = playJson.optJSONObject("data")
        val durl = playData?.optJSONArray("durl")
        if (durl != null && durl.length() > 0) {
            videoUrl = durl.getJSONObject(0).optString("url")
        }

        val mediaList = mutableListOf<MediaInfo>()
        if (videoUrl != null) {
            mediaList.add(
                MediaInfo(
                    type = "video",
                    url = videoUrl,
                    thumbUrl = pic,
                    duration = duration,
                    ext = "mp4"
                )
            )
        }

        return ParseResult(
            platform = "B站",
            type = "video",
            title = title,
            content = desc,
            author = owner,
            avatar = null,
            media = mediaList
        )
    }

    private fun parseWeibo(url: String): ParseResult {
        return ParseResult(
            platform = "微博",
            type = "video",
            title = "微博内容",
            content = "",
            media = listOf(
                MediaInfo(
                    type = "video",
                    url = url,
                    thumbUrl = null,
                    ext = "mp4"
                )
            ),
            error = "微博解析需要专用接口，暂仅支持基础展示"
        )
    }

    private fun parseGeneric(url: String): ParseResult {
        val platform = detectPlatform(url)
        val platformName = when (platform) {
            "xiaohongshu" -> "小红书"
            "youtube" -> "YouTube"
            "twitter" -> "Twitter"
            "tieba" -> "贴吧"
            else -> "未知平台"
        }

        return ParseResult(
            platform = platformName,
            type = "video",
            title = "$platformName 内容",
            content = url,
            media = listOf(
                MediaInfo(
                    type = "video",
                    url = url,
                    thumbUrl = null,
                    ext = "mp4"
                )
            ),
            error = "$platformName 解析功能开发中，暂仅支持抖音和 B 站"
        )
    }

    private fun getFinalUrl(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            return response.request.url.toString()
        }
    }

    private fun extractDouyinId(url: String): String? {
        val patterns = listOf(
            "/video/(\\d+)".toRegex(),
            "/note/(\\d+)".toRegex(),
            "itemId=(\\d+)".toRegex(),
            "aweme_id=(\\d+)".toRegex()
        )
        for (pattern in patterns) {
            val match = pattern.find(url)
            if (match != null) return match.groupValues[1]
        }
        return null
    }

    private fun extractBvid(url: String): String? {
        val pattern = "(BV[0-9A-Za-z]+)".toRegex()
        val match = pattern.find(url)
        return match?.groupValues?.get(1)
    }

    private fun fetchJson(url: String, headers: Map<String, String> = emptyMap()): JSONObject {
        val requestBuilder = Request.Builder().url(url).get()
        for ((key, value) in headers) {
            requestBuilder.header(key, value)
        }
        client.newCall(requestBuilder.build()).execute().use { response ->
            val body = response.body?.string() ?: "{}"
            return JSONObject(body)
        }
    }

    suspend fun downloadMedia(
        media: MediaInfo,
        onProgress: ((Int) -> Unit)? = null
    ): String? = withContext(Dispatchers.IO) {
        val url = media.url ?: return@withContext null
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null

                val body = response.body ?: return@withContext null
                val contentLength = body.contentLength()

                val cacheDir = File(context.cacheDir, "downloads")
                if (!cacheDir.exists()) cacheDir.mkdirs()

                val fileName = "parsehub_${System.currentTimeMillis()}.${media.ext ?: "mp4"}"
                val file = File(cacheDir, fileName)

                FileOutputStream(file).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalRead = 0L
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (contentLength > 0 && onProgress != null) {
                                val progress = (totalRead * 100 / contentLength).toInt()
                                onProgress(progress)
                            }
                        }
                    }
                }

                return@withContext file.absolutePath
            }
        } catch (e: Exception) {
            Log.e(TAG, "下载失败", e)
            null
        }
    }

    fun saveToGallery(file: File, type: String): Boolean {
        return try {
            val isVideo = type.contains("video", ignoreCase = true)
            val contentValues = android.content.ContentValues()

            val displayName = "ParseHub_${System.currentTimeMillis()}_${file.name}"
            val mimeType = if (isVideo) {
                when (file.extension.lowercase()) {
                    "mp4" -> "video/mp4"
                    "mov" -> "video/quicktime"
                    "mkv" -> "video/x-matroska"
                    else -> "video/*"
                }
            } else {
                when (file.extension.lowercase()) {
                    "jpg", "jpeg" -> "image/jpeg"
                    "png" -> "image/png"
                    "gif" -> "image/gif"
                    "webp" -> "image/webp"
                    "heic", "heif" -> "image/heif"
                    else -> "image/*"
                }
            }

            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, mimeType)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    if (isVideo) Environment.DIRECTORY_MOVIES + "/ParseHub"
                    else Environment.DIRECTORY_PICTURES + "/ParseHub"
                )
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val uri = if (isVideo) {
                context.contentResolver.insert(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues
                )
            } else {
                context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
                )
            } ?: return false

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                FileInputStream(file).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    companion object {
        @Volatile
        private var instance: ParseRepository? = null

        fun getInstance(context: Context): ParseRepository {
            return instance ?: synchronized(this) {
                instance ?: ParseRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
