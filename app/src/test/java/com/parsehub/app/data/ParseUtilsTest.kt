package com.parsehub.app.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ParseUtilsTest {

    // ========== extractInitialState ==========

    @Test
    fun extractInitialState_returnsNull_whenNoMarker() {
        val html = "<html><body>no state here</body></html>"
        assertNull(ParseUtils.extractInitialState(html))
    }

    @Test
    fun extractInitialState_returnsJson_whenValid() {
        val html = """
            <html><head>
            <script>window.__INITIAL_STATE__ = {"note":{"id":"123"}};</script>
            </head><body>content</body></html>
        """.trimIndent()
        val result = ParseUtils.extractInitialState(html)
        assertNotNull(result)
        assertEquals("123", result?.getJSONObject("note")?.getString("id"))
    }

    @Test
    fun extractInitialState_replacesUndefined_withNull() {
        val html = """
            <script>window.__INITIAL_STATE__ = {"note":{"id":undefined,"title":"test"}};</script>
        """.trimIndent()
        val result = ParseUtils.extractInitialState(html)
        assertNotNull(result)
        assertTrue(result?.getJSONObject("note")?.isNull("id") == true)
        assertEquals("test", result?.getJSONObject("note")?.getString("title"))
    }

    @Test
    fun extractInitialState_handlesLargeHtml_withoutBacktracking() {
        // 模拟小红书 500KB+ HTML：前面有 __INITIAL_STATE__，后面大量填充
        val large = buildString {
            append("<html><head>")
            append("<script>window.__INITIAL_STATE__ = {\"note\":{\"firstNoteId\":\"abc123\"}};</script>")
            repeat(50000) { append("<!-- padding padding padding -->") }
            append("</head><body>")
            repeat(50000) { append("<div>filler content</div>") }
            append("</body></html>")
        }

        val start = System.currentTimeMillis()
        val result = ParseUtils.extractInitialState(large)
        val elapsed = System.currentTimeMillis() - start

        assertNotNull(result)
        assertEquals("abc123", result?.getJSONObject("note")?.getString("firstNoteId"))
        assertTrue("应该在 200ms 内完成，实际 ${elapsed}ms", elapsed < 200)
    }

    @Test
    fun extractInitialState_handlesMultipleScriptTags() {
        val html = """
            <html><head>
            <script>var a = 1;</script>
            <script>var b = 2;</script>
            <script>window.__INITIAL_STATE__ = {"key":"value"};</script>
            <script>var c = 3;</script>
            </head></html>
        """.trimIndent()
        val result = ParseUtils.extractInitialState(html)
        assertNotNull(result)
        assertEquals("value", result?.getString("key"))
    }

    @Test
    fun extractInitialState_handlesWhitespaceAroundEquals() {
        val html = """
            <script>window.__INITIAL_STATE__   =   {"key":"value"}</script>
        """.trimIndent()
        val result = ParseUtils.extractInitialState(html)
        assertNotNull(result)
        assertEquals("value", result?.getString("key"))
    }

    // ========== extractDouyinId ==========

    @Test
    fun extractDouyinId_returnsId_fromVideoUrl() {
        val url = "https://www.douyin.com/video/7371234567890123"
        assertEquals("7371234567890123", ParseUtils.extractDouyinId(url))
    }

    @Test
    fun extractDouyinId_returnsId_fromShareVideoUrl() {
        val url = "https://www.iesdouyin.com/share/video/7371234567890123/?region=CN"
        assertEquals("7371234567890123", ParseUtils.extractDouyinId(url))
    }

    @Test
    fun extractDouyinId_returnsId_fromNoteUrl() {
        val url = "https://www.douyin.com/note/7371234567890123"
        assertEquals("7371234567890123", ParseUtils.extractDouyinId(url))
    }

    @Test
    fun extractDouyinId_returnsNull_fromShortLink_withoutRedirect() {
        // 短链需要重定向才能获取真实 ID，纯函数应返回 null
        val url = "https://v.douyin.com/abcdefg/"
        assertNull(ParseUtils.extractDouyinId(url))
    }

    @Test
    fun extractDouyinId_returnsNull_fromInvalidUrl() {
        val url = "https://www.example.com/something"
        assertNull(ParseUtils.extractDouyinId(url))
    }

    @Test
    fun extractDouyinId_returnsId_fromAwemeIdParam() {
        val url = "https://www.douyin.com/?aweme_id=7371234567890123"
        assertEquals("7371234567890123", ParseUtils.extractDouyinId(url))
    }

    // ========== extractBvid ==========

    @Test
    fun extractBvid_returnsBvid_fromStandardUrl() {
        val url = "https://www.bilibili.com/video/BV1GJ411x7fD"
        assertEquals("BV1GJ411x7fD", ParseUtils.extractBvid(url))
    }

    @Test
    fun extractBvid_returnsBvid_fromUrlWithParams() {
        val url = "https://www.bilibili.com/video/BV1GJ411x7fD?spm_id_from=333.337"
        assertEquals("BV1GJ411x7fD", ParseUtils.extractBvid(url))
    }

    @Test
    fun extractBvid_returnsBvid_fromB23ShortLink() {
        val url = "https://b23.tv/BV1GJ411x7fD"
        assertEquals("BV1GJ411x7fD", ParseUtils.extractBvid(url))
    }

    @Test
    fun extractBvid_returnsNull_fromInvalidUrl() {
        val url = "https://www.example.com/something"
        assertNull(ParseUtils.extractBvid(url))
    }

    // ========== getWeiboId ==========

    @Test
    fun getWeiboId_returnsId_fromStatusUrl() {
        val url = "https://weibo.com/1234567890/status/AbcDef123"
        assertEquals("AbcDef123", ParseUtils.getWeiboId(url))
    }

    @Test
    fun getWeiboId_returnsOid_fromFidParam() {
        val url = "https://video.weibo.com/show?fid=1034:5315286551691432"
        assertEquals("5315286551691432", ParseUtils.getWeiboId(url))
    }

    @Test
    fun getWeiboId_returnsId_fromNumericLastSegment() {
        val url = "https://weibo.com/detail/5034567890123456"
        assertEquals("5034567890123456", ParseUtils.getWeiboId(url))
    }

    @Test
    fun getWeiboId_returnsNull_fromInvalidUrl() {
        val url = "https://www.example.com/something"
        assertNull(ParseUtils.getWeiboId(url))
    }

    // ========== selectXhsStream ==========

    @Test
    fun selectXhsStream_prefersH264() {
        val stream = JSONObject("""
            {"h264":[{"masterUrl":"http://h264.mp4"}],"av1":[{"masterUrl":"http://av1.mp4"}]}
        """.trimIndent())
        val selected = ParseUtils.selectXhsStream(stream)
        assertNotNull(selected)
        assertEquals("http://h264.mp4", selected?.optString("masterUrl"))
    }

    @Test
    fun selectXhsStream_fallsBackToAv1_whenNoH264() {
        val stream = JSONObject("""
            {"av1":[{"masterUrl":"http://av1.mp4"}],"h265":[{"masterUrl":"http://h265.mp4"}]}
        """.trimIndent())
        val selected = ParseUtils.selectXhsStream(stream)
        assertNotNull(selected)
        assertEquals("http://av1.mp4", selected?.optString("masterUrl"))
    }

    @Test
    fun selectXhsStream_returnsNull_whenNoStreams() {
        val stream = JSONObject("""{}""")
        assertNull(ParseUtils.selectXhsStream(stream))
    }
}
