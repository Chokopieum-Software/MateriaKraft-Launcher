/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package ui

import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.painterResource
import funlauncher.Account
import funlauncher.MicrosoftAccount
import funlauncher.OfflineAccount
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

object ImageLoader {
    private val client = HttpClient(CIO)
    private val cache = ConcurrentHashMap<String, ImageBitmap>()

    suspend fun loadAvatar(uuid: String?): ImageBitmap? {
        if (uuid == null) return null
        val url = "https://crafatar.com/avatars/$uuid?size=64&overlay"
        return loadImage(url)
    }

    suspend fun loadImage(url: String?): ImageBitmap? {
        if (url == null) return null
        return cache[url] ?: withContext(Dispatchers.IO) {
            try {
                val bytes = client.get(url).body<ByteArray>()
                val bitmap = loadImageBitmap(bytes.inputStream())
                cache[url] = bitmap
                bitmap
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Failed to load image from $url: ${e.message}")
                null
            }
        }
    }

    fun close() {
        client.close()
        cache.clear()
    }
}

@Composable
fun AvatarImage(account: Account?, modifier: Modifier = Modifier) {
    when (account) {
        is MicrosoftAccount -> {
            val imageBitmap by produceState<ImageBitmap?>(initialValue = null, account.uuid) {
                value = ImageLoader.loadAvatar(account.uuid)
            }

            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap!!,
                    contentDescription = "Avatar of ${account.username}",
                    modifier = modifier
                )
            } else {
                // Placeholder while loading or if loading fails
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "Loading Avatar",
                    modifier = modifier
                )
            }
        }
        is OfflineAccount -> {
            Image(
                painter = painterResource("steve_head.png"),
                contentDescription = "Offline Account Avatar",
                modifier = modifier
            )
        }
        else -> {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = "Default Avatar",
                modifier = modifier
            )
        }
    }
}
