package com.parsehub.app.ui.screens.home

import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.parsehub.app.ui.theme.AlphaTokens
import com.parsehub.app.ui.theme.Dimensions
import com.parsehub.app.ui.theme.HeaderGradient
import com.parsehub.app.ui.theme.Motion
import com.parsehub.app.ui.theme.ParseIcons
import com.parsehub.app.ui.theme.Spacing

/**
 * Hero 顶部头(spec 4.1)
 *
 * - 220dp full-bleed,HeaderGradient 背景
 * - Aurora 微动效:2 个低透明度光斑缓慢漂浮(20-30s 一轮)
 * - Logo 72dp + 品牌名 + 副标题 + 支持平台数
 * - 入场:Logo Scale 0.8→1.0 Spring;文字 SlideUp + Fade
 */
@Composable
fun HeroHeader(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val aurora = rememberInfiniteTransition(label = "aurora")
    val x1 by aurora.animateFloat(
        initialValue = -40f, targetValue = 40f,
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "x1"
    )
    val y1 by aurora.animateFloat(
        initialValue = -20f, targetValue = 30f,
        animationSpec = infiniteRepeatable(tween(28_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "y1"
    )
    val x2 by aurora.animateFloat(
        initialValue = 30f, targetValue = -30f,
        animationSpec = infiniteRepeatable(tween(26_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "x2"
    )
    val logoScale by aurora.animateFloat(
        initialValue = 0.8f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(Motion.Slow, easing = LinearEasing), RepeatMode.Reverse),
        label = "logoScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimensions.headerHeight)
            .background(HeaderGradient)
    ) {
        // Aurora 光斑(alpha 0.04,blur 软化,API<31 自动降级)
        AuroraBlob(size = 160.dp, offset = x1.dp to y1.dp, modifier = Modifier.align(Alignment.TopStart))
        AuroraBlob(size = 200.dp, offset = x2.dp to y1.dp, modifier = Modifier.align(Alignment.BottomEnd))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = Spacing.md),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "设置",
                    tint = Color.White
                )
            }
            IconButton(onClick = onToggleTheme) {
                Icon(
                    imageVector = if (isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                    contentDescription = "切换主题",
                    tint = Color.White
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = ParseIcons.Logo,
                contentDescription = "ParseHub",
                tint = Color.White,
                modifier = Modifier
                    .size(Dimensions.logo)
                    .scale(logoScale)
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = "ParseHub",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = "智能解析下载中心",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f)
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = "支持 5+ 平台",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = AlphaTokens.Subtitle)
            )
        }
    }
}

@Composable
private fun AuroraBlob(
    size: androidx.compose.ui.unit.Dp,
    offset: Pair<androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.Dp>,
    modifier: Modifier = Modifier
) {
    // 去掉 Modifier.blur(API<31 渲染崩溃风险),用径向渐变 + 低 alpha 代替软化效果
    Box(
        modifier = modifier
            .offset(x = offset.first, y = offset.second)
            .size(size)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.08f), Color.Transparent)
                ),
                shape = CircleShape
            )
    )
}
