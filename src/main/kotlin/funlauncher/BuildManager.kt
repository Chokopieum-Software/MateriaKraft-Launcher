/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package funlauncher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.*

@Serializable
enum class BuildType {
    VANILLA,
    FABRIC,
    FORGE
}

@Serializable
data class MinecraftBuild(
    val name: String,
    val version: String,
    val type: BuildType,
    val installPath: String,
    val createdAt: String = java.time.Instant.now().toString(),
    val imagePath: String? = null, // Путь к обложке
    // Индивидуальные настройки сборки
    val javaPath: String? = null,
    val maxRamMb: Int? = null,
    val javaArgs: String? = null,
    val envVars: String? = null,
    val modloaderVersion: String? = null
)

@OptIn(ExperimentalPathApi::class)
class BuildManager(private val pathManager: PathManager) {
    private val launcherPath: Path = pathManager.getAppDataDirectory()
    private val instancesPath: Path = launcherPath.resolve("instances")
    private val versionsPath: Path = launcherPath.resolve("versions")
    private val buildsFilePath: Path = launcherPath.resolve("builds.json")
    private val backgroundsPath: Path = launcherPath.resolve("backgrounds")

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private var _builds: MutableList<MinecraftBuild> = mutableListOf()
    private val buildsMutex = Mutex()

    private fun loadBuildsInternal(): MutableList<MinecraftBuild> {
        if (!buildsFilePath.exists()) {
            return mutableListOf()
        }
        return try {
            json.decodeFromString<MutableList<MinecraftBuild>>(buildsFilePath.readText())
        } catch (e: Exception) {
            println("Ошибка чтения builds.json: ${e.message}")
            try {
                val oldFormatBuilds = json.decodeFromString<List<OldMinecraftBuild>>(buildsFilePath.readText())
                val newBuilds = oldFormatBuilds.map { it.toNewBuild() }.toMutableList()
                // Сохраняем мигрированные сборки сразу
                buildsFilePath.writeText(json.encodeToString(newBuilds))
                newBuilds
            } catch (e2: Exception) {
                println("Миграция не удалась: ${e2.message}")
                mutableListOf()
            }
        }
    }

    private fun saveBuildsInternal(buildsToSave: List<MinecraftBuild>) {
        _builds = buildsToSave.toMutableList()
        val content = json.encodeToString(_builds)
        buildsFilePath.writeText(content)
    }

    suspend fun loadBuilds(): List<MinecraftBuild> = withContext(Dispatchers.IO) {
        buildsMutex.withLock {
            _builds = loadBuildsInternal()
        }
        return@withContext _builds.toList()
    }

    private fun prepareDefaultBackgrounds(): List<Path> {
        if (!backgroundsPath.exists()) {
            backgroundsPath.createDirectories()
        }

        val defaultBackgrounds = listOf("1.png", "2.png", "3.png")
        val backgroundPaths = mutableListOf<Path>()

        for (bgName in defaultBackgrounds) {
            val destPath = backgroundsPath.resolve(bgName)
            if (!destPath.exists()) {
                try {
                    val resourceStream: InputStream? = this::class.java.getResourceAsStream("/backgrounds/$bgName")
                    if (resourceStream != null) {
                        destPath.outputStream().use { resourceStream.copyTo(it) }
                    } else {
                        println("Не удалось найти ресурс: /backgrounds/$bgName")
                    }
                } catch (e: Exception) {
                    println("Ошибка копирования фона $bgName: ${e.message}")
                }
            }
            if (destPath.exists()) {
                backgroundPaths.add(destPath)
            }
        }
        return backgroundPaths
    }

    suspend fun synchronizeBuilds(): Pair<List<MinecraftBuild>, Int> = withContext(Dispatchers.IO) {
        var newBuildsCount = 0
        val resultingBuilds = buildsMutex.withLock {
            val currentBuilds = loadBuildsInternal()

            if (!instancesPath.exists() || !instancesPath.isDirectory()) {
                return@withLock currentBuilds
            }

            val defaultBackgrounds = prepareDefaultBackgrounds()
            val existingBuildNames = currentBuilds.map { it.name }.toSet()
            val directoryNames = instancesPath.listDirectoryEntries()
                .filter { it.isDirectory() }
                .map { it.fileName.toString() }

            var changed = false
            for (dirName in directoryNames) {
                if (dirName !in existingBuildNames) {
                    val versionInfo = detectVersionFromInstance(instancesPath.resolve(dirName))
                    val randomBackground = if (defaultBackgrounds.isNotEmpty()) defaultBackgrounds.random().toString() else null
                    val newBuild = MinecraftBuild(
                        name = dirName,
                        version = versionInfo.first,
                        type = versionInfo.second,
                        installPath = instancesPath.resolve(dirName).toString(),
                        imagePath = randomBackground
                    )
                    currentBuilds.add(newBuild)
                    changed = true
                    newBuildsCount++
                    println("Найдена и добавлена новая сборка: $dirName (Версия: ${versionInfo.first}, Тип: ${versionInfo.second})")
                }
            }

            if (changed) {
                saveBuildsInternal(currentBuilds)
            }
            _builds = currentBuilds
            _builds
        }
        return@withContext resultingBuilds.toList() to newBuildsCount
    }

    private fun detectVersionFromInstance(instancePath: Path): Pair<String, BuildType> {
        val versionJsonFile = instancePath.resolve(".minecraft").resolve("version.json")
        if (versionJsonFile.exists() && versionJsonFile.isRegularFile()) {
            try {
                val content = versionJsonFile.readText()
                val jsonElement = Json.parseToJsonElement(content)
                val versionId = jsonElement.jsonObject["id"]?.jsonPrimitive?.content
                if (versionId != null) {
                    val buildType = when {
                        versionId.contains("fabric", ignoreCase = true) -> BuildType.FABRIC
                        versionId.contains("forge", ignoreCase = true) -> BuildType.FORGE
                        else -> BuildType.VANILLA
                    }
                    return versionId to buildType
                }
            } catch (e: Exception) {
                println("Ошибка чтения version.json для ${instancePath.fileName}: ${e.message}")
            }
        }
        return "Unknown" to BuildType.VANILLA
    }

    suspend fun addBuild(name: String, version: String, type: BuildType, imagePath: String?) = withContext(Dispatchers.IO) {
        buildsMutex.withLock {
            val currentBuilds = loadBuildsInternal()

            require(name.isNotBlank()) { "Название сборки не может быть пустым" }
            require(version.isNotBlank()) { "Версия не может быть пустой" }

            val invalidChars = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
            require(name.none { it in invalidChars }) { "Название сборки содержит запрещенные символы" }

            if (currentBuilds.any { it.name.equals(name, ignoreCase = true) }) {
                throw IllegalStateException("Сборка с именем '$name' уже существует")
            }

            val buildPath = getBuildPath(name)
            val newBuild = MinecraftBuild(
                name = name.trim(),
                version = version,
                type = type,
                installPath = buildPath.toString(),
                imagePath = imagePath,
                javaPath = "", // Автоматический выбор по умолчанию
                maxRamMb = null,
                javaArgs = null,
                envVars = null
            )

            currentBuilds.add(newBuild)
            saveBuildsInternal(currentBuilds)

            buildPath.createDirectories()
        }
    }

    suspend fun updateBuildSettings(
        oldName: String,
        newName: String,
        newVersion: String,
        newType: BuildType,
        newImagePath: String?,
        newJavaPath: String?,
        newMaxRam: Int?,
        newJavaArgs: String?,
        newEnvVars: String?
    ) = withContext(Dispatchers.IO) {
        buildsMutex.withLock {
            val currentBuilds = loadBuildsInternal()
            val buildIndex = currentBuilds.indexOfFirst { it.name.equals(oldName, ignoreCase = true) }
            if (buildIndex == -1) return@withLock

            val oldBuild = currentBuilds[buildIndex]

            if (oldName != newName && currentBuilds.any { it.name.equals(newName, ignoreCase = true) }) {
                throw IllegalStateException("Сборка с именем '$newName' уже существует.")
            }

            val newInstallPath = if (oldName != newName) {
                val oldPath = Path(oldBuild.installPath)
                val newPath = instancesPath.resolve(newName.trim())
                if (newPath.exists()) {
                    throw IllegalStateException("Папка для сборки '$newName' уже существует.")
                }
                oldPath.moveTo(newPath, true)
                newPath.toString()
            } else {
                oldBuild.installPath
            }

            if (oldBuild.version != newVersion || oldBuild.type != newType) {
                val versionDir = versionsPath.resolve(oldBuild.version)
                if (versionDir.exists()) {
                    println("Version changed. Deleting old version directory: $versionDir")
                    versionDir.deleteRecursively()
                }
                val vanillaPart = oldBuild.version.split("-fabric-").firstOrNull()
                if (vanillaPart != null) {
                    val vanillaDir = versionsPath.resolve(vanillaPart)
                    if (vanillaDir.exists()) {
                        println("Deleting old vanilla version directory: $vanillaDir")
                        vanillaDir.deleteRecursively()
                    }
                }
            }

            currentBuilds[buildIndex] = oldBuild.copy(
                name = newName.trim(),
                version = newVersion,
                type = newType,
                installPath = newInstallPath,
                imagePath = newImagePath,
                javaPath = newJavaPath,
                maxRamMb = newMaxRam,
                javaArgs = newJavaArgs,
                envVars = newEnvVars
            )
            saveBuildsInternal(currentBuilds)
        }
    }

    suspend fun updateBuildModloaderVersion(buildName: String, modloaderVersion: String) = withContext(Dispatchers.IO) {
        buildsMutex.withLock {
            val currentBuilds = loadBuildsInternal()
            val buildIndex = currentBuilds.indexOfFirst { it.name.equals(buildName, ignoreCase = true) }
            if (buildIndex != -1) {
                val oldBuild = currentBuilds[buildIndex]
                if (oldBuild.modloaderVersion != modloaderVersion) {
                    currentBuilds[buildIndex] = oldBuild.copy(modloaderVersion = modloaderVersion)
                    saveBuildsInternal(currentBuilds)
                }
            }
        }
    }

    suspend fun deleteBuild(name: String) = withContext(Dispatchers.IO) {
        val buildToRemove = buildsMutex.withLock {
            val currentBuilds = loadBuildsInternal()
            val build = currentBuilds.firstOrNull { it.name.equals(name, ignoreCase = true) }
            if (build != null) {
                currentBuilds.remove(build)
                saveBuildsInternal(currentBuilds)
            }
            build
        } ?: return@withContext

        val instancePath = Path(buildToRemove.installPath)
        if (instancePath.exists()) {
            instancePath.deleteRecursively()
            println("Deleted instance directory: $instancePath")
        }

        val versionIdToDelete = buildToRemove.modloaderVersion ?: buildToRemove.version
        val versionDir = versionsPath.resolve(versionIdToDelete)
        if (versionDir.exists()) {
            versionDir.deleteRecursively()
            println("Deleted version directory: $versionDir")
        }

        buildToRemove.imagePath?.let {
            val imageFile = File(it)
            if (imageFile.exists() && imageFile.isFile) {
                imageFile.delete()
                println("Deleted cover image: $it")
            }
        }

        if (buildToRemove.type == BuildType.FABRIC) {
            val gameVersion = buildToRemove.version.split("-fabric-").firstOrNull()
            if (gameVersion != null) {
                val cacheFile = launcherPath.resolve("cache").resolve("fabric_loader_versions_$gameVersion.json")
                if (cacheFile.exists()) {
                    cacheFile.deleteIfExists()
                    println("Deleted fabric loader cache for $gameVersion")
                }
            }
        }
    }

    fun getBuildPath(buildName: String): Path {
        return instancesPath.resolve(buildName.trim())
    }

    fun getLauncherDataPath(): Path {
        return launcherPath
    }
}

// Вспомогательный класс для миграции
@Serializable
private data class OldMinecraftBuild(
    val name: String,
    val version: String,
    val type: String,
    val installPath: String,
    val createdAt: String,
    val imagePath: String? = null,
    val javaPath: String? = null,
    val maxRamMb: Int? = null,
    val javaArgs: String? = null,
    val envVars: String? = null
) {
    fun toNewBuild(): MinecraftBuild {
        val buildType = when (type.uppercase()) {
            "FABRIC" -> BuildType.FABRIC
            "FORGE" -> BuildType.FORGE
            else -> BuildType.VANILLA
        }
        return MinecraftBuild(
            name, version, buildType, installPath, createdAt, imagePath, javaPath, maxRamMb, javaArgs, envVars
        )
    }
}
