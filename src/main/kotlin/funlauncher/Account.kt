/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package funlauncher

import kotlinx.serialization.Serializable

@Serializable
sealed class Account {
    abstract val username: String
    abstract val uuid: String?
    abstract val accessToken: String?
    abstract val isLicensed: Boolean
}

@Serializable
data class OfflineAccount(
    override val username: String,
    override val uuid: String? = "00000000-0000-0000-0000-000000000000",
    override val accessToken: String? = "0",
    override val isLicensed: Boolean = false
) : Account()

@Serializable
data class MicrosoftAccount(
    override val username: String,
    override val uuid: String?,
    override val accessToken: String?,
    val skinUrl: String?,
    override val isLicensed: Boolean = true
) : Account()
