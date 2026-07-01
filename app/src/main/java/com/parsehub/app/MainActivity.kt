package com.parsehub.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.parsehub.app.data.ParseHistory
import com.parsehub.app.ui.screens.home.ParseRoute
import com.parsehub.app.ui.theme.ParseHubTheme
import com.parsehub.app.ui.theme.ThemeManager

class MainActivity : ComponentActivity() {

    /** 从其他 app 分享接收到的链接,ParseRoute 读取后置空 */
    @Volatile
    private var sharedUrl: String? = null

    /** Android 13+ 通知权限请求(下载完成通知需要) */
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> /* 用户拒绝也不影响下载,只是没有通知 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.init(this)
        ParseHistory.init(this)

        // 处理从分享菜单或外部点击链接进入的 Intent
        handleIncomingIntent(intent)

        // Android 13+(澎湃OS基于Android 14)需要请求通知权限
        requestNotificationPermissionIfNeeded()

        setContent {
            val mode = ThemeManager.currentMode.value
            val darkTheme = when (mode) {
                ThemeManager.Mode.SYSTEM -> isSystemInDarkTheme()
                ThemeManager.Mode.DARK -> true
                ThemeManager.Mode.LIGHT -> false
            }
            ParseHubTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ParseRoute(initialSharedUrl = sharedUrl)
                }
            }
        }
    }

    /** 新 Intent(单顶模式,从分享再次进入时触发) */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    /** 从 SEND intent 提取分享的文本,正则提取 URL */
    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!text.isNullOrBlank()) {
                // 简单提取 URL(分享文本通常是"标题 + 链接")
                val url = extractUrl(text)
                if (url != null) {
                    sharedUrl = url
                }
            }
        }
    }

    private fun extractUrl(text: String): String? {
        val pattern = Regex("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+", RegexOption.IGNORE_CASE)
        val match = pattern.find(text) ?: return null
        return match.value.trimEnd(',', ')', ';', '.', '!', '?', ':', '\'')
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
