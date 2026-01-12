/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package funlauncher.game

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put

// --- Primary Data Models used across the application ---

@Serializable
data class VersionInfo(
    val id: String,
    val libraries: List<Library>,
    val mainClass: String,
    @SerialName("minecraftArguments") val gameArguments: String? = null,
    val arguments: Arguments? = null,
    val assets: String,
    val downloads: Downloads,
    val assetIndex: AssetIndexInfo
) {
    @Serializable
    data class Library(
        val name: String,
        val url: String? = null,
        val downloads: LibraryDownloads? = null,
        val natives: Map<String, String>? = null,
        val rules: List<Rule>? = null
    )

    @Serializable
    data class Arguments(
        val game: List<JsonElementWrapper> = emptyList(),
        val jvm: List<JsonElementWrapper> = emptyList()
    )

    @Serializable
    data class LibraryDownloads(
        val artifact: Artifact? = null,
        val classifiers: Map<String, Artifact>? = null
    )

    @Serializable
    data class Artifact(
        val path: String,
        val url: String,
        val size: Long,
        val sha1: String? = null
    )

    @Serializable
    data class Downloads(val client: ClientDownload)

    @Serializable
    data class ClientDownload(val url: String)

    @Serializable
    data class AssetIndexInfo(val id: String, val url: String)

    @Serializable
    data class Rule(val action: String, val os: OS? = null)

    @Serializable
    data class OS(val name: String? = null)
}

@Serializable
data class AssetIndex(val objects: Map<String, AssetObject>) {
    @Serializable
    data class AssetObject(val hash: String, val size: Long)
}

// --- Specific Profile Models for Fabric/Forge ---

@Serializable
data class FabricProfile(
    val id: String? = null,
    val inheritsFrom: String? = null,
    val mainClass: String? = null,
    val libraries: List<VersionInfo.Library> = emptyList(),
    val arguments: VersionInfo.Arguments? = null
)

@Serializable
data class VersionProfile(
    val id: String,
    val inheritsFrom: String,
    val mainClass: String,
    val libraries: List<VersionInfo.Library>,
    val arguments: VersionInfo.Arguments? = null
)

// --- UI-facing Models ---

@Serializable
data class MinecraftVersion(val id: String, val type: String, val releaseTime: String)

@Serializable
data class FabricGameVersion(val version: String, val stable: Boolean)

@Serializable
data class FabricVersion(val version: String, val stable: Boolean)

@Serializable
data class QuiltGameVersion(val version: String, val stable: Boolean)

@Serializable
data class QuiltVersion(val version: String)

@Serializable
data class NeoForgeVersion(
    val mcVersion: String,
    val neoForgeVersion: String
)


// --- Manifests for fetching version lists ---

@Serializable
data class VersionManifest(val versions: List<VersionEntry>)

@Serializable
data class VersionEntry(val id: String, val type: String, val url: String, val releaseTime: String)

@Serializable
data class FabricLoaderApiResponse(
    val loader: FabricLoaderInfo
)

@Serializable
data class FabricLoaderInfo(
    val version: String,
    val stable: Boolean
)

@Serializable
data class QuiltLoaderApiResponse(
    val loader: QuiltLoaderInfo
)

@Serializable
data class QuiltLoaderInfo(
    val version: String
)

@Serializable
data class NeoForgeApiResponse(
    val versions: List<String>
)


// --- Custom Serializer for mixed-type JSON arrays in arguments ---

@Serializable(with = JsonElementWrapperSerializer::class)
sealed class JsonElementWrapper {
    @Serializable
    data class StringValue(val value: String) : JsonElementWrapper()

    @Serializable
    data class ObjectValue(val rules: List<VersionInfo.Rule>, val value: JsonElement) : JsonElementWrapper() {
        fun getValues(): List<String> = when (value) {
            is JsonPrimitive -> listOf(value.content)
            is JsonArray -> value.map { (it as JsonPrimitive).content }
            else -> emptyList()
        }
    }
}

object JsonElementWrapperSerializer : KSerializer<JsonElementWrapper> {
    override val descriptor = buildClassSerialDescriptor("JsonElementWrapper")

    override fun serialize(encoder: Encoder, value: JsonElementWrapper) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw kotlinx.serialization.SerializationException("This serializer can be used only with JSON")
        val json = jsonEncoder.json
        when (value) {
            is JsonElementWrapper.StringValue -> jsonEncoder.encodeJsonElement(JsonPrimitive(value.value))
            is JsonElementWrapper.ObjectValue -> {
                val obj = buildJsonObject {
                    put("rules", json.encodeToJsonElement(ListSerializer(VersionInfo.Rule.serializer()), value.rules))
                    put("value", value.value)
                }
                jsonEncoder.encodeJsonElement(obj)
            }
        }
    }

    override fun deserialize(decoder: Decoder): JsonElementWrapper {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw kotlinx.serialization.SerializationException("This serializer can be used only with JSON")
        val json = jsonDecoder.json

        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> JsonElementWrapper.StringValue(element.content)
            is JsonObject -> json.decodeFromJsonElement<JsonElementWrapper.ObjectValue>(element)
            else -> throw kotlinx.serialization.SerializationException("Unexpected JSON element type for JsonElementWrapper: $element")
        }
    }
}
