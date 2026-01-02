/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package funlauncher

import java.nio.file.Files
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

    /**
     * Проверяет, нужно ли запускать мастер первоначальной настройки.
     * Это определяется по наличию корневой папки лаунчера.
     */
    fun isFirstRunRequired(): Boolean {
        return !Files.exists(getAppDataDirectory())
    }

    /**
     * Создает все необходимые директории для работы лаунчера.
     */
    fun createRequiredDirectories() {
        val appData = getAppDataDirectory()
        Files.createDirectories(appData)
        Files.createDirectories(appData.resolve("versions"))
        Files.createDirectories(appData.resolve("jdks"))
        Files.createDirectories(appData.resolve("accounts"))
        // Можно добавить другие необходимые папки здесь
    }
}
