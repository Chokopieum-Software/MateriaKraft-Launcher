/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package funlauncher.database.dao

import funlauncher.auth.Account
import funlauncher.auth.MicrosoftAccount
import funlauncher.auth.OfflineAccount
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class AccountDao {

    fun getAll(): List<Account> {
        return transaction {
            Accounts.selectAll().map { toAccount(it) }
        }
    }

    fun add(account: Account) {
        transaction {
            Accounts.insert {
                it[username] = account.username
                it[uuid] = account.uuid
                it[accessToken] = account.accessToken
                it[isLicensed] = account.isLicensed
                when (account) {
                    is MicrosoftAccount -> {
                        it[type] = "microsoft"
                        it[skinUrl] = account.skinUrl
                    }
                    is OfflineAccount -> {
                        it[type] = "offline"
                    }
                }
            }
        }
    }

    fun delete(account: Account) {
        transaction {
            Accounts.deleteWhere { Accounts.uuid eq account.uuid }
        }
    }

    fun deleteAllMicrosoft() {
        transaction {
            Accounts.deleteWhere { Accounts.type eq "microsoft" }
        }
    }

    fun exists(username: String): Boolean {
        return transaction {
            Accounts.select { Accounts.username eq username }.count() > 0
        }
    }

    private fun toAccount(row: ResultRow): Account {
        val type = row[Accounts.type]
        return when (type) {
            "microsoft" -> MicrosoftAccount(
                username = row[Accounts.username],
                uuid = row[Accounts.uuid],
                accessToken = row[Accounts.accessToken],
                skinUrl = row[Accounts.skinUrl],
                isLicensed = row[Accounts.isLicensed]
            )
            "offline" -> OfflineAccount(
                username = row[Accounts.username],
                uuid = row[Accounts.uuid],
                accessToken = row[Accounts.accessToken],
                isLicensed = row[Accounts.isLicensed]
            )
            else -> throw IllegalArgumentException("Unknown account type: $type")
        }
    }
}
