package ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Цветовая схема "Day" (Светлая, с акцентными цветами).
val dayColorScheme = lightColorScheme(
    primary = Color(0xFFC06C84),
    secondary = Color(0xFF6C5B7B),
    tertiary = Color(0xFF355C7D),
    background = Color(0xFFF8F3F1),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF333333),
    onSurface = Color(0xFF333333),
)

// Цветовая схема "Amoled" (Темная, с черным фоном для AMOLED-экранов).
val amoledColorScheme = darkColorScheme(
    primary = Color(0xFFC06C84),
    secondary = Color(0xFF6C5B7B),
    tertiary = Color(0xFF355C7D),
    background = Color.Black,
    surface = Color(0xFF1A1A1A),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFFE0E0E0),
    onSurface = Color(0xFFE0E0E0),
)
