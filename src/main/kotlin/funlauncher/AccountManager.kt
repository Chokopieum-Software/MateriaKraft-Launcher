package funlauncher

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import java.io.File
import kotlin.io.path.div

class AccountManager {
    private val accountsFile = PathManager.getAppDataDirectory().resolve("accounts.json").toFile()
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        serializersModule = SerializersModule {
            polymorphic(Account::class) {
                subclass(OfflineAccount::class)
                // TODO: Добавить MojangAccount
            }
        }
    }
    private var accounts: MutableList<Account> = mutableListOf()

    init {
        loadAccounts()
    }

    fun loadAccounts(): List<Account> {
        if (accountsFile.exists()) {
            try {
                val content = accountsFile.readText()
                accounts = json.decodeFromString<MutableList<Account>>(content)
            } catch (e: Exception) {
                e.printStackTrace()
                accounts = mutableListOf()
            }
        }
        return accounts
    }

    private fun saveAccounts() {
        try {
            accountsFile.parentFile.mkdirs()
            accountsFile.writeText(json.encodeToString(accounts))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addAccount(account: Account): Boolean {
        // Загружаем актуальный список аккаунтов перед проверкой
        loadAccounts()

        // Проверяем, есть ли уже аккаунты. Если есть, не позволяем добавить новый.
        if (accounts.isNotEmpty()) {
            return false // Уже есть один аккаунт, добавление нового запрещено
        }

        // Проверяем на дубликаты имен (хотя при ограничении в 1 аккаунт это менее критично)
        if (accounts.any { it.username.equals(account.username, ignoreCase = true) }) {
            return false // Аккаунт с таким именем уже существует
        }

        accounts.add(account)
        saveAccounts()
        return true
    }

    fun deleteAccount(account: Account) {
        accounts.remove(account)
        saveAccounts()
    }

    fun getAccounts(): List<Account> {
        return accounts
    }
}
