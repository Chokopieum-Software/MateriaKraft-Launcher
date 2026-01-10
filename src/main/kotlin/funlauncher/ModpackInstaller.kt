package funlauncher

import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.writeBytes

class ModpackInstaller(
    private val buildManager: BuildManager,
    private val modrinthApi: ModrinthApi,
    private val pathManager: PathManager,
    private val scope: CoroutineScope // Используем внешний scope
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun install(modpackVersion: Version, onComplete: () -> Unit) {
        scope.launch(Dispatchers.IO) { // Запускаем установку в переданном scope
            val modpackFile = modpackVersion.files.firstOrNull { it.filename.endsWith(".mrpack") }
                ?: throw IllegalArgumentException("Выбранная версия не является модпаком (.mrpack).")

            val task = DownloadManager.startTask("Установка модпака: ${modpackVersion.name}")

            try {
                // 1. Скачиваем .mrpack файл
                DownloadManager.updateTask(task.id, 0.05f, "Скачивание архива...")
                val tempMrpackFile = Files.createTempFile("modpack-", ".mrpack").toFile()
                tempMrpackFile.deleteOnExit()
                val modpackData = Network.client.get(modpackFile.url).body<ByteArray>()
                tempMrpackFile.writeBytes(modpackData)

                // 2. Распаковываем и читаем manifest
                DownloadManager.updateTask(task.id, 0.1f, "Чтение манифеста...")
                var manifest: ModrinthIndex? = null
                val overridesDir = Files.createTempDirectory("modpack-overrides-").toFile()
                overridesDir.deleteOnExit()

                ZipInputStream(tempMrpackFile.inputStream()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val entryName = entry.name
                        if (entryName == "modrinth.index.json") {
                            manifest = json.decodeFromString<ModrinthIndex>(zip.bufferedReader().readText())
                        } else if (entryName.startsWith("overrides/")) {
                            val destFile = File(overridesDir, entryName.removePrefix("overrides/"))
                            if (entry.isDirectory) {
                                destFile.mkdirs()
                            } else {
                                destFile.parentFile.mkdirs()
                                destFile.outputStream().use { zip.copyTo(it) }
                            }
                        }
                        entry = zip.nextEntry
                    }
                }

                val modrinthIndex = manifest ?: throw IllegalStateException("modrinth.index.json не найден в архиве.")

                // 3. Создаем новую сборку
                DownloadManager.updateTask(task.id, 0.15f, "Создание сборки...")
                val buildType = when {
                    modrinthIndex.dependencies.quiltLoader != null -> BuildType.QUILT
                    modrinthIndex.dependencies.neoforge != null -> BuildType.NEOFORGE
                    modrinthIndex.dependencies.fabricLoader != null -> BuildType.FABRIC
                    modrinthIndex.dependencies.forge != null -> BuildType.FORGE
                    else -> BuildType.VANILLA
                }
                val mcVersion = modrinthIndex.dependencies.minecraft
                val loaderVersion = modrinthIndex.dependencies.quiltLoader ?: modrinthIndex.dependencies.neoforge ?: modrinthIndex.dependencies.fabricLoader ?: modrinthIndex.dependencies.forge ?: ""
                val buildVersionString = when(buildType) {
                    BuildType.QUILT -> "$mcVersion-quilt-$loaderVersion"
                    BuildType.NEOFORGE -> "$mcVersion-neoforge-$loaderVersion"
                    BuildType.FABRIC -> "$mcVersion-fabric-$loaderVersion"
                    BuildType.FORGE -> "$mcVersion-forge-$loaderVersion"
                    else -> mcVersion
                }

                var buildName = modrinthIndex.name
                var counter = 1
                while (buildManager.loadBuilds().any { it.name.equals(buildName, ignoreCase = true) }) {
                    buildName = "${modrinthIndex.name} ($counter)"
                    counter++
                }

                val project = modrinthApi.getProject(modpackVersion.projectId)
                val bannerGenerator = BannerGenerator(pathManager)
                val bannerPath = project.iconUrl?.let {
                    DownloadManager.updateTask(task.id, 0.18f, "Создание фона...")
                    bannerGenerator.generateBanner(it, buildName)
                }

                buildManager.addBuild(buildName, buildVersionString, buildType, bannerPath)
                val newBuild = buildManager.loadBuilds().first { it.name == buildName }

                // 4. Копируем файлы из overrides
                DownloadManager.updateTask(task.id, 0.2f, "Копирование конфигов...")
                val buildPath = Path.of(newBuild.installPath)
                overridesDir.copyRecursively(buildPath.toFile(), overwrite = true)

                // 5. Скачиваем все моды, ресурспаки и т.д.
                val totalFiles = modrinthIndex.files.size
                coroutineScope {
                    modrinthIndex.files.forEachIndexed { index, file ->
                        launch {
                            val progress = 0.2f + (index.toFloat() / totalFiles) * 0.8f
                            DownloadManager.updateTask(task.id, progress, "Скачивание: ${file.path}")

                            val destination = buildPath.resolve(file.path)
                            destination.parent.createDirectories()

                            try {
                                val fileData = Network.client.get(file.downloads.first()).body<ByteArray>()
                                if (fileData.isNotEmpty()) {
                                    destination.writeBytes(fileData)
                                } else {
                                    println("Warning: Downloaded empty file for ${file.path}")
                                }
                            } catch (e: Exception) {
                                println("Error downloading ${file.path}: ${e.message}")
                            }
                        }
                    }
                }

                DownloadManager.updateTask(task.id, 1.0f, "Установка завершена!")
            } catch (e: Exception) {
                e.printStackTrace()
                DownloadManager.updateTask(task.id, 1.0f, "Ошибка: ${e.message}")
                // Убрана строка "throw e", которая приводила к крэшу
            } finally {
                withContext(Dispatchers.Main) {
                    onComplete() // Вызываем callback в основном потоке
                }
            }
        }
    }
}
