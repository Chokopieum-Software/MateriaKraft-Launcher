@file:OptIn(ExperimentalTime::class)
/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package funlauncher

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@Serializable
data class CacheData<T>(
    val timestamp: Long,
    val data: T
)

class CacheManager<T>(private val cacheFileName: String, private val timeToLive: Duration) {

    private val cacheFile: File = PathManager.getAppDataDirectory().resolve(cacheFileName).toFile()
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): T? {
        if (!cacheFile.exists()) return null

        return try {
            val content = cacheFile.readText()
            val cacheData = json.decodeFromString<CacheData<T>>(content)

            if (Instant.now().toEpochMilli() - cacheData.timestamp > timeToLive.inWholeMilliseconds) {
                return null
            }
            
            cacheData.data
        } catch (e: Exception) {
            // If deserialization fails or any other error, treat it as a cache miss
            null
        }
    }

    fun save(data: T) {
        try {
            val cacheData = CacheData(Instant.now().toEpochMilli(), data)
            val content = json.encodeToString(cacheData)
            cacheFile.parentFile.mkdirs()
            cacheFile.writeText(content)
        } catch (e: Exception) {
            // Ignore write errors
        }
    }
}
