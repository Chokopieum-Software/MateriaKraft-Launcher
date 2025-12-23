/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package ui

import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.AwtWindow
import androidx.compose.ui.window.Dialog
import funlauncher.*
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.toComposeImageBitmap

private fun log(message: String) {
    println("[BuildSettingsScreen] $message")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildSettingsScreen(
    build: MinecraftBuild,
    globalSettings: AppSettings,
    onDismiss: () -> Unit,
    onSave: (
        newName: String,
        newVersion: String,
        newType: BuildType,
        newImagePath: String?,
        javaPath: String?,
        maxRam: Int?,
        javaArgs: String?,
        envVars: String?
    ) -> Unit
) {
    log("Composing BuildSettingsScreen for build: ${build.name}")

    val scope = rememberCoroutineScope()
    val javaManager = remember { JavaManager() }
    val versionManager = remember { VersionManager() }
    val buildManager = remember { BuildManager() }

    var javaInstallations by remember { mutableStateOf<List<JavaInfo>>(emptyList()) }
    var allMcVersions by remember { mutableStateOf<List<MinecraftVersion>>(emptyList()) }
    var fabricGameVersions by remember { mutableStateOf<List<FabricGameVersion>>(emptyList()) }
    var fabricLoaderVersions by remember { mutableStateOf<List<FabricVersion>>(emptyList()) }
    var forgeVersions by remember { mutableStateOf<List<ForgeVersion>>(emptyList()) }
    var isLoadingMcVersions by remember { mutableStateOf(false) }
    var isLoadingFabricVersions by remember { mutableStateOf(false) }

    // --- Build State ---
    var buildName by remember { mutableStateOf(build.name) }
    val (initialMcVersion, initialLoaderVersion) = remember(build.version, build.type) {
        parseBuildVersion(build.version, build.type)
    }
    var selectedMcVersion by remember { mutableStateOf(initialMcVersion) }
    var selectedLoaderVersion by remember { mutableStateOf(initialLoaderVersion) }
    var selectedBuildType by remember { mutableStateOf(build.type) }
    var imagePath by remember { mutableStateOf(build.imagePath) }

    // --- Settings State ---
    var selectedJavaPath by remember { mutableStateOf(build.javaPath) }
    var useGlobalRam by remember { mutableStateOf(build.maxRamMb == null) }
    var useGlobalJavaArgs by remember { mutableStateOf(build.javaArgs == null) }
    var useGlobalEnvVars by remember { mutableStateOf(build.envVars == null) }
    var maxRam by remember { mutableStateOf(build.maxRamMb ?: globalSettings.maxRamMb) }
    var javaArgs by remember { mutableStateOf(build.javaArgs ?: globalSettings.javaArgs) }
    var envVars by remember { mutableStateOf(build.envVars ?: globalSettings.envVars) }

    val totalSystemMemory = remember {
        (ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean).totalMemorySize / (1024 * 1024)
    }

    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }

    val displayedMcVersions = remember(selectedBuildType, allMcVersions, fabricGameVersions, forgeVersions) {
        log("Recalculating displayed MC versions. Build type: $selectedBuildType")
        when (selectedBuildType) {
            BuildType.FABRIC -> {
                val supportedIds = fabricGameVersions.map { it.version }.toSet()
                allMcVersions.filter { it.id in supportedIds }
            }
            BuildType.FORGE -> {
                val supportedIds = forgeVersions.map { it.mcVersion }.toSet()
                allMcVersions.filter { it.id in supportedIds }
            }
            else -> {
                allMcVersions
            }
        }
    }

    fun refreshAllVersions(force: Boolean = false) {
        log("Queueing refresh of all versions (force=$force)")
        scope.launch {
            isLoadingMcVersions = true
            log("Starting network fetch for all versions.")
            val mcVersionsJob = launch { allMcVersions = versionManager.getMinecraftVersions(force).filter { it.type == "release" } }
            val fabricGameVersionsJob = launch { fabricGameVersions = versionManager.getFabricGameVersions(force) }
            val forgeVersionsJob = launch { forgeVersions = versionManager.getForgeVersions(force) }
            joinAll(mcVersionsJob, fabricGameVersionsJob, forgeVersionsJob)
            log("Finished network fetch.")
            isLoadingMcVersions = false
        }
    }

    fun refreshFabricLoaderVersions(force: Boolean = false) {
        if (selectedMcVersion.isBlank()) return
        log("Queueing refresh of Fabric loader versions for $selectedMcVersion (force=$force)")
        scope.launch {
            isLoadingFabricVersions = true
            fabricLoaderVersions = versionManager.getFabricLoaderVersions(selectedMcVersion, force)
            if (selectedLoaderVersion.isBlank() || fabricLoaderVersions.none { it.version == selectedLoaderVersion }) {
                selectedLoaderVersion = fabricLoaderVersions.firstOrNull { it.stable }?.version ?: fabricLoaderVersions.firstOrNull()?.version ?: ""
            }
            isLoadingFabricVersions = false
        }
    }

    LaunchedEffect(Unit) {
        log("Initial LaunchedEffect running.")
        javaInstallations = javaManager.findJavaInstallations().let { it.launcher + it.system }
        refreshAllVersions()
    }

    LaunchedEffect(selectedMcVersion, selectedBuildType) {
        log("Effect for selectedMcVersion/BuildType: $selectedMcVersion, $selectedBuildType")
        selectedLoaderVersion = "" // Reset on change
        when (selectedBuildType) {
            BuildType.FABRIC -> if (selectedMcVersion.isNotBlank()) refreshFabricLoaderVersions()
            BuildType.FORGE -> {
                val forgeInfo = forgeVersions.find { it.mcVersion == selectedMcVersion && it.isRecommended }
                    ?: forgeVersions.find { it.mcVersion == selectedMcVersion }
                selectedLoaderVersion = forgeInfo?.forgeVersion ?: ""
            }
            else -> { /* No loader needed */ }
        }
    }

    LaunchedEffect(displayedMcVersions) {
        log("Effect for displayedMcVersions. Count: ${displayedMcVersions.size}")
        if (displayedMcVersions.isNotEmpty() && !displayedMcVersions.any { it.id == selectedMcVersion }) {
            selectedMcVersion = displayedMcVersions.first().id
        }
    }

    val dismiss = { visibleState.targetState = false }
    LaunchedEffect(visibleState.currentState) {
        if (!visibleState.currentState && !visibleState.targetState) onDismiss()
    }

    Dialog(onDismissRequest = dismiss) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 8 },
            exit = fadeOut(tween(250)) + slideOutVertically(tween(250)) { it / 8 }
        ) {
            Scaffold(
                topBar = { TopAppBar(title = { Text("Настройки: ${build.name}") }) },
                bottomBar = {
                    Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.End) {
                        Button(onClick = dismiss) { Text("Отмена") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            val finalVersion = when (selectedBuildType) {
                                BuildType.FABRIC -> "$selectedMcVersion-fabric-$selectedLoaderVersion"
                                BuildType.FORGE -> "$selectedMcVersion-forge-$selectedLoaderVersion"
                                else -> selectedMcVersion
                            }
                            log("Saving build with version: $finalVersion")
                            onSave(
                                buildName,
                                finalVersion,
                                selectedBuildType,
                                imagePath,
                                selectedJavaPath,
                                if (useGlobalRam) null else maxRam,
                                if (useGlobalJavaArgs) null else javaArgs,
                                if (useGlobalEnvVars) null else envVars
                            )
                            dismiss()
                        }) { Text("Сохранить") }
                    }
                }
            ) { paddingValues ->
                Column(
                    Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Spacer(Modifier.height(0.dp))

                    ImageSelectionGroup(
                        imagePath = imagePath,
                        onImagePathChange = { selectedFile ->
                            val coversDir = buildManager.getLauncherDataPath().resolve("covers")
                            Files.createDirectories(coversDir)
                            val newPath = coversDir.resolve(selectedFile.name)
                            Files.copy(selectedFile.toPath(), newPath, StandardCopyOption.REPLACE_EXISTING)
                            imagePath = newPath.toString()
                        }
                    )

                    OutlinedTextField(
                        value = buildName,
                        onValueChange = { buildName = it },
                        label = { Text("Название установки") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    VersionSelectionGroup(
                        mcVersions = displayedMcVersions,
                        fabricVersions = fabricLoaderVersions,
                        selectedMcVersion = selectedMcVersion,
                        onMcVersionSelected = { selectedMcVersion = it },
                        selectedLoaderVersion = selectedLoaderVersion,
                        onLoaderVersionSelected = { selectedLoaderVersion = it },
                        buildType = selectedBuildType,
                        onBuildTypeSelected = {
                            if (it != selectedBuildType) {
                                log("Build type changed to $it")
                                selectedBuildType = it
                            }
                        },
                        isLoadingMc = isLoadingMcVersions,
                        isLoadingFabric = isLoadingFabricVersions,
                        onRefreshMc = { refreshAllVersions(true) },
                        onRefreshFabric = { refreshFabricLoaderVersions(true) }
                    )

                    JavaSelectionGroup(
                        javaInstallations = javaInstallations,
                        globalJavaPath = globalSettings.javaPath,
                        selectedPath = selectedJavaPath,
                        onPathSelected = { selectedJavaPath = it }
                    )

                    SettingGroup("Выделение ОЗУ", useGlobalRam, { useGlobalRam = it; if (it) maxRam = globalSettings.maxRamMb }) {
                        if (useGlobalRam) {
                            Text("Используется: ${globalSettings.maxRamMb} МБ (глобальная настройка)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp))
                        } else {
                            RamSelector(maxRam, totalSystemMemory.toInt()) { maxRam = it }
                        }
                    }

                    SettingGroup("Аргументы Java", useGlobalJavaArgs, { useGlobalJavaArgs = it; if (it) javaArgs = globalSettings.javaArgs }) {
                        OutlinedTextField(javaArgs, { javaArgs = it }, Modifier.fillMaxWidth().height(100.dp), !useGlobalJavaArgs, label = { Text("Аргументы Java") })
                    }

                    SettingGroup("Переменные среды", useGlobalEnvVars, { useGlobalEnvVars = it; if (it) envVars = globalSettings.envVars }) {
                        OutlinedTextField(envVars, { envVars = it }, Modifier.fillMaxWidth().height(100.dp), !useGlobalEnvVars, label = { Text("Переменные среды (VAR=VAL)") })
                    }
                    Spacer(Modifier.height(0.dp))
                }
            }
        }
    }
}

@Composable
private fun ImageSelectionGroup(
    imagePath: String?,
    onImagePathChange: (File) -> Unit
) {
    var showFileDialog by remember { mutableStateOf(false) }

    if (showFileDialog) {
        AwtWindow(
            create = {
                object : FileDialog(null as Frame?, "Выберите обложку", LOAD) {
                    override fun setVisible(value: Boolean) {
                        super.setVisible(value)
                        if (value) {
                            files.firstOrNull()?.let(onImagePathChange)
                        }
                        showFileDialog = false
                    }
                }
            },
            dispose = { it.dispose() }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Обложка сборки", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                modifier = Modifier
                    .size(128.dp, 80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                val image by remember(imagePath) {
                    derivedStateOf {
                        imagePath?.let { path ->
                            val file = File(path)
                            if (file.exists()) {
                                runCatching { BitmapPainter(org.jetbrains.skia.Image.makeFromEncoded(file.readBytes()).toComposeImageBitmap()) }.getOrNull()
                            } else null
                        }
                    }
                }

                if (image != null) {
                    Image(
                        painter = image!!,
                        contentDescription = "Превью обложки",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Default.BrokenImage, contentDescription = "Нет изображения", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button(onClick = { showFileDialog = true }) {
                Icon(Icons.Default.Image, contentDescription = "Выбрать изображение", modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Выбрать...")
            }
        }
    }
}


@Composable
private fun VersionSelectionGroup(
    mcVersions: List<MinecraftVersion>,
    fabricVersions: List<FabricVersion>,
    selectedMcVersion: String,
    onMcVersionSelected: (String) -> Unit,
    selectedLoaderVersion: String,
    onLoaderVersionSelected: (String) -> Unit,
    buildType: BuildType,
    onBuildTypeSelected: (BuildType) -> Unit,
    isLoadingMc: Boolean,
    isLoadingFabric: Boolean,
    onRefreshMc: () -> Unit,
    onRefreshFabric: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Версия игры", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (isLoadingMc) {
                CircularProgressIndicator(Modifier.size(24.dp))
            } else {
                IconButton(onClick = onRefreshMc) { Icon(Icons.Default.Refresh, "Обновить версии Minecraft") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                Dropdown(
                    label = "Minecraft",
                    items = mcVersions.map { it.id },
                    selected = selectedMcVersion,
                    onSelected = onMcVersionSelected,
                    enabled = mcVersions.isNotEmpty()
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                Dropdown(
                    label = "Тип",
                    items = BuildType.entries.map { it.name },
                    selected = buildType.name,
                    onSelected = { onBuildTypeSelected(BuildType.valueOf(it)) }
                )
            }
        }
        if (buildType == BuildType.FABRIC) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    Dropdown(
                        label = "Fabric Loader",
                        items = fabricVersions.map { it.version },
                        selected = selectedLoaderVersion,
                        onSelected = onLoaderVersionSelected,
                        enabled = fabricVersions.isNotEmpty()
                    )
                }
                if (isLoadingFabric) {
                    CircularProgressIndicator(Modifier.size(24.dp).padding(start = 8.dp))
                } else {
                    IconButton(onClick = onRefreshFabric, enabled = selectedMcVersion.isNotBlank()) { Icon(Icons.Default.Refresh, "Обновить версии Fabric") }
                }
            }
        }
    }
}

@Composable
private fun JavaSelectionGroup(
    javaInstallations: List<JavaInfo>,
    globalJavaPath: String,
    selectedPath: String?,
    onPathSelected: (String?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Версия Java", style = MaterialTheme.typography.titleMedium)
        JavaSelection(
            installedJavas = javaInstallations,
            globalJavaPath = globalJavaPath,
            selectedPath = selectedPath,
            onPathSelected = onPathSelected
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dropdown(label: String, items: List<String>, selected: String, onSelected: (String) -> Unit, enabled: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { if (enabled) expanded = !expanded }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            items.forEach { item ->
                DropdownMenuItem(text = { Text(item) }, onClick = { onSelected(item); expanded = false })
            }
        }
    }
}

private fun parseBuildVersion(version: String, type: BuildType): Pair<String, String> {
    return when (type) {
        BuildType.FABRIC -> {
            val parts = version.split("-fabric-")
            parts.getOrElse(0) { version } to parts.getOrElse(1) { "" }
        }
        BuildType.FORGE -> {
            val parts = version.split("-forge-")
            parts.getOrElse(0) { version } to parts.getOrElse(1) { "" }
        }
        else -> version to ""
    }
}

// Keep other composables like SettingGroup, JavaSelection, RamSelector the same
@Composable
private fun SettingGroup(
    title: String,
    useGlobal: Boolean,
    onUseGlobalChange: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Наследовать", style = MaterialTheme.typography.labelMedium)
                Checkbox(checked = useGlobal, onCheckedChange = onUseGlobalChange)
            }
        }
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JavaSelection(
    installedJavas: List<JavaInfo>,
    globalJavaPath: String,
    selectedPath: String?,
    onPathSelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val globalJavaName = if (globalJavaPath.isNotBlank()) {
        installedJavas.find { it.path == globalJavaPath }?.displayName ?: "Путь не найден"
    } else {
        "Авто"
    }
    val inheritText = "Наследовать ($globalJavaName)"
    val automaticText = "Автоматически (Рекомендуется)"

    val currentDisplayName = when (selectedPath) {
        null -> inheritText
        "" -> automaticText
        else -> installedJavas.find { it.path == selectedPath }?.displayName ?: "Путь не найден"
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = currentDisplayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Версия Java") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(inheritText) },
                onClick = {
                    onPathSelected(null)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text(automaticText) },
                onClick = {
                    onPathSelected("")
                    expanded = false
                }
            )
            HorizontalDivider()
            installedJavas.forEach { javaInfo ->
                DropdownMenuItem(
                    text = { Text(javaInfo.displayName) },
                    onClick = {
                        onPathSelected(javaInfo.path)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun RamSelector(
    currentRam: Int,
    totalRam: Int,
    onRamChange: (Int) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ОЗУ: $currentRam MB", modifier = Modifier.weight(1f))
            OutlinedTextField(
                value = currentRam.toString(),
                onValueChange = { onRamChange(it.toIntOrNull() ?: 0) },
                modifier = Modifier.width(100.dp),
                singleLine = true
            )
        }
        Slider(
            value = currentRam.toFloat(),
            onValueChange = { onRamChange(it.roundToInt()) },
            valueRange = 1024f..totalRam.toFloat(),
            steps = (totalRam / 512) - 2,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
