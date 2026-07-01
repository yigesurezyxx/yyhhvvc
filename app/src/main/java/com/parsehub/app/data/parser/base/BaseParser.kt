package com.parsehub.app.data.parser.base

import android.util.Log
import com.parsehub.app.data.ParseResult
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

/**
 * 解析器基类 — 对齐 parse_hub_bot 的 BaseParser
 *
 * 负责(URL 匹配、短链重定向、参数清洗)
 * 七步管道中的步骤 1-2(UrlNormalizer + ShortLinkResolver)由此覆盖。
 *
 * 注:本类从 data/ 迁移至 data/parser/base/,逻辑零改动。
 */
abstract class BaseParser(
    protected val client: OkHttpClient,
    protected val cookie: String? = null
) {
    companion object {
        private const val TAG = "BaseParser"

        /** 全局默认 UA(对齐 parse_hub_bot GlobalConfig.ua) */
        const val DEFAULT_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36"

        /** httpx 默认 UA(XHS HTML 请求用这个,对齐 parse_hub_bot) */
        const val HTTPX_DEFAULT_UA = "python-httpx/0.28.1"
    }

    /** 平台名称 */
    abstract val platform: String

    /** URL 匹配正则 */
    abstract val matchPattern: String

    /** 需要跟随重定向的关键词 */
    open val redirectKeywords: List<String> = emptyList()

    /** 始终保留的参数(如分P的 p) */
    open val reservedParameters: List<String> = emptyList()

    /** 解析时保留、输出时清理的参数(如 xsec_token) */
    open val afterCleanParameters: List<String> = emptyList()

    /** 匹配 URL */
    fun match(url: String): Boolean {
        return matchPattern.toRegex(RegexOption.IGNORE_CASE).containsMatchIn(url)
    }

    /**
     * 获取真实 URL — 短链重定向 + 参数清洗
     * 对齐 parse_hub_bot 的 get_raw_url
     *
     * @param cleanAll true=清理所有 afterClean 参数(输出用),false=保留 afterClean 参数(解析用)
     */
    fun getRawUrl(url: String, cleanAll: Boolean = false): String {
        var finalUrl = url.trim()

        // 确保有协议
        if (!finalUrl.startsWith("http")) {
            finalUrl = "https://$finalUrl"
        }

        // 短链重定向(对齐 parse_hub_bot:含 redirect_keywords 时跟随重定向)
        if (redirectKeywords.any { finalUrl.contains(it) }) {
            val redirected = followRedirect(finalUrl)
            if (redirected != null) {
                finalUrl = redirected
                Log.d(TAG, "$platform 短链重定向: $url -> $finalUrl")
            }
        }

        // 参数清洗(对齐 parse_hub_bot 的 _clean_params)
        finalUrl = cleanUrlParams(finalUrl, cleanAll)

        return finalUrl
    }

    /**
     * 跟随重定向获取最终 URL
     * 对齐 parse_hub_bot:GET + follow_redirects=True + 全局 UA
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
     * 对齐 parse_hub_bot 的参数保留逻辑:
     * - reservedParameters: 始终保留
     * - afterCleanParameters: cleanAll=false 时保留,cleanAll=true 时清理
     * - 其他参数: 一律清理
     */
    private fun cleanUrlParams(url: String, cleanAll: Boolean): String {
        return try {
            val parsed = url.toHttpUrlOrNull() ?: return url
            val queryParamNames = parsed.queryParameterNames

            val keepParams = mutableListOf<Pair<String, String>>()
            for (name in queryParamNames) {
                val isReserved = name in reservedParameters
                val isAfterClean = name in afterCleanParameters
                val keep = (isReserved && !(cleanAll && isAfterClean)) || (isAfterClean && !cleanAll)
                if (keep) {
                    for (value in parsed.queryParameterValues(name)) {
                        if (value != null) keepParams.add(name to value)
                    }
                }
            }

            val base = url.substringBefore("?")
            if (keepParams.isEmpty()) {
                base
            } else {
                base + "?" + keepParams.joinToString("&") { (k, v) ->
                    URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "URL 参数清洗失败: ${e.message}")
            url
        }
    }

    /**
     * 执行解析
     * 对齐 parse_hub_bot 的 parse 方法:getRawUrl → _doParse → 清理输出URL
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
