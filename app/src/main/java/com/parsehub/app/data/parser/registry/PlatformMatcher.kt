package com.parsehub.app.data.parser.registry

/**
 * 平台匹配器(spec 4.3:替代 Repository.detectPlatform 的 when 硬编码)
 *
 * 数据驱动:新增平台只改 [PATTERNS] 列表,不改匹配逻辑。
 * 正则预编译(对齐审计报告 P1:Regex 缓存,避免每次 new)。
 */
object PlatformMatcher {

    private val PATTERNS: List<Pair<String, Regex>> = listOf(
        "douyin" to Regex("""(douyin|iesdouyin)\.com""", RegexOption.IGNORE_CASE),
        "bilibili" to Regex("""(bilibili|b23)\.(com|tv)""", RegexOption.IGNORE_CASE),
        "kuaishou" to Regex("""(kuaishou|gifshow)\.com""", RegexOption.IGNORE_CASE),
        "weibo" to Regex("""weibo\.(com|cn)""", RegexOption.IGNORE_CASE),
        "xiaohongshu" to Regex("""(xiaohongshu|xhslink)\.com""", RegexOption.IGNORE_CASE),
        "youtube" to Regex("""(youtube|youtu)\.(com|be)""", RegexOption.IGNORE_CASE),
        "twitter" to Regex("""(twitter|x)\.com""", RegexOption.IGNORE_CASE),
        "tieba" to Regex("""tieba\.baidu\.com""", RegexOption.IGNORE_CASE)
    )

    /** 返回平台 id,未匹配返回 null */
    fun match(url: String): String? =
        PATTERNS.firstOrNull { it.second.containsMatchIn(url) }?.first

    /** 返回所有已注册平台 id */
    fun allPlatformIds(): List<String> = PATTERNS.map { it.first }
}
