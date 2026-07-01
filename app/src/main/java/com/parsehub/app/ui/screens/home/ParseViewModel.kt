package com.parsehub.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parsehub.app.data.HistoryItem
import com.parsehub.app.data.IParseHistory
import com.parsehub.app.data.IParseRepository
import com.parsehub.app.data.ParseError
import com.parsehub.app.data.ParseResult
import com.parsehub.app.data.ParseStage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * ParseHub 首页 ViewModel(spec 3.2)
 *
 * - 构造函数注入(便于 Hilt/Koin 接入与单测 Mock)
 * - 零 Compose 依赖,零 Context 依赖
 * - State 分片避免单一 data class 膨胀
 * - Intent 统一入口,UDF 单向数据流
 * - UiEffect 单次事件(Toast/Snackbar/Share/OpenFolder)
 */
class ParseViewModel(
    private val repository: IParseRepository,
    private val history: IParseHistory,
    private val clipboardProvider: () -> String? = { null },
    private val themeToggler: () -> Unit = {}
) : ViewModel() {

    private val _uiState = MutableStateFlow(ParseUiState())
    val uiState: StateFlow<ParseUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<UiEffect>(extraBufferCapacity = 8)
    val effects: SharedFlow<UiEffect> = _effects.asSharedFlow()

    private var parseStartTime = 0L
    private var parseTimerJob: Job? = null

    init {
        // 初始化时加载历史
        _uiState.update { it.copy(history = it.history.copy(items = history.load())) }
    }

    fun dispatch(intent: ParseIntent) {
        when (intent) {
            is ParseIntent.UrlChanged -> handleUrlChanged(intent.url)
            ParseIntent.Paste -> handlePaste()
            is ParseIntent.Parse -> startParse(intent.targetUrl ?: _uiState.value.input.url)
            ParseIntent.Retry -> startParse(_uiState.value.parse.lastParsedUrl)
            ParseIntent.Download -> startDownload()
            is ParseIntent.DeleteHistory -> deleteHistory(intent.item)
            ParseIntent.ToggleHistory -> toggleHistory()
            is ParseIntent.HistoryItemClick -> {
                handleUrlChanged(intent.item.url)
                startParse(intent.item.url)
            }
            ParseIntent.ClearAllHistory -> clearAllHistory()
            ParseIntent.ToggleTheme -> themeToggler()
        }
    }

    // ===== Input =====

    private fun handleUrlChanged(url: String) {
        _uiState.update {
            it.copy(input = it.input.copy(
                url = url,
                detectedPlatformId = detectPlatformId(url)
            ))
        }
    }

    private fun handlePaste() {
        val clip = clipboardProvider() ?: return
        handleUrlChanged(clip)
    }

    // ===== Parse =====

    private fun startParse(targetUrl: String) {
        if (targetUrl.isBlank()) return
        parseTimerJob?.cancel()
        _uiState.update {
            it.copy(
                parse = it.parse.copy(
                    isParsing = true,
                    stage = ParseStage.DETECTING,
                    elapsedMs = 0L,
                    result = null,
                    error = null,
                    lastParsedUrl = targetUrl
                ),
                download = DownloadState()
            )
        }
        parseStartTime = System.currentTimeMillis()
        startParseTimer()

        viewModelScope.launch {
            val result = try {
                repository.parse(targetUrl.trim()) { stage ->
                    _uiState.update { it.copy(parse = it.parse.copy(stage = stage)) }
                }
            } catch (e: Exception) {
                ParseResult(error = e.message ?: "解析失败")
            }
            stopParseTimer()
            if (result.error != null) {
                _uiState.update {
                    it.copy(parse = it.parse.copy(
                        isParsing = false,
                        stage = null,
                        result = result,
                        error = classifyError(result.error)
                    ))
                }
            } else {
                _uiState.update {
                    it.copy(parse = it.parse.copy(
                        isParsing = false,
                        stage = null,
                        result = result,
                        error = null
                    ))
                }
                if (result.hasMedia) {
                    val item = HistoryItem(
                        url = targetUrl.trim(),
                        platform = result.platform ?: idToLabel(detectPlatformId(targetUrl)) ?: "未知",
                        title = result.title.ifBlank { result.content.take(30).ifBlank { "未命名" } },
                        timestamp = System.currentTimeMillis()
                    )
                    history.add(item)
                    _uiState.update { it.copy(history = it.history.copy(items = history.load())) }
                }
            }
        }
    }

    private fun startParseTimer() {
        parseTimerJob = viewModelScope.launch {
            while (true) {
                _uiState.update {
                    it.copy(parse = it.parse.copy(elapsedMs = System.currentTimeMillis() - parseStartTime))
                }
                delay(100L)
            }
        }
    }

    private fun stopParseTimer() {
        parseTimerJob?.cancel()
        parseTimerJob = null
        _uiState.update {
            it.copy(parse = it.parse.copy(elapsedMs = System.currentTimeMillis() - parseStartTime))
        }
    }

    // ===== Download =====

    private fun startDownload() {
        val result = _uiState.value.parse.result ?: return
        if (!result.hasMedia) return

        _uiState.update {
            it.copy(download = DownloadState(
                isDownloading = true,
                status = "下载中...",
                success = false
            ))
        }

        viewModelScope.launch {
            try {
                var savedCount = 0
                val totalCount = result.media.size
                for ((index, media) in result.media.withIndex()) {
                    _uiState.update {
                        it.copy(download = it.download.copy(status = "下载中 (${index + 1}/$totalCount)..."))
                    }
                    val localPath = repository.downloadMedia(media)
                    if (localPath != null) {
                        val file = File(localPath)
                        if (file.exists() && repository.saveToGallery(file, media.type)) {
                            savedCount++
                        }
                    }
                }
                _uiState.update {
                    it.copy(download = DownloadState(
                        isDownloading = false,
                        status = if (savedCount > 0) "已保存 $savedCount 个文件到相册" else "保存失败",
                        success = savedCount > 0
                    ))
                }
                if (savedCount > 0) {
                    _effects.emit(UiEffect.Toast("已保存 $savedCount 个文件"))
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(download = DownloadState(
                        isDownloading = false,
                        status = "下载失败: ${e.message}",
                        success = false
                    ))
                }
            }
        }
    }

    // ===== History =====

    private fun deleteHistory(item: HistoryItem) {
        history.remove(item.url)
        _uiState.update { it.copy(history = it.history.copy(items = history.load())) }
    }

    private fun toggleHistory() {
        _uiState.update { it.copy(history = it.history.copy(expanded = !it.history.expanded)) }
    }

    private fun clearAllHistory() {
        history.clear()
        _uiState.update { it.copy(history = it.history.copy(items = emptyList())) }
    }

    // ===== Helpers =====

    private fun classifyError(message: String): ParseError {
        return when {
            message.contains("超时", true) || message.contains("timeout", true) -> ParseError.Network(message)
            message.contains("暂不支持", true) -> ParseError.Unsupported("")
            message.contains("无效", true) || message.contains("无法提取", true) -> ParseError.InvalidLink(message)
            else -> ParseError.ParseFailed(message)
        }
    }

    private fun detectPlatformId(url: String): String? {
        val u = url.trim()
        if (u.isBlank() || !u.contains("://", true)) return null
        return when {
            u.contains("xiaohongshu", true) || u.contains("xhslink", true) -> "xiaohongshu"
            u.contains("douyin", true) || u.contains("iesdouyin", true) -> "douyin"
            u.contains("bilibili", true) || u.contains("b23.tv", true) -> "bilibili"
            u.contains("weibo", true) -> "weibo"
            u.contains("kuaishou", true) || u.contains("gifshow", true) -> "kuaishou"
            u.contains("youtube", true) || u.contains("youtu.be", true) -> "youtube"
            u.contains("twitter", true) || u.contains("x.com", true) -> "twitter"
            u.contains("tieba", true) -> "tieba"
            else -> null
        }
    }

    private fun idToLabel(id: String?): String? = when (id) {
        "xiaohongshu" -> "小红书"
        "douyin" -> "抖音"
        "bilibili" -> "B站"
        "weibo" -> "微博"
        "kuaishou" -> "快手"
        "youtube" -> "YouTube"
        "twitter" -> "Twitter"
        "tieba" -> "贴吧"
        else -> null
    }

    override fun onCleared() {
        super.onCleared()
        parseTimerJob?.cancel()
    }
}

// ===== State 分片(spec 3.2) =====

data class ParseUiState(
    val input: InputState = InputState(),
    val parse: ParseState = ParseState(),
    val download: DownloadState = DownloadState(),
    val history: HistoryState = HistoryState()
)

data class InputState(
    val url: String = "",
    val detectedPlatformId: String? = null
)

data class ParseState(
    val isParsing: Boolean = false,
    val stage: ParseStage? = null,
    val elapsedMs: Long = 0L,
    val result: ParseResult? = null,
    val error: ParseError? = null,
    val lastParsedUrl: String = ""
)

data class DownloadState(
    val isDownloading: Boolean = false,
    val status: String? = null,
    val success: Boolean = false
)

data class HistoryState(
    val items: List<HistoryItem> = emptyList(),
    val expanded: Boolean = false
)

// ===== Intent(spec 3.2 统一入口) =====

sealed interface ParseIntent {
    data class UrlChanged(val url: String) : ParseIntent
    object Paste : ParseIntent
    data class Parse(val targetUrl: String?) : ParseIntent
    object Retry : ParseIntent
    object Download : ParseIntent
    data class DeleteHistory(val item: HistoryItem) : ParseIntent
    object ToggleHistory : ParseIntent
    data class HistoryItemClick(val item: HistoryItem) : ParseIntent
    object ClearAllHistory : ParseIntent
    object ToggleTheme : ParseIntent
}

// ===== UiEffect(spec 3.2 单次事件) =====

sealed interface UiEffect {
    data class Toast(val message: String) : UiEffect
    data class Snackbar(val message: String) : UiEffect
    data class Share(val content: String) : UiEffect
    data class OpenFolder(val path: String) : UiEffect
}
