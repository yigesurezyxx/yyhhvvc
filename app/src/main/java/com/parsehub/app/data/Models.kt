package com.parsehub.app.data

data class MediaInfo(
    val type: String,
    val url: String?,
    val thumbUrl: String?,
    val width: Int?,
    val height: Int?,
    val duration: Int?,
    val size: Long?,
    val ext: String?,
    val localPath: String? = null
)

data class ParseResult(
    val platform: String?,
    val type: String?,
    val title: String,
    val content: String,
    val rawUrl: String?,
    val author: String?,
    val avatar: String?,
    val media: List<MediaInfo>,
    val error: String? = null
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
