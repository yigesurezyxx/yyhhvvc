package com.parsehub.app.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.parsehub.app.ui.theme.Spacing

/** ParseScreen(spec 3.4:<80 行,纯 UI 组合,零业务逻辑;副作用集中在 ParseRoute) */
@Composable
fun ParseScreen(
    state: ParseUiState,
    isDarkTheme: Boolean,
    onIntent: (ParseIntent) -> Unit,
    onDownloadClick: () -> Unit
) {
    val input = state.input; val parse = state.parse; val history = state.history
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        HeroHeader(isDarkTheme = isDarkTheme, onToggleTheme = { onIntent(ParseIntent.ToggleTheme) })
        Column(modifier = Modifier.padding(Spacing.lg)) {
            LinkInputCard(
                url = input.url, detectedPlatformId = input.detectedPlatformId,
                onUrlChange = { onIntent(ParseIntent.UrlChanged(it)) },
                onPaste = { onIntent(ParseIntent.Paste) },
                onClear = { onIntent(ParseIntent.UrlChanged("")) },
                onParse = { onIntent(ParseIntent.Parse(null)) }
            )
            Spacer(modifier = Modifier.height(Spacing.lg))
            ParseActionButton(
                url = input.url, isParsing = parse.isParsing,
                hasError = parse.error != null,
                hasSuccess = parse.result != null && parse.error == null,
                onClick = { onIntent(if (parse.error != null) ParseIntent.Retry else ParseIntent.Parse(null)) }
            )
            AnimatedVisibility(visible = parse.isParsing, enter = fadeIn(), exit = fadeOut()) {
                LoadingCard(stage = parse.stage, elapsedMs = parse.elapsedMs)
            }
            parse.error?.let { error ->
                Spacer(modifier = Modifier.height(Spacing.lg))
                ErrorCard(error = error, onRetry = { onIntent(ParseIntent.Retry) })
            }
            parse.result?.takeIf { it.isSuccess }?.let { result ->
                Spacer(modifier = Modifier.height(Spacing.lg))
                ResultSection(
                    result = result, isDownloading = state.download.isDownloading,
                    downloadStatus = state.download.status, downloadSuccess = state.download.success,
                    onDownloadClick = onDownloadClick
                )
            }
            Spacer(modifier = Modifier.height(Spacing.xl))
            PlatformGrid(onPlatformClick = { onIntent(ParseIntent.UrlChanged(it)) })
            if (history.items.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Spacing.lg))
                RecentHistorySection(
                    items = history.items, expanded = history.expanded,
                    onToggleExpand = { onIntent(ParseIntent.ToggleHistory) },
                    onItemClick = { onIntent(ParseIntent.HistoryItemClick(it)) },
                    onItemDelete = { onIntent(ParseIntent.DeleteHistory(it)) },
                    onClearAll = { onIntent(ParseIntent.ClearAllHistory) }
                )
            } else if (parse.result == null && !parse.isParsing && parse.error == null) {
                EmptyState(kind = EmptyKind.NO_HISTORY)
            }
            FooterInfo()
        }
    }
}
