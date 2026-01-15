package org.chokopieum.software.mlgd

import kotlinx.serialization.Serializable
import java.io.File
import java.nio.charset.StandardCharsets

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

            // ИСПРАВЛЕНИЕ: Добавляем -jar только если это файл .jar
            // Если это Fabric/Forge (класс), флаг -jar не нужен
            if (launchConfig.gameJarPath.endsWith(".jar")) {
                add("-jar")
            }

            add(launchConfig.gameJarPath)
            addAll(launchConfig.gameArgs)
        }

        // DebugLogger.log("Cmd: $command") // Можно раскомментировать для отладки

        val processBuilder = ProcessBuilder(command).apply {
            directory(File(launchConfig.workingDir))
            // Добавляем переменные окружения
            environment().putAll(launchConfig.envVars)
        }

        val process = processBuilder.start()

        // Используем UTF-8 для чтения логов
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
 * Main.kt ругается, потому что не видит этот класс.
 */
@Serializable
data class LaunchConfig(
    val buildName: String,
    val javaPath: String,
    val jvmArgs: List<String> = emptyList(),
    val gameJarPath: String,
    val gameArgs: List<String> = emptyList(),
    val workingDir: String,
    val envVars: Map<String, String> = emptyMap()
)
