package ui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import funlauncher.*
import funlauncher.game.VersionMetadataFetcher
import funlauncher.managers.BuildManager
import funlauncher.managers.CacheManager
import funlauncher.managers.PathManager
import funlauncher.modpack.ModpackInstaller
import funlauncher.net.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

enum class CategoryFilterState {
    INCLUDED, EXCLUDED
}

@Composable
fun ModificationCard(hit: Hit, onClick: () -> Unit) {
    val imageBitmap by produceState<ImageBitmap?>(null, hit.iconUrl) {
        value = ImageLoader.loadImage(hit.iconUrl)
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                imageBitmap?.let {
                    Image(
                        bitmap = it,
                        contentDescription = "${hit.title} icon",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } ?: Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = "Placeholder icon",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(hit.title, style = MaterialTheme.typography.titleMedium)
                Text("by ${hit.author}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    hit.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
fun ModificationDetails(
    project: Project,
    projectVersions: List<Version>,
    onInstallClick: (Version) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Markdown(
                content = project.body,

            )
        }

        item {
            Text("Versions", style = MaterialTheme.typography.titleLarge)
        }

        items(projectVersions) { version ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(version.name, style = MaterialTheme.typography.titleMedium)
                        Text("Type: ${version.versionType}", style = MaterialTheme.typography.bodySmall)
                        Text("MC: ${version.gameVersions.joinToString()}", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = { onInstallClick(version) }) {
                        Text("Install")
                    }
                }
            }
        }
    }
}

@Composable
fun InstallModificationDialog(
    compatibleBuilds: List<MinecraftBuild>,
    onDismiss: () -> Unit,
    onInstall: (MinecraftBuild) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select a build") },
        text = {
            if (compatibleBuilds.isEmpty()) {
                Text("No compatible builds found.")
            } else {
                LazyColumn {
                    items(compatibleBuilds) { build ->
                        Text(build.name, modifier = Modifier.clickable { onInstall(build) }.fillMaxWidth().padding(12.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun ModificationsScreen(
    onBack: () -> Unit,
    navPanelPosition: NavPanelPosition,
    buildManager: BuildManager,
    onModpackInstalled: () -> Unit,
    pathManager: PathManager,
    snackbarHostState: SnackbarHostState,
    cacheManager: CacheManager
) {
    val scope = rememberCoroutineScope()
    val modrinthApi = remember { ModrinthApi(cacheManager) }
    val modificationDownloader = remember { ModificationDownloader() }
    val versionMetadataFetcher = remember { VersionMetadataFetcher(buildManager, pathManager) }
    // Используем глобальный scope для установщика
    val modpackInstaller = remember { ModpackInstaller(buildManager, modrinthApi, pathManager, ApplicationScope.scope) }

    var searchQuery by remember { mutableStateOf("") }
    var searchResult by remember { mutableStateOf<SearchResult?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    var selectedProject by remember { mutableStateOf<Project?>(null) }
    var projectVersions by remember { mutableStateOf<List<Version>>(emptyList()) }
    var isLoadingProject by remember { mutableStateOf(false) }

    var versionToInstall by remember { mutableStateOf<Version?>(null) }
    val allBuilds = remember { mutableStateListOf<MinecraftBuild>() }

    var selectedType by remember { mutableStateOf(ModificationType.MODPACKS) }
    val versions = remember { mutableStateListOf<String>() }
    val selectedVersions = remember { mutableStateListOf<String>() }
    val categoryFilters = remember { mutableStateMapOf<ModrinthCategory, CategoryFilterState>() }
    
    var versionLoadTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        allBuilds.addAll(buildManager.loadBuilds())
    }

    DisposableEffect(Unit) {
        onDispose {
            modrinthApi.close()
        }
    }

    LaunchedEffect(versionLoadTrigger) {
        withContext(Dispatchers.IO) {
            val cachedVersions = cacheManager.getOrFetch<List<String>>("vanilla_versions") {
                versionMetadataFetcher.getVanillaVersions()
            }
            if (cachedVersions != null) {
                withContext(Dispatchers.Main) {
                    versions.clear()
                    versions.addAll(cachedVersions)
                }
            }

            // Background update
            try {
                val freshVersions = versionMetadataFetcher.getVanillaVersions()
                if (freshVersions.sorted() != cachedVersions?.sorted()) {
                    withContext(Dispatchers.Main) {
                        versions.clear()
                        versions.addAll(freshVersions)
                    }
                    // Update cache in the background
                    cacheManager.getOrFetch<List<String>>("vanilla_versions") { freshVersions }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val result = snackbarHostState.showSnackbar(
                        message = "Ошибка загрузки версий: ${e.message}",
                        actionLabel = "Повторить",
                        duration = SnackbarDuration.Indefinite
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        versionLoadTrigger++
                    }
                }
            }
        }
    }

    LaunchedEffect(searchQuery, selectedType, selectedVersions.size, categoryFilters.toMap()) {
        if (selectedProject == null) {
            isLoading = true
            scope.launch(Dispatchers.IO) {
                try {
                    val facets = mutableListOf<List<String>>()
                    facets.add(listOf("project_type:${selectedType.projectType}"))
                    selectedVersions.forEach { facets.add(listOf("versions:$it")) }

                    val includedCategories = categoryFilters.filter { it.value == CategoryFilterState.INCLUDED }.keys.map { "categories:${it.internalName}" }
                    val excludedCategories = categoryFilters.filter { it.value == CategoryFilterState.EXCLUDED }.keys.map { "categories:-${it.internalName}" }

                    if (includedCategories.isNotEmpty()) {
                        facets.add(includedCategories)
                    }
                    if (excludedCategories.isNotEmpty()) {
                        excludedCategories.forEach { facets.add(listOf(it)) }
                    }

                    val facetsJsonString = Json.encodeToString(facets)
                    
                    val result = modrinthApi.search(searchQuery, facetsJsonString)
                    withContext(Dispatchers.Main) {
                        searchResult = result
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        snackbarHostState.showSnackbar("Ошибка поиска: ${e.message}")
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        isLoading = false
                    }
                }
            }
        }
    }


    val contentPaddingStart by animateDpAsState(if (navPanelPosition == NavPanelPosition.Left) 96.dp else 0.dp)
    val contentPaddingBottom by animateDpAsState(if (navPanelPosition == NavPanelPosition.Bottom) 80.dp else 0.dp)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AnimatedContent(targetState = selectedProject) { project ->
                        if (project == null) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Поиск...") },
                                modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
                                singleLine = true
                            )
                        } else {
                            Text(project.title)
                        }
                    }
                },
                navigationIcon = {
                    AnimatedVisibility(visible = selectedProject != null) {
                        IconButton(onClick = { selectedProject = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                ),
                modifier = Modifier.shadow(4.dp)
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Row(modifier = Modifier.fillMaxSize().padding(start = contentPaddingStart, bottom = contentPaddingBottom)) {
                // Левое меню
                AnimatedVisibility(visible = selectedProject == null) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(250.dp)
                            .padding(16.dp)
                            .shadow(4.dp, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                            .padding(16.dp)
                    ) {
                        Text("Версии", style = MaterialTheme.typography.titleMedium)
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(versions) { version ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = version in selectedVersions,
                                        onCheckedChange = {
                                            if (it) selectedVersions.add(version) else selectedVersions.remove(version)
                                        }
                                    )
                                    Text(version, modifier = Modifier.padding(start = 4.dp))
                                 }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Категории", style = MaterialTheme.typography.titleMedium)
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(ModrinthCategory.entries) { category ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        val currentState = categoryFilters[category]
                                        val nextState = when (currentState) {
                                            null -> CategoryFilterState.INCLUDED
                                            CategoryFilterState.INCLUDED -> CategoryFilterState.EXCLUDED
                                            CategoryFilterState.EXCLUDED -> null
                                        }
                                        if (nextState == null) {
                                            categoryFilters.remove(category)
                                        } else {
                                            categoryFilters[category] = nextState
                                        }
                                    },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(category.displayName, modifier = Modifier.weight(1f))
                                    AnimatedContent(targetState = categoryFilters[category]) { state ->
                                        when (state) {
                                            CategoryFilterState.INCLUDED -> Icon(
                                                Icons.Default.Check,
                                                contentDescription = "Включено",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            CategoryFilterState.EXCLUDED -> Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Исключено",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                            null -> Spacer(Modifier.size(24.dp)) // Placeholder for alignment
                                        }
                                    }
                                }
                            }
                        }
                    }
                }


                // Основной контент
                Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Crossfade(targetState = selectedProject) { project ->
                        if (project != null) {
                            ModificationDetails(
                                project = project,
                                projectVersions = projectVersions,
                                onInstallClick = { version ->
                                    if (selectedType == ModificationType.MODPACKS) {
                                        // Запускаем установку и сразу переключаемся
                                        modpackInstaller.install(version) {
                                            onModpackInstalled()
                                        }
                                        selectedProject = null // Возвращаемся к списку
                                    } else {
                                        versionToInstall = version
                                    }
                                }
                            )
                        } else {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(searchResult?.hits ?: emptyList()) { hit ->
                                        ModificationCard(hit) {
                                            scope.launch(Dispatchers.IO) {
                                                isLoadingProject = true
                                                try {
                                                    val fullProject = modrinthApi.getProject(hit.projectId)
                                                    val versions = modrinthApi.getProjectVersions(hit.projectId)
                                                    withContext(Dispatchers.Main) {
                                                        selectedProject = fullProject
                                                        projectVersions = versions
                                                    }
                                                } catch (e: Exception) {
                                                    withContext(Dispatchers.Main) {
                                                        snackbarHostState.showSnackbar("Ошибка загрузки проекта: ${e.message}")
                                                    }
                                                } finally {
                                                    withContext(Dispatchers.Main) {
                                                        isLoadingProject = false
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (isLoadingProject) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }
            }

            versionToInstall?.let { version ->
                val compatibleBuilds = allBuilds.filter { build ->
                    version.gameVersions.any { gameVersion -> build.version.contains(gameVersion) } &&
                            (version.loaders.isEmpty() || version.loaders.any { loader -> build.type.name.contains(loader, ignoreCase = true) })
                }
                InstallModificationDialog(
                    compatibleBuilds = compatibleBuilds,
                    onDismiss = { versionToInstall = null },
                    onInstall = { build ->
                        scope.launch {
                            try {
                                val fileToDownload = version.files.firstOrNull { it.primary } ?: version.files.first()
                                val destinationDir = File(build.installPath, selectedType.installDir)
                                val destinationFile = File(destinationDir, fileToDownload.filename)
                                val task = DownloadManager.startTask("Скачивание ${version.name}")

                                modificationDownloader.download(fileToDownload, destinationFile) { progress, status ->
                                    DownloadManager.updateTask(task.id, progress, status)
                                }
                                snackbarHostState.showSnackbar("Загрузка '${version.name}' началась.")
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("Ошибка установки: ${e.message}")
                            } finally {
                                versionToInstall = null
                            }
                        }
                    }
                )
            }

            AnimatedVisibility(
                visible = navPanelPosition == NavPanelPosition.Left,
                enter = slideInHorizontally(initialOffsetX = { -it }),
                exit = slideOutHorizontally(targetOffsetX = { -it }),
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                NavigationRail(
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .height(400.dp)
                        .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp)),
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier.fillMaxHeight().padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                        ModificationType.entries.forEach { type ->
                            NavigationRailItem(
                                selected = selectedType == type,
                                onClick = { selectedType = type },
                                icon = { Icon(type.icon, contentDescription = type.displayName) },
                                label = { Text(type.displayName) }
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = navPanelPosition == NavPanelPosition.Bottom,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                NavigationBar(
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .width(500.dp)
                        .height(64.dp)
                        .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp)),
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                    ModificationType.entries.forEach { type ->
                        NavigationBarItem(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            icon = { Icon(type.icon, contentDescription = type.displayName) },
                            label = { Text(type.displayName) }
                        )
                    }
                }
            }
        }
    }
}

enum class ModificationType(
    val displayName: String,
    val icon: ImageVector,
    val projectType: String,
    val installDir: String
) {
    MODPACKS("Модпаки", Icons.Default.Home, "modpack", ""), // Modpacks are handled differently
    MODS("Моды", Icons.Default.Star, "mod", "mods"),
    RESOURCE_PACKS("Ресурс-паки", Icons.Default.Settings, "resourcepack", "resourcepacks"),
    DATAPACKS("Датапаки", Icons.Default.DateRange, "datapack", "datapacks"), // Usually per-world
    SHADERS("Шейдеры", Icons.Default.Build, "shader", "shaderpacks")
}

enum class ModrinthCategory(val displayName: String, val internalName: String) {
    ADVENTURE("Adventure", "adventure"),
    CURSED("Cursed", "cursed"),
    DECORATION("Decoration", "decoration"),
    ECONOMY("Economy", "economy"),
    EQUIPMENT("Equipment", "equipment"),
    FOOD("Food", "food"),
    GAME_MECHANICS("Game Mechanics", "game-mechanics"),
    LIBRARY("Library", "library"),
    MAGIC("Magic", "magic"),
    MANAGEMENT("Management", "management"),
    MINIGAME("Minigame", "minigame"),
    MOBS("Mobs", "mobs"),
    OPTIMIZATION("Optimization", "optimization"),
    SOCIAL("Social", "social"),
    STORAGE("Storage", "storage"),
    TECHNOLOGY("Technology", "technology"),
    TRANSPORTATION("Transportation", "transportation"),
    UTILITY("Utility", "utility"),
    WORLD_GENERATION("World Generation", "world-generation")
}
