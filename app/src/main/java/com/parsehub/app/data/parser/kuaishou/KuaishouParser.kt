package com.parsehub.app.data.parser.kuaishou

import android.util.Log
import com.parsehub.app.data.MediaInfo
import com.parsehub.app.data.ParseResult
import com.parsehub.app.data.network.CookieManager
import com.parsehub.app.data.network.NetworkManager
import com.parsehub.app.data.parser.base.BaseParser
import com.parsehub.app.data.parser.registry.IParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * 快手解析器 — 完全对齐 parsehub (z-mio/ParseHub) 的 KuaiShouParser + KuaiShouAPI
 *
 * 实现逻辑:
 * 1. 短链(v.kuaishou.com / /f/)重定向获取真实URL(BaseParser.getRawUrl)
 * 2. 从 URL 末段提取 photoId
 * 3. POST GraphQL (https://www.kuaishou.com/graphql) 查询 visionVideoDetail
 * 4. 从 manifestH265 → adaptationSet[0] → representation[0].url 取无水印视频
 *
 * 关键限制:
 * - 快手 GraphQL 对未登录 IP 强制风控(result:400002 要求验证码)
 * - 需要用户在 CookieManager 配置登录后的快手 cookie
 *   (parsehub 原版也是要求 pl_cfg.roll_cookie('kuaishou') 配置)
 * - 无 cookie 时返回明确提示
 */
class KuaishouParser(
    network: NetworkManager,
    cookieManager: CookieManager
) : BaseParser(network.client, cookieManager.get("kuaishou")), IParser {

    override val platformId = "kuaishou"
    override val displayName = "快手"
    override val platform = displayName
    override val matchPattern = """(kuaishou|gifshow)\.com"""
    override val redirectKeywords = listOf("v.kuaishou", "/f/")

    private val TAG = "KuaishouParser"

    override suspend fun doParse(url: String): ParseResult {
        Log.d(TAG, "开始解析: $url")

        // 1. 提取 photoId
        val photoId = extractPhotoId(url)
        if (photoId.isEmpty()) {
            return ParseResult(
                platform = platform,
                error = "无法提取快手视频ID,URL: $url"
            )
        }
        Log.d(TAG, "photoId: $photoId")

        // 2. 调 GraphQL
        val data = fetchVideoDetail(photoId)
            ?: return ParseResult(
                platform = platform,
                error = "快手接口请求失败,请检查网络或稍后重试"
            )

        // 3. 风控检查
        val resultCode = data.optInt("result", -1)
        if (resultCode != 0) {
            // 400002 / 2 都是风控
            val msg = when (resultCode) {
                400002, 2 -> "快手账号风控,需要登录Cookie(在设置中配置快手Cookie后重试)"
                else -> "快手接口返回异常 code=$resultCode"
            }
            Log.e(TAG, "风控/异常: code=$resultCode, url=$url")
            return ParseResult(platform = platform, error = msg)
        }

        // 4. 提取视频数据
        val visionDetail = data.optJSONObject("visionVideoDetail")
            ?: return ParseResult(platform = platform, error = "未获取到视频详情")

        val photo = visionDetail.optJSONObject("photo")
            ?: return ParseResult(platform = platform, error = "未获取到视频数据,可能需要登录Cookie")

        return buildResult(photo)
    }

    /**
     * 提取 photoId — 对齐 parsehub KuaiShouAPI.get_video_id
     * URL 形如 https://www.kuaishou.com/short-video/3x9yyhbsqqcug4m
     * 取最后一段 path
     */
    private fun extractPhotoId(url: String): String {
        return try {
            // 去掉 query 和 fragment
            val noQuery = url.substringBefore("?").substringBefore("#").trimEnd('/')
            // 取最后一段
            val segments = noQuery.split("/")
            val last = segments.lastOrNull() ?: ""
            // /photo/ 路径是图文,parsehub 明确不支持
            if (url.contains("/photo/")) {
                Log.w(TAG, "图文解析暂不支持(parsehub 也不支持)")
                return ""
            }
            last
        } catch (e: Exception) {
            Log.e(TAG, "提取 photoId 失败: ${e.message}")
            ""
        }
    }

    /**
     * POST GraphQL 查询 — 对齐 parsehub KuaiShouAPI.get_video_info
     */
    private fun fetchVideoDetail(photoId: String): JSONObject? {
        return try {
            val body = buildRequestBody(photoId)
            val request = Request.Builder()
                .url("https://www.kuaishou.com/graphql")
                .header("User-Agent", DEFAULT_UA)
                .header("Content-Type", "application/json")
                .header("Referer", "https://www.kuaishou.com/")
                .header("Origin", "https://www.kuaishou.com")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val respBody = response.body?.string()
                if (respBody == null) {
                    Log.e(TAG, "GraphQL 响应空, HTTP ${response.code}")
                    return null
                }
                Log.d(TAG, "GraphQL HTTP ${response.code}, 长度=${respBody.length}")
                try {
                    JSONObject(respBody)
                } catch (e: Exception) {
                    Log.e(TAG, "JSON 解析失败: ${respBody.take(200)}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "GraphQL 请求失败: ${e.message}")
            null
        }
    }

    private fun buildRequestBody(photoId: String): String {
        // 完整字段对齐 parsehub KuaiShouAPI.query
        val query = """
            query visionVideoDetail(${'$'}photoId: String, ${'$'}type: String, ${'$'}page: String, ${'$'}webPageArea: String) {
              visionVideoDetail(photoId: ${'$'}photoId, type: ${'$'}type, page: ${'$'}page, webPageArea: ${'$'}webPageArea) {
                status
                type
                author {
                  id
                  name
                  following
                  headerUrl
                  __typename
                }
                photo {
                  id
                  duration
                  caption
                  likeCount
                  realLikeCount
                  coverUrl
                  photoUrl
                  liked
                  timestamp
                  expTag
                  llsid
                  viewCount
                  videoRatio
                  stereoType
                  musicBlocked
                  manifest {
                    mediaType
                    businessType
                    version
                    adaptationSet {
                      id
                      duration
                      representation {
                        id
                        defaultSelect
                        backupUrl
                        codecs
                        url
                        height
                        width
                        avgBitrate
                        maxBitrate
                        m3u8Slice
                        qualityType
                        qualityLabel
                        frameRate
                        featureP2sp
                        hidden
                        disableAdaptive
                        __typename
                      }
                      __typename
                    }
                    __typename
                  }
                  manifestH265
                  photoH265Url
                  coronaCropManifest
                  coronaCropManifestH265
                  croppedPhotoH265Url
                  croppedPhotoUrl
                  videoResource
                  __typename
                }
                tags {
                  type
                  name
                  __typename
                }
                commentLimit {
                  canAddComment
                  __typename
                }
                llsid
                danmakuSwitch
                __typename
              }
            }
        """.trimIndent()

        val json = JSONObject()
        json.put("operationName", "visionVideoDetail")
        val variables = JSONObject()
        variables.put("photoId", photoId)
        variables.put("page", "search")
        json.put("variables", variables)
        json.put("query", query)
        return json.toString()
    }

    /**
     * 构建解析结果 — 对齐 parsehub KuaiShouVideo.parse + _get_video
     */
    private fun buildResult(photo: JSONObject): ParseResult {
        val title = photo.optString("caption", "")
        val coverUrl = photo.optString("coverUrl", "")
        val duration = photo.optInt("duration", 0)
        val author = photo.optJSONObject("author")?.optString("name", "") ?: ""

        // 提取视频 URL — 对齐 _get_video:
        // 优先 manifestH265,失败回退 videoResource.h264
        val videoInfo = extractVideoUrl(photo)
            ?: return ParseResult(
                platform = platform,
                error = "未提取到视频信息(可能需要登录Cookie)"
            )

        val mediaList = mutableListOf<MediaInfo>()
        mediaList.add(MediaInfo(
            type = "video",
            url = videoInfo.first,
            thumbUrl = coverUrl.ifEmpty { null },
            duration = duration,
            width = videoInfo.second,
            height = videoInfo.third,
            ext = "mp4"
        ))

        return ParseResult(
            platform = platform,
            type = "video",
            title = title,
            author = author,
            media = mediaList,
            error = if (mediaList.isEmpty()) "未找到可下载的视频" else null
        )
    }

    /**
     * 提取视频流 URL — 对齐 parsehub _get_video
     * 优先级: manifestH265 > videoResource.h264
     * 取 adaptationSet[0].representation[0].url
     */
    private fun extractVideoUrl(photo: JSONObject): Triple<String, Int, Int>? {
        // 1. manifestH265
        val manifestH265 = photo.optJSONObject("manifestH265")
        if (manifestH265 != null) {
            val result = extractFromManifest(manifestH265)
            if (result != null) return result
        }

        // 2. videoResource.h264
        val videoResource = photo.optJSONObject("videoResource")
        if (videoResource != null) {
            val h264 = videoResource.optJSONObject("h264")
            if (h264 != null) {
                val result = extractFromManifest(h264)
                if (result != null) return result
            }
        }

        // 3. 兜底:photoUrl 直接字段
        val photoUrl = photo.optString("photoUrl", "")
        if (photoUrl.isNotEmpty()) {
            return Triple(photoUrl, 0, 0)
        }

        return null
    }

    private fun extractFromManifest(manifest: JSONObject): Triple<String, Int, Int>? {
        val adaptationSetArr = manifest.optJSONArray("adaptationSet") ?: return null
        if (adaptationSetArr.length() == 0) return null

        val adaptationSet = adaptationSetArr.optJSONObject(0) ?: return null
        val representationArr = adaptationSet.optJSONArray("representation") ?: return null
        if (representationArr.length() == 0) return null

        val representation = representationArr.optJSONObject(0) ?: return null
        val url = representation.optString("url", "")
        if (url.isEmpty()) return null

        val width = representation.optInt("width", 0)
        val height = representation.optInt("height", 0)
        return Triple(url, width, height)
    }

    override fun matches(url: String): Boolean =
        matchPattern.toRegex(RegexOption.IGNORE_CASE).containsMatchIn(url)
}
