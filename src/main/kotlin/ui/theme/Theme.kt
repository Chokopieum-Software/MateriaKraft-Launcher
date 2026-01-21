package ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import funlauncher.Theme

/**
 * Компонент, который применяет выбранную цветовую схему с анимацией.
 * @param theme Выбранная тема оформления.
 * @param content Содержимое, к которому применяется тема.
 */
@Composable
fun AnimatedAppTheme(
    theme: Theme,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val colors = when (theme) {
        Theme.System -> if (isSystemDark) darkColorScheme() else lightColorScheme()
        Theme.Light -> lightColorScheme()
        Theme.Dark -> darkColorScheme()
        Theme.Day -> dayColorScheme
        Theme.Amoled -> amoledColorScheme
    }

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
