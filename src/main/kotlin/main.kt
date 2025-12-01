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
import androidx.compose.material.icons.filled.Refresh
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

// === ЗАГЛУШКИ (Оставим пока не реализуем) ===
data class AuthSession(val username: String, val accessToken: String)

class ElyByAuth {
    fun loadSession(): AuthSession? = null
    fun saveSession(session: AuthSession) {}
    fun deleteSession() {}
    suspend fun loginAsync(user: String, pass: String): AuthSession {
        delay(1000) // Имитация запроса
        return AuthSession(user, "token_123")
    }
}

class MinecraftInstaller(private val buildName: String) {
    data class InstallProgress(val progress: Float, val message: String)
    data class LaunchResult(val success: Boolean, val errorMessage: String = "")

    suspend fun ensureInstalled(version: String, type: String, onProgress: (InstallProgress) -> Unit): String {
        for (i in 0..100 step 10) {
            delay(200)
            onProgress(InstallProgress(i / 100f, "Скачивание файлов... $i%"))
        }
        return "ver-id-$version"
    }

    suspend fun launchMinecraft(version: String, session: AuthSession): LaunchResult {
        delay(1500)
        return LaunchResult(true)
    }
}


// === ГЛАВНОЕ ПРИЛОЖЕНИЕ И UI ===

val GreenPrimary = Color(0xFF1B4D2B)
val BackgroundColor = Color(0xFFF0F0F0)

enum class AppTab { Launch, Builds, Mods, Settings, Info }

@Composable
@Preview
fun App() {
    val scope = rememberCoroutineScope()
    // Используем реальный BuildManager, а не заглушку
    val buildManager = remember { BuildManager() }
    val authClient = remember { ElyByAuth() }

    var currentSession by remember { mutableStateOf(authClient.loadSession()) }
    var currentTab by remember { mutableStateOf(AppTab.Launch) }
    var statusText by remember { mutableStateOf("Готов к запуску") }
    // Загружаем сборки из файла при старте
    var buildList by remember { mutableStateOf(buildManager.loadBuilds()) }

    // Состояния для диалоговых окон
    var showLoginDialog by remember { mutableStateOf(false) }
    var showAddBuildDialog by remember { mutableStateOf(false) }
    var errorDialogMessage by remember { mutableStateOf<String?>(null) }
    var buildToDelete by remember { mutableStateOf<MinecraftBuild?>(null) }

    MaterialTheme {
        Row(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
            // Сайдбар
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

                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (currentSession != null) "User: ${currentSession!!.username}" else "Гость",
                        style = MaterialTheme.typography.caption
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        if (currentSession != null) {
                            currentSession = null
                            authClient.deleteSession()
                        } else {
                            showLoginDialog = true
                        }
                    }, colors = ButtonDefaults.buttonColors(backgroundColor = GreenPrimary, contentColor = Color.White)) {
                        Text(if (currentSession != null) "Выйти" else "Войти")
                    }
                }
            }

            // Основной контент
            Box(modifier = Modifier.weight(1f).padding(16.dp)) {
                when (currentTab) {
                    AppTab.Launch -> LaunchTab(buildList, currentSession) { statusText = it }
                    AppTab.Builds -> BuildsTab(
                        builds = buildList,
                        onAddClick = { showAddBuildDialog = true },
                        onDeleteClick = { build -> buildToDelete = build },
                        onOpenFolderClick = { build -> openFolder(build.installPath) },
                        onRefresh = { buildList = buildManager.loadBuilds() }
                    )
                    else -> Text("Раздел в разработке: $currentTab", style = MaterialTheme.typography.h5)
                }
            }
        }

        // Статус-бар внизу
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
            Text(statusText, modifier = Modifier.fillMaxWidth().background(Color.LightGray).padding(4.dp))
        }

        // === Диалоговые окна ===
        if (showLoginDialog) {
            LoginDialog(onDismiss = { showLoginDialog = false }) { user, pass ->
                scope.launch {
                    statusText = "Авторизация..."
                    currentSession = authClient.loginAsync(user, pass)
                    authClient.saveSession(currentSession!!)
                    statusText = "Вход выполнен!"
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


// === UI Компоненты для вкладок и диалогов ===

@Composable
fun LaunchTab(builds: List<MinecraftBuild>, session: AuthSession?, onStatus: (String) -> Unit) {
    var selectedBuild by remember { mutableStateOf(builds.firstOrNull()) }
    var progress by remember { mutableStateOf(0f) }
    var isLaunching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("СБОРКА", style = MaterialTheme.typography.caption)

        var expanded by remember { mutableStateOf(false) }
        Box {
            Button(onClick = { expanded = true }, colors = ButtonDefaults.buttonColors(backgroundColor = Color.White)) {
                Text(selectedBuild?.name ?: "Нет сборок")
            }
            DropdownMenu(expanded, { expanded = false }) {
                builds.forEach { b -> DropdownMenuItem({ selectedBuild = b; expanded = false }) { Text(b.name) } }
            }
        }

        Spacer(Modifier.height(30.dp))

        if (isLaunching) {
            LinearProgressIndicator(progress = progress, modifier = Modifier.width(200.dp), color = GreenPrimary)
            Text("${(progress * 100).toInt()}%")
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                scope.launch {
                    isLaunching = true
                    try {
                        val installer = MinecraftInstaller(selectedBuild!!.name)
                        val ver = installer.ensureInstalled(selectedBuild!!.version, selectedBuild!!.type) {
                            progress = it.progress
                            onStatus(it.message)
                        }
                        onStatus("Запуск игры...")
                        installer.launchMinecraft(ver, session!!)
                        onStatus("Игра запущена!")
                    } catch (e: Exception) {
                        onStatus("Ошибка: ${e.message}")
                    } finally {
                        isLaunching = false; progress = 0f
                    }
                }
            },
            enabled = !isLaunching && session != null && selectedBuild != null,
            modifier = Modifier.size(200.dp, 60.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = GreenPrimary, contentColor = Color.White)
        ) {
            Text("ИГРАТЬ", style = MaterialTheme.typography.h5)
        }
    }
}

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
            IconButton(onAddClick) { Icon(Icons.Default.Add, null) }
            IconButton(onRefresh) { Icon(Icons.Default.Refresh, null) }
        }
        LazyColumn {
            items(builds) { build ->
                Card(Modifier.fillMaxWidth().padding(4.dp), elevation = 2.dp) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(build.name, style = MaterialTheme.typography.h6); Text(build.version) }
                        IconButton({ onOpenFolderClick(build) }) { Icon(Icons.Default.Folder, null) }
                        IconButton({ onDeleteClick(build) }) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                    }
                }
            }
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
                Text("Вход Ely.by", style = MaterialTheme.typography.h6)
                TextField(u, { u = it }, label = { Text("Логин") })
                TextField(p, { p = it }, label = { Text("Пароль") })
                Button({ onLogin(u, p) }, Modifier.padding(top = 8.dp)) { Text("Войти") }
            }
        }
    }
}

@Composable
fun AddBuildDialog(onDismiss: () -> Unit, onAdd: (String, String, String) -> Unit) {
    var n by remember { mutableStateOf("") }
    var v by remember { mutableStateOf("1.20.1") }
    var t by remember { mutableStateOf("Fabric") }
    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.padding(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Новая сборка")
                TextField(n, { n = it }, label = { Text("Название") })
                TextField(v, { v = it }, label = { Text("Версия") })
                TextField(t, { t = it }, label = { Text("Тип") })
                Button({ onAdd(n, v, t) }, Modifier.padding(top = 8.dp)) { Text("Создать") }
            }
        }
    }
}

fun openFolder(path: String) = runCatching { Desktop.getDesktop().open(File(path)) }

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "EchoLauncher Kotlin") {
        App()
    }
}