/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package funlauncher.database

import funlauncher.database.dao.Accounts
import funlauncher.database.dao.Builds
import funlauncher.managers.PathManager
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseManager {
    fun init(pathManager: PathManager) {
        val dbFile = pathManager.getAppDataDirectory().resolve("materiakraft.db")
        Database.connect("jdbc:sqlite:${dbFile.toFile().absolutePath}", "org.sqlite.JDBC")

        transaction {
            SchemaUtils.create(Accounts, Builds)
        }
    }
}
