package com.parsehub.app.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.parsehub.app.ui.theme.AlphaTokens
import com.parsehub.app.ui.theme.CapabilityIcons
import com.parsehub.app.ui.theme.Dimensions
import com.parsehub.app.ui.theme.Elevation as DsElevation
import com.parsehub.app.ui.theme.ParseIcons
import com.parsehub.app.ui.theme.PlatformCapability
import com.parsehub.app.ui.theme.RadiusShapes
import com.parsehub.app.ui.theme.Spacing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check

/**
 * 链接输入卡片(spec 4.2)
 *
 * - Surface 卡片,CardRadius=24dp,surfaceContainerLow,Elevation level1
 * - 输入 + 粘贴 + 清空 + 智能识别反馈
 * - 识别反馈:AnimatedVisibility + SlideInHorizontally + Fade,Normal
 */
@Composable
fun LinkInputCard(
    url: String,
    detectedPlatformId: String?,
    onUrlChange: (String) -> Unit,
    onPaste: () -> Unit,
    onClear: () -> Unit,
    onParse: () -> Unit,
    modifier: Modifier = Modifier
) {
    val detected = platformById(detectedPlatformId)
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = DsElevation.level1),
        shape = RadiusShapes.card
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = ParseIcons.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Dimensions.icon)
                )
                Spacer(modifier = Modifier.width(Spacing.sm))
                OutlinedTextField(
                    value = url,
                    onValueChange = onUrlChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            text = "粘贴分享链接",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = AlphaTokens.Subtitle),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    shape = RadiusShapes.input,
                    singleLine = true,
                    maxLines = 1,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onParse() }),
                    trailingIcon = {
                        if (url.isNotEmpty()) {
                            IconButton(onClick = onClear) {
                                Icon(
                                    imageVector = ParseIcons.Clear,
                                    contentDescription = "清空",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(Dimensions.icon)
                                )
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = onPaste,
                    label = { Text("粘贴") },
                    leadingIcon = {
                        Icon(ParseIcons.Paste, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                AnimatedVisibility(
                    visible = detected != null,
                    enter = slideInHorizontally(initialOffsetX = { it / 2 }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { it / 2 }) + fadeOut()
                ) {
                    detected?.let { DetectionBadge(it) }
                }
            }
        }
    }
}

@Composable
private fun DetectionBadge(platform: PlatformItem) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val brandColor = platform.brand.color(isDark)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(Spacing.xs))
        Icon(
            imageVector = platform.icon,
            contentDescription = platform.name,
            tint = brandColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(Spacing.xs))
        Text(
            text = platform.name,
            style = MaterialTheme.typography.bodySmall,
            color = brandColor
        )
        if (platform.supported) {
            Spacer(modifier = Modifier.width(Spacing.sm))
            platform.capabilities.firstOrNull()?.let { cap ->
                Icon(
                    imageVector = iconForCapability(cap),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(Spacing.xs))
            Text(
                text = "支持下载",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(
                text = "即将支持",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = AlphaTokens.Subtitle)
            )
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

private fun Color.luminance(): Float {
    val r = red; val g = green; val b = blue
    return 0.299f * r + 0.587f * g + 0.114f * b
}
