package com.parsehub.app.ui.screens.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.parsehub.app.BuildConfig
import com.parsehub.app.ui.theme.Spacing

/**
 * 版本信息(spec 4.9)
 *
 * - bodySmall alpha 0.5,居中
 * - 上方 Divider alpha 0.1
 * - 版本号取自 BuildConfig.VERSION_NAME(不写死)
 */
@Composable
fun FooterInfo(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
        Text(
            text = "Powered by ParseHub Engine · v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.sm)
        )
    }
}
