package com.parsehub.app.ui.screens

import android.Manifest
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.parsehub.app.data.MediaInfo
import com.parsehub.app.data.ParseRepository
import com.parsehub.app.data.ParseResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.ClipboardManager
import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParseScreen() {
    var url by remember { mutableStateOf("") }
    var isParsing by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var parseResult by remember { mutableStateOf<ParseResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var downloadStatus by remember { mutableStateOf<String?>(null) }
    var downloadSuccess by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            downloadAndSave(parseResult)
        }
    }

    fun pasteFromClipboard() {
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            url = clip.getItemAt(0).text.toString()
        }
    }

    fun doParse() {
        if (url.isBlank()) return
        isParsing = true
        errorMessage = null
        parseResult = null
        downloadStatus = null
        downloadSuccess = false

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    ParseRepository.getInstance().parse(url.trim())
                } catch (e: Exception) {
                    ParseResult(
                        platform = null,
                        type = null,
                        title = "",
                        content = "",
                        rawUrl = null,
                        author = null,
                        avatar = null,
                        media = emptyList(),
                        error = e.message ?: "解析失败"
                    )
                }
            }
            parseResult = result
            isParsing = false
            if (result.error != null) {
                errorMessage = result.error
            }
        }
    }

    fun downloadAndSave(result: ParseResult?) {
        if (result == null || !result.hasMedia) return
        isDownloading = true
        downloadStatus = "下载中..."
        downloadSuccess = false

        scope.launch {
            try {
                val downloadResult = withContext(Dispatchers.IO) {
                    ParseRepository.getInstance().download(url.trim())
                }
                if (!downloadResult.isSuccess) {
                    downloadStatus = "下载失败: ${downloadResult.error}"
                    isDownloading = false
                    return@launch
                }

                var savedCount = 0
                for (media in downloadResult.media) {
                    val localPath = media.localPath ?: continue
                    val file = File(localPath)
                    if (!file.exists()) continue

                    val saved = saveToGallery(context, file, media.type)
                    if (saved) savedCount++
                }

                downloadSuccess = savedCount > 0
                downloadStatus = if (savedCount > 0) {
                    "已保存 $savedCount 个文件到相册"
                } else {
                    "保存失败"
                }
            } catch (e: Exception) {
                downloadStatus = "下载失败: ${e.message}"
            }
            isDownloading = false
        }
    }

    fun onDownloadClick() {
        val result = parseResult ?: return
        if (!result.hasMedia) return

        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
        }

        permissionLauncher.launch(permissions.toTypedArray())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "🔗 ParseHub",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "粘贴链接，一键解析下载",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(40.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("粘贴分享链接...") },
            shape = RoundedCornerShape(16.dp),
            singleLine = false,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { doParse() }),
            leadingIcon = {
                IconButton(onClick = { pasteFromClipboard() }) {
                    Icon(Icons.Default.ContentPaste, contentDescription = "粘贴")
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { doParse() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = !isParsing && url.isNotBlank()
        ) {
            if (isParsing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("解析中...", style = MaterialTheme.typography.titleMedium)
            } else {
                Text("开始解析", style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        errorMessage?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "❌ 解析失败",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        parseResult?.takeIf { it.isSuccess }?.let { result ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    result.platform?.let { platform ->
                        AssistChip(
                            onClick = {},
                            label = { Text(platform) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }

                    if (result.title.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = result.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (result.content.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = result.content,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (result.hasMedia) {
                        Spacer(modifier = Modifier.height(14.dp))
                        MediaGrid(mediaList = result.media)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { onDownloadClick() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isDownloading && result.hasMedia
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("下载中...")
                        } else {
                            Text("📥 下载到相册")
                        }
                    }

                    downloadStatus?.let { status ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (downloadSuccess) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = status,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (downloadSuccess) Color(0xFF4CAF50) 
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (!isParsing && parseResult == null && errorMessage == null) {
            Spacer(modifier = Modifier.height(60.dp))
            Text(
                text = "✨ 支持 16+ 平台\n抖音 · B站 · YouTube · 小红书\nTwitter · 微博 · 贴吧 · 快手",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = androidx.compose.ui.unit.sp(22.sp.value)
            )
        }
    }
}

@Composable
fun MediaGrid(mediaList: List<MediaInfo>) {
    val count = mediaList.size.coerceAtMost(9)
    val displayList = mediaList.take(count)

    if (count == 1) {
        SingleMedia(media = displayList[0])
    } else {
        val cols = if (count <= 4) 2 else 3
        val rows = (count + cols - 1) / cols

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (row in 0 until rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (col in 0 until cols) {
                        val index = row * cols + col
                        if (index < count) {
                            Box(
                                modifier = Modifier.weight(1f).aspectRatio(1f)
                            ) {
                                MediaThumbnail(media = displayList[index])
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    if (mediaList.size > 9) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "还有 ${mediaList.size - 9} 个文件...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SingleMedia(media: MediaInfo) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        val thumbUrl = media.thumbUrl ?: media.url
        if (thumbUrl != null) {
            AsyncImage(
                model = thumbUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        if (isVideoType(media.type)) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(56.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(28.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("▶", color = Color.White, fontSize = androidx.compose.ui.unit.sp(24.sp.value))
            }
        }
    }
}

@Composable
fun MediaThumbnail(media: MediaInfo) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        val thumbUrl = media.thumbUrl ?: media.url
        if (thumbUrl != null) {
            AsyncImage(
                model = thumbUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        if (isVideoType(media.type)) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(18.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("▶", color = Color.White, fontSize = androidx.compose.ui.unit.sp(16.sp.value))
            }
        }
    }
}

private fun isVideoType(type: String): Boolean {
    return type.contains("Video", ignoreCase = true) || 
           type.contains("Live", ignoreCase = true)
}

private fun saveToGallery(context: Context, file: File, type: String): Boolean {
    return try {
        val isVideo = isVideoType(type)
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
