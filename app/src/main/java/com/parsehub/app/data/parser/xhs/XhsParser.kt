package com.parsehub.app.data.parser.xhs

import android.util.Log
import com.parsehub.app.data.MediaInfo
import com.parsehub.app.data.ParseResult
import com.parsehub.app.data.network.HeaderFactory
import com.parsehub.app.data.network.NetworkManager
import com.parsehub.app.data.network.CookieManager
import com.parsehub.app.data.parser.base.BaseParser
import com.parsehub.app.data.parser.base.ScriptExtractor
import com.parsehub.app.data.parser.registry.IParser
import okhttp3.Request
import org.json.JSONObject

/**
 * 小红书解析器(spec 5:多策略降级版)
 *
 * 改造点(对齐审计报告 P0-②):
 * - 从单一 window.__INITIAL_STATE__ 升级为 [ScriptExtractor] 五级降级
 * - 从 Repository 注入 NetworkManager + CookieManager(不再自带 client)
 * - HTML 抓取改用 NetworkManager.newNoRedirectClient() + HeaderFactory.xhs()
 *
 * 保留点(行为不变):
 * - parseNoteData / buildResult / selectStream 原样搬移
 * - 视频流选择优先级 h264 > av1 > h265 > h266
 * - LivePhoto 取 stream[0].masterUrl
 */
class XhsParser(
    private val network: NetworkManager,
    cookieManager: CookieManager
) : BaseParser(network.client, cookieManager.get("xiaohongshu")), IParser {

    override val platformId = "xiaohongshu"
    override val displayName = "小红书"
    override val platform = displayName
    override val matchPattern = """(xiaohongshu|xhslink)\.com/"""
    override val redirectKeywords = listOf("xhslink", "item")
    override val afterCleanParameters = listOf("xsec_token")

    private val TAG = "XhsParser"
    private val extractor = ScriptExtractor()

    override suspend fun doParse(url: String): ParseResult {
        // 1. GET HTML — 关闭重定向,避免跟随 XHS 反爬重定向链
        val html = fetchHtml(url) ?: return ParseResult(
            platform = platform,
            error = "无法加载小红书页面"
        )
        Log.d(TAG, "HTML长度: ${html.length}")

        // 2. 多策略提取(ScriptExtractor 五级降级)
        val initialState = extractor.extractInitialState(html) ?: return ParseResult(
            platform = platform,
            error = "小红书解析失败,可能需要登录或链接已失效"
        )

        // 3. 解析笔记(原逻辑)
        return parseNoteData(initialState)
    }

    /**
     * 抓取 HTML(spec 5:双策略降级)
     *
     * 策略 1:noRedirect client(对齐 parse_hub_bot httpx follow_redirects=False)
     *   - 避免 OkHttp 跟随 XHS 反爬重定向链(每次 302 是额外往返,是 20s 卡顿主因)
     *   - getRawUrl 已处理 xhslink 短链重定向,fetchHtml 不应再重定向
     *
     * 策略 2(降级):noRedirect 拿到 3xx 或空 body 时,用 followRedirect client 重试
     *   - 兜底场景:部分链接必须跟随重定向才能拿到真实页面
     */
    private fun fetchHtml(url: String): String? {
        val headers = HeaderFactory.xhs()
        // 策略 1:noRedirect
        val body = executeRequest(network.newNoRedirectClient(), url, headers)
        if (!body.isNullOrEmpty() && body.length > 500) {
            Log.d(TAG, "noRedirect 成功,HTML长度: ${body.length}")
            return body
        }
        // 策略 2:降级 followRedirect
        Log.d(TAG, "noRedirect 拿到空/短 body(长度=${body?.length ?: 0}),降级 followRedirect")
        val fallback = executeRequest(network.client, url, headers)
        if (!fallback.isNullOrEmpty()) {
            Log.d(TAG, "followRedirect 降级成功,HTML长度: ${fallback.length}")
        }
        return fallback
    }

    private fun executeRequest(
        client: okhttp3.OkHttpClient,
        url: String,
        headers: Map<String, String>
    ): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                Log.d(TAG, "HTTP ${response.code}")
                response.body?.string()
            }
        } catch (e: Exception) {
            Log.e(TAG, "请求失败: ${e.message}")
            null
        }
    }

    /** 解析笔记数据 — 原样搬移 */
    private fun parseNoteData(data: JSONObject): ParseResult {
        val noteSection = data.optJSONObject("note") ?: return ParseResult(
            platform = platform,
            error = "该帖子需要登录后查看"
        )

        val firstNoteId = noteSection.optString("firstNoteId", "")
        val noteDetailMap = noteSection.optJSONObject("noteDetailMap")

        if (firstNoteId.isEmpty() || noteDetailMap == null) {
            return ParseResult(
                platform = platform,
                error = "未获取到内容,该帖子可能需要登录后查看"
            )
        }

        val detail = noteDetailMap.optJSONObject(firstNoteId)
        val note = detail?.optJSONObject("note") ?: return ParseResult(
            platform = platform,
            error = "未获取到内容,该帖子可能需要登录后查看"
        )

        return buildResult(note)
    }

    /** 构建解析结果 — 原样搬移 */
    private fun buildResult(note: JSONObject): ParseResult {
        val title = note.optString("title", "")
            .ifEmpty { note.optString("displayTitle", "") }
        val desc = note.optString("desc", "")
        val author = note.optJSONObject("user")?.optString("nickname", "") ?: ""

        val type = note.optString("type", "normal")
        val mediaList = mutableListOf<MediaInfo>()

        if (type == "video") {
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
                        // Live Photo — 取 stream[0].masterUrl 作视频(原逻辑保留,本轮未启用)
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

    /** 选择视频流 — 原样搬移(h264 > av1 > h265 > h266) */
    private fun selectStream(stream: JSONObject): JSONObject? {
        val codecs = listOf("h264", "av1", "h265", "h266")
        for (codec in codecs) {
            val arr = stream.optJSONArray(codec)
            if (arr != null && arr.length() > 0) {
                return arr.optJSONObject(0)
            }
            val obj = stream.optJSONObject(codec)
            if (obj != null && obj.optString("masterUrl", "").isNotEmpty()) {
                return obj
            }
        }
        return null
    }

    override fun matches(url: String): Boolean =
        matchPattern.toRegex(RegexOption.IGNORE_CASE).containsMatchIn(url)
}
