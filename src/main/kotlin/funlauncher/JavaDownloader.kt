/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package funlauncher

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipInputStream
import kotlin.io.path.*

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val assets: List<GitHubAsset> = emptyList()
)

@Serializable
private data class GitHubAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String
)

class JavaDownloader {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30000 // 30 секунд
            connectTimeoutMillis = 30000
        }
    }
    private val jdksDir = PathManager.getAppDataDirectory().resolve("jdks")

    private fun log(message: String) = println("[JavaDownloader] $message")

    fun downloadAndUnpack(version: Int, onComplete: (Result<JavaInfo>) -> Unit) {
        ApplicationScope.scope.launch {
            val task = DownloadManager.startTask("Java $version")
            try {
                if (!jdksDir.exists()) jdksDir.createDirectories()

                DownloadManager.updateTask(task.id, 0.05f, "Поиск дистрибутива...")
                val asset = findAsset(version) ?: throw Exception("Не удалось найти подходящий дистрибутив Java $version")
                log("Выбран для скачивания: ${asset.name}")

                val archivePath = jdksDir.resolve(asset.name)
                val tempUnpackDir = jdksDir.resolve("temp_${System.currentTimeMillis()}")
                val destDir = jdksDir.resolve("temurin-$version-jdk")

                try {
                    log("Скачивание с ${asset.browserDownloadUrl} в ${archivePath.absolutePathString()}")
                    downloadFile(asset.browserDownloadUrl, archivePath) { progress, status ->
                        DownloadManager.updateTask(task.id, progress, status)
                    }

                    DownloadManager.updateTask(task.id, 0.9f, "Распаковка...")
                    log("Распаковка ${archivePath.fileName} в ${tempUnpackDir.absolutePathString()}")
                    if (destDir.exists()) destDir.toFile().deleteRecursively()
                    if (tempUnpackDir.exists()) tempUnpackDir.toFile().deleteRecursively()
                    tempUnpackDir.createDirectories()

                    withContext(Dispatchers.IO) {
                        unpack(archivePath, tempUnpackDir)
                    }
                    log("Перемещение из временной папки в ${destDir.absolutePathString()}")
                    withContext(Dispatchers.IO) {
                        moveFromNestedDirectory(tempUnpackDir, destDir)
                    }

                    DownloadManager.updateTask(task.id, 0.99f, "Поиск java...")
                    val javaPath = withContext(Dispatchers.IO) {
                        findJavaIn(destDir)
                    } ?: throw Exception("Не удалось найти java в распакованной папке: $destDir")
                    log("Найден исполняемый файл: ${javaPath.absolutePathString()}")

                    val javaInfo = JavaManager().getJavaInfo(javaPath.toString(), isManaged = true)
                    if (javaInfo != null) {
                        onComplete(Result.success(javaInfo))
                    } else {
                        throw Exception("Не удалось получить информацию о Java после установки.")
                    }
                } finally {
                    withContext(Dispatchers.IO) {
                        archivePath.deleteIfExists()
                        tempUnpackDir.toFile().deleteRecursively()
                    }
                    log("Очистка временных файлов завершена.")
                }
            } catch (e: Exception) {
                onComplete(Result.failure(e))
            } finally {
                DownloadManager.endTask(task.id)
            }
        }
    }

    private suspend fun findAsset(version: Int): GitHubAsset? {
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()

        val repo = "temurin${version}-binaries"
        val url = "https://api.github.com/repos/adoptium/$repo/releases"
        log("Запрос к GitHub API: $url")

        val releases = try {
            client.get(url).body<List<GitHubRelease>>()
        } catch (e: Exception) {
            log("Ошибка при запросе к GitHub API: ${e.message}")
            return null
        }

        val latestRelease = releases.firstOrNull { !it.tagName.contains("ea") }
        if (latestRelease == null) {
            log("Не найден стабильный релиз для Java $version.")
            return null
        }
        log("Найден последний стабильный релиз: ${latestRelease.tagName}")

        val (osName, fileExt, archName) = when {
            os.contains("win") -> Triple("windows", "zip", "x64")
            os.contains("mac") -> Triple("mac", "tar.gz", if (arch == "aarch64") "aarch64" else "x64")
            else -> Triple("linux", "tar.gz", if (arch == "aarch64") "aarch64" else "x64")
        }
        log("Критерии поиска: OS='$osName', Arch='$archName', Ext='$fileExt', Type='jdk', VM='hotspot'")

        val allAssets = latestRelease.assets
        log("Всего ассетов в релизе: ${allAssets.size}. Примеры:\n" + allAssets.take(5).joinToString("\n") { " - ${it.name}" })

        return allAssets.find {
            val name = it.name.lowercase()
            name.contains(osName) &&
            name.contains(archName) &&
            name.endsWith(fileExt) &&
            name.contains("jdk") && // Ищем JDK вместо JRE
            name.contains("hotspot") &&
            !name.contains("alpine") && // Исключаем Alpine
            !name.contains("debugimage") && // Исключаем debug
            !name.contains("testimage") // Исключаем test
        }
    }

    private suspend fun downloadFile(url: String, path: Path, onProgress: (Float, String) -> Unit) {
        client.prepareGet(url).execute { httpResponse ->
            val channel: ByteReadChannel = httpResponse.body()
            val totalBytes = httpResponse.headers["Content-Length"]?.toLongOrNull() ?: 0L
            var bytesRead = 0L

            FileOutputStream(path.toFile()).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = channel.readAvailable(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    bytesRead += read

                    if (totalBytes > 0) {
                        val progress = 0.1f + (bytesRead.toFloat() / totalBytes.toFloat()) * 0.8f
                        onProgress(progress, "Загрузка... ${(bytesRead / 1024 / 1024)} MB")
                    } else {
                        onProgress(0.5f, "Загрузка... ${(bytesRead / 1024 / 1024)} MB")
                    }
                }
            }
        }
    }

    private fun unpack(source: Path, destination: Path) {
        when {
            source.toString().endsWith(".zip") -> unpackZip(source, destination)
            source.toString().endsWith(".tar.gz") -> unpackTarGz(source, destination)
            else -> throw UnsupportedOperationException("Формат архива не поддерживается: $source")
        }
    }

    private fun unpackZip(source: Path, destination: Path) {
        ZipInputStream(Files.newInputStream(source)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val newFile = destination.resolve(entry.name).normalize()
                if (!newFile.startsWith(destination)) throw SecurityException("Invalid zip entry")
                if (entry.isDirectory) {
                    newFile.createDirectories()
                } else {
                    newFile.parent.createDirectories()
                    newFile.outputStream().use { fos -> zis.copyTo(fos) }
                }
                entry = zis.nextEntry
            }
        }
    }

    private fun unpackTarGz(source: Path, destination: Path) {
        TarArchiveInputStream(GzipCompressorInputStream(BufferedInputStream(Files.newInputStream(source)))).use { tais ->
            var entry = tais.nextEntry
            while (entry != null) {
                val newFile = destination.resolve(entry.name).normalize()
                if (!newFile.startsWith(destination)) throw SecurityException("Invalid tar entry")

                when {
                    entry.isDirectory -> newFile.createDirectories()
                    entry.isSymbolicLink -> {
                        newFile.parent.createDirectories()
                        Files.createSymbolicLink(newFile, Paths.get(entry.linkName))
                    }
                    else -> {
                        newFile.parent.createDirectories()
                        newFile.outputStream().use { fos -> tais.copyTo(fos) }
                        if (entry.mode and "111".toInt(8) != 0) {
                            newFile.toFile().setExecutable(true, false)
                        }
                    }
                }
                entry = tais.nextEntry
            }
        }
    }

    private fun moveFromNestedDirectory(sourceDir: Path, destDir: Path) {
        val files = sourceDir.listDirectoryEntries()
        if (files.size == 1 && files.first().isDirectory()) {
            val nestedDir = files.first()
            log("Обнаружена вложенная папка: ${nestedDir.fileName}, перемещаю ее в ${destDir.fileName}")
            nestedDir.moveTo(destDir, overwrite = true)
        } else {
            log("Вложенная папка не найдена, перемещаю содержимое ${sourceDir.fileName} в ${destDir.fileName}")
            sourceDir.moveTo(destDir, overwrite = true)
        }
    }

    private fun findJavaIn(dir: Path): Path? {
        return Files.walk(dir, 5)
            .filter { it.fileName.toString() == "java" || it.fileName.toString() == "java.exe" }
            .findFirst()
            .orElse(null)
    }
}
