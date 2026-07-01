package com.parsehub.app.data.network

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * Cookie 管理接口
 *
 * - 按 platformId 存取(小红书/微博/抖音/快手...)
 * - asOkHttpJar() 桥接 OkHttp CookieJar
 * - SharedPreferences 持久化(杀掉 app 不丢)
 */
interface CookieManager {
    /** 获取某平台 Cookie,未配置返回 null */
    fun get(platformId: String): String?
    /** 设置某平台 Cookie(覆盖) */
    fun set(platformId: String, cookie: String)
    /** 清除某平台或全部 Cookie */
    fun clear(platformId: String? = null)
    /** 获取所有平台 cookie 映射(设置界面用) */
    fun getAll(): Map<String, String>
    /** 桥接 OkHttp CookieJar(网络层用) */
    fun asOkHttpJar(): CookieJar
}

/**
 * SharedPreferences 持久化 CookieManager
 *
 * 设计:
 * 1. platformCookies: 用户手动配置的各平台 cookie(SharedPreferences 持久化)
 * 2. allCookies: OkHttp CookieJar 运行时 cookie(内存,页面访问过程中积累的 did/webid 等)
 * 3. 手动配置的 cookie 注入到 allCookies(Domain=.xxx.com 对子域生效)
 * 4. 快手等需要登录态的平台,用户可在设置里填 cookie 后立即生效
 */
class PersistentCookieManager(context: Context) : CookieManager {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("cookie_prefs", Context.MODE_PRIVATE)

    /** 用户手动配置的各平台 cookie(从 SharedPreferences 读) */
    private val platformCookies = ConcurrentHashMap<String, String>()

    /** OkHttp 运行时 cookie 存储(内存,含手动注入 + 响应积累) */
    private val allCookies = mutableListOf<Cookie>()
    private val lock = Any()

    init {
        loadFromPrefs()
        injectWeiboCookie()
    }

    private fun loadFromPrefs() {
        val map = prefs.all
        for ((key, value) in map) {
            if (value is String && value.isNotEmpty()) {
                platformCookies[key] = value
                injectPlatformCookie(key, value)
            }
        }
    }

    /** 把某平台 cookie 注入到 OkHttp CookieJar */
    private fun injectPlatformCookie(platformId: String, cookieStr: String) {
        val domain = when (platformId) {
            "xiaohongshu" -> ".xiaohongshu.com"
            "weibo" -> ".weibo.com"
            "douyin" -> ".douyin.com"
            "kuaishou" -> ".kuaishou.com"
            "bilibili" -> ".bilibili.com"
            else -> return
        }
        val url = "https://www${domain}/".toHttpUrl()
        // cookie 可能是 "key=value; key2=value2" 格式,拆成多个 Cookie
        val parts = cookieStr.split(";").map { it.trim() }.filter { it.contains("=") }
        for (part in parts) {
            val cookie = Cookie.parse(url, "$part; Domain=$domain; Path=/")
            if (cookie != null) {
                synchronized(lock) {
                    allCookies.removeAll {
                        it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path
                    }
                    allCookies.add(cookie)
                }
            }
        }
    }

    private fun injectWeiboCookie() {
        // 如果用户配置了微博 cookie,不用默认的
        if (platformCookies.containsKey("weibo")) return
        val sub = "SUB=_2AkMR47Mlf8NxqwFRmfocxG_lbox2wg7EieKnv0L-JRMxHRl-yT9yqhFdtRB6OmOdyoia9pKPkqoHRRmSBA_WNPaHuybH"
        injectPlatformCookie("weibo", sub)
    }

    override fun get(platformId: String): String? = platformCookies[platformId]

    override fun set(platformId: String, cookie: String) {
        platformCookies[platformId] = cookie
        prefs.edit().putString(platformId, cookie).apply()
        // 重新注入(先清旧的再加新的)
        val domain = when (platformId) {
            "xiaohongshu" -> ".xiaohongshu.com"
            "weibo" -> ".weibo.com"
            "douyin" -> ".douyin.com"
            "kuaishou" -> ".kuaishou.com"
            "bilibili" -> ".bilibili.com"
            else -> null
        }
        if (domain != null) {
            val url = "https://www${domain}/".toHttpUrl()
            val parts = cookie.split(";").map { it.trim() }.filter { it.contains("=") }
            synchronized(lock) {
                allCookies.removeAll { it.domain == domain }
                for (part in parts) {
                    val c = Cookie.parse(url, "$part; Domain=$domain; Path=/")
                    if (c != null) allCookies.add(c)
                }
            }
        }
    }

    override fun clear(platformId: String?) {
        if (platformId == null) {
            platformCookies.clear()
            prefs.edit().clear().apply()
            synchronized(lock) { allCookies.clear() }
            injectWeiboCookie()
        } else {
            platformCookies.remove(platformId)
            prefs.edit().remove(platformId).apply()
            val domain = when (platformId) {
                "xiaohongshu" -> ".xiaohongshu.com"
                "weibo" -> ".weibo.com"
                "douyin" -> ".douyin.com"
                "kuaishou" -> ".kuaishou.com"
                "bilibili" -> ".bilibili.com"
                else -> null
            }
            if (domain != null) {
                synchronized(lock) { allCookies.removeAll { it.domain == domain } }
            }
            if (platformId == "weibo") injectWeiboCookie()
        }
    }

    override fun getAll(): Map<String, String> = platformCookies.toMap()

    override fun asOkHttpJar(): CookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            synchronized(lock) {
                for (cookie in cookies) {
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
                val now = System.currentTimeMillis()
                allCookies.removeAll { it.expiresAt < now }
                return allCookies.filter { it.matches(url) }
            }
        }
    }
}
