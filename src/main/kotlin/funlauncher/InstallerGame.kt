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
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger
import java.util.jar.JarFile
import kotlin.io.path.*
import kotlin.time.measureTime

// === Модели JSON ===
// (Оставляем как есть, они нужны для парсинга)
private var connectRetryAttempts: Int=3

@Serializable data class VersionManifest(val versions: List<VersionEntry>) {
    @Serializable data class VersionEntry(val id: String, val type: String, val url: String)
}

@Serializable data class VersionInfo(
    val id: String,
    val libraries: List<Library>,
    val mainClass: String,
    @SerialName("minecraftArguments") val gameArguments: String = "",
    val assets: String,
    val downloads: Downloads,
    val assetIndex: AssetIndexInfo
) {
    @Serializable data class Library(
        val name: String,
        val downloads: LibraryDownloads? = null,
        val natives: Map<String, String>? = null,
        val rules: List<Rule>? = null
    )

    @Serializable data class LibraryDownloads(
        val artifact: Artifact? = null,
        val classifiers: Map<String, Artifact>? = null
    )

    @Serializable data class Artifact(val path: String, val url: String, val size: Long, val sha1: String? = null)
    @Serializable data class Downloads(val client: ClientDownload)
    @Serializable data class ClientDownload(val url: String)
    @Serializable data class AssetIndexInfo(val id: String, val url: String)

    @Serializable data class Rule(val action: String, val os: OS? = null)
    @Serializable data class OS(val name: String)
}

@Serializable data class AssetIndex(val objects: Map<String, AssetObject>) {
    @Serializable data class AssetObject(val hash: String, val size: Long)
}

@Serializable(with = JsonElementWrapperSerializer::class)
sealed class JsonElementWrapper {
    data class StringValue(val value: String) : JsonElementWrapper()
    data class ObjectValue(val value: String) : JsonElementWrapper()
}

// === Installer ===

class MinecraftInstaller(private val build: MinecraftBuild) {
    private val gameDir: Path = Paths.get(build.installPath)
    private val launcherDataDir: Path = PathManager.getAppDataDirectory()
    private val globalAssetsDir: Path = launcherDataDir.resolve("assets")
    private val globalVersionsDir: Path = launcherDataDir.resolve("versions")
    private val globalLibrariesDir: Path = launcherDataDir.resolve("libraries")
    private val nativesDir: Path = globalVersionsDir.resolve(build.version).resolve("natives")

    private val MAVEN_CENTRAL = "https://repo1.maven.org/maven2/"

    // Принудительное использование новой библиотеки
    private val ARM_LWJGL_VERSION = "3.3.3"

    private val isArm64Arch: Boolean by lazy {
        val arch = System.getProperty("os.arch").lowercase()
        arch.contains("aarch64") || arch.contains("arm64")
    }

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; coerceInputValues = true }
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 60000
            connectTimeoutMillis = 30000
        }
        engine {
            maxConnectionsCount = 1000
            endpoint {
                maxConnectionsPerRoute = 50
                connectAttempts = 3
            }
        }
        defaultRequest {
            header("User-Agent", "MinecraftLauncher/1.0 (Linux; ${System.getProperty("os.arch")})")
        }
    }

    private val logChannel = Channel<String>(Channel.UNLIMITED)

    init {
        CoroutineScope(Dispatchers.IO).launch {
            for (message in logChannel) {
                println("[LOG] $message")
            }
        }
    }

    private fun log(message: String) {
        logChannel.trySend(message)
    }

    suspend fun launchOffline(
        username: String,
        javaPath: String,
        maxRamMb: Int,
        javaArgs: String,
        envVars: String,
        showConsole: Boolean
    ): Process {
        val task = DownloadManager.startTask("Minecraft ${build.version}")
        try {
            log("=== SYSTEM INFO ===")
            log("OS: ${System.getProperty("os.name")}")
            log("Arch: ${System.getProperty("os.arch")}")
            log("Is ARM64 Mode: $isArm64Arch")
            log("Target LWJGL for ARM: $ARM_LWJGL_VERSION")
            log("Using Java: $javaPath")
            log("===================")

            if (nativesDir.exists()) nativesDir.toFile().deleteRecursively()

            listOf(launcherDataDir, globalAssetsDir, globalLibrariesDir, globalVersionsDir, gameDir, nativesDir).forEach {
                it.createDirectories()
            }

            DownloadManager.updateTask(task.id, 0.05f, "Получение метаданных...")
            val versionInfo = getVersionInfo()

            DownloadManager.updateTask(task.id, 0.1f, "Загрузка файлов...")
            val downloadTime = measureTime {
                downloadRequiredFiles(versionInfo) { progress, status ->
                    DownloadManager.updateTask(task.id, progress, status)
                }
            }
            log("Downloads finished in ${downloadTime.inWholeSeconds}s")

            DownloadManager.updateTask(task.id, 0.9f, "Распаковка...")
            extractNatives(versionInfo)

            DownloadManager.updateTask(task.id, 1.0f, "Запуск...")
            val command = buildLaunchCommand(versionInfo, username, javaPath, maxRamMb, javaArgs)

            log("CMD length: ${command.size}")

            val processBuilder = ProcessBuilder(command)
            processBuilder.directory(gameDir.toFile())
            processBuilder.redirectErrorStream(true)

            if (envVars.isNotBlank()) {
                val env = processBuilder.environment()
                envVars.lines().forEach { line ->
                    val parts = line.split("=", limit = 2)
                    if (parts.size == 2 && parts[0].isNotBlank()) env[parts[0].trim()] = parts[1].trim()
                }
            }

            val process = processBuilder.start()
            
            if (showConsole) {
                GameConsole.clear()
                Thread {
                    process.inputStream.bufferedReader().use { reader ->
                        reader.lines().forEach { GameConsole.addLog(it) }
                    }
                }.start()
            }

            return process
        } finally {
            DownloadManager.endTask(task.id)
        }
    }

    private suspend fun getVersionInfo(): VersionInfo {
        val manifestUrl = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
        val manifest = client.get(manifestUrl).body<VersionManifest>()

        val entry = manifest.versions.find { it.id == build.version }
            ?: throw Exception("Version '${build.version}' not found")

        val jsonFile = globalVersionsDir.resolve(build.version).resolve("${build.version}.json")
        if (!jsonFile.exists()) {
            jsonFile.parent.createDirectories()
            val bytes = client.get(entry.url).body<ByteArray>()
            jsonFile.writeBytes(bytes)
        }

        return json.decodeFromString<VersionInfo>(jsonFile.readText())
    }

    private suspend fun downloadRequiredFiles(info: VersionInfo, onProgress: (Float, String) -> Unit) {
        val clientJar = globalVersionsDir.resolve(info.id).resolve("${info.id}.jar")
        downloadFile(info.downloads.client.url, clientJar, "Client JAR")

        val libraries = info.libraries.filter { isLibraryAllowed(it) }
        val semaphore = Semaphore(20)
        val total = libraries.size + 1
        val counter = AtomicInteger(0)

        libraries.map { lib ->
            CoroutineScope(Dispatchers.IO).async {
                semaphore.acquire()
                try {
                    downloadLibrary(lib)
                } finally {
                    semaphore.release()
                    val c = counter.incrementAndGet()
                    onProgress(0.1f + (c.toFloat() / total) * 0.4f, "Libs: ${lib.name.substringBefore(":")}")
                }
            }
        }.awaitAll()

        val idxFile = globalAssetsDir.resolve("indexes").resolve("${info.assetIndex.id}.json")
        downloadFile(info.assetIndex.url, idxFile, "Asset Index")
        val idx = json.decodeFromString<AssetIndex>(idxFile.readText())

        downloadAssetsInParallel(idx, onProgress)
    }

    // === КЛЮЧЕВАЯ ЛОГИКА ПОДМЕНЫ ВЕРСИЙ ===

    // Если лаунчер на ARM64 и это LWJGL - возвращаем новую версию (например 3.3.3)
    // В противном случае возвращаем оригинальное имя из JSON
    private fun processLwjglVersion(libName: String): String {
        if (!isArm64Arch) return libName
        if (!isLwjglLibrary(libName)) return libName

        val parts = libName.split(":")
        if (parts.size != 3) return libName

        // Если это LWJGL и версия старая (начинается на 3.2. или 3.1.), меняем на ARM_LWJGL_VERSION
        if (parts[2].startsWith("3.2.") || parts[2].startsWith("3.1.")) {
            // Возвращаем: group:artifact:3.3.3
            return "${parts[0]}:${parts[1]}:$ARM_LWJGL_VERSION"
        }

        return libName
    }

    // Генерирует Artifact объект на основе имени библиотеки
    private fun getArtifactForLibrary(lib: VersionInfo.Library, isNative: Boolean): VersionInfo.Artifact? {
        val originalName = lib.name
        val isLwjgl = isLwjglLibrary(originalName)

        // --- НОВАЯ ЛОГИКА ---
        // Если это Linux ARM64 и библиотека LWJGL, принудительно используем Maven
        if (isArm64Arch && getOsName() == "linux" && isLwjgl) {
            val effectiveName = processLwjglVersion(originalName)
            val classifier = if (isNative) "natives-linux-arm64" else null
            return generateMavenArtifact(effectiveName, classifier)
        }
        // --- КОНЕЦ НОВОЙ ЛОГИКИ ---

        // Старая логика для всех остальных случаев
        if (isNative) {
            val nativeKey = lib.natives?.get(getOsName()) ?: return null
            return lib.downloads?.classifiers?.get(nativeKey)
        } else {
            return lib.downloads?.artifact
        }
    }


    private fun generateMavenArtifact(libName: String, classifier: String?): VersionInfo.Artifact {
        val parts = libName.split(":")
        val group = parts[0]
        val artifactId = parts[1]
        val version = parts[2]

        val groupPath = group.replace('.', '/')
        val fileName = if (classifier != null) "$artifactId-$version-$classifier.jar" else "$artifactId-$version.jar"
        val path = "$groupPath/$artifactId/$version/$fileName"

        return VersionInfo.Artifact(path, "$MAVEN_CENTRAL$path", 0)
    }

    private suspend fun downloadLibrary(lib: VersionInfo.Library) {
        // 1. Скачиваем JAR (возможно обновленной версии)
        getArtifactForLibrary(lib, isNative = false)?.let { artifact ->
            val path = globalLibrariesDir.resolve(artifact.path)
            // Пропускаем проверку хеша, если это наша сгенерированная ссылка
            val skipHash = artifact.url.startsWith(MAVEN_CENTRAL)
            downloadFile(artifact.url, path, "Lib: ${lib.name}", validateHash = !skipHash, expectedHash = artifact.sha1)
        }

        // 2. Скачиваем Natives (возможно обновленной версии)
        getArtifactForLibrary(lib, isNative = true)?.let { artifact ->
            val path = globalLibrariesDir.resolve(artifact.path)
            val skipHash = artifact.url.startsWith(MAVEN_CENTRAL)
            downloadFile(artifact.url, path, "Native: ${lib.name}", validateHash = !skipHash, expectedHash = artifact.sha1)
        }
    }

    private fun extractNatives(info: VersionInfo) {
        log("Extracting natives...")
        info.libraries.forEach { lib ->
            if (!isLibraryAllowed(lib)) return@forEach

            // Используем ту же логику получения артефакта, что и при скачивании
            val nativeArtifact = getArtifactForLibrary(lib, isNative = true) ?: return@forEach
            val jarPath = globalLibrariesDir.resolve(nativeArtifact.path)

            if (jarPath.exists()) {
                try {
                    JarFile(jarPath.toFile()).use { jar ->
                        jar.entries().asSequence().forEach { entry ->
                            if (!entry.isDirectory && entry.name.endsWith(".so") && !entry.name.contains("META-INF")) {
                                val outFile = nativesDir.resolve(entry.name.substringAfterLast('/'))
                                Files.copy(jar.getInputStream(entry), outFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                            }
                        }
                    }
                } catch (e: Exception) {
                    log("Failed to extract ${jarPath.name}: ${e.message}")
                }
            }
        }
    }

    private fun buildLaunchCommand(
        info: VersionInfo,
        username: String,
        javaPath: String,
        maxRamMb: Int,
        javaArgs: String
    ): List<String> {
        val cp = mutableListOf<String>()

        info.libraries.forEach { lib ->
            if (!isLibraryAllowed(lib)) return@forEach

            // Добавляем JAR в classpath. Важно: используем getArtifactForLibrary,
            // чтобы путь указывал на новую версию (3.3.3), а не на старую (3.2.2)
            getArtifactForLibrary(lib, isNative = false)?.let {
                cp.add(globalLibrariesDir.resolve(it.path).toAbsolutePath().toString())
            }

            // Некоторые библиотеки требуют добавления native jar в cp
            getArtifactForLibrary(lib, isNative = true)?.let {
                cp.add(globalLibrariesDir.resolve(it.path).toAbsolutePath().toString())
            }
        }

        cp.add(globalVersionsDir.resolve(info.id).resolve("${info.id}.jar").toAbsolutePath().toString())

        val args = mutableListOf<String>()
        args.add(javaPath.ifBlank { "java" }) // Используем путь из настроек

        val javaInfo = JavaManager().getJavaInfo(javaPath)
        if (javaInfo != null && javaInfo.version >= 17) {
            args.add("--enable-native-access=ALL-UNNAMED")
        }

        args.add("-Xmx${maxRamMb}M")
        args.add("-Djava.library.path=${nativesDir.toAbsolutePath()}")
        args.add("-Dorg.lwjgl.librarypath=${nativesDir.toAbsolutePath()}")

        if (javaArgs.isNotBlank()) args.addAll(javaArgs.split(" "))

        args.add("-cp")
        args.add(cp.joinToString(File.pathSeparator))
        args.add(info.mainClass)

        val gameArgs = if (info.gameArguments.isNotEmpty()) {
            info.gameArguments.split(" ")
        } else {
            listOf(
                "--username", "\${auth_player_name}",
                "--version", "\${version_name}",
                "--gameDir", "\${game_directory}",
                "--assetsDir", "\${assets_root}",
                "--assetIndex", "\${assets_index_name}",
                "--uuid", "\${auth_uuid}",
                "--accessToken", "\${auth_access_token}",
                "--userType", "\${user_type}",
                "--versionType", "\${version_type}"
            )
        }

        val finalArgs = gameArgs.map { arg ->
            arg.replace("\${auth_player_name}", username)
                .replace("\${version_name}", info.id)
                .replace("\${game_directory}", gameDir.toAbsolutePath().toString())
                .replace("\${assets_root}", globalAssetsDir.toAbsolutePath().toString())
                .replace("\${assets_index_name}", info.assetIndex.id)
                .replace("\${auth_uuid}", "00000000-0000-0000-0000-000000000000")
                .replace("\${auth_access_token}", "null")
                .replace("\${user_type}", "legacy")
                .replace("\${version_type}", "release")
        }
        args.addAll(finalArgs)

        return args
    }

    private suspend fun downloadFile(
        url: String,
        path: Path,
        desc: String,
        validateHash: Boolean = false,
        expectedHash: String? = null
    ) {
        if (path.exists()) {
            if (validateHash && expectedHash != null) {
                if (path.fileSize() > 0) return
            } else if (path.fileSize() > 0) {
                return
            }
        }

        try {
            path.parent.createDirectories()
            val resp = client.get(url)
            if (resp.status.value == 200) {
                path.writeBytes(resp.body())
            } else {
                throw Exception("HTTP ${resp.status.value}")
            }
        } catch (e: Exception) {
            log("Error downloading $desc from $url: ${e.message}")
            runCatching { path.deleteIfExists() }
            if (!url.contains(MAVEN_CENTRAL)) throw e
        }
    }

    private suspend fun downloadAssetsInParallel(idx: AssetIndex, onProgress: (Float, String) -> Unit) {
        val assets = idx.objects.entries.toList()
        val semaphore = Semaphore(50)
        val counter = AtomicInteger(0)

        assets.map { (name, asset) ->
            CoroutineScope(Dispatchers.IO).async {
                semaphore.acquire()
                try {
                    val p = asset.hash.substring(0, 2)
                    val path = globalAssetsDir.resolve("objects").resolve(p).resolve(asset.hash)
                    val url = "https://resources.download.minecraft.net/$p/${asset.hash}"

                    if (!path.exists() || path.fileSize() != asset.size) {
                        downloadFile(url, path, "Asset", validateHash = false)
                    }
                    val c = counter.incrementAndGet()
                    if (c % 100 == 0) onProgress(0.5f + (c.toFloat() / assets.size) * 0.4f, "Assets: $c/${assets.size}")
                } finally {
                    semaphore.release()
                }
            }
        }.awaitAll()
    }

    private fun isLibraryAllowed(lib: VersionInfo.Library): Boolean {
        val rules = lib.rules ?: return true
        if (rules.isEmpty()) return true
        var allow = false
        var hasMatchingRule = false
        for (rule in rules) {
            if (rule.os == null || rule.os.name == getOsName()) {
                allow = rule.action == "allow"
                hasMatchingRule = true
            }
        }
        return if (hasMatchingRule) allow else false
    }

    private fun isLwjglLibrary(name: String): Boolean {
        return name.contains("lwjgl") || name.contains("java-objc-bridge")
    }

    private fun getOsName(): String {
        val os = System.getProperty("os.name").lowercase()
        return when {
            os.contains("win") -> "windows"
            os.contains("mac") -> "osx"
            else -> "linux"
        }
    }
}

object JsonElementWrapperSerializer : KSerializer<JsonElementWrapper> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Wrapper", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: JsonElementWrapper) { }
    override fun deserialize(decoder: Decoder): JsonElementWrapper {
        return JsonElementWrapper.StringValue(decoder.decodeString())
    }
}
