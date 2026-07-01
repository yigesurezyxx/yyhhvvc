package com.parsehub.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.parsehub.app.ui.theme.AlphaTokens
import com.parsehub.app.ui.theme.CapabilityIcons
import com.parsehub.app.ui.theme.Dimensions
import com.parsehub.app.ui.theme.PlatformCapability
import com.parsehub.app.ui.theme.RadiusShapes
import com.parsehub.app.ui.theme.Spacing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring

/**
 * 平台卡片网格(spec 4.4)
 *
 * - 数据驱动 [PlatformCatalog],新增平台只改数据
 * - 4 列布局,卡片 88dp
 * - 即将支持:整体 alpha 0.5 + Lock 角标
 * - 点击:Scale 0.95 + Ripple,填入示例链接
 */
@Composable
fun PlatformGrid(
    onPlatformClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "支持平台",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        PlatformCatalog.chunked(4).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                rowItems.forEach { platform ->
                    PlatformCard(
                        platform = platform,
                        onClick = { onPlatformClick(platform.example) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(4 - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
            Spacer(modifier = Modifier.height(Spacing.sm))
        }
        Text(
            text = "点击平台名可填入示例链接,灰色为即将支持",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = AlphaTokens.Subtitle)
        )
    }
}

@Composable
private fun PlatformCard(
    platform: PlatformItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val brandColor = platform.brand.color(isDark)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring<Float>(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "cardScale"
    )
    val containerAlpha = if (isDark) 0.2f else 0.12f

    Box(
        modifier = modifier
            .height(Dimensions.platformCard)
            .scale(scale)
            .background(brandColor.copy(alpha = containerAlpha), RadiusShapes.chip)
            .border(1.dp, brandColor.copy(alpha = 0.3f), RadiusShapes.chip)
            .clickable(
                enabled = platform.supported,
                interactionSource = interactionSource,
                indication = LocalIndication.current
            ) { onClick() }
    ) {
        if (!platform.supported) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = "即将支持",
                tint = brandColor.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.xs)
                    .size(12.dp)
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(Spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = platform.icon,
                contentDescription = platform.name,
                tint = if (platform.supported) brandColor else brandColor.copy(alpha = 0.5f),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = platform.name,
                style = MaterialTheme.typography.bodySmall,
                color = if (platform.supported) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.Center) {
                platform.capabilities.take(2).forEach { cap ->
                    Icon(
                        imageVector = iconForCapability(cap),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                }
            }
        }
    }
}

private fun iconForCapability(cap: PlatformCapability) = when (cap) {
    PlatformCapability.VIDEO -> CapabilityIcons.VIDEO
    PlatformCapability.IMAGE -> CapabilityIcons.IMAGE
    PlatformCapability.ALBUM -> CapabilityIcons.ALBUM
    PlatformCapability.LIVE -> CapabilityIcons.LIVE
    PlatformCapability.AUDIO -> CapabilityIcons.AUDIO
}

private fun Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue
