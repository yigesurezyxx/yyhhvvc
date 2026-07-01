package com.parsehub.app.data.parser.registry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * PlatformMatcher 单测(spec 9.1:8 平台 + 未匹配)
 */
class PlatformMatcherTest {

    @Test
    fun `douyin standard url`() {
        assertEquals("douyin", PlatformMatcher.match("https://www.douyin.com/video/7300000000000000000"))
    }

    @Test
    fun `douyin iesdouyin url`() {
        assertEquals("douyin", PlatformMatcher.match("https://www.iesdouyin.com/share/video/123"))
    }

    @Test
    fun `bilibili standard url`() {
        assertEquals("bilibili", PlatformMatcher.match("https://www.bilibili.com/video/BV1xx411c7mD"))
    }

    @Test
    fun `bilibili short url`() {
        assertEquals("bilibili", PlatformMatcher.match("https://b23.tv/abc123"))
    }

    @Test
    fun `kuaishou url`() {
        assertEquals("kuaishou", PlatformMatcher.match("https://v.kuaishou.com/xxx"))
    }

    @Test
    fun `weibo url`() {
        assertEquals("weibo", PlatformMatcher.match("https://weibo.com/6001863056/5315444644776304"))
    }

    @Test
    fun `xiaohongshu url`() {
        assertEquals("xiaohongshu", PlatformMatcher.match("https://www.xiaohongshu.com/explore/6700000000000000000"))
    }

    @Test
    fun `xiaohongshu short url`() {
        assertEquals("xiaohongshu", PlatformMatcher.match("https://xhslink.com/abc"))
    }

    @Test
    fun `twitter x com url`() {
        assertEquals("twitter", PlatformMatcher.match("https://x.com/user/status/123"))
    }

    @Test
    fun `unknown platform returns null`() {
        assertNull(PlatformMatcher.match("https://www.example.com/some/page"))
    }

    @Test
    fun `all platform ids contains 8 platforms`() {
        val ids = PlatformMatcher.allPlatformIds()
        assertEquals(8, ids.size)
    }
}
