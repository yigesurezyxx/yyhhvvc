package com.parsehub.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.parsehub.app.data.MediaInfo
import com.parsehub.app.data.ParseResult

/**
 * 解析结果区(spec 3.4:保留不动,本次只调用不重构)
 *
 * 包装 [ResultCard] + [MediaGrid],属"解析结果页"(子项目 2)。
 * 此处仅迁移到 home 包并暴露 [ResultSection] 入口,代码逻辑保持原样。
 */
@Composable
fun ResultSection(
    result: ParseResult,
    isDownloading: Boolean,
    downloadStatus: String?,
    downloadSuccess: Boolean,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ResultCard(
        result = result,
        isDownloading = isDownloading,
        downloadStatus = downloadStatus,
        downloadSuccess = downloadSuccess,
        onDownloadClick = onDownloadClick,
        modifier = modifier
    )
}

@Composable
internal fun ResultCard(
    result: ParseResult,
    isDownloading: Boolean,
    downloadStatus: String?,
    downloadSuccess: Boolean,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
                    Text("下载到相册")
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

@Composable
internal fun MediaGrid(mediaList: List<MediaInfo>) {
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
                            Box(modifier = Modifier.weight(1f).aspectRatio(1f)) {
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
internal fun SingleMedia(media: MediaInfo) {
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
                    .background(color = Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("▶", color = Color.White, fontSize = 24.sp)
            }
        }
    }
}

@Composable
internal fun MediaThumbnail(media: MediaInfo) {
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
                    .background(color = Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("▶", color = Color.White, fontSize = 16.sp)
            }
        }
    }
}

private fun isVideoType(type: String): Boolean =
    type.contains("Video", ignoreCase = true) ||
        type.contains("Live", ignoreCase = true) ||
        type.contains("video", ignoreCase = true)
