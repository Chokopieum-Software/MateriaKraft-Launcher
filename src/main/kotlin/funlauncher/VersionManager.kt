@file:OptIn(ExperimentalTime::class)
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
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime

@Serializable
data class ForgeVersion(
    val mcVersion: String,
    val forgeVersion: String,
    val isRecommended: Boolean = false,
    val isLatest: Boolean = false
)

class VersionManager {

    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
        defaultRequest {
            header("User-Agent", "MateriaKraft Launcher/1.0")
        }
    }

    private val minecraftVersionsCache = CacheManager<List<MinecraftVersion>>("minecraft_versions.json", 1.days)
    private val fabricGameVersionsCache = CacheManager<List<FabricGameVersion>>("fabric_game_versions.json", 1.days)
    private val fabricLoaderVersionsCache = mutableMapOf<String, CacheManager<List<FabricVersion>>>()
    private val forgeVersionsCache = CacheManager<List<ForgeVersion>>("forge_versions.json", 1.days)

    private fun log(message: String) {
        println("[VersionManager] $message")
    }

    suspend fun getMinecraftVersions(forceRefresh: Boolean = false): List<MinecraftVersion> {
        log("Запрос версий Minecraft (forceRefresh=$forceRefresh)")
        if (!forceRefresh) {
            val cached = minecraftVersionsCache.load()
            if (cached != null) {
                log("Возвращаем ${cached.size} кэшированных версий Minecraft.")
                return cached
            }
        }
        val manifestUrl = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
        log("Кэш пуст или требуется обновление. Запрос на $manifestUrl")
        try {
            val manifest = client.get(manifestUrl).body<VersionManifest>()
            val versions = manifest.versions.map { MinecraftVersion(it.id, it.type) }
            minecraftVersionsCache.save(versions)
            log("Получено и кэшировано ${versions.size} версий Minecraft.")
            return versions
        } catch (e: Exception) {
            log("Ошибка при получении версий Minecraft: ${e.message}")
            return emptyList()
        }
    }

    suspend fun getFabricGameVersions(forceRefresh: Boolean = false): List<FabricGameVersion> {
        log("Запрос поддерживаемых Fabric версий игры (forceRefresh=$forceRefresh)")
        if (!forceRefresh) {
            val cached = fabricGameVersionsCache.load()
            if (cached != null) {
                log("Возвращаем ${cached.size} кэшированных версий игр, поддерживаемых Fabric.")
                return cached
            }
        }
        val url = "https://meta.fabricmc.net/v2/versions/game"
        log("Кэш пуст или требуется обновление. Запрос на $url")
        try {
            val versions = client.get(url).body<List<FabricGameVersion>>()
            fabricGameVersionsCache.save(versions)
            log("Получено и кэшировано ${versions.size} версий игр, поддерживаемых Fabric.")
            return versions
        } catch (e: Exception) {
            log("Ошибка при получении поддерживаемых Fabric версий игр: ${e.message}")
            return emptyList()
        }
    }

    suspend fun getFabricLoaderVersions(gameVersion: String, forceRefresh: Boolean = false): List<FabricVersion> {
        log("Запрос версий загрузчика Fabric для Minecraft $gameVersion (forceRefresh=$forceRefresh)")
        val cache = fabricLoaderVersionsCache.getOrPut(gameVersion) {
            CacheManager("fabric_loader_versions_$gameVersion.json", 1.days)
        }
        if (!forceRefresh) {
            val cached = cache.load()
            if (cached != null) {
                log("Возвращаем ${cached.size} кэшированных версий загрузчика для $gameVersion.")
                return cached
            }
        }
        val url = "https://meta.fabricmc.net/v2/versions/loader/$gameVersion"
        log("Кэш пуст или требуется обновление для $gameVersion. Запрос на $url")
        try {
            val response = client.get(url)
            val responseText = response.bodyAsText()

            if (response.status.value != 200) {
                log("API Fabric вернуло статус ${response.status.value} для $gameVersion. Возвращаем пустой список.")
                cache.save(emptyList())
                return emptyList()
            }

            val apiResponse = json.decodeFromString<List<FabricLoaderApiResponse>>(responseText)
            val versions = apiResponse.map { FabricVersion(it.loader.version, it.loader.stable) }

            cache.save(versions)
            log("Получено и кэшировано ${versions.size} версий загрузчика для $gameVersion.")
            return versions

        } catch (e: Exception) {
            log("Критическая ошибка при получении или парсинге версий загрузчика для $gameVersion: ${e.message}. Возвращаем пустой список.")
            cache.save(emptyList())
            return emptyList()
        }
    }

    suspend fun getForgeVersions(forceRefresh: Boolean = false): List<ForgeVersion> {
        log("Запрос версий Forge (forceRefresh=$forceRefresh)")
        if (!forceRefresh) {
            val cached = forgeVersionsCache.load()
            if (cached != null) {
                log("Возвращаем ${cached.size} кэшированных версий Forge.")
                return cached
            }
        }
        val url = "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json"
        log("Кэш пуст или требуется обновление. Запрос на $url")
        try {
            val response = client.get(url).body<JsonObject>()
            val promos = response["promos"] as JsonObject
            val versions = mutableListOf<ForgeVersion>()

            promos.entries.forEach { (key, value) ->
                val mcVersion = key.substringBeforeLast('-')
                val type = key.substringAfterLast('-') // "recommended" or "latest"
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
            forgeVersionsCache.save(versions)
            log("Получено и кэшировано ${versions.size} уникальных версий Forge.")
            return versions
        } catch (e: Exception) {
            log("Ошибка при получении версий Forge: ${e.message}")
            return emptyList()
        }
    }
}
