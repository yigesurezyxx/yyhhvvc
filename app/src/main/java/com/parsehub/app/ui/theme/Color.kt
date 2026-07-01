package com.parsehub.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ========== Color Scheme ==========

/** 浅色色板 */
object LightColors {
    val background = Color(0xFFF8FAFC)
    val surface = Color(0xFFFFFFFF)
    val surfaceContainerLowest = Color(0xFFFFFFFF)
    val surfaceContainerLow = Color(0xFFF1F5F9)
    val surfaceContainer = Color(0xFFE2E8F0)
    val surfaceContainerHigh = Color(0xFFCBD5E1)
    val surfaceContainerHighest = Color(0xFF94A3B8)
    val surfaceVariant = Color(0xFFF1F5F9)
    val primary = Color(0xFF4F7CFF)
    val onPrimary = Color(0xFFFFFFFF)
    val primaryContainer = Color(0xFFE0E7FF)
    val onPrimaryContainer = Color(0xFF1E3A5F)
    val secondary = Color(0xFF8B5CF6)
    val onSecondary = Color(0xFFFFFFFF)
    val tertiary = Color(0xFFEC4899)
    val error = Color(0xFFEF4444)
    val onError = Color(0xFFFFFFFF)
    val errorContainer = Color(0xFFFEE2E2)
    val onErrorContainer = Color(0xFF7F1D1D)
    val onBackground = Color(0xFF1A1A2E)
    val onSurface = Color(0xFF1A1A2E)
    val onSurfaceVariant = Color(0xFF64748B)
    val outline = Color(0xFFCBD5E1)
}

/** 深色色板 */
object DarkColors {
    val background = Color(0xFF0F172A)
    val surface = Color(0xFF1E293B)
    val surfaceContainerLowest = Color(0xFF0F172A)
    val surfaceContainerLow = Color(0xFF1E293B)
    val surfaceContainer = Color(0xFF293548)
    val surfaceContainerHigh = Color(0xFF334155)
    val surfaceContainerHighest = Color(0xFF475569)
    val surfaceVariant = Color(0xFF334155)
    val primary = Color(0xFF60A5FA)
    val onPrimary = Color(0xFF0F172A)
    val primaryContainer = Color(0xFF1E3A5F)
    val onPrimaryContainer = Color(0xFFE0E7FF)
    val secondary = Color(0xFFA78BFA)
    val onSecondary = Color(0xFF0F172A)
    val tertiary = Color(0xFFF472B6)
    val error = Color(0xFFF87171)
    val onError = Color(0xFF0F172A)
    val errorContainer = Color(0xFF7F1D1D)
    val onErrorContainer = Color(0xFFFECACA)
    val onBackground = Color(0xFFE0E0E0)
    val onSurface = Color(0xFFE0E0E0)
    val onSurfaceVariant = Color(0xFF94A3B8)
    val outline = Color(0xFF334155)
}

// ========== 渐变画笔 ==========

/** Hero Header 背景:蓝 → 紫 */
val HeaderGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF4F7CFF), Color(0xFF8B5CF6))
)

/** 主 CTA 按钮:蓝 → 紫 */
val ButtonGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF4F7CFF), Color(0xFF8B5CF6))
)

// ========== Alpha Token ==========

object AlphaTokens {
    /** 毛玻璃叠加透明度 */
    val Glass = 0.78f
    /** 阴影透明度 */
    val Shadow = 0.12f
    /** 边框透明度 */
    val Border = 0.1f
    /** 禁用态透明度 */
    val Disabled = 0.38f
    /** 辅助文本透明度 */
    val Subtitle = 0.7f
}

// ========== 状态色 ==========

object StatusColors {
    val success = LightPair(Color(0xFF22C55E), Color(0xFF4ADE80))
    val warning = LightPair(Color(0xFFF59E0B), Color(0xFFFBBF24))
    val error = LightPair(Color(0xFFEF4444), Color(0xFFF87171))
    val info = LightPair(Color(0xFF3B82F6), Color(0xFF60A5FA))
    val disabled = LightPair(Color(0xFF94A3B8), Color(0xFF475569))
}

/** 浅深色成对状态色 */
data class LightPair(val light: Color, val dark: Color)
