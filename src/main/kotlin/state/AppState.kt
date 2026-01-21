package state

import funlauncher.AppSettings
import funlauncher.MinecraftBuild
import funlauncher.auth.Account

// Запечатанный класс для представления различных экранов приложения.
sealed class Screen {
    object Splash : Screen() // Экран-заставка
    object FirstRunWizard : Screen() // Мастер первоначальной настройки
    data class MainApp(val state: AppState) : Screen() // Основной экран приложения
}

// Класс данных, хранящий полное состояние приложения.
data class AppState(
    val settings: AppSettings,
    val builds: List<MinecraftBuild>,
    val accounts: List<Account>
)
