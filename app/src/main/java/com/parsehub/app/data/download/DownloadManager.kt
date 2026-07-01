package com.parsehub.app.data.download

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.parsehub.app.data.MediaInfo
import com.parsehub.app.data.network.HeaderFactory
import com.parsehub.app.data.network.NetworkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * 下载管理器(spec 3.1:迁移 ParseRepository.downloadMedia + saveToGallery)
 *
 * 职责:
 * - 下载媒体文件到 cache(带进度回调)
 * - 保存到系统相册(MediaStore,Android 10+ scoped storage)
 *
 * 业务逻辑零改动,只做:
 * - package 迁移至 data/download/
 * - Header 改用 HeaderFactory.download(url)
 * - 网络请求复用 NetworkManager.client
 */
class DownloadManager(
    private val context: Context,
    private val network: NetworkManager
) {
    private val TAG = "DownloadManager"

    /**
     * 下载媒体文件
     * @return 下载后的本地文件路径,失败返回 null
     */
    suspend fun download(
        media: MediaInfo,
        referer: String? = null,
        onProgress: ((Int) -> Unit)? = null
    ): String? = withContext(Dispatchers.IO) {
        val url = media.url ?: return@withContext null
        try {
            // Header 优先用调用方传入的 referer,否则按 URL 域名自动选
            val headerMap = HeaderFactory.download(url).toMutableMap()
            if (referer != null) headerMap["Referer"] = referer

            val request = Request.Builder()
                .url(url)
                .apply { headerMap.forEach { (k, v) -> header(k, v) } }
                .get()
                .build()

            network.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "下载失败 HTTP ${response.code}")
                    return@withContext null
                }

                val body = response.body ?: return@withContext null
                val contentLength = body.contentLength()

                val cacheDir = File(context.cacheDir, "downloads")
                if (!cacheDir.exists()) cacheDir.mkdirs()

                val ext = media.ext ?: "mp4"
                val fileName = "parsehub_${System.currentTimeMillis()}.$ext"
                val file = File(cacheDir, fileName)

                FileOutputStream(file).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalRead = 0L
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (contentLength > 0 && onProgress != null) {
                                val progress = (totalRead * 100 / contentLength).toInt()
                                onProgress(progress)
                            }
                        }
                    }
                }

                if (file.length() < 1024) {
                    Log.e(TAG, "下载文件过小: ${file.length()} bytes")
                    file.delete()
                    return@withContext null
                }

                return@withContext file.absolutePath
            }
        } catch (e: Exception) {
            Log.e(TAG, "下载失败", e)
            null
        }
    }

    /**
     * 保存文件到系统相册
     * @param type media.type("video"/"image")
     * @return 成功返回 true
     */
    fun saveToGallery(file: File, type: String): Boolean {
        return try {
            val isVideo = type.contains("video", ignoreCase = true)
            val contentValues = ContentValues()

            val displayName = "ParseHub_${System.currentTimeMillis()}_${file.name}"
            val mimeType = if (isVideo) {
                when (file.extension.lowercase()) {
                    "mp4" -> "video/mp4"
                    "mov" -> "video/quicktime"
                    "mkv" -> "video/x-matroska"
                    else -> "video/*"
                }
            } else {
                when (file.extension.lowercase()) {
                    "jpg", "jpeg" -> "image/jpeg"
                    "png" -> "image/png"
                    "gif" -> "image/gif"
                    "webp" -> "image/webp"
                    "heic", "heif" -> "image/heif"
                    else -> "image/*"
                }
            }

            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, mimeType)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    if (isVideo) Environment.DIRECTORY_MOVIES + "/ParseHub"
                    else Environment.DIRECTORY_PICTURES + "/ParseHub"
                )
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val uri = if (isVideo) {
                context.contentResolver.insert(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues
                )
            } else {
                context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
                )
            } ?: return false

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                FileInputStream(file).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
