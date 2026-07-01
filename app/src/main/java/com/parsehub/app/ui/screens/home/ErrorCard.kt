package com.parsehub.app.ui.screens.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.parsehub.app.data.ParseError
import com.parsehub.app.ui.theme.MotionSpec
import com.parsehub.app.ui.theme.ParseIcons
import com.parsehub.app.ui.theme.RadiusShapes
import com.parsehub.app.ui.theme.Spacing

/**
 * 分类错误提示(spec 4.6)
 *
 * - errorContainer 背景,CardRadius=24dp
 * - 图标按错误类型:Network/Unsupported/ParseFailed/InvalidLink
 * - 重试按钮:OutlinedButton + Refresh 图标
 * - 入场:Shake ±8dp ×3(spec 5 全局动效)
 */
@Composable
fun ErrorCard(
    error: ParseError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(error) {
        val amp = MotionSpec.ShakeAmplitude
        shake.animateTo(
            targetValue = 0f,
            animationSpec = keyframes {
                durationMillis = MotionSpec.ShakeRepeat * 150
                0f at 0 with LinearEasing
                amp at 50 with LinearEasing
                -amp at 100 with LinearEasing
                amp at 150 with LinearEasing
                -amp at 200 with LinearEasing
                amp at 250 with LinearEasing
                -amp at 300 with LinearEasing
                0f at 450 with LinearEasing
            }
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = shake.value.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RadiusShapes.card
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = iconFor(error),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(
                    text = titleFor(error),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.size(Spacing.sm))
            Text(
                text = error.message.ifBlank { "请检查链接后重试" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.size(Spacing.md))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onRetry) {
                    Icon(
                        imageVector = ParseIcons.Retry,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text("重试")
                }
            }
        }
    }
}

private fun iconFor(error: ParseError): ImageVector = when (error) {
    is ParseError.Network -> ParseIcons.ErrorNetwork
    is ParseError.Unsupported -> ParseIcons.ErrorUnsupported
    is ParseError.ParseFailed -> ParseIcons.ErrorParseFailed
    is ParseError.InvalidLink -> ParseIcons.ErrorInvalidLink
}

private fun titleFor(error: ParseError): String = when (error) {
    is ParseError.Network -> "网络异常"
    is ParseError.Unsupported -> "暂不支持"
    is ParseError.ParseFailed -> "解析失败"
    is ParseError.InvalidLink -> "链接无效"
}
