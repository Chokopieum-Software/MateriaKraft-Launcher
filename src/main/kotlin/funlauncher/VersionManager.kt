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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

@Serializable
data class ForgeVersion(
    val mcVersion: String,
    val forgeVersion: String,
    val isRecommended: Boolean = false,
    val isLatest: Boolean = false
)

class VersionManager(pathManager: PathManager) {

    private val json = Network.json
    private val client = Network.client
    private val cacheDir: Path = pathManager.getCacheDir()

    private fun log(message: String) {
        println("[VersionManager] $message")
    }

    suspend fun getMinecraftVersions(): List<MinecraftVersion> {
        val cacheFile = cacheDir.resolve("vanilla_versions.json")
        if (!cacheFile.exists()) {
            log("Кэш версий Minecraft не найден.")
            return emptyList()
        }
        try {
            val manifest = json.decodeFromString<VersionManifest>(cacheFile.readText())
            return manifest.versions.map { MinecraftVersion(it.id, it.type, it.releaseTime) }
        } catch (e: Exception) {
            log("Ошибка чтения кэша версий Minecraft: ${e.message}")
            return emptyList()
        }
    }

    suspend fun getFabricGameVersions(): List<FabricGameVersion> {
        val url = "https://meta.fabricmc.net/v2/versions/game"
        try {
            return client.get(url).body<List<FabricGameVersion>>()
        } catch (e: Exception) {
            log("Ошибка при получении поддерживаемых Fabric версий игр: ${e.message}")
            return emptyList()
        }
    }

    suspend fun getFabricLoaderVersions(gameVersion: String): List<FabricVersion> {
        val url = "https://meta.fabricmc.net/v2/versions/loader/$gameVersion"
        try {
            val response = client.get(url).body<List<FabricLoaderApiResponse>>()
            return response.map { FabricVersion(it.loader.version, it.loader.stable) }
        } catch (e: Exception) {
            log("Ошибка при получении версий загрузчика Fabric для $gameVersion: ${e.message}")
            return emptyList()
        }
    }

    suspend fun getForgeVersions(): List<ForgeVersion> {
        val cacheFile = cacheDir.resolve("forge_versions.json")
        if (!cacheFile.exists()) {
            log("Кэш версий Forge не найден.")
            return emptyList()
        }
        try {
            val response = json.decodeFromString<JsonObject>(cacheFile.readText())
            val promos = response["promos"] as JsonObject
            val versions = mutableListOf<ForgeVersion>()

            promos.entries.forEach { (key, value) ->
                val mcVersion = key.substringBeforeLast('-')
                val type = key.substringAfterLast('-')
                val forgeVersion = value.jsonPrimitive.content

                val existing = versions.find { it.mcVersion == mcVersion }
                if (existing != null) {
                    versions.remove(existing)
                    versions.add(existing.copy(
                        isRecommended = existing.isRecommended || type == "recommended",
                        isLatest = existing.isLatest || type == "latest"
                    ))
                } else {
                    versions.add(ForgeVersion(
                        mcVersion = mcVersion,
                        forgeVersion = forgeVersion,
                        isRecommended = type == "recommended",
                        isLatest = type == "latest"
                    ))
                }
            }
            return versions
        } catch (e: Exception) {
            log("Ошибка при получении версий Forge: ${e.message}")
            return emptyList()
        }
    }

    suspend fun getQuiltGameVersions(): List<QuiltGameVersion> {
        val url = "https://meta.quiltmc.org/v3/versions/game"
        try {
            return client.get(url).body<List<QuiltGameVersion>>()
        } catch (e: Exception) {
            log("Ошибка при получении поддерживаемых Quilt версий игр: ${e.message}")
            return emptyList()
        }
    }

    suspend fun getQuiltLoaderVersions(gameVersion: String): List<QuiltVersion> {
        val url = "https://meta.quiltmc.org/v3/versions/loader/$gameVersion"
        try {
            val response = client.get(url).body<List<QuiltLoaderApiResponse>>()
            return response.map { QuiltVersion(it.loader.version) }
        } catch (e: Exception) {
            log("Ошибка при получении версий загрузчика Quilt для $gameVersion: ${e.message}")
            return emptyList()
        }
    }

    suspend fun getNeoForgeVersions(): List<NeoForgeVersion> {
        val cacheFile = cacheDir.resolve("neoforge_versions.json")
        if (!cacheFile.exists()) {
            log("Кэш версий NeoForge не найден.")
            return emptyList()
        }
        try {
            val response = json.decodeFromString<NeoForgeApiResponse>(cacheFile.readText())
            return response.versions.mapNotNull { neoVersion ->
                getMcVersionFromNeoForgeVersion(neoVersion)?.let { mcVersion ->
                    NeoForgeVersion(mcVersion, neoVersion)
                }
            }
        } catch (e: Exception) {
            log("Ошибка при получении версий NeoForge: ${e.message}")
            return emptyList()
        }
    }

    private fun getMcVersionFromNeoForgeVersion(versionString: String): String? {
        if (versionString.startsWith("0")) return null // April Fools versions

        val parts = versionString.split('.')
        return try {
            if (parts.first().toInt() >= 26) {
                var mcVersion = "${parts[0]}.${parts[1]}"
                if (parts[2] != "0") {
                    mcVersion += ".${parts[2]}"
                }
                val snapshotIdentifier = versionString.split('+').getOrNull(1)
                if (snapshotIdentifier != null) {
                    mcVersion += "-$snapshotIdentifier"
                }
                mcVersion
            } else {
                "1.${parts[0]}.${parts[1]}"
            }
        } catch (e: NumberFormatException) {
            null
        }
    }
}
