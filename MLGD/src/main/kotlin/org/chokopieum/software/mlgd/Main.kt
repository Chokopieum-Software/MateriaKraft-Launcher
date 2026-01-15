package org.chokopieum.software.mlgd

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import io.ktor.http.ContentType

// --- Data Models ---

@Serializable
data class StatusResponse(
    val running: Boolean,
    val buildName: String? = null
)

// --- Simple File Logger ---

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

// --- Global State ---

val currentGameProcess = AtomicReference<Process?>(null)
val currentBuildName = AtomicReference<String?>(null)
val shutdownAfterGame = AtomicReference(false)

fun main() {
    DebugLogger.log("==========================================")
    DebugLogger.log("Materia Launcher Game Daemon (MLGD) Starting...")
    DebugLogger.log("Current Directory: ${System.getProperty("user.dir")}")

    try {
        embeddedServer(Netty, port = 25560, host = "127.0.0.1") {
            configureSerialization()
            configureRouting()
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

fun Application.configureRouting() {
    routing {
        get("/ping") {
            DebugLogger.log("Ping requested")
            call.respondText("pong")
        }

        get("/status") {
            val process = currentGameProcess.get()
            val isRunning = process?.isAlive == true

            // Ручная сериализация ответа
            val responseObj = StatusResponse(running = isRunning, buildName = if (isRunning) currentBuildName.get() else null)
            val responseJson = Json.encodeToString(responseObj)
            call.respondText(responseJson, ContentType.Application.Json)
        }

        route("/launch") {
            post {
                DebugLogger.log("Received /launch request")

                try {
                    val jsonText = call.receiveText()
                    DebugLogger.log("Raw JSON received: $jsonText")

                    // Ручной парсинг JSON (безопасно для GraalVM)
                    val launchConfig = Json.decodeFromString<LaunchConfig>(jsonText)

                    DebugLogger.log("Config parsed successfully: ${launchConfig.buildName}")

                    val workDir = File(launchConfig.workingDir)
                    if (!workDir.exists()) throw IllegalArgumentException("Work dir missing: ${workDir.absolutePath}")

                    val javaFile = File(launchConfig.javaPath)
                    if (!javaFile.exists()) throw IllegalArgumentException("Java missing: ${javaFile.absolutePath}")

                    val process = currentGameProcess.get()
                    if (process?.isAlive == true) {
                        val responseObj = StatusResponse(running = true, buildName = currentBuildName.get())
                        val responseJson = Json.encodeToString(responseObj)
                        call.respondText(responseJson, ContentType.Application.Json)
                        return@post
                    }

                    // Вызов GameDaemon (он должен быть во втором файле!)
                    val newProcess = GameDaemon.launchGame(
                        launchConfig = launchConfig,
                        onOutput = { DebugLogger.log("[GAME] $it") },
                        onErrorOutput = { DebugLogger.log("[GAME-ERR] $it") }
                    )

                    currentGameProcess.set(newProcess)
                    currentBuildName.set(launchConfig.buildName)

                    Thread {
                        newProcess.waitFor()
                        DebugLogger.log("Game finished.")
                        currentGameProcess.set(null)
                        currentBuildName.set(null)
                        if (shutdownAfterGame.get()) {
                            DebugLogger.log("Shutdown requested.")
                            System.exit(0)
                        }
                    }.start()

                    val responseObj = StatusResponse(running = true, buildName = currentBuildName.get())
                    val responseJson = Json.encodeToString(responseObj)
                    call.respondText(responseJson, ContentType.Application.Json)

                } catch (e: Throwable) {
                    DebugLogger.error("CRITICAL LAUNCH ERROR", e)
                    call.respondText("Server Error: ${e.message}", status = io.ktor.http.HttpStatusCode.InternalServerError)
                }
            }
        }

        route("/shutdown") {
            post {
                val process = currentGameProcess.get()
                if (process?.isAlive == true) {
                    shutdownAfterGame.set(true)
                    call.respondText("Shutdown scheduled.")
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