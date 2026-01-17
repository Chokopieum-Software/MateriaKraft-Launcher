/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */
import org.jetbrains.compose.resources.ExperimentalResourceApi
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.sun.management.OperatingSystemMXBean
import funlauncher.*
import funlauncher.auth.Account
import funlauncher.auth.AccountManager
import funlauncher.game.MLGDClient
import funlauncher.game.MinecraftInstaller
import funlauncher.managers.BuildManager
import funlauncher.managers.CacheManager
import funlauncher.managers.JavaManager
import funlauncher.managers.PathManager
import funlauncher.net.DownloadManager
import funlauncher.net.JavaDownloader
import funlauncher.net.ModrinthApi
import kotlinx.coroutines.*
import org.chokopieum.software.mlgd.StatusResponse
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.chokopieum.software.materia_launcher.generated.resources.*
import ui.dialogs.AddBuildDialog
import ui.dialogs.ConfirmDeleteDialog
import ui.dialogs.ErrorDialog
import ui.dialogs.RamWarningDialog
import ui.screens.*
import ui.windows.GameConsoleWindow
import ui.widgets.BeautifulCircularProgressIndicator
import ui.widgets.DownloadsPopup
import java.awt.Dimension
import java.awt.Image
import java.awt.Toolkit
import java.lang.management.ManagementFactory
import java.time.Month
import java.time.OffsetDateTime
import java.util.Locale
import java.util.Properties
import javax.imageio.ImageIO
import javax.swing.*
import kotlin.math.min

// Перечисление для вкладок навигации в приложении.
enum class AppTab { Home, Modifications, Settings }

// Цветовая схема "Day" (Светлая, с акцентными цветами).
val dayColorScheme = lightColorScheme(
    primary = Color(0xFFC06C84),
    secondary = Color(0xFF6C5B7B),
    tertiary = Color(0xFF355C7D),
    background = Color(0xFFF8F3F1),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF333333),
    onSurface = Color(0xFF333333),
)

// Цветовая схема "Amoled" (Темная, с черным фоном для AMOLED-экранов).
val amoledColorScheme = darkColorScheme(
    primary = Color(0xFFC06C84),
    secondary = Color(0xFF6C5B7B),
    tertiary = Color(0xFF355C7D),
    background = Color.Black,
    surface = Color(0xFF1A1A1A),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFFE0E0E0),
    onSurface = Color(0xFFE0E0E0),
)

/**
 * Компонент, который применяет выбранную цветовую схему с анимацией.
 * @param theme Выбранная тема оформления.
 * @param content Содержимое, к которому применяется тема.
 */
@Composable
fun AnimatedAppTheme(
    theme: Theme,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val colors = when (theme) {
        Theme.System -> if (isSystemDark) darkColorScheme() else lightColorScheme()
        Theme.Light -> lightColorScheme()
        Theme.Dark -> darkColorScheme()
        Theme.Day -> dayColorScheme
        Theme.Amoled -> amoledColorScheme
    }

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}

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
    cacheManager: CacheManager
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
                                onOpenJavaManager = { showJavaManagerWindow = true }
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

/**
 * Компонент, который управляет отображением всех модальных окон и диалогов поверх основного интерфейса.
 */
@Composable
fun AppOverlays(
    appState: AppState,
    showJavaManagerWindow: Boolean,
    onCloseJavaManagerWindow: () -> Unit,
    javaManager: JavaManager,
    javaDownloader: JavaDownloader,
    showAddBuildDialog: Boolean,
    onCloseAddBuildDialog: () -> Unit,
    onAddBuild: (String, String, BuildType, String?) -> Unit,
    pathManager: PathManager,
    buildSettingsToShow: MinecraftBuild?,
    onCloseBuildSettings: () -> Unit,
    onSaveBuildSettings: (String, String, BuildType, String?, String?, Int?, String?, String?) -> Unit,
    showAccountScreen: Boolean,
    onCloseAccountScreen: () -> Unit,
    onAccountSelected: (Account) -> Unit,
    accountManager: AccountManager,
    showGameConsole: Boolean,
    onCloseGameConsole: () -> Unit,
    ramWarningDialogToShow: MinecraftBuild?,
    onCloseRamWarningDialog: () -> Unit,
    onConfirmRamWarning: (MinecraftBuild) -> Unit,
    errorDialogMessage: String?,
    onDismissErrorDialog: () -> Unit,
    buildToDelete: MinecraftBuild?,
    onDismissDeleteDialog: () -> Unit,
    onConfirmDelete: (MinecraftBuild) -> Unit
) {
    if (showJavaManagerWindow) {
        JavaManagerWindow(
            onCloseRequest = onCloseJavaManagerWindow,
            javaManager = javaManager,
            javaDownloader = javaDownloader,
            appSettings = appState.settings
        )
    }
    if (showAddBuildDialog) {
        AddBuildDialog(
            onDismiss = onCloseAddBuildDialog,
            onAdd = onAddBuild,
            pathManager = pathManager
        )
    }
    buildSettingsToShow?.let { build ->
        BuildSettingsScreen(
            build = build,
            globalSettings = appState.settings,
            onDismiss = onCloseBuildSettings,
            onSave = onSaveBuildSettings,
            pathManager = pathManager
        )
    }
    if (showAccountScreen) {
        AccountScreen(
            accountManager = accountManager,
            onDismiss = onCloseAccountScreen,
            onAccountSelected = onAccountSelected
        )
    }
    if (showGameConsole) {
        GameConsoleWindow(onCloseRequest = onCloseGameConsole)
    }
    ramWarningDialogToShow?.let { build ->
        RamWarningDialog(
            build = build,
            onDismiss = onCloseRamWarningDialog,
            onConfirm = { onConfirmRamWarning(build) }
        )
    }
    errorDialogMessage?.let { message ->
        ErrorDialog(
            message = message,
            onDismiss = onDismissErrorDialog
        )
    }
    buildToDelete?.let { build ->
        ConfirmDeleteDialog(
            build = build,
            onDismiss = onDismissDeleteDialog,
            onConfirm = { onConfirmDelete(build) }
        )
    }
}

// Запечатанный класс для представления различных экранов приложения.
private sealed class Screen {
    object Splash : Screen() // Экран-заставка
    object FirstRunWizard : Screen() // Мастер первоначальной настройки
    data class MainApp(val state: AppState) : Screen() // Основной экран приложения
}

// Класс данных, хранящий полное состояние приложения.
data class AppState(
    val settings: AppSettings,
    val builds: List<MinecraftBuild>,
    val accounts: List<Account>
)

// Флаг, указывающий, что основной контент готов к отображению (используется для скрытия сплеш-скрина).
var isContentReady by mutableStateOf(false)

private fun createAndShowSplashScreen(statusLabel: JLabel): JWindow? {
    return runCatching {
        JWindow().apply {
            val props = Properties().apply {
                Thread.currentThread().contextClassLoader.getResourceAsStream("app.properties")?.use(::load)
            }
            val version = props.getProperty("version", "Unknown")
            val buildNumber = props.getProperty("buildNumber", "N/A")
            val versionText = "$version ($buildNumber)"

            val versionLabel = JLabel(versionText, SwingConstants.RIGHT).apply {
                foreground = java.awt.Color.WHITE
            }
            statusLabel.apply {
                foreground = java.awt.Color.WHITE
                horizontalAlignment = SwingConstants.RIGHT
            }

            val originalImage = ImageIO.read(Thread.currentThread().contextClassLoader.getResource("banner.png"))
            val screenSize = Toolkit.getDefaultToolkit().screenSize
            val targetWidth = screenSize.width / 2.5
            val targetHeight = screenSize.height / 2.5
            val ratio = min(targetWidth / originalImage.width, targetHeight / originalImage.height)
            val newWidth = (originalImage.width * ratio).toInt()
            val newHeight = (originalImage.height * ratio).toInt()
            val finalImage = originalImage.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH)

            contentPane = JLayeredPane().apply {
                preferredSize = Dimension(newWidth, newHeight)
                val margin = 10
                
                add(JLabel(ImageIcon(finalImage)).apply {
                    setBounds(0, 0, newWidth, newHeight)
                }, JLayeredPane.DEFAULT_LAYER)
                
                add(statusLabel.apply {
                    setBounds(0, newHeight - 30 - margin, newWidth - margin, 20)
                }, JLayeredPane.PALETTE_LAYER)

                add(versionLabel.apply {
                    setBounds(0, newHeight - 15 - margin, newWidth - margin, 20)
                }, JLayeredPane.PALETTE_LAYER)
            }
            pack()
            setLocationRelativeTo(null)
            isVisible = true
        }
    }.onFailure {
        println("Failed to create splash screen: ${it.stackTraceToString()}")
    }.getOrNull()
}

// Главная функция, точка входа в приложение.
@OptIn(ExperimentalResourceApi::class)
fun main() {
    runBlocking(Dispatchers.IO) {
        val pathManager = PathManager(PathManager.getDefaultAppDataDirectory())
        val settingsManager = SettingsManager(pathManager)
        val settings = settingsManager.loadSettings()
        Locale.setDefault(Locale(settings.language))
    }

    // Попытка установить оптимальный рендер-API (Vulkan или OpenGL) для Skiko.
    runCatching {
        val os = System.getProperty("os.name").lowercase()
        if (!os.contains("mac")) {
            val renderApi = try {
                Class.forName("org.jetbrains.skiko.vulkan.VulkanWindow")
                "VULKAN"
            } catch (_: Throwable) {
                "OPENGL"
            }
            System.setProperty("skiko.renderApi", renderApi)
            println("Using render API: $renderApi")
        }
    }

    // Создание и отображение сплеш-скрина с помощью Swing.
    val statusLabel = JLabel("Initializing...")
    val splash = createAndShowSplashScreen(statusLabel)

    // Запуск и проверка доступности демона MLGD.
    runBlocking {
        val pathManager by lazy { PathManager(PathManager.getDefaultAppDataDirectory()) }
        SwingUtilities.invokeLater { statusLabel.text = "Starting daemon..." }
        runCatching {
            MLGDClient.ensureDaemonRunning(pathManager.getLauncherDir())
        }.onFailure { e ->
            println(e.stackTraceToString())
            splash?.isVisible = false
            JOptionPane.showMessageDialog(null, "Could not start or connect to MLGD daemon.\n${e.message}", "Fatal Error", JOptionPane.ERROR_MESSAGE)
            return@runBlocking
        }
    }

    // Запуск основного цикла приложения Compose.
    application {
        var currentScreen by remember { mutableStateOf<Screen>(Screen.Splash) }
        var appState by remember { mutableStateOf<AppState?>(null) }
        val scope = rememberCoroutineScope()

        // Выбор иконки приложения в зависимости от времени года (зима/не зима).
        val month = OffsetDateTime.now().month // TODO: Перенести в ресурсы
        val isWinter = month == Month.DECEMBER || month == Month.JANUARY || month == Month.FEBRUARY
        val icon = if (isWinter) {
            painterResource(Res.drawable.MLicon_snow)
        } else {
            painterResource(Res.drawable.MLicon)
        }

        // Ленивая инициализация менеджеров.
        val pathManager by lazy { PathManager(PathManager.getDefaultAppDataDirectory()) }
        val settingsManager by lazy { SettingsManager(pathManager) }
        val buildManager by lazy { BuildManager(pathManager) }
        val accountManager by lazy { AccountManager(pathManager) }
        val javaManager by lazy { JavaManager(pathManager) }
        val javaDownloader by lazy { JavaDownloader(pathManager, javaManager) }
        val cacheManager by lazy { CacheManager(pathManager) }
        val modrinthApi by lazy { ModrinthApi(cacheManager) }

        // Эффект для скрытия сплеш-скрина, когда контент готов.
        LaunchedEffect(isContentReady) {
            if (isContentReady) {
                splash?.isVisible = false
                splash?.dispose()
            }
        }

        // Главный эффект, который управляет переключением между экранами (Splash -> Wizard/MainApp).
        LaunchedEffect(currentScreen) {
            if (currentScreen is Screen.Splash) {
                isContentReady = false
                scope.launch(Dispatchers.IO) {
                    if (pathManager.isFirstRunRequired()) {
                        withContext(Dispatchers.Main) {
                            currentScreen = Screen.FirstRunWizard
                        }
                    } else {
                        // Асинхронная загрузка всех необходимых данных.
                        SwingUtilities.invokeLater { statusLabel.text = "Loading UI..." }
                        val settingsJob = async { settingsManager.loadSettings() }
                        val buildsJob = async { buildManager.loadBuilds() }
                        val accountsJob = async { accountManager.loadAccounts() }

                        val loadedState = AppState(
                            settings = settingsJob.await(),
                            builds = buildsJob.await(),
                            accounts = accountsJob.await()
                        )
                        withContext(Dispatchers.Main) {
                            appState = loadedState
                            currentScreen = Screen.MainApp(loadedState)
                        }
                    }
                }
            }
        }

        // Рендеринг текущего экрана в зависимости от состояния.
        when (currentScreen) {
            is Screen.Splash -> { /* Ничего не делаем, сплеш уже показан */ }
            is Screen.FirstRunWizard -> {
                var wizardTheme by remember { mutableStateOf(Theme.Dark) }
                Window(
                    onCloseRequest = {
                        scope.launch {
                            // Корректное завершение работы.
                            MLGDClient.shutdown()
                            modrinthApi.close()
                            exitApplication()
                        }
                    },
                    title = "Materia - Мастер настройки",
                    visible = isContentReady,
                    icon = icon,
                    state = rememberWindowState(width = 600.dp, height = 700.dp, position = WindowPosition(Alignment.Center))
                ) {
                    AnimatedAppTheme(wizardTheme) {
                        FirstRunWizard(
                            accountManager = accountManager,
                            initialTheme = wizardTheme,
                            onThemeChange = { wizardTheme = it },
                            onWizardComplete = { newSettings ->
                                scope.launch(Dispatchers.IO) {
                                    pathManager.createRequiredDirectories()
                                    settingsManager.saveSettings(newSettings)
                                    currentScreen = Screen.Splash
                                }
                            }
                        )
                        SideEffect { if (!isContentReady) isContentReady = true }
                    }
                }
            }
            is Screen.MainApp -> {
                appState?.let { state ->
                    Window(
                        onCloseRequest = {
                            // Корректное завершение работы.
                            scope.launch {
                                MLGDClient.shutdown()
                                modrinthApi.close()
                                exitApplication()
                            }
                        },
                        title = stringResource(Res.string.app_name),
                        visible = isContentReady,
                        icon = icon,
                        state = rememberWindowState(width = 1024.dp, height = 768.dp)
                    ) {
                        App(
                            appState = state,
                            onSettingsChange = { newSettings ->
                                appState = state.copy(settings = newSettings)
                                scope.launch { settingsManager.saveSettings(newSettings) }
                            },
                            buildManager = buildManager,
                            javaManager = javaManager,
                            accountManager = accountManager,
                            javaDownloader = javaDownloader,
                            pathManager = pathManager,
                            cacheManager = cacheManager
                        )
                        SideEffect { if (!isContentReady) isContentReady = true }
                    }
                }
            }
        }
    }
}
