package ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import funlauncher.VersionManifest

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

@OptIn(ExperimentalMaterial3Api::class)
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
                Text("Новая сборка", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название") })
                Spacer(Modifier.height(8.dp))
                Text("Тип:")
                var typeExpanded by remember { mutableStateOf(false) }
                Box {
                    Button(onClick = { typeExpanded = true }) { Text(type) }
                    DropdownMenu(typeExpanded, { typeExpanded = false }) {
                        DropdownMenuItem(text = { Text("Vanilla") }, onClick = {
                            type = "Vanilla"
                            typeExpanded = false
                        })
                        DropdownMenuItem(text = { Text("Fabric") }, onClick = {
                            type = "Fabric"
                            typeExpanded = false
                        })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Версия:")
                var verExpanded by remember { mutableStateOf(false) }
                Box {
                    Button(onClick = { verExpanded = true }) { Text(version.ifEmpty { "Выбрать" }) }
                    DropdownMenu(verExpanded, { verExpanded = false }, modifier = Modifier.height(200.dp)) {
                        if (versionsList.isEmpty()) {
                            DropdownMenuItem(text = { Text("Загрузка...") }, onClick = {}, enabled = false)
                        } else {
                            versionsList.forEach { v ->
                                DropdownMenuItem(text = { Text(v) }, onClick = {
                                    version = v
                                    verExpanded = false
                                })
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
