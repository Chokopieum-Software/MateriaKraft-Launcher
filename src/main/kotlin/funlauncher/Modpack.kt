package funlauncher

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ModrinthIndex(
    val formatVersion: Int,
    val game: String,
    val versionId: String,
    val name: String,
    val summary: String? = null,
    val files: List<ModpackFile>,
    val dependencies: ModpackDependencies
)

@Serializable
data class ModpackFile(
    val path: String,
    val hashes: Map<String, String>,
    val env: Map<String, String>? = null,
    val downloads: List<String>,
    val fileSize: Long
)

@Serializable
data class ModpackDependencies(
    val minecraft: String,
    @SerialName("fabric-loader") val fabricLoader: String? = null,
    @SerialName("forge") val forge: String? = null,
    @SerialName("quilt-loader") val quiltLoader: String? = null,
    @SerialName("neoforge") val neoforge: String? = null
)
