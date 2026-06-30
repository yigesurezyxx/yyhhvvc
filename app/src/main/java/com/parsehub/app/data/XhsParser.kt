package com.parsehub.app.data

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * 小红书解析器 — 完全对齐 parse_hub_bot (z-mio/ParseHub) 的 XHSParser + XHSAPI
 *
 * 实现逻辑：
 * 1. 短链(xhslink)重定向获取真实URL
 * 2. GET HTML（不发任何自定义头，对齐 httpx 默认行为）
 * 3. 从 <script> 提取 window.__INITIAL_STATE__
 * 4. 从 note.firstNoteId → noteDetailMap[firstNoteId].note 取笔记
 * 5. 视频：stream.h264 > av1 > h265 > h266，取 [0].masterUrl
 * 6. 图片：urlDefault，LivePhoto 取 stream[0].masterUrl
 */
class XhsParser(
    client: OkHttpClient,
    cookie: String? = null
) : BaseParser(client, cookie) {

    override val platform = "小红书"
    override val matchPattern = """(xiaohongshu|xhslink)\.com/"""
    override val redirectKeywords = listOf("xhslink", "item")
    override val afterCleanParameters = listOf("xsec_token")

    private val TAG = "XhsParser"

    override suspend fun doParse(url: String): ParseResult {
        // 1. GET HTML — 对齐 parse_hub_bot 的 __fetch_html
        //    httpx 默认头: Accept: */*, User-Agent: python-httpx/0.28.1
        //    OkHttp 默认头: User-Agent: okhttp/4.x.x (会被XHS拒绝)
        //    所以手动设为 httpx 的默认值
        val html = fetchHtml(url) ?: return ParseResult(
            platform = platform,
            error = "无法加载小红书页面"
        )
        Log.d(TAG, "HTML长度: ${html.length}")

        // 2. 提取 __INITIAL_STATE__ — 对齐 __extract_data
        val initialState = extractInitialState(html) ?: return ParseResult(
            platform = platform,
            error = "小红书解析失败，可能需要登录或链接已失效"
        )

        // 3. 解析笔记 — 对齐 __parse
        return parseNoteData(initialState)
    }

    /**
     * 抓取 HTML — 对齐 parse_hub_bot 的 XHSAPI.__fetch_html
     * 不发任何自定义头，只发 httpx 默认的 Accept 和 User-Agent
     *
     * 关键: httpx 默认 follow_redirects=False，这里用独立 client 关闭重定向，
     * 避免 OkHttp 跟随 XHS 反爬重定向链（每次 302 都是一次额外往返，是 20s 卡顿的主因之一）。
     * getRawUrl 已处理 xhslink 短链重定向，fetchHtml 不应再重定向。
     */
    private fun fetchHtml(url: String): String? {
        return try {
            val noRedirectClient = client.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()

            val request = Request.Builder()
                .url(url)
                .header("Accept", "*/*")
                .header("User-Agent", HTTPX_DEFAULT_UA)
                .header("Accept-Encoding", "gzip")
                .get()
                .build()

            noRedirectClient.newCall(request).execute().use { response ->
                Log.d(TAG, "HTTP ${response.code}, Content-Length: ${response.body?.contentLength()}")
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    // 非 2xx 也尝试读 body（可能是反爬页面）
                    val body = response.body?.string()
                    if (body != null && body.isNotEmpty()) body else null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "抓取 HTML 失败: ${e.message}")
            null
        }
    }

    /**
     * 提取 window.__INITIAL_STATE__ — 对齐 parse_hub_bot 的 __extract_data
     * 1. 找 <script> 标签内容以 window.__INITIAL_STATE__ 开头
     * 2. 去掉 "window.__INITIAL_STATE__=" 前缀
     * 3. 替换 undefined -> null
     * 4. json.loads
     */
    private fun extractInitialState(html: String): JSONObject? {
        return try {
            val marker = "window.__INITIAL_STATE__"
            val markerIdx = html.indexOf(marker)
            if (markerIdx == -1) {
                Log.d(TAG, "未找到 __INITIAL_STATE__ marker")
                return null
            }

            val endIdx = html.indexOf("</script>", markerIdx)
            if (endIdx == -1) return null

            // 取 marker 到 </script> 的内容
            var jsonStr = html.substring(markerIdx, endIdx)
            // 去掉 "window.__INITIAL_STATE__" 前缀
            jsonStr = jsonStr.removePrefix(marker)
            // 去掉前导 =
            if (jsonStr.startsWith("=")) jsonStr = jsonStr.substring(1)
            jsonStr = jsonStr.trim()
            // 去掉末尾分号
            if (jsonStr.endsWith(";")) jsonStr = jsonStr.dropLast(1)

            // 替换 undefined -> null（对齐 parse_hub_bot: re.sub(r"\bundefined\b", "null")）
            jsonStr = jsonStr.replace(Regex("\\bundefined\\b"), "null")

            JSONObject(jsonStr)
        } catch (e: Exception) {
            Log.e(TAG, "提取 __INITIAL_STATE__ 失败: ${e.message}")
            null
        }
    }

    /**
     * 解析笔记数据 — 对齐 parse_hub_bot 的 __parse
     */
    private fun parseNoteData(data: JSONObject): ParseResult {
        // data["note"] 不存在 → "该帖子需要登录后查看"
        val noteSection = data.optJSONObject("note") ?: return ParseResult(
            platform = platform,
            error = "该帖子需要登录后查看"
        )

        val firstNoteId = noteSection.optString("firstNoteId", "")
        val noteDetailMap = noteSection.optJSONObject("noteDetailMap")

        if (firstNoteId.isEmpty() || noteDetailMap == null) {
            return ParseResult(
                platform = platform,
                error = "未获取到内容，该帖子可能需要登录后查看"
            )
        }

        val detail = noteDetailMap.optJSONObject(firstNoteId)
        val note = detail?.optJSONObject("note") ?: return ParseResult(
            platform = platform,
            error = "未获取到内容，该帖子可能需要登录后查看"
        )

        return buildResult(note)
    }

    /**
     * 构建解析结果 — 对齐 parse_hub_bot 的 __parse_media
     */
    private fun buildResult(note: JSONObject): ParseResult {
        val title = note.optString("title", "")
            .ifEmpty { note.optString("displayTitle", "") }
        val desc = note.optString("desc", "")
        val author = note.optJSONObject("user")?.optString("nickname", "") ?: ""

        val type = note.optString("type", "normal")
        val mediaList = mutableListOf<MediaInfo>()

        if (type == "video") {
            // 视频 — 对齐 __parse_media 的 video 分支
            val video = note.optJSONObject("video")
            val media = video?.optJSONObject("media")
            val stream = media?.optJSONObject("stream")

            if (stream != null) {
                val selectedStream = selectStream(stream)
                if (selectedStream != null) {
                    val masterUrl = selectedStream.optString("masterUrl", "")
                    if (masterUrl.isNotEmpty()) {
                        val imageList = note.optJSONArray("imageList")
                        val coverUrl = if (imageList != null && imageList.length() > 0) {
                            imageList.optJSONObject(0)?.optString("urlDefault", "")
                        } else null

                        mediaList.add(MediaInfo(
                            type = "video",
                            url = masterUrl,
                            thumbUrl = coverUrl,
                            duration = selectedStream.optInt("duration", 0),
                            width = selectedStream.optInt("width", 0),
                            height = selectedStream.optInt("height", 0),
                            ext = "mp4"
                        ))
                    }
                }
            }
        } else {
            // 图文 — 对齐 __parse_media 的 imageList 分支
            val imageList = note.optJSONArray("imageList")
            if (imageList != null) {
                for (i in 0 until imageList.length()) {
                    val img = imageList.optJSONObject(i) ?: continue
                    val urlDefault = img.optString("urlDefault", "")
                    if (urlDefault.isEmpty()) continue

                    val isLivePhoto = img.optBoolean("livePhoto", false)
                    val w = img.optInt("width", 0)
                    val h = img.optInt("height", 0)
                    val urlPre = img.optString("urlPre", "")

                    if (isLivePhoto) {
                        // Live Photo — 对齐 parse_hub_bot: 取 stream[0].masterUrl 作视频
                        val stream = img.optJSONObject("stream")
                        var liveVideoUrl: String? = null
                        if (stream != null) {
                            val selected = selectStream(stream)
                            liveVideoUrl = selected?.optString("masterUrl", "")
                        }
                        mediaList.add(MediaInfo(
                            type = "image",
                            url = urlDefault,
                            thumbUrl = urlPre.ifEmpty { urlDefault },
                            width = w,
                            height = h,
                            ext = "jpg"
                        ))
                    } else {
                        // 普通图片 — 对齐 parse_hub_bot: url= urlDefault, thumb= urlPre
                        mediaList.add(MediaInfo(
                            type = "image",
                            url = urlDefault,
                            thumbUrl = urlPre.ifEmpty { urlDefault },
                            width = w,
                            height = h,
                            ext = "jpg"
                        ))
                    }
                }
            }
        }

        return ParseResult(
            platform = platform,
            type = if (type == "video") "video" else "image",
            title = title,
            content = desc,
            author = author,
            media = mediaList,
            error = if (mediaList.isEmpty()) "未找到可下载的媒体文件" else null
        )
    }

    /**
     * 选择视频流 — 对齐 parse_hub_bot 的 __select_stream
     * 优先级: h264 > av1 > h265 > h266
     * XHS 的 stream 格式: {"h264": [{masterUrl:...}], "av1": [...]}
     */
    private fun selectStream(stream: JSONObject): JSONObject? {
        val codecs = listOf("h264", "av1", "h265", "h266")
        for (codec in codecs) {
            val arr = stream.optJSONArray(codec)
            if (arr != null && arr.length() > 0) {
                return arr.optJSONObject(0)
            }
            // 兼容对象格式
            val obj = stream.optJSONObject(codec)
            if (obj != null && obj.optString("masterUrl", "").isNotEmpty()) {
                return obj
            }
        }
        return null
    }
}
