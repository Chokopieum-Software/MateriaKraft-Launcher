package ui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.sun.management.OperatingSystemMXBean
import funlauncher.*
import funlauncher.auth.Account
import funlauncher.auth.AccountManager
import funlauncher.game.MLGDClient
import funlauncher.game.MinecraftInstaller
import funlauncher.game.VersionMetadataFetcher
import funlauncher.managers.BuildManager
import funlauncher.managers.CacheManager
import funlauncher.managers.JavaManager
import funlauncher.managers.PathManager
import funlauncher.net.DownloadManager
import funlauncher.net.JavaDownloader
import kotlinx.coroutines.*
import org.chokopieum.software.materia_launcher.generated.resources.*
import org.chokopieum.software.mlgd.StatusResponse
import org.jetbrains.compose.resources.stringResource
import state.AppState
import ui.screens.*
import ui.theme.AnimatedAppTheme
import ui.widgets.BeautifulCircularProgressIndicator
import ui.widgets.DownloadsPopup
import java.lang.management.ManagementFactory

// Перечисление для вкладок навигации в приложении.
enum class AppTab { Home, Modifications, Settings }

/**
 * Главный компонент приложения, который управляет состоянием и отображением основного интерфейса.
 * @param appState Текущее состояние приложения (настройки, сборки, аккаунты).
 * @param onSettingsChange Функция обратного вызова для сохранения измененных настроек.
 * @param buildManager Менеджер для работы со сборками Minecraft.
 * @param javaManager Менеджер для работы с установленными версиями Java.
 * @param accountManager Менеджер для работы с аккаунтами пользователей.
 * @param javaDownloader Загрузчик для скачивания версий Java.
 * @param pathManager Менеджер путей к файлам приложения.
 * @param cacheManager Менеджер кэша.
 * @param versionMetadataFetcher Фетчер метаданных версий.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun App(
    appState: AppState,
    onSettingsChange: (AppSettings) -> Unit,
    buildManager: BuildManager,
    javaManager: JavaManager,
    accountManager: AccountManager,
    javaDownloader: JavaDownloader,
    pathManager: PathManager,
    cacheManager: CacheManager,
    versionMetadataFetcher: VersionMetadataFetcher
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // --- Состояния UI ---
    var currentTab by remember { mutableStateOf(AppTab.Home) }
    val buildList = remember { mutableStateListOf(*appState.builds.toTypedArray()) }
    val buildsPendingDeletion = remember { mutableStateSetOf<String>() } // Сборки, ожидающие удаления (для анимации)
    var accounts by remember { mutableStateOf(appState.accounts) }
    var currentAccount by remember { mutableStateOf(accounts.firstOrNull()) }

    // --- Интеграция с MLGD (MateriaKraft Launcher Game Daemon) ---
    // Состояние демона, отвечающего за запуск игры
    var daemonStatus by remember { mutableStateOf(StatusResponse(running = false)) }
    // Определение текущей запущенной сборки на основе статуса демона
    val runningBuild = remember(daemonStatus, buildList.size) {
        if (daemonStatus.running) buildList.find { it.name == daemonStatus.buildName } else null
    }

    // --- Состояния видимости диалогов и окон ---
    var showAddBuildDialog by remember { mutableStateOf(false) }
    var showJavaManagerWindow by remember { mutableStateOf(false) }
    var showAccountScreen by remember { mutableStateOf(false) }
    var showDownloadsPopup by remember { mutableStateOf(false) }
    var showBuildSettingsScreen by remember { mutableStateOf<MinecraftBuild?>(null) }
    var errorDialogMessage by remember { mutableStateOf<String?>(null) } // Сообщение для диалога об ошибке
    var buildToDelete by remember { mutableStateOf<MinecraftBuild?>(null) } // Сборка, выбранная для удаления
    var showGameConsole by remember { mutableStateOf(false) }
    var showRamWarningDialog by remember { mutableStateOf<MinecraftBuild?>(null) } // Диалог с предупреждением о нехватке ОЗУ

    // --- Состояния процесса запуска ---
    var isLaunchingBuildId by remember { mutableStateOf<String?>(null) }
    var showCheckmark by remember { mutableStateOf(false) }

    // --- MLGD Integration: Poll for status ---
    LaunchedEffect(Unit) {
        while (true) {
            val newStatus = MLGDClient.getStatus()
            if (newStatus != daemonStatus) {
                daemonStatus = newStatus
                if (!newStatus.running) {
                    isLaunchingBuildId = null
                }
            }
            delay(2000) // Poll every 2 seconds
        }
    }

    // Эффект для синхронизации сборок при первом запуске.
    LaunchedEffect(Unit) {
        val (synchronizedBuilds, newCount) = withContext(Dispatchers.IO) {
            buildManager.synchronizeBuilds()
        }
        if (synchronizedBuilds.size != buildList.size || synchronizedBuilds != buildList) {
            buildList.clear()
            buildList.addAll(synchronizedBuilds)
        }
        if (newCount > 0) {
            val message = "Обнаружено $newCount новых сборок. Возможно, потребуется указать версию вручную."
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long)
        }
    }

    // Функция для принудительного обновления списка сборок из файлов.
    fun refreshBuilds() {
        scope.launch {
            val freshBuilds = withContext(Dispatchers.IO) {
                buildManager.loadBuilds()
            }
            buildList.clear()
            buildList.addAll(freshBuilds)
        }
    }

    // Эффект для отображения "галочки" после завершения всех загрузок.
    LaunchedEffect(DownloadManager.tasks.size) {
        if (DownloadManager.tasks.isEmpty() && !showCheckmark) {
            showCheckmark = true
            delay(2000)
            showCheckmark = false
        }
    }

    // Функция для запуска Minecraft через MLGD.
    suspend fun launchMinecraft(build: MinecraftBuild, javaPath: String, account: Account) {
        runCatching {
            if (appState.settings.showConsoleOnLaunch) {
                showGameConsole = true
            }
            val installer = MinecraftInstaller(build, buildManager)
            val finalMaxRam = build.maxRamMb ?: appState.settings.maxRamMb
            val finalJavaArgs = build.javaArgs ?: appState.settings.javaArgs
            val finalEnvVars = build.envVars ?: appState.settings.envVars

            val launchConfig = withContext(Dispatchers.IO) {
                installer.createLaunchConfig(
                    account = account,
                    javaPath = javaPath,
                    maxRamMb = finalMaxRam,
                    javaArgs = finalJavaArgs,
                    envVars = finalEnvVars,
                    showConsole = appState.settings.showConsoleOnLaunch
                )
            }
            val newStatus = MLGDClient.launch(launchConfig)
            daemonStatus = newStatus
        }.onFailure { e ->
            println(e.stackTraceToString())
            errorDialogMessage = "Ошибка запуска: ${e.message}"
            isLaunchingBuildId = null
        }
    }

    // Лямбда, инкапсулирующая логику подготовки к запуску (включая авто-поиск Java).
    val performLaunch: (MinecraftBuild) -> Unit = { build ->
        isLaunchingBuildId = build.name
        scope.launch {
            val useAutoJava = build.javaPath.isNullOrBlank() && appState.settings.javaPath.isBlank()

            if (useAutoJava) {
                val recommendedVersion = javaManager.getRecommendedJavaVersion(build.version)
                val installations = javaManager.findJavaInstallations()
                val exactJava = (installations.launcher + installations.system).firstOrNull { it.version == recommendedVersion && it.is64Bit }

                if (exactJava != null) {
                    launchMinecraft(build, exactJava.path, currentAccount!!)
                } else {
                    javaDownloader.downloadAndUnpack(recommendedVersion) { result ->
                        scope.launch {
                            result.fold(
                                onSuccess = { downloadedJava -> launchMinecraft(build, downloadedJava.path, currentAccount!!) },
                                onFailure = {
                                    errorDialogMessage = "Не удалось скачать Java: ${it.message}"
                                    isLaunchingBuildId = null
                                }
                            )
                        }
                    }
                }
            } else {
                val finalJavaPath = build.javaPath ?: appState.settings.javaPath
                launchMinecraft(build, finalJavaPath, currentAccount!!)
            }
        }
    }

    // Обработчик нажатия на кнопку "Играть".
    val onLaunchClick: (MinecraftBuild) -> Unit = onLaunchClick@{ build ->
        if (currentAccount == null) {
            errorDialogMessage = "Сначала выберите аккаунт!"
            return@onLaunchClick
        }
        if (daemonStatus.running) {
            scope.launch { snackbarHostState.showSnackbar("Игра '${daemonStatus.buildName}' уже запущена.") }
            return@onLaunchClick
        }

        // Проверка доступной оперативной памяти перед запуском.
        val osBean = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean
        val freeMemory = osBean.freeMemorySize / (1024 * 1024)
        val allocatedRam = build.maxRamMb ?: appState.settings.maxRamMb

        if (allocatedRam > freeMemory) {
            showRamWarningDialog = build
        } else {
            performLaunch(build)
        }
    }

    // Обработчик нажатия на кнопку удаления сборки.
    val onDeleteBuildClick: (MinecraftBuild) -> Unit = { build ->
        buildToDelete = build
    }

    // Обработчик нажатия на кнопку настроек сборки.
    val onSettingsBuildClick: (MinecraftBuild) -> Unit = { build ->
        showBuildSettingsScreen = build
    }

    // Основная тема и разметка приложения.
    AnimatedAppTheme(appState.settings.theme) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.background
        ) {
            // Анимированные отступы для контента в зависимости от положения навигационной панели.
            val contentPaddingStart by animateDpAsState(if (appState.settings.navPanelPosition == NavPanelPosition.Left && currentTab != AppTab.Modifications) 96.dp else 0.dp)
            val contentPaddingBottom by animateDpAsState(if (appState.settings.navPanelPosition == NavPanelPosition.Bottom && currentTab != AppTab.Modifications) 80.dp else 0.dp)

            Box(modifier = Modifier.fillMaxSize()) {
                // Плавный переход между экранами (вкладками).
                Box(modifier = Modifier.fillMaxSize().padding(start = contentPaddingStart, bottom = contentPaddingBottom)) {
                    Crossfade(targetState = currentTab, animationSpec = tween(300)) { tab ->
                        when (tab) {
                            AppTab.Home -> HomeScreen(
                                builds = buildList,
                                runningBuild = runningBuild,
                                onLaunchClick = onLaunchClick,
                                onOpenFolderClick = { openFolder(it.installPath) },
                                onAddBuildClick = { showAddBuildDialog = true },
                                isLaunchingBuildId = isLaunchingBuildId,
                                onDeleteBuildClick = onDeleteBuildClick,
                                onSettingsBuildClick = onSettingsBuildClick,
                                currentAccount = currentAccount,
                                onOpenAccountManager = { showAccountScreen = true },
                                buildsPendingDeletion = buildsPendingDeletion
                            )
                            AppTab.Modifications -> ModificationsScreen(
                                onBack = { currentTab = AppTab.Home },
                                navPanelPosition = appState.settings.navPanelPosition,
                                buildManager = buildManager,
                                onModpackInstalled = {
                                    refreshBuilds()
                                    currentTab = AppTab.Home
                                },
                                pathManager = pathManager,
                                snackbarHostState = snackbarHostState,
                                cacheManager = cacheManager
                            )
                            AppTab.Settings -> SettingsTab(
                                currentSettings = appState.settings,
                                onSave = onSettingsChange,
                                onOpenJavaManager = { showJavaManagerWindow = true },
                                accountManager = accountManager,
                                coroutineScope = scope,
                                versionMetadataFetcher = versionMetadataFetcher,
                                snackbarHostState = snackbarHostState
                            )
                        }
                    }
                }

                // Боковая навигационная панель (слева).
                AnimatedVisibility(
                    visible = appState.settings.navPanelPosition == NavPanelPosition.Left && currentTab != AppTab.Modifications,
                    enter = slideInHorizontally(initialOffsetX = { -it }),
                    exit = slideOutHorizontally(targetOffsetX = { -it }),
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    NavigationRail(
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .height(300.dp)
                            .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp)),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            modifier = Modifier.fillMaxHeight().padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            NavigationRailItem(
                                selected = currentTab == AppTab.Home,
                                onClick = { currentTab = AppTab.Home },
                                icon = { Icon(Icons.Default.Home, contentDescription = stringResource(Res.string.tab_home)) },
                                label = { Text(stringResource(Res.string.tab_home)) }
                            )
                            Spacer(Modifier.height(16.dp))
                            NavigationRailItem(
                                selected = currentTab == AppTab.Modifications,
                                onClick = { currentTab = AppTab.Modifications },
                                icon = { Icon(Icons.Default.Build, contentDescription = stringResource(Res.string.tab_modifications)) },
                                label = { Text(stringResource(Res.string.tab_modifications)) }
                            )
                            Spacer(Modifier.height(16.dp))
                            NavigationRailItem(
                                selected = currentTab == AppTab.Settings,
                                onClick = { currentTab = AppTab.Settings },
                                icon = { Icon(Icons.Default.Settings, contentDescription = stringResource(Res.string.tab_settings)) },
                                label = { Text(stringResource(Res.string.tab_settings)) }
                            )
                        }
                    }
                }

                // Нижняя навигационная панель.
                AnimatedVisibility(
                    visible = appState.settings.navPanelPosition == NavPanelPosition.Bottom && currentTab != AppTab.Modifications,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it }),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    NavigationBar(
                        modifier = Modifier
                            .padding(bottom = 16.dp)
                            .width(300.dp)
                            .height(64.dp)
                            .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp)),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        NavigationBarItem(
                            selected = currentTab == AppTab.Home,
                            onClick = { currentTab = AppTab.Home },
                            icon = { Icon(Icons.Default.Home, contentDescription = stringResource(Res.string.tab_home)) },
                            label = { Text(stringResource(Res.string.tab_home)) }
                        )
                        NavigationBarItem(
                            selected = currentTab == AppTab.Modifications,
                            onClick = { currentTab = AppTab.Modifications },
                            icon = { Icon(Icons.Default.Build, contentDescription = stringResource(Res.string.tab_modifications)) },
                            label = { Text(stringResource(Res.string.tab_modifications)) }
                        )
                        NavigationBarItem(
                            selected = currentTab == AppTab.Settings,
                            onClick = { currentTab = AppTab.Settings },
                            icon = { Icon(Icons.Default.Settings, contentDescription = stringResource(Res.string.tab_settings)) },
                            label = { Text(stringResource(Res.string.tab_settings)) }
                        )
                    }
                }

                // Кнопка (FAB) для отображения статуса и списка загрузок.
                val hasActiveDownloads = DownloadManager.tasks.isNotEmpty()
                AnimatedVisibility(
                    visible = hasActiveDownloads || showCheckmark,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
                ) {
                    Box {
                        FloatingActionButton(
                            onClick = { showDownloadsPopup = !showDownloadsPopup },
                            shape = CircleShape,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            when {
                                showCheckmark -> Icon(Icons.Default.Check, contentDescription = "Загрузка завершена")
                                hasActiveDownloads -> BeautifulCircularProgressIndicator(
                                    size = 24.dp,
                                    strokeWidth = 3.dp,
                                    primaryColor = MaterialTheme.colorScheme.primary,
                                    secondaryColor = MaterialTheme.colorScheme.tertiary
                                )
                                else -> Icon(Icons.Default.Download, contentDescription = "Загрузки")
                            }
                        }
                        if (showDownloadsPopup) {
                            DownloadsPopup(onDismissRequest = { showDownloadsPopup = false })
                        }
                    }
                }
            }
        }

        // Компонент для отображения всех оверлеев (диалоги, всплывающие окна).
        AppOverlays(
            appState = appState,
            showJavaManagerWindow = showJavaManagerWindow,
            onCloseJavaManagerWindow = { showJavaManagerWindow = false },
            javaManager = javaManager,
            javaDownloader = javaDownloader,
            showAddBuildDialog = showAddBuildDialog,
            onCloseAddBuildDialog = { showAddBuildDialog = false },
            onAddBuild = { name, version, type, imagePath ->
                scope.launch {
                    runCatching {
                        buildManager.addBuild(name, version, type, imagePath)
                        refreshBuilds()
                        showAddBuildDialog = false
                    }.onFailure { e ->
                        errorDialogMessage = e.message
                    }
                }
            },
            pathManager = pathManager,
            buildSettingsToShow = showBuildSettingsScreen,
            onCloseBuildSettings = { showBuildSettingsScreen = null },
            onSaveBuildSettings = { newName, newVersion, newType, newImagePath, javaPath, maxRam, javaArgs, envVars ->
                scope.launch {
                    runCatching {
                        showBuildSettingsScreen?.let {
                            buildManager.updateBuildSettings(
                                oldName = it.name,
                                newName = newName,
                                newVersion = newVersion,
                                newType = newType,
                                newImagePath = newImagePath,
                                newJavaPath = javaPath,
                                newMaxRam = maxRam,
                                newJavaArgs = javaArgs,
                                newEnvVars = envVars
                            )
                        }
                        refreshBuilds()
                        showBuildSettingsScreen = null
                    }.onFailure { e ->
                        errorDialogMessage = e.message
                    }
                }
            },
            showAccountScreen = showAccountScreen,
            onCloseAccountScreen = { showAccountScreen = false },
            onAccountSelected = {
                currentAccount = it
                showAccountScreen = false
            },
            accountManager = accountManager,
            showGameConsole = showGameConsole,
            onCloseGameConsole = { showGameConsole = false },
            ramWarningDialogToShow = showRamWarningDialog,
            onCloseRamWarningDialog = { showRamWarningDialog = null; isLaunchingBuildId = null },
            onConfirmRamWarning = { build ->
                showRamWarningDialog = null
                performLaunch(build)
            },
            errorDialogMessage = errorDialogMessage,
            onDismissErrorDialog = { errorDialogMessage = null },
            buildToDelete = buildToDelete,
            onDismissDeleteDialog = { buildToDelete = null },
            onConfirmDelete = { build ->
                scope.launch {
                    buildsPendingDeletion.add(build.name)
                    buildToDelete = null
                    delay(400)
                    buildManager.deleteBuild(build.name)
                    buildList.removeIf { it.name == build.name }
                    buildsPendingDeletion.remove(build.name)
                }
            }
        )
    }
}
