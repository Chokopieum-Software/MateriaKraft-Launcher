/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package funlauncher.managers

import funlauncher.MinecraftBuild
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class PathManager(private val rootDir: Path) {

    /**
     * Возвращает корневую папку для всех данных лаунчера.
     */
    fun getAppDataDirectory(): Path = rootDir

    /**
     * Возвращает путь к глобальной папке с ассетами.
     */
    fun getGlobalAssetsDir(): Path = getAppDataDirectory().resolve("assets")

    /**
     * Возвращает путь к глобальной папке с версиями.
     */
    fun getGlobalVersionsDir(): Path = getAppDataDirectory().resolve("versions")

    /**
     * Возвращает путь к глобальной папке с библиотеками.
     */
    fun getGlobalLibrariesDir(): Path = getAppDataDirectory().resolve("libraries")
    
    /**
     * Возвращает путь к папке для кэширования манифестов версий.
     */
    fun getCacheDir(): Path = getAppDataDirectory().resolve(".cache")

    /**
     * Возвращает путь к папке с нативными библиотеками для конкретной сборки.
     */
    fun getNativesDir(build: MinecraftBuild): Path = Paths.get(build.installPath).resolve("natives")

    /**
     * Возвращает путь к папке с фонами.
     */
    fun getBackgroundsDir(): Path = getAppDataDirectory().resolve("backgrounds")


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
        Files.createDirectories(getAppDataDirectory())
        Files.createDirectories(getGlobalVersionsDir())
        Files.createDirectories(getGlobalLibrariesDir())
        Files.createDirectories(getGlobalAssetsDir())
        Files.createDirectories(getCacheDir())
        Files.createDirectories(getAppDataDirectory().resolve("jdks"))
        Files.createDirectories(getBackgroundsDir())
    }

    companion object {
        /**
         * Возвращает путь по умолчанию для данных лаунчера.
         * - Windows: %APPDATA%/.MateriaLauncher
         * - Linux/macOS: ~/.MateriaLauncher
         */
        fun getDefaultAppDataDirectory(): Path {
            val osName = System.getProperty("os.name").lowercase()
            val userHome = System.getProperty("user.home")
            val launcherDirName = ".MateriaLauncher"

            return when {
                osName.contains("win") -> {
                    val appData = System.getenv("APPDATA")
                    val basePath = if (!appData.isNullOrBlank()) Paths.get(appData) else Paths.get(userHome)
                    basePath.resolve(launcherDirName)
                }
                else -> {
                    Paths.get(userHome, launcherDirName)
                }
            }
        }
    }
}
