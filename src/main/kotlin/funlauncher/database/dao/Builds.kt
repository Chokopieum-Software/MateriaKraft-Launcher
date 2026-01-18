/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package funlauncher.database.dao

import org.jetbrains.exposed.sql.Table

object Builds : Table() {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 100).uniqueIndex()
    val version = varchar("version", 50)
    val type = varchar("type", 20)
    val modloaderVersion = varchar("modloader_version", 50).nullable()
    val installPath = text("install_path")
    val createdAt = varchar("created_at", 50)
    val imagePath = text("image_path").nullable()
    val javaPath = text("java_path").nullable()
    val maxRamMb = integer("max_ram_mb").nullable()
    val javaArgs = text("java_args").nullable()
    val envVars = text("env_vars").nullable()

    override val primaryKey = PrimaryKey(id)
}
