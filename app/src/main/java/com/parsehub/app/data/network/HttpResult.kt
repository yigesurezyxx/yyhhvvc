package com.parsehub.app.data.network

/**
 * HTTP 请求结果(spec 6:统一返回类型)
 *
 * - [Success]:2xx,body 可读
 * - [Redirect]:3xx,Location 头(短链解析用)
 * - [Error]:4xx/5xx 或网络异常
 */
sealed class HttpResult {
    data class Success(val body: String, val finalUrl: String, val code: Int) : HttpResult()
    data class Redirect(val location: String, val code: Int) : HttpResult()
    data class Error(val code: Int?, val message: String) : HttpResult()
}
