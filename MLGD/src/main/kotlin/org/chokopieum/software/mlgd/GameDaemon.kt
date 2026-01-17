package org.chokopieum.software.mlgd

import kotlinx.serialization.Serializable
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Простой логгер в файл для игровых сессий.
 * Создает файл в указанной директории.
 */
class FileLogger(logDir: String, logFileName: String) {
    private val logFile: File

    init {
        val directory = File(logDir)
        if (!directory.exists()) {
            directory.mkdirs()
        }
        logFile = File(directory, logFileName)
    }

    /**
     * Записывает строку в лог-файл.
     * @param message Сообщение для записи.
     */
    fun log(message: String) {
        try {
            logFile.appendText("$message\n", StandardCharsets.UTF_8)
        } catch (e: Exception) {
            // Если не удалось записать в лог игры, пишем ошибку в главный лог демона
            DebugLogger.error("Failed to write to game log ${logFile.name}", e)
        }
    }
}

/**
 * Объект-демон для управления игровым процессом.
 */
object GameDaemon {

    fun launchGame(
        launchConfig: LaunchConfig,
        onOutput: (String) -> Unit,
        onErrorOutput: (String) -> Unit
    ): Process {
        val command = buildList {
            add(launchConfig.javaPath)
            addAll(launchConfig.jvmArgs)

            if (launchConfig.gameJarPath.endsWith(".jar")) {
                add("-jar")
            }

            add(launchConfig.gameJarPath)
            addAll(launchConfig.gameArgs)
        }

        val processBuilder = ProcessBuilder(command).apply {
            directory(File(launchConfig.workingDir))
            environment().putAll(launchConfig.envVars)
        }

        val process = processBuilder.start()

        Thread {
            process.inputStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                lines.forEach(onOutput)
            }
        }.start()

        Thread {
            process.errorStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                lines.forEach(onErrorOutput)
            }
        }.start()

        return process
    }
}

/**
 * Конфигурация запуска (DTO).
 */
@Serializable
data class LaunchConfig(
    val buildName: String,
    val javaPath: String,
    val jvmArgs: List<String> = emptyList(),
    val gameJarPath: String,
    val gameArgs: List<String> = emptyList(),
    val workingDir: String,
    val logsPath: String,
    val showConsole: Boolean = false, // Новый параметр для отображения консоли
    val envVars: Map<String, String> = emptyMap()
)
