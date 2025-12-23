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

    private var cachedMinecraftVersions: List<MinecraftVersion>? = null
    private var cachedFabricGameVersions: List<FabricGameVersion>? = null
    private val cachedFabricLoaderVersions = mutableMapOf<String, List<FabricVersion>>()
    private var cachedForgeVersions: List<ForgeVersion>? = null

    private fun log(message: String) {
        println("[VersionManager] $message")
    }

    suspend fun getMinecraftVersions(forceRefresh: Boolean = false): List<MinecraftVersion> {
        log("Запрос версий Minecraft (forceRefresh=$forceRefresh)")
        if (cachedMinecraftVersions != null && !forceRefresh) {
            log("Возвращаем ${cachedMinecraftVersions!!.size} кэшированных версий Minecraft.")
            return cachedMinecraftVersions!!
        }
        val manifestUrl = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
        log("Кэш пуст или требуется обновление. Запрос на $manifestUrl")
        try {
            val manifest = client.get(manifestUrl).body<VersionManifest>()
            cachedMinecraftVersions = manifest.versions.map { MinecraftVersion(it.id, it.type) }
            log("Получено и кэшировано ${cachedMinecraftVersions!!.size} версий Minecraft.")
            return cachedMinecraftVersions!!
        } catch (e: Exception) {
            log("Ошибка при получении версий Minecraft: ${e.message}")
            return emptyList()
        }
    }

    suspend fun getFabricGameVersions(forceRefresh: Boolean = false): List<FabricGameVersion> {
        log("Запрос поддерживаемых Fabric версий игры (forceRefresh=$forceRefresh)")
        if (cachedFabricGameVersions != null && !forceRefresh) {
            log("Возвращаем ${cachedFabricGameVersions!!.size} кэшированных версий игр, поддерживаемых Fabric.")
            return cachedFabricGameVersions!!
        }
        val url = "https://meta.fabricmc.net/v2/versions/game"
        log("Кэш пуст или требуется обновление. Запрос на $url")
        try {
            val versions = client.get(url).body<List<FabricGameVersion>>()
            cachedFabricGameVersions = versions
            log("Получено и кэшировано ${versions.size} версий игр, поддерживаемых Fabric.")
            return versions
        } catch (e: Exception) {
            log("Ошибка при получении поддерживаемых Fabric версий игр: ${e.message}")
            return emptyList()
        }
    }

    suspend fun getFabricLoaderVersions(gameVersion: String, forceRefresh: Boolean = false): List<FabricVersion> {
        log("Запрос версий загрузчика Fabric для Minecraft $gameVersion (forceRefresh=$forceRefresh)")
        if (cachedFabricLoaderVersions.containsKey(gameVersion) && !forceRefresh) {
            val cachedVersions = cachedFabricLoaderVersions[gameVersion]!!
            log("Возвращаем ${cachedVersions.size} кэшированных версий загрузчика для $gameVersion.")
            return cachedVersions
        }
        val url = "https://meta.fabricmc.net/v2/versions/loader/$gameVersion"
        log("Кэш пуст или требуется обновление для $gameVersion. Запрос на $url")
        try {
            val response = client.get(url)
            val responseText = response.bodyAsText()

            if (response.status.value != 200) {
                log("API Fabric вернуло статус ${response.status.value} для $gameVersion. Возвращаем пустой список.")
                cachedFabricLoaderVersions[gameVersion] = emptyList()
                return emptyList()
            }

            // Парсим сложный ответ от сервера
            val apiResponse = json.decodeFromString<List<FabricLoaderApiResponse>>(responseText)
            // Преобразуем его в наш чистый внутренний формат
            val versions = apiResponse.map { FabricVersion(it.loader.version, it.loader.stable) }

            cachedFabricLoaderVersions[gameVersion] = versions
            log("Получено и кэшировано ${versions.size} версий загрузчика для $gameVersion.")
            return versions

        } catch (e: Exception) {
            log("Критическая ошибка при получении или парсинге версий загрузчика для $gameVersion: ${e.message}. Возвращаем пустой список.")
            cachedFabricLoaderVersions[gameVersion] = emptyList()
            return emptyList()
        }
    }

    suspend fun getForgeVersions(forceRefresh: Boolean = false): List<ForgeVersion> {
        log("Запрос версий Forge (forceRefresh=$forceRefresh)")
        if (cachedForgeVersions != null && !forceRefresh) {
            log("Возвращаем ${cachedForgeVersions!!.size} кэшированных версий Forge.")
            return cachedForgeVersions!!
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
            cachedForgeVersions = versions
            log("Получено и кэшировано ${versions.size} уникальных версий Forge.")
            return versions
        } catch (e: Exception) {
            log("Ошибка при получении версий Forge: ${e.message}")
            return emptyList()
        }
    }
}
