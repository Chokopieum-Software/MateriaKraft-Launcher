/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import funlauncher.Theme

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF64B5F6), // Синий
    secondary = Color(0xFF81C784), // Зеленый
    tertiary = Color(0xFFFFF176), // Желтый
    background = Color(0xFF121212), // Очень темный фон
    surface = Color(0xFF1E1E1E), // Поверхности чуть светлее фона
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1976D2), // Синий
    secondary = Color(0xFF388E3C), // Зеленый
    tertiary = Color(0xFFFBC02D), // Желтый
    background = Color(0xFFFFFFFF), // Белый фон
    surface = Color(0xFFFFFFFF), // Белые поверхности
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = Color.Black,
    onSurface = Color.Black,
)

@Composable
fun AppTheme(
    theme: Theme,
    content: @Composable () -> Unit
) {
    val colorScheme = when (theme) {
        Theme.System -> LightColorScheme
        Theme.Light -> LightColorScheme
        Theme.Dark -> DarkColorScheme
        Theme.Day -> LightColorScheme
        Theme.Amoled -> DarkColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
