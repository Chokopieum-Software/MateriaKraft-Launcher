/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package funlauncher.database.dao

import org.jetbrains.exposed.sql.Table

object Accounts : Table() {
    val id = integer("id").autoIncrement()
    val username = varchar("username", 50)
    val uuid = varchar("uuid", 36)
    val accessToken = text("access_token")
    val skinUrl = text("skin_url").nullable()
    val isLicensed = bool("is_licensed")
    val type = varchar("type", 20) // "microsoft" or "offline"

    override val primaryKey = PrimaryKey(id)
}
