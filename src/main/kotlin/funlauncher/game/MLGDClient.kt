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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.lang.management.ManagementFactory

@Serializable
data class LaunchPayload(
    val command: String,
    val arguments: List<String>,
    val environment: Map<String, String>
)

object MLGDClient {
    private const val DAEMON_PORT = 8080
    private const val DAEMON_HOST = "127.0.0.1"
    private const val DAEMON_URL = "http://$DAEMON_HOST:$DAEMON_PORT"

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
        engine {
            requestTimeout = 5000 // 5 seconds
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

    suspend fun getStatus(): String {
        return try {
            client.get("$DAEMON_URL/status").body()
        } catch (e: Exception) {
            "STOPPED"
        }
    }

    suspend fun launch(payload: LaunchPayload) {
        client.post("$DAEMON_URL/launch") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
    }

    suspend fun stop() {
        try {
            client.post("$DAEMON_URL/stop")
        } catch (e: Exception) {
            // Daemon or game might already be stopped
        }
    }
    
    suspend fun shutdown() {
        // The new daemon shuts down automatically.
    }

    private fun findDaemonExecutable(pathManager: PathManager): File? {
        val os = System.getProperty("os.name").lowercase()
        val daemonFileName = if (os.contains("win")) "mlgd.exe" else "mlgd"
        val launcherDir = pathManager.getLauncherDir()

        // 1. Search next to the launcher executable
        val alongsideExecutable = File(launcherDir, daemonFileName)
        if (alongsideExecutable.exists()) {
            println("Found MLGD executable alongside launcher: ${alongsideExecutable.absolutePath}")
            return alongsideExecutable
        }

        // 2. Search in 'app' subdirectory (for packaged apps)
        val inAppDir = File(launcherDir, "app/$daemonFileName")
        if (inAppDir.exists()) {
            println("Found MLGD executable in app directory: ${inAppDir.absolutePath}")
            return inAppDir
        }
        
        // 3. Search in development path (relative to the project root)
        val devPath = launcherDir.resolve("../MLGD/build/native/nativeCompile/$daemonFileName")
        if (devPath.exists()) {
            println("Found MLGD executable in development path: ${devPath.normalize().absolutePath}")
            return devPath.normalize()
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
        
        val launcherPid = ManagementFactory.getRuntimeMXBean().name.split("@")[0]

        withContext(Dispatchers.IO) {
            val logFile = pathManager.getAppDataDirectory().resolve("mlgd-stdout.log").toFile()
            val errorFile = pathManager.getAppDataDirectory().resolve("mlgd-stderr.log").toFile()

            println("Redirecting daemon output to:")
            println("STDOUT: ${logFile.absolutePath}")
            println("STDERR: ${errorFile.absolutePath}")

            ProcessBuilder(daemonFile.absolutePath, launcherPid)
                .directory(daemonFile.parentFile)
                .redirectOutput(logFile)
                .redirectError(errorFile)
                .start()
        }

        // Wait a bit for the daemon to start
        for (i in 1..15) {
            delay(500)
            if (ping()) {
                println("MLGD started successfully.")
                return
            }
        }

        throw RuntimeException("Failed to start or connect to MLGD. Check mlgd-stderr.log for details.")
    }
}
