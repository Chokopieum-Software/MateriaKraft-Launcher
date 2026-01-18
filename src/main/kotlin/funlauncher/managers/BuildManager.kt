/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package funlauncher.managers

import funlauncher.BuildType
import funlauncher.MinecraftBuild
import funlauncher.database.dao.BuildDao
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

@OptIn(ExperimentalPathApi::class)
class BuildManager(private val pathManager: PathManager) {
    private val launcherPath: Path = pathManager.getAppDataDirectory()
    private val instancesPath: Path = launcherPath.resolve("instances")
    private val versionsPath: Path = launcherPath.resolve("versions")
    private val backgroundsPath: Path = launcherPath.resolve("backgrounds")
    private val buildDao = BuildDao()
    private val buildsMutex = Mutex()

    init {
        migrateFromJson()
    }

    private fun migrateFromJson() {
        val buildsFilePath = launcherPath.resolve("builds.json")
        if (buildsFilePath.exists()) {
            println("--- Запуск миграции сборок из builds.json ---")
            val content = buildsFilePath.readText()
            if (content.isNotBlank()) {
                val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
                try {
                    val jsonBuilds = json.decodeFromString<List<MinecraftBuild>>(content)
                    jsonBuilds.forEach { build ->
                        if (!buildDao.exists(build.name)) {
                            buildDao.add(build)
                            println("Мигрирована сборка: ${build.name}")
                        }
                    }
                } catch (e: Exception) {
                    println("Ошибка десериализации builds.json во время миграции: ${e.message}")
                    try {
                        val oldFormatBuilds = json.decodeFromString<List<OldMinecraftBuild>>(content)
                        val newBuilds = oldFormatBuilds.map { it.toNewBuild() }
                        newBuilds.forEach { build ->
                            if (!buildDao.exists(build.name)) {
                                buildDao.add(build)
                                println("Мигрирована сборка (старый формат): ${build.name}")
                            }
                        }
                    } catch (e2: Exception) {
                        println("Миграция из старого формата не удалась: ${e2.message}")
                    }
                }
            }
            buildsFilePath.moveTo(launcherPath.resolve("builds.json.migrated"), true)
            println("--- Миграция сборок завершена ---")
        }
    }

    suspend fun loadBuilds(): List<MinecraftBuild> = withContext(Dispatchers.IO) {
        buildsMutex.withLock {
            return@withContext buildDao.getAll()
        }
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
            val currentBuilds = buildDao.getAll().toMutableList()

            if (!instancesPath.exists() || !instancesPath.isDirectory()) {
                return@withLock currentBuilds
            }

            val defaultBackgrounds = prepareDefaultBackgrounds()
            val existingBuildNames = currentBuilds.map { it.name }.toSet()
            val directoryNames = instancesPath.listDirectoryEntries()
                .filter { it.isDirectory() }
                .map { it.fileName.toString() }

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
                    buildDao.add(newBuild)
                    currentBuilds.add(newBuild)
                    newBuildsCount++
                    println("Найдена и добавлена новая сборка: $dirName (Версия: ${versionInfo.first}, Тип: ${versionInfo.second})")
                }
            }
            currentBuilds
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
                        versionId.contains("quilt", ignoreCase = true) -> BuildType.QUILT
                        versionId.contains("neoforge", ignoreCase = true) -> BuildType.NEOFORGE
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
            require(name.isNotBlank()) { "Название сборки не может быть пустым" }
            require(version.isNotBlank()) { "Версия не может быть пустой" }

            val invalidChars = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
            require(name.none { it in invalidChars }) { "Название сборки содержит запрещенные символы" }

            if (buildDao.exists(name)) {
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

            buildDao.add(newBuild)
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
            val buildToUpdate = buildDao.getAll().find { it.name.equals(oldName, ignoreCase = true) } ?: return@withLock

            if (oldName != newName && buildDao.exists(newName)) {
                throw IllegalStateException("Сборка с именем '$newName' уже существует.")
            }

            val newInstallPath = if (oldName != newName) {
                val oldPath = Path(buildToUpdate.installPath)
                val newPath = instancesPath.resolve(newName.trim())
                if (newPath.exists()) {
                    throw IllegalStateException("Папка для сборки '$newName' уже существует.")
                }
                oldPath.moveTo(newPath, true)
                newPath.toString()
            } else {
                buildToUpdate.installPath
            }

            if (buildToUpdate.version != newVersion || buildToUpdate.type != newType) {
                val versionDir = versionsPath.resolve(buildToUpdate.version)
                if (versionDir.exists()) {
                    println("Version changed. Deleting old version directory: $versionDir")
                    versionDir.deleteRecursively()
                }
                val vanillaPart = buildToUpdate.version.split("-fabric-").firstOrNull()
                if (vanillaPart != null) {
                    val vanillaDir = versionsPath.resolve(vanillaPart)
                    if (vanillaDir.exists()) {
                        println("Deleting old vanilla version directory: $vanillaDir")
                        vanillaDir.deleteRecursively()
                    }
                }
            }

            val updatedBuild = buildToUpdate.copy(
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
            
            buildDao.update(oldName, updatedBuild)
        }
    }

    suspend fun updateBuildModloaderVersion(buildName: String, modloaderVersion: String) = withContext(Dispatchers.IO) {
        buildsMutex.withLock {
            val buildToUpdate = buildDao.getAll().find { it.name.equals(buildName, ignoreCase = true) }
            if (buildToUpdate != null && buildToUpdate.modloaderVersion != modloaderVersion) {
                val updatedBuild = buildToUpdate.copy(modloaderVersion = modloaderVersion)
                buildDao.update(updatedBuild)
            }
        }
    }

    suspend fun deleteBuild(name: String) = withContext(Dispatchers.IO) {
        val buildToRemove = buildDao.getAll().find { it.name.equals(name, ignoreCase = true) } ?: return@withContext

        buildDao.delete(name)

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
            "QUILT" -> BuildType.QUILT
            "NEOFORGE" -> BuildType.NEOFORGE
            "FABRIC" -> BuildType.FABRIC
            "FORGE" -> BuildType.FORGE
            else -> BuildType.VANILLA
        }
        return MinecraftBuild(
            name, version, buildType, installPath, createdAt, imagePath, javaPath, maxRamMb, javaArgs, envVars
        )
    }
}
