/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package funlauncher

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import java.io.IOException

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

    fun loadAccounts(): List<Account> {
        println("--- Загрузка аккаунтов ---")
        println("Путь к файлу: ${accountsFile.absolutePath}")

        if (!accountsFile.exists()) {
            println("Файл accounts.json не найден.")
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
            println("Файл пуст.")
            return accounts
        }

        try {
            // Пытаемся декодировать напрямую
            accounts = json.decodeFromString<MutableList<Account>>(content)
            println("Аккаунты успешно загружены стандартным парсером.")
        } catch (e: SerializationException) {
            println("Ошибка десериализации kotlinx.serialization, попытка миграции через Gson...")
            e.printStackTrace() // Показываем ошибку kotlinx
            try {
                // Если не удалось, используем Gson для миграции
                val gson = Gson()
                val type = object : TypeToken<MutableList<MutableMap<String, Any?>>>() {}.type
                val rawAccounts = gson.fromJson<MutableList<MutableMap<String, Any?>>>(content, type)

                rawAccounts.forEach { acc ->
                    when (acc["type"]) {
                        "funlauncher.MicrosoftAccount" -> {
                            acc.putIfAbsent("isLicensed", true)
                        }
                        "funlauncher.OfflineAccount" -> {
                            acc.putIfAbsent("uuid", "00000000-0000-0000-0000-000000000000")
                            acc.putIfAbsent("accessToken", "0")
                            acc.putIfAbsent("isLicensed", false)
                        }
                    }
                }

                val migratedContent = gson.toJson(rawAccounts)
                println("Мигрированное содержимое:\n$migratedContent")
                accounts = json.decodeFromString<MutableList<Account>>(migratedContent)
                // Сохраняем исправленный файл
                saveAccounts()
                println("Миграция и загрузка аккаунтов прошли успешно.")
            } catch (migrationError: Exception) {
                println("Критическая ошибка миграции аккаунтов:")
                migrationError.printStackTrace()
                accounts = mutableListOf()
            }
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
