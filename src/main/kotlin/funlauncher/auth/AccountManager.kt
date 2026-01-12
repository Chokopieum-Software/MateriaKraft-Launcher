/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package funlauncher.auth

import funlauncher.managers.PathManager
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import java.io.IOException

class AccountManager(pathManager: PathManager) {
    private val accountsFile = pathManager.getAppDataDirectory().resolve("accounts.json").toFile()
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        serializersModule = SerializersModule {
            polymorphic(Account::class) {
                subclass(OfflineAccount::class)
                subclass(MicrosoftAccount::class)
            }
        }
    }
    private var accounts: MutableList<Account> = mutableListOf()

    private fun createDefaultAccounts(): MutableList<Account> {
        println("Создание файла accounts.json по умолчанию.")
        return mutableListOf(
            MicrosoftAccount(
                username = "YourMicrosoftName",
                uuid = "123e4567-e89b-12d3-a456-426614174001",
                accessToken = "dummy_token_for_testing",
                skinUrl = null,
                isLicensed = true
            ),
            OfflineAccount(
                username = "OfflinePlayer",
                uuid = "00000000-0000-0000-0000-000000000000",
                accessToken = "0",
                isLicensed = false
            )
        )
    }

    fun loadAccounts(): List<Account> {
        println("--- Загрузка аккаунтов ---")
        println("Путь к файлу: ${accountsFile.absolutePath}")

        if (!accountsFile.exists()) {
            println("Файл accounts.json не найден. Создание файла по умолчанию.")
            accounts = createDefaultAccounts()
            saveAccounts()
            return accounts
        }

        val content = try {
            accountsFile.readText()
        } catch (e: Exception) {
            println("Критическая ошибка чтения файла accounts.json:")
            e.printStackTrace()
            return accounts
        }

        println("Содержимое файла:\n$content")

        if (content.isBlank()) {
            println("Файл пуст. Создание файла по умолчанию.")
            accounts = createDefaultAccounts()
            saveAccounts()
            return accounts
        }

        try {
            accounts = json.decodeFromString<MutableList<Account>>(content)
            println("Аккаунты успешно загружены.")
        } catch (e: SerializationException) {
            println("Ошибка десериализации kotlinx.serialization. Создание файла по умолчанию.")
            e.printStackTrace()
            accounts = createDefaultAccounts()
            saveAccounts()
        } catch (e: Exception) {
            println("Произошла неожиданная ошибка при загрузке аккаунтов:")
            e.printStackTrace()
            accounts = mutableListOf()
        }
        println("--- Загрузка завершена, найдено ${accounts.size} аккаунтов ---")
        return accounts
    }

    private fun saveAccounts() {
        try {
            accountsFile.parentFile.mkdirs()
            accountsFile.writeText(json.encodeToString(accounts))
            println("Аккаунты сохранены в ${accountsFile.absolutePath}")
        } catch (e: IOException) {
            println("Ошибка сохранения accounts.json: ${e.message}")
        }
    }

    fun hasLicensedAccount(): Boolean {
        return accounts.any { it.isLicensed }
    }

    fun addAccount(account: Account): Boolean {
        if (account is OfflineAccount && !hasLicensedAccount()) {
            println("Нельзя добавить оффлайн аккаунт без лицензионного.")
            return false
        }

        if (accounts.any { it.username.equals(account.username, ignoreCase = true) }) {
            println("Аккаунт с именем ${account.username} уже существует.")
            return false
        }

        accounts.add(account)
        saveAccounts()
        return true
    }

    fun loginWithMicrosoft(): Boolean {
        return try {
            val authenticator = MateriaAuthenticator()
            val profile = authenticator.login()
            val account = MicrosoftAccount(
                username = profile.name,
                uuid = profile.id,
                accessToken = profile.accessToken,
                skinUrl = profile.skinUrl,
                isLicensed = true
            )
            addAccount(account)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun deleteAccount(account: Account) {
        accounts.remove(account)
        saveAccounts()
    }

    fun getAccounts(): List<Account> {
        return accounts
    }
}
