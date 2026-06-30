package com.parsehub.app.data

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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

    // 内存级 CookieJar，自动管理重定向间的 cookie（XHS 反爬依赖 cookie）
    private val cookieJar = object : okhttp3.CookieJar {
        private val store = mutableMapOf<String, List<okhttp3.Cookie>>()

        @Synchronized
        override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
            store[url.host] = (store[url.host] ?: emptyList()) + cookies
        }

        @Synchronized
        override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> {
            return store[url.host] ?: emptyList()
        }
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .cookieJar(cookieJar)
            .build()
    }

    private val mobileUA =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"

    private val desktopUA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36"

    private val weiboSubCookie =
        "SUB=_2AkMR47Mlf8NxqwFRmfocxG_lbox2wg7EieKnv0L-JRMxHRl-yT9yqhFdtRB6OmOdyoia9pKPkqoHRRmSBA_WNPaHuybH"

    suspend fun parse(url: String): ParseResult = withContext(Dispatchers.IO) {
        withTimeoutOrNull(20_000L) {
            try {
                val extractedUrl = ParseUtils.extractUrlFromText(url.trim())
                val trimmedUrl = extractedUrl ?: url.trim()
                val platform = detectPlatform(trimmedUrl)
                Log.d(TAG, "检测平台: $platform, URL: $trimmedUrl")

                val result = when (platform) {
                    "douyin" -> parseDouyin(trimmedUrl)
                    "bilibili" -> parseBilibili(trimmedUrl)
                    "kuaishou" -> parseKuaishou(trimmedUrl)
                    "weibo" -> parseWeibo(trimmedUrl)
                    "xiaohongshu" -> XhsParser(client).parse(trimmedUrl)
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
        } ?: ParseResult(
            error = "解析超时，请稍后重试或检查网络"
        )
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
        // 优先从 URL 直接提取 ID（跳过 HEAD 请求）
        var videoId = ParseUtils.extractDouyinId(url)
        var realUrl = url

        if (videoId == null) {
            // 短链需要重定向获取真实 URL
            realUrl = safeGetFinalUrl(url, mobileUA) ?: url
            Log.d(TAG, "抖音真实URL: $realUrl")
            videoId = ParseUtils.extractDouyinId(realUrl)
        }

        if (videoId == null) {
            return ParseResult(
                platform = "抖音",
                error = "无法从链接中提取视频ID，请确认是有效的抖音分享链接"
            )
        }
        Log.d(TAG, "抖音视频ID: $videoId, 跳过HEAD: ${url != realUrl}")

        // 优先走 API（快，1 次请求）
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

        // API 失败时回退到网页解析
        try {
            val result = parseDouyinByWeb(realUrl)
            if (result != null && result.isSuccess) return result
        } catch (e: Exception) {
            Log.e(TAG, "抖音网页解析失败", e)
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

            val awemeObj = renderData.optJSONObject("aweme")
            val detail = awemeObj?.optJSONObject("detail")
            val awemeInfo = detail?.optJSONArray("awemeInfo")
                ?.optJSONObject(0)
            if (awemeInfo != null) aweme = awemeInfo

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
    private suspend fun parseBilibili(url: String): ParseResult {
        var bvid = ParseUtils.extractBvid(url)

        if (bvid == null) {
            val realUrl = safeGetFinalUrl(url, desktopUA) ?: url
            bvid = ParseUtils.extractBvid(realUrl)
        }

        if (bvid == null) {
            return ParseResult(
                platform = "B站",
                error = "无法提取BV号，请确认是有效的 B 站视频链接"
            )
        }
        Log.d(TAG, "B站 BV号: $bvid")

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

    // ========== 快手解析 ==========
    private fun parseKuaishou(url: String): ParseResult {
        return ParseResult(
            platform = "快手",
            error = "快手解析暂不支持"
        )
    }

    // ========== 微博解析 ==========
    private fun parseWeibo(url: String): ParseResult {
        return try {
            val fid = getFidFromUrl(url)
            val hasFid = fid != null

            val resolvedUrl = if (hasFid) url else resolveWeiboUrl(url)
            Log.d(TAG, "微博URL: $resolvedUrl, fid: $fid")

            val isTv = isWeiboTv(resolvedUrl) || hasFid

            if (isTv) {
                parseWeiboTv(resolvedUrl, fid)
            } else {
                parseWeiboStatus(resolvedUrl)
            }
        } catch (e: Exception) {
            Log.e(TAG, "微博解析失败", e)
            ParseResult(
                platform = "微博",
                error = "微博解析失败: ${e.message}"
            )
        }
    }

    private fun getFidFromUrl(url: String): String? {
        val fidPattern = "fid=([^&]+)".toRegex()
        val match = fidPattern.find(url) ?: return null
        val fid = match.groupValues[1]
        val parts = fid.split(":")
        return if (parts.size >= 2) parts[1] else fid
    }

    private fun isWeiboTv(url: String): Boolean {
        return "/tv/show" in url || "video.weibo.com/show" in url
    }

    private fun resolveWeiboUrl(url: String): String {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", desktopUA)
                .head()
                .build()
            client.newCall(request).execute().use { response ->
                response.request.url.toString()
            }
        } catch (e: Exception) {
            url
        }
    }

    private fun parseWeiboStatus(url: String): ParseResult {
        val bid = ParseUtils.getWeiboId(url) ?: return ParseResult(
            platform = "微博",
            error = "无法从链接中提取微博ID"
        )
        Log.d(TAG, "微博ID: $bid")

        val apiUrl = "https://weibo.com/ajax/statuses/show?id=$bid&isGetLongText=true"
        val json = safeFetchJson(apiUrl, mapOf(
            "User-Agent" to desktopUA,
            "Referer" to "https://weibo.com",
            "Cookie" to weiboSubCookie
        )) ?: return ParseResult(
            platform = "微博",
            error = "无法连接微博 API"
        )

        if (json.has("error") || json.has("error_code")) {
            return ParseResult(
                platform = "微博",
                error = "微博 API 错误: ${json.optString("error", "未知错误")}"
            )
        }

        val textRaw = json.optString("text_raw", "")
        val text = cleanWeiboText(textRaw)
        val user = json.optJSONObject("user")
        val author = user?.optString("screen_name", "")
            ?: user?.optString("nickname", "")

        val mediaList = mutableListOf<MediaInfo>()

        val picInfos = json.optJSONObject("pic_infos")
        val picIds = json.optJSONArray("pic_ids")

        if (picInfos != null && picIds != null && picIds.length() > 0) {
            for (i in 0 until picIds.length()) {
                val picId = picIds.optString(i) ?: continue
                val picInfo = picInfos.optJSONObject(picId) ?: continue
                val type = picInfo.optString("type", "photo")

                if (type == "video" || type == "livephoto" || type == "gif") {
                    val largest = picInfo.optJSONObject("largest")
                    val thumbnail = picInfo.optJSONObject("thumbnail")
                    val videoUrl = picInfo.optString("video", "")

                    if (videoUrl.isNotEmpty()) {
                        mediaList.add(
                            MediaInfo(
                                type = "video",
                                url = videoUrl,
                                thumbUrl = thumbnail?.optString("url", ""),
                                width = largest?.optInt("width", 0) ?: 0,
                                height = largest?.optInt("height", 0) ?: 0,
                                ext = "mp4"
                            )
                        )
                    } else if (largest != null) {
                        mediaList.add(
                            MediaInfo(
                                type = "image",
                                url = largest.optString("url", ""),
                                thumbUrl = thumbnail?.optString("url", ""),
                                width = largest.optInt("width", 0),
                                height = largest.optInt("height", 0),
                                ext = "jpg"
                            )
                        )
                    }
                } else {
                    val largest = picInfo.optJSONObject("largest")
                    val thumbnail = picInfo.optJSONObject("thumbnail")
                    if (largest != null) {
                        mediaList.add(
                            MediaInfo(
                                type = "image",
                                url = largest.optString("url", ""),
                                thumbUrl = thumbnail?.optString("url", ""),
                                width = largest.optInt("width", 0),
                                height = largest.optInt("height", 0),
                                ext = "jpg"
                            )
                        )
                    }
                }
            }
        }

        val pageInfo = json.optJSONObject("page_info")
        if (pageInfo != null && mediaList.isEmpty()) {
            val objectType = pageInfo.optString("object_type", "")
            if (objectType == "video") {
                val mediaInfo = pageInfo.optJSONObject("media_info")
                val playbackList = mediaInfo?.optJSONArray("playback_list")
                var videoUrl: String? = null
                var width = 0
                var height = 0
                var duration = 0

                if (playbackList != null && playbackList.length() > 0) {
                    val playback = playbackList.getJSONObject(0).optJSONObject("play_info")
                    if (playback != null) {
                        videoUrl = playback.optString("url", "")
                        width = playback.optInt("width", 0)
                        height = playback.optInt("height", 0)
                        duration = playback.optInt("duration", 0)
                    }
                }

                if (videoUrl.isNullOrEmpty()) {
                    videoUrl = mediaInfo?.optString("mp4_hd_url", "")
                        ?.ifEmpty { mediaInfo.optString("mp4_sd_url", "") }
                    duration = mediaInfo?.optInt("duration", 0) ?: 0
                }

                val pagePic = pageInfo.optString("page_pic", "")

                if (!videoUrl.isNullOrEmpty()) {
                    mediaList.add(
                        MediaInfo(
                            type = "video",
                            url = videoUrl,
                            thumbUrl = pagePic,
                            width = width,
                            height = height,
                            duration = duration,
                            ext = "mp4"
                        )
                    )
                }
            }
        }

        val mixMediaInfo = json.optJSONObject("mix_media_info")
        if (mixMediaInfo != null && mediaList.isEmpty()) {
            val items = mixMediaInfo.optJSONArray("items")
            if (items != null && items.length() > 0) {
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val type = item.optString("type", "")
                    val data = item.optJSONObject("data") ?: continue

                    if (type == "pic") {
                        val largest = data.optJSONObject("largest")
                        val thumbnail = data.optJSONObject("thumbnail")
                        if (largest != null) {
                            mediaList.add(
                                MediaInfo(
                                    type = "image",
                                    url = largest.optString("url", ""),
                                    thumbUrl = thumbnail?.optString("url", ""),
                                    width = largest.optInt("width", 0),
                                    height = largest.optInt("height", 0),
                                    ext = "jpg"
                                )
                            )
                        }
                    } else if (type == "video") {
                        val mediaInfo = data.optJSONObject("media_info")
                        val playbackList = mediaInfo?.optJSONArray("playback_list")
                        var videoUrl: String? = null
                        if (playbackList != null && playbackList.length() > 0) {
                            val playback = playbackList.getJSONObject(0).optJSONObject("play_info")
                            videoUrl = playback?.optString("url", "")
                        }
                        val pagePic = data.optString("page_pic", "")
                        if (!videoUrl.isNullOrEmpty()) {
                            mediaList.add(
                                MediaInfo(
                                    type = "video",
                                    url = videoUrl,
                                    thumbUrl = pagePic,
                                    ext = "mp4"
                                )
                            )
                        }
                    }
                }
            }
        }

        val hasVideo = mediaList.any { it.type == "video" }

        return ParseResult(
            platform = "微博",
            type = if (hasVideo) "video" else "image",
            title = text.take(80),
            content = text,
            author = author,
            media = mediaList,
            error = if (mediaList.isEmpty()) "未找到可下载的媒体文件" else null
        )
    }

    private fun parseWeiboTv(url: String, oidOverride: String? = null): ParseResult {
        val oid = oidOverride ?: ParseUtils.getWeiboId(url) ?: return ParseResult(
            platform = "微博",
            error = "无法提取微博视频 ID"
        )
        Log.d(TAG, "微博TV OID: $oid")

        val apiUrl = "https://weibo.com/tv/api/component"
        val params = mapOf("page" to "/tv/show/$oid")
        val formData = "data={\"Component_Play_Playinfo\":{\"oid\":\"$oid\"}}"

        val json = safeFetchJsonPost(apiUrl, formData, mapOf(
            "User-Agent" to desktopUA,
            "Referer" to "https://weibo.com/tv/home",
            "Content-Type" to "application/x-www-form-urlencoded",
            "Cookie" to weiboSubCookie
        ), params) ?: return ParseResult(
            platform = "微博",
            error = "无法连接微博 TV API"
        )

        val data = json.optJSONObject("data")
        val cpp = data?.optJSONObject("Component_Play_Playinfo")
            ?: return ParseResult(
                platform = "微博",
                error = "微博 TV 返回数据为空"
            )

        val coverImage = "https:${cpp.optString("cover_image", "")}"
        val duration = cpp.optInt("duration_time", 0)
        val rawText = cpp.optString("text", "")
        val text = cleanWeiboText(rawText)
        val urls = cpp.optJSONObject("urls")
        val videoUrl = if (urls != null && urls.length() > 0) {
            val firstKey = urls.keys().next()
            "https:${urls.optString(firstKey, "")}"
        } else null

        val mediaList = mutableListOf<MediaInfo>()
        if (!videoUrl.isNullOrEmpty()) {
            mediaList.add(
                MediaInfo(
                    type = "video",
                    url = videoUrl,
                    thumbUrl = coverImage,
                    duration = duration,
                    ext = "mp4"
                )
            )
        }

        return ParseResult(
            platform = "微博",
            type = "video",
            title = text.take(80),
            content = text,
            media = mediaList,
            error = if (mediaList.isEmpty()) "未找到视频地址" else null
        )
    }

    private fun cleanWeiboText(text: String): String {
        var cleaned = text
        cleaned = cleaned.replace(Regex("<[^>]+>"), " ")
        cleaned = cleaned.replace(Regex("https?://\\S+"), "")
        cleaned = cleaned.replace(Regex("\\s+"), " ")
        return cleaned.trim()
    }

    // ========== 小红书解析已移至 XhsParser ==========

    // ========== 通用/其他平台 ==========
    private fun parseGeneric(url: String, platform: String): ParseResult {
        val platformName = when (platform) {
            "youtube" -> "YouTube"
            "twitter" -> "Twitter"
            "tieba" -> "贴吧"
            else -> "未知平台"
        }

        return ParseResult(
            platform = platformName,
            error = "$platformName 解析功能开发中，目前支持抖音、B站、微博、小红书"
        )
    }

    // ========== 工具方法 ==========
    private fun safeGetFinalUrl(url: String, ua: String): String? {
        return try {
            // 用 GET + follow_redirects（和 parse_hub_bot 的 get_raw_url 一致）
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", ua)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                response.request.url.toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取重定向 URL 失败: ${e.message}")
            null
        }
    }

    private fun safeFetchHtml(url: String, headers: Map<String, String> = emptyMap()): String? {
        return safeFetchHtmlWithFinalUrl(url, headers)?.first
    }

    private fun safeFetchHtmlWithFinalUrl(
        url: String,
        headers: Map<String, String> = emptyMap()
    ): Pair<String, String>? {
        return try {
            val requestBuilder = Request.Builder().url(url).get()
            for ((key, value) in headers) {
                requestBuilder.header(key, value)
            }
            client.newCall(requestBuilder.build()).execute().use { response ->
                val body = response.body?.string()
                val finalUrl = response.request.url.toString()
                if (response.isSuccessful && body != null) {
                    Pair(body, finalUrl)
                } else {
                    Log.e(TAG, "HTTP ${response.code} for $url, body长度: ${body?.length ?: 0}")
                    // 对 3xx 重定向已由 OkHttp 自动处理，这里只到 4xx/5xx
                    // 返回 body 给调用方判断是否是反爬页面
                    if (body != null && body.isNotEmpty()) {
                        Pair(body, finalUrl)
                    } else null
                }
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

    private fun safeFetchJsonPost(
        url: String,
        body: String,
        headers: Map<String, String> = emptyMap(),
        queryParams: Map<String, String> = emptyMap()
    ): JSONObject? {
        return try {
            val urlWithParams = buildString {
                append(url)
                if (queryParams.isNotEmpty()) {
                    append("?")
                    append(queryParams.entries.joinToString("&") { (k, v) ->
                        "${java.net.URLEncoder.encode(k, "UTF-8")}=${java.net.URLEncoder.encode(v, "UTF-8")}"
                    })
                }
            }

            val requestBuilder = Request.Builder()
                .url(urlWithParams)
                .post(okhttp3.RequestBody.create(null, body))

            for ((key, value) in headers) {
                requestBuilder.header(key, value)
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                val responseBody = if (response.isSuccessful) response.body?.string() else null
                if (responseBody != null) {
                    try {
                        JSONObject(responseBody)
                    } catch (e: Exception) {
                        Log.e(TAG, "POST JSON 解析失败: ${responseBody.take(100)}")
                        null
                    }
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "HTTP POST 请求失败: ${e.message}")
            null
        }
    }

    // ========== 下载相关 ==========
    private fun getRefererForUrl(url: String): String {
        return when {
            "xiaohongshu" in url || "xhslink" in url -> "https://www.xiaohongshu.com/"
            "weibo" in url -> "https://weibo.com/"
            "douyin" in url || "iesdouyin" in url -> "https://www.douyin.com/"
            "kuaishou" in url || "gifshow" in url -> "https://www.kuaishou.com/"
            else -> ""
        }
    }

    private fun isXiaohongshuUrl(url: String): Boolean {
        return "xiaohongshu" in url || "xhslink" in url
    }

    suspend fun downloadMedia(
        media: MediaInfo,
        referer: String? = null,
        onProgress: ((Int) -> Unit)? = null
    ): String? = withContext(Dispatchers.IO) {
        val url = media.url ?: return@withContext null
        try {
            val requestBuilder = Request.Builder()
                .url(url)
                .get()

            val autoReferer = referer ?: getRefererForUrl(url)
            val autoUA = if (isXiaohongshuUrl(url)) mobileUA else desktopUA

            requestBuilder.header("User-Agent", autoUA)
            if (autoReferer.isNotEmpty()) {
                requestBuilder.header("Referer", autoReferer)
            }

            val request = requestBuilder.build()

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
