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
    val envVars: String? = null
)

@OptIn(ExperimentalPathApi::class)
class BuildManager {
    private val launcherPath: Path = PathManager.getAppDataDirectory()
    private val instancesPath: Path = launcherPath.resolve("instances")
    private val versionsPath: Path = launcherPath.resolve("versions")
    private val assetsPath: Path = launcherPath.resolve("assets")
    private val buildsFilePath: Path = launcherPath.resolve("builds.json")

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    init {
        launcherPath.createDirectories()
        instancesPath.createDirectories()
        assetsPath.createDirectories()
        versionsPath.createDirectories()
    }

    suspend fun loadBuilds(): List<MinecraftBuild> = withContext(Dispatchers.IO) {
        if (!buildsFilePath.exists()) {
            return@withContext emptyList()
        }
        val content = buildsFilePath.readText()
        try {
            json.decodeFromString<List<MinecraftBuild>>(content)
        } catch (e: Exception) {
            println("Ошибка чтения builds.json: ${e.message}")
            // Попытка миграции со старого формата
            try {
                val oldFormatBuilds = json.decodeFromString<List<OldMinecraftBuild>>(content)
                val newBuilds = oldFormatBuilds.map {
                    it.toNewBuild()
                }
                saveBuilds(newBuilds)
                newBuilds
            } catch (e2: Exception) {
                println("Миграция не удалась: ${e2.message}")
                emptyList()
            }
        }
    }

    private suspend fun saveBuilds(builds: List<MinecraftBuild>) = withContext(Dispatchers.IO) {
        val content = json.encodeToString(builds)
        buildsFilePath.writeText(content)
    }

    suspend fun addBuild(name: String, version: String, type: BuildType, imagePath: String?) = withContext(Dispatchers.IO) {
        require(name.isNotBlank()) { "Название сборки не может быть пустым" }
        require(version.isNotBlank()) { "Версия не может быть пустой" }

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
        val builds = loadBuilds().toMutableList()
        val buildIndex = builds.indexOfFirst { it.name.equals(oldName, ignoreCase = true) }
        if (buildIndex == -1) return@withContext

        val oldBuild = builds[buildIndex]

        // 1. Проверка на конфликт имен
        if (oldName != newName && builds.any { it.name.equals(newName, ignoreCase = true) }) {
            throw IllegalStateException("Сборка с именем '$newName' уже существует.")
        }

        // 2. Переименование папки установки, если имя изменилось
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

        // 3. Удаление старых файлов версии, если версия или тип изменились
        if (oldBuild.version != newVersion || oldBuild.type != newType) {
            val versionDir = versionsPath.resolve(oldBuild.version)
            if (versionDir.exists()) {
                println("Version changed. Deleting old version directory: $versionDir")
                versionDir.deleteRecursively()
            }
            // Также удаляем json файл ванильной версии, если он был частью старой сборки
            val vanillaPart = oldBuild.version.split("-fabric-").firstOrNull()
            if (vanillaPart != null) {
                val vanillaDir = versionsPath.resolve(vanillaPart)
                if (vanillaDir.exists()) {
                     println("Deleting old vanilla version directory: $vanillaDir")
                    vanillaDir.deleteRecursively()
                }
            }
        }

        // 4. Обновление и сохранение сборки
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

    suspend fun deleteBuild(name: String) = withContext(Dispatchers.IO) {
        val builds = loadBuilds().toMutableList()
        val buildToRemove = builds.firstOrNull { it.name.equals(name, ignoreCase = true) }

        if (buildToRemove != null) {
            builds.remove(buildToRemove)
            saveBuilds(builds)

            // Удаляем папку установки
            val pathToDelete = Path(buildToRemove.installPath)
            if (pathToDelete.exists()) {
                pathToDelete.deleteRecursively()
            }

            // Удаляем папку версии
            val versionDir = versionsPath.resolve(buildToRemove.version)
            if (versionDir.exists()) {
                versionDir.deleteRecursively()
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
