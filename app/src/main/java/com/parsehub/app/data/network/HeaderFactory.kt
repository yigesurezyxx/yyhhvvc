package com.parsehub.app.data.network

/**
 * 统一 Header 策略(spec 6.2:Parser 不再自己拼 Header)
 *
 * 每个平台一套 Header,下载按 URL 域名选 Header。
 * 新增平台只在此扩展。
 */
object HeaderFactory {
    const val MOBILE_UA =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
    const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36"
    const val HTTPX_UA = "python-httpx/0.28.1"

    /** 小红书 HTML 抓取(对齐 parse_hub_bot httpx 默认头) */
    fun xhs(): Map<String, String> = mapOf(
        "Accept" to "*/*",
        "User-Agent" to HTTPX_UA,
        "Accept-Encoding" to "gzip"
    )

    /** 抖音 API/Web */
    fun douyin(): Map<String, String> = mapOf(
        "User-Agent" to MOBILE_UA,
        "Referer" to "https://www.douyin.com/",
        "Accept-Language" to "zh-CN,zh;q=0.9"
    )

    /** B站 API */
    fun bilibili(bvid: String): Map<String, String> = mapOf(
        "User-Agent" to DESKTOP_UA,
        "Referer" to "https://www.bilibili.com/video/$bvid"
    )

    /** 微博 API/TV */
    fun weibo(): Map<String, String> = mapOf(
        "User-Agent" to DESKTOP_UA,
        "Referer" to "https://weibo.com"
    )

    /** 下载请求 Header(按 URL 域名选 UA + Referer) */
    fun download(url: String): Map<String, String> {
        val isXhs = url.contains("xiaohongshu") || url.contains("xhslink")
        val ua = if (isXhs) MOBILE_UA else DESKTOP_UA
        val referer = when {
            isXhs -> "https://www.xiaohongshu.com/"
            url.contains("weibo") -> "https://weibo.com/"
            url.contains("douyin") || url.contains("iesdouyin") -> "https://www.douyin.com/"
            url.contains("kuaishou") || url.contains("gifshow") -> "https://www.kuaishou.com/"
            else -> ""
        }
        return buildMap {
            put("User-Agent", ua)
            if (referer.isNotEmpty()) put("Referer", referer)
        }
    }
}
