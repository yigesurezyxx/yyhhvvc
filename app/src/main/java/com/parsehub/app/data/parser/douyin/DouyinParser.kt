package com.parsehub.app.data.parser.douyin

import android.util.Log
import com.parsehub.app.data.MediaInfo
import com.parsehub.app.data.ParseResult
import com.parsehub.app.data.ParseUtils
import com.parsehub.app.data.network.CookieManager
import com.parsehub.app.data.network.HeaderFactory
import com.parsehub.app.data.network.NetworkManager
import com.parsehub.app.data.parser.base.BaseParser
import com.parsehub.app.data.parser.registry.IParser
import org.json.JSONObject
import java.net.URLDecoder

/**
 * 抖音解析器 — 迁移自 ParseRepository.parseDouyin*(spec 3.2:原样搬移)
 *
 * 业务逻辑零改动,只做:
 * - package 迁移至 data/parser/douyin/
 * - 网络请求改用 NetworkManager
 * - Header 改用 HeaderFactory.douyin()
 * - 实现 IParser 接口
 *
 * 保留:API 优先 + Web 兜底双通道
 */
class DouyinParser(
    private val network: NetworkManager,
    cookieManager: CookieManager
) : BaseParser(network.client, cookieManager.get("douyin")), IParser {

    override val platformId = "douyin"
    override val displayName = "抖音"
    override val platform = displayName
    override val matchPattern = """(douyin|iesdouyin)\.com"""
    private val TAG = "DouyinParser"

    override suspend fun doParse(url: String): ParseResult {
        // 优先从 URL 直接提取 ID(跳过 HEAD 请求)
        var videoId = ParseUtils.extractDouyinId(url)
        var realUrl = url

        if (videoId == null) {
            realUrl = network.fetchFinalUrl(url, HeaderFactory.MOBILE_UA) ?: url
            Log.d(TAG, "抖音真实URL: $realUrl")
            videoId = ParseUtils.extractDouyinId(realUrl)
        }

        if (videoId == null) {
            return ParseResult(
                platform = platform,
                error = "无法从链接中提取视频ID,请确认是有效的抖音分享链接"
            )
        }
        Log.d(TAG, "抖音视频ID: $videoId, 跳过HEAD: ${url != realUrl}")

        // 优先走 API(快,1 次请求)
        try {
            val apiUrl = "https://www.iesdouyin.com/aweme/v1/web/aweme/detail/?aweme_id=$videoId"
            val headers = HeaderFactory.douyin()
            val json = network.fetchJson(apiUrl, headers)
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
            platform = platform,
            error = "抖音解析失败,可能是链接无效或平台限制"
        )
    }

    private fun parseDouyinByWeb(url: String): ParseResult? {
        val html = network.fetchHtml(url, HeaderFactory.douyin()) ?: return null

        val renderData = extractRenderData(html)
        if (renderData == null) {
            Log.d(TAG, "未找到 RENDER_DATA, HTML 长度: ${html.length}")
            return null
        }

        return try {
            var aweme: JSONObject? = null

            val awemeObj = renderData.optJSONObject("aweme")
            val detail = awemeObj?.optJSONObject("detail")
            val awemeInfo = detail?.optJSONArray("awemeInfo")?.optJSONObject(0)
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
        val author = aweme.optJSONObject("author")?.optString("nickname", "") ?: ""
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
            platform = platform,
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

    override fun matches(url: String): Boolean =
        matchPattern.toRegex(RegexOption.IGNORE_CASE).containsMatchIn(url)
}
