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
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeBytes

/**
 * Manages the background caching of version manifests and other remote resources.
 */
class CacheManager(pathManager: PathManager) {
    private val client = Network.client
    private val cacheDir: Path = pathManager.getCacheDir()
    private val json = Json { ignoreUnknownKeys = true }

    private val VANILLA_MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    private val FABRIC_LOADER_MANIFEST_URL = "https://meta.fabricmc.net/v2/versions/loader"

    /**
     * Updates all remote resources and stores them in the local cache.
     * Reports progress via the DownloadManager.
     */
    suspend fun updateAllCaches() {
        val task = DownloadManager.startTask("Обновление списков версий")
        try {
            coroutineScope {
                // Download Vanilla and Fabric manifests in parallel
                val vanillaJob = launch {
                    downloadResource("Vanilla Manifest", VANILLA_MANIFEST_URL, cacheDir.resolve("vanilla_versions.json"))
                    DownloadManager.updateTask(task.id, 0.5f, "Загружен список Vanilla")
                }
                val fabricJob = launch {
                    downloadResource("Fabric Manifest", FABRIC_LOADER_MANIFEST_URL, cacheDir.resolve("fabric_loaders.json"))
                    DownloadManager.updateTask(task.id, 0.5f, "Загружен список Fabric")
                }

                vanillaJob.join()
                fabricJob.join()
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
