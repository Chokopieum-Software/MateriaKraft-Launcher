
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
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
import java.util.jar.JarFile
import kotlin.io.path.*

// === Модели JSON ===
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

    @Serializable data class Artifact(val path: String, val url: String, val size: Long)
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
    private val globalAssetsDir: Path = Paths.get("assets")
    private val globalVersionsDir: Path = Paths.get("versions")
    private val globalLibrariesDir: Path = Paths.get("libraries")
    private val nativesDir: Path = globalVersionsDir.resolve(build.version).resolve("natives")

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; coerceInputValues = true }
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) { requestTimeoutMillis = 30000; connectTimeoutMillis = 15000 }
        defaultRequest { header("User-Agent", "EchoLauncher/1.0 (Linux ARM64)") }
    }

    suspend fun launchOffline(username: String, settings: AppSettings, onProgress: (String, Float) -> Unit) {
        println("=== SYSTEM INFO ===")
        println("OS: ${System.getProperty("os.name")}")
        println("Arch: ${System.getProperty("os.arch")}")
        println("Is ARM64 detected: ${isArm64()}")
        println("===================")

        // Чистим natives перед каждым запуском
        if (nativesDir.exists()) nativesDir.toFile().deleteRecursively()

        listOf(globalAssetsDir, globalLibrariesDir, globalVersionsDir, gameDir, nativesDir).forEach { it.createDirectories() }

        onProgress("Метаданные...", 0.05f)
        val versionInfo = getVersionInfo()

        onProgress("Проверка библиотек...", 0.1f)
        downloadRequiredFiles(versionInfo, onProgress)

        onProgress("Распаковка драйверов...", 0.8f)
        extractNatives(versionInfo)

        onProgress("Запуск...", 0.95f)
        val command = buildLaunchCommand(versionInfo, username, settings)

        println("CMD: ${command.joinToString(" ")}")

        val processBuilder = ProcessBuilder(command)
        processBuilder.directory(gameDir.toFile())
        processBuilder.redirectErrorStream(true)

        if (settings.envVars.isNotBlank()) {
            val env = processBuilder.environment()
            settings.envVars.lines().forEach { line ->
                val parts = line.split("=", limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank()) env[parts[0].trim()] = parts[1].trim()
            }
        }

        val process = processBuilder.start()
        Thread {
            process.inputStream.bufferedReader().use { reader ->
                reader.lines().forEach { println("[MC]: $it") }
            }
        }.start()

        onProgress("Запущено!", 1.0f)
    }

    private suspend fun getVersionInfo(): VersionInfo {
        val manifestUrl = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
        val manifest = try { client.get(manifestUrl).body<VersionManifest>() } catch (e: Exception) { throw Exception("Net Err: ${e.message}") }
        val entry = manifest.versions.find { it.id == build.version } ?: throw Exception("Ver not found")
        val jsonFile = globalVersionsDir.resolve(build.version).resolve("${build.version}.json")
        if (!jsonFile.exists()) { jsonFile.parent.createDirectories(); jsonFile.writeBytes(client.get(entry.url).body()) }
        return json.decodeFromString<VersionInfo>(jsonFile.readText())
    }

    private suspend fun downloadRequiredFiles(info: VersionInfo, onProgress: (String, Float) -> Unit) {
        val clientJar = globalVersionsDir.resolve(info.id).resolve("${info.id}.jar")
        downloadFile(info.downloads.client.url, clientJar)

        val totalLibs = info.libraries.size
        var downloadedCount = 0

        info.libraries.forEach { lib ->
            if (!isLibraryAllowed(lib)) return@forEach

            // 1. Скачиваем JAR с кодом
            lib.downloads?.artifact?.let {
                downloadFile(it.url, globalLibrariesDir.resolve(it.path))
            }

            // 2. Скачиваем Native
            val nativeArtifact = resolveNativeArtifact(lib)
            if (nativeArtifact != null) {
                downloadFile(nativeArtifact.url, globalLibrariesDir.resolve(nativeArtifact.path))
            }

            updateProgress(lib.name.substringBefore(":"), ++downloadedCount, totalLibs, onProgress)
        }

        // Ассеты
        val idxFile = globalAssetsDir.resolve("indexes").resolve("${info.assetIndex.id}.json")
        downloadFile(info.assetIndex.url, idxFile)
        val idx = json.decodeFromString<AssetIndex>(idxFile.readText())
        var ac = 0
        idx.objects.forEach { (_, asset) ->
            val p = asset.hash.substring(0, 2)
            val path = globalAssetsDir.resolve("objects").resolve(p).resolve(asset.hash)
            if (!path.exists() || path.fileSize() != asset.size) {
                downloadFile("https://resources.download.minecraft.net/$p/${asset.hash}", path)
            }
            if (++ac % 50 == 0) updateProgress("Assets", ac, idx.objects.size, onProgress, 0.5f)
        }
    }

    private fun extractNatives(info: VersionInfo) {
        info.libraries.forEach { lib ->
            if (!isLibraryAllowed(lib)) return@forEach

            val nativeArtifact = resolveNativeArtifact(lib) ?: return@forEach
            val jarPath = globalLibrariesDir.resolve(nativeArtifact.path)

            // ФИЛЬТР БЕЗОПАСНОСТИ: Если мы на ARM64, но файл называется как x64 (без arm64), НЕ распаковываем
            if (isArm64() && jarPath.name.contains("natives-linux") && !jarPath.name.contains("arm64")) {
                println("SKIP Extracting x64 artifact on ARM: ${jarPath.name}")
                return@forEach
            }

            if (jarPath.exists()) {
                try {
                    JarFile(jarPath.toFile()).use { jar ->
                        jar.entries().asSequence().forEach { entry ->
                            if (!entry.isDirectory && entry.name.endsWith(".so") && !entry.name.startsWith("META-INF")) {
                                val out = nativesDir.resolve(entry.name.substringAfterLast('/'))
                                Files.copy(jar.getInputStream(entry), out, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                            }
                        }
                    }
                } catch (e: Exception) { println("Err extract ${jarPath.name}: ${e.message}") }
            }
        }
    }

    private fun resolveNativeArtifact(lib: VersionInfo.Library): VersionInfo.Artifact? {
        val osName = getOsName()
        val isArm64 = isArm64()
        val nativeKey = lib.natives?.get(osName) ?: return null

        if (osName == "linux" && isArm64) {
            val armKey = "${nativeKey}-arm64"
            val armArtifact = lib.downloads?.classifiers?.get(armKey)
            if (armArtifact != null) return armArtifact
            return null // Если нет ARM версии, лучше вернуть null, чем x64
        }

        return lib.downloads?.classifiers?.get(nativeKey)
    }

    private fun isLibraryAllowed(lib: VersionInfo.Library): Boolean {
        val rules = lib.rules ?: return true
        var allow = false
        for (rule in rules) {
            if (rule.action == "allow") {
                if (rule.os == null || rule.os.name == getOsName()) allow = true
            } else if (rule.action == "disallow") {
                if (rule.os == null || rule.os.name == getOsName()) allow = false
            }
        }
        if (rules.isEmpty()) return true
        return allow
    }

    private fun buildLaunchCommand(info: VersionInfo, username: String, settings: AppSettings): List<String> {
        val cp = mutableListOf<String>()

        info.libraries.forEach { lib ->
            if (!isLibraryAllowed(lib)) return@forEach

            // 1. Основной JAR
            lib.downloads?.artifact?.let {
                cp.add(globalLibrariesDir.resolve(it.path).toAbsolutePath().toString())
            }

            // 2. Native JAR
            resolveNativeArtifact(lib)?.let { nativeArt ->
                val pathStr = globalLibrariesDir.resolve(nativeArt.path).toAbsolutePath().toString()

                // === ГЛАВНЫЙ ФИЛЬТР БЕЗОПАСНОСТИ ===
                // Если мы на ARM64, но путь содержит "natives-linux" и НЕ содержит "arm64",
                // мы ЗАПРЕЩАЕМ добавлять этот файл в classpath.
                // Это спасет игру от краша.
                if (isArm64() && pathStr.contains("natives-linux") && !pathStr.contains("arm64")) {
                    println("SAFETY FILTER: Removed x64 native from classpath: $pathStr")
                } else {
                    cp.add(pathStr)
                }
            }
        }

        cp.add(globalVersionsDir.resolve(info.id).resolve("${info.id}.jar").toAbsolutePath().toString())

        val args = mutableListOf<String>()
        args.add("java")
        args.add("-Xmx${settings.maxRamMb}M")
        args.add("-Djava.library.path=${nativesDir.toAbsolutePath()}")
        if (settings.javaArgs.isNotBlank()) args.addAll(settings.javaArgs.split(" "))

        args.add("-cp"); args.add(cp.joinToString(File.pathSeparator))
        args.add(info.mainClass)

        if (info.gameArguments.isNotEmpty()) {
            args.addAll(info.gameArguments.split(" "))
        } else {
            args.addAll(listOf("--username", username, "--version", info.id, "--gameDir", gameDir.toAbsolutePath().toString(), "--assetsDir", globalAssetsDir.toAbsolutePath().toString(), "--assetIndex", info.assetIndex.id, "--accessToken", "0", "--userType", "legacy", "--versionType", "release", "--uuid", "00000000-0000-0000-0000-000000000000"))
        }

        return args.map {
            it.replace("\${auth_player_name}", username)
                .replace("\${version_name}", info.id)
                .replace("\${game_directory}", gameDir.toAbsolutePath().toString())
                .replace("\${assets_root}", globalAssetsDir.toAbsolutePath().toString())
                .replace("\${assets_index_name}", info.assetIndex.id)
                .replace("\${auth_uuid}", "00000000-0000-0000-0000-000000000000")
                .replace("\${auth_access_token}", "null")
                .replace("\${user_type}", "legacy")
                .replace("\${version_type}", "release")
        }
    }

    private suspend fun downloadFile(url: String, path: Path) {
        if (!path.exists()) { path.parent.createDirectories(); try { path.writeBytes(client.get(url).body()) } catch (e: Exception) { println("DL Err $url") } }
    }

    private fun updateProgress(item: String, current: Int, total: Int, callback: (String, Float) -> Unit, base: Float = 0.1f) {
        val p = base + (current.toFloat() / total.toFloat()) * 0.4f
        callback("Скачивание: $item", p)
    }

    private fun getOsName(): String { val os = System.getProperty("os.name").lowercase(); return when { os.contains("win") -> "windows"; os.contains("mac") -> "osx"; else -> "linux" } }

    private fun isArm64(): Boolean {
        val arch = System.getProperty("os.arch").lowercase()
        return arch.contains("aarch64") || arch.contains("arm64")
    }
}

// Заглушка сериализатора
object JsonElementWrapperSerializer : KSerializer<JsonElementWrapper> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Wrapper", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: JsonElementWrapper) { }
    override fun deserialize(decoder: Decoder): JsonElementWrapper { return JsonElementWrapper.StringValue(decoder.decodeString()) }
}