package com.parsehub.app.data.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
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
 * 迁移自 ParseRepository 的硬编码微博 Cookie(line 59-60)。
 */
class InMemoryCookieManager : CookieManager {
    private val platformCookies = ConcurrentHashMap<String, String>()
    private val hostCookies = ConcurrentHashMap<String, MutableList<Cookie>>()

    init {
        // 迁移硬编码微博 Cookie(过渡期保留,后续可由用户在设置页导入)
        platformCookies["weibo"] =
            "SUB=_2AkMR47Mlf8NxqwFRmfocxG_lbox2wg7EieKnv0L-JRMxHRl-yT9yqhFdtRB6OmOdyoia9pKPkqoHRRmSBA_WNPaHuybH"
    }

    override fun get(platformId: String): String? = platformCookies[platformId]
    override fun set(platformId: String, cookie: String) { platformCookies[platformId] = cookie }
    override fun clear(platformId: String?) {
        if (platformId == null) {
            platformCookies.clear()
            hostCookies.clear()
        } else {
            platformCookies.remove(platformId)
        }
    }

    override fun asOkHttpJar(): CookieJar = object : CookieJar {
        @Synchronized
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val list = hostCookies.getOrPut(url.host) { mutableListOf() }
            list.addAll(cookies)
        }

        @Synchronized
        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            hostCookies[url.host]?.toList() ?: emptyList()
    }
}
