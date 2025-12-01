import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*

// Используем kotlinx.serialization, аналог System.Text.Json
@Serializable
data class MinecraftBuild(
    val name: String,
    val version: String,
    val type: String,
    val installPath: String,
    // Дату лучше хранить в стандартном формате ISO-8601
    val createdAt: String = java.time.Instant.now().toString()
)

class BuildManager {

    private val launcherPath: Path
    private val instancesPath: Path
    private val buildsFilePath: Path

    // Создаем экземпляр JSON-сериализатора с красивым форматированием
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    init {
        // Получаем путь к папке, где запущен лаунчер
        launcherPath = Paths.get(System.getProperty("user.dir"))
        instancesPath = launcherPath.resolve("instances")
        buildsFilePath = launcherPath.resolve("builds.json")

        // Создаем директории, если их нет (аналог Directory.CreateDirectory)
        instancesPath.createDirectories()
    }

    /**
     * Загружает список сборок из файла builds.json
     */
    fun loadBuilds(): List<MinecraftBuild> {
        if (!buildsFilePath.exists()) {
            return emptyList()
        }
        return try {
            val content = buildsFilePath.readText()
            json.decodeFromString<List<MinecraftBuild>>(content)
        } catch (e: Exception) {
            // Если файл поврежден или пуст, возвращаем пустой список
            println("Ошибка чтения builds.json: ${e.message}")
            emptyList()
        }
    }

    /**
     * Сохраняет список сборок в файл builds.json
     */
    private fun saveBuilds(builds: List<MinecraftBuild>) {
        val content = json.encodeToString(builds)
        buildsFilePath.writeText(content)
    }

    /**
     * Добавляет новую сборку, создает для нее папку и сохраняет изменения.
     */
    fun addBuild(name: String, version: String, type: String) {
        // Проверка входных данных (require - идиоматичный способ в Kotlin)
        require(name.isNotBlank()) { "Название сборки не может быть пустым" }
        require(version.isNotBlank()) { "Версия не может быть пустой" }
        require(type.isNotBlank()) { "Тип сборки не может быть пустым" }

        // Проверка на запрещенные символы в имени. Простая, но эффективная.
        val invalidChars = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
        require(name.none { it in invalidChars }) { "Название сборки содержит запрещенные символы" }

        val builds = loadBuilds().toMutableList()

        // Проверка на существование сборки (сравнение без учета регистра)
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

        // Создаем папку для новой сборки
        buildPath.createDirectories()
    }

    /**
     * Удаляет сборку, ее папку и сохраняет изменения.
     */
    @OptIn(ExperimentalPathApi::class)
    fun deleteBuild(name: String) {
        val builds = loadBuilds().toMutableList()
        val buildToRemove = builds.firstOrNull { it.name.equals(name, ignoreCase = true) }

        if (buildToRemove != null) {
            builds.remove(buildToRemove)
            saveBuilds(builds)

            // Удаляем папку сборки рекурсивно
            val pathToDelete = Paths.get(buildToRemove.installPath)
            if (pathToDelete.exists()) {
                // deleteRecursively - удобная Kotlin-функция
                pathToDelete.deleteRecursively()
            }
        }
    }

    /**
     * Возвращает полный путь к папке сборки.
     */
    fun getBuildPath(buildName: String): Path {
        return instancesPath.resolve(buildName)
    }
}