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
                coerceInputValues = true // Полезно, если API вернет null там, где мы не ждем
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
        } ?: throw IllegalStateException("Failed to fetch search results")
    }

    suspend fun getProject(id: String): Project {
        val cacheKey = "modrinth_project_$id"
        return cacheManager.getOrFetch(cacheKey) {
            val response = client.get("$MODRINTH_API_URL/project/$id")
            response.body()
        } ?: throw IllegalStateException("Failed to fetch project details")
    }

    suspend fun getProjectVersions(id: String): List<Version> {
        val cacheKey = "modrinth_project_versions_$id"
        return cacheManager.getOrFetch(cacheKey) {
            val response = client.get("$MODRINTH_API_URL/project/$id/version")
            response.body()
        } ?: throw IllegalStateException("Failed to fetch project versions")
    }

    // --- RAW DATA METHODS ---

    suspend fun getCategories(): List<ModrinthCategoryTag> {
        val cacheKey = "modrinth_categories"
        return cacheManager.getOrFetch(cacheKey) {
            client.get("$MODRINTH_API_URL/tag/category").body()
        } ?: emptyList()
    }

    suspend fun getLoaders(): List<ModrinthLoaderTag> {
        val cacheKey = "modrinth_loaders"
        return cacheManager.getOrFetch(cacheKey) {
            client.get("$MODRINTH_API_URL/tag/loader").body()
        } ?: emptyList()
    }

    suspend fun getGameVersions(): List<ModrinthGameVersionTag> {
        val cacheKey = "modrinth_game_versions"
        return cacheManager.getOrFetch(cacheKey) {
            client.get("$MODRINTH_API_URL/tag/game_version").body()
        } ?: emptyList()
    }

    // --- SMART AGGREGATED FILTER METHOD ---

    /**
     * Запрашивает все теги и группирует их для удобного использования в UI.
     * Возвращает фильтры отдельно для модов, шейдеров, ресурспаков и т.д.
     */
    suspend fun getSmartFilters(): AppFilters {
        // Запрашиваем данные (можно параллельно через async/await, если CacheManager поддерживает)
        val categories = getCategories()
        val loaders = getLoaders()
        val gameVersionsRaw = getGameVersions()

        // 1. Фильтруем версии игры (только релизы и снепшоты, сортируем сами если нужно)
        val validVersions = gameVersionsRaw
            .filter { it.version_type == "release" } // Берем только релизы
            .map { it.version }

        // 2. Подготавливаем структуру
        val filterMap = mutableMapOf<String, ProjectTypeFilters>()
        val projectTypes = listOf("mod", "resourcepack", "shader", "modpack")

        // Инициализируем пустые фильтры
        projectTypes.forEach { type ->
            filterMap[type] = ProjectTypeFilters(categories = mutableListOf(), loaders = mutableListOf())
        }

        // 3. Распределяем Категории (Жанры)
        categories.forEach { cat ->
            if (filterMap.containsKey(cat.project_type)) {
                filterMap[cat.project_type]?.categories?.add(
                    FilterItem(name = cat.name, icon = cat.icon)
                )
            }
        }

        // 4. Распределяем Лоадеры (Fabric, Forge...)
        loaders.forEach { loader ->
            loader.supported_project_types.forEach { type ->
                if (filterMap.containsKey(type)) {
                    filterMap[type]?.loaders?.add(loader.name)
                }
            }
        }

        // 5. Специальная логика для Datapacks
        // Датапаки в Modrinth технически являются модами, но мы хотим для них отдельный фильтр.
        // Они используют категории модов, но лоадер у них жестко "datapack".
        val datapackFilters = ProjectTypeFilters(
            categories = filterMap["mod"]?.categories?.toMutableList() ?: mutableListOf(),
            loaders = mutableListOf("datapack")
        )

        return AppFilters(
            mods = filterMap["mod"]!!,
            resourcePacks = filterMap["resourcepack"]!!,
            shaders = filterMap["shader"]!!,
            modpacks = filterMap["modpack"]!!,
            datapacks = datapackFilters,
            gameVersions = validVersions
        )
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

// --- NEW FILTER DATA CLASSES ---

@Serializable
data class AppFilters(
    val mods: ProjectTypeFilters,
    val resourcePacks: ProjectTypeFilters,
    val shaders: ProjectTypeFilters,
    val modpacks: ProjectTypeFilters,
    val datapacks: ProjectTypeFilters,
    val gameVersions: List<String>
)

@Serializable
data class ProjectTypeFilters(
    val categories: MutableList<FilterItem>, // Жанры (Adventure, Tech...)
    val loaders: MutableList<String>         // Загрузчики (Fabric, Iris...)
)

@Serializable
data class FilterItem(
    val name: String, // ID для поиска (в facets)
    val icon: String? // SVG иконка
)

// --- UPDATED API DTOs ---

@Serializable
data class ModrinthCategoryTag(
    val icon: String?,
    val name: String,
    val project_type: String,
    val header: String?,
    val pretty_name: String? = null // Сделал nullable и default null на всякий случай
)

@Serializable
data class ModrinthLoaderTag(
    val icon: String?,
    val name: String,
    @SerialName("supported_project_types")
    val supported_project_types: List<String>, // ВАЖНО: Это список, а не строка!
    val pretty_name: String? = null
)

@Serializable
data class ModrinthGameVersionTag(
    val version: String,
    val version_type: String, // release, snapshot, alpha, beta
    val date: String,
    val major: Boolean
)

// --- EXISTING CLASSES (UNCHANGED) ---

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