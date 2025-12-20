package funlauncher

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

@Serializable
enum class Theme {
    System, Light, Dark
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
    val showConsoleOnLaunch: Boolean = false
)

class SettingsManager {
    private val settingsPath = Paths.get("settings.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun loadSettings(): AppSettings {
        if (!settingsPath.exists()) return AppSettings()
        return try {
            json.decodeFromString<AppSettings>(settingsPath.readText())
        } catch (_: Exception) {
            AppSettings()
        }
    }

    fun saveSettings(settings: AppSettings) {
        settingsPath.writeText(json.encodeToString(settings))
    }
}
