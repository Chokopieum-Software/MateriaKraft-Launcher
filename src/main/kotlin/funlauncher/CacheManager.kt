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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.readText
import kotlin.io.path.writeBytes

/**
 * Manages the background caching of version manifests and other remote resources.
 */
class CacheManager(pathManager: PathManager) {
    private val client = Network.client
    private val cacheDir: Path = pathManager.getCacheDir()
    private val json = Json { ignoreUnknownKeys = true }

    private val vanillaManifestUrl = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    private val fabricLoaderManifestUrl = "https://meta.fabricmc.net/v2/versions/loader"
    private val quiltLoaderManifestUrl = "https://meta.quiltmc.org/v3/versions/loader"
    private val neoforgeManifestUrl = "https://maven.neoforged.net/api/maven/versions/releases/net%2Fneoforged%2Fneoforge"
    private val forgeManifestUrl = "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json"


    /**
     * Updates all remote resources and stores them in the local cache.
     * Reports progress via the DownloadManager.
     */
    suspend fun updateAllCaches() {
        val task = DownloadManager.startTask("Обновление списков версий")
        val progress = AtomicReference(0f)

        fun updateProgress(delta: Float, message: String) {
            val newProgress = progress.accumulateAndGet(delta, Float::plus)
            DownloadManager.updateTask(task.id, newProgress, message)
        }

        try {
            coroutineScope {
                val jobs = listOf(
                    launch {
                        downloadResource("Vanilla Manifest", vanillaManifestUrl, cacheDir.resolve("vanilla_versions.json"))
                        updateProgress(0.2f, "Загружен список Vanilla")
                    },
                    launch {
                        downloadResource("Fabric Manifest", fabricLoaderManifestUrl, cacheDir.resolve("fabric_loaders.json"))
                        updateProgress(0.2f, "Загружен список Fabric")
                    },
                    launch {
                        downloadResource("Quilt Manifest", quiltLoaderManifestUrl, cacheDir.resolve("quilt_loaders.json"))
                        updateProgress(0.2f, "Загружен список Quilt")
                    },
                    launch {
                        downloadResource("NeoForge Manifest", neoforgeManifestUrl, cacheDir.resolve("neoforge_versions.json"))
                        updateProgress(0.2f, "Загружен список NeoForge")
                    },
                    launch {
                        downloadResource("Forge Manifest", forgeManifestUrl, cacheDir.resolve("forge_versions.json"))
                        updateProgress(0.2f, "Загружен список Forge")
                    }
                )
                jobs.joinAll()
            }
            DownloadManager.updateTask(task.id, 1.0f, "Списки версий обновлены")
        } catch (e: CancellationException) {
            println("[CacheManager] Cache update was cancelled.")
            DownloadManager.updateTask(task.id, 1.0f, "Обновление отменено")
            throw e // Re-throw CancellationException
        } catch (e: Exception) {
            println("[CacheManager] Failed to update caches: ${e.message}")
            DownloadManager.updateTask(task.id, 1.0f, "Ошибка обновления кэша")
        } finally {
            // Keep the task visible for a moment before ending it
            kotlinx.coroutines.delay(2000)
            DownloadManager.endTask(task.id)
        }
    }

    private suspend fun downloadResource(name: String, url: String, destination: Path) {
        try {
            println("[CacheManager] Updating $name from $url")
            val data = client.get(url).body<ByteArray>()
            destination.parent.toFile().mkdirs()
            destination.writeBytes(data)
            println("[CacheManager] Successfully updated $name")
        } catch (e: Exception) {
            println("[CacheManager] Could not download $name: ${e.message}. Using existing cache if available.")
            // Don't rethrow, as we can often function with a stale cache
        }
    }

    fun getVanillaVersions(): List<String> {
        val manifestFile = cacheDir.resolve("vanilla_versions.json")
        if (!manifestFile.toFile().exists()) return emptyList()

        return try {
            val manifest = json.decodeFromString<VersionManifest>(manifestFile.readText())
            manifest.versions.filter { it.type == "release" }.map { it.id }
        } catch (e: Exception) {
            println("[CacheManager] Failed to parse vanilla versions: ${e.message}")
            emptyList()
        }
    }

    @Serializable
    private data class VersionManifest(val versions: List<VersionEntry>)

    @Serializable
    private data class VersionEntry(val id: String, val type: String, val releaseTime: String)
}
