package com.github.aw00987.tokyosandwich.ui

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList

/**
 * The app's language: the system's, or one of the three the app is translated into. The system keeps the choice
 * (per-app language, `LocaleManager`), so it is the same one as in the system settings and is not in `settings.json`.
 * [label] is the language's own name, the same in every language; [System] is worded by a string resource.
 */
enum class AppLanguage(val tag: String?, val label: String?) {
    System(null, null),
    Chinese("zh", "中文"),
    Japanese("ja", "日本語"),
    English("en", "English"),
}

/** The language chosen for the app; [AppLanguage.System] when none is, or the chosen one is not one of ours. */
fun currentAppLanguage(context: Context): AppLanguage {
    val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
    if (locales.isEmpty) return AppLanguage.System
    val language = locales[0].language
    return AppLanguage.entries.firstOrNull { it.tag == language } ?: AppLanguage.System
}

/** Sets the app's language; the system then recreates the activity. */
fun setAppLanguage(context: Context, language: AppLanguage) {
    context.getSystemService(LocaleManager::class.java).applicationLocales =
        language.tag?.let { LocaleList.forLanguageTags(it) } ?: LocaleList.getEmptyLocaleList()
}
