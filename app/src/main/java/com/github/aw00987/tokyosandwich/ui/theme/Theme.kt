package com.github.aw00987.tokyosandwich.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = WaterBlue80,
    secondary = WaterBlueGrey80,
    tertiary = Teal80,
)

private val LightColorScheme = lightColorScheme(
    primary = WaterBlue40,
    secondary = WaterBlueGrey40,
    tertiary = Teal40,
)

/** Material 3 theme; uses the wallpaper-based dynamic palette (always available on minSdk 34). */
@Composable
fun TokyoSandwichTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor -> dynamicLightColorScheme(context)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
