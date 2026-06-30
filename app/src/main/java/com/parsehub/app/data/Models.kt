package com.parsehub.app.data

data class MediaInfo(
    val type: String = "image",
    val url: String? = null,
    val thumbUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val duration: Int? = null,
    val size: Long? = null,
    val ext: String? = "jpg",
    val localPath: String? = null
)

data class ParseResult(
    val platform: String? = null,
    val type: String? = null,
    val title: String = "",
    val content: String = "",
    val rawUrl: String? = null,
    val author: String? = null,
    val avatar: String? = null,
    val media: List<MediaInfo> = emptyList(),
    val error: String? = null,
    val markdownContent: String? = null
) {
    val isSuccess: Boolean get() = error == null
    val hasMedia: Boolean get() = media.isNotEmpty()
}

data class DownloadResult(
    val outputDir: String,
    val media: List<MediaInfo>,
    val error: String? = null
) {
    val isSuccess: Boolean get() = error == null
}

data class PlatformInfo(
    val id: String,
    val name: String
)

/**
 * 解析阶段，用于 UI 进度展示
 */
enum class ParseStage(val label: String) {
    DETECTING("正在检测平台..."),
    FETCHING("正在抓取页面内容..."),
    DONE("解析完成")
}
