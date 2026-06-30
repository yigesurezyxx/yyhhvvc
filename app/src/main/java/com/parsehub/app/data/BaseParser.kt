package com.parsehub.app.data

import android.util.Log
import okhttp3.HttpUrl
import okhttp3.Request
import java.net.URLEncoder

/**
 * 解析器基类 — 对齐 parse_hub_bot 的 BaseParser
 *
 * 负责：URL 匹配、短链重定向、参数清洗（保留/清理）
 */
abstract class BaseParser(
    protected val client: okhttp3.OkHttpClient,
    protected val cookie: String? = null
) {
    companion object {
        private const val TAG = "BaseParser"

        /** 全局默认 UA（对齐 parse_hub_bot GlobalConfig.ua） */
        const val DEFAULT_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36"

        /** httpx 默认 UA（XHS HTML 请求用这个，对齐 parse_hub_bot） */
        const val HTTPX_DEFAULT_UA = "python-httpx/0.28.1"
    }

    /** 平台名称 */
    abstract val platform: String

    /** URL 匹配正则 */
    abstract val matchPattern: String

    /** 需要跟随重定向的关键词 */
    open val redirectKeywords: List<String> = emptyList()

    /** 始终保留的参数（如分P的 p） */
    open val reservedParameters: List<String> = emptyList()

    /** 解析时保留、输出时清理的参数（如 xsec_token） */
    open val afterCleanParameters: List<String> = emptyList()

    /** 匹配 URL */
    fun match(url: String): Boolean {
        return matchPattern.toRegex(RegexOption.IGNORE_CASE).containsMatchIn(url)
    }

    /**
     * 获取真实 URL — 短链重定向 + 参数清洗
     * 对齐 parse_hub_bot 的 get_raw_url
     *
     * @param cleanAll true=清理所有 afterClean 参数（输出用），false=保留 afterClean 参数（解析用）
     */
    fun getRawUrl(url: String, cleanAll: Boolean = false): String {
        var finalUrl = url.trim()

        // 确保有协议
        if (!finalUrl.startsWith("http")) {
            finalUrl = "https://$finalUrl"
        }

        // 短链重定向（对齐 parse_hub_bot：含 redirect_keywords 时跟随重定向）
        if (redirectKeywords.any { finalUrl.contains(it) }) {
            val redirected = followRedirect(finalUrl)
            if (redirected != null) {
                finalUrl = redirected
                Log.d(TAG, "$platform 短链重定向: $url -> $finalUrl")
            }
        }

        // 参数清洗（对齐 parse_hub_bot 的 _clean_params）
        finalUrl = cleanUrlParams(finalUrl, cleanAll)

        return finalUrl
    }

    /**
     * 跟随重定向获取最终 URL
     * 对齐 parse_hub_bot：GET + follow_redirects=True + 全局 UA
     */
    private fun followRedirect(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", DEFAULT_UA)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                response.request.url.toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "$platform 重定向失败: ${e.message}")
            null
        }
    }

    /**
     * 清洗 URL 参数
     * 对齐 parse_hub_bot 的参数保留逻辑：
     * - reservedParameters: 始终保留
     * - afterCleanParameters: cleanAll=false 时保留，cleanAll=true 时清理
     * - 其他参数: 一律清理
     */
    private fun cleanUrlParams(url: String, cleanAll: Boolean): String {
        return try {
            val parsed = HttpUrl.parse(url) ?: return url
            val queryParamNames = parsed.queryParameterNames.toMutableList()

            val newQuery = StringBuilder()
            for (name in queryParamNames) {
                val isReserved = name in reservedParameters
                val isAfterClean = name in afterCleanParameters
                // 保留条件：(保留参数 且 非全清模式的after_clean) 或 (after_clean 且 非全清)
                val keep = (isReserved && !(cleanAll && isAfterClean)) || (isAfterClean && !cleanAll)
                if (keep) {
                    val values = parsed.queryParameterValues(name)
                    for (value in values) {
                        if (newQuery.isNotEmpty()) newQuery.append("&")
                        newQuery.append(URLEncoder.encode(name, "UTF-8"))
                        newString("=")
                        newQuery.append(URLEncoder.encode(value, "UTF-8"))
                    }
                }
            }

            val builder = parsed.newBuilder()
            // 清除所有原有参数
            for (name in queryParamNames) {
                builder.removeAllQueryParameter(name)
            }
            // 添加保留的参数
            if (newQuery.isNotEmpty()) {
                for (pair in newQuery.toString().split("&")) {
                    val idx = pair.indexOf("=")
                    if (idx > 0) {
                        builder.addQueryParameter(
                            pair.substring(0, idx),
                            pair.substring(idx + 1)
                        )
                    }
                }
            }

            builder.build().toString()
        } catch (e: Exception) {
            Log.e(TAG, "URL 参数清洗失败: ${e.message}")
            url
        }
    }

    private fun StringBuilder.newString(s: String): StringBuilder = this.append(s)

    /**
     * 执行解析
     * 对齐 parse_hub_bot 的 parse 方法：getRawUrl → _doParse → 清理输出URL
     */
    suspend fun parse(url: String): ParseResult {
        return try {
            val rawUrl = getRawUrl(url, cleanAll = false)
            Log.d(TAG, "$platform 解析URL: $rawUrl")
            val result = doParse(rawUrl)
            result
        } catch (e: Exception) {
            Log.e(TAG, "$platform 解析失败", e)
            ParseResult(
                platform = platform,
                error = "解析失败: ${e.message}"
            )
        }
    }

    /** 子类实现的解析逻辑 */
    protected abstract suspend fun doParse(url: String): ParseResult
}
