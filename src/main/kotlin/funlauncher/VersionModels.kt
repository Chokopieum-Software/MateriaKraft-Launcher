/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package funlauncher

import kotlinx.serialization.Serializable

// --- Mojang & Common Models ---

/**
 * Represents a single Minecraft version entry from the Mojang version manifest.
 */
@Serializable
data class MinecraftVersion(val id: String, val type: String)

/**
 * A clean, internal representation of a Fabric Loader version.
 * The UI and other logic should use this class.
 */
@Serializable
data class FabricVersion(val version: String, val stable: Boolean)

// --- Fabric Meta API Response Models ---

/**
 * Represents a single Minecraft game version supported by Fabric, from the Fabric Meta API.
 */
@Serializable
data class FabricGameVersion(val version: String, val stable: Boolean)

/**
 * Represents the top-level object in the array returned by the Fabric loader versions endpoint.
 * This is used internally by VersionManager to parse the complex response.
 */
@Serializable
data class FabricLoaderApiResponse(
    val loader: FabricLoaderInfo
)

/**
 * Represents the nested "loader" object inside the API response.
 */
@Serializable
data class FabricLoaderInfo(
    val version: String,
    val stable: Boolean
)

/**
 * Represents the top-level structure of Mojang's version manifest JSON.
 * This is used internally by VersionManager.
 */
@Serializable
internal data class VersionManifest(val versions: List<VersionEntry>) {
    @Serializable
    internal data class VersionEntry(val id: String, val type: String, val url: String)
}
