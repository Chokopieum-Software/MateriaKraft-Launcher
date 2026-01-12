package funlauncher.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class Account {
    abstract val username: String
    abstract val uuid: String
    abstract val accessToken: String
    abstract val isLicensed: Boolean
}

@Serializable
@SerialName("funlauncher.MicrosoftAccount")
data class MicrosoftAccount(
    override val username: String,
    override val uuid: String,
    override val accessToken: String,
    val skinUrl: String? = null,
    override val isLicensed: Boolean = true
) : Account()

@Serializable
@SerialName("funlauncher.OfflineAccount")
data class OfflineAccount(
    override val username: String,
    override val uuid: String,
    override val accessToken: String,
    override val isLicensed: Boolean = false
) : Account()
