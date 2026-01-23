/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package ui.widgets

import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import org.jetbrains.compose.resources.painterResource // Используем painterResource из org.jetbrains.compose.resources
import funlauncher.auth.Account
import funlauncher.auth.MicrosoftAccount
import funlauncher.auth.OfflineAccount

@Composable
fun AvatarImage(account: Account?, modifier: Modifier = Modifier) {
    when (account) {
        is MicrosoftAccount -> {
            val avatarUrl = "https://crafatar.com/avatars/${account.uuid}?size=64&overlay"
            val imageBitmap = ImageLoader.rememberImageBitmapFromUrl(avatarUrl)

            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
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
            // Возвращаем к строковому пути, предполагая, что ресурс находится в папке drawable
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
