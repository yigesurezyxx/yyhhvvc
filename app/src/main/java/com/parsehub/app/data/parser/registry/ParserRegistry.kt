package com.parsehub.app.data.parser.registry

import android.util.Log
import com.parsehub.app.data.ParseResult
import com.parsehub.app.data.ParseStage

/**
 * 解析器注册中心(spec 4.1:替代 Repository 的 when(platform) 硬编码)
 *
 * - 持有所有平台 [IParser] 实例
 * - 按 URL 匹配调度,无匹配返回 Unsupported 错误
 * - 新增平台只需在构造时加入 parsers 列表
 *
 * Repository 只调用 [parse],不再关心具体平台。
 */
class ParserRegistry(
    private val parsers: List<IParser>
) {
    private val TAG = "ParserRegistry"

    /** 按 URL 匹配返回平台 id,未匹配返回 null */
    fun matchPlatform(url: String): String? = PlatformMatcher.match(url)

    /**
     * 调度解析
     * @param url 用户输入的 URL
     * @param onProgress 进度回调(FETCHING 阶段)
     * @return 解析结果,无匹配平台返回错误
     */
    suspend fun parse(url: String, onProgress: ((ParseStage) -> Unit)? = null): ParseResult {
        val platformId = matchPlatform(url)
        if (platformId == null) {
            Log.d(TAG, "未识别平台: $url")
            return ParseResult(error = "无法识别平台,请检查链接")
        }

        val parser = parsers.firstOrNull { it.platformId == platformId }
            ?: return ParseResult(
                platform = platformId,
                error = "$platformId 暂不支持"
            )

        Log.d(TAG, "调度 $platformId 解析: $url")
        onProgress?.invoke(ParseStage.FETCHING)
        return parser.parse(url)
    }

    /** 所有已注册平台(供 UI 展示支持列表用) */
    fun registeredPlatforms(): List<Pair<String, String>> =
        parsers.map { it.platformId to it.displayName }
}
