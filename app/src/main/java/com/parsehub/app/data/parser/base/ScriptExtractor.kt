package com.parsehub.app.data.parser.base

import android.util.Log
import org.json.JSONObject

/**
 * 多策略脚本提取器(spec 5.2:小红书抗改版五级降级)
 *
 * 解决审计报告 P0-②(单一 __INITIAL_STATE__ 依赖)+ P1-④(indexOf("</script>") 脆弱)
 *
 * 五级策略:
 * 1. window.__INITIAL_STATE__={...};(跨行,现有逻辑)
 * 2. __INITIAL_STATE__={...} 单行内联赋值
 * 3. __INITIAL_STATE_V2__(新版改版)
 * 4. __NEXT_DATA__(Next.js SSR)
 * 5. _ROUTER_DATA / _SSR_DATA(兜底)
 *
 * 关键改进:不再用 indexOf("</script>"),改用括号深度匹配提取 JSON 体,
 * 避免脚本内嵌 </script> 字符串误判(P1-④)。
 */
class ScriptExtractor {

    private val TAG = "ScriptExtractor"

    /** 候选 marker 列表(按优先级) */
    private val markers = listOf(
        "window.__INITIAL_STATE__",
        "__INITIAL_STATE_V2__",
        "__NEXT_DATA__",
        "window._ROUTER_DATA",
        "window._SSR_DATA"
    )

    /** 内联赋值变量名(不含 window. 前缀,正则匹配用) */
    private val inlineVars = listOf(
        "__INITIAL_STATE__",
        "__INITIAL_STATE_V2__",
        "__NEXT_DATA__"
    )

    /**
     * 多策略提取,返回第一个成功的 JSON
     * @param html 原始 HTML
     * @return 解析后的 JSONObject,全部失败返回 null
     */
    fun extractInitialState(html: String): JSONObject? {
        // 策略 1/3/5:marker + 括号深度匹配
        for (marker in markers) {
            val json = extractByMarker(html, marker)
            if (json != null) return json
        }
        // 策略 2:内联赋值(变量名 = {...};)
        for (varName in inlineVars) {
            val json = extractInlineAssign(html, varName)
            if (json != null) return json
        }
        // 策略 4:Next.js <script id="__NEXT_DATA__" type="application/json">
        extractNextData(html)?.let { return it }

        Log.d(TAG, "所有策略均失败,html 长度=${html.length}")
        return null
    }

    /**
     * 策略 1/3/5:marker + 括号深度匹配
     *
     * 形如:window.__INITIAL_STATE__={...};
     * - 定位 marker
     * - 跳过 = 和空白
     * - 从第一个 { 开始括号深度扫描,深度归零时截断
     * - JsonSanitizer 清洗后解析
     */
    private fun extractByMarker(html: String, marker: String): JSONObject? {
        return try {
            val markerIdx = html.indexOf(marker)
            if (markerIdx == -1) return null

            // 跳过 marker 和 = 与空白
            var i = markerIdx + marker.length
            while (i < html.length && (html[i] == '=' || html[i].isWhitespace())) i++
            if (i >= html.length || html[i] != '{') return null

            val jsonStr = extractBalancedJson(html, i) ?: return null
            val sanitized = JsonSanitizer.sanitize(jsonStr)
            JSONObject(sanitized)
        } catch (e: Exception) {
            Log.d(TAG, "extractByMarker($marker) 失败: ${e.message}")
            null
        }
    }

    /**
     * 策略 2:内联赋值 varName={...};
     * 用正则定位,再走括号匹配
     */
    private fun extractInlineAssign(html: String, varName: String): JSONObject? {
        return try {
            val pattern = Regex("""(?:window\.)?$varName\s*=\s*\{""")
            val match = pattern.find(html) ?: return null
            // match.range.last 指向 { 的位置
            val jsonStr = extractBalancedJson(html, match.range.last) ?: return null
            val sanitized = JsonSanitizer.sanitize(jsonStr)
            JSONObject(sanitized)
        } catch (e: Exception) {
            Log.d(TAG, "extractInlineAssign($varName) 失败: ${e.message}")
            null
        }
    }

    /**
     * 策略 4:Next.js <script id="__NEXT_DATA__" type="application/json">{...}</script>
     */
    private fun extractNextData(html: String): JSONObject? {
        return try {
            val pattern = Regex(
                """<script[^>]*id=["']__NEXT_DATA__["'][^>]*>(.*?)</script>""",
                RegexOption.DOT_MATCHES_ALL
            )
            val match = pattern.find(html) ?: return null
            val jsonStr = match.groupValues[1].trim()
            val sanitized = JsonSanitizer.sanitize(jsonStr)
            JSONObject(sanitized)
        } catch (e: Exception) {
            Log.d(TAG, "extractNextData 失败: ${e.message}")
            null
        }
    }

    /**
     * 括号深度匹配:从 startIdx(指向 {)开始扫描,返回完整的 {...} 字符串
     *
     * 处理:
     * - 字符串内的 {/} 忽略(避免 "}" 字符串误判)
     * - 转义符 \跳过下一字符
     * - 深度归零时截断
     */
    private fun extractBalancedJson(html: String, startIdx: Int): String? {
        if (startIdx >= html.length || html[startIdx] != '{') return null

        var depth = 0
        var inString = false
        var escape = false
        val end = html.length

        for (i in startIdx until end) {
            val c = html[i]
            if (escape) { escape = false; continue }
            if (inString) {
                when (c) {
                    '\\' -> escape = true
                    '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return html.substring(startIdx, i + 1)
                }
            }
        }
        return null
    }
}
