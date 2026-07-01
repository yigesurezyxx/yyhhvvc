package com.parsehub.app.data.parser.base

/**
 * JSON 清洗模块(spec 5.3:处理 JS 字面量 → 合法 JSON)
 *
 * 处理审计报告 P1-⑤ 指出的问题:
 * - undefined → null
 * - NaN → null
 * - Infinity / -Infinity → null
 * - BigInt(123n) → 123
 * - new Date(123) → 123 / new Date() → 0
 *
 * 这些字面量会导致 JSONObject(jsonStr) 直接异常。
 */
object JsonSanitizer {
    private val UNDEFINED = Regex("""\bundefined\b""")
    private val NAN = Regex("""\bNaN\b""")
    private val INFINITY = Regex("""-?\bInfinity\b""")
    private val BIGINT = Regex("""(\d+)n""")
    private val NEW_DATE = Regex("""new\s+Date\(\s*(\d*)\s*\)""")

    /** 清洗 JS 字面量为合法 JSON */
    fun sanitize(jsonStr: String): String {
        return jsonStr
            .replace(NEW_DATE) { m -> m.groupValues[1].ifBlank { "0" } }
            .replace(BIGINT) { m -> m.groupValues[1] }
            .replace(UNDEFINED, "null")
            .replace(NAN, "null")
            .replace(INFINITY, "null")
    }
}
