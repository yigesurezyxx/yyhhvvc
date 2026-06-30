package com.parsehub.app.ui.screens

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.parsehub.app.data.MediaInfo
import com.parsehub.app.data.ParseRepository
import com.parsehub.app.data.ParseResult
import com.parsehub.app.data.ParseStage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
    var parseStage by remember { mutableStateOf<ParseStage?>(null) }
    var parseElapsedMs by remember { mutableStateOf(0L) }
    var lastParsedUrl by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    // 实时计时：解析中每 100ms 更新已用时
    LaunchedEffect(isParsing) {
        if (isParsing) {
            parseElapsedMs = 0L
            while (isParsing) {
                parseElapsedMs += 100L
                delay(100L)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            downloadAndSave(parseResult, scope, context,
                onStatus = { downloadStatus = it },
                onSuccess = { downloadSuccess = it },
                onLoading = { isDownloading = it }
            )
        }
    }

    fun pasteFromClipboard() {
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            url = clip.getItemAt(0).text.toString()
        }
    }

    fun clearInput() {
        url = ""
        parseResult = null
        errorMessage = null
        parseStage = null
        downloadStatus = null
        downloadSuccess = false
    }

    fun doParse(targetUrl: String = url) {
        if (targetUrl.isBlank()) return
        isParsing = true
        errorMessage = null
        parseResult = null
        parseStage = ParseStage.DETECTING
        downloadStatus = null
        downloadSuccess = false
        lastParsedUrl = targetUrl

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    ParseRepository.getInstance(context).parse(targetUrl.trim()) { stage ->
                        parseStage = stage
                    }
                } catch (e: Exception) {
                    ParseResult(
                        error = e.message ?: "解析失败"
                    )
                }
            }
            parseResult = result
            isParsing = false
            parseStage = null
            if (result.error != null) {
                errorMessage = result.error
            }
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

    val detectedPlatform = remember(url) { detectPlatformLabel(url) }

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

        Spacer(modifier = Modifier.height(32.dp))

        // 智能输入框：带示例水印 + 粘贴图标 + 清空按钮
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    "粘贴分享链接，例如\nhttps://www.xiaohongshu.com/explore/xxx?xsec_token=...",
                    maxLines = 2,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            shape = RoundedCornerShape(16.dp),
            singleLine = false,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { doParse() }),
            leadingIcon = {
                IconButton(onClick = { pasteFromClipboard() }) {
                    Icon(Icons.Default.ContentPaste, contentDescription = "粘贴")
                }
            },
            trailingIcon = {
                if (url.isNotEmpty()) {
                    IconButton(onClick = { clearInput() }) {
                        Text("✕", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        )

        // 平台自动识别提示
        AnimatedVisibility(
            visible = detectedPlatform != null && !isParsing,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, start = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📱 检测到：$detectedPlatform",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

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
                Text("🚀 开始解析", style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 解析状态可视化卡片
        AnimatedVisibility(
            visible = isParsing,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            ParsingProgressCard(stage = parseStage, elapsedMs = parseElapsedMs)
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 错误提示（带重试）
        errorMessage?.let { error ->
            ErrorCard(
                error = error,
                onRetry = { doParse(lastParsedUrl) }
            )
        }

        parseResult?.takeIf { it.isSuccess }?.let { result ->
            ResultCard(
                result = result,
                isDownloading = isDownloading,
                downloadStatus = downloadStatus,
                downloadSuccess = downloadSuccess,
                onDownloadClick = { onDownloadClick() }
            )
        }

        if (!isParsing && parseResult == null && errorMessage == null) {
            Spacer(modifier = Modifier.height(40.dp))
            Text(
                text = "✨ 支持平台\n抖音 · B站 · YouTube · 小红书\nTwitter · 微博 · 贴吧 · 快手",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp
            )
        }
    }
}

/**
 * 解析进度卡片：展示当前阶段 + 已用时 + 动画进度条
 */
@Composable
private fun ParsingProgressCard(stage: ParseStage?, elapsedMs: Long) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "🔍 ${stage?.label ?: "解析中..."}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "⏱ %.1fs".format(elapsedMs / 1000.0),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "正在与平台服务器通信，请稍候...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * 错误卡片：友好提示 + 重试按钮
 */
@Composable
private fun ErrorCard(error: String, onRetry: () -> Unit) {
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
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onRetry,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("重试")
            }
        }
    }
}

/**
 * 解析结果卡片
 */
@Composable
private fun ResultCard(
    result: ParseResult,
    isDownloading: Boolean,
    downloadStatus: String?,
    downloadSuccess: Boolean,
    onDownloadClick: () -> Unit
) {
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
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "共 ${result.media.size} 个文件",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

/**
 * 根据输入的 URL 实时检测平台（仅用于 UI 提示）
 */
private fun detectPlatformLabel(input: String): String? {
    val url = input.trim()
    if (url.isBlank() || !url.contains("://", true)) return null
    return when {
        url.contains("xiaohongshu", true) || url.contains("xhslink", true) -> "小红书"
        url.contains("douyin", true) || url.contains("iesdouyin", true) -> "抖音"
        url.contains("bilibili", true) || url.contains("b23.tv", true) -> "B站"
        url.contains("weibo", true) -> "微博"
        url.contains("kuaishou", true) || url.contains("gifshow", true) -> "快手"
        url.contains("youtube", true) || url.contains("youtu.be", true) -> "YouTube"
        url.contains("twitter", true) || url.contains("x.com", true) -> "Twitter"
        url.contains("tieba", true) -> "贴吧"
        else -> null
    }
}

private fun downloadAndSave(
    result: ParseResult?,
    scope: CoroutineScope,
    context: Context,
    onStatus: (String) -> Unit,
    onSuccess: (Boolean) -> Unit,
    onLoading: (Boolean) -> Unit
) {
    if (result == null || !result.hasMedia) return
    onLoading(true)
    onStatus("下载中...")
    onSuccess(false)

    scope.launch {
        try {
            var savedCount = 0
            val totalCount = result.media.size

            for ((index, media) in result.media.withIndex()) {
                onStatus("下载中 (${index + 1}/$totalCount)...")

                val localPath = withContext(Dispatchers.IO) {
                    ParseRepository.getInstance(context).downloadMedia(media)
                }

                if (localPath != null) {
                    val file = File(localPath)
                    if (file.exists()) {
                        val saved = ParseRepository.getInstance(context)
                            .saveToGallery(file, media.type)
                        if (saved) savedCount++
                    }
                }
            }

            onSuccess(savedCount > 0)
            onStatus(if (savedCount > 0) {
                "已保存 $savedCount 个文件到相册"
            } else {
                "保存失败"
            })
        } catch (e: Exception) {
            onStatus("下载失败: ${e.message}")
        }
        onLoading(false)
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
                Text("▶", color = Color.White, fontSize = 24.sp)
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
                Text("▶", color = Color.White, fontSize = 16.sp)
            }
        }
    }
}

private fun isVideoType(type: String): Boolean {
    return type.contains("Video", ignoreCase = true) ||
           type.contains("Live", ignoreCase = true) ||
           type.contains("video", ignoreCase = true)
}
