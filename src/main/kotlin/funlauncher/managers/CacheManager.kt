package funlauncher.managers

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
    private val imageCacheDir = File(cacheDir, "images").also { it.mkdirs() }

    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    fun getJsonCacheFile(key: String): File {
        val hashedKey = sha256(key)
        return File(cacheDir, "$hashedKey.json")
    }

    fun getImageCacheFile(url: String): File {
        val hashedKey = sha256(url)
        return File(imageCacheDir, hashedKey)
    }

    suspend inline fun <reified T> getOrFetch(key: String, crossinline fetcher: suspend () -> T): T? {
        val cacheFile = getJsonCacheFile(key)

        if (cacheFile.exists()) {
            try {
                val cachedData = json.decodeFromString<T>(cacheFile.readText())
                println("CacheManager: Успешно загружены данные из кэша для ключа: $key")
                return cachedData
            } catch (e: Exception) {
                println("CacheManager: Ошибка чтения или десериализации кэша для ключа: $key. Удаление файла. Ошибка: ${e.stackTraceToString()}")
                cacheFile.delete()
            }
        }

        return try {
            println("CacheManager: Данные для ключа: $key не найдены в кэше или кэш поврежден. Выполняем fetcher().")
            val fetchedData = fetcher()
            cacheFile.writeText(json.encodeToString(fetchedData))
            println("CacheManager: Успешно получены и закэшированы данные для ключа: $key")
            fetchedData
        } catch (e: Exception) {
            println("CacheManager: Ошибка при выполнении fetcher() для ключа: $key. Ошибка: ${e.stackTraceToString()}")
            null
        }
    }

    suspend fun prefetchCaches(vararg prefetchTasks: Pair<String, suspend () -> Any>) = coroutineScope {
        for ((key, fetcher) in prefetchTasks) {
            async {
                getOrFetch(key, fetcher)
            }
        }
    }

    private fun sha256(input: String): String {
        val bytes = input.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
}
