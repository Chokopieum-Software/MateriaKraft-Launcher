
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.io.File

// Цвета
val GreenPrimary = Color(0xFF1B4D2B)
val BackgroundColor = Color(0xFFF0F0F0)

enum class AppTab { Launch, Builds, Mods, Settings, Info }

// Заглушка AuthSession
data class AuthSession(val username: String, val accessToken: String)
class ElyByAuth {
    fun loadSession(): AuthSession? = null
    fun saveSession(session: AuthSession) {}
    fun deleteSession() {}
    suspend fun loginAsync(user: String, pass: String): AuthSession {
        delay(1000)
        return AuthSession(user, "token_123")
    }
}

// Загрузчик списка версий
class VersionFetcher {
    suspend fun getVanillaVersions(): List<String> {
        return listOf("1.20.4", "1.20.1", "1.19.4", "1.18.2", "1.16.5", "1.12.2")
    }
}

@Composable
@Preview
fun App() {
    val scope = rememberCoroutineScope()
    // Инициализируем менеджеры
    val buildManager = remember { BuildManager() }
    val authClient = remember { ElyByAuth() }
    val settingsManager = remember { SettingsManager() }

    // Состояния
    var currentSession by remember { mutableStateOf(authClient.loadSession()) }
    var currentTab by remember { mutableStateOf(AppTab.Launch) }
    var buildList by remember { mutableStateOf(buildManager.loadBuilds()) }
    var appSettings by remember { mutableStateOf(settingsManager.loadSettings()) }

    // Диалоги
    var showLoginDialog by remember { mutableStateOf(false) }
    var showAddBuildDialog by remember { mutableStateOf(false) }
    var errorDialogMessage by remember { mutableStateOf<String?>(null) }
    var buildToDelete by remember { mutableStateOf<MinecraftBuild?>(null) }

    MaterialTheme {
        Row(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
            // === SIDEBAR ===
            Column(
                modifier = Modifier.width(200.dp).fillMaxHeight().background(Color.White),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TabButton("Запуск", currentTab == AppTab.Launch) { currentTab = AppTab.Launch }
                TabButton("Сборки", currentTab == AppTab.Builds) {
                    buildList = buildManager.loadBuilds()
                    currentTab = AppTab.Builds
                }
                TabButton("Моды", currentTab == AppTab.Mods) { currentTab = AppTab.Mods }
                TabButton("Настройки", currentTab == AppTab.Settings) { currentTab = AppTab.Settings }
                TabButton("Инфо", currentTab == AppTab.Info) { currentTab = AppTab.Info }

                Spacer(Modifier.weight(1f))

                // Кнопка входа
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (currentSession != null) "User: ${currentSession!!.username}" else "Гость",
                        style = MaterialTheme.typography.caption
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (currentSession != null) {
                                currentSession = null
                                authClient.deleteSession()
                            } else {
                                showLoginDialog = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(backgroundColor = GreenPrimary, contentColor = Color.White)
                    ) {
                        Text(if (currentSession != null) "Выйти" else "Войти")
                    }
                }
            }

            // === CONTENT ===
            Box(modifier = Modifier.weight(1f).padding(16.dp)) {
                when (currentTab) {
                    // ВАЖНО: LaunchTab теперь не требует statusText
                    AppTab.Launch -> LaunchTab(
                        builds = buildList,
                        session = currentSession,
                        settings = appSettings
                    )

                    AppTab.Builds -> BuildsTab(
                        builds = buildList,
                        onAddClick = { showAddBuildDialog = true },
                        onDeleteClick = { buildToDelete = it },
                        onOpenFolderClick = { openFolder(it.installPath) },
                        onRefresh = { buildList = buildManager.loadBuilds() }
                    )
                    AppTab.Settings -> SettingsTab(appSettings) { newSettings ->
                        appSettings = newSettings
                        settingsManager.saveSettings(newSettings)
                    }
                    else -> Text("Раздел в разработке: $currentTab", style = MaterialTheme.typography.h5)
                }
            }
        }

        // === ДИАЛОГИ ===
        if (showLoginDialog) {
            LoginDialog(onDismiss = { showLoginDialog = false }) { user, pass ->
                scope.launch {
                    currentSession = authClient.loginAsync(user, pass)
                    authClient.saveSession(currentSession!!)
                    showLoginDialog = false
                }
            }
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

        if (errorDialogMessage != null) {
            AlertDialog(
                onDismissRequest = { errorDialogMessage = null },
                title = { Text("Ошибка") },
                text = { Text(errorDialogMessage!!) },
                confirmButton = { Button(onClick = { errorDialogMessage = null }) { Text("OK") } }
            )
        }

        if (buildToDelete != null) {
            AlertDialog(
                onDismissRequest = { buildToDelete = null },
                title = { Text("Подтверждение") },
                text = { Text("Вы уверены, что хотите удалить сборку '${buildToDelete!!.name}'? Папка также будет удалена.") },
                confirmButton = {
                    Button(
                        onClick = {
                            buildManager.deleteBuild(buildToDelete!!.name)
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

// === ОБНОВЛЕННАЯ LaunchTab С ПРОГРЕССОМ ===

@Composable
fun LaunchTab(builds: List<MinecraftBuild>, session: AuthSession?, settings: AppSettings) {
    var selectedBuild by remember { mutableStateOf(builds.firstOrNull()) }
    var isLaunching by remember { mutableStateOf(false) }
    var offlineUsername by remember { mutableStateOf("testplayer") }

    // Локальное состояние прогресса
    var launchStatus by remember { mutableStateOf("Готов к запуску") }
    var launchProgress by remember { mutableStateOf(0f) }

    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {

        OutlinedTextField(value = offlineUsername, onValueChange = { offlineUsername = it }, label = { Text("Никнейм") })
        Spacer(Modifier.height(16.dp))

        // Выпадающий список
        var expanded by remember { mutableStateOf(false) }
        Box {
            Button(onClick = { expanded = true }, colors = ButtonDefaults.buttonColors(backgroundColor = Color.White)) {
                Text(selectedBuild?.name ?: "Выберите сборку")
            }
            DropdownMenu(expanded, { expanded = false }) {
                builds.forEach { b -> DropdownMenuItem({ selectedBuild = b; expanded = false }) { Text(b.name) } }
            }
        }

        Spacer(Modifier.height(30.dp))

        // Блок прогресса
        if (isLaunching) {
            Text(launchStatus, style = MaterialTheme.typography.caption)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = launchProgress,
                modifier = Modifier.width(300.dp).height(10.dp),
                color = GreenPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text("${(launchProgress * 100).toInt()}%")
        } else {
            Text(launchStatus, style = MaterialTheme.typography.caption)
            Spacer(Modifier.height(20.dp))
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                if (selectedBuild != null && offlineUsername.isNotBlank()) {
                    scope.launch {
                        isLaunching = true
                        launchProgress = 0f
                        try {
                            val installer = MinecraftInstaller(selectedBuild!!)
                            // Передаем callback для обновления прогресса
                            installer.launchOffline(offlineUsername, settings) { msg, progress ->
                                launchStatus = msg
                                launchProgress = progress
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            launchStatus = "Ошибка: ${e.message}"
                        } finally {
                            isLaunching = false
                            launchStatus = "Запущено"
                            launchProgress = 1f
                        }
                    }
                } else {
                    launchStatus = "Выберите сборку и введите ник!"
                }
            },
            enabled = !isLaunching && selectedBuild != null,
            modifier = Modifier.size(200.dp, 60.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = GreenPrimary, contentColor = Color.White)
        ) {
            Text(if (isLaunching) "ЗАГРУЗКА..." else "ИГРАТЬ", style = MaterialTheme.typography.h5)
        }
    }
}

// === ОСТАЛЬНЫЕ КОМПОНЕНТЫ ===

@Composable
fun BuildsTab(
    builds: List<MinecraftBuild>,
    onAddClick: () -> Unit,
    onDeleteClick: (MinecraftBuild) -> Unit,
    onOpenFolderClick: (MinecraftBuild) -> Unit,
    onRefresh: () -> Unit
) {
    Column {
        Row(Modifier.padding(8.dp)) {
            IconButton(onAddClick) { Icon(Icons.Default.Add, "Создать") }
//            IconButton(onRefresh) { Icon(Icons.Default.Refresh, "Обновить") }
        }
        LazyColumn {
            items(builds) { build ->
                Card(Modifier.fillMaxWidth().padding(4.dp), elevation = 2.dp) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(build.name, style = MaterialTheme.typography.h6)
                            Text("${build.version} (${build.type})", style = MaterialTheme.typography.body2)
                        }
                        IconButton({ onOpenFolderClick(build) }) { Icon(Icons.Default.Folder, null) }
                        IconButton({ onDeleteClick(build) }) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsTab(currentSettings: AppSettings, onSave: (AppSettings) -> Unit) {
    var ramValue by remember { mutableStateOf(currentSettings.maxRamMb.toString()) }
    var javaArgs by remember { mutableStateOf(currentSettings.javaArgs) }
    var envVars by remember { mutableStateOf(currentSettings.envVars) }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Text("Настройки", style = MaterialTheme.typography.h5)
        Spacer(Modifier.height(16.dp))

        Text("ОЗУ (МБ):")
        OutlinedTextField(
            value = ramValue,
            onValueChange = { ramValue = it.filter { char -> char.isDigit() } },
            label = { Text("2048") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        Text("Аргументы Java:")
        OutlinedTextField(
            value = javaArgs,
            onValueChange = { javaArgs = it },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        Text("Переменные среды (VAR=VAL):")
        OutlinedTextField(
            value = envVars,
            onValueChange = { envVars = it },
            modifier = Modifier.fillMaxWidth().height(150.dp),
            maxLines = 10
        )
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                val ram = ramValue.toIntOrNull() ?: 2048
                onSave(currentSettings.copy(maxRamMb = ram, javaArgs = javaArgs, envVars = envVars))
            },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Сохранить")
        }
    }
}

@Composable
fun TabButton(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(50.dp)
            .background(if (active) GreenPrimary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(text, color = if (active) Color.White else Color.Black)
    }
}

@Composable
fun LoginDialog(onDismiss: () -> Unit, onLogin: (String, String) -> Unit) {
    var u by remember { mutableStateOf("") }
    var p by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.padding(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Вход Ely.by (Не реализовано)", style = MaterialTheme.typography.h6)
                TextField(u, { u = it }, label = { Text("Логин") })
                TextField(p, { p = it }, label = { Text("Пароль") })
                Button({ onLogin(u, p) }, Modifier.padding(top = 8.dp)) { Text("Войти") }
            }
        }
    }
}

@Composable
fun AddBuildDialog(onDismiss: () -> Unit, onAdd: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Vanilla") }
    var version by remember { mutableStateOf("") }

    // В реальности тут лучше использовать VersionFetcher
    val versionsList = listOf("1.20.4", "1.20.1", "1.19.4", "1.18.2", "1.16.5", "1.12.2")

    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.padding(16.dp).height(400.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Новая сборка", style = MaterialTheme.typography.h6)
                Spacer(Modifier.height(16.dp))
                TextField(value = name, onValueChange = { name = it }, label = { Text("Название") })

                Spacer(Modifier.height(8.dp))
                Text("Тип:")
                var typeExpanded by remember { mutableStateOf(false) }
                Box {
                    Button(onClick = { typeExpanded = true }) { Text(type) }
                    DropdownMenu(typeExpanded, { typeExpanded = false }) {
                        DropdownMenuItem({ type = "Vanilla"; typeExpanded = false }) { Text("Vanilla") }
                        DropdownMenuItem({ type = "Fabric"; typeExpanded = false }) { Text("Fabric") }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("Версия:")
                var verExpanded by remember { mutableStateOf(false) }
                Box {
                    Button(onClick = { verExpanded = true }) { Text(if (version.isEmpty()) "Выбрать" else version) }
                    DropdownMenu(verExpanded, { verExpanded = false }, modifier = Modifier.height(200.dp)) {
                        versionsList.forEach { v ->
                            DropdownMenuItem({ version = v; verExpanded = false }) { Text(v) }
                        }
                    }
                }

                Spacer(Modifier.weight(1f))
                Button(onClick = { onAdd(name, version, type) }, enabled = name.isNotBlank() && version.isNotBlank()) {
                    Text("Создать")
                }
            }
        }
    }
}

fun openFolder(path: String) = runCatching { Desktop.getDesktop().open(File(path)) }

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "EchoLauncher PreAlpha") {
        App()
    }
}