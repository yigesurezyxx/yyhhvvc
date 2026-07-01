package com.parsehub.app.data.parser.kuaishou

import com.parsehub.app.data.ParseResult
import com.parsehub.app.data.network.CookieManager
import com.parsehub.app.data.network.NetworkManager
import com.parsehub.app.data.parser.base.BaseParser
import com.parsehub.app.data.parser.registry.IParser

/**
 * 快手解析器 — 占位(spec 3.2:原 Repository.parseKuaishou 返回"暂不支持")
 *
 * 后续实现时填充 doParse 逻辑,无需改 Registry。
 */
class KuaishouParser(
    network: NetworkManager,
    cookieManager: CookieManager
) : BaseParser(network.client, cookieManager.get("kuaishou")), IParser {

    override val platformId = "kuaishou"
    override val displayName = "快手"
    override val platform = displayName
    override val matchPattern = """(kuaishou|gifshow)\.com"""

    override suspend fun doParse(url: String): ParseResult {
        return ParseResult(
            platform = platform,
            error = "快手解析暂不支持"
        )
    }

    override fun matches(url: String): Boolean =
        matchPattern.toRegex(RegexOption.IGNORE_CASE).containsMatchIn(url)
}
