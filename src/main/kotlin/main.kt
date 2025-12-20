import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

val BackgroundColor = Color(0xFFF0F0F0)

enum class AppTab { Home, Mods, Settings }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val scope = rememberCoroutineScope()
    val buildManager = remember { BuildManager() }
    val settingsManager = remember { SettingsManager() }
    val javaManager = remember { JavaManager() }
    val accountManager = remember { AccountManager() }
    val javaDownloader = remember { JavaDownloader() }

    var currentTab by remember { mutableStateOf(AppTab.Home) }
    var buildList by remember { mutableStateOf(buildManager.loadBuilds()) }
    var appSettings by remember { mutableStateOf(settingsManager.loadSettings()) }
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
        buildList = buildManager.loadBuilds()
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

    suspend fun launchMinecraft(build: MinecraftBuild, settings: AppSettings) {
        try {
            if (settings.showConsoleOnLaunch) {
                showGameConsole = true
            }
            val installer = MinecraftInstaller(build)
            val finalJavaPath = build.javaPath ?: settings.javaPath
            val finalMaxRam = build.maxRamMb ?: settings.maxRamMb
            val finalJavaArgs = build.javaArgs ?: settings.javaArgs
            val finalEnvVars = build.envVars ?: settings.envVars

            val process = installer.launchOffline(
                username = currentAccount!!.username,
                javaPath = finalJavaPath,
                maxRamMb = finalMaxRam,
                javaArgs = finalJavaArgs,
                envVars = finalEnvVars,
                showConsole = settings.showConsoleOnLaunch
            )
            gameProcess = process
            isLaunchingBuildId = null
        } catch (e: Exception) {
            e.printStackTrace()
            errorDialogMessage = "Ошибка запуска: ${e.message}"
            isLaunchingBuildId = null
        }
    }

    val performLaunch: (MinecraftBuild) -> Unit = { build ->
        selectedBuild = build
        isLaunchingBuildId = build.name
        scope.launch {
            val javaPath = build.javaPath ?: appSettings.javaPath
            if (javaPath.isBlank()) {
                val recommendedVersion = javaManager.getRecommendedJavaVersion(build.version)
                val installations = javaManager.findJavaInstallations()
                val allJavas = installations.launcher + installations.system
                val exactJava = allJavas.firstOrNull { it.version == recommendedVersion && it.is64Bit }

                if (exactJava != null) {
                    launchMinecraft(build, appSettings.copy(javaPath = exactJava.path))
                } else {
                    javaDownloader.downloadAndUnpack(recommendedVersion) { result ->
                        scope.launch {
                            result.fold(
                                onSuccess = { downloadedJava ->
                                    launchMinecraft(build, appSettings.copy(javaPath = downloadedJava.path))
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
                launchMinecraft(build, appSettings)
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
                        ExtendedFloatingActionButton(
                            onClick = { showAccountScreen = true },
                            modifier = Modifier.padding(vertical = 8.dp),
                            text = { Text(currentAccount?.username ?: "Аккаунт") },
                            icon = { Icon(Icons.Default.Person, contentDescription = "Аккаунты") }
                        )
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
                                        hasActiveDownloads -> CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp
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

            Box(modifier = Modifier.weight(1f).padding(16.dp)) {
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
                    AppTab.Mods -> Text("Раздел 'Моды' в разработке", style = MaterialTheme.typography.headlineMedium)
                    AppTab.Settings -> SettingsTab(
                        currentSettings = appSettings,
                        onSave = { newSettings ->
                            appSettings = newSettings
                            settingsManager.saveSettings(newSettings)
                        },
                        onOpenJavaManager = { showJavaManagerWindow = true }
                    )
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
                onAdd = { n, v, t ->
                    try {
                        buildManager.addBuild(n, v, t)
                        refreshBuilds()
                        showAddBuildDialog = false
                    } catch (e: Exception) {
                        errorDialogMessage = e.message
                    }
                }
            )
        }
        showBuildSettingsScreen?.let { build ->
            BuildSettingsScreen(
                build = build,
                globalSettings = appSettings,
                onDismiss = { showBuildSettingsScreen = null },
                onSave = { javaPath, maxRam, javaArgs, envVars ->
                    buildManager.updateBuildSettings(build.name, javaPath, maxRam, javaArgs, envVars)
                    refreshBuilds()
                    showBuildSettingsScreen = null
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
                            buildManager.deleteBuild(it.name)
                            refreshBuilds()
                            buildToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) { Text("Удалить") }
                },
                dismissButton = { Button(onClick = { buildToDelete = null }) { Text("Отмена") } }
            )
        }
    }
}

fun openFolder(path: String) = runCatching { Desktop.getDesktop().open(File(path)) }

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "MateriaKraft") {
        App()
    }
}
