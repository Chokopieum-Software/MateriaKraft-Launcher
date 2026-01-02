/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package funlauncher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
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
class BuildManager {
    private val launcherPath: Path = PathManager.getAppDataDirectory()
    private val instancesPath: Path = launcherPath.resolve("instances")
    private val versionsPath: Path = launcherPath.resolve("versions")
    private val buildsFilePath: Path = launcherPath.resolve("builds.json")

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private var builds: MutableList<MinecraftBuild> = mutableListOf()

    suspend fun loadBuilds(): List<MinecraftBuild> = withContext(Dispatchers.IO) {
        if (!buildsFilePath.exists()) {
            builds = mutableListOf()
            return@withContext builds
        }
        val content = buildsFilePath.readText()
        try {
            builds = json.decodeFromString<MutableList<MinecraftBuild>>(content)
        } catch (e: Exception) {
            println("Ошибка чтения builds.json: ${e.message}")
            try {
                val oldFormatBuilds = json.decodeFromString<List<OldMinecraftBuild>>(content)
                val newBuilds = oldFormatBuilds.map { it.toNewBuild() }.toMutableList()
                saveBuilds(newBuilds)
            } catch (e2: Exception) {
                println("Миграция не удалась: ${e2.message}")
                builds = mutableListOf()
            }
        }
        return@withContext builds
    }

    private suspend fun saveBuilds(buildsToSave: List<MinecraftBuild>) = withContext(Dispatchers.IO) {
        builds = buildsToSave.toMutableList()
        val content = json.encodeToString(builds)
        buildsFilePath.writeText(content)
    }

    suspend fun addBuild(name: String, version: String, type: BuildType, imagePath: String?) = withContext(Dispatchers.IO) {
        require(name.isNotBlank()) { "Название сборки не может быть пустым" }
        require(version.isNotBlank()) { "Версия не может быть пустой" }

        val invalidChars = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
        require(name.none { it in invalidChars }) { "Название сборки содержит запрещенные символы" }

        if (builds.any { it.name.equals(name, ignoreCase = true) }) {
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

        builds.add(newBuild)
        saveBuilds(builds)

        buildPath.createDirectories()
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
        val buildIndex = builds.indexOfFirst { it.name.equals(oldName, ignoreCase = true) }
        if (buildIndex == -1) return@withContext

        val oldBuild = builds[buildIndex]

        if (oldName != newName && builds.any { it.name.equals(newName, ignoreCase = true) }) {
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

        builds[buildIndex] = oldBuild.copy(
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
        saveBuilds(builds)
    }

    suspend fun updateBuildModloaderVersion(buildName: String, modloaderVersion: String) = withContext(Dispatchers.IO) {
        val buildIndex = builds.indexOfFirst { it.name.equals(buildName, ignoreCase = true) }
        if (buildIndex != -1) {
            val oldBuild = builds[buildIndex]
            if (oldBuild.modloaderVersion != modloaderVersion) {
                builds[buildIndex] = oldBuild.copy(modloaderVersion = modloaderVersion)
                saveBuilds(builds)
            }
        }
    }

    suspend fun deleteBuild(name: String) = withContext(Dispatchers.IO) {
        val buildToRemove = builds.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return@withContext

        // 1. Удаляем из списка и сохраняем JSON
        builds.remove(buildToRemove)
        saveBuilds(builds)

        // 2. Удаляем папку инстанса (saves, mods, etc.)
        val instancePath = Path(buildToRemove.installPath)
        if (instancePath.exists()) {
            instancePath.deleteRecursively()
            println("Deleted instance directory: $instancePath")
        }

        // 3. Удаляем папку с версией из global versions
        val versionIdToDelete = buildToRemove.modloaderVersion ?: buildToRemove.version
        val versionDir = versionsPath.resolve(versionIdToDelete)
        if (versionDir.exists()) {
            versionDir.deleteRecursively()
            println("Deleted version directory: $versionDir")
        }

        // 4. Удаляем обложку, если она есть
        buildToRemove.imagePath?.let {
            val imageFile = File(it)
            if (imageFile.exists() && imageFile.isFile) {
                imageFile.delete()
                println("Deleted cover image: $it")
            }
        }

        // 5. Очищаем кэш загрузчика для этой версии (на случай, если он остался)
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
