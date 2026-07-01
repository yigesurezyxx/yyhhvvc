package com.parsehub.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ========== Color Schemes(引用 Color.kt) ==========

private val DarkColorScheme = darkColorScheme(
    primary = DarkColors.primary,
    onPrimary = DarkColors.onPrimary,
    primaryContainer = DarkColors.primaryContainer,
    onPrimaryContainer = DarkColors.onPrimaryContainer,
    secondary = DarkColors.secondary,
    onSecondary = DarkColors.onSecondary,
    tertiary = DarkColors.tertiary,
    background = DarkColors.background,
    onBackground = DarkColors.onBackground,
    surface = DarkColors.surface,
    onSurface = DarkColors.onSurface,
    surfaceVariant = DarkColors.surfaceVariant,
    onSurfaceVariant = DarkColors.onSurfaceVariant,
    surfaceContainerLowest = DarkColors.surfaceContainerLowest,
    surfaceContainerLow = DarkColors.surfaceContainerLow,
    surfaceContainer = DarkColors.surfaceContainer,
    surfaceContainerHigh = DarkColors.surfaceContainerHigh,
    surfaceContainerHighest = DarkColors.surfaceContainerHighest,
    error = DarkColors.error,
    onError = DarkColors.onError,
    errorContainer = DarkColors.errorContainer,
    onErrorContainer = DarkColors.onErrorContainer,
    outline = DarkColors.outline,
)

private val LightColorScheme = lightColorScheme(
    primary = LightColors.primary,
    onPrimary = LightColors.onPrimary,
    primaryContainer = LightColors.primaryContainer,
    onPrimaryContainer = LightColors.onPrimaryContainer,
    secondary = LightColors.secondary,
    onSecondary = LightColors.onSecondary,
    tertiary = LightColors.tertiary,
    background = LightColors.background,
    onBackground = LightColors.onBackground,
    surface = LightColors.surface,
    onSurface = LightColors.onSurface,
    surfaceVariant = LightColors.surfaceVariant,
    onSurfaceVariant = LightColors.onSurfaceVariant,
    surfaceContainerLowest = LightColors.surfaceContainerLowest,
    surfaceContainerLow = LightColors.surfaceContainerLow,
    surfaceContainer = LightColors.surfaceContainer,
    surfaceContainerHigh = LightColors.surfaceContainerHigh,
    surfaceContainerHighest = LightColors.surfaceContainerHighest,
    error = LightColors.error,
    onError = LightColors.onError,
    errorContainer = LightColors.errorContainer,
    onErrorContainer = LightColors.onErrorContainer,
    outline = LightColors.outline,
)

// ========== CompositionLocal 入口 ==========

val LocalSpacing = compositionLocalOf { Spacing }
val LocalDimensions = compositionLocalOf { Dimensions }
val LocalRadius = compositionLocalOf { Radius }
val LocalElevation = compositionLocalOf { Elevation }
val LocalMotion = compositionLocalOf { Motion }

/** 统一访问入口,如:ParseTheme.spacing.large / ParseTheme.radius.card */
object ParseTheme {
    val spacing get() = Spacing
    val dimensions get() = Dimensions
    val radius get() = Radius
    val elevation get() = Elevation
    val motion get() = Motion
}

// ========== Theme 入口 ==========

/**
 * ParseHub 主题入口
 *
 * - 固定品牌色(禁用 Material You dynamicColor),保证所有用户看同样蓝调
 * - 引用 Design System 所有 Token
 * - 提供 CompositionLocal 给子组件访问 spacing/radius 等
 *
 * @param darkTheme 是否深色模式(由 ThemeManager 决定,默认跟随系统)
 */
@Composable
fun ParseHubTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalSpacing provides Spacing,
        LocalDimensions provides Dimensions,
        LocalRadius provides Radius,
        LocalElevation provides Elevation,
        LocalMotion provides Motion
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ParseTypography,
            shapes = ParseShapes,
            content = content
        )
    }
}

// ========== Preview Theme(供 Compose Preview 复用) ==========

@Composable
fun PreviewLightTheme(content: @Composable () -> Unit) {
    ParseHubTheme(darkTheme = false, content = content)
}

@Composable
fun PreviewDarkTheme(content: @Composable () -> Unit) {
    ParseHubTheme(darkTheme = true, content = content)
}
