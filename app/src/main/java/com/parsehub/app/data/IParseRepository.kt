package com.parsehub.app.data

import java.io.File

/**
 * ParseRepository 接口(spec 3.3:接口化便于 Mock/Cloud 替换)
 *
 * 现有 [ParseRepository] 实现此接口。ViewModel 注入接口,单测注入 MockParseRepository。
 */
interface IParseRepository {
    suspend fun parse(
        url: String,
        onProgress: ((ParseStage) -> Unit)? = null
    ): ParseResult

    suspend fun downloadMedia(
        media: MediaInfo,
        referer: String? = null,
        onProgress: ((Int) -> Unit)? = null
    ): String?

    fun saveToGallery(file: File, type: String): Boolean
}
