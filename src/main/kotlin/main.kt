/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
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

val BackgroundColor = Color(0xffaedddd) // Пастельно-мятный

enum class AppTab { Home, Mods, Settings }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun App() {
    val scope = rememberCoroutineScope()
    val buildManager = remember { BuildManager() }
    val settingsManager = remember { SettingsManager() }
    val javaManager = remember { JavaManager() }
    val accountManager = remember { AccountManager() }
    val javaDownloader = remember { JavaDownloader() }

    var currentTab by remember { mutableStateOf(AppTab.Home) }
    var buildList by remember { mutableStateOf<List<MinecraftBuild>>(emptyList()) }
    var appSettings by remember { mutableStateOf<AppSettings?>(null) }
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

    // Асинхронная загрузка настроек и сборок при старте
    LaunchedEffect(Unit) {
        appSettings = settingsManager.loadSettings()
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
        if (appSettings == null) return // Не запускаем, если настройки еще не загружены
        try {
            if (appSettings!!.showConsoleOnLaunch) {
                showGameConsole = true
            }
            val installer = MinecraftInstaller(build)
            val finalMaxRam = build.maxRamMb ?: appSettings!!.maxRamMb
            val finalJavaArgs = build.javaArgs ?: appSettings!!.javaArgs
            val finalEnvVars = build.envVars ?: appSettings!!.envVars

            val process = installer.launchOffline(
                username = currentAccount!!.username,
                javaPath = javaPath,
                maxRamMb = finalMaxRam,
                javaArgs = finalJavaArgs,
                envVars = finalEnvVars,
                showConsole = appSettings!!.showConsoleOnLaunch
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
            if (appSettings == null) return@launch
            // Определяем, нужно ли авто-определение Java
            val useAutoJava = build.javaPath == "" || (build.javaPath == null && appSettings!!.javaPath.isBlank())

            if (useAutoJava) {
                // Логика автоматического поиска и скачивания Java
                val recommendedVersion = javaManager.getRecommendedJavaVersion(build.version)
                val installations = javaManager.findJavaInstallations() // Теперь это suspend-функция
                val allJavas = installations.launcher + installations.system
                val exactJava = allJavas.firstOrNull { it.version == recommendedVersion && it.is64Bit }

                if (exactJava != null) {
                    // Нашли подходящую версию, запускаем
                    launchMinecraft(build, exactJava.path)
                } else {
                    // Не нашли, скачиваем
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
                // Используем указанный путь к Java
                val finalJavaPath = build.javaPath ?: appSettings!!.javaPath
                launchMinecraft(build, finalJavaPath)
            }
        }
    }

    val onLaunchClick: (MinecraftBuild) -> Unit = onLaunchClick@{ build ->
        if (appSettings == null) return@onLaunchClick
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
        val allocatedRam = build.maxRamMb ?: appSettings!!.maxRamMb

        if (allocatedRam > freeMemory) {
            showRamWarningDialog = build
        } else {
            println("RAM Check: Allocated RAM is OK. Launching directly.")
            performLaunch(build)
        }
    }

    val onDeleteBuildClick: (MinecraftBuild) -> Unit = { build ->
        buildToDelete = build
    }

    val onSettingsBuildClick: (MinecraftBuild) -> Unit = { build ->
        showBuildSettingsScreen = build
    }

    MaterialTheme {
        Row(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
            NavigationRail(
                modifier = Modifier.background(Color.White).fillMaxHeight(),
                containerColor = Color.White
            ) {
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(8.dp))
                        TooltipArea(
                            tooltip = {
                                Surface(
                                    modifier = Modifier.padding(4.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    shadowElevation = 4.dp
                                ) {
                                    Text(
                                        text = currentAccount?.username ?: "Выбрать аккаунт",
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                        ) {
                            IconButton(
                                onClick = { showAccountScreen = true },
                                modifier = Modifier.padding(vertical = 8.dp).size(48.dp)
                            ) {
                                Image(
                                    painter = painterResource("steve_head.png"),
                                    contentDescription = "Аккаунты",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            }
                        }
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
                                            // Можно настроить цвета под твой "пастельно-мятный" стиль
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
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                if (appSettings == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    when (currentTab) {
                        AppTab.Home -> HomeScreen(
                            builds = buildList,
                            runningBuild = if (isGameRunning) selectedBuild else null,
                            onLaunchClick = onLaunchClick,
                            onOpenFolderClick = { openFolder(it.installPath) },
                            onAddBuildClick = { showAddBuildDialog = true },
                            isLaunchingBuildId = isLaunchingBuildId,
                            onDeleteBuildClick = onDeleteBuildClick,
                            onSettingsBuildClick = onSettingsBuildClick
                        )
                        AppTab.Mods -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Раздел 'Моды' в разработке", style = MaterialTheme.typography.headlineMedium)
                        }
                        AppTab.Settings -> SettingsTab(
                            currentSettings = appSettings!!,
                            onSave = { newSettings ->
                                appSettings = newSettings
                                scope.launch { settingsManager.saveSettings(newSettings) }
                            },
                            onOpenJavaManager = { showJavaManagerWindow = true }
                        )
                    }
                }
            }
        }

        if (showJavaManagerWindow) {
            JavaManagerWindow(
                onCloseRequest = { showJavaManagerWindow = false },
                javaManager = javaManager,
                javaDownloader = javaDownloader
            )
        }
        if (showAddBuildDialog) {
            AddBuildDialog(
                onDismiss = { showAddBuildDialog = false },
                onAdd = { name, version, type ->
                    scope.launch {
                        try {
                            buildManager.addBuild(name, version, type)
                            refreshBuilds()
                            showAddBuildDialog = false
                        } catch (e: Exception) {
                            errorDialogMessage = e.message
                        }
                    }
                }
            )
        }
        if (appSettings != null) {
            showBuildSettingsScreen?.let { build ->
                BuildSettingsScreen(
                    build = build,
                    globalSettings = appSettings!!,
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
        // Теперь rotate вызывается правильно, без длинного пути
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

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Materia") {
        App()
    }
}
