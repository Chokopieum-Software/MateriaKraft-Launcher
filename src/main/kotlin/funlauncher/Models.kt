/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */
package funlauncher

import kotlinx.serialization.Serializable

@Serializable
enum class BuildType {
    VANILLA,
    FABRIC,
    FORGE,
    QUILT,
    NEOFORGE
}

@Serializable
data class MinecraftBuild(
    val name: String,
    val version: String,
    val type: BuildType,
    val installPath: String,
    val createdAt: String = java.time.Instant.now().toString(),
    val imagePath: String? = null, // Путь к обложке
    // Индивидуальные настройки сборки
    val javaPath: String? = null,
    val maxRamMb: Int? = null,
    val javaArgs: String? = null,
    val envVars: String? = null,
    val modloaderVersion: String? = null
)

@Serializable
data class AppSettings(
    val theme: Theme = Theme.System,
    val navPanelPosition: NavPanelPosition = NavPanelPosition.Left,
    val maxRamMb: Int = 4096,
    val javaArgs: String = "",
    val envVars: String = "",
    val javaPath: String = "",
    val showConsoleOnLaunch: Boolean = false,
    val language: String = "ru"
)

enum class Theme {
    System, Light, Dark, Day, Amoled
}

enum class NavPanelPosition {
    Left, Bottom
}

data class JavaInfo(
    val path: String,
    val version: Int,
    val vendor: String,
    val is64Bit: Boolean,
    val isManagedByLauncher: Boolean // Флаг, что JRE управляется лаунчером
) {
    val displayName: String
        get() = "Java $version ($vendor, ${if (is64Bit) "64-bit" else "32-bit"})"
}

data class JavaInstallations(
    val system: List<JavaInfo>,
    val launcher: List<JavaInfo>
)
