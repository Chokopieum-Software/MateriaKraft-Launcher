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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.sun.management.OperatingSystemMXBean
import funlauncher.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ui.*
import java.awt.Desktop
import java.io.File
import java.lang.management.ManagementFactory
import java.net.URI

enum class AppTab { Home, Mods, Settings }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun App(appSettings: AppSettings, onSettingsChange: (AppSettings) -> Unit) {
    val scope = rememberCoroutineScope()
    val buildManager = remember { BuildManager() }
    val settingsManager = remember { SettingsManager() }
    val javaManager = remember { JavaManager() }
    val accountManager = remember { AccountManager() }
    val javaDownloader = remember { JavaDownloader() }

    var currentTab by remember { mutableStateOf(AppTab.Home) }
    var buildList by remember { mutableStateOf<List<MinecraftBuild>>(emptyList()) }
    var accounts by remember { mutableStateOf(accountManager.getAccounts()) }
    var currentAccount by remember { mutableStateOf(accounts.firstOrNull()) }

    var selectedBuild by remember { mutableStateOf<MinecraftBuild?>(null) }
    var gameProcess by remember { mutableStateOf<Process?>(null) }
    val isGameRunning = gameProcess?.isAlive == true

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

    fun refreshBuilds() {
        scope.launch {
            buildList = buildManager.loadBuilds()
        }
    }

    LaunchedEffect(Unit) {
        refreshBuilds()
    }

    LaunchedEffect(DownloadManager.tasks.size) {
        if (DownloadManager.tasks.isEmpty() && !showCheckmark) {
            showCheckmark = true
            delay(2000)
            showCheckmark = false
        }
    }

    LaunchedEffect(isGameRunning) {
        if (!isGameRunning) {
            showGameConsole = false
        }
    }

    LaunchedEffect(gameProcess) {
        if (gameProcess != null) {
            scope.launch(Dispatchers.IO) {
                gameProcess?.waitFor()
                gameProcess = null
                selectedBuild = null
            }
        }
    }

    suspend fun launchMinecraft(build: MinecraftBuild, javaPath: String) {
        try {
            if (appSettings.showConsoleOnLaunch) {
                showGameConsole = true
            }
            val installer = MinecraftInstaller(build)
            val finalMaxRam = build.maxRamMb ?: appSettings.maxRamMb
            val finalJavaArgs = build.javaArgs ?: appSettings.javaArgs
            val finalEnvVars = build.envVars ?: appSettings.envVars

            val process = installer.launchOffline(
                username = currentAccount!!.username,
                javaPath = javaPath,
                maxRamMb = finalMaxRam,
                javaArgs = finalJavaArgs,
                envVars = finalEnvVars,
                showConsole = appSettings.showConsoleOnLaunch
            )
            gameProcess = process
        } catch (e: Exception) {
            e.printStackTrace()
            errorDialogMessage = "Ошибка запуска: ${e.message}"
        } finally {
            isLaunchingBuildId = null
        }
    }

    val performLaunch: (MinecraftBuild) -> Unit = { build ->
        selectedBuild = build
        isLaunchingBuildId = build.name
        scope.launch {
            val useAutoJava = build.javaPath == "" || (build.javaPath == null && appSettings.javaPath.isBlank())

            if (useAutoJava) {
                val recommendedVersion = javaManager.getRecommendedJavaVersion(build.version)
                val installations = javaManager.findJavaInstallations()
                val allJavas = installations.launcher + installations.system
                val exactJava = allJavas.firstOrNull { it.version == recommendedVersion && it.is64Bit }

                if (exactJava != null) {
                    launchMinecraft(build, exactJava.path)
                } else {
                    javaDownloader.downloadAndUnpack(recommendedVersion) { result ->
                        scope.launch {
                            result.fold(
                                onSuccess = { downloadedJava ->
                                    launchMinecraft(build, downloadedJava.path)
                                },
                                onFailure = {
                                    errorDialogMessage = "Не удалось скачать Java: ${it.message}"
                                    isLaunchingBuildId = null
                                }
                            )
                        }
                    }
                }
            } else {
                val finalJavaPath = build.javaPath ?: appSettings.javaPath
                launchMinecraft(build, finalJavaPath)
            }
        }
    }

    val onLaunchClick: (MinecraftBuild) -> Unit = onLaunchClick@{ build ->
        if (currentAccount == null) {
            errorDialogMessage = "Сначала выберите аккаунт!"
            return@onLaunchClick
        }
        if (isGameRunning) {
            gameProcess?.destroyForcibly()
            return@onLaunchClick
        }

        val osBean = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean
        val freeMemory = osBean.freeMemorySize / (1024 * 1024)
        val allocatedRam = build.maxRamMb ?: appSettings.maxRamMb

        if (allocatedRam > freeMemory) {
            showRamWarningDialog = build
        } else {
            performLaunch(build)
        }
    }

    val onDeleteBuildClick: (MinecraftBuild) -> Unit = { build ->
        buildToDelete = build
    }

    val onSettingsBuildClick: (MinecraftBuild) -> Unit = { build ->
        showBuildSettingsScreen = build
    }

    val contentPaddingStart by animateDpAsState(if (appSettings.navPanelPosition == NavPanelPosition.Left) 96.dp else 0.dp)
    val contentPaddingBottom by animateDpAsState(if (appSettings.navPanelPosition == NavPanelPosition.Bottom) 80.dp else 0.dp)

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(modifier = Modifier.fillMaxSize().padding(start = contentPaddingStart, bottom = contentPaddingBottom)) {
            when (currentTab) {
                AppTab.Home -> HomeScreen(
                    builds = buildList,
                    runningBuild = if (isGameRunning) selectedBuild else null,
                    onLaunchClick = onLaunchClick,
                    onOpenFolderClick = { openFolder(it.installPath) },
                    onAddBuildClick = { showAddBuildDialog = true },
                    isLaunchingBuildId = isLaunchingBuildId,
                    onDeleteBuildClick = onDeleteBuildClick,
                    onSettingsBuildClick = onSettingsBuildClick,
                    currentAccount = currentAccount,
                    onOpenAccountManager = { showAccountScreen = true }
                )
                AppTab.Mods -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Раздел 'Моды' в разработке",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                AppTab.Settings -> SettingsTab(
                    currentSettings = appSettings,
                    onSave = { newSettings ->
                        onSettingsChange(newSettings)
                        scope.launch { settingsManager.saveSettings(newSettings) }
                    },
                    onOpenJavaManager = { showJavaManagerWindow = true }
                )
            }
        }

        AnimatedVisibility(
            visible = appSettings.navPanelPosition == NavPanelPosition.Left,
            enter = slideInHorizontally(initialOffsetX = { -it }),
            exit = slideOutHorizontally(targetOffsetX = { -it }),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            NavigationRail(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .height(380.dp)
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp)),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.fillMaxHeight().padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        NavigationRailItem(
                            selected = currentTab == AppTab.Home,
                            onClick = { currentTab = AppTab.Home },
                            icon = { Icon(Icons.Default.Home, contentDescription = "Главная") },
                            label = { Text("Главная") }
                        )
                        NavigationRailItem(
                            selected = currentTab == AppTab.Mods,
                            onClick = { currentTab = AppTab.Mods },
                            icon = { Icon(Icons.Default.Star, contentDescription = "Моды") },
                            label = { Text("Моды") }
                        )
                        NavigationRailItem(
                            selected = currentTab == AppTab.Settings,
                            onClick = { currentTab = AppTab.Settings },
                            icon = { Icon(Icons.Default.Settings, contentDescription = "Настройки") },
                            label = { Text("Настройки") }
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box {
                            val hasActiveDownloads = DownloadManager.tasks.isNotEmpty()
                            NavigationRailItem(
                                selected = false,
                                onClick = { showDownloadsPopup = !showDownloadsPopup },
                                icon = {
                                    when {
                                        showCheckmark -> Icon(Icons.Default.Check, contentDescription = "Загрузка завершена")
                                        hasActiveDownloads -> BeautifulCircularProgressIndicator(
                                            size = 24.dp,
                                            strokeWidth = 3.dp,
                                            primaryColor = Color(0xFF2196F3),
                                            secondaryColor = Color(0xFF00E5FF)
                                        )
                                        else -> Icon(Icons.Default.Download, contentDescription = "Загрузки")
                                    }
                                },
                                label = { Text("Загрузки") }
                            )
                            if (showDownloadsPopup) {
                                DownloadsPopup(onDismissRequest = { showDownloadsPopup = false })
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = appSettings.navPanelPosition == NavPanelPosition.Bottom,
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
                    selected = currentTab == AppTab.Mods,
                    onClick = { currentTab = AppTab.Mods },
                    icon = { Icon(Icons.Default.Star, contentDescription = "Моды") },
                    label = { Text("Моды") }
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.Settings,
                    onClick = { currentTab = AppTab.Settings },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Настройки") },
                    label = { Text("Настройки") }
                )
            }
        }
    }

    if (showJavaManagerWindow) {
        JavaManagerWindow(
            onCloseRequest = { showJavaManagerWindow = false },
            javaManager = javaManager,
            javaDownloader = javaDownloader,
            appSettings = appSettings
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
            }
        )
    }
    showBuildSettingsScreen?.let { build ->
        BuildSettingsScreen(
            build = build,
            globalSettings = appSettings,
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
            }
        )
    }
    if (showAccountScreen) {
        AccountScreen(
            accountManager = accountManager,
            onDismiss = { showAccountScreen = false },
            onAccountSelected = {
                currentAccount = it
                showAccountScreen = false
            },
            onAccountsUpdated = {
                accounts = accountManager.getAccounts()
                currentAccount = accounts.firstOrNull()
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
    errorDialogMessage?.let {
        AlertDialog(
            onDismissRequest = { errorDialogMessage = null },
            title = { Text("Ошибка") },
            text = { Text(it) },
            confirmButton = { Button(onClick = { errorDialogMessage = null }) { Text("OK") } }
        )
    }
    buildToDelete?.let {
        AlertDialog(
            onDismissRequest = { buildToDelete = null },
            title = { Text("Подтверждение") },
            text = { Text("Вы уверены, что хотите удалить сборку '${it.name}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            buildManager.deleteBuild(it.name)
                            refreshBuilds()
                            buildToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("Удалить") }
            },
            dismissButton = { Button(onClick = { buildToDelete = null }) { Text("Отмена") } }
        )
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

fun main() = application {
    Window(onCloseRequest = {
        ImageLoader.close()
        exitApplication()
    }, title = "Materia") {
        val settingsManager = remember { SettingsManager() }
        var appSettings by remember { mutableStateOf<AppSettings?>(null) }

        LaunchedEffect(Unit) {
            appSettings = settingsManager.loadSettings()
        }

        if (appSettings != null) {
            AppTheme(appSettings!!.theme) {
                App(appSettings!!, onSettingsChange = { appSettings = it })
            }
        } else {
            // Обновленный экран загрузки
            AppTheme(Theme.Dark) { // Можно использовать любую тему по умолчанию для экрана загрузки
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator()
                            Text("Загрузка...", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}
