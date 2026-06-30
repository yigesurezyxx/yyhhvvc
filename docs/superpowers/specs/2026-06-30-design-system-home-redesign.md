# ParseHub Design System + 首页重设计

- **日期**:2026-06-30
- **状态**:已冻结,作为 ParseHub 后续开发的唯一设计基线
- **范围**:Design System 基础层 + 主题重构 + 首页(ParseScreen)重设计
- **非目标**:解析结果页、下载管理、设置页、历史页(独立子项目)

## 0. 设计原则(Design Principles)

整个 ParseHub 后续开发必须遵守:

1. **一屏只保留一个视觉焦点**(CTA 优先)
2. **所有业务状态通过动画反馈**(不要突然变化)
3. **统一 Design Token,禁止硬编码颜色、圆角、间距**
4. **组件保持 Pure UI**,不依赖 Repository、Context 等业务对象
5. **所有新增页面必须复用 Design System**,不允许创建新的视觉风格
6. **优先保持 60fps 流畅体验**,视觉效果不能以牺牲性能为代价
7. **任何动画不超过 400ms**(仅 Success 允许 600ms)

## 1. 背景与决策

### 1.1 现状问题
- [ParseScreen.kt](file:///workspace/ParseHubApp/app/src/main/java/com/parsehub/app/ui/screens/ParseScreen.kt) 969 行,7 个组件 + helper 全塞一个文件
- 无 ViewModel,13 个 `remember` 状态 + 业务逻辑耦合在 Composable 内
- [Theme.kt](file:///workspace/ParseHubApp/app/src/main/java/com/parsehub/app/ui/theme/Theme.kt) 用 Material You 动态色,品牌识别弱;深色背景 `#0F0F1A` 几乎纯黑
- 平台用 emoji(📕📹),无品牌色
- [Models.kt](file:///workspace/ParseHubApp/app/src/main/java/com/parsehub/app/data/Models.kt#L21-L22) 有 author/avatar 字段但 UI 未用

### 1.2 关键决策
| 决策点 | 选择 | 原因 |
|--------|------|------|
| 设计方向 | A++ M3 Expressive + 自定义 DS + Telegram/Nagram/Pixiv 融合 | 保留 M3 稳定性与生态,外观脱离默认味 |
| 色彩策略 | 固定品牌色(禁用 Material You) | 品牌一致性强,所有用户看同样蓝调 |
| 字体策略 | 系统默认字体 + 字重层级 | 零体积代价(刚瘦身移除 Chaquopy) |
| 毛玻璃 | `Modifier.blur(18dp)`,API<31 自动降级 | Compose 原生,无第三方依赖 |
| 范围 | 主题+首页(不含结果页/下载/设置/历史) | 单 spec 可控,DS 基础层先行 |

## 2. Design System 基础层

`ui/theme/` 下 10 个文件,作为所有页面的统一规范。

### 2.1 Color.kt — 品牌色板 + 渐变 + Alpha Token

**Color Scheme:**

| 角色 | 浅色 | 深色 |
|------|------|------|
| background | `#F8FAFC` | `#0F172A` |
| surface | `#FFFFFF` | `#1E293B` |
| surfaceContainerLowest | `#FFFFFF` | `#0F172A` |
| surfaceContainerLow | `#F1F5F9` | `#1E293B` |
| surfaceContainer | `#E2E8F0` | `#293548` |
| surfaceContainerHigh | `#CBD5E1` | `#334155` |
| surfaceContainerHighest | `#94A3B8` | `#475569` |
| surfaceVariant | `#F1F5F9` | `#334155` |
| primary | `#4F7CFF` | `#60A5FA` |
| primaryContainer | `#E0E7FF` | `#1E3A5F` |
| secondary | `#8B5CF6` | `#A78BFA` |
| error | `#EF4444` | `#F87171` |

**渐变画笔:**
```kotlin
val HeaderGradient = Brush.linearGradient(listOf(Color(0xFF4F7CFF), Color(0xFF8B5CF6)))
val ButtonGradient = Brush.linearGradient(listOf(Color(0xFF4F7CFF), Color(0xFF8B5CF6)))
```

**Alpha Token:**
- `GlassAlpha = 0.78f`
- `ShadowAlpha = 0.12f`
- `BorderAlpha = 0.1f`
- `DisabledAlpha = 0.38f`

**状态色(Success/Warning/Error/Info/Disabled):**
- Success: `#22C55E` / `#4ADE80`
- Warning: `#F59E0B` / `#FBBF24`
- Error: `#EF4444` / `#F87171`
- Info: `#3B82F6` / `#60A5FA`
- Disabled: `#94A3B8` / `#475569`

### 2.2 Typography.kt — 字重层级(系统字体)

| 角色 | 字号 | 字重 | letterSpacing |
|------|------|------|--------------|
| headlineLarge(品牌名) | 32sp | SemiBold | -0.5sp |
| titleLarge(结果标题) | 22sp | Bold | 0 |
| titleMedium(按钮) | 16sp | SemiBold | 0.1sp |
| titleSmall(卡片标题) | 14sp | Medium | 0.1sp |
| bodyLarge(正文) | 15sp | Regular | 0.5sp |
| bodyMedium(次正文) | 14sp | Regular | 0.25sp |
| bodySmall(辅助) | 12sp | Regular | 0.4sp |
| labelSmall(标签) | 11sp | Medium | 0.5sp |

现有 [Type.kt](file:///workspace/ParseHubApp/app/src/main/java/com/parsehub/app/ui/theme/Type.kt) 合并后删除。

### 2.3 Shape.kt — M3 Shape

| 角色 | 圆角 |
|------|------|
| small | 12dp |
| medium | 16dp |
| large | 24dp |

### 2.4 Radius.kt — 固定圆角值

| Token | 值 | 用途 |
|-------|----|----|
| InputRadius | 20dp | 输入框 |
| CardRadius | 24dp | 主卡片/结果卡 |
| ChipRadius | 16dp | 芯片 |
| DialogRadius | 28dp | 对话框 |
| FABRadius | 20dp | FAB |

### 2.5 Spacing.kt — 间距尺度

4 / 8 / 12 / 16 / 24 / 32 dp

### 2.6 Dimensions.kt — 组件尺寸

| Token | 值 |
|-------|----|
| ButtonHeight | 56dp |
| InputHeight | 60dp |
| HeaderHeight | 220dp |
| PlatformCard | 88dp |
| Avatar | 44dp |
| Logo | 72dp |
| Icon | 24dp |
| MinTouchTarget | 48dp |

### 2.7 Elevation.kt — 阴影层级

| Token | 值 |
|-------|----|
| level0 | 0dp |
| level1 | 1dp |
| level2 | 3dp |
| level3 | 8dp |
| PressedElevation | 0dp |
| HoveredElevation | level1 |
| FocusedElevation | level1 |

### 2.8 Animation.kt — 动画时长 + Easing + 规则

**Motion Token:**
- UltraFast = 80ms
- Fast = 150ms
- Normal = 250ms
- Slow = 400ms
- ExtraSlow = 600ms(仅 Success)

**Easing:** Spring(damping 0.6, stiffness 400) / Overshoot / EaseOut / EaseInOut

**规则映射:**
| 场景 | 动效 | 时长 |
|------|------|------|
| 按钮点击 | Scale 0.96 | Fast,Spring |
| 卡片出现 | Fade + SlideUp 16dp | Normal,stagger 50ms |
| 解析成功 | Scale 1.0→1.05→1.0 + Fade | ExtraSlow |
| 错误提示 | Shake ±8dp ×3 | Fast |
| Snackbar | SlideUp | Normal |
| 页面切换 | SharedAxis Z(Fade+Scale) | Normal |
| 图片加载 | Crossfade | Normal |

**硬规则:任何动画不超过 400ms,仅 Success 允许 600ms。**

### 2.9 Icons.kt — 图标包 + 平台品牌色 + 状态色

**图标包:** Filled / Outlined / Rounded 三套(Material Icons)

**平台品牌色映射:**
| 平台 | 品牌色 | 支持状态 |
|------|--------|---------|
| 抖音 | `#000000`(深色 `#FFFFFF`) | ✅ |
| B站 | `#00A1D6` | ✅ |
| 小红书 | `#FF2442` | ✅ |
| 微博 | `#E6162D` | ✅ |
| 快手 | `#FF4906` | ✅ |
| 贴吧 | `#4E6EF2` | 即将支持 |
| YouTube | `#FF0000` | 即将支持 |
| X | `#000000` | 即将支持 |

**移除所有 emoji**(📕📹▶️等),平台 logo 用 Material Icons 或 VectorDrawable(本次先用 Material Icons 占位,正式 logo 子项目决定)。

### 2.10 Theme.kt — 统一入口 + CompositionLocal

- `dynamicColor = false`(禁用 Material You)
- 提供 `LocalSpacing / LocalDimensions / LocalRadius / LocalAnimation / LocalElevation`
- 使用方式:`ParseTheme.spacing.large` / `ParseTheme.radius.card` / `ParseTheme.dimensions.buttonHeight`
- 增加 `PreviewLightTheme` / `PreviewDarkTheme` 供 Compose Preview 复用

### 2.11 命名规范

- 所有 Token 使用 PascalCase
- 所有 CompositionLocal 使用 `LocalXXX`
- 所有公开组件使用 `ParseXXX` 前缀
- 所有内部组件使用 `InternalXXX` 前缀
- **禁止在业务页面直接写颜色、圆角、间距等硬编码值**,统一引用 Design Token

### 2.12 Accessibility(可访问性)

- 点击区域 ≥ 48dp(MinTouchTarget)
- 文本对比度 ≥ WCAG AA
- 所有 Icon 有 contentDescription
- 动画支持减少动态(Respect 系统动画设置 `Settings.Global.ANIMATOR_DURATION_SCALE`)
- 深浅色都保证可读性

## 3. 首页架构(MVVM + UDF)

### 3.1 整体架构

```
MainActivity
  │
  ▼
ParseRoute          ← collect State / collect Effect / 权限 / Navigation
  │
  ▼
ParseViewModel      ← 持有 state + 调 repository/history,零 Compose 依赖
  │
  ▼
ParseScreen         ← collectAsState(),只做 UI 组合,零业务逻辑
  │
  ├── HeroHeader
  ├── LinkInputCard
  ├── ParseActionButton
  ├── PlatformGrid + PlatformCard
  ├── RecentHistorySection
  ├── LoadingCard
  ├── ErrorCard
  ├── EmptyState
  └── FooterInfo
```

### 3.2 ParseViewModel

**构造函数注入**(不继承 AndroidViewModel,便于 Hilt/Koin 接入与单测 Mock):
```kotlin
class ParseViewModel(
    private val repository: IParseRepository,
    private val history: ParseHistory
) : ViewModel()
```

**State 分片**(避免单一 data class 膨胀):
```kotlin
data class ParseUiState(
    val input: InputState,
    val parse: ParseState,
    val download: DownloadState,
    val history: HistoryState
)
data class InputState(val url: String, val detectedPlatform: PlatformItem?)
data class ParseState(val isParsing: Boolean, val stage: ParseStage?, val elapsedMs: Long, val result: ParseResult?, val error: ParseError?)
data class DownloadState(val isDownloading: Boolean, val status: String?, val success: Boolean)
data class HistoryState(val items: List<HistoryItem>, val expanded: Boolean)

val uiState: StateFlow<ParseUiState>
```

**UiEffect(单次事件,SharedFlow):**
```kotlin
sealed interface UiEffect {
    data class Toast(val message: String) : UiEffect
    data class Snackbar(val message: String) : UiEffect
    data class Share(val content: String) : UiEffect
    data class OpenFolder(val path: String) : UiEffect
}
val effects: SharedFlow<UiEffect>
```

**Intent 统一入口:**
```kotlin
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
fun dispatch(intent: ParseIntent)
```

### 3.3 Repository 接口化

新增 `IParseRepository` 接口,现有 [ParseRepository](file:///workspace/ParseHubApp/app/src/main/java/com/parsehub/app/data/ParseRepository.kt) 实现它。便于以后 MockRepository / CloudRepository 替换。ParseHistory 同样接口化。

### 3.4 组件拆分(9 个文件,`ui/screens/home/`)

| 组件 | 行数预算 | 职责边界 |
|------|---------|---------|
| [ParseScreen.kt](file:///workspace/ParseHubApp/app/src/main/java/com/parsehub/app/ui/screens/ParseScreen.kt) | <80 | UI 组合,零业务逻辑 |
| HeroHeader.kt | <60 | Logo + 标题 + 副标题 + Aurora 微动效 |
| LinkInputCard.kt | <120 | 输入 + 粘贴 + 清空 + 智能识别反馈 |
| ParseActionButton.kt | <100 | 四态按钮(Disabled/Idle/Loading/Success) |
| PlatformGrid.kt + PlatformCard | <120 | 数据驱动的平台卡片网格 |
| RecentHistorySection.kt | <150 | 历史折叠区 |
| EmptyState.kt | <60 | 状态化空态引导 |
| LoadingCard.kt | <80 | 时间轴进度 |
| ErrorCard.kt | <80 | 分类错误提示 |
| FooterInfo.kt | <40 | 版本信息 |

**组件约束:**
- 禁止调用 Repository / ParseHistory / ClipboardManager
- 只接收 state 片段 + callback
- `remember = 0`(仅 LazyListState / FocusRequester / ScrollState / SnackbarHostState 除外)
- ParseScreen 不允许出现业务 remember

**保留不动:** 现有 [MediaGrid/SingleMedia/MediaThumbnail](file:///workspace/ParseHubApp/app/src/main/java/com/parsehub/app/ui/screens/ParseScreen.kt#L853-L963) 和 [ResultCard](file:///workspace/ParseHubApp/app/src/main/java/com/parsehub/app/ui/screens/ParseScreen.kt#L679-L784) 属于"解析结果页"(子项目 2),本次只调用不重构。

### 3.5 数据模型扩展

**PlatformItem(数据驱动平台网格):**
```kotlin
data class PlatformItem(
    val id: String,
    val name: String,
    val brandColor: Color,
    val supported: Boolean,
    val capabilities: Set<PlatformCapability>,  // Video/Image/Album/Live/Audio
    val example: String,
    val icon: ImageVector
)
enum class PlatformCapability { VIDEO, IMAGE, ALBUM, LIVE, AUDIO }
```
新增平台只改数据列表,不改 UI。

**ParseError(分类错误):**
```kotlin
sealed class ParseError(val type: String, val message: String) {
    class Network(message: String) : ParseError("network", message)
    class Unsupported(platform: String) : ParseError("unsupported", "$platform 暂不支持")
    class ParseFailed(message: String) : ParseError("parse_failed", message)
    class InvalidLink(message: String) : ParseError("invalid_link", message)
}
```
不同类型对应不同 Icon 和文案。

## 4. 视觉细节与动效规范

### 4.1 HeroHeader

**布局**(220dp full-bleed 顶部):
```
[☀️ 主题切换 右上]
[Logo 72dp]
ParseHub                      ← headlineLarge 32sp SemiBold
智能解析下载中心                ← bodyLarge 15sp 白色 alpha 0.85
支持 5+ 平台                   ← bodySmall 12sp 白 alpha 0.7
```

**视觉:**
- 背景:`HeaderGradient` 覆盖整块
- 毛玻璃:`Modifier.blur(18dp)` + `GlassAlpha=0.78f`(API<31 降级为渐变纯色)
- **Aurora 微动效**:叠加 1-2 个低透明度(3-5%)光斑,缓慢漂浮 20-30 秒一轮,避免死板
- Logo:72dp 白色 Material Icons `AutoAwesome` 占位

**入场动效:**
- Logo:Scale 0.8→1.0 + Fade,Spring(400ms)
- 品牌名:SlideUp 20dp + Fade,Normal
- 副标题:SlideUp + Fade,延迟 100ms

### 4.2 LinkInputCard

**布局**(Surface 卡片,CardRadius=24dp):
```
🔗 粘贴分享链接          [✕]
https://...
─────────────────────
[📋 粘贴]      [✓ 已识别 抖音 视频]
```

**视觉:**
- 卡片:`surfaceContainerLow`,Elevation level1
- 输入区:无边框 BasicTextField + 下划线(InputHeight=60dp)
- 粘贴按钮:AssistChip,ChipRadius=16dp
- **智能识别反馈**:检测到平台时显示 `✓ 已识别 [Logo] 抖音 [Video] 支持下载`,primary 色 + 品牌色

**动效:**
- 识别反馈:`AnimatedVisibility` + SlideInHorizontally + Fade,Normal
- 清空按钮:按需出现,Fast

### 4.3 ParseActionButton(四态)

**状态机:**
| 状态 | 视觉 | 动效 |
|------|------|------|
| Disabled | 灰色 Surface,DisabledAlpha | — |
| Idle | `ButtonGradient` + 发光(level3 + 8dp glow) | 静态 |
| Loading | 渐变降低亮度 alpha 0.7 | 文字渐隐 → CircularProgressIndicator 渐显 |
| Success | 绿色 + Check 图标 | Scale 1.0→1.05→1.0 + Fade,ExtraSlow 600ms 后恢复 Idle |

**规格:** 56dp 高,full width,CardRadius=24dp,图标 `RocketLaunch`(替代 🚀 emoji)

### 4.4 PlatformGrid + PlatformCard

**PlatformCard(88dp):**
```
[Logo 32dp 品牌色]
[平台名 14sp]
[支持/Video+Album 11sp alpha 0.6]
```

**视觉:**
- 卡片背景:品牌色 alpha 0.12(浅色)/ 0.2(深色)
- 边框:品牌色 alpha 0.3,1dp
- 图标:品牌色纯色
- 即将支持:整体 alpha 0.5 + Lock 角标

**动效:**
- 入场:LazyGrid 逐项 Fade + SlideUp,stagger 50ms
- 点击:Scale 0.95 + Ripple

### 4.5 LoadingCard(时间轴)

**布局:**
```
🔍 正在解析链接...     ⏱ 2.3s
预计剩余 2 秒

●  正在识别平台      ✓
│
●  正在获取数据      ✓
│
◉  正在解析资源      ⟳ 进行中(脉冲)
│
○  正在生成下载链接   待处理
```

**阶段映射(扩展 ParseStage 为 4 阶段):**
- DETECTING → 正在识别平台
- FETCHING → 正在获取数据
- PARSING → 正在解析资源(新增)
- DONE → 正在生成下载链接(瞬时)

**视觉:**
- 当前阶段:primary 实心圆 + 脉冲(scale 1.0↔1.2,Normal 无限循环)
- 已完成:primary 空心圆 + Check
- 未开始:onSurfaceVariant alpha 0.3 空心圆
- 连线:已完成段 primary,未完成段 alpha 0.2

### 4.6 ErrorCard

- errorContainer 背景,CardRadius=24dp
- 图标按错误类型:Network=`WifiOff` / Unsupported=`Lock` / ParseFailed=`ErrorOutline` / InvalidLink=`LinkOff`
- 重试按钮:OutlinedButton + `Refresh` 图标
- 入场:Shake ±8dp ×3,Fast

### 4.7 EmptyState(状态化)

根据 HistoryState 变化文案:
- 首次启动:`欢迎使用 ParseHub` + `粘贴链接开始解析`
- 无历史:`开始你的第一次解析` + `试试抖音示例 →`
- 历史清空:`历史已清空` + `重新开始解析`

### 4.8 RecentHistorySection

- 卡片 surfaceContainerLow
- 平台图标用品牌色(替代 emoji)
- 列表项入场:Fade + SlideRight,Fast stagger 30ms
- 展开:`AnimatedVisibility` + ExpandVertically,Normal

### 4.9 FooterInfo

- bodySmall alpha 0.5,居中
- 内容:`Powered by ParseHub Engine · v${BuildConfig.VERSION_NAME}`(不写死)
- 上方 Divider alpha 0.1

### 4.10 Skeleton Loading(全局规范)

- **解析时间 > 400ms 时自动切 Skeleton**(替代全屏 CircularProgressIndicator)
- 适用:图片、卡片、历史项加载
- Skeleton 元素:品牌色 alpha 0.1 矩形 + shimmer 动画(左→右高光扫过,Slow)
- 用户感知"APP 很快"

## 5. 全局动效汇总

| 场景 | 动效 | 时长 |
|------|------|------|
| 页面入场 | SharedAxis Z(Fade+Scale) | Normal 250ms |
| 卡片出现 | Fade + SlideUp 16dp | Normal,stagger 50ms |
| 按钮点击 | Scale 0.96 | Fast 150ms,Spring |
| 解析成功 | Scale + Fade | ExtraSlow 600ms |
| 错误提示 | Shake ±8dp ×3 | Fast |
| Snackbar | SlideUp | Normal |
| 主题切换 | Crossfade 全屏 | Normal |
| Skeleton shimmer | 高光扫过 | Slow 400ms |
| Aurora 光斑 | 漂浮 | 20-30s 无限循环 |

**硬规则:任何动画不超过 400ms,仅 Success 允许 600ms。**

## 6. 测试策略

### 6.1 Design System 单测
- Color/Shape/Radius/Spacing/Dimensions/Elevation:值正确性
- PlatformBrand:8 平台品牌色映射正确

### 6.2 ViewModel 单测(MockRepository)
- Intent 派发 → State 变更
- 解析成功/失败/超时 → State 正确
- UiEffect 发出时机(Toast/Snackbar)
- 历史增删查 → HistoryState 正确

### 6.3 Compose UI 测试(可选,关键流程)
- 输入链接 → 检测平台 → 识别反馈显示
- 点击解析 → Loading → 结果/错误
- 历史展开/收起/删除

### 6.4 验证标准
- 既有 5 平台解析功能不回归
- ParseScreen 行数 < 80,子组件各 < 150
- ParseScreen 无业务 remember
- 所有颜色/圆角/间距引用 Design Token(无硬编码)
- 动画均 ≤ 400ms(仅 Success 600ms)
- APK 体积不显著增加(无新重型依赖)
- CI 全绿

## 7. 实施顺序(供 writing-plans 参考)

1. Design System 基础层(10 个文件)
2. Theme.kt 重构(引用 DS,禁用 dynamicColor)
3. Repository 接口化(IParseRepository)
4. ParseViewModel + State 分片 + Intent + UiEffect
5. ParseRoute + ParseScreen 瘦身
6. 9 个 home 组件逐个实现(TDD:先 VM 测试,再组件)
7. MainActivity 接入 ViewModel
8. 提交推送 CI 验证

## 8. 非目标(明确排除)

- 解析结果页增强(作者头像/点赞评论/下载视频+图片+复制文案+分享)→ 子项目 2
- 下载管理页 → 子项目 3
- 设置页 → 子项目 4
- 历史页独立化(Room 存储)→ 子项目 5
- 平台正式 logo(VectorDrawable)→ 子项目决定
- 字体引入 → 永久非目标(保持瘦身)

## 9. 预期结果

- ParseScreen 从 969 行 → 主文件 < 80 行,9 个组件各 < 150 行
- UI 视觉从 M3 默认味 → Telegram/Nagram/Pixiv 融合质感
- 架构从 Composable 耦合 → 标准 MVVM + UDF + Intent
- 建立可复用 Design System,支撑后续所有页面
- APK 体积不显著增加(系统字体 + Material Icons,无新重型依赖)
