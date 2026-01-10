/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package funlauncher

import io.ktor.client.plugins.*
import java.net.ConnectException
import java.net.UnknownHostException
import kotlin.io.path.exists

/**
 * Main installer class that coordinates the launch process.
 * It delegates tasks to VersionMetadataFetcher, FileDownloader, and GameLauncher.
 */
class MinecraftInstaller(private val build: MinecraftBuild, private val buildManager: BuildManager) {

    private val pathManager: PathManager = PathManager(PathManager.getDefaultAppDataDirectory())

    private fun log(message: String) {
        println("[Installer] $message")
    }

    suspend fun launch(
        account: Account, javaPath: String, maxRamMb: Int, javaArgs: String, envVars: String, showConsole: Boolean
    ): Process {
        val task = DownloadManager.startTask("Minecraft ${build.version}")
        try {
            log("Starting launch for ${build.name} (${build.version})")
            log("System: ${System.getProperty("os.name")} ${System.getProperty("os.arch")}, Java: $javaPath")

            // 1. Fetch Version Metadata
            DownloadManager.updateTask(task.id, 0.05f, "Получение метаданных...")
            val metadataFetcher = VersionMetadataFetcher(buildManager, pathManager)
            val versionInfo = metadataFetcher.getVersionInfo(build, task)

            // 2. Download Files
            DownloadManager.updateTask(task.id, 0.1f, "Загрузка файлов...")
            val fileDownloader = FileDownloader(versionInfo, pathManager, buildManager)
            fileDownloader.downloadRequiredFiles(build) { progress, status ->
                DownloadManager.updateTask(task.id, 0.1f + progress * 0.8f, status)
            }

            // 3. Launch Game
            DownloadManager.updateTask(task.id, 0.95f, "Запуск...")
            val gameLauncher = GameLauncher(versionInfo, build, pathManager)
            val process = gameLauncher.launch(account, javaPath, maxRamMb, javaArgs, envVars, showConsole)

            DownloadManager.updateTask(task.id, 1.0f, "Запущено")
            log("Launch process started successfully.")
            return process

        } catch (e: Exception) {
            handleLaunchException(e)
        } finally {
            DownloadManager.endTask(task.id)
        }
    }

    private fun handleLaunchException(e: Exception): Nothing {
        log("Launch failed: ${e.message}")
        e.printStackTrace()
        when (e) {
            is UnknownHostException, is ConnectException, is HttpRequestTimeoutException -> {
                val versionId = build.modloaderVersion ?: build.version
                val jsonFile = pathManager.getGlobalVersionsDir().resolve(versionId).resolve("$versionId.json")
                val message = if (jsonFile.exists()) {
                    "Не удалось скачать некоторые файлы игры. Проверьте подключение к интернету и попробуйте снова."
                } else {
                    "Не удалось получить информацию о версии '$versionId'. Для первого запуска этой версии требуется подключение к интернету."
                }
                throw IllegalStateException(message, e)
            }
            else -> throw e // rethrow other exceptions
        }
    }
}
