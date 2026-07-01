package com.parsehub.app.data.parser.registry

import com.parsehub.app.data.ParseResult

/**
 * 平台解析器接口(spec 4.2)
 *
 * Registry 只依赖此接口,新增平台不改 Registry(开闭原则)。
 * 各平台 Parser 实现此接口,内部可继承 BaseParser 复用 URL 处理逻辑。
 */
interface IParser {
    /** 平台 id(与 PlatformMatcher 返回值一致,如 "xiaohongshu") */
    val platformId: String
    /** 显示名(如 "小红书") */
    val displayName: String
    /** URL 是否属于本平台 */
    fun matches(url: String): Boolean
    /** 执行解析 */
    suspend fun parse(url: String): ParseResult
}
