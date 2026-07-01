package com.parsehub.app.ui.screens.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.MusicVideo
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.graphics.vector.ImageVector
import com.parsehub.app.ui.theme.BrandSpec
import com.parsehub.app.ui.theme.PlatformBrand
import com.parsehub.app.ui.theme.PlatformCapability

/**
 * 平台数据模型(spec 3.5:数据驱动 PlatformGrid)
 *
 * - id 与 [ParseViewModel.detectPlatformId] 返回值一致,用于 LinkInputCard 智能识别反馈
 * - icon:本次先用 Material Icons 占位,正式 logo 子项目决定(spec 2.9)
 * - 新增平台只改 [PlatformCatalog],不改 UI
 */
data class PlatformItem(
    val id: String,
    val name: String,
    val brand: BrandSpec,
    val supported: Boolean,
    val capabilities: Set<PlatformCapability>,
    val example: String,
    val icon: ImageVector
)

/**
 * 平台目录(8 平台,与 PlatformBrand 一一对应)
 *
 * 注:VM 的 detectPlatformId 返回 "twitter"(非 "x"),此处 id 与之对齐,
 * brand 字段复用 PlatformBrand.x。
 */
val PlatformCatalog: List<PlatformItem> = listOf(
    PlatformItem(
        id = "douyin", name = "抖音", brand = PlatformBrand.douyin, supported = true,
        capabilities = setOf(PlatformCapability.VIDEO, PlatformCapability.LIVE, PlatformCapability.IMAGE),
        example = "https://www.iesdouyin.com/share/video/7300000000000000000",
        icon = Icons.Filled.MusicVideo
    ),
    PlatformItem(
        id = "bilibili", name = "B站", brand = PlatformBrand.bilibili, supported = true,
        capabilities = setOf(PlatformCapability.VIDEO, PlatformCapability.LIVE, PlatformCapability.AUDIO),
        example = "https://www.bilibili.com/video/BV1xx411c7mD",
        icon = Icons.Filled.PlayCircle
    ),
    PlatformItem(
        id = "xiaohongshu", name = "小红书", brand = PlatformBrand.xiaohongshu, supported = true,
        capabilities = setOf(PlatformCapability.IMAGE, PlatformCapability.ALBUM, PlatformCapability.VIDEO),
        example = "https://www.xiaohongshu.com/explore/6700000000000000000?xsec_token=Abc123",
        icon = Icons.Filled.PhotoLibrary
    ),
    PlatformItem(
        id = "weibo", name = "微博", brand = PlatformBrand.weibo, supported = true,
        capabilities = setOf(PlatformCapability.IMAGE, PlatformCapability.VIDEO),
        example = "https://weibo.com/6001863056/5315444644776304",
        icon = Icons.Filled.Campaign
    ),
    PlatformItem(
        id = "kuaishou", name = "快手", brand = PlatformBrand.kuaishou, supported = true,
        capabilities = setOf(PlatformCapability.VIDEO, PlatformCapability.LIVE),
        example = "https://v.kuaishou.com/xxx",
        icon = Icons.Filled.Videocam
    ),
    PlatformItem(
        id = "youtube", name = "YouTube", brand = PlatformBrand.youtube, supported = false,
        capabilities = setOf(PlatformCapability.VIDEO),
        example = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        icon = Icons.Filled.PlayCircle
    ),
    PlatformItem(
        id = "twitter", name = "Twitter", brand = PlatformBrand.x, supported = false,
        capabilities = setOf(PlatformCapability.IMAGE, PlatformCapability.VIDEO),
        example = "https://twitter.com/user/status/1234567890",
        icon = Icons.Filled.Public
    ),
    PlatformItem(
        id = "tieba", name = "贴吧", brand = PlatformBrand.tieba, supported = false,
        capabilities = setOf(PlatformCapability.IMAGE),
        example = "https://tieba.baidu.com/p/9000000000",
        icon = Icons.Filled.Forum
    )
)

/** 按 id 查询平台,null 表示未识别 */
fun platformById(id: String?): PlatformItem? = id?.let { target ->
    PlatformCatalog.firstOrNull { it.id == target }
}
