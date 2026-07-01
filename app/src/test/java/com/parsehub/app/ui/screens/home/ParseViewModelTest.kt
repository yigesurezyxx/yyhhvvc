package com.parsehub.app.ui.screens.home

import com.parsehub.app.data.FakeParseHistory
import com.parsehub.app.data.FakeParseRepository
import com.parsehub.app.data.HistoryItem
import com.parsehub.app.data.MediaInfo
import com.parsehub.app.data.ParseError
import com.parsehub.app.data.ParseResult
import com.parsehub.app.data.ParseStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ParseViewModel 单测(spec 6.2)
 *
 * 覆盖:
 * - Intent 派发 → State 变更
 * - 解析成功/失败/超时/异常 → State 正确
 * - UiEffect 发出时机(Toast on download success)
 * - 历史增删查 → HistoryState 正确
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ParseViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepo: FakeParseRepository
    private lateinit var fakeHistory: FakeParseHistory
    private var clipboardContent: String? = null
    private var themeToggleCount: Int = 0
    private lateinit var viewModel: ParseViewModel

    @Before
    fun setup() {
        // 关键:Main 与 TestScope 共享同一 scheduler,advanceUntilIdle 才能推进 VM 协程
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeParseRepository()
        fakeHistory = FakeParseHistory()
        clipboardContent = null
        themeToggleCount = 0
        viewModel = ParseViewModel(
            repository = fakeRepo,
            history = fakeHistory,
            clipboardProvider = { clipboardContent },
            themeToggler = { themeToggleCount++ }
        )
    }

    @After
    fun tearDown() {
        viewModel.onCleared()
        Dispatchers.resetMain()
    }

    // ===== 初始状态 =====

    @Test
    fun initial_state_isEmpty() {
        val state = viewModel.uiState.value
        assertEquals("", state.input.url)
        assertNull(state.input.detectedPlatformId)
        assertFalse(state.parse.isParsing)
        assertNull(state.parse.stage)
        assertEquals(0L, state.parse.elapsedMs)
        assertNull(state.parse.result)
        assertNull(state.parse.error)
        assertFalse(state.download.isDownloading)
        assertTrue(state.history.items.isEmpty())
        assertFalse(state.history.expanded)
    }

    // ===== Intent.UrlChanged =====

    @Test
    fun urlChanged_updates_url_and_detectedPlatformId() = runTest(testDispatcher) {
        viewModel.dispatch(ParseIntent.UrlChanged("https://www.douyin.com/video/123"))
        val input = viewModel.uiState.value.input
        assertEquals("https://www.douyin.com/video/123", input.url)
        assertEquals("douyin", input.detectedPlatformId)
    }

    @Test
    fun urlChanged_clears_detectedPlatformId_whenNoScheme() = runTest(testDispatcher) {
        viewModel.dispatch(ParseIntent.UrlChanged("just text no url"))
        val input = viewModel.uiState.value.input
        assertEquals("just text no url", input.url)
        assertNull(input.detectedPlatformId)
    }

    @Test
    fun urlChanged_detects_all_platforms() = runTest(testDispatcher) {
        val cases = mapOf(
            "https://www.xiaohongshu.com/explore/abc" to "xiaohongshu",
            "https://xhslink.com/a/abc" to "xiaohongshu",
            "https://www.iesdouyin.com/share/video/1" to "douyin",
            "https://www.bilibili.com/video/BV1xx" to "bilibili",
            "https://b23.tv/abc" to "bilibili",
            "https://weibo.com/123/abc" to "weibo",
            "https://v.kuaishou.com/abc" to "kuaishou",
            "https://www.youtube.com/watch?v=abc" to "youtube",
            "https://x.com/user/status/1" to "twitter",
            "https://tieba.baidu.com/p/1" to "tieba"
        )
        cases.forEach { (url, expectedId) ->
            viewModel.dispatch(ParseIntent.UrlChanged(url))
            assertEquals("platform for $url", expectedId, viewModel.uiState.value.input.detectedPlatformId)
        }
    }

    // ===== Intent.Paste =====

    @Test
    fun paste_uses_clipboardProvider_and_updates_state() = runTest(testDispatcher) {
        clipboardContent = "https://www.bilibili.com/video/BV1xx411c7mD"
        viewModel.dispatch(ParseIntent.Paste)
        val input = viewModel.uiState.value.input
        assertEquals("https://www.bilibili.com/video/BV1xx411c7mD", input.url)
        assertEquals("bilibili", input.detectedPlatformId)
    }

    @Test
    fun paste_doesNothing_when_clipboardEmpty() = runTest(testDispatcher) {
        clipboardContent = null
        viewModel.dispatch(ParseIntent.Paste)
        assertEquals("", viewModel.uiState.value.input.url)
    }

    // ===== Intent.Parse 成功路径 =====

    @Test
    fun parse_success_updates_result_and_adds_history() = runTest(testDispatcher) {
        fakeRepo.parseResult = ParseResult(
            platform = "抖音",
            title = "测试视频",
            content = "内容描述",
            media = listOf(MediaInfo(type = "video", url = "http://example.com/v.mp4"))
        )
        viewModel.dispatch(ParseIntent.Parse("https://www.douyin.com/video/123"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.parse.isParsing)
        assertNull(state.parse.stage)
        assertNotNull(state.parse.result)
        assertEquals("测试视频", state.parse.result?.title)
        assertNull(state.parse.error)
        assertEquals("https://www.douyin.com/video/123", state.parse.lastParsedUrl)

        // 历史已写入
        assertEquals(1, state.history.items.size)
        val historyItem = state.history.items[0]
        assertEquals("https://www.douyin.com/video/123", historyItem.url)
        assertEquals("抖音", historyItem.platform)
        assertEquals("测试视频", historyItem.title)
    }

    @Test
    fun parse_emits_all_stages_during_progress() = runTest(testDispatcher) {
        fakeRepo.parseResult = ParseResult(platform = "抖音")
        viewModel.dispatch(ParseIntent.Parse("https://www.douyin.com/video/1"))
        advanceUntilIdle()

        assertEquals(
            listOf(ParseStage.DETECTING, ParseStage.FETCHING, ParseStage.DONE),
            fakeRepo.parseStages
        )
    }

    @Test
    fun parse_success_without_media_does_not_add_history() = runTest(testDispatcher) {
        fakeRepo.parseResult = ParseResult(platform = "抖音", title = "无媒体")
        viewModel.dispatch(ParseIntent.Parse("https://www.douyin.com/video/1"))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.history.items.isEmpty())
    }

    @Test
    fun parse_blankUrl_isNoop() = runTest(testDispatcher) {
        viewModel.dispatch(ParseIntent.Parse("   "))
        advanceUntilIdle()
        assertEquals(0, fakeRepo.parseCallCount)
        assertFalse(viewModel.uiState.value.parse.isParsing)
    }

    // ===== Intent.Parse 失败路径 =====

    @Test
    fun parse_failure_sets_error_and_no_history() = runTest(testDispatcher) {
        fakeRepo.parseResult = ParseResult(error = "解析失败:无法获取数据")
        viewModel.dispatch(ParseIntent.Parse("https://www.douyin.com/video/1"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.parse.isParsing)
        assertNotNull(state.parse.error)
        assertTrue("应为 ParseFailed 类型", state.parse.error is ParseError.ParseFailed)
        assertEquals("解析失败:无法获取数据", state.parse.error?.message)
        assertTrue(state.history.items.isEmpty())
    }

    @Test
    fun parse_timeout_classified_as_network_error() = runTest(testDispatcher) {
        fakeRepo.parseResult = ParseResult(error = "解析超时,请稍后重试")
        viewModel.dispatch(ParseIntent.Parse("https://www.douyin.com/video/1"))
        advanceUntilIdle()

        val error = viewModel.uiState.value.parse.error
        assertNotNull(error)
        assertTrue("超时应分类为 Network", error is ParseError.Network)
    }

    @Test
    fun parse_invalidLink_classified_correctly() = runTest(testDispatcher) {
        fakeRepo.parseResult = ParseResult(error = "无法提取视频ID")
        viewModel.dispatch(ParseIntent.Parse("https://www.douyin.com/video/1"))
        advanceUntilIdle()

        val error = viewModel.uiState.value.parse.error
        assertTrue("无法提取应分类为 InvalidLink", error is ParseError.InvalidLink)
    }

    @Test
    fun parse_exception_sets_parseFailed_error() = runTest(testDispatcher) {
        fakeRepo.throwOnParse = true
        viewModel.dispatch(ParseIntent.Parse("https://www.douyin.com/video/1"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.parse.isParsing)
        assertNotNull(state.parse.error)
        assertTrue("异常应分类为 ParseFailed", state.parse.error is ParseError.ParseFailed)
    }

    // ===== Intent.Retry =====

    @Test
    fun retry_re_parses_lastParsedUrl() = runTest(testDispatcher) {
        fakeRepo.parseResult = ParseResult(
            platform = "抖音",
            media = listOf(MediaInfo(type = "video", url = "http://example.com/v.mp4"))
        )
        viewModel.dispatch(ParseIntent.Parse("https://www.douyin.com/video/123"))
        advanceUntilIdle()
        assertEquals(1, fakeRepo.parseCallCount)

        viewModel.dispatch(ParseIntent.Retry)
        advanceUntilIdle()
        assertEquals(2, fakeRepo.parseCallCount)
        assertEquals("https://www.douyin.com/video/123", fakeRepo.lastParseUrl)
    }

    // ===== Intent.Download =====

    @Test
    fun download_success_updates_state_and_emits_toast() = runTest(testDispatcher) {
        // 先解析填充 result
        fakeRepo.parseResult = ParseResult(
            platform = "抖音",
            media = listOf(
                MediaInfo(type = "video", url = "http://example.com/v1.mp4"),
                MediaInfo(type = "image", url = "http://example.com/i1.jpg")
            )
        )
        fakeRepo.downloadPath = "/tmp/test.mp4"
        fakeRepo.saveToGalleryResult = true
        viewModel.dispatch(ParseIntent.Parse("https://www.douyin.com/video/1"))
        advanceUntilIdle()

        // 收集 effects
        val effects = mutableListOf<UiEffect>()
        val collectorJob = launch { viewModel.effects.toList(effects) }
        advanceUntilIdle() // 让 collector 订阅

        viewModel.dispatch(ParseIntent.Download)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.download.isDownloading)
        assertTrue(state.download.success)
        assertEquals("已保存 2 个文件到相册", state.download.status)
        assertTrue("应有 Toast effect", effects.any { it is UiEffect.Toast })
        collectorJob.cancel()
    }

    @Test
    fun download_noResult_isNoop() = runTest(testDispatcher) {
        viewModel.dispatch(ParseIntent.Download)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.download.isDownloading)
    }

    @Test
    fun download_saveFailure_does_not_emit_toast() = runTest(testDispatcher) {
        fakeRepo.parseResult = ParseResult(
            platform = "抖音",
            media = listOf(MediaInfo(type = "video", url = "http://example.com/v.mp4"))
        )
        fakeRepo.downloadPath = null
        fakeRepo.saveToGalleryResult = false
        viewModel.dispatch(ParseIntent.Parse("https://www.douyin.com/video/1"))
        advanceUntilIdle()

        val effects = mutableListOf<UiEffect>()
        val collectorJob = launch { viewModel.effects.toList(effects) }
        advanceUntilIdle()

        viewModel.dispatch(ParseIntent.Download)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.download.success)
        assertEquals("保存失败", state.download.status)
        assertTrue("保存失败不应发 Toast", effects.none { it is UiEffect.Toast })
        collectorJob.cancel()
    }

    // ===== Intent.HistoryItemClick =====

    @Test
    fun historyItemClick_updates_url_and_starts_parse() = runTest(testDispatcher) {
        fakeRepo.parseResult = ParseResult(platform = "抖音", media = listOf(MediaInfo(type = "video", url = "x")))
        val item = HistoryItem(
            url = "https://www.douyin.com/video/456",
            platform = "抖音",
            title = "历史项",
            timestamp = 1000L
        )
        viewModel.dispatch(ParseIntent.HistoryItemClick(item))
        advanceUntilIdle()

        assertEquals("https://www.douyin.com/video/456", viewModel.uiState.value.input.url)
        assertEquals("douyin", viewModel.uiState.value.input.detectedPlatformId)
        assertEquals(1, fakeRepo.parseCallCount)
        assertEquals("https://www.douyin.com/video/456", fakeRepo.lastParseUrl)
    }

    // ===== Intent.DeleteHistory =====

    @Test
    fun deleteHistory_removes_item_from_state() = runTest(testDispatcher) {
        val item1 = HistoryItem("url1", "抖音", "title1", 1L)
        val item2 = HistoryItem("url2", "B站", "title2", 2L)
        fakeHistory.seed(item1)
        fakeHistory.seed(item2)
        // 重新创建 VM 以加载预填历史
        viewModel = ParseViewModel(fakeRepo, fakeHistory, { null }, { })
        assertEquals(2, viewModel.uiState.value.history.items.size)

        viewModel.dispatch(ParseIntent.DeleteHistory(item1))

        val items = viewModel.uiState.value.history.items
        assertEquals(1, items.size)
        assertEquals("url2", items[0].url)
    }

    // ===== Intent.ToggleHistory =====

    @Test
    fun toggleHistory_toggles_expanded_state() = runTest(testDispatcher) {
        assertFalse(viewModel.uiState.value.history.expanded)
        viewModel.dispatch(ParseIntent.ToggleHistory)
        assertTrue(viewModel.uiState.value.history.expanded)
        viewModel.dispatch(ParseIntent.ToggleHistory)
        assertFalse(viewModel.uiState.value.history.expanded)
    }

    // ===== Intent.ClearAllHistory =====

    @Test
    fun clearAllHistory_empties_items() = runTest(testDispatcher) {
        fakeHistory.seed(HistoryItem("u1", "p1", "t1", 1L))
        fakeHistory.seed(HistoryItem("u2", "p2", "t2", 2L))
        viewModel = ParseViewModel(fakeRepo, fakeHistory, { null }, { })
        assertEquals(2, viewModel.uiState.value.history.items.size)

        viewModel.dispatch(ParseIntent.ClearAllHistory)

        assertTrue(viewModel.uiState.value.history.items.isEmpty())
    }

    // ===== Intent.ToggleTheme =====

    @Test
    fun toggleTheme_calls_themeToggler() = runTest(testDispatcher) {
        assertEquals(0, themeToggleCount)
        viewModel.dispatch(ParseIntent.ToggleTheme)
        assertEquals(1, themeToggleCount)
        viewModel.dispatch(ParseIntent.ToggleTheme)
        assertEquals(2, themeToggleCount)
    }

    // ===== 历史去重(spec 既有行为不回归) =====

    @Test
    fun parse_sameUrl_twice_deduplicates_history() = runTest(testDispatcher) {
        fakeRepo.parseResult = ParseResult(
            platform = "抖音",
            title = "测试",
            media = listOf(MediaInfo(type = "video", url = "http://example.com/v.mp4"))
        )
        viewModel.dispatch(ParseIntent.Parse("https://www.douyin.com/video/123"))
        advanceUntilIdle()
        viewModel.dispatch(ParseIntent.Parse("https://www.douyin.com/video/123"))
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.history.items.size)
    }
}
