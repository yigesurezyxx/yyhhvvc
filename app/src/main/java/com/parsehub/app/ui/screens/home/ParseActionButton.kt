package com.parsehub.app.ui.screens.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.parsehub.app.ui.theme.AlphaTokens
import com.parsehub.app.ui.theme.ButtonGradient
import com.parsehub.app.ui.theme.Dimensions
import com.parsehub.app.ui.theme.MotionSpec
import com.parsehub.app.ui.theme.ParseIcons
import com.parsehub.app.ui.theme.RadiusShapes
import com.parsehub.app.ui.theme.Spacing
import com.parsehub.app.ui.theme.StatusColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.luminance

/**
 * 解析主按钮(四态,spec 4.3)
 *
 * | 状态 | 触发 | 视觉 |
 * |------|------|------|
 * | Disabled | url 空 | 灰色,DisabledAlpha |
 * | Idle | 可解析 | ButtonGradient + 发光 |
 * | Loading | isParsing | 渐变 alpha 0.7 + CircularProgressIndicator |
 * | Success | 解析成功 | 绿色 + Check(瞬时 scale 弹跳) |
 *
 * 错误态复用 Idle 文案改为"重试"。
 */
@Composable
fun ParseActionButton(
    url: String,
    isParsing: Boolean,
    hasError: Boolean,
    hasSuccess: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val enabled = !isParsing && url.isNotBlank()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) MotionSpec.ButtonScaleTarget else 1f,
        animationSpec = spring<Float>(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "pressScale"
    )
    val successScale by animateFloatAsState(
        targetValue = if (hasSuccess) MotionSpec.SuccessScalePeak else 1f,
        animationSpec = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "successScale"
    )
    val scale = if (hasSuccess) successScale else pressScale

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val successColor = if (isDark) StatusColors.success.dark else StatusColors.success.light
    val background: Brush = when {
        !enabled -> Brush.linearGradient(
            listOf(Color.Gray.copy(alpha = AlphaTokens.Disabled), Color.Gray.copy(alpha = AlphaTokens.Disabled))
        )
        hasSuccess -> Brush.linearGradient(listOf(successColor, successColor))
        isParsing -> Brush.linearGradient(BRAND_COLORS.map { it.copy(alpha = 0.7f) })
        else -> ButtonGradient
    }
    val contentAlpha = if (isParsing) 0.7f else 1f

    val text = when {
        isParsing -> "解析中..."
        hasError -> "重试"
        hasSuccess -> "重新解析"
        else -> "开始解析"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimensions.buttonHeight)
            .scale(scale)
            .shadow(
                elevation = if (enabled && !isParsing && !hasSuccess) Dimensions.buttonHeight / 7 else 0.dp,
                shape = RadiusShapes.card,
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Shadow),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Shadow)
            )
            .clip(RadiusShapes.card)
            .background(background)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isParsing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = if (hasSuccess) Icons.Filled.Check else ParseIcons.ParseAction,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(Dimensions.icon)
                )
            }
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = if (!enabled) AlphaTokens.Disabled else contentAlpha)
            )
        }
    }
}

private val BRAND_COLORS = listOf(Color(0xFF4F7CFF), Color(0xFF8B5CF6))

