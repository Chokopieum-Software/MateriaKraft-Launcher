package funlauncher

import kotlinx.serialization.Serializable

@Serializable
sealed class Account {
    abstract val username: String
}

@Serializable
data class OfflineAccount(override val username: String) : Account()

// TODO: Добавить MojangAccount, когда будет реализована аутентификация
