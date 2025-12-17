package ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import funlauncher.JavaDownloader
import funlauncher.JavaInfo
import funlauncher.JavaInstallations
import funlauncher.JavaManager
import kotlinx.coroutines.launch

@Composable
fun JavaManagerWindow(
    onCloseRequest: () -> Unit,
    javaManager: JavaManager,
    javaDownloader: JavaDownloader
) {
    var installations by remember { mutableStateOf<JavaInstallations?>(null) }
    var status by remember { mutableStateOf("Загрузка...") }
    var progress by remember { mutableStateOf(0f) }
    var isWorking by remember { mutableStateOf(true) }
    var javaToDelete by remember { mutableStateOf<JavaInfo?>(null) }
    var showInstallDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        isWorking = true
        status = "Поиск Java..."
        installations = javaManager.findJavaInstallations()
        status = "Готов"
        isWorking = false
    }

    LaunchedEffect(Unit) {
        isWorking = true
        status = "Поиск Java..."
        installations = javaManager.findJavaInstallations()
        status = "Готов"
        isWorking = false
    }

    fun install(version: Int) {
        scope.launch {
            isWorking = true
            try {
                javaDownloader.downloadAndUnpack(version) { msg, p ->
                    status = msg
                    progress = p
                }
                status = "Java $version установлена!"
                refresh()
            } catch (e: Exception) {
                status = "Ошибка: ${e.message}"
                e.printStackTrace()
            } finally {
                isWorking = false
            }
        }
    }

    fun delete(javaInfo: JavaInfo) {
        scope.launch {
            javaToDelete = javaInfo
            try {
                javaManager.deleteLauncherJre(javaInfo)
                refresh()
            } catch (e: Exception) {
                status = "Ошибка удаления: ${e.message}"
                e.printStackTrace()
            } finally {
                javaToDelete = null
            }
        }
    }

    Window(
        onCloseRequest = onCloseRequest,
        title = "Управление Java",
        state = rememberWindowState(width = 700.dp, height = 500.dp, position = WindowPosition(Alignment.Center))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (installations == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Row(Modifier.weight(1f)) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Установлено лаунчером", style = MaterialTheme.typography.h6)
                            LazyColumn(Modifier.fillMaxHeight()) {
                                if (installations!!.launcher.isEmpty()) item { Text("Нет версий, установленных лаунчером.") }
                                items(installations!!.launcher) { java ->
                                    JavaListItem(java, isDeleting = java == javaToDelete) { delete(java) }
                                }
                            }
                        }
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text("Обнаружено в системе", style = MaterialTheme.typography.h6)
                            LazyColumn(Modifier.fillMaxHeight()) {
                                if (installations!!.system.isEmpty()) item { Text("В системе не найдено Java.") }
                                items(installations!!.system) { java ->
                                    JavaListItem(
                                        java,
                                        isDeleting = false,
                                        onDelete = null
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    AnimatedVisibility(visible = isWorking && installations != null) {
                        LinearProgressIndicator(progress, modifier = Modifier.fillMaxWidth())
                    }
                    Text(status, style = MaterialTheme.typography.caption)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { showInstallDialog = true },
                        enabled = !isWorking,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Установить JDK")
                    }
                }
            }
        }

        if (showInstallDialog) {
            InstallJavaDialog(
                onDismiss = { showInstallDialog = false },
                onInstall = { version ->
                    showInstallDialog = false
                    install(version)
                }
            )
        }
    }
}

@Composable
private fun JavaListItem(java: JavaInfo, isDeleting: Boolean, onDelete: (() -> Unit)?) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), elevation = 2.dp) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(java.displayName, style = MaterialTheme.typography.body1)
                Text(java.path, style = MaterialTheme.typography.caption, maxLines = 1)
            }
            if (isDeleting) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Delete, "Удалить", tint = Color.Red)
                }
            }
        }
    }
}

@Composable
private fun InstallJavaDialog(onDismiss: () -> Unit, onInstall: (Int) -> Unit) {
    val versions = listOf(8, 11, 17, 21, 25)
    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.padding(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Выберите версию JDK для установки", style = MaterialTheme.typography.h6)
                Spacer(Modifier.height(16.dp))
                versions.forEach { version ->
                    Button(
                        onClick = { onInstall(version) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text("Java $version")
                    }
                }
            }
        }
    }
}
