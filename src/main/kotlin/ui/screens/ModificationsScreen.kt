package ui.screens

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
import kotlinx.serialization.encodeToString
import org.jetbrains.compose.resources.stringResource
import org.chokopieum.software.materia_launcher.generated.resources.*
import ui.widgets.ImageLoader
import java.io.File

// Добавлены импорты для прокрутки
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

enum class FilterState {
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
            Text(stringResource(Res.string.versions), style = MaterialTheme.typography.titleLarge)
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
                        Text(stringResource(Res.string.install))
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
        title = { Text(stringResource(Res.string.select_a_build)) },
        text = {
            if (compatibleBuilds.isEmpty()) {
                Text(stringResource(Res.string.no_compatible_builds))
            } else {
                LazyColumn {
                    items(compatibleBuilds) { build ->
                        Text(build.name, modifier = Modifier.clickable { onInstall(build) }.fillMaxWidth().padding(12.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
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
    val modpackInstaller = remember { ModpackInstaller(buildManager, modrinthApi, pathManager, ApplicationScope.scope) }

    var searchQuery by remember { mutableStateOf("") }
    var searchResult by remember { mutableStateOf<SearchResult?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    var selectedProject by remember { mutableStateOf<Project?>(null) }
    var projectVersions by remember { mutableStateOf<List<Version>>(emptyList()) }
    var isLoadingProject by remember { mutableStateOf(false) }

    var versionToInstall by remember { mutableStateOf<Version?>(null) }
    val allBuilds = remember { mutableStateListOf<MinecraftBuild>() }

    var selectedType by remember { mutableStateOf(ModificationType.MODS) } // Default to MODS
    val allVanillaVersions = remember { mutableStateListOf<String>() } // Храним все версии
    val selectedVersions = remember { mutableStateListOf<String>() }

    val allCategories = remember { mutableStateListOf<ModrinthCategoryTag>() }
    val allLoaders = remember { mutableStateListOf<ModrinthLoaderTag>() }

    val filteredCategories = remember { mutableStateListOf<ModrinthCategoryTag>() }
    val filteredLoaders = remember { mutableStateListOf<ModrinthLoaderTag>() }

    val selectedCategories = remember { mutableStateMapOf<String, FilterState>() } // Key is category.name
    val selectedLoaders = remember { mutableStateMapOf<String, FilterState>() } // Key is loader.name

    var versionLoadTrigger by remember { mutableStateOf(0) }

    // Состояния для сворачиваемых списков
    var versionsExpanded by remember { mutableStateOf(true) }
    var categoriesExpanded by remember { mutableStateOf(true) }
    var loadersExpanded by remember { mutableStateOf(true) }

    // Состояние для отображения всех версий (релизы vs все)
    var showOnlyReleaseVersions by remember { mutableStateOf(true) }


    // --- String Resources ---
    val strErrorVersionLoad = stringResource(Res.string.error_version_load)
    val strRetry = stringResource(Res.string.retry)
    val strErrorSearch = stringResource(Res.string.error_search)
    val strErrorProjectLoad = stringResource(Res.string.error_project_load)
    val strDownloadStarted = stringResource(Res.string.download_started)
    val strErrorInstall = stringResource(Res.string.error_install)
    val unknownError = "Unknown error"
    val strLoaders = stringResource(Res.string.loaders) // New string resource

    LaunchedEffect(Unit) {
        allBuilds.addAll(buildManager.loadBuilds())
        // Fetch all categories and loaders once
        scope.launch(Dispatchers.IO) {
            println("ModificationsScreen: Начинаем загрузку категорий...")
            try {
                val fetchedCategories = modrinthApi.getCategories()
                withContext(Dispatchers.Main) {
                    allCategories.addAll(fetchedCategories)
                    println("ModificationsScreen: Загружено ${fetchedCategories.size} категорий.")
                }
            } catch (e: Exception) {
                println("ModificationsScreen: Ошибка загрузки категорий: ${e.stackTraceToString()}")
                withContext(Dispatchers.Main) {
                    snackbarHostState.showSnackbar("Ошибка загрузки категорий: ${e.message ?: unknownError}")
                }
            }
            println("ModificationsScreen: Начинаем загрузку лоадеров...")
            try {
                val fetchedLoaders = modrinthApi.getLoaders()
                withContext(Dispatchers.Main) {
                    allLoaders.addAll(fetchedLoaders)
                    println("ModificationsScreen: Загружено ${fetchedLoaders.size} лоадеров.")
                }
            } catch (e: Exception) {
                println("ModificationsScreen: Ошибка загрузки лоадеров: ${e.stackTraceToString()}")
                withContext(Dispatchers.Main) {
                    snackbarHostState.showSnackbar("Ошибка загрузки лоадеров: ${e.message ?: unknownError}")
                }
            }
        }
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
                    allVanillaVersions.clear()
                    allVanillaVersions.addAll(cachedVersions)
                }
            }

            // Background update
            try {
                val freshVersions = versionMetadataFetcher.getVanillaVersions()
                if (freshVersions.sorted() != cachedVersions?.sorted()) {
                    withContext(Dispatchers.Main) {
                        allVanillaVersions.clear()
                        allVanillaVersions.addAll(freshVersions)
                    }
                    // Update cache in the background
                    cacheManager.getOrFetch<List<String>>("vanilla_versions") { freshVersions }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val result = snackbarHostState.showSnackbar(
                        message = strErrorVersionLoad.format(e.message ?: unknownError),
                        actionLabel = strRetry,
                        duration = SnackbarDuration.Indefinite
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        versionLoadTrigger++
                    }
                }
            }
        }
    }

    // Filter categories and loaders based on selectedType
    LaunchedEffect(selectedType, allCategories.size, allLoaders.size) {
        filteredCategories.clear()
        selectedCategories.clear() // Clear selections when type changes
        filteredLoaders.clear()
        selectedLoaders.clear() // Clear selections when type changes

        val targetProjectType = if (selectedType == ModificationType.DATAPACKS) {
            ModificationType.MODS.projectType // Datapacks use mod project type for categories
        } else {
            selectedType.projectType
        }

        filteredCategories.addAll(allCategories.filter { it.project_type == targetProjectType })

        // Исправленный список серверных ядер, которые нужно исключать из модов
        val serverCoreLoadersToExclude = setOf("paper", "spigot", "purpur")
        
        filteredLoaders.addAll(allLoaders.filter { loader ->
            loader.supported_project_types.contains(selectedType.projectType) &&
            !(selectedType == ModificationType.MODS && serverCoreLoadersToExclude.contains(loader.name.lowercase()))
        })
    }

    // Отфильтрованные версии для отображения (релизы vs все)
    val displayedVersions = remember(allVanillaVersions, showOnlyReleaseVersions) {
        if (showOnlyReleaseVersions) {
            // Более точная фильтрация релизных версий
            allVanillaVersions.filter { version ->
                !version.contains("snapshot", ignoreCase = true) &&
                !version.contains("pre-release", ignoreCase = true) &&
                !version.contains("rc", ignoreCase = true) &&
                !version.contains("alpha", ignoreCase = true) &&
                !version.contains("beta", ignoreCase = true)
            }
        } else {
            allVanillaVersions
        }
    }

    // Состояния для "Показать больше"
    var showAllVersionsList by remember { mutableStateOf(false) }
    var showAllCategoriesList by remember { mutableStateOf(false) }
    var showAllLoadersList by remember { mutableStateOf(false) }

    val contentPaddingStart by animateDpAsState(if (navPanelPosition == NavPanelPosition.Left) 96.dp else 0.dp)
    val contentPaddingBottom by animateDpAsState(if (navPanelPosition == NavPanelPosition.Bottom) 80.dp else 0.dp)

    // LaunchedEffect для поиска
    LaunchedEffect(searchQuery, selectedType, selectedVersions, selectedCategories, selectedLoaders) {
        if (selectedProject != null) return@LaunchedEffect // Не ищем, если открыт проект

        isLoading = true
        searchResult = null // Очищаем предыдущие результаты

        val facetsList = mutableListOf<List<String>>()

        // Project Type
        val actualProjectType = if (selectedType == ModificationType.DATAPACKS) {
            ModificationType.MODS.projectType // Datapacks используют project_type 'mod' для поиска
        } else {
            selectedType.projectType
        }
        facetsList.add(listOf("project_type:$actualProjectType"))

        // Game Versions
        if (selectedVersions.isNotEmpty()) {
            facetsList.add(selectedVersions.map { "versions:$it" })
        }

        // Categories
        val includedCategories = selectedCategories.filter { it.value == FilterState.INCLUDED }.keys
        if (includedCategories.isNotEmpty()) {
            facetsList.add(includedCategories.map { "categories:$it" })
        }

        // Loaders
        val includedLoaders = selectedLoaders.filter { it.value == FilterState.INCLUDED }.keys
        if (includedLoaders.isNotEmpty()) {
            facetsList.add(includedLoaders.map { "loaders:$it" })
        }

        val facetsJson = Json.encodeToString(facetsList)

        try {
            val result = withContext(Dispatchers.IO) {
                modrinthApi.search(query = searchQuery, facets = facetsJson)
            }
            searchResult = result
        } catch (e: Exception) {
            snackbarHostState.showSnackbar(strErrorSearch.format(e.message ?: unknownError))
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AnimatedContent(targetState = selectedProject) { project ->
                        if (project == null) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text(stringResource(Res.string.search_placeholder)) },
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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back))
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
                            .verticalScroll(rememberScrollState()) // Делаем всю колонку прокручиваемой
                    ) {
                        // Секция версий
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { versionsExpanded = !versionsExpanded },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(Res.string.versions), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            IconButton(onClick = { versionsExpanded = !versionsExpanded }) {
                                Icon(
                                    imageVector = if (versionsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (versionsExpanded) "Свернуть версии" else "Развернуть версии"
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))

                        AnimatedVisibility(visible = versionsExpanded) {
                            Column {
                                val versionsToDisplay = if (showAllVersionsList) displayedVersions else displayedVersions.take(7)
                                versionsToDisplay.forEach { version ->
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        tonalElevation = 2.dp
                                    ) {
                                        Row(
                                            modifier = Modifier.clickable {
                                                if (version in selectedVersions) selectedVersions.remove(version) else selectedVersions.add(version)
                                            }.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
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
                                if (displayedVersions.size > 7) {
                                    TextButton(onClick = { showAllVersionsList = !showAllVersionsList }) {
                                        Text(if (showAllVersionsList) "Свернуть" else "Показать больше (${displayedVersions.size - 7})")
                                    }
                                }
                                TextButton(onClick = { showOnlyReleaseVersions = !showOnlyReleaseVersions }) {
                                    Text(if (showOnlyReleaseVersions) "Показать все версии" else "Показать только релизы")
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Секция категорий
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { categoriesExpanded = !categoriesExpanded },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(Res.string.categories), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            IconButton(onClick = { categoriesExpanded = !categoriesExpanded }) {
                                Icon(
                                    imageVector = if (categoriesExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (categoriesExpanded) "Свернуть категории" else "Развернуть категории"
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))

                        AnimatedVisibility(visible = categoriesExpanded) {
                            Column {
                                val categoriesToDisplay = if (showAllCategoriesList) filteredCategories else filteredCategories.take(7)
                                categoriesToDisplay.forEach { category ->
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        tonalElevation = 2.dp
                                    ) {
                                        val categoryIcon by produceState<ImageBitmap?>(null, category.icon) {
                                            value = ImageLoader.loadImage(category.icon)
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                val currentState = selectedCategories[category.name]
                                                val nextState = when (currentState) {
                                                    null -> FilterState.INCLUDED
                                                    FilterState.INCLUDED -> FilterState.EXCLUDED
                                                    FilterState.EXCLUDED -> null
                                                }
                                                if (nextState == null) {
                                                    selectedCategories.remove(category.name)
                                                } else {
                                                    selectedCategories[category.name] = nextState
                                                }
                                            }.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            categoryIcon?.let {
                                                Image(
                                                    bitmap = it,
                                                    contentDescription = category.pretty_name,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            } ?: Icon(
                                                imageVector = Icons.Default.Category, // Placeholder icon
                                                contentDescription = category.pretty_name,
                                                modifier = Modifier.size(24.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(category.pretty_name ?: category.name, modifier = Modifier.weight(1f).padding(start = 4.dp))
                                            AnimatedContent(targetState = selectedCategories[category.name]) { state ->
                                                when (state) {
                                                    FilterState.INCLUDED -> Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = stringResource(Res.string.included),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                    FilterState.EXCLUDED -> Icon(
                                                        Icons.Default.Close,
                                                        contentDescription = stringResource(Res.string.excluded),
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                    null -> Spacer(Modifier.size(24.dp)) // Placeholder for alignment
                                                }
                                            }
                                        }
                                    }
                                }
                                if (filteredCategories.size > 7) {
                                    TextButton(onClick = { showAllCategoriesList = !showAllCategoriesList }) {
                                        Text(if (showAllCategoriesList) "Свернуть" else "Показать больше (${filteredCategories.size - 7})")
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Секция загрузчиков
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { loadersExpanded = !loadersExpanded },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(strLoaders, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            IconButton(onClick = { loadersExpanded = !loadersExpanded }) {
                                Icon(
                                    imageVector = if (loadersExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (loadersExpanded) "Свернуть загрузчики" else "Развернуть загрузчики"
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))

                        AnimatedVisibility(visible = loadersExpanded) {
                            Column {
                                val loadersToDisplay = if (showAllLoadersList) filteredLoaders else filteredLoaders.take(7)
                                loadersToDisplay.forEach { loader ->
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        tonalElevation = 2.dp
                                    ) {
                                        val loaderIcon by produceState<ImageBitmap?>(null, loader.icon) {
                                            value = ImageLoader.loadImage(loader.icon)
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                val currentState = selectedLoaders[loader.name]
                                                val nextState = when (currentState) {
                                                    null -> FilterState.INCLUDED
                                                    FilterState.INCLUDED -> FilterState.EXCLUDED
                                                    FilterState.EXCLUDED -> null
                                                }
                                                if (nextState == null) {
                                                    selectedLoaders.remove(loader.name)
                                                } else {
                                                    selectedLoaders[loader.name] = nextState
                                                }
                                            }.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            loaderIcon?.let {
                                                Image(
                                                    bitmap = it,
                                                    contentDescription = loader.pretty_name,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            } ?: Icon(
                                                imageVector = Icons.Default.Extension, // Placeholder icon
                                                contentDescription = loader.pretty_name,
                                                modifier = Modifier.size(24.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(loader.pretty_name ?: loader.name, modifier = Modifier.weight(1f).padding(start = 4.dp))
                                            AnimatedContent(targetState = selectedLoaders[loader.name]) { state ->
                                                when (state) {
                                                    FilterState.INCLUDED -> Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = stringResource(Res.string.included),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                    FilterState.EXCLUDED -> Icon(
                                                        Icons.Default.Close,
                                                        contentDescription = stringResource(Res.string.excluded),
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                    null -> Spacer(Modifier.size(24.dp)) // Placeholder for alignment
                                                }
                                            }
                                        }
                                    }
                                }
                                if (filteredLoaders.size > 7) {
                                    TextButton(onClick = { showAllLoadersList = !showAllLoadersList }) {
                                        Text(if (showAllLoadersList) "Свернуть" else "Показать больше (${filteredLoaders.size - 7})")
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
                                                        snackbarHostState.showSnackbar(strErrorProjectLoad.format(e.message ?: unknownError))
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
                                snackbarHostState.showSnackbar(strDownloadStarted.format(version.name))
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar(strErrorInstall.format(e.message ?: unknownError))
                            } finally {
                                versionToInstall = null
                            }
                        }
                    }
                )
            }

            AnimatedVisibility(
                visible = navPanelPosition == NavPanelPosition.Left,
                enter = slideInHorizontally(initialOffsetX = { offset -> -offset }),
                exit = slideOutHorizontally(targetOffsetX = { offset -> -offset }),
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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back))
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
                enter = slideInVertically(initialOffsetY = { offset -> offset }),
                exit = slideOutVertically(targetOffsetY = { offset -> offset }),
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back))
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
    DATAPACKS("Датапаки", Icons.Default.DateRange, "datapack", "datapacks"), // projectType will be 'mod' for search
    SHADERS("Шейдеры", Icons.Default.Build, "shader", "shaderpacks")
}
