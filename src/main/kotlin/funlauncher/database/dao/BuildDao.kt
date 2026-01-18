/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package funlauncher.database.dao

import funlauncher.BuildType
import funlauncher.MinecraftBuild
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class BuildDao {

    fun getAll(): List<MinecraftBuild> {
        return transaction {
            Builds.selectAll().map { toBuild(it) }
        }
    }

    fun add(build: MinecraftBuild) {
        transaction {
            Builds.insert {
                it[name] = build.name
                it[version] = build.version
                it[type] = build.type.name
                it[modloaderVersion] = build.modloaderVersion
                it[installPath] = build.installPath
                it[createdAt] = build.createdAt
                it[imagePath] = build.imagePath
                it[javaPath] = build.javaPath
                it[maxRamMb] = build.maxRamMb
                it[javaArgs] = build.javaArgs
                it[envVars] = build.envVars
            }
        }
    }

    fun update(build: MinecraftBuild) {
        transaction {
            Builds.update({ Builds.name eq build.name }) {
                it[version] = build.version
                it[type] = build.type.name
                it[modloaderVersion] = build.modloaderVersion
                it[installPath] = build.installPath
                it[createdAt] = build.createdAt
                it[imagePath] = build.imagePath
                it[javaPath] = build.javaPath
                it[maxRamMb] = build.maxRamMb
                it[javaArgs] = build.javaArgs
                it[envVars] = build.envVars
            }
        }
    }
    
    fun update(oldName: String, build: MinecraftBuild) {
        transaction {
            Builds.update({ Builds.name eq oldName }) {
                it[name] = build.name
                it[version] = build.version
                it[type] = build.type.name
                it[modloaderVersion] = build.modloaderVersion
                it[installPath] = build.installPath
                it[createdAt] = build.createdAt
                it[imagePath] = build.imagePath
                it[javaPath] = build.javaPath
                it[maxRamMb] = build.maxRamMb
                it[javaArgs] = build.javaArgs
                it[envVars] = build.envVars
            }
        }
    }

    fun delete(name: String) {
        transaction {
            Builds.deleteWhere { Builds.name eq name }
        }
    }

    fun exists(name: String): Boolean {
        return transaction {
            Builds.select { Builds.name eq name }.count() > 0
        }
    }

    private fun toBuild(row: ResultRow): MinecraftBuild {
        return MinecraftBuild(
            name = row[Builds.name],
            version = row[Builds.version],
            type = BuildType.valueOf(row[Builds.type]),
            modloaderVersion = row[Builds.modloaderVersion],
            installPath = row[Builds.installPath],
            createdAt = row[Builds.createdAt],
            imagePath = row[Builds.imagePath],
            javaPath = row[Builds.javaPath],
            maxRamMb = row[Builds.maxRamMb],
            javaArgs = row[Builds.javaArgs],
            envVars = row[Builds.envVars]
        )
    }
}
