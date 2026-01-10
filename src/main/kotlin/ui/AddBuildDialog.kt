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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBuildDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, BuildType, String?) -> Unit,
    pathManager: PathManager
) {
    var name by remember { mutableStateOf("") }
    var selectedMcVersion by remember { mutableStateOf<MinecraftVersion?>(null) }
    var selectedLoaderVersion by remember { mutableStateOf("") }
    var modLoader by remember { mutableStateOf(BuildType.VANILLA) }

    var showVersionSelectionDialog by remember { mutableStateOf(false) }

    var vanillaVersions by remember { mutableStateOf<List<MinecraftVersion>>(emptyList()) }
    var fabricLoaderVersions by remember { mutableStateOf<List<FabricVersion>>(emptyList()) }
    var forgeVersions by remember { mutableStateOf<List<ForgeVersion>>(emptyList()) }
    var applicableForgeVersions by remember { mutableStateOf<List<ForgeVersion>>(emptyList()) }
    var quiltLoaderVersions by remember { mutableStateOf<List<QuiltVersion>>(emptyList()) }
    var neoForgeVersions by remember { mutableStateOf<List<NeoForgeVersion>>(emptyList()) }
    var applicableNeoForgeVersions by remember { mutableStateOf<List<NeoForgeVersion>>(emptyList()) }

    var isLoading by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val versionManager = remember { VersionManager(pathManager) }

    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }

    LaunchedEffect(visibleState.currentState) {
        if (!visibleState.currentState && !visibleState.targetState) {
            onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        isLoading = true
        scope.launch {
            vanillaVersions = versionManager.getMinecraftVersions()
            forgeVersions = versionManager.getForgeVersions()
            neoForgeVersions = versionManager.getNeoForgeVersions()
            isLoading = false
        }
    }

    LaunchedEffect(selectedMcVersion, modLoader) {
        selectedLoaderVersion = ""
        selectedMcVersion?.let { mcVersion ->
            when (modLoader) {
                BuildType.FABRIC -> {
                    isLoading = true
                    fabricLoaderVersions = emptyList()
                    scope.launch {
                        fabricLoaderVersions = versionManager.getFabricLoaderVersions(mcVersion.id)
                        selectedLoaderVersion = fabricLoaderVersions.firstOrNull { it.stable }?.version ?: fabricLoaderVersions.firstOrNull()?.version ?: ""
                        isLoading = false
                    }
                }
                BuildType.FORGE -> {
                    applicableForgeVersions = forgeVersions.filter { it.mcVersion == mcVersion.id }
                    val recommended = applicableForgeVersions.find { it.isRecommended }
                    val latest = applicableForgeVersions.find { it.isLatest }
                    selectedLoaderVersion = recommended?.forgeVersion ?: latest?.forgeVersion ?: applicableForgeVersions.firstOrNull()?.forgeVersion ?: ""
                }
                BuildType.QUILT -> {
                    isLoading = true
                    quiltLoaderVersions = emptyList()
                    scope.launch {
                        quiltLoaderVersions = versionManager.getQuiltLoaderVersions(mcVersion.id)
                        selectedLoaderVersion = quiltLoaderVersions.firstOrNull()?.version ?: ""
                        isLoading = false
                    }
                }
                BuildType.NEOFORGE -> {
                    applicableNeoForgeVersions = neoForgeVersions.filter { it.mcVersion == mcVersion.id }
                    selectedLoaderVersion = applicableNeoForgeVersions.firstOrNull()?.neoForgeVersion ?: ""
                }
                else -> {
                    // No loader for Vanilla
                }
            }
        }
    }

    if (showVersionSelectionDialog) {
        VersionSelectionDialog(
            versions = vanillaVersions,
            onDismiss = { showVersionSelectionDialog = false },
            onVersionSelected = {
                selectedMcVersion = it
                showVersionSelectionDialog = false
            }
        )
    }

    Dialog(onDismissRequest = { visibleState.targetState = false }) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 8 },
            exit = fadeOut(tween(250)) + slideOutVertically(tween(250)) { it / 8 }
        ) {
            Card(Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.9f)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Новая сборка", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Название") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = "",
                        onValueChange = {},
                        label = { Text("Добавить в группу (заглушка)") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false
                    )
                    Spacer(Modifier.height(16.dp))

                    Button(onClick = { showVersionSelectionDialog = true }) {
                        Text(selectedMcVersion?.id ?: "Выберите версию")
                    }
                    Spacer(Modifier.height(16.dp))

                    AnimatedVisibility(visible = selectedMcVersion != null) {
                        Column {
                            Text("Загрузчик модов", style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                                val types = BuildType.entries
                                types.forEachIndexed { index, type ->
                                    val shape = when (index) {
                                        0 -> RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)
                                        types.lastIndex -> RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
                                        else -> RoundedCornerShape(0.dp)
                                    }
                                    val isSelected = modLoader == type
                                    OutlinedButton(
                                        onClick = { modLoader = type },
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

                            if (modLoader != BuildType.VANILLA) {
                                var loaderExpanded by remember { mutableStateOf(false) }
                                ExposedDropdownMenuBox(expanded = loaderExpanded, onExpandedChange = { loaderExpanded = !loaderExpanded }) {
                                    OutlinedTextField(
                                        value = if (selectedMcVersion == null) "Сначала выберите версию" else selectedLoaderVersion.ifEmpty { "Загрузка..." },
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Версия загрузчика") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = loaderExpanded) },
                                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                                        enabled = selectedMcVersion != null
                                    )
                                    ExposedDropdownMenu(expanded = loaderExpanded, onDismissRequest = { loaderExpanded = false }) {
                                        if (isLoading) {
                                            DropdownMenuItem(text = { Text("Загрузка...") }, onClick = {}, enabled = false)
                                        } else {
                                            when (modLoader) {
                                                BuildType.FABRIC -> {
                                                    fabricLoaderVersions.forEach { v ->
                                                        DropdownMenuItem(text = { Text(v.version) }, onClick = {
                                                            selectedLoaderVersion = v.version
                                                            loaderExpanded = false
                                                        })
                                                    }
                                                }
                                                BuildType.FORGE -> {
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
                                                                selectedLoaderVersion = v.forgeVersion
                                                                loaderExpanded = false
                                                            }
                                                        )
                                                    }
                                                }
                                                BuildType.QUILT -> {
                                                    quiltLoaderVersions.forEach { v ->
                                                        DropdownMenuItem(text = { Text(v.version) }, onClick = {
                                                            selectedLoaderVersion = v.version
                                                            loaderExpanded = false
                                                        })
                                                    }
                                                }
                                                BuildType.NEOFORGE -> {
                                                    applicableNeoForgeVersions.forEach { v ->
                                                        DropdownMenuItem(
                                                            text = { Text(v.neoForgeVersion) },
                                                            onClick = {
                                                                selectedLoaderVersion = v.neoForgeVersion
                                                                loaderExpanded = false
                                                            }
                                                        )
                                                    }
                                                }
                                                else -> {}
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { visibleState.targetState = false }) { Text("Отмена") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val finalVersion = when (modLoader) {
                                    BuildType.FABRIC -> "${selectedMcVersion!!.id}-fabric-$selectedLoaderVersion"
                                    BuildType.FORGE -> "${selectedMcVersion!!.id}-forge-$selectedLoaderVersion"
                                    BuildType.QUILT -> "${selectedMcVersion!!.id}-quilt-$selectedLoaderVersion"
                                    BuildType.NEOFORGE -> "${selectedMcVersion!!.id}-neoforge-$selectedLoaderVersion"
                                    else -> selectedMcVersion!!.id
                                }
                                val backgroundsDir = File("src/main/resources/backgrounds")
                                val randomImage = if (backgroundsDir.exists() && backgroundsDir.isDirectory) {
                                    backgroundsDir.listFiles()?.randomOrNull()?.absolutePath
                                } else {
                                    null
                                }
                                onAdd(name, finalVersion, modLoader, randomImage)
                                visibleState.targetState = false
                            },
                            enabled = name.isNotBlank() && selectedMcVersion != null && (modLoader == BuildType.VANILLA || selectedLoaderVersion.isNotBlank())
                        ) { Text("Создать") }
                    }
                }
            }
        }
    }
}
