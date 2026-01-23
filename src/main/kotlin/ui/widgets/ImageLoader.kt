package ui.widgets

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.loadImageBitmap
import funlauncher.managers.CacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

object ImageLoader {
    private val inMemoryCache = ConcurrentHashMap<String, ImageBitmap>()
    private lateinit var cacheManager: CacheManager // Will be initialized in main

    fun init(cacheManager: CacheManager) {
        this.cacheManager = cacheManager
    }

    @Composable
    fun rememberImageBitmap(filePath: String?): ImageBitmap? {
        var imageBitmap by remember(filePath) { mutableStateOf<ImageBitmap?>(null) }

        LaunchedEffect(filePath) {
            if (filePath == null) {
                imageBitmap = null
                return@LaunchedEffect
            }

            if (inMemoryCache.containsKey(filePath)) {
                imageBitmap = inMemoryCache[filePath]
                return@LaunchedEffect
            }

            imageBitmap = withContext(Dispatchers.IO) {
                val file = File(filePath)
                if (file.exists()) {
                    try {
                        file.inputStream().use { stream ->
                            loadImageBitmap(stream).also { bitmap ->
                                inMemoryCache[filePath] = bitmap
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                } else {
                    null
                }
            }
        }
        return imageBitmap
    }

    @Composable
    fun rememberImageBitmapFromUrl(url: String?): ImageBitmap? {
        var imageBitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }

        LaunchedEffect(url) {
            if (url == null || url.isBlank()) {
                imageBitmap = null
                return@LaunchedEffect
            }

            if (inMemoryCache.containsKey(url)) {
                imageBitmap = inMemoryCache[url]
                return@LaunchedEffect
            }

            imageBitmap = withContext(Dispatchers.IO) {
                val cacheFile = cacheManager.getImageCacheFile(url)

                if (cacheFile.exists()) {
                    try {
                        cacheFile.inputStream().use { stream ->
                            loadImageBitmap(stream).also { bitmap ->
                                inMemoryCache[url] = bitmap
                            }
                        }
                    } catch (e: Exception) {
                        println("ImageLoader: Error loading image from disk cache for URL: $url. Deleting file. Error: ${e.stackTraceToString()}")
                        cacheFile.delete()
                        null
                    }
                } else {
                    try {
                        val connection = URL(url).openConnection()
                        connection.setRequestProperty("User-Agent", "MateriaKraft-Launcher")
                        connection.getInputStream().use { input ->
                            loadImageBitmap(input).also { bitmap ->
                                inMemoryCache[url] = bitmap
                                cacheFile.outputStream().use { output ->
                                    input.copyTo(output) // Save to disk cache
                                }
                            }
                        }
                    } catch (e: Exception) {
                        println("ImageLoader: Error downloading image from URL: $url. Error: ${e.stackTraceToString()}")
                        null
                    }
                }
            }
        }
        return imageBitmap
    }
}
