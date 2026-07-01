package com.parsehub.app.data.parser.weibo

import android.util.Log
import com.parsehub.app.data.MediaInfo
import com.parsehub.app.data.ParseResult
import com.parsehub.app.data.ParseUtils
import com.parsehub.app.data.network.CookieManager
import com.parsehub.app.data.network.HeaderFactory
import com.parsehub.app.data.network.NetworkManager
import com.parsehub.app.data.parser.base.BaseParser
import com.parsehub.app.data.parser.registry.IParser
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 微博解析器 — 迁移自 ParseRepository.parseWeibo*(spec 3.2:原样搬移)
 *
 * 业务逻辑零改动:
 * - resolveWeiboUrl(短链重定向)
 * - getFidFromUrl / isWeiboTv
 * - parseWeiboStatus(图文) / parseWeiboTv(视频)
 * - cleanWeiboText
 *
 * Cookie 来自 CookieManager.get("weibo")(迁移自硬编码 weiboSubCookie)
 */
class WeiboParser(
    private val network: NetworkManager,
    private val cookieManager: CookieManager
) : BaseParser(network.client, cookieManager.get("weibo")), IParser {

    override val platformId = "weibo"
    override val displayName = "微博"
    override val platform = displayName
    override val matchPattern = """weibo\.(com|cn)"""
    /** fid 是微博视频的关键参数(video.weibo.com/show?fid=...),不能被 cleanUrlParams 清理 */
    override val reservedParameters = listOf("fid")
    private val TAG = "WeiboParser"

    override suspend fun doParse(url: String): ParseResult {
        return try {
            val resolvedUrl = resolveWeiboUrl(url)
            val fid = getFidFromUrl(resolvedUrl)
            Log.d(TAG, "微博URL: $url -> $resolvedUrl, fid: $fid")

            val isTv = isWeiboTv(resolvedUrl) || fid != null
            if (isTv) {
                parseWeiboTv(resolvedUrl, fid)
            } else {
                parseWeiboStatus(resolvedUrl)
            }
        } catch (e: Exception) {
            Log.e(TAG, "微博解析失败", e)
            ParseResult(
                platform = platform,
                error = "微博解析失败: ${e.message}"
            )
        }
    }

    private fun getFidFromUrl(url: String): String? {
        // 完整 fid 格式为 "1034:5315286551691432"(type:id),微博 TV API 的 oid 需要完整 fid
        // 之前错误地 split(":") 只取后半段,导致 API 返回 302 失败
        val fidPattern = "fid=([^&]+)".toRegex()
        val match = fidPattern.find(url) ?: return null
        return match.groupValues[1]
    }

    private fun isWeiboTv(url: String): Boolean {
        return "/tv/show" in url || "video.weibo.com/show" in url
    }

    /**
     * 对齐 parse_hub_bot WeiboAPI.resolve_url
     * 只对 mapp.api.weibo.cn/fx/ 短链做重定向;其他原样返回
     */
    private fun resolveWeiboUrl(url: String): String {
        val httpUrl = url.toHttpUrlOrNull() ?: return url
        val needResolve =
            httpUrl.host == "mapp.api.weibo.cn" && httpUrl.encodedPath.startsWith("/fx/")
        if (!needResolve) return url

        return network.fetchRedirectLocation(url, HeaderFactory.weibo()) ?: url
    }

    private fun parseWeiboStatus(url: String): ParseResult {
        val bid = ParseUtils.getWeiboId(url) ?: return ParseResult(
            platform = platform,
            error = "无法从链接中提取微博ID"
        )
        Log.d(TAG, "微博ID: $bid")

        val apiUrl = "https://weibo.com/ajax/statuses/show?id=$bid&isGetLongText=true"
        // Cookie 由 CookieJar 统一管理(初始化时注入 SUB + 服务器响应自动更新)
        // 不再手动设 Cookie header,避免被 OkHttp BridgeInterceptor 覆盖导致第二次请求失败
        val headers = HeaderFactory.weibo()
        val json = network.fetchJson(apiUrl, headers) ?: return ParseResult(
            platform = platform,
            error = "无法连接微博 API"
        )

        if (json.has("error") || json.has("error_code")) {
            return ParseResult(
                platform = platform,
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
            platform = platform,
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
            platform = platform,
            error = "无法提取微博视频 ID"
        )
        Log.d(TAG, "微博TV OID: $oid")

        val apiUrl = "https://weibo.com/tv/api/component"
        val params = mapOf("page" to "/tv/show/$oid")
        val formData = "data={\"Component_Play_Playinfo\":{\"oid\":\"$oid\"}}"

        val headers = HeaderFactory.weibo().toMutableMap().apply {
            put("Content-Type", "application/x-www-form-urlencoded")
        }
        // Cookie 由 CookieJar 统一管理,不再手动设 Cookie header
        val json = network.postForm(apiUrl, formData, headers, params) ?: return ParseResult(
            platform = platform,
            error = "无法连接微博 TV API"
        )

        val data = json.optJSONObject("data")
        val cpp = data?.optJSONObject("Component_Play_Playinfo")
            ?: return ParseResult(
                platform = platform,
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
            platform = platform,
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

    override fun matches(url: String): Boolean =
        matchPattern.toRegex(RegexOption.IGNORE_CASE).containsMatchIn(url)
}
