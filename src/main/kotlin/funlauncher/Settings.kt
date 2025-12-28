/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package funlauncher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Paths
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

@Serializable
enum class Theme {
    System, Light, Dark
}

@Serializable
enum class NavPanelPosition {
    Left, Bottom
}

@Serializable
data class AppSettings(
    // Глобальные настройки запуска по умолчанию
    val maxRamMb: Int = 2048,
    val javaArgs: String = "",
    val envVars: String = "",
    
    // Остальные настройки
    val javaPath: String = "", // Глобальный путь к Java
    val theme: Theme = Theme.System,
    val showConsoleOnLaunch: Boolean = false,
    val navPanelPosition: NavPanelPosition = NavPanelPosition.Left
)

class SettingsManager {
    private val settingsPath = PathManager.getAppDataDirectory().resolve("settings.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun loadSettings(): AppSettings = withContext(Dispatchers.IO) {
        if (!settingsPath.exists()) return@withContext AppSettings()
        try {
            json.decodeFromString<AppSettings>(settingsPath.readText())
        } catch (e: Exception) {
            e.printStackTrace() // Для отладки
            AppSettings()
        }
    }

    suspend fun saveSettings(settings: AppSettings) = withContext(Dispatchers.IO) {
        try {
            settingsPath.parent.createDirectories()
            settingsPath.writeText(json.encodeToString(settings))
        } catch (e: Exception) {
            e.printStackTrace() // Для отладки
        }
    }
}
