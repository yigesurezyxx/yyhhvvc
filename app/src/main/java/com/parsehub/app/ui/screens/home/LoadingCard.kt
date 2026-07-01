package com.parsehub.app.ui.screens.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.parsehub.app.data.ParseStage
import com.parsehub.app.ui.theme.Motion
import com.parsehub.app.ui.theme.RadiusShapes
import com.parsehub.app.ui.theme.Spacing

/**
 * 时间轴进度卡片(spec 4.5)
 *
 * 阶段映射(基于实际 ParseStage 枚举):
 * - DETECTING → 正在识别平台
 * - FETCHING → 正在获取数据
 * - DONE → 正在生成下载链接(瞬时)
 *
 * 视觉:当前阶段 primary 实心圆 + 脉冲;已完成 Check;未开始空心圆。
 */
@Composable
fun LoadingCard(
    stage: ParseStage?,
    elapsedMs: Long,
    modifier: Modifier = Modifier
) {
    val stages = listOf(
        ParseStage.DETECTING to "正在识别平台",
        ParseStage.FETCHING to "正在获取数据",
        ParseStage.DONE to "正在生成下载链接"
    )
    val currentIndex = stage?.let { s -> stages.indexOfFirst { it.first == s }.takeIf { it >= 0 } } ?: -1

    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(Motion.Normal, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RadiusShapes.card
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stage?.label ?: "正在解析链接...",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "%.1fs".format(elapsedMs / 1000.0),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.height(Spacing.md))
            stages.forEachIndexed { index, (_, label) ->
                TimelineRow(
                    label = label,
                    state = when {
                        currentIndex < 0 -> TimelineState.ACTIVE
                        index < currentIndex -> TimelineState.DONE
                        index == currentIndex -> TimelineState.ACTIVE
                        else -> TimelineState.PENDING
                    },
                    pulseScale = if (index == currentIndex) pulseScale else 1f
                )
                if (index < stages.lastIndex) {
                    TimelineConnector(
                        active = currentIndex >= 0 && index < currentIndex
                    )
                }
            }
        }
    }
}

private enum class TimelineState { PENDING, ACTIVE, DONE }

@Composable
private fun TimelineRow(label: String, state: TimelineState, pulseScale: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val dotColor = if (state == TimelineState.PENDING)
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        else MaterialTheme.colorScheme.primary
        Box(
            modifier = Modifier
                .size(16.dp)
                .scale(if (state == TimelineState.ACTIVE) pulseScale else 1f)
                .background(dotColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (state == TimelineState.DONE) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(Spacing.md))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (state == TimelineState.PENDING)
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun TimelineConnector(active: Boolean) {
    Box(
        modifier = Modifier
            .padding(start = 7.dp)
            .height(16.dp)
            .width(2.dp)
            .background(
                if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
            )
    )
}
