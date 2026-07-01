package com.parsehub.app.data

import android.content.Context
import android.util.Log
import com.parsehub.app.data.download.DownloadManager
import com.parsehub.app.data.network.InMemoryCookieManager
import com.parsehub.app.data.network.NetworkManager
import com.parsehub.app.data.parser.bilibili.BilibiliParser
import com.parsehub.app.data.parser.douyin.DouyinParser
import com.parsehub.app.data.parser.kuaishou.KuaishouParser
import com.parsehub.app.data.parser.registry.ParserRegistry
import com.parsehub.app.data.parser.weibo.WeiboParser
import com.parsehub.app.data.parser.xhs.XhsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * ParseRepository(spec 3.2:瘦身后仅编排 registry + downloader)
 *
 * 职责:
 * - 组装 ParserRegistry(注入 NetworkManager + CookieManager)
 * - parse() 委托 registry,加超时 + 进度回调
 * - download()/saveToGallery() 委托 DownloadManager
 *
 * VM 通过 [IParseRepository] 注入,本类仍是 DCL 单例。
 */
class ParseRepository private constructor(
    private val registry: ParserRegistry,
    private val downloader: DownloadManager
) : IParseRepository {

    private val TAG = "ParseRepository"

    override suspend fun parse(
        url: String,
        onProgress: ((ParseStage) -> Unit)?
    ): ParseResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        withTimeoutOrNull(20_000L) {
            try {
                val extracted = ParseUtils.extractUrlFromText(url.trim()) ?: url.trim()
                Log.d(TAG, "解析 URL: $extracted")
                registry.parse(extracted, onProgress)
            } catch (e: Exception) {
                Log.e(TAG, "解析异常", e)
                ParseResult(error = "解析出错: ${e.javaClass.simpleName} - ${e.message}")
            }
        } ?: ParseResult(
            error = "解析超时(已用时 ${System.currentTimeMillis() - start}ms),请稍后重试或检查网络"
        )
    }

    override suspend fun downloadMedia(
        media: MediaInfo,
        referer: String?,
        onProgress: ((Int) -> Unit)?
    ): String? = downloader.download(media, referer, onProgress)

    override fun saveToGallery(file: File, type: String): Boolean =
        downloader.saveToGallery(file, type)

    companion object {
        @Volatile
        private var instance: ParseRepository? = null

        fun getInstance(context: Context): ParseRepository =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): ParseRepository {
            val cookieManager = InMemoryCookieManager()
            val network = NetworkManager(cookieManager)
            val registry = ParserRegistry(
                parsers = listOf(
                    XhsParser(network, cookieManager),
                    DouyinParser(network, cookieManager),
                    BilibiliParser(network, cookieManager),
                    WeiboParser(network, cookieManager),
                    KuaishouParser(network, cookieManager)
                )
            )
            val downloader = DownloadManager(context, network)
            return ParseRepository(registry, downloader)
        }
    }
}
