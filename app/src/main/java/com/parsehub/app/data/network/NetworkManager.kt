package com.parsehub.app.data.network

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 网络管理器(spec 6.1:统一 OkHttpClient + 请求封装)
 *
 * - 单一 OkHttpClient(带 CookieJar + 超时)
 * - 提供 fetchHtml / fetchJson / postForm / fetchFinalUrl
 * - newNoRedirectClient():XHS HTML 抓取需要关闭重定向
 *
 * 迁移自 ParseRepository 的 safeFetchHtml/safeFetchJson/safeFetchJsonPost/safeGetFinalUrl。
 */
class NetworkManager(private val cookieManager: CookieManager) {

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .cookieJar(cookieManager.asOkHttpJar())
            .build()
    }

    private val TAG = "NetworkManager"

    /**
     * 抓取 HTML(对齐 safeFetchHtml)
     * @return body 文本,失败返回 null
     */
    fun fetchHtml(url: String, headers: Map<String, String> = emptyMap()): String? {
        return try {
            val request = buildGetRequest(url, headers)
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (response.isSuccessful && body != null) {
                    body
                } else {
                    Log.d(TAG, "fetchHtml HTTP ${response.code}, body长度: ${body?.length ?: 0}")
                    if (!body.isNullOrEmpty()) body else null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchHtml 失败: ${e.message}")
            null
        }
    }

    /**
     * 抓取 HTML 并返回最终 URL(对齐 safeFetchHtmlWithFinalUrl)
     */
    fun fetchHtmlWithFinalUrl(
        url: String,
        headers: Map<String, String> = emptyMap()
    ): Pair<String, String>? {
        return try {
            val request = buildGetRequest(url, headers)
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                val finalUrl = response.request.url.toString()
                if (response.isSuccessful && body != null) {
                    Pair(body, finalUrl)
                } else if (!body.isNullOrEmpty()) {
                    Pair(body, finalUrl)
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchHtmlWithFinalUrl 失败: ${e.message}")
            null
        }
    }

    /**
     * GET JSON(对齐 safeFetchJson)
     */
    fun fetchJson(url: String, headers: Map<String, String> = emptyMap()): JSONObject? {
        val body = fetchHtml(url, headers) ?: return null
        return try {
            JSONObject(body)
        } catch (e: Exception) {
            Log.e(TAG, "JSON 解析失败: ${body.take(100)}")
            null
        }
    }

    /**
     * POST 表单获取 JSON(对齐 safeFetchJsonPost)
     */
    fun postForm(
        url: String,
        body: String,
        headers: Map<String, String> = emptyMap(),
        queryParams: Map<String, String> = emptyMap()
    ): JSONObject? {
        return try {
            val urlWithParams = if (queryParams.isEmpty()) url else {
                url + "?" + queryParams.entries.joinToString("&") { (k, v) ->
                    "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
                }
            }
            val request = Request.Builder()
                .url(urlWithParams)
                .post(body.toRequestBody(null))
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = if (response.isSuccessful) response.body?.string() else null
                if (responseBody != null) {
                    try {
                        JSONObject(responseBody)
                    } catch (e: Exception) {
                        Log.e(TAG, "POST JSON 解析失败: ${responseBody.take(100)}")
                        null
                    }
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "POST 请求失败: ${e.message}")
            null
        }
    }

    /**
     * 获取重定向最终 URL(对齐 safeGetFinalUrl,GET + follow_redirects)
     */
    fun fetchFinalUrl(url: String, ua: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", ua)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                response.request.url.toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchFinalUrl 失败: ${e.message}")
            null
        }
    }

    /**
     * 不跟随重定向的 GET(对齐 resolveWeiboUrl,取 Location 头)
     */
    fun fetchRedirectLocation(url: String, headers: Map<String, String> = emptyMap()): String? {
        return try {
            val noRedirectClient = client.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
            val request = buildGetRequest(url, headers)
            noRedirectClient.newCall(request).execute().use { response ->
                response.header("Location")
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchRedirectLocation 失败: ${e.message}")
            null
        }
    }

    /**
     * 关闭重定向的 client(XHS HTML 抓取用,避免跟随反爬重定向链)
     */
    fun newNoRedirectClient(): OkHttpClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private fun buildGetRequest(url: String, headers: Map<String, String>): Request {
        return Request.Builder().url(url).get().apply {
            headers.forEach { (k, v) -> header(k, v) }
        }.build()
    }
}
