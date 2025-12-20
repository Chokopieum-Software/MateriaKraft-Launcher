package ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import funlauncher.AppSettings
import funlauncher.JavaInfo
import funlauncher.JavaManager
import funlauncher.MinecraftBuild
import java.lang.management.ManagementFactory
import kotlin.math.roundToInt
import androidx.compose.material3.ExposedDropdownMenuDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildSettingsScreen(
    build: MinecraftBuild,
    globalSettings: AppSettings,
    onDismiss: () -> Unit,
    onSave: (javaPath: String?, maxRam: Int?, javaArgs: String?, envVars: String?) -> Unit
) {
    val javaManager = remember { JavaManager() }
    var javaInstallations by remember { mutableStateOf<List<JavaInfo>>(emptyList()) }

    // null = Наследовать, "" = Автоматически, "path" = конкретный путь
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

    LaunchedEffect(Unit) {
        val installations = javaManager.findJavaInstallations()
        javaInstallations = installations.launcher + installations.system
    }

    Dialog(onDismissRequest = onDismiss) {
        Scaffold(
            topBar = { TopAppBar(title = { Text("Настройки: ${build.name}") }) },
            bottomBar = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = onDismiss) { Text("Отмена") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        onSave(
                            selectedJavaPath,
                            if (useGlobalRam) null else maxRam,
                            if (useGlobalJavaArgs) null else javaArgs,
                            if (useGlobalEnvVars) null else envVars
                        )
                    }) { Text("Сохранить") }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // --- JAVA SELECTION ---
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Версия Java", style = MaterialTheme.typography.titleMedium)
                    JavaSelection(
                        installedJavas = javaInstallations,
                        globalJavaPath = globalSettings.javaPath,
                        selectedPath = selectedJavaPath,
                        onPathSelected = { selectedJavaPath = it }
                    )
                }

                // --- RAM SELECTION ---
                SettingGroup(
                    title = "Выделение ОЗУ",
                    useGlobal = useGlobalRam,
                    onUseGlobalChange = {
                        useGlobalRam = it
                        if (it) maxRam = globalSettings.maxRamMb
                    }
                ) {
                    if (useGlobalRam) {
                        Text(
                            "Используется: ${globalSettings.maxRamMb} МБ (глобальная настройка)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp)
                        )
                    } else {
                        RamSelector(
                            currentRam = maxRam,
                            totalRam = totalSystemMemory.toInt(),
                            onRamChange = { maxRam = it }
                        )
                    }
                }

                // --- JAVA ARGS ---
                SettingGroup(
                    title = "Аргументы Java",
                    useGlobal = useGlobalJavaArgs,
                    onUseGlobalChange = {
                        useGlobalJavaArgs = it
                        if (it) javaArgs = globalSettings.javaArgs
                    }
                ) {
                    OutlinedTextField(
                        value = javaArgs,
                        onValueChange = { javaArgs = it },
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        enabled = !useGlobalJavaArgs,
                        label = { Text("Аргументы Java") }
                    )
                }

                // --- ENV VARS ---
                SettingGroup(
                    title = "Переменные среды",
                    useGlobal = useGlobalEnvVars,
                    onUseGlobalChange = {
                        useGlobalEnvVars = it
                        if (it) envVars = globalSettings.envVars
                    }
                ) {
                    OutlinedTextField(
                        value = envVars,
                        onValueChange = { envVars = it },
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        enabled = !useGlobalEnvVars,
                        label = { Text("Переменные среды (VAR=VAL)") }
                    )
                }
            }
        }
    }
}

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
            modifier = Modifier.menuAnchor().fillMaxWidth()
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
            Divider()
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
