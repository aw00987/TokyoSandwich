package com.github.aw00987.tokyosandwich.ui

import android.app.UiModeManager
import android.content.Context

/**
 * Light or dark: the system's, or fixed for the app. The system keeps the choice (per-app night mode,
 * `UiModeManager.setApplicationNightMode`), like [AppLanguage], so it is not in `settings.json`; the configuration's
 * `uiMode` then says dark or not, so `isSystemInDarkTheme()` and the `values-night` window theme follow it.
 */
enum class AppTheme(val mode: Int) {
    System(UiModeManager.MODE_NIGHT_AUTO),
    Light(UiModeManager.MODE_NIGHT_NO),
    Dark(UiModeManager.MODE_NIGHT_YES),
}

/** The theme chosen for the app; [AppTheme.System] when none is. */
fun currentAppTheme(context: Context): AppTheme {
    val mode = context.getSystemService(UiModeManager::class.java).nightMode
    return AppTheme.entries.firstOrNull { it.mode == mode } ?: AppTheme.System
}

/** Sets the app's theme; the system then applies it to the configuration and recreates the activity. */
fun setAppTheme(context: Context, theme: AppTheme) {
    context.getSystemService(UiModeManager::class.java).setApplicationNightMode(theme.mode)
}
