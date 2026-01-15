/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.sun.management.OperatingSystemMXBean
import funlauncher.AppSettings
import funlauncher.MinecraftBuild
import funlauncher.NavPanelPosition
import funlauncher.SettingsManager
import funlauncher.Theme
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
import ui.*
import java.awt.Desktop
import java.awt.Dimension
import java.awt.Image
import java.awt.Toolkit
import java.io.File
import java.lang.management.ManagementFactory
import java.net.URI
import java.util.Properties
import javax.imageio.ImageIO
import javax.swing.*
import kotlin.math.min

enum class AppTab { Home, Modifications, Settings }

@Composable
fun AnimatedAppTheme(
    theme: Theme,
    content: @Composable () -> Unit
) {
    val isDark = when (theme) {
        Theme.System -> isSystemInDarkTheme()
        Theme.Light -> false
        Theme.Dark -> true
    }
    val colors = if (isDark) darkColorScheme() else lightColorScheme()

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}

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

    var currentTab by remember { mutableStateOf(AppTab.Home) }
    val buildList = remember { mutableStateListOf(*appState.builds.toTypedArray()) }
    val buildsPendingDeletion = remember { mutableStateSetOf<String>() }
    var accounts by remember { mutableStateOf(appState.accounts) }
    var currentAccount by remember { mutableStateOf(accounts.firstOrNull()) }

    // --- MLGD Integration ---
    var daemonStatus by remember { mutableStateOf(StatusResponse(running = false)) }
    val runningBuild = remember(daemonStatus, buildList.size) {
        if (daemonStatus.running) buildList.find { it.name == daemonStatus.buildName } else null
    }
    // --- End MLGD Integration ---

    var showAddBuildDialog by remember { mutableStateOf(false) }
    var showJavaManagerWindow by remember { mutableStateOf(false) }
    var showAccountScreen by remember { mutableStateOf(false) }
    var showDownloadsPopup by remember { mutableStateOf(false) }
    var showBuildSettingsScreen by remember { mutableStateOf<MinecraftBuild?>(null) }
    var errorDialogMessage by remember { mutableStateOf<String?>(null) }
    var buildToDelete by remember { mutableStateOf<MinecraftBuild?>(null) }
    var showGameConsole by remember { mutableStateOf(false) }
    var showRamWarningDialog by remember { mutableStateOf<MinecraftBuild?>(null) }

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
    // --- End MLGD Integration ---

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

    fun refreshBuilds() {
        scope.launch {
            val freshBuilds = withContext(Dispatchers.IO) {
                buildManager.loadBuilds()
            }
            buildList.clear()
            buildList.addAll(freshBuilds)
        }
    }

    LaunchedEffect(DownloadManager.tasks.size) {
        if (DownloadManager.tasks.isEmpty() && !showCheckmark) {
            showCheckmark = true
            delay(2000)
            showCheckmark = false
        }
    }

    // --- MLGD Integration: launchMinecraft function ---
    suspend fun launchMinecraft(build: MinecraftBuild, javaPath: String, account: Account) {
        try {
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
                    envVars = finalEnvVars
                )
            }
            val newStatus = MLGDClient.launch(launchConfig)
            daemonStatus = newStatus
        } catch (e: Exception) {
            e.printStackTrace()
            errorDialogMessage = "Ошибка запуска: ${e.message}"
            isLaunchingBuildId = null
        }
    }
    // --- End MLGD Integration ---

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

    // --- MLGD Integration: onLaunchClick ---
    val onLaunchClick: (MinecraftBuild) -> Unit = onLaunchClick@{ build ->
        if (currentAccount == null) {
            errorDialogMessage = "Сначала выберите аккаунт!"
            return@onLaunchClick
        }
        if (daemonStatus.running) {
            scope.launch { snackbarHostState.showSnackbar("Игра '${daemonStatus.buildName}' уже запущена.") }
            return@onLaunchClick
        }

        val osBean = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean
        val freeMemory = osBean.freeMemorySize / (1024 * 1024)
        val allocatedRam = build.maxRamMb ?: appState.settings.maxRamMb

        if (allocatedRam > freeMemory) {
            showRamWarningDialog = build
        } else {
            performLaunch(build)
        }
    }
    // --- End MLGD Integration ---

    val onDeleteBuildClick: (MinecraftBuild) -> Unit = { build ->
        buildToDelete = build
    }

    val onSettingsBuildClick: (MinecraftBuild) -> Unit = { build ->
        showBuildSettingsScreen = build
    }

    AnimatedAppTheme(appState.settings.theme) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.background
        ) {
            val contentPaddingStart by animateDpAsState(if (appState.settings.navPanelPosition == NavPanelPosition.Left && currentTab != AppTab.Modifications) 96.dp else 0.dp)
            val contentPaddingBottom by animateDpAsState(if (appState.settings.navPanelPosition == NavPanelPosition.Bottom && currentTab != AppTab.Modifications) 80.dp else 0.dp)

            Box(modifier = Modifier.fillMaxSize()) {
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
                                icon = { Icon(Icons.Default.Home, contentDescription = "Главная") },
                                label = { Text("Главная") }
                            )
                            Spacer(Modifier.height(16.dp))
                            NavigationRailItem(
                                selected = currentTab == AppTab.Modifications,
                                onClick = { currentTab = AppTab.Modifications },
                                icon = { Icon(Icons.Default.Build, contentDescription = "Модификации") },
                                label = { Text("Модификации") }
                            )
                            Spacer(Modifier.height(16.dp))
                            NavigationRailItem(
                                selected = currentTab == AppTab.Settings,
                                onClick = { currentTab = AppTab.Settings },
                                icon = { Icon(Icons.Default.Settings, contentDescription = "Настройки") },
                                label = { Text("Настройки") }
                            )
                        }
                    }
                }

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
                            icon = { Icon(Icons.Default.Home, contentDescription = "Главная") },
                            label = { Text("Главная") }
                        )
                        NavigationBarItem(
                            selected = currentTab == AppTab.Modifications,
                            onClick = { currentTab = AppTab.Modifications },
                            icon = { Icon(Icons.Default.Build, contentDescription = "Модификации") },
                            label = { Text("Модификации") }
                        )
                        NavigationBarItem(
                            selected = currentTab == AppTab.Settings,
                            onClick = { currentTab = AppTab.Settings },
                            icon = { Icon(Icons.Default.Settings, contentDescription = "Настройки") },
                            label = { Text("Настройки") }
                        )
                    }
                }

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

        if (showJavaManagerWindow) {
            JavaManagerWindow(
                onCloseRequest = { showJavaManagerWindow = false },
                javaManager = javaManager,
                javaDownloader = javaDownloader,
                appSettings = appState.settings
            )
        }
        if (showAddBuildDialog) {
            AddBuildDialog(
                onDismiss = { showAddBuildDialog = false },
                onAdd = { name, version, type, imagePath ->
                    scope.launch {
                        try {
                            buildManager.addBuild(name, version, type, imagePath)
                            refreshBuilds()
                            showAddBuildDialog = false
                        } catch (e: Exception) {
                            errorDialogMessage = e.message
                        }
                    }
                },
                pathManager = pathManager
            )
        }
        showBuildSettingsScreen?.let { build ->
            BuildSettingsScreen(
                build = build,
                globalSettings = appState.settings,
                onDismiss = { showBuildSettingsScreen = null },
                onSave = { newName, newVersion, newType, newImagePath, javaPath, maxRam, javaArgs, envVars ->
                    scope.launch {
                        try {
                            buildManager.updateBuildSettings(
                                oldName = build.name,
                                newName = newName,
                                newVersion = newVersion,
                                newType = newType,
                                newImagePath = newImagePath,
                                newJavaPath = javaPath,
                                newMaxRam = maxRam,
                                newJavaArgs = javaArgs,
                                newEnvVars = envVars
                            )
                            refreshBuilds()
                            showBuildSettingsScreen = null
                        } catch (e: Exception) {
                            errorDialogMessage = e.message
                        }
                    }
                },
                pathManager = pathManager
            )
        }
        if (showAccountScreen) {
            AccountScreen(
                accountManager = accountManager,
                onDismiss = { showAccountScreen = false },
                onAccountSelected = {
                    currentAccount = it
                    showAccountScreen = false
                }
            )
        }
        if (showGameConsole) {
            GameConsoleWindow(onCloseRequest = { showGameConsole = false })
        }
        showRamWarningDialog?.let { build ->
            AlertDialog(
                onDismissRequest = { showRamWarningDialog = null; isLaunchingBuildId = null },
                title = { Text("Предупреждение") },
                text = { Text("Вы выделили больше ОЗУ, чем доступно в системе. Это может привести к проблемам с производительностью или запуском. Продолжить?") },
                confirmButton = {
                    Button(onClick = {
                        showRamWarningDialog = null
                        performLaunch(build)
                    }) { Text("Все равно запустить") }
                },
                dismissButton = {
                    Button(onClick = { showRamWarningDialog = null; isLaunchingBuildId = null }) { Text("Отмена") }
                }
            )
        }
        errorDialogMessage?.let { message ->
            AlertDialog(
                onDismissRequest = { errorDialogMessage = null },
                title = { Text("Ошибка") },
                text = { Text(message) },
                confirmButton = { Button(onClick = { errorDialogMessage = null }) { Text("OK") } }
            )
        }
        buildToDelete?.let { build ->
            AlertDialog(
                onDismissRequest = { buildToDelete = null },
                title = { Text("Подтверждение") },
                text = { Text("Вы уверены, что хотите удалить сборку '${build.name}'?") },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                buildsPendingDeletion.add(build.name)
                                buildToDelete = null
                                delay(400)
                                buildManager.deleteBuild(build.name)
                                buildList.removeIf { it.name == build.name }
                                buildsPendingDeletion.remove(build.name)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) { Text("Удалить") }
                },
                dismissButton = { Button(onClick = { buildToDelete = null }) { Text("Отмена") } }
            )
        }
    }
}


@Composable
fun BeautifulCircularProgressIndicator(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 24.dp,
    strokeWidth: androidx.compose.ui.unit.Dp = 3.dp,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    secondaryColor: Color = MaterialTheme.colorScheme.tertiary
) {
    val transition = rememberInfiniteTransition()

    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing)
        )
    )

    Canvas(modifier = modifier.size(size)) {
        rotate(rotation) {
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(
                        Color.Transparent,
                        primaryColor.copy(alpha = 0.5f),
                        primaryColor,
                        secondaryColor
                    )
                ),
                radius = size.toPx() / 2 - strokeWidth.toPx() / 2,
                style = Stroke(
                    width = strokeWidth.toPx(),
                    cap = StrokeCap.Round
                )
            )
        }
    }
}
fun openFolder(path: String) = runCatching { Desktop.getDesktop().open(File(path)) }

fun openUri(uri: URI) {
    val os = System.getProperty("os.name").lowercase()
    runCatching {
        if (os.contains("win")) {
            Desktop.getDesktop().browse(uri)
        } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            Runtime.getRuntime().exec(arrayOf("xdg-open", uri.toString()))
        } else if (os.contains("mac")) {
            Runtime.getRuntime().exec(arrayOf("open", uri.toString()))
        } else {
            Desktop.getDesktop().browse(uri)
        }
    }
}

private sealed class Screen {
    object Splash : Screen()
    object FirstRunWizard : Screen()
    data class MainApp(val state: AppState) : Screen()
}

data class AppState(
    val settings: AppSettings,
    val builds: List<MinecraftBuild>,
    val accounts: List<Account>
)

var isContentReady by mutableStateOf(false)

fun main() {
    val os = System.getProperty("os.name").lowercase()
    if (!os.contains("mac")) {
        val renderApi = try {
            Class.forName("org.jetbrains.skiko.vulkan.VulkanWindow")
            "VULKAN"
        } catch (e: Throwable) {
            "OPENGL"
        }
        System.setProperty("skiko.renderApi", renderApi)
        println("Using render API: $renderApi")
    }

    val splash = JWindow()
    val statusLabel = JLabel("Initializing...", SwingConstants.RIGHT)

    try {
        val props = Properties()
        Thread.currentThread().contextClassLoader.getResourceAsStream("app.properties")?.use { props.load(it) }
        val version = props.getProperty("version", "Unknown")
        val buildNumber = props.getProperty("buildNumber", "N/A")
        val versionText = "$version ($buildNumber)"

        val versionLabel = JLabel(versionText, SwingConstants.RIGHT)
        versionLabel.foreground = java.awt.Color.WHITE
        statusLabel.foreground = java.awt.Color.WHITE

        val originalImage = ImageIO.read(Thread.currentThread().contextClassLoader.getResource("banner.png"))
        val screenSize = Toolkit.getDefaultToolkit().screenSize
        val targetWidth = screenSize.width / 2.5
        val targetHeight = screenSize.height / 2.5
        val imageWidth = originalImage.width
        val imageHeight = originalImage.height
        val ratio = min(targetWidth / imageWidth, targetHeight / imageHeight)
        val newWidth = (imageWidth * ratio).toInt()
        val newHeight = (imageHeight * ratio).toInt()
        val finalImage = originalImage.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH)
        
        val layeredPane = JLayeredPane()
        layeredPane.preferredSize = Dimension(newWidth, newHeight)

        val imageLabel = JLabel(ImageIcon(finalImage))
        imageLabel.setBounds(0, 0, newWidth, newHeight)
        
        val margin = 10
        statusLabel.setBounds(0, newHeight - 30 - margin, newWidth - margin, 20)
        versionLabel.setBounds(0, newHeight - 15 - margin, newWidth - margin, 20)

        layeredPane.add(imageLabel, JLayeredPane.DEFAULT_LAYER)
        layeredPane.add(statusLabel, JLayeredPane.PALETTE_LAYER)
        layeredPane.add(versionLabel, JLayeredPane.PALETTE_LAYER)

        splash.contentPane.add(layeredPane)
        splash.pack()
        splash.setLocationRelativeTo(null)
        splash.isVisible = true
    } catch (e: Exception) {
        e.printStackTrace()
    }

    runBlocking {
        val pathManager by lazy { PathManager(PathManager.getDefaultAppDataDirectory()) }
        SwingUtilities.invokeLater { statusLabel.text = "Starting daemon..." }
        try {
            MLGDClient.ensureDaemonRunning(pathManager.getLauncherDir())
        } catch (e: Exception) {
            e.printStackTrace()
            splash.isVisible = false
            JOptionPane.showMessageDialog(null, "Could not start or connect to MLGD daemon.\n${e.message}", "Fatal Error", JOptionPane.ERROR_MESSAGE)
            return@runBlocking
        }
    }

    application {
        var currentScreen by remember { mutableStateOf<Screen>(Screen.Splash) }
        var appState by remember { mutableStateOf<AppState?>(null) }
        val scope = rememberCoroutineScope()
        val icon = painterResource("logo.ico")

        val pathManager by lazy { PathManager(PathManager.getDefaultAppDataDirectory()) }
        val settingsManager by lazy { SettingsManager(pathManager) }
        val buildManager by lazy { BuildManager(pathManager) }
        val accountManager by lazy { AccountManager(pathManager) }
        val javaManager by lazy { JavaManager(pathManager) }
        val javaDownloader by lazy { JavaDownloader(pathManager, javaManager) }
        val cacheManager by lazy { CacheManager(pathManager) }
        val modrinthApi by lazy { ModrinthApi(cacheManager) }

        LaunchedEffect(isContentReady) {
            if (isContentReady) {
                splash.isVisible = false
                splash.dispose()
            }
        }

        LaunchedEffect(currentScreen) {
            if (currentScreen is Screen.Splash) {
                isContentReady = false
                scope.launch(Dispatchers.IO) {
                    if (pathManager.isFirstRunRequired()) {
                        withContext(Dispatchers.Main) {
                            currentScreen = Screen.FirstRunWizard
                        }
                    } else {
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

        when (currentScreen) {
            is Screen.Splash -> { /* Ничего не делаем, сплеш уже показан */ }
            is Screen.FirstRunWizard -> {
                var wizardTheme by remember { mutableStateOf(Theme.Dark) }
                Window(
                    onCloseRequest = {
                        scope.launch {
                            MLGDClient.shutdown()
                            ImageLoader.close()
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
                            scope.launch {
                                MLGDClient.shutdown()
                                ImageLoader.close()
                                modrinthApi.close()
                                exitApplication()
                            }
                        },
                        title = "Materia",
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
