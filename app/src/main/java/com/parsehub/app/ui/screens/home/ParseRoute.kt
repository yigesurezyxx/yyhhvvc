package com.parsehub.app.ui.screens.home

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.parsehub.app.data.ParseHistory
import com.parsehub.app.data.ParseRepository
import com.parsehub.app.data.network.CookieManager
import com.parsehub.app.ui.theme.ThemeManager

/**
 * ParseRoute(spec 3.1:UDF 入口)
 *
 * - 创建/持有 [ParseViewModel](构造注入 repository/history/clipboard/themeToggler)
 * - collect State → 传给 [ParseScreen]
 * - collect UiEffect → Toast / Share / OpenFolder
 * - 下载权限请求 → 通过后 dispatch Download
 *
 * ParseScreen 零业务依赖,所有副作用集中在 Route 层。
 */
@Composable
fun ParseRoute(initialSharedUrl: String? = null) {
    val context = LocalContext.current
    val repo = ParseRepository.getInstance(context)
    val viewModel: ParseViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                ParseViewModel(
                    repository = repo,
                    history = ParseHistory,
                    cookieManager = repo.cookieManager,
                    clipboardProvider = { readClipboard(context) },
                    themeToggler = { ThemeManager.toggle(context) }
                )
            }
        }
    )
    val state by viewModel.uiState.collectAsState()

    val mode = ThemeManager.currentMode.value
    val isDarkTheme = when (mode) {
        ThemeManager.Mode.DARK -> true
        ThemeManager.Mode.LIGHT -> false
        ThemeManager.Mode.SYSTEM -> isSystemInDarkTheme()
    }

    // 从分享菜单进入: 自动填入 URL
    LaunchedEffect(initialSharedUrl) {
        if (!initialSharedUrl.isNullOrBlank()) {
            viewModel.dispatch(ParseIntent.UrlChanged(initialSharedUrl))
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is UiEffect.Toast -> Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                is UiEffect.Snackbar -> Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                is UiEffect.Share -> {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, effect.content)
                    }
                    context.startActivity(Intent.createChooser(intent, "分享").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                is UiEffect.OpenFolder -> Toast.makeText(context, effect.path, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            viewModel.dispatch(ParseIntent.Download)
        }
    }

    val onDownloadClick: () -> Unit = {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    ParseScreen(
        state = state,
        isDarkTheme = isDarkTheme,
        onIntent = viewModel::dispatch,
        onDownloadClick = onDownloadClick
    )
}

/** 读取系统剪贴板文本(Android 10+ 受限,无焦点时返回 null) */
private fun readClipboard(context: Context): String? {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    val clip = cm.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return runCatching { clip.getItemAt(0).coerceToText(context)?.toString() }.getOrNull()
}
