package ui

import VersionFetcher
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
import funlauncher.MinecraftBuild
import kotlinx.coroutines.launch

@Composable
fun BuildsTab(
    builds: List<MinecraftBuild>,
    onAddClick: () -> Unit,
    onDeleteClick: (MinecraftBuild) -> Unit,
    onOpenFolderClick: (MinecraftBuild) -> Unit
) {
    Column {
        Row(Modifier.padding(8.dp)) {
            IconButton(onAddClick) { Icon(Icons.Default.Add, "Создать") }
        }
        LazyColumn {
            items(builds) { build ->
                Card(Modifier.fillMaxWidth().padding(4.dp), elevation = 2.dp) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(build.name, style = MaterialTheme.typography.h6)
                            Text(
                                "${build.version} (${build.type})",
                                style = MaterialTheme.typography.body2
                            )
                        }
                        IconButton({ onOpenFolderClick(build) }) {
                            Icon(
                                Icons.Default.Folder,
                                null
                            )
                        }
                        IconButton({ onDeleteClick(build) }) {
                            Icon(
                                Icons.Default.Delete,
                                null,
                                tint = Color.Red
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddBuildDialog(onDismiss: () -> Unit, onAdd: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Vanilla") }
    var version by remember { mutableStateOf("") }
    var versionsList by remember { mutableStateOf(emptyList<String>()) }
    val scope = rememberCoroutineScope()
    val versionFetcher = remember { VersionFetcher() }

    LaunchedEffect(Unit) { scope.launch { versionsList = versionFetcher.getVanillaVersions() } }

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
                        DropdownMenuItem(onClick = {
                            type = "Vanilla"
                            typeExpanded = false
                        }) { Text("Vanilla") }
                        DropdownMenuItem(onClick = {
                            type = "Fabric"
                            typeExpanded = false
                        }) { Text("Fabric") }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Версия:")
                var verExpanded by remember { mutableStateOf(false) }
                Box {
                    Button(onClick = { verExpanded = true }) { Text(version.ifEmpty { "Выбрать" }) }
                    DropdownMenu(verExpanded, { verExpanded = false }, modifier = Modifier.height(200.dp)) {
                        if (versionsList.isEmpty()) {
                            DropdownMenuItem(onClick = {}, enabled = false) { Text("Загрузка...") }
                        } else {
                            versionsList.forEach { v ->
                                DropdownMenuItem(onClick = {
                                    version = v
                                    verExpanded = false
                                }) { Text(v) }
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { onAdd(name, version, type) },
                    enabled = name.isNotBlank() && version.isNotBlank()
                ) { Text("Создать") }
            }
        }
    }
}
