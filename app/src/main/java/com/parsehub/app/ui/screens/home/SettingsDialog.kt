package com.parsehub.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.parsehub.app.ui.theme.Spacing

/**
 * 设置对话框 — 配置各平台 Cookie
 *
 * 快手等平台需要登录 Cookie 才能解析无水印视频。
 * Cookie 获取方式: 浏览器登录对应平台 → F12 → Application → Cookies → 复制。
 */
@Composable
fun SettingsDialog(
    state: SettingsState,
    onDismiss: () -> Unit,
    onCookieChange: (platformId: String, cookie: String) -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Cookie 设置",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = "部分平台(如快手)需要登录 Cookie 才能解析无水印内容。\n从浏览器登录后复制 Cookie 粘贴到对应输入框。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.lg))

                CookieField(
                    label = "快手 Cookie",
                    value = state.kuaishouCookie,
                    onValueChange = { onCookieChange("kuaishou", it) },
                    hint = "kpf=PC_WEB; did=web_xxx; kpn=KUAISHOU_VISION; ..."
                )
                Spacer(modifier = Modifier.height(Spacing.md))

                CookieField(
                    label = "小红书 Cookie",
                    value = state.xiaohongshuCookie,
                    onValueChange = { onCookieChange("xiaohongshu", it) },
                    hint = "a1=xxx; web_session=xxx; ..."
                )
                Spacer(modifier = Modifier.height(Spacing.md))

                CookieField(
                    label = "微博 Cookie",
                    value = state.weiboCookie,
                    onValueChange = { onCookieChange("weibo", it) },
                    hint = "SUB=xxx; SUBP=xxx; ..."
                )
                Spacer(modifier = Modifier.height(Spacing.md))

                CookieField(
                    label = "抖音 Cookie",
                    value = state.douyinCookie,
                    onValueChange = { onCookieChange("douyin", it) },
                    hint = "msToken=xxx; ttwid=xxx; ..."
                )
                Spacer(modifier = Modifier.height(Spacing.md))

                CookieField(
                    label = "B站 Cookie",
                    value = state.bilibiliCookie,
                    onValueChange = { onCookieChange("bilibili", it) },
                    hint = "SESSDATA=xxx; bili_jct=xxx; ..."
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun CookieField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String
) {
    var showValue by remember { mutableStateOf(false) }

    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(hint, style = MaterialTheme.typography.bodySmall) },
            singleLine = false,
            maxLines = 3,
            textStyle = MaterialTheme.typography.bodySmall
        )
    }
}
