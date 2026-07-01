package com.parsehub.app.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicVideo
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * ParseHub 图标统一入口
 *
 * - 三套图标包:Filled / Outlined / Rounded(按 Material Icons 扩展)
 * - 平台品牌色映射
 * - 状态色图标
 * - 平台 logo:本次先用 Material Icons 占位,正式 logo 子项目决定
 */
object ParseIcons {
    // ===== App 通用 =====
    val Logo: ImageVector = Icons.Filled.AutoAwesome
    val ParseAction: ImageVector = Icons.Filled.RocketLaunch
    val Paste: ImageVector = Icons.Filled.ContentPaste
    val Clear: ImageVector = Icons.Filled.Clear
    val Link: ImageVector = Icons.Filled.Link
    val Check: ImageVector = Icons.Filled.Check
    val Settings: ImageVector = Icons.Filled.Settings

    // ===== 错误分类 =====
    val ErrorNetwork: ImageVector = Icons.Filled.WifiOff
    val ErrorUnsupported: ImageVector = Icons.Filled.Lock
    val ErrorParseFailed: ImageVector = Icons.Filled.ErrorOutline
    val ErrorInvalidLink: ImageVector = Icons.Filled.Link
    val Retry: ImageVector = Icons.Filled.Refresh
    val Info: ImageVector = Icons.Filled.Info
    val Warning: ImageVector = Icons.Filled.Warning
}

/** 平台品牌色映射 */
object PlatformBrand {
    val douyin = BrandSpec(Color(0xFF000000), Color(0xFFFFFFFF), supported = true)
    val bilibili = BrandSpec(Color(0xFF00A1D6), Color(0xFF00A1D6), supported = true)
    val xiaohongshu = BrandSpec(Color(0xFFFF2442), Color(0xFFFF2442), supported = true)
    val weibo = BrandSpec(Color(0xFFE6162D), Color(0xFFE6162D), supported = true)
    val kuaishou = BrandSpec(Color(0xFFFF4906), Color(0xFFFF4906), supported = true)
    val tieba = BrandSpec(Color(0xFF4E6EF2), Color(0xFF4E6EF2), supported = false)
    val youtube = BrandSpec(Color(0xFFFF0000), Color(0xFFFF0000), supported = false)
    val x = BrandSpec(Color(0xFF000000), Color(0xFFFFFFFF), supported = false)
}

/** 平台品牌色规格(浅色/深色,支持状态) */
data class BrandSpec(
    val lightColor: Color,
    val darkColor: Color,
    val supported: Boolean
) {
    fun color(isDark: Boolean): Color = if (isDark) darkColor else lightColor
}

/** 平台能力(数据驱动 PlatformGrid) */
enum class PlatformCapability { VIDEO, IMAGE, ALBUM, LIVE, AUDIO }

/** 平台能力图标映射 */
object CapabilityIcons {
    val VIDEO: ImageVector = Icons.Filled.Videocam
    val IMAGE: ImageVector = Icons.Filled.PhotoLibrary
    val ALBUM: ImageVector = Icons.Filled.PhotoLibrary
    val LIVE: ImageVector = Icons.Filled.PlayCircle
    val AUDIO: ImageVector = Icons.Filled.MusicVideo
}
