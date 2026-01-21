package funlauncher.game

import funlauncher.managers.PathManager
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.chokopieum.software.mlgd.LaunchConfig
import org.chokopieum.software.mlgd.StatusResponse
import java.io.File

object MLGDClient {
    private const val DAEMON_PORT = 25560
    private const val DAEMON_HOST = "127.0.0.1"
    private const val DAEMON_URL = "http://$DAEMON_HOST:$DAEMON_PORT"

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
        engine {
            requestTimeout = 5000
        }
    }

    suspend fun ping(): Boolean {
        return try {
            val response: String = client.get("$DAEMON_URL/ping").body()
            response == "pong"
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getStatus(): StatusResponse {
        return try {
            client.get("$DAEMON_URL/status").body()
        } catch (e: Exception) {
            StatusResponse(running = false)
        }
    }

    suspend fun launch(launchConfig: LaunchConfig): StatusResponse {
        return client.post("$DAEMON_URL/launch") {
            contentType(ContentType.Application.Json)
            setBody(launchConfig)
        }.body()
    }

    suspend fun shutdown() {
        try {
            client.post("$DAEMON_URL/shutdown")
        } catch (e: Exception) {
            // Демон может уже быть выключен, это нормально
        }
    }

    private fun findDaemonExecutable(pathManager: PathManager): File? {
        val os = System.getProperty("os.name").lowercase()
        val daemonFileName = if (os.contains("win")) "mlgd.exe" else "mlgd"
        val launcherDir = pathManager.getLauncherDir()

        // 1. Искать рядом с JAR'ом
        val alongsideJar = File(launcherDir, daemonFileName)
        if (alongsideJar.exists()) {
            println("Found MLGD executable alongside launcher: ${alongsideJar.absolutePath}")
            return alongsideJar
        }

        // 2. Искать в папке 'app'
        val inAppDir = File(launcherDir, "app/$daemonFileName")
        if (inAppDir.exists()) {
            println("Found MLGD executable in app directory: ${inAppDir.absolutePath}")
            return inAppDir
        }

        // 3. Искать в пути для разработки
        val devPath = launcherDir.resolve("MLGD/build/native/nativeCompile/$daemonFileName")
        if (devPath.exists()) {
            println("Found MLGD executable in development path: ${devPath.absolutePath}")
            return devPath
        }

        return null
    }

    suspend fun ensureDaemonRunning(pathManager: PathManager) {
        if (ping()) {
            println("MLGD is already running.")
            return
        }

        println("MLGD not found, starting it...")

        val daemonFile = findDaemonExecutable(pathManager)
            ?: throw IllegalStateException("MLGD executable not found. Searched in launcher directory, app/ subdirectory, and common development paths.")

        val os = System.getProperty("os.name").lowercase()
        if (!os.contains("win") && !daemonFile.canExecute()) {
            println("Making MLGD executable...")
            daemonFile.setExecutable(true)
        }

        withContext(Dispatchers.IO) {
            val logFile = pathManager.getAppDataDirectory().resolve("mlgd-stdout.log").toFile()
            val errorFile = pathManager.getAppDataDirectory().resolve("mlgd-stderr.log").toFile()

            println("Redirecting daemon output to:")
            println("STDOUT: ${logFile.absolutePath}")
            println("STDERR: ${errorFile.absolutePath}")

            ProcessBuilder(daemonFile.absolutePath)
                .directory(daemonFile.parentFile) // Запускаем из папки, где находится исполняемый файл
                .redirectOutput(logFile)
                .redirectError(errorFile)
                .start()
        }

        // Подождем немного, чтобы демон успел запуститься
        for (i in 1..15) {
            kotlinx.coroutines.delay(500)
            if (ping()) {
                println("MLGD started successfully.")
                return
            }
        }

        throw RuntimeException("Failed to start or connect to MLGD. Check mlgd-stderr.log for details.")
    }
}
