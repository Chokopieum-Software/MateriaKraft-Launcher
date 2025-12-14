import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*

@Serializable
data class MinecraftBuild(
    val name: String,
    val version: String,
    val type: String,
    val installPath: String,
    val createdAt: String = java.time.Instant.now().toString()
)

class BuildManager {
    // Изменяем базовый путь на подпапку .materialkrast
    private val launcherPath: Path = Paths.get(System.getProperty("user.dir")).resolve(".materialkrast")
    private val instancesPath: Path = launcherPath.resolve("instances")
    private val assetsPath: Path = launcherPath.resolve("assets")  // Добавляем путь для assets
    private val buildsFilePath: Path = launcherPath.resolve("builds.json")

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    init {
        // Создаем все необходимые директории при инициализации
        launcherPath.createDirectories()
        instancesPath.createDirectories()
        assetsPath.createDirectories()  // Создаем папку для ассетов
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
            installPath = buildPath.toString()
        )

        builds.add(newBuild)
        saveBuilds(builds)

        buildPath.createDirectories()
    }

    @OptIn(ExperimentalPathApi::class)
    fun deleteBuild(name: String) {
        val builds = loadBuilds().toMutableList()
        val buildToRemove = builds.firstOrNull { it.name.equals(name, ignoreCase = true) }

        if (buildToRemove != null) {
            builds.remove(buildToRemove)
            saveBuilds(builds)

            val pathToDelete = Paths.get(buildToRemove.installPath)
            if (pathToDelete.exists()) {
                pathToDelete.deleteRecursively()
            }
        }
    }

    fun getBuildPath(buildName: String): Path {
        return instancesPath.resolve(buildName)
    }

    // Добавляем геттер для пути к папке assets
    fun getAssetsPath(): Path {
        return assetsPath
    }

    // Добавляем геттер для корневой папки лаунчера
    fun getLauncherDataPath(): Path {
        return launcherPath
    }
}