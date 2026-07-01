# 解析框架与基础设施层重构 spec V1.0

> 冻结基线:2026-06-30
> 上游依据:《ParseHub 源码深度审计报告 V1.0》P0/P1
> 范围:本轮完成 P0 全部三项 + 落地 `data/{parser,network,download}` 底座骨架;Room 历史升级;小红书多策略降级
> 非目标:新增平台解析器实现(抖音/B站/微博/快手仅迁移到新骨架,业务逻辑原样保留);下载模块拆分(留待下一轮);设置页(子项目 5)

---

## 1. 现状盘点(审计对照)

### 1.1 仓库真实状态(精读源码后纠正)

| 模块 | 审计报告说法 | 实际情况 |
|------|-------------|---------|
| 多平台解析器 | "只有 XhsParser" | ❌ 不准确。`ParseRepository` 内联了 `parseDouyin`(含 API + Web 双通道)/`parseBilibili`/`parseWeibo`(含 Status + TV)/`parseKuaishou`(空实现) |
| 平台调度 | `when(platform)` 硬编码 | ✅ 属实,line 80-87 |
| Repository 职责 | 过重 | ✅ 属实,1027 行同时承担调度+网络+下载+存储+Cookie |
| Cookie 管理 | 薄弱 | ✅ 属实,仅内存 `CookieJar` + 微博硬编码 `weiboSubCookie` |
| 小红书抗改版 | 单一 `__INITIAL_STATE__` | ✅ 属实,XhsParser 只有一种策略 |
| BaseParser | 设计不错 | ✅ 已有 `getRawUrl`/`cleanUrlParams`/`followRedirect` 骨架,可直接复用 |

### 1.2 关键约束(影响实现)

- **BaseParser 已存在且设计合理**:七步管道中的 `UrlNormalizer`/`ShortLinkResolver` 已由 `BaseParser.getRawUrl` 覆盖,本轮**不重写 BaseParser**,只补 `ScriptExtractor`/`JsonSanitizer`/`MediaExtractor` 公共能力
- **抖音/微博已有可工作实现**:迁移到独立 Parser 时**原样搬移业务逻辑**,不做行为变更,降低回归风险
- `ParseRepository` 是 DCL 单例(`getInstance(context)`),VM 通过 `IParseRepository` 注入;重构后 VM 注入方式不变,只换实现
- 本地无 Android SDK,所有改动靠 CI 验证,必须**严格保证编译通过**

---

## 2. 目标架构

### 2.1 目录结构(本轮新增/迁移)

```
data/
├── parser/
│   ├── base/
│   │   ├── BaseParser.kt          (迁移自 data/,不改逻辑)
│   │   ├── ScriptExtractor.kt     (新增:多策略脚本提取)
│   │   ├── JsonSanitizer.kt       (新增:JSON 清洗)
│   │   └── MediaExtractor.kt      (新增:公共媒体抽取工具,本轮可选)
│   ├── registry/
│   │   ├── ParserRegistry.kt      (新增:平台注册 + 匹配 + 调度)
│   │   └── PlatformMatcher.kt     (新增:URL → platformId,替代 detectPlatform)
│   ├── xhs/
│   │   └── XhsParser.kt           (迁移 + 多策略降级)
│   ├── douyin/
│   │   └── DouyinParser.kt        (迁移自 Repository.parseDouyin*)
│   ├── bilibili/
│   │   └── BilibiliParser.kt      (迁移自 Repository.parseBilibili)
│   ├── weibo/
│   │   └── WeiboParser.kt         (迁移自 Repository.parseWeibo*)
│   └── kuaishou/
│       └── KuaishouParser.kt      (新增占位,返回"暂不支持")
├── network/
│   ├── NetworkManager.kt          (新增:OkHttpClient 单例 + HeaderFactory + 请求封装)
│   ├── HeaderFactory.kt           (新增:统一 UA/Referer/Accept 策略)
│   ├── CookieManager.kt           (新增:CookieStore 接口 + 内存实现,微博 Cookie 硬编码迁移至此)
│   └── HttpResult.kt              (新增:sealed class Success/Redirect/Error)
├── download/
│   └── DownloadManager.kt         (迁移自 Repository.downloadMedia + saveToGallery,不改逻辑)
├── history/
│   ├── HistoryRepository.kt       (新增:Room 实现 IParseHistory)
│   ├── HistoryDatabase.kt         (新增:Room DB)
│   ├── HistoryDao.kt              (新增:DAO)
│   └── HistoryEntity.kt           (新增:实体)
├── ParseRepository.kt             (瘦身:仅编排 registry + download,~80 行)
├── IParseRepository.kt            (不变)
├── IParseHistory.kt               (不变)
├── ParseHistory.kt                (改为委托 HistoryRepository,过渡期兼容)
├── ParseUtils.kt                  (迁移至 parser/registry/UrlPatterns.kt,旧文件保留 re-export)
└── Models.kt                      (不变)
```

### 2.2 依赖关系(单向,无环)

```
ParseViewModel
      ↓ (注入)
IParseRepository ← ParseRepository
      ↓ (编排)
ParserRegistry → 各平台 Parser → BaseParser + ScriptExtractor + JsonSanitizer
                                  ↓
                              NetworkManager → HeaderFactory + CookieManager
      ↓ (编排)
DownloadManager → NetworkManager + MediaStore
      ↓ (实现)
IParseHistory ← HistoryRepository → Room
```

### 2.3 Parser 七步管道(落地形态)

| 步骤 | 审计建议 | 本轮实现 | 归属 |
|------|---------|---------|------|
| 1 UrlNormalizer | 独立 | 复用 `BaseParser.getRawUrl` 的协议补全 + 参数清洗 | base/BaseParser |
| 2 ShortLinkResolver | 独立 | 复用 `BaseParser.getRawUrl` 的 `followRedirect` | base/BaseParser |
| 3 HtmlFetcher | 独立 | 新增 `NetworkManager.fetchHtml()`,封装 HeaderFactory | network |
| 4 ScriptExtractor | 独立 | 新增,多策略(`__INITIAL_STATE__`/`JSON.parse`/`__NEXT_DATA__`/`_ROUTER_DATA`) | base/ScriptExtractor |
| 5 JsonSanitizer | 独立 | 新增,清洗 `undefined`/`NaN`/`Infinity`/`BigInt`/`new Date()` | base/JsonSanitizer |
| 6 MediaExtractor | 独立 | 本轮不做统一抽取(各平台差异大),留待下一轮 | — |
| 7 ResultBuilder | 独立 | 各 Parser 内联,沿用现状 | 各 Parser |

**务实决策**:七步管道是理想形态,但步骤 6(MediaExtractor 统一)需要各平台字段对齐,本轮范围过大。步骤 1-5 落地为公共能力,步骤 6-7 保留在 Parser 内部,后续迭代抽取。

---

## 3. P0-1 Repository 拆分

### 3.1 拆分边界

| 原职责 | 新归属 | 行数预算 |
|--------|--------|---------|
| `detectPlatform` | `PlatformMatcher.match` | <40 |
| `when(platform){...}` 调度 | `ParserRegistry.parse` | <80 |
| `safeFetchHtml/Json/JsonPost` | `NetworkManager.fetchHtml/fetchJson/postForm` | <120 |
| `mobileUA/desktopUA/weiboSubCookie` | `HeaderFactory` + `CookieManager` | <60 + <80 |
| `downloadMedia` + `saveToGallery` | `DownloadManager` | <160 |
| 超时/进度编排 | `ParseRepository.parse` | <80 |
| DCL 单例 | `ParseRepository.getInstance` | <10 |

### 3.2 ParseRepository 瘦身后形态(~80 行)

```kotlin
class ParseRepository private constructor(
    private val context: Context,
    private val registry: ParserRegistry,
    private val downloader: DownloadManager
) : IParseRepository {
    override suspend fun parse(url: String, onProgress: ((ParseStage) -> Unit)?): ParseResult =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            withTimeoutOrNull(20_000L) {
                onProgress?.invoke(ParseStage.DETECTING)
                val result = registry.parse(url) { onProgress?.invoke(it) }
                onProgress?.invoke(ParseStage.DONE)
                result
            } ?: ParseResult(error = "解析超时...")
        }

    override suspend fun downloadMedia(media: MediaInfo, referer: String?, onProgress: ((Int) -> Unit)?): String? =
        downloader.download(media, referer, onProgress)

    override fun saveToGallery(file: File, type: String): Boolean =
        downloader.saveToGallery(file, type)

    companion object {
        fun getInstance(context: Context): ParseRepository { /* DCL,组装 registry + downloader */ }
    }
}
```

### 3.3 兼容性保证

- `IParseRepository` 签名不变 → VM 零改动
- `getInstance(context)` 签名不变 → ParseRoute 零改动
- 行为不变:所有平台解析结果与重构前一致(通过迁移而非重写保证)

---

## 4. P0-2 ParserRegistry

### 4.1 接口设计

```kotlin
class ParserRegistry(
    private val network: NetworkManager,
    private val cookieManager: CookieManager
) {
    private val parsers: List<IParser> = listOf(
        XhsParser(network, cookieManager),
        DouyinParser(network, cookieManager),
        BilibiliParser(network, cookieManager),
        WeiboParser(network, cookieManager),
        KuaishouParser(network, cookieManager)  // 占位
    )

    /** 按 URL 匹配返回平台 id,未匹配返回 null */
    fun matchPlatform(url: String): String? = PlatformMatcher.match(url)

    /** 调度解析,无匹配返回 Unsupported 错误 */
    suspend fun parse(url: String, onProgress: ((ParseStage) -> Unit)?): ParseResult {
        val platformId = matchPlatform(url) ?: return ParseResult(error = "无法识别平台")
        val parser = parsers.firstOrNull { it.platformId == platformId }
            ?: return ParseResult(error = "$platformId 暂不支持")
        onProgress?.invoke(ParseStage.FETCHING)
        return parser.parse(url)
    }
}
```

### 4.2 IParser 接口(替代直接继承 BaseParser 的硬约束)

```kotlin
interface IParser {
    val platformId: String      // "xiaohongshu" / "douyin" ...
    val displayName: String     // "小红书" / "抖音" ...
    fun matches(url: String): Boolean
    suspend fun parse(url: String): ParseResult
}
```

各平台 Parser `implements IParser`,内部可继续继承 `BaseParser`(复用 `getRawUrl`)。Registry 只依赖 `IParser`,新增平台不改 Registry。

### 4.3 PlatformMatcher(替代 Repository.detectPlatform)

```kotlin
object PlatformMatcher {
    private val patterns = listOf(
        "douyin" to Regex("""(douyin|iesdouyin)\.com""", RegexOption.IGNORE_CASE),
        "bilibili" to Regex("""(bilibili|b23)\.(com|tv)""", RegexOption.IGNORE_CASE),
        "kuaishou" to Regex("""(kuaishou|gifshow)\.com""", RegexOption.IGNORE_CASE),
        "weibo" to Regex("""weibo\.(com|cn)""", RegexOption.IGNORE_CASE),
        "xiaohongshu" to Regex("""(xiaohongshu|xhslink)\.com""", RegexOption.IGNORE_CASE),
        "youtube" to Regex("""(youtube|youtu)\.(com|be)""", RegexOption.IGNORE_CASE),
        "twitter" to Regex("""(twitter|x)\.com""", RegexOption.IGNORE_CASE),
        "tieba" to Regex("""tieba\.baidu\.com""", RegexOption.IGNORE_CASE)
    )
    fun match(url: String): String? = patterns.firstOrNull { it.second.containsMatchIn(url) }?.first
}
```

---

## 5. P0-3 小红书多策略降级

### 5.1 五级降级策略

| 策略 | 标记 | 提取方式 | 容错 |
|------|------|---------|------|
| 1 | `window.__INITIAL_STATE__` | 现有逻辑 | 现状 |
| 2 | `window.__INITIAL_STATE__={...}` 单行 | 正则 `\{[\s\S]*?\};?\s*$` | 新增 |
| 3 | `__INITIAL_STATE_V2` | 同策略 1 改 marker | 新增 |
| 4 | `__NEXT_DATA__` | `<script id="__NEXT_DATA__" type="application/json">{...}</script>` | 新增 |
| 5 | API(备用) | 本轮**不实现**,留 TODO,因需逆向 a1/web_session 签名 | 占位 |

### 5.2 ScriptExtractor 设计

```kotlin
class ScriptExtractor {
    /** 多策略提取,返回第一个成功的 JSON */
    fun extractInitialState(html: String): JSONObject? {
        return extractByMarker(html, "window.__INITIAL_STATE__")   // 策略 1
            ?: extractInlineAssign(html, "__INITIAL_STATE__")      // 策略 2
            ?: extractByMarker(html, "__INITIAL_STATE_V2")         // 策略 3
            ?: extractNextData(html)                                 // 策略 4
            ?: extractByMarker(html, "_ROUTER_DATA")               // 兜底
    }

    /** 健壮的脚本块提取:不用 indexOf("</script>"),用正则 + 括号匹配 */
    private fun extractByMarker(html: String, marker: String): JSONObject? { ... }
    private fun extractInlineAssign(html: String, varName: String): JSONObject? { ... }
    private fun extractNextData(html: String): JSONObject? { ... }
}
```

**关键改进(针对审计 P1-④)**:
- 不再用 `indexOf("</script>")` 简单截取
- 改用**括号深度匹配**:从 `=` 后第一个 `{` 开始,逐字符扫描 `{`/`}` 深度,深度归零时截断,避免脚本内嵌 `</script>` 字符串误判
- 仍保留 `</script>` 作为快速下界 fallback

### 5.3 JsonSanitizer 设计

```kotlin
object JsonSanitizer {
    private val UNDEFINED = Regex("""\bundefined\b""")
    private val NAN = Regex("""\bNaN\b""")
    private val INFINITY = Regex("""-?\bInfinity\b""")
    private val BIGINT = Regex("""(\d+)n""")
    private val NEW_DATE = Regex("""new Date\(([^)]*)\)""")

    /** 清洗 JS 字面量为合法 JSON */
    fun sanitize(jsonStr: String): String {
        return jsonStr
            .replace(NEW_DATE) { m -> m.groupValues[1].ifBlank { "0" } }  // new Date(123) → 123
            .replace(BIGINT) { m -> m.groupValues[1] }                     // 123n → 123
            .replace(UNDEFINED, "null")
            .replace(NAN, "null")
            .replace(INFINITY, "null")
    }
}
```

### 5.4 XhsParser 改造

```kotlin
class XhsParser(
    network: NetworkManager,
    cookieManager: CookieManager
) : BaseParser(network.client, cookieManager.get("xiaohongshu")), IParser {

    override val platformId = "xiaohongshu"
    override val displayName = "小红书"
    private val extractor = ScriptExtractor()

    override suspend fun doParse(url: String): ParseResult {
        val html = network.fetchHtml(url, HeaderFactory.xhs()) ?: return errorResult("无法加载小红书页面")
        val state = extractor.extractInitialState(html) ?: return errorResult("解析失败,可能需要登录")
        return parseNoteData(state)  // 原逻辑不动
    }
    // parseNoteData / buildResult / selectStream 原样保留
}
```

---

## 6. 网络层(NetworkManager + HeaderFactory + CookieManager)

### 6.1 NetworkManager

```kotlin
class NetworkManager(
    private val cookieManager: CookieManager
) {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .retryOnConnectionFailure(true)
            .cookieJar(cookieManager.asOkHttpJar())
            .build()
    }

    suspend fun fetchHtml(url: String, headers: Map<String, String>): String?
    suspend fun fetchJson(url: String, headers: Map<String, String>): JSONObject?
    suspend fun postForm(url: String, body: String, headers: Map<String, String>, params: Map<String, String>): JSONObject?
    fun fetchFinalUrl(url: String, ua: String): String?    // 同步,短链解析用
    fun newNoRedirectClient(): OkHttpClient                 // XHS HTML 抓取需要
}
```

### 6.2 HeaderFactory

```kotlin
object HeaderFactory {
    const val MOBILE_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 ...) Mobile/15E148 Safari/604.1"
    const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) ... Chrome/144.0.0.0 Safari/537.36"
    const val HTTPX_UA = "python-httpx/0.28.1"

    fun xhs(): Map<String, String> = mapOf(
        "Accept" to "*/*", "User-Agent" to HTTPX_UA, "Accept-Encoding" to "gzip"
    )
    fun douyin(): Map<String, String> = mapOf("User-Agent" to MOBILE_UA, "Referer" to "https://www.douyin.com/")
    fun bilibili(bvid: String): Map<String, String> = mapOf("User-Agent" to DESKTOP_UA, "Referer" to "https://www.bilibili.com/video/$bvid")
    fun weibo(): Map<String, String> = mapOf("User-Agent" to DESKTOP_UA, "Referer" to "https://weibo.com")
    fun download(url: String): Map<String, String> { /* 根据 url 选 UA + Referer */ }
}
```

### 6.3 CookieManager

```kotlin
interface CookieManager {
    fun get(platformId: String): String?
    fun set(platformId: String, cookie: String)
    fun clear(platformId: String? = null)
    fun asOkHttpJar(): okhttp3.CookieJar
}

/** 内存实现,本轮够用;后续可换持久化实现 */
class InMemoryCookieManager : CookieManager {
    private val store = mutableMapOf<String, String>()
    init {
        // 迁移 Repository 的硬编码微博 cookie
        store["weibo"] = "SUB=_2AkMR47Mlf8NxqwFRmfocxG_lbox2wg7EieKnv0L-JRMxHRl-yT9yqhFdtRB6OmOdyoia9pKPkqoHRRmSBA_WNPaHuybH"
    }
    override fun get(platformId: String): String? = store[platformId]
    override fun set(platformId: String, cookie: String) { store[platformId] = cookie }
    override fun clear(platformId: String?) { if (platformId == null) store.clear() else store.remove(platformId) }
    override fun asOkHttpJar(): okhttp3.CookieJar = InMemoryCookieJar(store)
}
```

---

## 7. 历史记录升级 Room

### 7.1 实体 + DAO + DB

```kotlin
@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val url: String,
    val title: String,
    val platform: String,
    val timestamp: Long,
    val coverUrl: String?
)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<HistoryEntity>>
    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    suspend fun loadAll(): List<HistoryEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HistoryEntity)
    @Delete suspend fun delete(entity: HistoryEntity)
    @Query("DELETE FROM history") suspend fun clear()
}

@Database(entities = [HistoryEntity::class], version = 1, exportSchema = false)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    companion object {
        @Volatile private var INSTANCE: HistoryDatabase? = null
        fun get(context: Context): HistoryDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context, HistoryDatabase::class.java, "parsehub.db").build().also { INSTANCE = it }
        }
    }
}
```

### 7.2 HistoryRepository 实现 IParseHistory

```kotlin
class HistoryRepository(private val dao: HistoryDao) : IParseHistory {
    private var appContext: Context? = null
    override fun init(context: Context) { appContext = context.applicationContext }
    override fun load(): List<HistoryItem> = runBlocking { dao.loadAll().map { it.toHistoryItem() } }
    override fun add(item: HistoryItem) = runBlocking { dao.upsert(item.toEntity()) }
    override fun remove(url: String) = runBlocking { dao.delete(HistoryEntity(url, "", "", 0, null)) }
    override fun clear() = runBlocking { dao.clear() }
}
```

**注意**:`IParseHistory` 接口是同步签名(`load(): List`),HistoryRepository 内部用 `runBlocking` 桥接。这是过渡期妥协,下轮接口化升级时改 `Flow`。

### 7.3 ParseHistory 兼容

`ParseHistory` object 改为委托 `HistoryRepository`,保持 `IParseHistory` 实现不变,MainActivity 调用不变:

```kotlin
object ParseHistory : IParseHistory {
    private lateinit var repo: HistoryRepository
    override fun init(context: Context) {
        val dao = HistoryDatabase.get(context).historyDao()
        repo = HistoryRepository(dao)
        repo.init(context)
    }
    override fun load() = repo.load()
    override fun add(item: HistoryItem) = repo.add(item)
    override fun remove(url: String) = repo.remove(url)
    override fun clear() = repo.clear()
}
```

### 7.4 build.gradle 依赖

```gradle
implementation 'androidx.room:room-runtime:2.6.1'
implementation 'androidx.room:room-ktx:2.6.1'
ksp 'androidx.room:room-compiler:2.6.1'   // 需加 ksp 插件
```

---

## 8. 实施顺序(8 步)

| 步 | 内容 | 验证点 |
|----|------|--------|
| 1 | 创建 `data/network/`(NetworkManager + HeaderFactory + CookieManager + HttpResult) | 编译过 |
| 2 | 创建 `data/parser/base/` 公共能力(ScriptExtractor + JsonSanitizer),迁移 BaseParser | 单测覆盖 ScriptExtractor 5 策略 + JsonSanitizer 5 类字面量 |
| 3 | 创建 `data/parser/registry/`(IParser + ParserRegistry + PlatformMatcher) | 单测覆盖 8 平台匹配 |
| 4 | 迁移平台 Parser:xhs(加多策略)/douyin/bilibili/weibo/kuaishou 到 `data/parser/{platform}/` | 编译过,逻辑原样 |
| 5 | 创建 `data/download/DownloadManager`,迁移 downloadMedia + saveToGallery | 编译过 |
| 6 | 创建 `data/history/`(Entity + Dao + DB + Repository),改 ParseHistory 委托 | 编译过,Room schema 生成 |
| 7 | 瘦身 `ParseRepository`(<80 行,编排 registry + downloader) | 编译过,VM 零改动 |
| 8 | 提交推送 CI 验证 | CI 全绿 |

---

## 9. 测试策略

### 9.1 单测(本地 JVM,无 Android 依赖)

| 模块 | 测试 | 数量 |
|------|------|------|
| PlatformMatcher | 8 平台 URL + 未匹配 | 9 |
| ScriptExtractor | 5 策略各 1 正例 + 嵌套 `</script>` 容错 | 6 |
| JsonSanitizer | undefined/NaN/Infinity/BigInt/new Date | 5 |
| ParserRegistry | 匹配调度 + 未匹配 + Unsupported | 3 |

### 9.2 回归保证

- 现有 `ParseViewModelTest` 23 个测试**不动**,验证 VM 层行为不变
- 平台 Parser 迁移用**搬移而非重写**:git diff 应只显示 package + import + 构造参数变化,方法体零改动
- CI 编译通过即视为本轮交付完成(无真机/模拟器)

---

## 10. 风险与回滚

| 风险 | 概率 | 缓解 |
|------|------|------|
| Room KSP 插件与 AGP 版本不兼容 | 中 | 步骤 6 优先验证;若失败回退 SharedPreferences,Room 留待下轮 |
| 迁移 Parser 时遗漏 import 导致编译失败 | 高 | 严格搬移,CI 验证 |
| ScriptExtractor 括号匹配算法 bug | 中 | 单测覆盖嵌套场景 |
| 多策略降级改变 XHS 行为 | 低 | 策略 1 行为与原版完全一致,新增策略仅作 fallback |

回滚策略:每步独立 commit,任一步 CI 失败可 `git revert` 单步,不影响已完成的步骤。

---

## 11. 不在本轮范围(显式排除)

- 抖音/B站/微博/快手业务逻辑优化(只迁移不改)
- MediaExtractor 统一抽取(各平台字段差异大,需单独 spec)
- 下载模块拆分(Video/Image/Album 分流)
- 设置页(子项目 5)
- Cookie 持久化与 UI 管理(本轮仅内存 + 接口)
- XHS API 策略 5(需逆向签名,独立子项目)
- 历史页 Flow 化(IParseHistory 接口下轮升级)
