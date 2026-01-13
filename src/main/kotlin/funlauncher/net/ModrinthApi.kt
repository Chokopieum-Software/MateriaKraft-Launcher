package funlauncher.net

import funlauncher.managers.CacheManager
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val MODRINTH_API_URL = "https://api.modrinth.com/v2"

class ModrinthApi(private val cacheManager: CacheManager) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.NONE
        }
    }

    suspend fun search(
        query: String,
        facets: String,
        limit: Int = 20,
        offset: Int = 0
    ): SearchResult {
        val cacheKey = "modrinth_search_${query}_${facets}_${limit}_${offset}"
        return cacheManager.getOrFetch(cacheKey) {
            val response = client.get("$MODRINTH_API_URL/search") {
                parameter("query", query)
                parameter("facets", facets)
                parameter("limit", limit)
                parameter("offset", offset)
            }
            response.body()
        } ?: throw IllegalStateException("Failed to fetch search results from Modrinth API")
    }

    suspend fun getProject(id: String): Project {
        val cacheKey = "modrinth_project_$id"
        return cacheManager.getOrFetch(cacheKey) {
            val response = client.get("$MODRINTH_API_URL/project/$id")
            response.body()
        } ?: throw IllegalStateException("Failed to fetch project details from Modrinth API for id: $id")
    }

    suspend fun getProjectVersions(id: String): List<Version> {
        val cacheKey = "modrinth_project_versions_$id"
        return cacheManager.getOrFetch(cacheKey) {
            val response = client.get("$MODRINTH_API_URL/project/$id/version")
            response.body()
        } ?: throw IllegalStateException("Failed to fetch project versions from Modrinth API for id: $id")
    }

    internal suspend fun getPopularProjects(projectType: String): SearchResult {
        val response = client.get("$MODRINTH_API_URL/search") {
            parameter("facets", "[[\"project_type:$projectType\"]]")
            parameter("index", "downloads")
            parameter("limit", 20)
        }
        return response.body()
    }

    fun close() {
        client.close()
    }
}

@Serializable
data class SearchResult(
    val hits: List<Hit>,
    val offset: Int,
    val limit: Int,
    @SerialName("total_hits")
    val totalHits: Int
)

@Serializable
data class Hit(
    @SerialName("project_id")
    val projectId: String,
    @SerialName("project_type")
    val projectType: String,
    val slug: String,
    val author: String,
    val title: String,
    val description: String,
    val categories: List<String> = emptyList(),
    @SerialName("display_categories")
    val displayCategories: List<String> = emptyList(),
    val versions: List<String> = emptyList(),
    val downloads: Int,
    @SerialName("follows")
    val follows: Int,
    @SerialName("icon_url")
    val iconUrl: String?,
    @SerialName("date_created")
    val dateCreated: String,
    @SerialName("date_modified")
    val dateModified: String,
    @SerialName("latest_version")
    val latestVersion: String,
    val license: String,
    val gallery: List<String> = emptyList()
)

@Serializable
data class Project(
    val id: String,
    val slug: String,
    @SerialName("project_type")
    val projectType: String,
    val team: String,
    val title: String,
    val description: String,
    val body: String,
    @SerialName("body_url")
    val bodyUrl: String?,
    val published: String,
    val updated: String,
    val status: String,
    val license: License,
    @SerialName("client_side")
    val clientSide: String,
    @SerialName("server_side")
    val serverSide: String,
    val downloads: Int,
    val followers: Int,
    val categories: List<String> = emptyList(),
    @SerialName("version_ids")
    val versionIds: List<String> = emptyList(),
    @SerialName("icon_url")
    val iconUrl: String?,
    @SerialName("game_versions")
    val gameVersions: List<String> = emptyList(),
    val loaders: List<String> = emptyList(),
    val gallery: List<GalleryItem> = emptyList()
)

@Serializable
data class Version(
    val id: String,
    @SerialName("project_id")
    val projectId: String,
    @SerialName("author_id")
    val authorId: String?,
    val featured: Boolean,
    val name: String,
    @SerialName("version_number")
    val versionNumber: String,
    val changelog: String?,
    @SerialName("changelog_url")
    val changelogUrl: String?,
    @SerialName("date_published")
    val datePublished: String,
    val downloads: Int,
    @SerialName("version_type")
    val versionType: String,
    val files: List<File>,
    val dependencies: List<Dependency> = emptyList(),
    @SerialName("game_versions")
    val gameVersions: List<String> = emptyList(),
    val loaders: List<String> = emptyList()
)

@Serializable
data class License(
    val id: String,
    val name: String,
    val url: String?
)

@Serializable
data class GalleryItem(
    val url: String,
    val featured: Boolean,
    val title: String?,
    val description: String?,
    val created: String
)

@Serializable
data class File(
    val hashes: Hashes,
    val url: String,
    val filename: String,
    val primary: Boolean,
    val size: Int
)

@Serializable
data class Hashes(
    val sha1: String,
    val sha512: String
)

@Serializable
data class Dependency(
    @SerialName("version_id")
    val versionId: String?,
    @SerialName("project_id")
    val projectId: String?,
    @SerialName("file_name")
    val fileName: String?,
    @SerialName("dependency_type")
    val dependencyType: String
)
