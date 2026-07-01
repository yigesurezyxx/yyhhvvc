package com.parsehub.app.data.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * Cookie 管理接口(spec 6.3:生命周期 + 域名维度)
 *
 * - 按 platformId 存取(小红书/微博/抖音...)
 * - asOkHttpJar() 桥接 OkHttp CookieJar
 * - 本轮仅内存实现,后续可换持久化实现(不破坏调用方)
 */
interface CookieManager {
    /** 获取某平台 Cookie,未配置返回 null */
    fun get(platformId: String): String?
    /** 设置某平台 Cookie(覆盖) */
    fun set(platformId: String, cookie: String)
    /** 清除某平台或全部 Cookie */
    fun clear(platformId: String? = null)
    /** 桥接 OkHttp CookieJar(网络层用) */
    fun asOkHttpJar(): CookieJar
}

/**
 * 内存 CookieManager 实现
 *
 * 关键修复(解决"解析一次后失效"问题):
 * 1. saveFromResponse 去重: 同名同域同路径的旧 cookie 移除,新值覆盖
 *    (之前用 addAll 累加不清理,cookie 列表无限增长 + 同名冲突)
 * 2. loadForRequest 用 Cookie.matches(url) 做 domain/path 匹配
 *    (之前只按 host 精确匹配,weibo.com 的 cookie 不发送到 video.weibo.com)
 * 3. 硬编码微博 SUB cookie 注入到 CookieJar
 *    (之前 WeiboParser 手动设 Cookie header,会被 OkHttp CookieJar 覆盖:
 *     第一次请求 CookieJar 为空不覆盖 → 成功;
 *     第二次请求 CookieJar 有 cookie → 覆盖手动 header → 丢失 SUB → 失败)
 */
class InMemoryCookieManager : CookieManager {
    private val platformCookies = ConcurrentHashMap<String, String>()

    /** 统一 cookie 存储(不再按 host 分,用 Cookie.matches 做 domain/path 匹配) */
    private val allCookies = mutableListOf<Cookie>()
    private val lock = Any()

    init {
        injectWeiboCookie()
    }

    private fun injectWeiboCookie() {
        val sub = "SUB=_2AkMR47Mlf8NxqwFRmfocxG_lbox2wg7EieKnv0L-JRMxHRl-yT9yqhFdtRB6OmOdyoia9pKPkqoHRRmSBA_WNPaHuybH"
        val url = "https://weibo.com/".toHttpUrl()
        // Domain=.weibo.com 让 cookie 对所有 weibo 子域(weibo.com/video.weibo.com 等)有效
        val cookie = Cookie.parse(url, "$sub; Domain=.weibo.com; Path=/")
        if (cookie != null) {
            synchronized(lock) { allCookies.add(cookie) }
        }
    }

    override fun get(platformId: String): String? = platformCookies[platformId]
    override fun set(platformId: String, cookie: String) { platformCookies[platformId] = cookie }

    override fun clear(platformId: String?) {
        if (platformId == null) {
            platformCookies.clear()
            synchronized(lock) { allCookies.clear() }
            injectWeiboCookie()
        } else {
            platformCookies.remove(platformId)
        }
    }

    override fun asOkHttpJar(): CookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            synchronized(lock) {
                for (cookie in cookies) {
                    // 去重: 同名同域同路径的旧 cookie 移除,用新值覆盖
                    allCookies.removeAll {
                        it.name == cookie.name &&
                        it.domain == cookie.domain &&
                        it.path == cookie.path
                    }
                    allCookies.add(cookie)
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            synchronized(lock) {
                // 清理过期 cookie
                val now = System.currentTimeMillis()
                allCookies.removeAll { it.expiresAt < now }
                // 用 Cookie.matches 做 domain/path 匹配(支持 .weibo.com 对子域生效)
                return allCookies.filter { it.matches(url) }
            }
        }
    }
}
