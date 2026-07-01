package com.parsehub.app.data.parser.base

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JsonSanitizer 单测(spec 9.1:5 类 JS 字面量)
 */
class JsonSanitizerTest {

    @Test
    fun `undefined replaced with null`() {
        val json = """{"a":undefined,"b":1}"""
        val sanitized = JsonSanitizer.sanitize(json)
        // 应能被 JSONObject 解析
        val obj = JSONObject(sanitized)
        assertTrue(obj.isNull("a"))
        assertEquals(1, obj.getInt("b"))
    }

    @Test
    fun `NaN replaced with null`() {
        val json = """{"ratio":NaN}"""
        val sanitized = JsonSanitizer.sanitize(json)
        val obj = JSONObject(sanitized)
        assertTrue(obj.isNull("ratio"))
    }

    @Test
    fun `Infinity replaced with null`() {
        val json = """{"v":Infinity}"""
        val sanitized = JsonSanitizer.sanitize(json)
        val obj = JSONObject(sanitized)
        assertTrue(obj.isNull("v"))
    }

    @Test
    fun `negative Infinity replaced with null`() {
        val json = """{"v":-Infinity}"""
        val sanitized = JsonSanitizer.sanitize(json)
        val obj = JSONObject(sanitized)
        assertTrue(obj.isNull("v"))
    }

    @Test
    fun `BigInt n suffix removed`() {
        val json = """{"id":123n}"""
        val sanitized = JsonSanitizer.sanitize(json)
        val obj = JSONObject(sanitized)
        assertEquals(123, obj.getInt("id"))
    }

    @Test
    fun `new Date with timestamp converted`() {
        val json = """{"ts":new Date(1700000000000)}"""
        val sanitized = JsonSanitizer.sanitize(json)
        val obj = JSONObject(sanitized)
        assertEquals(1700000000000L, obj.getLong("ts"))
    }

    @Test
    fun `new Date without args converts to zero`() {
        val json = """{"ts":new Date()}"""
        val sanitized = JsonSanitizer.sanitize(json)
        val obj = JSONObject(sanitized)
        assertEquals(0L, obj.getLong("ts"))
    }

    @Test
    fun `combined literals all cleaned`() {
        val json = """{"a":undefined,"b":NaN,"c":Infinity,"d":456n,"e":new Date(100)}"""
        val sanitized = JsonSanitizer.sanitize(json)
        val obj = JSONObject(sanitized)
        assertTrue(obj.isNull("a"))
        assertTrue(obj.isNull("b"))
        assertTrue(obj.isNull("c"))
        assertEquals(456, obj.getInt("d"))
        assertEquals(100, obj.getInt("e"))
    }
}
