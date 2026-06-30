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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
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
import com.parsehub.app.data.HistoryItem
import com.parsehub.app.data.MediaInfo
import com.parsehub.app.data.ParseHistory
import com.parsehub.app.data.ParseRepository
import com.parsehub.app.data.ParseResult
import com.parsehub.app.data.ParseStage
import com.parsehub.app.ui.theme.ThemeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    var historyList by remember { mutableStateOf<List<HistoryItem>>(emptyList()) }
    var historyExpanded by remember { mutableStateOf(false) }
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

    // 首次进入加载历史记录
    LaunchedEffect(Unit) {
        historyList = ParseHistory.load(context)
    }

    fun refreshHistory() {
        historyList = ParseHistory.load(context)
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
            } else if (result.hasMedia) {
                // 解析成功且有媒体，记录到历史
                ParseHistory.add(
                    context,
                    HistoryItem(
                        url = targetUrl.trim(),
                        platform = result.platform ?: detectPlatformLabel(targetUrl) ?: "未知",
                        title = result.title.ifBlank { result.content.take(30).ifBlank { "未命名" } },
                        timestamp = System.currentTimeMillis()
                    )
                )
                refreshHistory()
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "🔗 ParseHub",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            val mode = ThemeManager.currentMode.value
            IconButton(onClick = { ThemeManager.toggle(context) }) {
                Icon(
                    imageVector = when (mode) {
                        ThemeManager.Mode.DARK -> Icons.Default.LightMode
                        ThemeManager.Mode.LIGHT -> Icons.Default.DarkMode
                        ThemeManager.Mode.SYSTEM -> Icons.Default.LightMode
                    },
                    contentDescription = "切换主题",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

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

        // 解析历史列表（有记录时显示）
        if (historyList.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            HistorySection(
                items = historyList,
                expanded = historyExpanded,
                onToggleExpand = { historyExpanded = !historyExpanded },
                onItemClick = { item ->
                    url = item.url
                    doParse(item.url)
                },
                onItemDelete = { item ->
                    ParseHistory.remove(context, item.url)
                    refreshHistory()
                },
                onClearAll = {
                    ParseHistory.clear(context)
                    refreshHistory()
                }
            )
        }

        // 平台列表（可点击填入示例链接）
        Spacer(modifier = Modifier.height(24.dp))
        PlatformChips(
            onPlatformClick = { example ->
                url = example
            }
        )

        Spacer(modifier = Modifier.height(40.dp))
    }
}

/**
 * 解析历史折叠区
 */
@Composable
private fun HistorySection(
    items: List<HistoryItem>,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onItemClick: (HistoryItem) -> Unit,
    onItemDelete: (HistoryItem) -> Unit,
    onClearAll: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "最近解析（${items.size}）",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (expanded && items.isNotEmpty()) {
                        Text(
                            text = "清空",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.clickable { onClearAll() }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "收起" else "展开",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    items.forEach { item ->
                        Divider(modifier = Modifier.padding(vertical = 2.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onItemClick(item) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = platformIcon(item.platform),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${item.platform} · ${formatTime(item.timestamp)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { onItemDelete(item) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "删除",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 平台动态列表：点击填入示例链接（教学作用），区分已支持/即将支持
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlatformChips(onPlatformClick: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "支持平台",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            platformExamples.forEach { p ->
                val isSupported = p.supported
                AssistChip(
                    onClick = {
                        if (isSupported) onPlatformClick(p.example)
                    },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = p.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSupported) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            if (!isSupported) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = "即将支持",
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (isSupported)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "点击平台名可填入示例链接，灰色为即将支持",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

private data class PlatformExample(
    val name: String,
    val example: String,
    val supported: Boolean
)

private val platformExamples = listOf(
    PlatformExample("抖音", "https://www.iesdouyin.com/share/video/7300000000000000000", true),
    PlatformExample("B站", "https://www.bilibili.com/video/BV1xx411c7mD", true),
    PlatformExample("小红书", "https://www.xiaohongshu.com/explore/6700000000000000000?xsec_token=Abc123", true),
    PlatformExample("微博", "https://weibo.com/6001863056/5315444644776304", true),
    PlatformExample("快手", "https://v.kuaishou.com/xxx", true),
    PlatformExample("贴吧", "https://tieba.baidu.com/p/9000000000", true),
    PlatformExample("YouTube", "https://www.youtube.com/watch?v=dQw4w9WgXcQ", false),
    PlatformExample("Twitter", "https://twitter.com/user/status/1234567890", false)
)

private fun platformIcon(platform: String): String = when {
    platform.contains("小红书", true) -> "📕"
    platform.contains("抖音", true) -> "📹"
    platform.contains("B站", true) || platform.contains("bilibili", true) -> "▶️"
    platform.contains("微博", true) -> "🇨"
    platform.contains("快手", true) -> "⚡"
    platform.contains("youtube", true) -> "🎬"
    platform.contains("twitter", true) -> "🐦"
    platform.contains("贴吧", true) -> "📢"
    else -> "📄"
}

private fun formatTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000} 分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000} 小时前"
        diff < 7 * 86_400_000L -> "${diff / 86_400_000} 天前"
        else -> SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
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
