/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package funlauncher

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

class AccountManager {
    private val accountsFile = PathManager.getAppDataDirectory().resolve("accounts.json").toFile()
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

    fun hasLicensedAccount(): Boolean {
        return accounts.any { it.isLicensed }
    }

    fun addAccount(account: Account): Boolean {
        loadAccounts()

        if (account is OfflineAccount && !hasLicensedAccount()) {
            return false // Запрещаем добавлять оффлайн-аккаунт, если нет лицензионного
        }

        if (accounts.any { it.username.equals(account.username, ignoreCase = true) }) {
            return false // Аккаунт с таким именем уже существует
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
