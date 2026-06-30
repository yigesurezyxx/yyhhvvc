package com.parsehub.app.ui.theme

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/**
 * 主题模式管理：跟随系统 / 浅色 / 深色
 * 用 SharedPreferences 持久化用户选择
 */
object ThemeManager {
    private const val PREFS = "theme_prefs"
    private const val KEY_MODE = "mode"

    enum class Mode { SYSTEM, LIGHT, DARK }

    val currentMode = mutableStateOf(Mode.SYSTEM)

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_MODE, Mode.SYSTEM.name) ?: Mode.SYSTEM.name
        currentMode.value = runCatching { Mode.valueOf(name) }.getOrDefault(Mode.SYSTEM)
    }

    fun setMode(context: Context, mode: Mode) {
        currentMode.value = mode
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode.name)
            .apply()
    }

    /**
     * 循环切换：系统 → 深色 → 浅色 → 系统
     */
    fun toggle(context: Context) {
        val next = when (currentMode.value) {
            Mode.SYSTEM -> Mode.DARK
            Mode.DARK -> Mode.LIGHT
            Mode.LIGHT -> Mode.SYSTEM
        }
        setMode(context, next)
    }
}
