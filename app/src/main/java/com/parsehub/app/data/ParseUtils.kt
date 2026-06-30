package com.parsehub.app.data

import org.json.JSONObject

/**
 * 解析工具函数 — 纯函数，无副作用，方便单元测试
 */
object ParseUtils {

    /**
     * 从 HTML 中提取 window.__INITIAL_STATE__ 的 JSON 数据
     * 使用 indexOf 定位，避免正则灾难性回溯
     */
    fun extractInitialState(html: String): JSONObject? {
        val marker = "window.__INITIAL_STATE__"
        val startIdx = html.indexOf(marker)
        if (startIdx == -1) return null

        val jsonStart = html.indexOf('=', startIdx) + 1
        if (jsonStart <= startIdx || jsonStart >= html.length) return null

        val endIdx = html.indexOf("</script>", jsonStart)
        if (endIdx == -1) return null

        var json = html.substring(jsonStart, endIdx).trim()
        // 去掉末尾分号
        if (json.endsWith(";")) json = json.dropLast(1)
        // 清理 JS 中的 undefined / NaN（替换为 null）
        json = json.replace(Regex("\\bundefined\\b"), "null")
            .replace(Regex("\\bNaN\\b"), "null")

        return try {
            JSONObject(json)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 从抖音 URL 中提取视频/笔记 ID
     * 支持标准链接和 API 参数链接，不支持短链（需重定向）
     */
    fun extractDouyinId(url: String): String? {
        val patterns = listOf(
            "/video/(\\d+)".toRegex(),
            "/note/(\\d+)".toRegex(),
            "/share/video/(\\d+)".toRegex(),
            "/share/note/(\\d+)".toRegex(),
            "aweme_id=(\\d+)".toRegex(),
            "itemId=(\\d+)".toRegex(),
            "video_id=(\\d+)".toRegex()
        )
        for (pattern in patterns) {
            val match = pattern.find(url)
            if (match != null) return match.groupValues[1]
        }
        return null
    }

    /**
     * 从 B 站 URL 中提取 BVID
     */
    fun extractBvid(url: String): String? {
        val pattern = "(BV[0-9A-Za-z]{10})".toRegex()
        val match = pattern.find(url)
        return match?.groupValues?.get(1)
    }

    /**
     * 从微博 URL 中提取状态 ID 或视频 OID
     */
    fun getWeiboId(url: String): String? {
        // 1. /status/xxx 格式
        val statusPattern = "/status/([^/?#]+)".toRegex()
        statusPattern.find(url)?.let { return it.groupValues[1] }

        // 2. fid=1034:oid 格式（微博视频）
        val fidPattern = "fid=([^&]+)".toRegex()
        fidPattern.find(url)?.let { match ->
            val fid = match.groupValues[1]
            val parts = fid.split(":")
            if (parts.size >= 2) return parts[1]
            return fid
        }

        // 3. 纯数字最后一段（如 /detail/5034567890123456）
        val lastSegment = url.split("/").lastOrNull()?.split("?")?.firstOrNull()
        if (lastSegment != null && lastSegment.isNotEmpty()) {
            if (lastSegment.all { it.isDigit() } && lastSegment.length >= 9) {
                return lastSegment
            }
        }

        return null
    }

    /**
     * 从小红书视频流中选择最佳编码
     * 优先级：h264 > av1 > h265 > h266
     * XHS 的 stream 格式：{"h264": [{masterUrl:...}], "av1": [...]}
     */
    fun selectXhsStream(stream: JSONObject): JSONObject? {
        val codecs = listOf("h264", "av1", "h265", "h266")
        for (codec in codecs) {
            // 优先按数组格式取（XHS 标准 API 格式）
            val arr = stream.optJSONArray(codec)
            if (arr != null && arr.length() > 0) {
                return arr.optJSONObject(0)
            }
            // 兼容对象格式
            val codecObj = stream.optJSONObject(codec)
            if (codecObj != null) {
                val masterUrl = codecObj.optString("masterUrl", "")
                    .ifEmpty { codecObj.optString("master_url", "") }
                if (masterUrl.isNotEmpty()) return codecObj
            }
        }
        return null
    }

    /**
     * 从分享文本中提取 URL，去掉末尾可能混入的标点
     */
    fun extractUrlFromText(text: String): String? {
        val urlPattern = "https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+".toRegex(RegexOption.IGNORE_CASE)
        val match = urlPattern.find(text) ?: return null
        return match.value.trimEnd(',', ')', ';', '.', '!', '?', ':', '\'')
    }

    /**
     * 检查小红书 URL 是否包含 xsec_token
     */
    fun hasXsecToken(url: String): Boolean {
        return "xsec_token=" in url
    }
}
