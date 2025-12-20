package funlauncher

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.*

@Serializable
data class MinecraftBuild(
    val name: String,
    val version: String,
    val type: String,
    val installPath: String,
    val createdAt: String = java.time.Instant.now().toString(),
    // Индивидуальные настройки сборки
    val javaPath: String? = null,
    val maxRamMb: Int? = null,
    val javaArgs: String? = null,
    val envVars: String? = null
)

class BuildManager {
    private val launcherPath: Path = PathManager.getAppDataDirectory()
    private val instancesPath: Path = launcherPath.resolve("instances")
    private val assetsPath: Path = launcherPath.resolve("assets")
    private val buildsFilePath: Path = launcherPath.resolve("builds.json")

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    init {
        launcherPath.createDirectories()
        instancesPath.createDirectories()
        assetsPath.createDirectories()
    }

    fun loadBuilds(): List<MinecraftBuild> {
        if (!buildsFilePath.exists()) {
            return emptyList()
        }
        return try {
            val content = buildsFilePath.readText()
            json.decodeFromString<List<MinecraftBuild>>(content)
        } catch (e: Exception) {
            println("Ошибка чтения builds.json: ${e.message}")
            emptyList()
        }
    }

    private fun saveBuilds(builds: List<MinecraftBuild>) {
        val content = json.encodeToString(builds)
        buildsFilePath.writeText(content)
    }

    fun addBuild(name: String, version: String, type: String) {
        require(name.isNotBlank()) { "Название сборки не может быть пустым" }
        require(version.isNotBlank()) { "Версия не может быть пустой" }
        require(type.isNotBlank()) { "Тип сборки не может быть пустым" }

        val invalidChars = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
        require(name.none { it in invalidChars }) { "Название сборки содержит запрещенные символы" }

        val builds = loadBuilds().toMutableList()

        if (builds.any { it.name.equals(name, ignoreCase = true) }) {
            throw IllegalStateException("Сборка с именем '$name' уже существует")
        }

        val buildPath = getBuildPath(name)
        val newBuild = MinecraftBuild(
            name = name.trim(),
            version = version,
            type = type,
            installPath = buildPath.toString(),
            javaPath = null,
            maxRamMb = null,
            javaArgs = null,
            envVars = null
        )

        builds.add(newBuild)
        saveBuilds(builds)

        buildPath.createDirectories()
    }

    fun updateBuildSettings(
        buildName: String,
        newJavaPath: String?,
        newMaxRam: Int?,
        newJavaArgs: String?,
        newEnvVars: String?
    ) {
        val builds = loadBuilds().toMutableList()
        val buildIndex = builds.indexOfFirst { it.name.equals(buildName, ignoreCase = true) }
        if (buildIndex != -1) {
            val oldBuild = builds[buildIndex]
            builds[buildIndex] = oldBuild.copy(
                javaPath = newJavaPath,
                maxRamMb = newMaxRam,
                javaArgs = newJavaArgs,
                envVars = newEnvVars
            )
            saveBuilds(builds)
        }
    }

    @OptIn(ExperimentalPathApi::class)
    fun deleteBuild(name: String) {
        val builds = loadBuilds().toMutableList()
        val buildToRemove = builds.firstOrNull { it.name.equals(name, ignoreCase = true) }

        if (buildToRemove != null) {
            builds.remove(buildToRemove)
            saveBuilds(builds)

            val pathToDelete = Path(buildToRemove.installPath)
            if (pathToDelete.exists()) {
                pathToDelete.deleteRecursively()
            }
        }
    }

    fun getBuildPath(buildName: String): Path {
        return instancesPath.resolve(buildName)
    }

    fun getAssetsPath(): Path {
        return assetsPath
    }

    fun getLauncherDataPath(): Path {
        return launcherPath
    }
}
