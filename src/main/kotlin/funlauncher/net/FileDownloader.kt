/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package funlauncher.net

import funlauncher.BuildType
import funlauncher.MinecraftBuild
import funlauncher.game.AssetIndex
import funlauncher.game.VersionInfo
import funlauncher.game.VersionMetadataFetcher
import funlauncher.managers.BuildManager
import funlauncher.managers.PathManager
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.*

/**
 * Handles downloading of game files, including libraries and assets.
 */
class FileDownloader(
    private val versionInfo: VersionInfo,
    private val pathManager: PathManager,
    private val buildManager: BuildManager
) {
    private val client = Network.client
    private val json = Network.json

    private val globalAssetsDir: Path = pathManager.getGlobalAssetsDir()
    private val globalVersionsDir: Path = pathManager.getGlobalVersionsDir()
    private val globalLibrariesDir: Path = pathManager.getGlobalLibrariesDir()

    private val availableCores = Runtime.getRuntime().availableProcessors()
    private val librarySemaphore = Semaphore(availableCores * 4)
    private val assetSemaphore = Semaphore(availableCores * 8)

    private fun log(message: String) = println("[FileDownloader] $message")

    suspend fun downloadRequiredFiles(build: MinecraftBuild, onProgress: (Float, String) -> Unit) {
        // 1. Download Client JAR
        val gameVersionForJar = getGameVersionForJar(build)
        val clientJarPath = globalVersionsDir.resolve(gameVersionForJar).resolve("$gameVersionForJar.jar")
        if (!clientJarPath.exists()) {
            // This needs the vanilla info, which we assume is already fetched.
            // A better approach would be to pass the vanilla info URL directly.
            val vanillaInfoForJar = VersionMetadataFetcher(buildManager, pathManager).getVersionInfo(build.copy(type = BuildType.VANILLA, version = gameVersionForJar))
            downloadFile(vanillaInfoForJar.downloads.client.url, clientJarPath, "Client JAR")
        }

        // 2. Download Libraries
        val libraries = versionInfo.libraries.filter { isRuleApplicable(it.rules ?: emptyList()) }
        val libCounter = AtomicInteger(0)
        libraries.map { lib ->
            CoroutineScope(Dispatchers.IO).async {
                librarySemaphore.acquire()
                try {
                    downloadLibrary(lib)
                } finally {
                    librarySemaphore.release()
                    val c = libCounter.incrementAndGet()
                    onProgress(c.toFloat() / libraries.size * 0.5f, "Libs: ${lib.name.substringAfterLast(':')}")
                }
            }
        }.awaitAll()

        // 3. Download Asset Index and Assets
        val idxFile = globalAssetsDir.resolve("indexes").resolve("${versionInfo.assetIndex.id}.json")
        downloadFile(versionInfo.assetIndex.url, idxFile, "Asset Index")
        val idx = json.decodeFromString<AssetIndex>(idxFile.readText())
        downloadAssetsInParallel(idx) { progress, status -> onProgress(0.5f + progress * 0.5f, status) }
    }

    private suspend fun downloadLibrary(lib: VersionInfo.Library) {
        val mavenCentral = "https://repo1.maven.org/maven2/"
        // Main artifact
        lib.downloads?.artifact?.let { artifact ->
            val path = globalLibrariesDir.resolve(artifact.path)
            downloadFile(artifact.url, path, "Lib: ${lib.name}")
        } ?: run {
            val artifactPath = getArtifactPath(lib.name)
            val path = globalLibrariesDir.resolve(artifactPath)
            val url = (lib.url ?: mavenCentral) + artifactPath
            downloadFile(url, path, "Lib: ${lib.name}")
        }

        // Native artifact handling
        if (lib.natives != null) {
            if (getOsName() == "linux" && getArch() == "arm64" && lib.name.startsWith("org.lwjgl")) {
                // Force download only ARM64 natives for LWJGL on Linux ARM
                val lwjglVersion = lib.name.split(":")[2]
                val artifactName = lib.name.split(":")[1]
                val classifier = "natives-linux-arm64"
                val artifactPath = getArtifactPath("org.lwjgl:$artifactName:$lwjglVersion", classifier)
                val path = globalLibrariesDir.resolve(artifactPath)
                val url = (lib.url ?: mavenCentral) + artifactPath
                downloadFile(url, path, "Native (ARM): ${lib.name}")
            } else {
                // Default native artifact logic for other OS/Arch
                lib.natives.get(getOsName())?.let { classifierTemplate ->
                    val classifier = classifierTemplate.replace("\${arch}", getArch())
                    lib.downloads?.classifiers?.get(classifier)?.let { nativeArtifact ->
                        val path = globalLibrariesDir.resolve(nativeArtifact.path)
                        downloadFile(nativeArtifact.url, path, "Native: ${lib.name}")
                    }
                }
            }
        }
    }

    private suspend fun downloadAssetsInParallel(idx: AssetIndex, onProgress: (Float, String) -> Unit) {
        val assets = idx.objects.entries.toList()
        val counter = AtomicInteger(0)
        assets.map { (_, asset) ->
            CoroutineScope(Dispatchers.IO).async {
                assetSemaphore.acquire()
                try {
                    val p = asset.hash.substring(0, 2)
                    val path = globalAssetsDir.resolve("objects").resolve(p).resolve(asset.hash)
                    if (!path.exists() || path.fileSize() != asset.size) {
                        downloadFile("https://resources.download.minecraft.net/$p/${asset.hash}", path, "Asset")
                    }
                    val c = counter.incrementAndGet()
                    if (c % 100 == 0) onProgress(c.toFloat() / assets.size, "Assets: $c/${assets.size}")
                } finally {
                    assetSemaphore.release()
                }
            }
        }.awaitAll()
    }

    private suspend fun downloadFile(url: String, path: Path, desc: String) {
        if (path.exists() && path.fileSize() > 0) return

        val maxRetries = 3
        val retryDelay = 3000L

        for (attempt in 1..maxRetries) {
            try {
                log("Downloading $desc (attempt $attempt/$maxRetries)...")
                path.parent.createDirectories()
                val resp = client.get(url)

                if (resp.status.value == 200) {
                    path.writeBytes(resp.body())
                    log("Successfully downloaded $desc")
                    return
                }
                throw Exception("HTTP ${resp.status.value}")
            } catch (e: Exception) {
                runCatching { path.deleteIfExists() }
                log("Error downloading $desc from $url: ${e.message}")
                if (attempt < maxRetries) {
                    delay(retryDelay)
                } else {
                    throw e
                }
            }
        }
    }

    private fun getArtifactPath(name: String, classifier: String? = null): String {
        val parts = name.split(':')
        val groupPath = parts[0].replace('.', '/')
        val artifactName = parts[1]
        val version = parts[2]
        val classifierStr = if (classifier != null) "-$classifier" else ""
        return "$groupPath/$artifactName/$version/$artifactName-$version$classifierStr.jar"
    }
    
    private fun getGameVersionForJar(build: MinecraftBuild): String = when (build.type) {
        BuildType.FABRIC -> build.version.split("-fabric-").first()
        BuildType.FORGE -> build.version.split("-forge-").first()
        BuildType.QUILT -> build.version.split("-quilt-").first()
        BuildType.NEOFORGE -> build.version.split("-neoforge-").first()
        else -> versionInfo.id
    }

    private fun isRuleApplicable(rules: List<VersionInfo.Rule>): Boolean {
        if (rules.isEmpty()) return true
        var finalAction = "allow" // Default to allow if no specific rule matches

        for (rule in rules) {
            val osRule = rule.os
            if (osRule?.name == getOsName()) {
                finalAction = rule.action
            } else if (osRule == null) {
                // Rule applies to all OS, but might be overridden by a more specific rule later
                finalAction = rule.action
            }
        }
        return finalAction == "allow"
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
