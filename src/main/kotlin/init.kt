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
import java.time.Month
import java.time.OffsetDateTime
import java.util.*
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

// Флаг, указывающий, что основной контент готов к отображению (используется для скрытия сплеш-скрина).
var isContentReady by mutableStateOf(false)

// Главная функция, точка входа в приложение.
@OptIn(ExperimentalResourceApi::class)
fun main(args: Array<String>) {
    val isUiTest = args.contains("--uitest")

    if (isUiTest) {
        System.setProperty("skiko.renderApi", "SOFTWARE")
        println("Using render API: SOFTWARE (UI Test)")
    } else {
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
    }

    runBlocking(Dispatchers.IO) {
        val pathManager = PathManager(PathManager.getDefaultAppDataDirectory())
        DatabaseManager.init(pathManager)
        val settingsManager = SettingsManager(pathManager)
        val settings = settingsManager.loadSettings()
        Locale.setDefault(Locale(settings.language))
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
