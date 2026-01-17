package org.chokopieum.software.mlgd

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.awt.GraphicsEnvironment
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicReference

// ... (StatusResponse, DebugLogger, Global State - без изменений) ...

@Serializable
data class StatusResponse(
    val running: Boolean,
    val buildName: String? = null
)

object DebugLogger {
    private val logFile = File("mlgd_debug.log")
    fun log(message: String) {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME)
        val msg = "[$timestamp] $message"
        println(msg)
        try {
            logFile.appendText("$msg\n")
        } catch (e: Exception) {
            println("FAILED TO WRITE LOG: ${e.message}")
        }
    }
    fun error(message: String, e: Throwable) {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        log("$message\nSTACKTRACE:\n$sw")
    }
}

val currentGameProcess = AtomicReference<Process?>(null)
val currentBuildName = AtomicReference<String?>(null)
val shutdownAfterGame = AtomicReference(false)

fun main() {
    // Проверяем, запускаемся ли мы в "безголовом" режиме (без GUI)
    // Это важно для GraalVM на серверах. Если GUI недоступен, Swing не будет работать.
    val isHeadless = GraphicsEnvironment.isHeadless()
    DebugLogger.log("==========================================")
    DebugLogger.log("Materia Launcher Game Daemon (MLGD) Starting...")
    DebugLogger.log("Headless mode: $isHeadless")
    DebugLogger.log("Current Directory: ${System.getProperty("user.dir")}")

    try {
        embeddedServer(Netty, port = 25560, host = "127.0.0.1") {
            configureSerialization()
            configureRouting(isHeadless) // Передаем флаг в роутинг
        }.start(wait = true)
    } catch (e: Exception) {
        DebugLogger.error("CRITICAL ERROR: Server failed to start", e)
    }
}

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json()
    }
}

fun Application.configureRouting(isHeadless: Boolean) {
    routing {
        // ... (/ping, /status - без изменений) ...
        get("/ping") {
            DebugLogger.log("Ping requested")
            call.respondText("pong")
        }

        get("/status") {
            val process = currentGameProcess.get()
            val isRunning = process?.isAlive == true
            val responseObj = StatusResponse(running = isRunning, buildName = if (isRunning) currentBuildName.get() else null)
            val responseJson = Json.encodeToString(responseObj)
            call.respondText(responseJson, ContentType.Application.Json)
        }

        route("/launch") {
            post {
                DebugLogger.log("Received /launch request")

                try {
                    val launchConfig = call.receive<LaunchConfig>()
                    DebugLogger.log("Config parsed successfully: ${launchConfig.buildName}")

                    // ... (проверки workDir, javaFile, isAlive - без изменений) ...
                    val workDir = File(launchConfig.workingDir)
                    if (!workDir.exists()) throw IllegalArgumentException("Work dir missing: ${workDir.absolutePath}")
                    val javaFile = File(launchConfig.javaPath)
                    if (!javaFile.exists()) throw IllegalArgumentException("Java missing: ${javaFile.absolutePath}")
                    if (currentGameProcess.get()?.isAlive == true) {
                        DebugLogger.log("Launch attempt while game is already running.")
                        call.respond(StatusResponse(running = true, buildName = currentBuildName.get()))
                        return@post
                    }

                    // Настройка логгера в файл
                    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
                    val safeBuildName = launchConfig.buildName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
                    val logFileName = "$safeBuildName-$timestamp.log"
                    val gameLogger = FileLogger(launchConfig.logsPath, logFileName)

                    // Создание окна консоли, если запрошено и возможно
                    val consoleWindow: GameConsoleWindow? = if (launchConfig.showConsole && !isHeadless) {
                        DebugLogger.log("Console window requested. Creating...")
                        GameConsoleWindow(launchConfig.buildName)
                    } else {
                        if (launchConfig.showConsole && isHeadless) {
                            DebugLogger.log("Console window requested, but daemon is in headless mode. Ignoring.")
                        }
                        null
                    }

                    // Запись заголовка в лог-файл
                    gameLogger.log("### Materia Launcher Game Log ###")
                    // ... (запись остальной информации в лог)

                    // Создаем "мультиплексор" для вывода логов
                    val outputMultiplexer: (String) -> Unit = { message ->
                        gameLogger.log(message)
                        consoleWindow?.appendLog(message)
                    }
                    val errorMultiplexer: (String) -> Unit = { message ->
                        gameLogger.log("[ERROR] $message")
                        consoleWindow?.appendLog("[ERROR] $message")
                    }

                    val newProcess = GameDaemon.launchGame(
                        launchConfig = launchConfig,
                        onOutput = outputMultiplexer,
                        onErrorOutput = errorMultiplexer
                    )

                    currentGameProcess.set(newProcess)
                    currentBuildName.set(launchConfig.buildName)

                    Thread {
                        outputMultiplexer("\n--- GAME PROCESS STARTED ---")
                        newProcess.waitFor()
                        outputMultiplexer("--- GAME PROCESS FINISHED (Code: ${newProcess.exitValue()}) ---")
                        
                        consoleWindow?.close() // Закрываем окно консоли
                        
                        DebugLogger.log("Game '${launchConfig.buildName}' finished.")
                        currentGameProcess.set(null)
                        currentBuildName.set(null)
                        if (shutdownAfterGame.get()) {
                            DebugLogger.log("Shutdown requested after game. Shutting down.")
                            System.exit(0)
                        }
                    }.start()

                    call.respond(StatusResponse(running = true, buildName = currentBuildName.get()))

                } catch (e: Throwable) {
                    DebugLogger.error("CRITICAL LAUNCH ERROR", e)
                    call.respond(HttpStatusCode.InternalServerError, "Server Error: ${e.message}")
                }
            }
        }

        // ... (/shutdown - без изменений) ...
        route("/shutdown") {
            post {
                val process = currentGameProcess.get()
                if (process?.isAlive == true) {
                    shutdownAfterGame.set(true)
                    call.respondText("Shutdown scheduled after game ends.")
                } else {
                    call.respondText("Shutting down...")
                    Thread {
                        Thread.sleep(500)
                        System.exit(0)
                    }.start()
                }
            }
        }
    }
}
