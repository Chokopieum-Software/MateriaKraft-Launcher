import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import funlauncher.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import ui.*
import java.awt.Desktop
import java.io.File

val ColorAkcent = Color(0xCDFF0000)
val BackgroundColor = Color(0xFFF0F0F0)

enum class AppTab { Launch, Builds, Settings, Info }

class VersionFetcher {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    suspend fun getVanillaVersions(): List<String> {
        return try {
            val manifest = client.get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json").body<VersionManifest>()
            manifest.versions.filter { it.type == "release" }.map { it.id }
        } catch (e: Exception) {
            e.printStackTrace()
            listOf("1.21", "1.20.4", "1.19.4", "1.18.2", "1.17.1", "1.16.5", "1.12.2", "1.8.9")
        }
    }
}

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    val buildManager = remember { BuildManager() }
    val settingsManager = remember { SettingsManager() }
    val javaManager = remember { JavaManager() }

    var currentTab by remember { mutableStateOf(AppTab.Launch) }
    var buildList by remember { mutableStateOf(buildManager.loadBuilds()) }
    var appSettings by remember { mutableStateOf(settingsManager.loadSettings()) }

    var selectedBuild by remember { mutableStateOf(buildList.firstOrNull()) }
    var offlineUsername by remember { mutableStateOf("testplayer") }
    var gameProcess by remember { mutableStateOf<Process?>(null) }
    var launchStatus by remember { mutableStateOf("Готов к запуску") }
    var launchProgress by remember { mutableStateOf(0f) }
    var isPreparing by remember { mutableStateOf(false) }
    val isGameRunning = gameProcess?.isAlive == true

    var showAddBuildDialog by remember { mutableStateOf(false) }
    var showJavaManagerWindow by remember { mutableStateOf(false) }
    var showWarningDialog by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var errorDialogMessage by remember { mutableStateOf<String?>(null) }
    var buildToDelete by remember { mutableStateOf<funlauncher.MinecraftBuild?>(null) }

    LaunchedEffect(gameProcess) {
        if (gameProcess != null) {
            scope.launch {
                gameProcess?.waitFor()
                launchStatus = "Игра завершена"
                gameProcess = null
            }
        }
    }

    val onLaunchClick: () -> Unit = onLaunchClick@{
        val build = selectedBuild
        if (build == null) {
            errorDialogMessage = "Сначала выберите сборку!"
            return@onLaunchClick
        }

        if (isGameRunning) {
            gameProcess?.destroyForcibly()
            return@onLaunchClick
        }

        scope.launch {
            val recommendedVersion = javaManager.getRecommendedJavaVersion(build.version)
            val installations = javaManager.findJavaInstallations()
            val allJavas = installations.launcher + installations.system
            val bestJava = allJavas.firstOrNull { it.version >= recommendedVersion && it.is64Bit }

            if (bestJava == null) {
                errorDialogMessage = "Не найдена подходящая Java (нужна версия $recommendedVersion+). Откройте Управление Java, чтобы установить ее."
                return@launch
            }

            isPreparing = true
            try {
                val finalSettings = appSettings.copy(javaPath = bestJava.path)
                val installer = MinecraftInstaller(build)
                val process = installer.launchOffline(offlineUsername, finalSettings) { msg, progress ->
                    launchStatus = msg
                    launchProgress = progress
                }
                gameProcess = process
                launchStatus = "Игра запущена!"
            } catch (e: Exception) {
                e.printStackTrace()
                errorDialogMessage = "Ошибка запуска: ${e.message}"
            } finally {
                isPreparing = false
            }
        }
    }

    MaterialTheme {
        Row(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
            Column(
                modifier = Modifier.width(200.dp).fillMaxHeight().background(Color.White),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TabButton("Запуск", currentTab == AppTab.Launch) { currentTab = AppTab.Launch }
                TabButton("Сборки", currentTab == AppTab.Builds) {
                    buildList = buildManager.loadBuilds()
                    currentTab = AppTab.Builds
                }
                TabButton("Настройки", currentTab == AppTab.Settings) { currentTab = AppTab.Settings }
                TabButton("Инфо", currentTab == AppTab.Info) { currentTab = AppTab.Info }
            }

            Box(modifier = Modifier.weight(1f).padding(16.dp)) {
                when (currentTab) {
                    AppTab.Launch -> LaunchTab(
                        builds = buildList,
                        selectedBuild = selectedBuild,
                        onBuildSelect = { selectedBuild = it },
                        offlineUsername = offlineUsername,
                        onUsernameChange = { offlineUsername = it },
                        launchStatus = launchStatus,
                        launchProgress = launchProgress,
                        isPreparing = isPreparing,
                        isGameRunning = isGameRunning,
                        onLaunchClick = onLaunchClick
                    )
                    AppTab.Builds -> BuildsTab(
                        builds = buildList,
                        onAddClick = { showAddBuildDialog = true },
                        onDeleteClick = { buildToDelete = it },
                        onOpenFolderClick = { openFolder(it.installPath) }
                    )
                    AppTab.Settings -> SettingsTab(
                        currentSettings = appSettings,
                        onSave = { newSettings ->
                            appSettings = newSettings
                            settingsManager.saveSettings(newSettings)
                        },
                        onOpenJavaManager = { showJavaManagerWindow = true }
                    )
                    AppTab.Info -> Text("Раздел в разработке: $currentTab", style = MaterialTheme.typography.h5)
                }
            }
        }

        if (showJavaManagerWindow) {
            JavaManagerWindow(
                onCloseRequest = { showJavaManagerWindow = false },
                javaManager = javaManager,
                javaDownloader = JavaDownloader()
            )
        }
        if (showAddBuildDialog) {
            AddBuildDialog(
                onDismiss = { showAddBuildDialog = false },
                onAdd = { n, v, t ->
                    try {
                        buildManager.addBuild(n, v, t)
                        buildList = buildManager.loadBuilds()
                        showAddBuildDialog = false
                    } catch (e: Exception) {
                        errorDialogMessage = e.message
                    }
                }
            )
        }
        showWarningDialog?.let { (text, onConfirm) ->
            AlertDialog(
                onDismissRequest = { showWarningDialog = null },
                title = { Text("Предупреждение") },
                text = { Text(text) },
                confirmButton = { Button(onClick = { onConfirm(); showWarningDialog = null }) { Text("Продолжить") } },
                dismissButton = { Button(onClick = { showWarningDialog = null }) { Text("Отмена") } }
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
                            buildList = buildManager.loadBuilds()
                            buildToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color.Red)
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
