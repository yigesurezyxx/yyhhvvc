package com.parsehub.app.data.parser.base

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ScriptExtractor 单测(spec 9.1:5 策略 + 嵌套 </script> 容错)
 */
class ScriptExtractorTest {

    private val extractor = ScriptExtractor()

    @Test
    fun `strategy 1 window INITIAL_STATE with semicolon`() {
        val html = """
            <html><head>
            <script>window.__INITIAL_STATE__={"note":{"firstNoteId":"abc123"},"foo":"bar"};</script>
            </head></html>
        """.trimIndent()
        val json = extractor.extractInitialState(html)
        assertEquals("abc123", json?.optJSONObject("note")?.optString("firstNoteId"))
        assertEquals("bar", json?.optString("foo"))
    }

    @Test
    fun `strategy 1 with undefined sanitized`() {
        val html = """<script>window.__INITIAL_STATE__={"a":undefined,"b":1};</script>"""
        val json = extractor.extractInitialState(html)
        assertEquals(1, json?.optInt("b"))
    }

    @Test
    fun `strategy 2 inline assign without window prefix`() {
        val html = """<script>__INITIAL_STATE__={"key":"value"};</script>"""
        val json = extractor.extractInitialState(html)
        assertEquals("value", json?.optString("key"))
    }

    @Test
    fun `strategy 3 INITIAL_STATE_V2`() {
        val html = """<script>window.__INITIAL_STATE_V2__={"v2":true};</script>"""
        val json = extractor.extractInitialState(html)
        assertEquals(true, json?.optBoolean("v2"))
    }

    @Test
    fun `strategy 4 NEXT_DATA script tag`() {
        val html = """<script id="__NEXT_DATA__" type="application/json">{"props":{"page":1}}</script>"""
        val json = extractor.extractInitialState(html)
        assertEquals(1, json?.optJSONObject("props")?.optInt("page"))
    }

    @Test
    fun `nested braces inside strings not miscounted`() {
        // 字符串内的 } 不应导致深度提前归零
        val html = """<script>window.__INITIAL_STATE__={"text":"has } in it","ok":true};</script>"""
        val json = extractor.extractInitialState(html)
        assertEquals("has } in it", json?.optString("text"))
        assertEquals(true, json?.optBoolean("ok"))
    }

    @Test
    fun `nested script close tag inside string`() {
        // 字符串内的 </script> 不应误截(P1-④ 场景)
        val html = """<script>window.__INITIAL_STATE__={"html":"<img src='x' onerror='</script>'>","ok":1};</script>"""
        val json = extractor.extractInitialState(html)
        // 旧 indexOf("</script>") 会截断,新括号匹配应正确取到完整 JSON
        assertEquals(1, json?.optInt("ok"))
    }

    @Test
    fun `multiple nested objects`() {
        val html = """<script>window.__INITIAL_STATE__={"a":{"b":{"c":{"d":42}}}};</script>"""
        val json = extractor.extractInitialState(html)
        assertEquals(42, json?.optJSONObject("a")?.optJSONObject("b")?.optJSONObject("c")?.optInt("d"))
    }

    @Test
    fun `no marker returns null`() {
        val html = "<html><body>普通页面,无脚本数据</body></html>"
        assertNull(extractor.extractInitialState(html))
    }

    @Test
    fun `empty html returns null`() {
        assertNull(extractor.extractInitialState(""))
    }

    @Test
    fun `prefers first successful strategy`() {
        // 同时存在 __INITIAL_STATE__ 和 __NEXT_DATA__,应返回前者
        val html = """
            <script>window.__INITIAL_STATE__={"src":"initial"};</script>
            <script id="__NEXT_DATA__" type="application/json">{"src":"next"}</script>
        """.trimIndent()
        val json = extractor.extractInitialState(html)
        assertEquals("initial", json?.optString("src"))
    }
}
