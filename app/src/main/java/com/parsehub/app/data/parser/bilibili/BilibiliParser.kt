package com.parsehub.app.data.parser.bilibili

import android.util.Log
import com.parsehub.app.data.MediaInfo
import com.parsehub.app.data.ParseResult
import com.parsehub.app.data.ParseUtils
import com.parsehub.app.data.network.CookieManager
import com.parsehub.app.data.network.HeaderFactory
import com.parsehub.app.data.network.NetworkManager
import com.parsehub.app.data.parser.base.BaseParser
import com.parsehub.app.data.parser.registry.IParser

/**
 * B站解析器 — 迁移自 ParseRepository.parseBilibili(spec 3.2:原样搬移)
 *
 * 业务逻辑零改动:BV号提取 → view API → playurl API
 */
class BilibiliParser(
    private val network: NetworkManager,
    cookieManager: CookieManager
) : BaseParser(network.client, cookieManager.get("bilibili")), IParser {

    override val platformId = "bilibili"
    override val displayName = "B站"
    override val platform = displayName
    override val matchPattern = """(bilibili|b23)\.(com|tv)"""
    private val TAG = "BilibiliParser"

    override suspend fun doParse(url: String): ParseResult {
        var bvid = ParseUtils.extractBvid(url)

        if (bvid == null) {
            val realUrl = network.fetchFinalUrl(url, HeaderFactory.DESKTOP_UA) ?: url
            bvid = ParseUtils.extractBvid(realUrl)
        }

        if (bvid == null) {
            return ParseResult(
                platform = platform,
                error = "无法提取BV号,请确认是有效的 B 站视频链接"
            )
        }
        Log.d(TAG, "B站 BV号: $bvid")

        val apiUrl = "https://api.bilibili.com/x/web-interface/view?bvid=$bvid"
        val json = network.fetchJson(apiUrl, HeaderFactory.bilibili(bvid)) ?: return ParseResult(
            platform = platform,
            error = "无法连接 B 站 API,请检查网络"
        )

        val code = json.optInt("code", -1)
        if (code != 0) {
            return ParseResult(
                platform = platform,
                error = "B 站 API 返回错误: ${json.optString("message", "未知错误")} (code=$code)"
            )
        }

        val data = json.optJSONObject("data")
            ?: return ParseResult(
                platform = platform,
                error = "视频数据为空"
            )

        val title = data.optString("title", "")
        val desc = data.optString("desc", "")
        val pic = data.optString("pic", "")
        val owner = data.optJSONObject("owner")?.optString("name", "") ?: ""
        val duration = data.optInt("duration")
        val cid = data.optInt("cid")

        val mediaList = mutableListOf<MediaInfo>()
        var videoUrl: String? = null

        if (cid > 0) {
            try {
                val playApi = "https://api.bilibili.com/x/player/playurl?bvid=$bvid&cid=$cid&qn=16&platform=html5&high_quality=1"
                val playJson = network.fetchJson(playApi, HeaderFactory.bilibili(bvid))

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
            platform = platform,
            type = "video",
            title = title,
            content = desc,
            author = owner,
            media = mediaList,
            error = if (videoUrl == null) "视频播放地址获取失败(可能需要登录),仅可预览封面" else null
        )
    }

    override fun matches(url: String): Boolean =
        matchPattern.toRegex(RegexOption.IGNORE_CASE).containsMatchIn(url)
}
