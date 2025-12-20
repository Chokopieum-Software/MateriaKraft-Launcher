package funlauncher

import java.nio.file.Path
import java.nio.file.Paths

object PathManager {

    /**
     * Возвращает корневую папку для всех данных лаунчера.
     * - Windows: %APPDATA%/.MateriaLauncher
     * - Linux/macOS: ~/.MateriaLauncher
     */
    fun getAppDataDirectory(): Path {
        val osName = System.getProperty("os.name").lowercase()
        val userHome = System.getProperty("user.home")
        val launcherDirName = ".MateriaLauncher"

        return when {
            // Для Windows используем AppData/Roaming
            osName.contains("win") -> {
                val appData = System.getenv("APPDATA")
                val basePath = if (!appData.isNullOrBlank()) Paths.get(appData) else Paths.get(userHome)
                basePath.resolve(launcherDirName)
            }
            // Для Linux, macOS и других систем используем домашнюю директорию
            else -> {
                Paths.get(userHome, launcherDirName)
            }
        }
    }
}
