/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package funlauncher

import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.*

/**
 * Fetches and parses version metadata for Vanilla, Fabric, and Forge.
 * It prioritizes using local cache before reaching out to the network.
 */
class VersionMetadataFetcher(private val buildManager: BuildManager, private val pathManager: PathManager) {
    private val json = Network.json
    private val client = Network.client

    private val globalVersionsDir: Path = pathManager.getGlobalVersionsDir()
    private val globalLibrariesDir: Path = pathManager.getGlobalLibrariesDir()
    private val launcherDataDir: Path = pathManager.getAppDataDirectory()
    private val cacheDir: Path = pathManager.getCacheDir()

    private fun log(message: String) = println("[VersionFetcher] $message")

    suspend fun getVanillaVersions(): List<String> {
        val manifest = client.get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json").body<VersionManifest>()
        return manifest.versions.map { it.id }
    }

    suspend fun getVersionInfo(build: MinecraftBuild, task: DownloadTask? = null): VersionInfo {
        // 1. Определяем ID версии для поиска
        val versionIdToFind = build.modloaderVersion ?: build.version
        val jsonFile = globalVersionsDir.resolve(versionIdToFind).resolve("$versionIdToFind.json")

        // 2. Пытаемся загрузить из локального файла, если он существует
        if (jsonFile.exists()) {
            try {
                val existingInfo = json.decodeFromString<VersionInfo>(jsonFile.readText())
                log("Загружена информация о версии '${existingInfo.id}' из локального файла.")
                return ensureLwjglOnLinuxArm(existingInfo)
            } catch (e: Exception) {
                log("Не удалось прочитать '${jsonFile.pathString}', файл будет перезапрошен. Ошибка: ${e.message}")
                jsonFile.deleteIfExists()
            }
        }

        // 3. Если локального файла нет или он поврежден, получаем информацию из сети
        log("Локальный файл для '$versionIdToFind' не найден или поврежден. Получение из сети...")
        val resultInfo = when (build.type) {
            BuildType.FABRIC -> fetchFabricProfile(build, task)
            BuildType.FORGE -> fetchForgeProfile(build, task)
            BuildType.QUILT -> fetchQuiltProfile(build, task)
            BuildType.NEOFORGE -> fetchNeoForgeProfile(build, task)
            BuildType.VANILLA -> fetchVanillaVersionInfo(build.version, task)
        }

        // 4. Обработка для Linux ARM
        val finalInfo = ensureLwjglOnLinuxArm(resultInfo)

        // 5. Сохраняем финальный JSON файл, используя ID из полученных данных
        val finalJsonFile = globalVersionsDir.resolve(finalInfo.id).resolve("${finalInfo.id}.json")
        finalJsonFile.parent.createDirectories()
        finalJsonFile.writeText(json.encodeToString(finalInfo))
        log("Информация о версии '${finalInfo.id}' сохранена в '${finalJsonFile.pathString}'")

        // 6. Если ID изменился (например, 'latest' разрешился в конкретную версию), обновляем сборку
        if (finalInfo.id != build.modloaderVersion) {
            log("Версия модлоадера для '${build.name}' обновлена на '${finalInfo.id}'")
            buildManager.updateBuildModloaderVersion(build.name, finalInfo.id)
        }

        return finalInfo
    }

    private fun ensureLwjglOnLinuxArm(originalInfo: VersionInfo): VersionInfo {
        val osName = getOsName()
        val arch = getArch()
        val isLinuxArm = osName == "linux" && arch == "arm64"

        if (!isLinuxArm) {
            return originalInfo
        }

        log("Linux ARM detected. Ensuring LWJGL 3.3.3 is in metadata.")

        val lwjglVersion = "3.3.3"
        val lwjglArtifacts = listOf(
            "lwjgl", "lwjgl-glfw", "lwjgl-jemalloc", "lwjgl-openal", "lwjgl-opengl", "lwjgl-stb", "lwjgl-tinyfd"
        )
        val nativeClassifier = "natives-linux-arm64"

        val newLibraries = lwjglArtifacts.flatMap { artifactName ->
            val mainLibName = "org.lwjgl:$artifactName:$lwjglVersion"
            val nativeLibName = "org.lwjgl:$artifactName:$lwjglVersion:$nativeClassifier"

            val mainArtifact = VersionInfo.Artifact(
                path = "org/lwjgl/$artifactName/$lwjglVersion/$artifactName-$lwjglVersion.jar",
                sha1 = "", // We'll rely on the downloader to validate if needed, or just download
                size = 0,
                url = "https://repo1.maven.org/maven2/org/lwjgl/$artifactName/$lwjglVersion/$artifactName-$lwjglVersion.jar"
            )

            val nativeArtifact = VersionInfo.Artifact(
                path = "org/lwjgl/$artifactName/$lwjglVersion/$artifactName-$lwjglVersion-$nativeClassifier.jar",
                sha1 = "",
                size = 0,
                url = "https://repo1.maven.org/maven2/org/lwjgl/$artifactName/$lwjglVersion/$artifactName-$lwjglVersion-$nativeClassifier.jar"
            )

            listOf(
                // Library entry for the main JAR
                VersionInfo.Library(
                    name = mainLibName,
                    downloads = VersionInfo.LibraryDownloads(artifact = mainArtifact)
                ),
                // Library entry for the native JAR
                VersionInfo.Library(
                    name = nativeLibName,
                    downloads = VersionInfo.LibraryDownloads(
                        classifiers = mapOf(nativeClassifier to nativeArtifact)
                    ),
                    natives = mapOf("linux" to "natives-linux-arm64")
                )
            )
        }

        // Filter out any existing LWJGL libraries to avoid conflicts
        val filteredOriginalLibs = originalInfo.libraries.filterNot { it.name.startsWith("org.lwjgl") }

        return originalInfo.copy(
            libraries = filteredOriginalLibs + newLibraries
        )
    }


    private suspend fun fetchFabricProfile(build: MinecraftBuild, task: DownloadTask?): VersionInfo {
        val (gameVersion, loaderVersion) = parseFabricVersion(build.version)
        task?.let { DownloadManager.updateTask(it.id, 0.06f, "Получение Vanilla $gameVersion") }
        val vanillaInfo = fetchVanillaVersionInfo(gameVersion, task)

        task?.let { DownloadManager.updateTask(it.id, 0.08f, "Получение профиля Fabric") }
        val fabricProfileUrl = "https://meta.fabricmc.net/v2/versions/loader/$gameVersion/$loaderVersion/profile/json"
        val fabricProfile = client.get(fabricProfileUrl).body<FabricProfile>()

        if (fabricProfile.id == null || fabricProfile.mainClass == null) {
            throw IllegalStateException("Failed to get valid Fabric profile. The server might have returned an error or the version is invalid.")
        }

        return vanillaInfo.copy(
            id = fabricProfile.id,
            mainClass = fabricProfile.mainClass,
            libraries = fabricProfile.libraries + vanillaInfo.libraries,
            arguments = mergeArguments(vanillaInfo.arguments, fabricProfile.arguments),
            gameArguments = null
        )
    }

    private suspend fun fetchForgeProfile(build: MinecraftBuild, task: DownloadTask?): VersionInfo {
        val versionId = build.modloaderVersion ?: build.version
        val (gameVersion, forgeVersion) = parseForgeVersion(versionId)

        task?.let { DownloadManager.updateTask(it.id, 0.06f, "Получение Vanilla $gameVersion") }
        fetchVanillaVersionInfo(gameVersion, task)

        val installerUrl = "https://maven.minecraftforge.net/net/minecraftforge/forge/$gameVersion-$forgeVersion/forge-$gameVersion-$forgeVersion-installer.jar"
        val installerJar = launcherDataDir.resolve("temp").resolve("forge-installer-$versionId.jar")
        
        installerJar.parent.createDirectories()
        if (!installerJar.exists()) {
            task?.let { DownloadManager.updateTask(it.id, 0.07f, "Загрузка установщика Forge") }
            log("Downloading Forge Installer...")
            val bytes = client.get(installerUrl).body<ByteArray>()
            installerJar.writeBytes(bytes)
        }

        runForgeInstaller(installerJar, task)

        val modernForgeJsonPath = globalVersionsDir.resolve(versionId).resolve("$versionId.json")
        val legacyForgeJsonPath = globalLibrariesDir.resolve("net/minecraftforge/forge/$gameVersion-$forgeVersion/forge-$gameVersion-$forgeVersion-client.json")

        val profileJsonPath = when {
            modernForgeJsonPath.exists() -> modernForgeJsonPath
            legacyForgeJsonPath.exists() -> legacyForgeJsonPath
            else -> throw IllegalStateException("Forge installer did not create the expected version JSON file.")
        }

        val versionProfile = json.decodeFromString<VersionProfile>(profileJsonPath.readText())
        val vanillaInfo = fetchVanillaVersionInfo(versionProfile.inheritsFrom, task)

        return vanillaInfo.copy(
            id = versionProfile.id,
            mainClass = versionProfile.mainClass,
            libraries = versionProfile.libraries + vanillaInfo.libraries,
            arguments = mergeArguments(vanillaInfo.arguments, versionProfile.arguments),
            gameArguments = null
        )
    }

    private suspend fun fetchQuiltProfile(build: MinecraftBuild, task: DownloadTask?): VersionInfo {
        val (gameVersion, loaderVersion) = parseQuiltVersion(build.version)
        task?.let { DownloadManager.updateTask(it.id, 0.06f, "Получение Vanilla $gameVersion") }
        val vanillaInfo = fetchVanillaVersionInfo(gameVersion, task)

        task?.let { DownloadManager.updateTask(it.id, 0.08f, "Получение профиля Quilt") }
        val quiltProfileUrl = "https://meta.quiltmc.org/v3/versions/loader/$gameVersion/$loaderVersion/profile/json"
        val quiltProfile = client.get(quiltProfileUrl).body<FabricProfile>() // Quilt profile is compatible with Fabric's

        if (quiltProfile.id == null || quiltProfile.mainClass == null) {
            throw IllegalStateException("Failed to get valid Quilt profile. The server might have returned an error or the version is invalid.")
        }

        return vanillaInfo.copy(
            id = quiltProfile.id,
            mainClass = quiltProfile.mainClass,
            libraries = quiltProfile.libraries + vanillaInfo.libraries,
            arguments = mergeArguments(vanillaInfo.arguments, quiltProfile.arguments),
            gameArguments = null
        )
    }

    @Serializable
    private data class InstallerProfile(val version: String)

    private suspend fun fetchNeoForgeProfile(build: MinecraftBuild, task: DownloadTask?): VersionInfo {
        val (gameVersion, neoForgeVersion) = parseNeoForgeVersion(build.version)
        task?.let { DownloadManager.updateTask(it.id, 0.06f, "Получение Vanilla $gameVersion") }
        fetchVanillaVersionInfo(gameVersion, task)

        val installerUrl = "https://maven.neoforged.net/releases/net/neoforged/neoforge/$neoForgeVersion/neoforge-$neoForgeVersion-installer.jar"
        val installerJar = launcherDataDir.resolve("temp").resolve("neoforge-installer-$neoForgeVersion.jar")

        installerJar.parent.createDirectories()
        if (!installerJar.exists()) {
            task?.let { DownloadManager.updateTask(it.id, 0.07f, "Загрузка установщика NeoForge") }
            log("Downloading NeoForge Installer...")
            val bytes = client.get(installerUrl).body<ByteArray>()
            installerJar.writeBytes(bytes)
        }

        // Read the version ID from the installer's profile
        val actualVersionId = withContext(Dispatchers.IO) {
            ZipFile(installerJar.toFile()).use { zip ->
                val entry = zip.getEntry("install_profile.json")
                    ?: throw IllegalStateException("install_profile.json not found in NeoForge installer.")
                val content = zip.getInputStream(entry).bufferedReader().readText()
                json.decodeFromString<InstallerProfile>(content).version
            }
        }
        log("Installer will create version: $actualVersionId")

        runForgeInstaller(installerJar, task) // NeoForge installer is compatible with Forge's

        val profileJsonPath = globalVersionsDir.resolve(actualVersionId).resolve("$actualVersionId.json")
        if (!profileJsonPath.exists()) {
            throw IllegalStateException("NeoForge installer did not create the expected version JSON file: $profileJsonPath")
        }

        val versionProfile = json.decodeFromString<VersionProfile>(profileJsonPath.readText())
        val vanillaInfo = fetchVanillaVersionInfo(versionProfile.inheritsFrom, task)

        return vanillaInfo.copy(
            id = versionProfile.id,
            mainClass = versionProfile.mainClass,
            libraries = versionProfile.libraries + vanillaInfo.libraries,
            arguments = mergeArguments(vanillaInfo.arguments, versionProfile.arguments),
            gameArguments = null
        )
    }


    private suspend fun runForgeInstaller(installerJar: Path, task: DownloadTask?) = withContext(Dispatchers.IO) {
        task?.let { DownloadManager.updateTask(it.id, 0.09f, "Запуск установщика...") }
        log("Running Forge installer: $installerJar")

        val fakeProfiles = launcherDataDir.resolve("launcher_profiles.json")
        if (!fakeProfiles.exists()) {
            log("Creating fake launcher_profiles.json to satisfy Forge installer.")
            fakeProfiles.writeText("""{ "profiles": {} }""")
        }

        val javaPath = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java"
        val command = listOf(javaPath, "-jar", installerJar.absolutePathString(), "--installClient", launcherDataDir.absolutePathString())

        val process = ProcessBuilder(command).redirectErrorStream(true).start()

        process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach {
                log("Installer: $it")
                task?.let { task -> DownloadManager.updateTask(task.id, 0.09f, "Установщик: ${it.take(40)}...") }
            }
        }

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw RuntimeException("Forge installer failed with exit code $exitCode.")
        }
        log("Forge installer finished successfully.")
    }

    private suspend fun fetchVanillaVersionInfo(gameVersion: String, task: DownloadTask?): VersionInfo {
        val jsonFile = globalVersionsDir.resolve(gameVersion).resolve("$gameVersion.json")
        if (jsonFile.exists()) {
            return json.decodeFromString(jsonFile.readText())
        }

        val cachedManifest = cacheDir.resolve("vanilla_versions.json")
        var versionUrl: String? = null

        if (cachedManifest.exists()) {
            try {
                val manifest = json.decodeFromString<VersionManifest>(cachedManifest.readText())
                versionUrl = manifest.versions.find { it.id == gameVersion }?.url
            } catch (e: Exception) {
                log("Could not read vanilla cache: ${e.message}")
            }
        }

        if (versionUrl == null) {
            task?.let { DownloadManager.updateTask(it.id, task.progress.value, "Загрузка манифеста Vanilla") }
            log("Fetching vanilla manifest from network...")
            val manifest = client.get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json").body<VersionManifest>()
            versionUrl = manifest.versions.find { it.id == gameVersion }?.url
        }

        val finalUrl = versionUrl ?: throw Exception("Base version '$gameVersion' not found in cache or network")

        task?.let { DownloadManager.updateTask(it.id, task.progress.value, "Загрузка JSON-файла Vanilla") }
        val bytes = client.get(finalUrl).body<ByteArray>()
        jsonFile.parent.createDirectories()
        jsonFile.writeBytes(bytes)
        return json.decodeFromString(bytes.decodeToString())
    }

    private fun mergeArguments(vanillaArgs: VersionInfo.Arguments?, otherArgs: VersionInfo.Arguments?): VersionInfo.Arguments {
        val game = (vanillaArgs?.game ?: emptyList()) + (otherArgs?.game ?: emptyList())
        val jvm = (vanillaArgs?.jvm ?: emptyList()) + (otherArgs?.jvm ?: emptyList())
        return VersionInfo.Arguments(game = game, jvm = jvm)
    }

    private fun parseFabricVersion(version: String): Pair<String, String> {
        val parts = version.split("-fabric-")
        return if (parts.size == 2) parts[0] to parts[1] else throw IllegalArgumentException("Invalid Fabric version string: $version")
    }

    private fun parseForgeVersion(version: String): Pair<String, String> {
        val parts = version.split("-forge-")
        return if (parts.size == 2) parts[0] to parts[1] else throw IllegalArgumentException("Invalid Forge version string: $version")
    }

    private fun parseQuiltVersion(version: String): Pair<String, String> {
        val parts = version.split("-quilt-")
        return if (parts.size == 2) parts[0] to parts[1] else throw IllegalArgumentException("Invalid Quilt version string: $version")
    }

    private fun parseNeoForgeVersion(version: String): Pair<String, String> {
        val parts = version.split("-neoforge-")
        return if (parts.size == 2) parts[0] to parts[1] else throw IllegalArgumentException("Invalid NeoForge version string: $version")
    }

    private fun getOsName(): String = when {
        System.getProperty("os.name").lowercase().contains("win") -> "windows"
        System.getProperty("os.name").lowercase().contains("mac") -> "osx"
        else -> "linux"
    }

    private fun getArch(): String = when (val arch = System.getProperty("os.arch").lowercase()) {
        "aarch64" -> "arm64"
        "x86_64", "amd64" -> "x64"
        "x86" -> "x86"
        else -> arch
    }
}
