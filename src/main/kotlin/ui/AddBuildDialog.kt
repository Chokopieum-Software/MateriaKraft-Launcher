/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package ui

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

private fun log(message: String) {
    println("[AddBuildDialog] $message")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBuildDialog(onDismiss: () -> Unit, onAdd: (String, String, BuildType) -> Unit) {
    log("Composing AddBuildDialog.")

    var name by remember { mutableStateOf("") }
    var buildType by remember { mutableStateOf(BuildType.VANILLA) }
    var selectedMcVersion by remember { mutableStateOf("") }
    var selectedLoaderVersion by remember { mutableStateOf("") }

    var vanillaVersions by remember { mutableStateOf<List<String>>(emptyList()) }
    var fabricGameVersions by remember { mutableStateOf<List<FabricGameVersion>>(emptyList()) }
    var fabricLoaderVersions by remember { mutableStateOf<List<FabricVersion>>(emptyList()) }
    var forgeVersions by remember { mutableStateOf<List<ForgeVersion>>(emptyList()) }

    var isLoading by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val versionManager = remember { VersionManager() }

    val displayedGameVersions = remember(buildType, vanillaVersions, fabricGameVersions, forgeVersions) {
        log("Recalculating displayed game versions. Build type: $buildType")
        when (buildType) {
            BuildType.FABRIC -> {
                val fabricSupportedIds = fabricGameVersions.map { it.version }.toSet()
                log("Fabric selected. Filtering ${vanillaVersions.size} vanilla versions against ${fabricSupportedIds.size} supported IDs.")
                vanillaVersions.filter { it in fabricSupportedIds }
            }
            BuildType.FORGE -> {
                val forgeSupportedIds = forgeVersions.map { it.mcVersion }.toSet()
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
                    val forgeInfo = forgeVersions.find { it.mcVersion == selectedMcVersion && it.isRecommended }
                        ?: forgeVersions.find { it.mcVersion == selectedMcVersion }
                    selectedLoaderVersion = forgeInfo?.forgeVersion ?: ""
                    log("Auto-selected Forge version: '$selectedLoaderVersion'")
                }
                else -> {
                    // No loader for Vanilla
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
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
                    val types = BuildType.values()
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
                            value = selectedLoaderVersion.ifEmpty { if (selectedMcVersion.isEmpty()) "Сначала выберите версию" else "Загрузка..." },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Версия загрузчика") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = loaderExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            enabled = selectedMcVersion.isNotEmpty()
                        )
                        if (buildType == BuildType.FABRIC) {
                            ExposedDropdownMenu(expanded = loaderExpanded, onDismissRequest = { loaderExpanded = false }) {
                                fabricLoaderVersions.forEach { v ->
                                    DropdownMenuItem(text = { Text(v.version) }, onClick = {
                                        log("Loader version selected: ${v.version}")
                                        selectedLoaderVersion = v.version
                                        loaderExpanded = false
                                    })
                                }
                            }
                        }
                        // For Forge, we just display the auto-selected version, so no dropdown items.
                    }
                    Spacer(Modifier.height(16.dp))
                }

                Spacer(Modifier.weight(1f))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Отмена") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val finalVersion = when (buildType) {
                                BuildType.FABRIC -> "$selectedMcVersion-fabric-$selectedLoaderVersion"
                                BuildType.FORGE -> "$selectedMcVersion-forge-$selectedLoaderVersion"
                                else -> selectedMcVersion
                            }
                            log("Creating build with version: $finalVersion")
                            onAdd(name, finalVersion, buildType)
                        },
                        enabled = name.isNotBlank() && selectedMcVersion.isNotBlank() && (buildType == BuildType.VANILLA || selectedLoaderVersion.isNotBlank())
                    ) { Text("Создать") }
                }
            }
        }
    }
}
