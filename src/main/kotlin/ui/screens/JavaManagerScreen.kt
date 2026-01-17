/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import funlauncher.*
import funlauncher.managers.JavaManager
import funlauncher.net.DownloadManager
import funlauncher.net.JavaDownloader
import kotlinx.coroutines.launch
import ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JavaManagerWindow(
    onCloseRequest: () -> Unit,
    javaManager: JavaManager,
    javaDownloader: JavaDownloader,
    appSettings: AppSettings
) {
    var installations by remember { mutableStateOf<JavaInstallations?>(null) }
    var status by remember { mutableStateOf("Загрузка...") }
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
        refresh()
    }

    fun install(version: Int) {
        scope.launch {
            javaDownloader.downloadAndUnpack(version) { result ->
                scope.launch {
                    result.fold(
                        onSuccess = {
                            status = "Java $version установлена!"
                            refresh()
                        },
                        onFailure = {
                            status = "Ошибка: ${it.message}"
                            it.printStackTrace()
                        }
                    )
                }
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

    Dialog(onDismissRequest = onCloseRequest) {
        AppTheme(appSettings.theme) {
            Scaffold(
                topBar = {
                    TopAppBar(title = { Text("Управление Java") })
                },
                bottomBar = {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(status, style = MaterialTheme.typography.labelSmall)
                        Button(
                            onClick = { showInstallDialog = true },
                            enabled = DownloadManager.tasks.isEmpty(),
                        ) {
                            Text("Установить JDK")
                        }
                    }
                }
            ) { paddingValues ->
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                    if (installations == null && isWorking) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else {
                        Row(Modifier.fillMaxSize().padding(16.dp)) {
                            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                Text("Установлено лаунчером", style = MaterialTheme.typography.titleMedium)
                                LazyColumn(Modifier.fillMaxHeight()) {
                                    if (installations?.launcher?.isEmpty() != false) item { Text("Нет версий, установленных лаунчером.") }
                                    items(installations?.launcher ?: emptyList()) { java ->
                                        JavaListItem(java, isDeleting = java == javaToDelete) { delete(java) }
                                    }
                                }
                            }
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                Text("Обнаружено в системе", style = MaterialTheme.typography.titleMedium)
                                LazyColumn(Modifier.fillMaxHeight()) {
                                    if (installations?.system?.isEmpty() != false) item { Text("В системе не найдено Java.") }
                                    items(installations?.system ?: emptyList()) { java ->
                                        JavaListItem(
                                            java,
                                            isDeleting = false,
                                            onDelete = null
                                        )
                                    }
                                }
                            }
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
                    },
                    theme = appSettings.theme
                )
            }
        }
    }
}

@Composable
private fun JavaListItem(java: JavaInfo, isDeleting: Boolean, onDelete: (() -> Unit)?) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(java.displayName, style = MaterialTheme.typography.bodyLarge)
                Text(java.path, style = MaterialTheme.typography.labelSmall, maxLines = 1)
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
private fun InstallJavaDialog(onDismiss: () -> Unit, onInstall: (Int) -> Unit, theme: Theme) {
    val versions = listOf(8, 11, 17, 21, 25)
    Dialog(onDismissRequest = onDismiss) {
        AppTheme(theme) {
            Card(Modifier.padding(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Выберите версию JDK для установки", style = MaterialTheme.typography.titleLarge)
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
}
