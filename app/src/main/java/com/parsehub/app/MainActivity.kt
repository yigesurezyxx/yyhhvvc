package com.parsehub.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.parsehub.app.ui.theme.ParseHubTheme
import com.parsehub.app.ui.theme.ThemeManager
import com.parsehub.app.ui.screens.ParseScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.init(this)

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
                    ParseScreen()
                }
            }
        }
    }
}
