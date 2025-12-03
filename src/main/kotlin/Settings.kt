import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

@Serializable
data class AppSettings(
    val maxRamMb: Int = 2048, // ОЗУ
    val javaArgs: String = "", // Например: -XX:+UseG1GC
    val envVars: String = ""   // Например: MESA_GL_VERSION_OVERRIDE=3.3
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