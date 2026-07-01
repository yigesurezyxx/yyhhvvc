package com.parsehub.app.data

import android.content.Context
import java.io.File

/**
 * IParseRepository 测试替身(spec 6.2:ViewModel 单测注入)。
 *
 * - 同步返回预设结果,无网络 IO
 * - 暴露调用计数/最后 URL/阶段记录,便于断言
 */
class FakeParseRepository : IParseRepository {

    var parseResult: ParseResult = ParseResult()
    var downloadPath: String? = null
    var saveToGalleryResult: Boolean = true
    var throwOnParse: Boolean = false

    var parseCallCount: Int = 0
        private set
    var lastParseUrl: String? = null
        private set
    var lastDownloadMedia: MediaInfo? = null
        private set
    val parseStages = mutableListOf<ParseStage>()

    override suspend fun parse(
        url: String,
        onProgress: ((ParseStage) -> Unit)?
    ): ParseResult {
        parseCallCount++
        lastParseUrl = url
        if (throwOnParse) throw RuntimeException("fake parse error")
        ParseStage.values().forEach { stage ->
            parseStages.add(stage)
            onProgress?.invoke(stage)
        }
        return parseResult
    }

    override suspend fun downloadMedia(
        media: MediaInfo,
        referer: String?,
        onProgress: ((Int) -> Unit)?
    ): String? {
        lastDownloadMedia = media
        return downloadPath
    }

    override fun saveToGallery(file: File, type: String): Boolean {
        return saveToGalleryResult
    }
}

/**
 * IParseHistory 测试替身(spec 6.2)。
 *
 * - 内存 List,无 SharedPreferences/Context 依赖
 * - init 是 no-op,测试不需要 Context
 */
class FakeParseHistory : IParseHistory {
    private val items = mutableListOf<HistoryItem>()

    override fun init(context: Context) {
        // no-op for tests
    }

    override fun load(): List<HistoryItem> = items.toList()

    override fun add(item: HistoryItem) {
        items.removeAll { it.url == item.url }
        items.add(0, item)
    }

    override fun remove(url: String) {
        items.removeAll { it.url == url }
    }

    override fun clear() {
        items.clear()
    }

    /** 测试辅助:外部预填数据 */
    fun seed(item: HistoryItem) = add(item)
}
