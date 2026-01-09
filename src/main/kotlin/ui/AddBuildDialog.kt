/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import funlauncher.*
import kotlinx.coroutines.launch
import java.io.File

private fun log(message: String) {
    println("[AddBuildDialog] $message")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBuildDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, BuildType, String?) -> Unit,
    pathManager: PathManager
) {
    log("Composing AddBuildDialog.")

    var name by remember { mutableStateOf("") }
    var buildType by remember { mutableStateOf(BuildType.VANILLA) }
    var selectedMcVersion by remember { mutableStateOf("") }
    var selectedLoaderVersion by remember { mutableStateOf<String>("") }

    var vanillaVersions by remember { mutableStateOf<List<String>>(emptyList()) }
    var fabricGameVersions by remember { mutableStateOf<List<FabricGameVersion>>(emptyList()) }
    var fabricLoaderVersions by remember { mutableStateOf<List<FabricVersion>>(emptyList()) }
    var forgeVersions by remember { mutableStateOf<List<ForgeVersion>>(emptyList()) }
    var applicableForgeVersions by remember { mutableStateOf<List<ForgeVersion>>(emptyList()) }

    var isLoading by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val versionManager = remember { VersionManager(pathManager) }

    val displayedGameVersions = remember(buildType, vanillaVersions, fabricGameVersions, forgeVersions) {
        log("Recalculating displayed game versions. Build type: $buildType")
        when (buildType) {
            BuildType.FABRIC -> {
                val fabricSupportedIds = fabricGameVersions.map { it.version }.toSet()
                log("Fabric selected. Filtering ${vanillaVersions.size} vanilla versions against ${fabricSupportedIds.size} supported IDs.")
                vanillaVersions.filter { it in fabricSupportedIds }
            }
            BuildType.FORGE -> {
                val forgeSupportedIds = forgeVersions.map { it.mcVersion }.distinct().toSet()
                log("Forge selected. Filtering ${vanillaVersions.size} vanilla versions against ${forgeSupportedIds.size} supported IDs.")
                vanillaVersions.filter { it in forgeSupportedIds }
            }
            else -> {
                log("Vanilla selected. Displaying ${vanillaVersions.size} versions.")
                vanillaVersions
            }
        }
    }

    // Fetch initial lists
    LaunchedEffect(Unit) {
        log("Initial LaunchedEffect running.")
        isLoading = true
        scope.launch {
            log("Coroutine for initial fetch started.")
            val vanillaJob = launch {
                log("Fetching vanilla versions...")
                vanillaVersions = versionManager.getMinecraftVersions().filter { it.type == "release" }.map { it.id }
                log("... got ${vanillaVersions.size} vanilla versions.")
            }
            val fabricJob = launch {
                log("Fetching Fabric game versions...")
                fabricGameVersions = versionManager.getFabricGameVersions()
                log("... got ${fabricGameVersions.size} Fabric game versions.")
            }
            val forgeJob = launch {
                log("Fetching Forge versions...")
                forgeVersions = versionManager.getForgeVersions()
                log("... got ${forgeVersions.size} Forge versions.")
            }
            vanillaJob.join()
            fabricJob.join()
            forgeJob.join()
            log("Initial fetch finished.")
            isLoading = false
        }
    }

    // Fetch loader versions when a game version is selected
    LaunchedEffect(selectedMcVersion, buildType) {
        log("LaunchedEffect for selectedMcVersion triggered: '$selectedMcVersion' with type $buildType")
        selectedLoaderVersion = "" // Reset loader version
        if (selectedMcVersion.isNotEmpty()) {
            when (buildType) {
                BuildType.FABRIC -> {
                    isLoading = true
                    fabricLoaderVersions = emptyList() // Clear previous results
                    log("Queueing fetch for Fabric loader versions.")
                    scope.launch {
                        fabricLoaderVersions = versionManager.getFabricLoaderVersions(selectedMcVersion)
                        log("Got ${fabricLoaderVersions.size} loader versions for $selectedMcVersion.")
                        selectedLoaderVersion = fabricLoaderVersions.firstOrNull { it.stable }?.version ?: fabricLoaderVersions.firstOrNull()?.version ?: ""
                        log("Auto-selected Fabric loader version: '$selectedLoaderVersion'")
                        isLoading = false
                    }
                }
                BuildType.FORGE -> {
                    applicableForgeVersions = forgeVersions.filter { it.mcVersion == selectedMcVersion }
                    val recommended = applicableForgeVersions.find { it.isRecommended }
                    val latest = applicableForgeVersions.find { it.isLatest }
                    selectedLoaderVersion = recommended?.forgeVersion ?: latest?.forgeVersion ?: applicableForgeVersions.firstOrNull()?.forgeVersion ?: ""
                    log("Found ${applicableForgeVersions.size} Forge versions for $selectedMcVersion. Auto-selected: '$selectedLoaderVersion'")
                }
                else -> {
                    // No loader for Vanilla
                }
            }
        }
    }

    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    LaunchedEffect(visibleState.currentState) {
        if (!visibleState.currentState && !visibleState.targetState) {
            onDismiss()
        }
    }

    Dialog(onDismissRequest = { visibleState.targetState = false }) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 8 },
            exit = fadeOut(tween(250)) + slideOutVertically(tween(250)) { it / 8 }
        ) {
            Card(Modifier.padding(16.dp)) {
                Column(Modifier.padding(16.dp).width(350.dp)) {
                    Text("Новая сборка", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Название") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))

                    // --- Build Type Button Group ---
                    Text("Тип сборки", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(0.dp) // No space for seamless buttons
                    ) {
                        val types = BuildType.entries
                        types.forEachIndexed { index, type ->
                            val shape = when (index) {
                                0 -> RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)
                                types.lastIndex -> RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
                                else -> RoundedCornerShape(0.dp)
                            }
                            val isSelected = buildType == type
                            OutlinedButton(
                                onClick = {
                                    if (!isSelected) {
                                        log("Build type changed to $type")
                                        buildType = type
                                        selectedMcVersion = ""
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = shape,
                                border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                )
                            ) {
                                Text(type.name.lowercase().replaceFirstChar { it.uppercase() })
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))


                    // --- Game Version Dropdown ---
                    var verExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = verExpanded, onExpandedChange = { verExpanded = !verExpanded }) {
                        OutlinedTextField(
                            value = selectedMcVersion.ifEmpty { "Выберите версию" },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Версия игры") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = verExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = verExpanded, onDismissRequest = { verExpanded = false }) {
                            if (isLoading && displayedGameVersions.isEmpty()) {
                                DropdownMenuItem(text = { Text("Загрузка...") }, onClick = {}, enabled = false)
                            } else {
                                displayedGameVersions.forEach { v ->
                                    DropdownMenuItem(text = { Text(v) }, onClick = {
                                        log("Game version selected: $v")
                                        selectedMcVersion = v
                                        verExpanded = false
                                    })
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))

                    // --- Loader Dropdown (visible for Fabric/Forge) ---
                    if (buildType == BuildType.FABRIC || buildType == BuildType.FORGE) {
                        var loaderExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(expanded = loaderExpanded, onExpandedChange = { loaderExpanded = !loaderExpanded }) {
                            OutlinedTextField(
                                value = if (selectedMcVersion.isEmpty()) "Сначала выберите версию" else selectedLoaderVersion.ifEmpty { "Загрузка..." },
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Версия загрузчика") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = loaderExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                enabled = selectedMcVersion.isNotEmpty()
                            )
                            ExposedDropdownMenu(expanded = loaderExpanded, onDismissRequest = { loaderExpanded = false }) {
                                if (buildType == BuildType.FABRIC) {
                                    if (isLoading) {
                                        DropdownMenuItem(text = { Text("Загрузка...") }, onClick = {}, enabled = false)
                                    } else {
                                        fabricLoaderVersions.forEach { v ->
                                            DropdownMenuItem(text = { Text(v.version) }, onClick = {
                                                log("Loader version selected: ${v.version}")
                                                selectedLoaderVersion = v.version
                                                loaderExpanded = false
                                            })
                                        }
                                    }
                                } else if (buildType == BuildType.FORGE) {
                                    applicableForgeVersions.forEach { v ->
                                        DropdownMenuItem(
                                            text = {
                                                val label = buildString {
                                                    append(v.forgeVersion)
                                                    if (v.isRecommended) append(" (рекомендуемая)")
                                                    else if (v.isLatest) append(" (последняя)")
                                                }
                                                Text(label)
                                            },
                                            onClick = {
                                                log("Loader version selected: ${v.forgeVersion}")
                                                selectedLoaderVersion = v.forgeVersion
                                                loaderExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    Spacer(Modifier.weight(1f))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { visibleState.targetState = false }) { Text("Отмена") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val finalVersion = when (buildType) {
                                    BuildType.FABRIC -> "$selectedMcVersion-fabric-$selectedLoaderVersion"
                                    BuildType.FORGE -> "$selectedMcVersion-forge-$selectedLoaderVersion"
                                    else -> selectedMcVersion
                                }
                                val backgroundsDir = File("src/main/resources/backgrounds")
                                val randomImage = if (backgroundsDir.exists() && backgroundsDir.isDirectory) {
                                    backgroundsDir.listFiles()?.randomOrNull()?.absolutePath
                                } else {
                                    null
                                }
                                log("Creating build with version: $finalVersion")
                                onAdd(name, finalVersion, buildType, randomImage)
                                visibleState.targetState = false
                            },
                            enabled = name.isNotBlank() && selectedMcVersion.isNotBlank() && (buildType == BuildType.VANILLA || selectedLoaderVersion.isNotBlank())
                        ) { Text("Создать") }
                    }
                }
            }
        }
    }
}
