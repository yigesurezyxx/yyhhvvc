package com.parsehub.app.ui.screens.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.parsehub.app.ui.theme.AlphaTokens
import com.parsehub.app.ui.theme.Dimensions
import com.parsehub.app.ui.theme.Spacing

/**
 * 状态化空态引导(spec 4.7)
 *
 * 根据 [EmptyKind] 切换文案:
 * - FIRST_RUN:首次启动
 * - NO_HISTORY:无历史
 * - HISTORY_CLEARED:历史已清空
 *
 * Pure UI:由 ParseScreen 根据 HistoryState 推断 kind 后传入。
 */
enum class EmptyKind { FIRST_RUN, NO_HISTORY, HISTORY_CLEARED }

@Composable
fun EmptyState(
    kind: EmptyKind,
    modifier: Modifier = Modifier
) {
    val (title, subtitle) = when (kind) {
        EmptyKind.FIRST_RUN -> "欢迎使用 ParseHub" to "粘贴链接开始解析"
        EmptyKind.NO_HISTORY -> "开始你的第一次解析" to "点击下方平台卡片试试示例"
        EmptyKind.HISTORY_CLEARED -> "历史已清空" to "重新开始解析"
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = AlphaTokens.Subtitle),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Dimensions.icon))
    }
}
