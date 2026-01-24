/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import funlauncher.*
import funlauncher.auth.AccountManager
import funlauncher.database.DatabaseManager
import funlauncher.game.MLGDClient
import funlauncher.game.VersionMetadataFetcher
import funlauncher.managers.BuildManager
import funlauncher.managers.CacheManager
import funlauncher.managers.JavaManager
import funlauncher.managers.PathManager
import funlauncher.net.JavaDownloader
import funlauncher.net.ModrinthApi
import kotlinx.coroutines.*
import org.chokopieum.software.materia_launcher.generated.resources.*
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import splash.createAndShowSplashScreen
import state.AppState
import state.Screen
import ui.App
import ui.screens.FirstRunWizard
import ui.theme.AnimatedAppTheme
import ui.viewmodel.AppViewModel
import ui.widgets.ImageLoader
import java.time.Month
import java.time.OffsetDateTime
import java.util.*
import javax.swing.JLabel
import javax.swing.SwingUtilities

// Флаг, указывающий, что основной контент готов к отображению (используется для скрытия сплеш-скрина).
var isContentReady by mutableStateOf(false)

// Глобальные переменные для менеджеров, которые должны быть инициализированы до запуска Compose UI
private lateinit var globalPathManager: PathManager
private lateinit var globalSettingsManager: SettingsManager
private lateinit var globalAccountManager: AccountManager
private lateinit var globalBuildManager: BuildManager
private lateinit var globalJavaManager: JavaManager
private lateinit var globalJavaDownloader: JavaDownloader
private lateinit var globalCacheManager: CacheManager
private lateinit var globalModrinthApi: ModrinthApi
private lateinit var globalVersionMetadataFetcher: VersionMetadataFetcher

@OptIn(ExperimentalResourceApi::class)
fun main(args: Array<String>) {
    val isUiTest = args.contains("--uitest")

    if (isUiTest) {
        System.setProperty("skiko.renderApi", "SOFTWARE")
        println("Using render API: SOFTWARE (UI Test)")
    } else {
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
    }

    globalPathManager = PathManager(PathManager.getDefaultAppDataDirectory())
    val isFirstRun = globalPathManager.isFirstRunRequired()

    runBlocking(Dispatchers.IO) {
        if (isFirstRun) {
            globalPathManager.createRequiredDirectories()
            DatabaseManager.init(globalPathManager)
        } else {
            DatabaseManager.init(globalPathManager)
        }

        globalSettingsManager = SettingsManager(globalPathManager)
        val settings = globalSettingsManager.loadSettings()
        Locale.setDefault(Locale(settings.language))

        globalBuildManager = BuildManager(globalPathManager)
        globalAccountManager = AccountManager(globalPathManager)
        globalJavaManager = JavaManager(globalPathManager)
        globalJavaDownloader = JavaDownloader(globalPathManager, globalJavaManager)
        globalCacheManager = CacheManager(globalPathManager)
        globalModrinthApi = ModrinthApi(globalCacheManager)
        globalVersionMetadataFetcher = VersionMetadataFetcher(globalBuildManager, globalPathManager)

        ImageLoader.init(globalCacheManager)
    }

    val statusLabel = JLabel("Initializing...")
    val splash = createAndShowSplashScreen(statusLabel)

    CoroutineScope(Dispatchers.IO).launch {
        runCatching {
            globalVersionMetadataFetcher.prefetchVersionMetadata { status ->
                SwingUtilities.invokeLater { statusLabel.text = status }
            }
        }.onFailure {
            println("Warning: Failed to prefetch version metadata in background: ${it.stackTraceToString()}")
        }
    }

    runBlocking {
        SwingUtilities.invokeLater { statusLabel.text = "Starting daemon..." }
        runCatching {
            MLGDClient.ensureDaemonRunning(globalPathManager)
        }.onFailure { e ->
            println("Could not start or connect to MLGD daemon. The application will continue without it.")
            println(e.stackTraceToString())
        }
    }

    application {
        var currentScreen by remember { mutableStateOf<Screen>(Screen.Splash) }
        var appState by remember { mutableStateOf<AppState?>(null) }
        val scope = rememberCoroutineScope()

        val month = OffsetDateTime.now().month
        val isWinter = month == Month.DECEMBER || month == Month.JANUARY || month == Month.FEBRUARY
        val icon = if (isWinter) {
            painterResource(Res.drawable.MLicon_snow)
        } else {
            painterResource(Res.drawable.MLicon)
        }

        LaunchedEffect(isContentReady) {
            if (isContentReady) {
                splash?.isVisible = false
                splash?.dispose()
            }
        }

        LaunchedEffect(currentScreen) {
            if (currentScreen is Screen.Splash) {
                isContentReady = false
                scope.launch(Dispatchers.IO) {
                    if (isFirstRun) {
                        withContext(Dispatchers.Main) {
                            currentScreen = Screen.FirstRunWizard
                        }
                    } else {
                        SwingUtilities.invokeLater { statusLabel.text = "Loading UI..." }
                        val settingsJob = async { globalSettingsManager.loadSettings() }
                        val buildsJob = async { globalBuildManager.loadBuilds() }
                        val accountsJob = async { globalAccountManager.loadAccounts() }

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
            is Screen.Splash -> { /* Do nothing, splash is already shown */ }
            is Screen.FirstRunWizard -> {
                var wizardTheme by remember { mutableStateOf(Theme.Dark) }
                Window(
                    onCloseRequest = {
                        scope.launch {
                            MLGDClient.shutdown()
                            globalModrinthApi.close()
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
                            accountManager = globalAccountManager,
                            initialTheme = wizardTheme,
                            onThemeChange = { wizardTheme = it },
                            onWizardComplete = { newSettings ->
                                scope.launch(Dispatchers.IO) {
                                    globalSettingsManager.saveSettings(newSettings)
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
                    val viewModel = remember {
                        AppViewModel(
                            appState = state,
                            buildManager = globalBuildManager,
                            javaManager = globalJavaManager,
                            accountManager = globalAccountManager,
                            javaDownloader = globalJavaDownloader,
                            versionMetadataFetcher = globalVersionMetadataFetcher,
                            onSettingsChange = { newSettings ->
                                appState = state.copy(settings = newSettings)
                                scope.launch { globalSettingsManager.saveSettings(newSettings) }
                            }
                        )
                    }

                    Window(
                        onCloseRequest = {
                            scope.launch {
                                viewModel.cancelScope()
                                MLGDClient.shutdown()
                                globalModrinthApi.close()
                                exitApplication()
                            }
                        },
                        title = stringResource(Res.string.app_name),
                        visible = isContentReady,
                        icon = icon,
                        state = rememberWindowState(width = 1024.dp, height = 768.dp)
                    ) {
                        App(
                            viewModel = viewModel,
                            appState = state,
                            pathManager = globalPathManager,
                            cacheManager = globalCacheManager
                        )
                        SideEffect {
                            if (!isContentReady) {
                                isContentReady = true
                                if (isUiTest) {
                                    println("MATERIAKRAFT_LAUNCHER_UI_TEST_SUCCESS")
                                 }
                            }
                        }
                    }
                }
            }
        }
    }
}
