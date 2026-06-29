package com.parsehub.app.data

import android.content.Context
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
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

class ParseRepository(private val context: Context) {
    private val TAG = "ParseRepository"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val mobileUA =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"

    private val desktopUA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    suspend fun parse(url: String): ParseResult = withContext(Dispatchers.IO) {
        try {
            // 从文本中提取 URL
            val extractedUrl = extractUrlFromText(url.trim())
            val trimmedUrl = extractedUrl ?: url.trim()
            val platform = detectPlatform(trimmedUrl)
            Log.d(TAG, "检测平台: $platform, URL: $trimmedUrl")

            val result = when (platform) {
                "douyin" -> parseDouyin(trimmedUrl)
                "bilibili" -> parseBilibili(trimmedUrl)
                "kuaishou" -> parseKuaishou(trimmedUrl)
                "weibo" -> parseWeibo(trimmedUrl)
                "xiaohongshu" -> parseXiaohongshu(trimmedUrl)
                else -> parseGeneric(trimmedUrl, platform)
            }
            Log.d(TAG, "解析完成: success=${result.isSuccess}, mediaCount=${result.media.size}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "解析异常", e)
            ParseResult(
                error = "解析出错: ${e.javaClass.simpleName} - ${e.message}"
            )
        }
    }

    private fun extractUrlFromText(text: String): String? {
        val urlPattern = "https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+".toRegex(RegexOption.IGNORE_CASE)
        val match = urlPattern.find(text)
        return match?.value
    }

    private fun detectPlatform(url: String): String {
        return when {
            url.contains("douyin", ignoreCase = true) || url.contains("iesdouyin", ignoreCase = true) -> "douyin"
            url.contains("kuaishou", ignoreCase = true) || url.contains("gifshow", ignoreCase = true) -> "kuaishou"
            url.contains("bilibili", ignoreCase = true) || url.contains("b23.tv", ignoreCase = true) -> "bilibili"
            url.contains("weibo", ignoreCase = true) -> "weibo"
            url.contains("xiaohongshu", ignoreCase = true) || url.contains("xhslink", ignoreCase = true) -> "xiaohongshu"
            url.contains("youtube", ignoreCase = true) || url.contains("youtu.be", ignoreCase = true) -> "youtube"
            url.contains("twitter", ignoreCase = true) || url.contains("x.com", ignoreCase = true) -> "twitter"
            url.contains("tieba", ignoreCase = true) -> "tieba"
            else -> "unknown"
        }
    }

    // ========== 抖音解析 ==========
    private fun parseDouyin(url: String): ParseResult {
        // 先尝试直接从 URL 提取视频ID
        val videoIdFromUrl = extractDouyinId(url)

        // 获取重定向后的真实 URL
        val realUrl = safeGetFinalUrl(url, mobileUA) ?: url
        Log.d(TAG, "抖音真实URL: $realUrl")

        val videoId = videoIdFromUrl ?: extractDouyinId(realUrl)
        if (videoId == null) {
            return ParseResult(
                platform = "抖音",
                error = "无法从链接中提取视频ID，请确认是有效的抖音分享链接"
            )
        }
        Log.d(TAG, "抖音视频ID: $videoId")

        // 方案1：从网页 HTML 提取 RENDER_DATA
        try {
            val result = parseDouyinByWeb(realUrl)
            if (result != null && result.isSuccess) return result
        } catch (e: Exception) {
            Log.e(TAG, "抖音网页解析失败", e)
        }

        // 方案2：尝试旧版 API
        try {
            val apiUrl = "https://www.iesdouyin.com/aweme/v1/web/aweme/detail/?aweme_id=$videoId"
            val json = safeFetchJson(apiUrl, mapOf(
                "User-Agent" to mobileUA,
                "Referer" to "https://www.douyin.com/"
            ))
            if (json != null) {
                val statusCode = json.optInt("status_code", -1)
                if (statusCode == 0) {
                    val aweme = json.optJSONObject("aweme_detail")
                    if (aweme != null) {
                        return parseDouyinAweme(aweme)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "抖音 API 解析失败", e)
        }

        return ParseResult(
            platform = "抖音",
            error = "抖音解析失败，可能是链接无效或平台限制"
        )
    }

    private fun parseDouyinByWeb(url: String): ParseResult? {
        val html = safeFetchHtml(url, mapOf(
            "User-Agent" to mobileUA,
            "Accept-Language" to "zh-CN,zh;q=0.9"
        )) ?: return null

        val renderData = extractRenderData(html)
        if (renderData == null) {
            Log.d(TAG, "未找到 RENDER_DATA，HTML 长度: ${html.length}")
            return null
        }

        return try {
            var aweme: JSONObject? = null

            // 路径1: aweme.detail.awemeInfo[0] (旧版)
            val awemeObj = renderData.optJSONObject("aweme")
            val detail = awemeObj?.optJSONObject("detail")
            val awemeInfo = detail?.optJSONArray("awemeInfo")
                ?.optJSONObject(0)
            if (awemeInfo != null) aweme = awemeInfo

            // 路径2: loaderData 中的 videoInfoRes.item_list[0] (新版 _ROUTER_DATA)
            if (aweme == null) {
                val loaderData = renderData.optJSONObject("loaderData")
                if (loaderData != null) {
                    val keys = loaderData.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val pageData = loaderData.optJSONObject(key)
                        val videoInfoRes = pageData?.optJSONObject("videoInfoRes")
                        val itemList = videoInfoRes?.optJSONArray("item_list")
                        if (itemList != null && itemList.length() > 0) {
                            aweme = itemList.getJSONObject(0)
                            break
                        }
                    }
                }
            }

            // 路径3: CURRENT_DATA / currentData
            if (aweme == null) {
                val currentData = renderData.optJSONObject("CURRENT_DATA")
                    ?: renderData.optJSONObject("currentData")
                if (currentData != null) aweme = currentData
            }

            if (aweme == null) {
                Log.d(TAG, "在 RENDER_DATA 中未找到视频数据")
                null
            } else {
                parseDouyinAweme(aweme)
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析 RENDER_DATA 失败", e)
            null
        }
    }

    private fun parseDouyinAweme(aweme: JSONObject): ParseResult {
        val desc = aweme.optString("desc", "")
        val author = aweme.optJSONObject("author")?.optString("nickname", "")
        val mediaList = mutableListOf<MediaInfo>()

        // 视频
        val video = aweme.optJSONObject("video")
        if (video != null) {
            val playAddr = video.optJSONObject("play_addr")
            val urlList = playAddr?.optJSONArray("url_list")
                ?: playAddr?.optJSONArray("urlList")
            val videoUrl = if (urlList != null && urlList.length() > 0) {
                URLDecoder.decode(urlList.getString(0), "UTF-8")
            } else null

            val cover = video.optJSONObject("cover")
            val coverUrl = cover?.optJSONArray("url_list")
                ?.let { if (it.length() > 0) it.getString(0) else null }
                ?: cover?.optJSONArray("urlList")
                    ?.let { if (it.length() > 0) it.getString(0) else null }

            if (videoUrl != null) {
                // 去除水印: playwm -> play
                val noWatermarkUrl = videoUrl.replace("/playwm/", "/play/")
                mediaList.add(
                    MediaInfo(
                        type = "video",
                        url = noWatermarkUrl,
                        thumbUrl = coverUrl,
                        duration = aweme.optInt("duration"),
                        ext = "mp4"
                    )
                )
            }
        }

        // 图文
        val images = aweme.optJSONArray("images")
        if (images != null && images.length() > 0) {
            for (i in 0 until images.length()) {
                val img = images.getJSONObject(i)
                val urlList = img.optJSONArray("url_list")
                    ?: img.optJSONArray("urlList")
                val imgUrl = if (urlList != null && urlList.length() > 0) urlList.getString(0) else null
                if (imgUrl != null) {
                    mediaList.add(
                        MediaInfo(
                            type = "image",
                            url = imgUrl,
                            thumbUrl = imgUrl,
                            ext = "jpg"
                        )
                    )
                }
            }
        }

        return ParseResult(
            platform = "抖音",
            type = if (images != null && images.length() > 0) "image" else "video",
            title = desc.take(80),
            content = desc,
            author = author,
            media = mediaList,
            error = if (mediaList.isEmpty()) "未找到可下载的媒体文件" else null
        )
    }

    private fun extractRenderData(html: String): JSONObject? {
        try {
            // 匹配多种格式
            val patterns = listOf(
                "<script id=\"RENDER_DATA\"[^>]*>(.*?)</script>".toRegex(RegexOption.DOT_MATCHES_ALL),
                "window\\.__RENDER_DATA__\\s*=\\s*['\"](.+?)['\"];".toRegex(),
                "window\\.__RENDER_DATA__\\s*=\\s*(\\{.*?\\});".toRegex(RegexOption.DOT_MATCHES_ALL),
                "window\\.__INITIAL_STATE__\\s*=\\s*(\\{.*?\\});".toRegex(RegexOption.DOT_MATCHES_ALL),
                "window\\._ROUTER_DATA\\s*=\\s*(\\{.*?\\})</script>".toRegex(RegexOption.DOT_MATCHES_ALL),
                "window\\._SSR_DATA\\s*=\\s*(\\{.*?\\});".toRegex(RegexOption.DOT_MATCHES_ALL)
            )

            for (pattern in patterns) {
                val match = pattern.find(html)
                if (match != null) {
                    var jsonStr = match.groupValues[1]
                    try {
                        jsonStr = URLDecoder.decode(jsonStr, "UTF-8")
                    } catch (_: Exception) {}
                    try {
                        return JSONObject(jsonStr)
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "提取 RENDER_DATA 异常", e)
        }
        return null
    }

    // ========== B站解析 ==========
    private fun parseBilibili(url: String): ParseResult {
        // 直接从原始 URL 提取 BV 号
        var bvid = extractBvid(url)

        // 如果没提取到，再从重定向 URL 提取
        if (bvid == null) {
            val realUrl = safeGetFinalUrl(url, desktopUA) ?: url
            bvid = extractBvid(realUrl)
        }

        if (bvid == null) {
            return ParseResult(
                platform = "B站",
                error = "无法提取BV号，请确认是有效的 B 站视频链接"
            )
        }
        Log.d(TAG, "B站 BV号: $bvid")

        // 获取视频信息
        val apiUrl = "https://api.bilibili.com/x/web-interface/view?bvid=$bvid"
        val json = safeFetchJson(apiUrl, mapOf(
            "User-Agent" to desktopUA,
            "Referer" to "https://www.bilibili.com/"
        )) ?: return ParseResult(
            platform = "B站",
            error = "无法连接 B 站 API，请检查网络"
        )

        val code = json.optInt("code", -1)
        if (code != 0) {
            return ParseResult(
                platform = "B站",
                error = "B 站 API 返回错误: ${json.optString("message", "未知错误")} (code=$code)"
            )
        }

        val data = json.optJSONObject("data")
            ?: return ParseResult(
                platform = "B站",
                error = "视频数据为空"
            )

        val title = data.optString("title", "")
        val desc = data.optString("desc", "")
        val pic = data.optString("pic", "")
        val owner = data.optJSONObject("owner")?.optString("name", "")
        val duration = data.optInt("duration")
        val cid = data.optInt("cid")

        val mediaList = mutableListOf<MediaInfo>()
        var videoUrl: String? = null

        // 尝试获取播放地址
        if (cid > 0) {
            try {
                val playApi = "https://api.bilibili.com/x/player/playurl?bvid=$bvid&cid=$cid&qn=16&platform=html5&high_quality=1"
                val playJson = safeFetchJson(playApi, mapOf(
                    "User-Agent" to desktopUA,
                    "Referer" to "https://www.bilibili.com/video/$bvid"
                ))

                if (playJson != null && playJson.optInt("code", -1) == 0) {
                    val playData = playJson.optJSONObject("data")
                    val durl = playData?.optJSONArray("durl")
                    if (durl != null && durl.length() > 0) {
                        videoUrl = durl.getJSONObject(0).optString("url")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "获取 B 站播放地址失败", e)
            }
        }

        mediaList.add(
            MediaInfo(
                type = "video",
                url = videoUrl,
                thumbUrl = pic,
                duration = duration,
                ext = "mp4"
            )
        )

        return ParseResult(
            platform = "B站",
            type = "video",
            title = title,
            content = desc,
            author = owner,
            media = mediaList,
            error = if (videoUrl == null) "视频播放地址获取失败（可能需要登录），仅可预览封面" else null
        )
    }

    // ========== 其他平台 ==========
    private fun parseKuaishou(url: String): ParseResult {
        return ParseResult(
            platform = "快手",
            error = "快手解析暂不支持"
        )
    }

    private fun parseWeibo(url: String): ParseResult {
        return ParseResult(
            platform = "微博",
            error = "微博解析暂不支持"
        )
    }

    private fun parseXiaohongshu(url: String): ParseResult {
        val realUrl = safeGetFinalUrl(url, mobileUA) ?: url
        Log.d(TAG, "小红书真实URL: $realUrl")

        val noteId = extractNoteId(realUrl)
        Log.d(TAG, "小红书笔记ID: $noteId")

        val html = safeFetchHtml(realUrl, mapOf(
            "User-Agent" to mobileUA,
            "Accept-Language" to "zh-CN,zh;q=0.9"
        )) ?: return ParseResult(
            platform = "小红书",
            error = "无法加载小红书页面"
        )

        val initialState = extractInitialState(html)
        if (initialState == null) {
            Log.d(TAG, "未找到 __INITIAL_STATE__")
            return ParseResult(
                platform = "小红书",
                error = "小红书解析失败，可能需要登录或链接已失效"
            )
        }

        return try {
            val note = initialState.optJSONObject("note")
            val noteDetailMap = note?.optJSONObject("noteDetailMap")

            var targetNote: JSONObject? = null

            // 如果有 noteId，直接从 map 中找
            if (noteId != null && noteDetailMap != null) {
                val noteDetail = noteDetailMap.optJSONObject(noteId)
                if (noteDetail != null) {
                    targetNote = noteDetail.optJSONObject("note")
                }
            }

            // 如果没找到，遍历 map 找第一个
            if (targetNote == null && noteDetailMap != null) {
                val keys = noteDetailMap.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val detail = noteDetailMap.optJSONObject(key)
                    val n = detail?.optJSONObject("note")
                    if (n != null) {
                        targetNote = n
                        break
                    }
                }
            }

            // 如果还是没有，从推荐流的第一条里拿（首页时的 fallback）
            if (targetNote == null) {
                val homeFeed = initialState.optJSONObject("home")
                    ?: initialState.optJSONObject("feeds")
                val feeds = homeFeed?.optJSONArray("feeds")
                    ?: homeFeed?.optJSONArray("feedList")
                if (feeds != null && feeds.length() > 0) {
                    val firstFeed = feeds.getJSONObject(0)
                    val noteCard = firstFeed.optJSONObject("noteCard")
                        ?: firstFeed.optJSONObject("note_card")
                    if (noteCard != null) {
                        targetNote = noteCard
                    }
                }
            }

            if (targetNote == null) {
                ParseResult(
                    platform = "小红书",
                    error = "未找到笔记内容，可能链接已失效或需要登录"
                )
            } else {
                parseXhsNote(targetNote)
            }
        } catch (e: Exception) {
            Log.e(TAG, "小红书解析失败", e)
            ParseResult(
                platform = "小红书",
                error = "解析出错: ${e.message}"
            )
        }
    }

    private fun parseXhsNote(note: JSONObject): ParseResult {
        val title = note.optString("displayTitle", "")
            .ifEmpty { note.optString("title", "") }
        val desc = note.optString("desc", "")
            .ifEmpty { note.optString("description", "") }

        val user = note.optJSONObject("user")
        val author = user?.optString("nickname", "")
            ?: user?.optString("nickName", "")

        val mediaList = mutableListOf<MediaInfo>()

        // 图片笔记
        val imageList = note.optJSONArray("imageList")
            ?: note.optJSONArray("image_list")
            ?: note.optJSONArray("images")

        if (imageList != null && imageList.length() > 0) {
            for (i in 0 until imageList.length()) {
                val img = imageList.optJSONObject(i) ?: continue
                val infoList = img.optJSONArray("infoList")
                    ?: img.optJSONArray("info_list")

                var imgUrl: String? = null
                var thumbUrl: String? = null

                if (infoList != null && infoList.length() > 0) {
                    for (j in 0 until infoList.length()) {
                        val info = infoList.optJSONObject(j) ?: continue
                        val url = info.optString("url", "")
                        if (url.isNotEmpty()) {
                            if (imgUrl == null) imgUrl = url
                            thumbUrl = url
                        }
                    }
                }

                // 备用：urlPre / urlDefault
                if (imgUrl.isNullOrEmpty()) {
                    val urlPre = img.optString("urlPre", "")
                    val urlDefault = img.optString("urlDefault", "")
                    val url = img.optString("url", "")
                    imgUrl = when {
                        urlPre.isNotEmpty() -> urlPre
                        urlDefault.isNotEmpty() -> urlDefault
                        url.isNotEmpty() -> url
                        else -> null
                    }
                    thumbUrl = imgUrl
                }

                if (!imgUrl.isNullOrEmpty()) {
                    mediaList.add(
                        MediaInfo(
                            type = "image",
                            url = imgUrl,
                            thumbUrl = thumbUrl ?: imgUrl,
                            width = img.optInt("width"),
                            height = img.optInt("height"),
                            ext = "jpg"
                        )
                    )
                }
            }
        }

        // 视频笔记
        val video = note.optJSONObject("video")
            ?: note.optJSONObject("video_info")
        if (video != null) {
            val media = video.optJSONObject("media")
            var videoUrl = media?.optJSONObject("stream")
                ?.optJSONObject("h264")
                ?.optJSONArray("master_url")
                ?.optString(0, "")

            if (videoUrl.isNullOrEmpty()) {
                val v = video.optString("url", "")
                val v2 = video.optString("video_url", "")
                videoUrl = when {
                    v.isNotEmpty() -> v
                    v2.isNotEmpty() -> v2
                    else -> null
                }
            }

            val cover = video.optJSONObject("cover")
            val coverUrl = cover?.let {
                val u = it.optString("url", "")
                val ud = it.optString("urlDefault", "")
                val up = it.optString("urlPre", "")
                when {
                    u.isNotEmpty() -> u
                    ud.isNotEmpty() -> ud
                    up.isNotEmpty() -> up
                    else -> null
                }
            }

            val duration = video.optJSONObject("capa")?.optInt("duration")

            if (!videoUrl.isNullOrEmpty()) {
                mediaList.add(
                    MediaInfo(
                        type = "video",
                        url = videoUrl,
                        thumbUrl = coverUrl,
                        duration = duration,
                        ext = "mp4"
                    )
                )
            }
        }

        // 如果没有找到媒体，但有 cover，就把 cover 当缩略图
        if (mediaList.isEmpty()) {
            val cover = note.optJSONObject("cover")
            val coverUrl = cover?.let {
                val ud = it.optString("urlDefault", "")
                val up = it.optString("urlPre", "")
                when {
                    ud.isNotEmpty() -> ud
                    up.isNotEmpty() -> up
                    else -> null
                }
            }
            if (!coverUrl.isNullOrEmpty()) {
                mediaList.add(
                    MediaInfo(
                        type = "image",
                        url = coverUrl,
                        thumbUrl = coverUrl,
                        ext = "jpg"
                    )
                )
            }
        }

        return ParseResult(
            platform = "小红书",
            type = if (video != null) "video" else "image",
            title = title,
            content = desc,
            author = author,
            media = mediaList,
            error = if (mediaList.isEmpty()) "未找到可下载的媒体文件" else null
        )
    }

    private fun extractNoteId(url: String): String? {
        val patterns = listOf(
            "/explore/([a-zA-Z0-9]+)".toRegex(),
            "/discovery/item/([a-zA-Z0-9]+)".toRegex(),
            "note_id=([a-zA-Z0-9]+)".toRegex(),
            "noteId=([a-zA-Z0-9]+)".toRegex(),
            "/note/([a-zA-Z0-9]+)".toRegex(),
            "xhslink\\.com/[^/]+/([a-zA-Z0-9]+)".toRegex()
        )
        for (pattern in patterns) {
            val match = pattern.find(url)
            if (match != null) return match.groupValues[1]
        }
        return null
    }

    private fun extractInitialState(html: String): JSONObject? {
        return try {
            val pattern = "window\\.__INITIAL_STATE__\\s*=\\s*(\\{.*?\\})\\s*</script>".toRegex(RegexOption.DOT_MATCHES_ALL)
            val match = pattern.find(html) ?: return null
            var jsonStr = match.groupValues[1]

            // 处理 undefined 和 NaN 等非标准 JSON 值
            jsonStr = jsonStr.replace(":undefined", ":null")
                .replace(" undefined", " null")
                .replace(":NaN", ":null")

            JSONObject(jsonStr)
        } catch (e: Exception) {
            Log.e(TAG, "解析 __INITIAL_STATE__ 失败", e)
            null
        }
    }

    private fun parseGeneric(url: String, platform: String): ParseResult {
        val platformName = when (platform) {
            "youtube" -> "YouTube"
            "twitter" -> "Twitter"
            "tieba" -> "贴吧"
            else -> "未知平台"
        }

        return ParseResult(
            platform = platformName,
            error = "$platformName 解析功能开发中，目前支持抖音和 B 站"
        )
    }

    // ========== 工具方法 ==========
    private fun safeGetFinalUrl(url: String, ua: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", ua)
                .head()
                .build()

            client.newCall(request).execute().use { response ->
                response.request.url.toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取重定向 URL 失败: ${e.message}")
            null
        }
    }

    private fun extractDouyinId(url: String): String? {
        val patterns = listOf(
            "/video/(\\d+)".toRegex(),
            "/note/(\\d+)".toRegex(),
            "aweme_id=(\\d+)".toRegex(),
            "itemId=(\\d+)".toRegex(),
            "video_id=(\\d+)".toRegex(),
            "/share/video/(\\d+)".toRegex(),
            "/share/note/(\\d+)".toRegex(),
            "douyin\\.com/video/(\\d+)".toRegex(),
            "iesdouyin\\.com/share/video/(\\d+)".toRegex()
        )
        for (pattern in patterns) {
            val match = pattern.find(url)
            if (match != null) return match.groupValues[1]
        }
        return null
    }

    private fun extractBvid(url: String): String? {
        val pattern = "(BV[0-9A-Za-z]{10})".toRegex()
        val match = pattern.find(url)
        return match?.groupValues?.get(1)
    }

    private fun safeFetchHtml(url: String, headers: Map<String, String> = emptyMap()): String? {
        return try {
            val requestBuilder = Request.Builder().url(url).get()
            for ((key, value) in headers) {
                requestBuilder.header(key, value)
            }
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "HTTP 请求失败: ${e.message}")
            null
        }
    }

    private fun safeFetchJson(url: String, headers: Map<String, String> = emptyMap()): JSONObject? {
        val body = safeFetchHtml(url, headers) ?: return null
        return try {
            JSONObject(body)
        } catch (e: Exception) {
            Log.e(TAG, "JSON 解析失败: ${body.take(100)}")
            null
        }
    }

    // ========== 下载相关 ==========
    suspend fun downloadMedia(
        media: MediaInfo,
        onProgress: ((Int) -> Unit)? = null
    ): String? = withContext(Dispatchers.IO) {
        val url = media.url ?: return@withContext null
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", desktopUA)
                .header("Referer", "https://www.douyin.com/")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "下载失败 HTTP ${response.code}")
                    return@withContext null
                }

                val body = response.body ?: return@withContext null
                val contentLength = body.contentLength()

                val cacheDir = File(context.cacheDir, "downloads")
                if (!cacheDir.exists()) cacheDir.mkdirs()

                val ext = media.ext ?: "mp4"
                val fileName = "parsehub_${System.currentTimeMillis()}.$ext"
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

                if (file.length() < 1024) {
                    Log.e(TAG, "下载文件过小: ${file.length()} bytes")
                    file.delete()
                    return@withContext null
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
