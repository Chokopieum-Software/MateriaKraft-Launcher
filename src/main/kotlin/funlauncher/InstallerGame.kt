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
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Semaphore
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import java.io.File
import java.net.ConnectException
import java.net.UnknownHostException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.jar.JarFile
import kotlin.io.path.*

// === Модели JSON ===

@Serializable
data class FabricProfile(
    val id: String? = null,
    val inheritsFrom: String? = null,
    val mainClass: String? = null,
    val libraries: List<VersionInfo.Library> = emptyList(),
    val arguments: VersionInfo.Arguments? = null
)

@Serializable
data class VersionProfile(
    val id: String,
    val inheritsFrom: String,
    val mainClass: String,
    val libraries: List<VersionInfo.Library>,
    val arguments: VersionInfo.Arguments? = null
)

@Serializable data class VersionInfo(
    val id: String,
    val libraries: List<Library>,
    val mainClass: String,
    @SerialName("minecraftArguments") val gameArguments: String? = null,
    val arguments: Arguments? = null,
    val assets: String,
    val downloads: Downloads,
    val assetIndex: AssetIndexInfo
) {
    @Serializable data class Library(
        val name: String,
        val url: String? = null, // For Fabric's custom maven
        val downloads: LibraryDownloads? = null,
        val natives: Map<String, String>? = null,
        val rules: List<Rule>? = null
    )

    @Serializable data class Arguments(val game: List<JsonElementWrapper> = emptyList(), val jvm: List<JsonElementWrapper> = emptyList())
    @Serializable data class LibraryDownloads(val artifact: Artifact? = null, val classifiers: Map<String, Artifact>? = null)
    @Serializable data class Artifact(val path: String, val url: String, val size: Long, val sha1: String? = null)
    @Serializable data class Downloads(val client: ClientDownload)
    @Serializable data class ClientDownload(val url: String)
    @Serializable data class AssetIndexInfo(val id: String, val url: String)
    @Serializable data class Rule(val action: String, val os: OS? = null)
    @Serializable data class OS(val name: String? = null) // Поле name сделано необязательным
}

@Serializable data class AssetIndex(val objects: Map<String, AssetObject>) {
    @Serializable data class AssetObject(val hash: String, val size: Long)
}

// === Installer ===

class MinecraftInstaller(private val build: MinecraftBuild) {
    private val gameDir: Path = Paths.get(build.installPath)
    private val launcherDataDir: Path = PathManager.getAppDataDirectory()
    private val globalAssetsDir: Path = launcherDataDir.resolve("assets")
    private val globalVersionsDir: Path = launcherDataDir.resolve("versions")
    private val globalLibrariesDir: Path = launcherDataDir.resolve("libraries")
    private val nativesDir: Path by lazy { gameDir.resolve("natives").also { it.createDirectories() } }

    private val mavenCentral = "https://repo1.maven.org/maven2/"

    private val isArm64Arch: Boolean by lazy { System.getProperty("os.arch").lowercase().contains("aarch64") }

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; coerceInputValues = true }
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) { requestTimeoutMillis = 60000 }
        defaultRequest {
            header("User-Agent", "MateriaKraft Launcher")
        }
    }

    private val logChannel = Channel<String>(Channel.UNLIMITED)

    init {
        CoroutineScope(Dispatchers.IO).launch { for (message in logChannel) { println("[LOG] $message") } }
    }

    private fun log(message: String) { logChannel.trySend(message) }

    suspend fun launchOffline(
        username: String, javaPath: String, maxRamMb: Int, javaArgs: String, envVars: String, showConsole: Boolean
    ): Process {
        val task = DownloadManager.startTask("Minecraft ${build.version}")
        try {
            log("System: ${System.getProperty("os.name")} ${System.getProperty("os.arch")}, Java: $javaPath")
            if (nativesDir.exists()) nativesDir.toFile().deleteRecursively()
            nativesDir.createDirectories()

            DownloadManager.updateTask(task.id, 0.05f, "Получение метаданных...")
            val originalVersionInfo = getVersionInfo()
            val versionInfo = if (isArm64Arch && getOsName() == "linux") {
                forceLwjglVersionForArm(originalVersionInfo)
            } else {
                originalVersionInfo
            }


            DownloadManager.updateTask(task.id, 0.1f, "Загрузка файлов...")
            downloadRequiredFiles(versionInfo) { progress, status ->
                DownloadManager.updateTask(task.id, 0.1f + progress * 0.8f, status)
            }

            DownloadManager.updateTask(task.id, 0.9f, "Распаковка...")
            extractNatives(versionInfo)

            DownloadManager.updateTask(task.id, 1.0f, "Запуск...")
            val command = buildLaunchCommand(versionInfo, username, javaPath, maxRamMb, javaArgs)
            val processBuilder = ProcessBuilder(command).directory(gameDir.toFile()).redirectErrorStream(true)

            if (envVars.isNotBlank()) {
                val env = processBuilder.environment()
                envVars.lines().forEach { line ->
                    val parts = line.split("=", limit = 2)
                    if (parts.size == 2) env[parts[0].trim()] = parts[1].trim()
                }
            }

            val process = withContext(Dispatchers.IO) {
                processBuilder.start()
            }

            // Всегда логируем в консоль IDE
            Thread {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        println(line) // Логирование в консоль IDE
                        if (showConsole) {
                            GameConsole.addLog(line) // Логирование в UI консоль
                        }
                    }
                }
            }.start()

            return process
        } catch (e: Exception) {
            when (e) {
                is UnknownHostException, is ConnectException, is HttpRequestTimeoutException -> {
                    val versionId = build.version
                    val jsonFile = globalVersionsDir.resolve(versionId).resolve("$versionId.json")
                    val message = if (jsonFile.exists()) {
                        "Не удалось скачать некоторые файлы игры. Проверьте подключение к интернету и попробуйте снова."
                    } else {
                        "Не удалось получить информацию о версии '$versionId'. Для первого запуска этой версии требуется подключение к интернету."
                    }
                    throw IllegalStateException(message, e)
                }
                else -> throw e // rethrow other exceptions
            }
        } finally {
            DownloadManager.endTask(task.id)
        }
    }

    private fun forceLwjglVersionForArm(info: VersionInfo): VersionInfo {
        log("ARM64 Linux detected. Forcing LWJGL 3.3.3 from Maven Central.")
        val forcedVersion = "3.3.3"

        val newLibraries = info.libraries.map { lib ->
            if (!isLwjglLibrary(lib.name)) {
                lib // Not an LWJGL lib, return as is
            } else {
                log("Overriding LWJGL library: ${lib.name} with version $forcedVersion")
                val parts = lib.name.split(':')
                val group = parts[0]
                val artifactId = parts[1]
                val newName = "$group:$artifactId:$forcedVersion"

                // Rebuild the 'downloads' object for the new version, pointing to Maven Central
                val mainArtifactPath = getArtifactPath(newName)
                val mainArtifactUrl = mavenCentral + mainArtifactPath
                val mainArtifact = VersionInfo.Artifact(path = mainArtifactPath, url = mainArtifactUrl, size = 0, sha1 = null)

                val nativeClassifier = "natives-linux-arm64"
                val nativeArtifactPath = getArtifactPath(newName, nativeClassifier)
                val nativeArtifactUrl = mavenCentral + nativeArtifactPath
                val nativeArtifact = VersionInfo.Artifact(path = nativeArtifactPath, url = nativeArtifactUrl, size = 0, sha1 = null)

                val newClassifiers = (lib.downloads?.classifiers?.toMutableMap() ?: mutableMapOf()).also {
                    it[nativeClassifier] = nativeArtifact
                }
                val newDownloads = VersionInfo.LibraryDownloads(artifact = mainArtifact, classifiers = newClassifiers)

                // Create the new library object with overridden data
                lib.copy(
                    name = newName,
                    downloads = newDownloads,
                    url = mavenCentral, // Force maven central for this lib
                    natives = (lib.natives?.toMutableMap() ?: mutableMapOf()).also {
                        it["linux"] = nativeClassifier // Ensure natives are set correctly for linux
                    }
                )
            }
        }
        return info.copy(libraries = newLibraries)
    }

    private suspend fun getVersionInfo(): VersionInfo {
        val versionId = build.version
        val jsonFile = globalVersionsDir.resolve(versionId).resolve("$versionId.json")

        if (jsonFile.exists()) {
            try {
                return json.decodeFromString<VersionInfo>(jsonFile.readText())
            } catch (e: Exception) {
                log("Не удалось прочитать '${jsonFile.pathString}', файл будет создан заново. Ошибка: ${e.message}")
                jsonFile.deleteIfExists()
            }
        }

        val resultInfo = when (build.type) {
            BuildType.FABRIC -> {
                val (gameVersion, loaderVersion) = parseFabricVersion(versionId)
                val vanillaInfo = getVanillaVersionInfo(gameVersion)
                val fabricProfileUrl = "https://meta.fabricmc.net/v2/versions/loader/$gameVersion/$loaderVersion/profile/json"
                val fabricProfile = client.get(fabricProfileUrl).body<FabricProfile>()

                if (fabricProfile.id == null || fabricProfile.mainClass == null) {
                    throw IllegalStateException("Failed to get valid Fabric profile. The server might have returned an error or the version is invalid.")
                }

                vanillaInfo.copy(
                    id = fabricProfile.id,
                    mainClass = fabricProfile.mainClass,
                    libraries = fabricProfile.libraries + vanillaInfo.libraries,
                    arguments = mergeArguments(vanillaInfo.arguments, fabricProfile.arguments),
                    gameArguments = null
                )
            }
            BuildType.FORGE -> {
                getForgeVersionInfo(versionId)
            }
            BuildType.VANILLA -> {
                getVanillaVersionInfo(versionId)
            }
        }

        jsonFile.parent.createDirectories()
        jsonFile.writeText(json.encodeToString(resultInfo))
        return resultInfo
    }

    private suspend fun getForgeVersionInfo(versionId: String): VersionInfo {
        val (gameVersion, forgeVersion) = parseForgeVersion(versionId)

        // 1. Убедимся, что ванильная версия скачана
        getVanillaVersionInfo(gameVersion)

        // 2. Скачиваем установщик Forge
        val installerUrl = "https://maven.minecraftforge.net/net/minecraftforge/forge/$gameVersion-$forgeVersion/forge-$gameVersion-$forgeVersion-installer.jar"
        val installerJar = launcherDataDir.resolve("temp").resolve("forge-installer-$versionId.jar")
        downloadFile(installerUrl, installerJar, "Forge Installer")

        // 3. Запускаем установщик
        runForgeInstaller(installerJar)

        // 4. Ищем и читаем созданный JSON профиль
        val modernForgeJsonPath = globalVersionsDir.resolve(versionId).resolve("$versionId.json")
        val legacyForgeJsonPath = globalLibrariesDir.resolve("net/minecraftforge/forge/$gameVersion-$forgeVersion/forge-$gameVersion-$forgeVersion-client.json")

        val profileJsonPath = when {
            modernForgeJsonPath.exists() -> modernForgeJsonPath
            legacyForgeJsonPath.exists() -> legacyForgeJsonPath
            else -> throw IllegalStateException("Forge installer did not create the expected version JSON file.")
        }

        val versionProfile = json.decodeFromString<VersionProfile>(profileJsonPath.readText())
        val vanillaInfo = getVanillaVersionInfo(versionProfile.inheritsFrom)

        return vanillaInfo.copy(
            id = versionProfile.id,
            mainClass = versionProfile.mainClass,
            libraries = versionProfile.libraries + vanillaInfo.libraries,
            arguments = mergeArguments(vanillaInfo.arguments, versionProfile.arguments),
            gameArguments = null
        )
    }

    private suspend fun runForgeInstaller(installerJar: Path) = withContext(Dispatchers.IO) {
        log("Running Forge installer: $installerJar")

        // Создаем фейковый launcher_profiles.json
        val fakeProfiles = launcherDataDir.resolve("launcher_profiles.json")
        if (!fakeProfiles.exists()) {
            log("Creating fake launcher_profiles.json to satisfy Forge installer.")
            fakeProfiles.writeText("""{ "profiles": {} }""")
        }

        val javaPath = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java"
        val command = listOf(javaPath, "-jar", installerJar.absolutePathString(), "--installClient", launcherDataDir.absolutePathString())

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { log("Installer: $it") }
        }

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw RuntimeException("Forge installer failed with exit code $exitCode.")
        }
        log("Forge installer finished successfully.")
    }

    private fun mergeArguments(vanillaArgs: VersionInfo.Arguments?, fabricArgs: VersionInfo.Arguments?): VersionInfo.Arguments {
        val game = (vanillaArgs?.game ?: emptyList()) + (fabricArgs?.game ?: emptyList())
        val jvm = (vanillaArgs?.jvm ?: emptyList()) + (fabricArgs?.jvm ?: emptyList())
        return VersionInfo.Arguments(game = game, jvm = jvm)
    }

    private suspend fun getVanillaVersionInfo(gameVersion: String): VersionInfo {
        val jsonFile = globalVersionsDir.resolve(gameVersion).resolve("$gameVersion.json")
        if (!jsonFile.exists()) {
            val manifestUrl = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
            val manifest = client.get(manifestUrl).body<VersionManifest>()
            val entry = manifest.versions.find { it.id == gameVersion } ?: throw Exception("Base version '$gameVersion' not found")
            val bytes = client.get(entry.url).body<ByteArray>()
            jsonFile.parent.createDirectories()
            jsonFile.writeBytes(bytes)
        }
        return json.decodeFromString(jsonFile.readText())
    }

    private fun parseFabricVersion(version: String): Pair<String, String> {
        val parts = version.split("-fabric-")
        return if (parts.size == 2) parts[0] to parts[1] else throw IllegalArgumentException("Invalid Fabric version string: $version")
    }

    private fun parseForgeVersion(version: String): Pair<String, String> {
        val parts = version.split("-forge-")
        return if (parts.size == 2) parts[0] to parts[1] else throw IllegalArgumentException("Invalid Forge version string: $version")
    }

    private suspend fun downloadRequiredFiles(info: VersionInfo, onProgress: (Float, String) -> Unit) {
        val gameVersionForJar = when (build.type) {
            BuildType.FABRIC -> parseFabricVersion(build.version).first
            BuildType.FORGE -> parseForgeVersion(build.version).first
            else -> info.id
        }
        val clientJarPath = globalVersionsDir.resolve(gameVersionForJar).resolve("$gameVersionForJar.jar")

        if (!clientJarPath.exists()) {
            val vanillaInfoForJar = getVanillaVersionInfo(gameVersionForJar)
            downloadFile(vanillaInfoForJar.downloads.client.url, clientJarPath, "Client JAR")
        }

        val libraries = info.libraries.filter { isLibraryAllowed(it) }
        val semaphore = Semaphore(30)
        val counter = AtomicInteger(0)

        libraries.map { lib ->
            CoroutineScope(Dispatchers.IO).async {
                semaphore.acquire()
                try {
                    downloadLibrary(lib)
                } finally {
                    semaphore.release()
                    val c = counter.incrementAndGet()
                    onProgress(c.toFloat() / libraries.size * 0.5f, "Libs: ${lib.name.substringAfterLast(':')}")
                }
            }
        }.awaitAll()

        val idxFile = globalAssetsDir.resolve("indexes").resolve("${info.assetIndex.id}.json")
        downloadFile(info.assetIndex.url, idxFile, "Asset Index")
        val idx = json.decodeFromString<AssetIndex>(idxFile.readText())
        downloadAssetsInParallel(idx) { progress, status -> onProgress(0.5f + progress * 0.5f, status) }
    }

    private fun getArtifactPath(name: String, classifier: String? = null): String {
        val parts = name.split(':')
        val groupPath = parts[0].replace('.', '/')
        val artifactName = parts[1]
        val version = parts[2]
        val classifierStr = if (classifier != null) "-$classifier" else ""
        return "$groupPath/$artifactName/$version/$artifactName-$version$classifierStr.jar"
    }

    private suspend fun downloadLibrary(lib: VersionInfo.Library) {
        // 1. Download main artifact
        lib.downloads?.artifact?.let { artifact ->
            val path = globalLibrariesDir.resolve(artifact.path)
            downloadFile(artifact.url, path, "Lib: ${lib.name}")
        } ?: run {
            // Fallback for libs without downloads info (like fabric-loader)
            val artifactPath = getArtifactPath(lib.name)
            val path = globalLibrariesDir.resolve(artifactPath)
            val url = (lib.url ?: mavenCentral) + artifactPath
            downloadFile(url, path, "Lib: ${lib.name}")
        }

        // 2. Download native artifact if applicable for the current OS
        lib.natives?.get(getOsName())?.let { nativeKey ->
            lib.downloads?.classifiers?.get(nativeKey)?.let { nativeArtifact ->
                val path = globalLibrariesDir.resolve(nativeArtifact.path)
                downloadFile(nativeArtifact.url, path, "Native: ${lib.name}")
            }
        }
    }

    private fun extractNatives(info: VersionInfo) {
        log("Extracting natives...")
        info.libraries.filter { isLibraryAllowed(it) && it.natives != null }.forEach { lib ->
            val nativeKey = lib.natives?.get(getOsName()) ?: return@forEach
            val artifactPath = lib.downloads?.classifiers?.get(nativeKey)?.path ?: return@forEach

            val jarPath = globalLibrariesDir.resolve(artifactPath)
            if (jarPath.exists()) {
                try {
                    JarFile(jarPath.toFile()).use { jar ->
                        jar.entries().asSequence().filterNot { it.isDirectory || it.name.contains("META-INF") }.forEach { entry ->
                            val outFile = nativesDir.resolve(entry.name.substringAfterLast('/'))
                            Files.copy(jar.getInputStream(entry), outFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                        }
                    }
                } catch (e: Exception) {
                    log("Failed to extract ${jarPath.name}: ${e.message}")
                }
            } else {
                log("Native JAR not found for extraction (this should have been downloaded earlier): ${jarPath.pathString}")
            }
        }
    }

    private fun buildLaunchCommand(info: VersionInfo, username: String, javaPath: String, maxRamMb: Int, customJavaArgs: String): List<String> {
        // 1. Собираем Classpath
        val cpList = mutableListOf<String>()
        info.libraries.filter { isLibraryAllowed(it) }.forEach { lib ->
            val path = lib.downloads?.artifact?.path ?: getArtifactPath(lib.name)
            cpList.add(globalLibrariesDir.resolve(path).toAbsolutePath().toString())
        }
        val gameVersionForJar = when (build.type) {
            BuildType.FABRIC -> parseFabricVersion(build.version).first
            BuildType.FORGE -> parseForgeVersion(build.version).first
            else -> info.id
        }
        val clientJarPath = globalVersionsDir.resolve(gameVersionForJar).resolve("$gameVersionForJar.jar")
        cpList.add(clientJarPath.toAbsolutePath().toString())
        val classpath = cpList.joinToString(File.pathSeparator)

        // 2. Готовим карту замен для плейсхолдеров
        val replacements = mapOf(
            "natives_directory" to nativesDir.toAbsolutePath().toString(),
            "launcher_name" to "MateriaKraft",
            "launcher_version" to "1.0",
            "classpath" to classpath,
            "auth_player_name" to username,
            "version_name" to info.id,
            "game_directory" to gameDir.toAbsolutePath().toString(),
            "assets_root" to globalAssetsDir.toAbsolutePath().toString(),
            "assets_index_name" to info.assetIndex.id,
            "auth_uuid" to UUID.nameUUIDFromBytes(username.toByteArray()).toString(),
            "auth_access_token" to "0",
            "user_properties" to "{}",
            "user_type" to "msa",
            "version_type" to build.type.name,
            "clientid" to "clientId",
            "auth_xuid" to "xuid",
            "resolution_width" to "854",
            "resolution_height" to "480"
        )

        // Функция для замены плейсхолдеров в строке
        val replacePlaceholders = { str: String ->
            var result = str
            replacements.forEach { (k, v) -> result = result.replace("\${$k}", v) }
            result
        }

        // 3. Собираем финальную команду
        val finalCommand = mutableListOf<String>()
        finalCommand.add(javaPath.ifBlank { "java" })

        // JVM аргументы
        finalCommand.add("-Xmx${maxRamMb}M")
        processArguments(info.arguments?.jvm).map(replacePlaceholders).let { finalCommand.addAll(it) }
        if (customJavaArgs.isNotBlank()) {
            finalCommand.addAll(customJavaArgs.split(" ").map(replacePlaceholders))
        }

        // Main class
        finalCommand.add(info.mainClass)

        // Игровые аргументы
        val gameArgsSource = info.arguments?.game ?: info.gameArguments?.split(" ")?.map { JsonElementWrapper.StringValue(it) }

        val processedGameArgs = processArguments(gameArgsSource).map(replacePlaceholders)

        // Фильтруем нежелательные аргументы, такие как --quickPlay и --demo
        val finalGameArgs = mutableListOf<String>()
        var i = 0
        while (i < processedGameArgs.size) {
            val arg = processedGameArgs[i]
            when {
                // Пропускаем --quickPlay и его значение
                arg.startsWith("--quickPlay") -> {
                    i += 2
                }
                // Пропускаем --demo и его необязательное значение (true/false)
                arg == "--demo" -> {
                    i++ // Пропускаем сам флаг --demo
                    // Если следующий аргумент это 'true' или 'false', пропускаем и его
                    if (i < processedGameArgs.size && (processedGameArgs[i].equals("true", ignoreCase = true) || processedGameArgs[i].equals("false", ignoreCase = true))) {
                        i++
                    }
                }
                else -> {
                    finalGameArgs.add(arg)
                    i++
                }
            }
        }
        finalCommand.addAll(finalGameArgs)

        log("--- LAUNCH COMMAND ---")
        log(finalCommand.joinToString(" "))
        log("----------------------")

        return finalCommand
    }


    private fun processArguments(args: List<JsonElementWrapper>?): List<String> {
        return args?.flatMap { wrapper ->
            when (wrapper) {
                is JsonElementWrapper.StringValue -> listOf(wrapper.value)
                is JsonElementWrapper.ObjectValue -> if (isRuleApplicable(wrapper.rules)) wrapper.getValues() else emptyList()
            }
        } ?: emptyList()
    }

    private fun isRuleApplicable(rules: List<VersionInfo.Rule>): Boolean {
        if (rules.isEmpty()) return true

        var applies = false
        for (rule in rules) {
            val osRule = rule.os
            if (osRule == null || osRule.name == null || osRule.name == getOsName()) {
                applies = rule.action == "allow"
            }
        }
        return applies
    }

    private suspend fun downloadFile(
        url: String,
        path: Path,
        desc: String,
        extraHeaders: Map<String, String> = emptyMap()
    ) {
        if (path.exists() && path.fileSize() > 0) {
            // log("File already exists, skipping: $desc") // Too verbose
            return
        }

        val maxRetries = 3
        val retryDelay = 3000L // 3 seconds

        for (attempt in 1..maxRetries) {
            try {
                log("Downloading $desc (attempt $attempt/$maxRetries)...")
                path.parent.createDirectories()
                val resp = client.get(url) {
                    extraHeaders.forEach { (key, value) -> header(key, value) }
                }

                if (resp.status.value == 200) {
                    path.writeBytes(resp.body())
                    log("Successfully downloaded $desc")
                    return // Success
                }

                throw Exception("HTTP ${resp.status.value}")

            } catch (e: Exception) {
                runCatching { path.deleteIfExists() } // Clean up partial file
                log("Error downloading $desc from $url: ${e.message}")
                if (attempt < maxRetries) {
                    log("Retrying in ${retryDelay / 1000} seconds...")
                    delay(retryDelay)
                } else {
                    throw e // Re-throw on the last attempt
                }
            }
        }
    }

    private suspend fun downloadAssetsInParallel(idx: AssetIndex, onProgress: (Float, String) -> Unit) {
        val assets = idx.objects.entries.toList()
        val semaphore = Semaphore(50)
        val counter = AtomicInteger(0)
        assets.map { (_, asset) ->
            CoroutineScope(Dispatchers.IO).async {
                semaphore.acquire()
                try {
                    val p = asset.hash.substring(0, 2)
                    val path = globalAssetsDir.resolve("objects").resolve(p).resolve(asset.hash)
                    if (!path.exists() || path.fileSize() != asset.size) {
                        downloadFile("https://resources.download.minecraft.net/$p/${asset.hash}", path, "Asset")
                    }
                    val c = counter.incrementAndGet()
                    if (c % 100 == 0) onProgress(c.toFloat() / assets.size, "Assets: $c/${assets.size}")
                } finally {
                    semaphore.release()
                }
            }
        }.awaitAll()
    }

    private fun isLibraryAllowed(lib: VersionInfo.Library): Boolean {
        return isRuleApplicable(lib.rules ?: emptyList())
    }

    private fun isLwjglLibrary(name: String): Boolean = name.startsWith("org.lwjgl")
    private fun getOsName(): String = when {
        System.getProperty("os.name").lowercase().contains("win") -> "windows"
        System.getProperty("os.name").lowercase().contains("mac") -> "osx"
        else -> "linux"
    }
}

@Serializable(with = JsonElementWrapperSerializer::class)
sealed class JsonElementWrapper {
    @Serializable data class StringValue(val value: String) : JsonElementWrapper()
    @Serializable data class ObjectValue(val rules: List<VersionInfo.Rule>, val value: JsonElement) : JsonElementWrapper() {
        fun getValues(): List<String> = when (value) {
            is JsonPrimitive -> listOf(value.content)
            is JsonArray -> value.map { (it as JsonPrimitive).content }
            else -> emptyList()
        }
    }
}

object JsonElementWrapperSerializer : KSerializer<JsonElementWrapper> {
    override val descriptor = buildClassSerialDescriptor("JsonElementWrapper")

    override fun serialize(encoder: Encoder, value: JsonElementWrapper) {
        val jsonEncoder = encoder as? JsonEncoder ?: throw kotlinx.serialization.SerializationException("This serializer can be used only with JSON")
        val json = jsonEncoder.json
        when (value) {
            is JsonElementWrapper.StringValue -> jsonEncoder.encodeJsonElement(JsonPrimitive(value.value))
            is JsonElementWrapper.ObjectValue -> jsonEncoder.encodeJsonElement(json.encodeToJsonElement(value))
        }
    }

    override fun deserialize(decoder: Decoder): JsonElementWrapper {
        val jsonDecoder = decoder as? JsonDecoder ?: throw kotlinx.serialization.SerializationException("This serializer can be used only with JSON")
        val json = jsonDecoder.json

        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> JsonElementWrapper.StringValue(element.content)
            is JsonObject -> json.decodeFromJsonElement<JsonElementWrapper.ObjectValue>(element)
            else -> throw kotlinx.serialization.SerializationException("Unexpected JSON element type for JsonElementWrapper: $element")
        }
    }
}
