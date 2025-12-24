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
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

object ImageLoader {
    private val client = HttpClient(CIO)

    suspend fun loadAvatar(uuid: String): ImageBitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val response: HttpResponse = client.get("https://crafatar.com/avatars/$uuid?size=64&overlay")
                val inputStream: InputStream = response.bodyAsChannel().toInputStream()
                inputStream.use { loadImageBitmap(it) }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    fun close() {
        client.close()
    }
}

@Composable
fun AvatarImage(account: Account?, modifier: Modifier = Modifier) {
    when (account) {
        is MicrosoftAccount -> {
            var imageBitmap by remember(account.uuid) { mutableStateOf<ImageBitmap?>(null) }

            LaunchedEffect(account.uuid) {
                imageBitmap = ImageLoader.loadAvatar(account.uuid)
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
