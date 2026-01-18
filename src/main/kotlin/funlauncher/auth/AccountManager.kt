/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package funlauncher.auth

import funlauncher.database.dao.AccountDao
import funlauncher.managers.PathManager
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

class AccountManager(pathManager: PathManager) {
    private val accountDao = AccountDao()
    private var accounts: MutableList<Account> = mutableListOf()

    init {
        migrateFromJson(pathManager)
        accounts = accountDao.getAll().toMutableList()
    }

    private fun migrateFromJson(pathManager: PathManager) {
        val accountsFile = pathManager.getAppDataDirectory().resolve("accounts.json").toFile()
        if (accountsFile.exists()) {
            println("--- Запуск миграции аккаунтов из accounts.json ---")
            val content = try {
                accountsFile.readText()
            } catch (e: Exception) {
                println("Критическая ошибка чтения файла accounts.json для миграции:")
                e.printStackTrace()
                return
            }

            if (content.isNotBlank()) {
                val json = Json {
                    prettyPrint = true
                    ignoreUnknownKeys = true
                    serializersModule = SerializersModule {
                        polymorphic(Account::class) {
                            subclass(OfflineAccount::class)
                            subclass(MicrosoftAccount::class)
                        }
                    }
                }
                try {
                    val jsonAccounts = json.decodeFromString<List<Account>>(content)
                    jsonAccounts.forEach { account ->
                        if (!accountDao.exists(account.username)) {
                            accountDao.add(account)
                            println("Мигрирован аккаунт: ${account.username}")
                        }
                    }
                } catch (e: SerializationException) {
                    println("Ошибка десериализации accounts.json во время миграции:")
                    e.printStackTrace()
                }
            }
            // Переименовываем файл после успешной миграции, чтобы избежать повторной миграции
            accountsFile.renameTo(pathManager.getAppDataDirectory().resolve("accounts.json.migrated").toFile())
            println("--- Миграция аккаунтов завершена ---")
        }
    }

    fun loadAccounts(): List<Account> {
        accounts = accountDao.getAll().toMutableList()
        println("--- Загрузка аккаунтов из БД, найдено ${accounts.size} ---")
        return accounts
    }

    fun hasLicensedAccount(): Boolean {
        return accounts.any { it.isLicensed }
    }

    fun addAccount(account: Account): Boolean {
        if (accountDao.exists(account.username)) {
            println("Аккаунт с именем ${account.username} уже существует.")
            return false
        }

        accountDao.add(account)
        accounts.add(account)
        return true
    }

    fun loginWithMicrosoft(): Boolean {
        return try {
            val authenticator = MateriaAuthenticator()
            val profile = authenticator.login()

            val account = if (profile.isLicensed) {
                MicrosoftAccount(
                    username = profile.name,
                    uuid = profile.id,
                    accessToken = profile.accessToken,
                    skinUrl = profile.skinUrl,
                    isLicensed = true
                )
            } else {
                OfflineAccount(
                    username = profile.name,
                    uuid = profile.id,
                    accessToken = profile.accessToken,
                    isLicensed = false
                )
            }
            addAccount(account)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun logoutFromMicrosoft() {
        val authenticator = MateriaAuthenticator()
        authenticator.logout()

        accountDao.deleteAllMicrosoft()
        accounts.removeIf { it is MicrosoftAccount }
    }

    fun deleteAccount(account: Account) {
        accountDao.delete(account)
        accounts.remove(account)
    }

    fun getAccounts(): List<Account> {
        return accounts
    }
}
