package funlauncher

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

class CacheManager(
    pathManager: PathManager
) {
    private val cacheDir = pathManager.getCacheDir().toFile()
    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    fun getCacheFile(key: String): File {
        val hashedKey = sha256(key)
        return File(cacheDir, "$hashedKey.json")
    }

    suspend inline fun <reified T> getOrFetch(key: String, crossinline fetcher: suspend () -> T): T? {
        val cacheFile = getCacheFile(key)

        if (cacheFile.exists()) {
            try {
                val cachedData = json.decodeFromString<T>(cacheFile.readText())
                return cachedData
            } catch (_: Exception) {
                // Log error or handle corrupted cache
                cacheFile.delete()
            }
        }

        return try {
            val fetchedData = fetcher()
            cacheFile.writeText(json.encodeToString(fetchedData))
            fetchedData
        } catch (_: Exception) {
            // Handle fetch error
            null
        }
    }

    suspend fun updateAllCaches() = coroutineScope {
        // Здесь можно добавить логику для обновления всех кэшей
        // Например, для популярных запросов Modrinth
        val popularModsJob = async {
            getOrFetch("modrinth_search_popular_mods") {
                // Эта логика теперь будет в ModrinthApi
            }
        }
        val popularModpacksJob = async {
            getOrFetch("modrinth_search_popular_modpacks") {
                // Эта логика теперь будет в ModrinthApi
            }
        }
        // Дождитесь выполнения всех задач
        popularModsJob.await()
        popularModpacksJob.await()
    }

    private fun sha256(input: String): String {
        val bytes = input.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
}
