/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import funlauncher.AppSettings
import funlauncher.NavPanelPosition
import funlauncher.Theme
import funlauncher.openUri
import java.awt.Desktop
import ui.dialogs.LaunchSettingsDialog
import java.io.File
import java.net.URI
import java.util.Properties

object AppInfo {
    val version: String
    val buildNumber: String
    val buildSource: String
    val gradleVersion: String
    val osInfo: String
    val renderApi: String

    init {
        val props = Properties()
        try {
            this.javaClass.classLoader.getResourceAsStream("app.properties").use { stream ->
                if (stream != null) {
                    props.load(stream)
                }
            }
        } catch (e: Exception) {
            // Файл может отсутствовать в IDE, это нормально
        }
        version = props.getProperty("version", "Dev")
        buildNumber = props.getProperty("buildNumber", "Local")
        buildSource = props.getProperty("buildSource", "IDE")
        gradleVersion = props.getProperty("gradleVersion", "N/A")
        osInfo = retrieveOsInfo()
        renderApi = System.getProperty("skiko.renderApi", "Unknown")
    }

    private fun retrieveOsInfo(): String {
        val osName = System.getProperty("os.name")
        val osArch = System.getProperty("os.arch")
        if (osName.startsWith("Linux")) {
            return try {
                val osReleaseFile = File("/etc/os-release")
                if (osReleaseFile.exists()) {
                    val properties = Properties()
                    properties.load(osReleaseFile.inputStream())
                    val prettyName = properties.getProperty("PRETTY_NAME", osName)
                    "$prettyName ($osArch)"
                } else {
                    "$osName ($osArch)"
                }
            } catch (e: Exception) {
                "$osName ($osArch)"
            }
        }
        return "$osName ($osArch)"
    }
}

private enum class SettingsSection(val title: String, val icon: ImageVector) {
    Appearance("Внешний вид", Icons.Default.Palette),
    Launch("Запуск игры", Icons.Default.PlayArrow),
    About("О программе", Icons.Default.Info)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(
    currentSettings: AppSettings,
    onSave: (AppSettings) -> Unit,
    onOpenJavaManager: () -> Unit
) {
    var currentSection by remember { mutableStateOf(SettingsSection.Appearance) }

    Row(modifier = Modifier.fillMaxSize()) {
        NavigationRail(
            modifier = Modifier.fillMaxHeight().padding(end = 16.dp),
            header = {
                Text(
                    "Настройки",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        ) {
            SettingsSection.values().forEach { section ->
                NavigationRailItem(
                    selected = currentSection == section,
                    onClick = { currentSection = section },
                    icon = { Icon(section.icon, contentDescription = section.title) },
                    label = { Text(section.title) },
                    alwaysShowLabel = false
                )
            }
        }

        Divider(modifier = Modifier.fillMaxHeight().width(1.dp))

        Box(modifier = Modifier.weight(1f).padding(16.dp)) {
            when (currentSection) {
                SettingsSection.Appearance -> AppearanceSettings(currentSettings, onSave)
                SettingsSection.Launch -> LaunchSettings(currentSettings, onSave, onOpenJavaManager)
                SettingsSection.About -> AboutScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSettings(
    currentSettings: AppSettings,
    onSave: (AppSettings) -> Unit
) {
    var showRestartDialog by remember { mutableStateOf(false) }
    val languages = remember { mapOf("ru" to "Русский", "en" to "English") }

    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = { Text("Перезапуск требуется") },
            text = { Text("Для применения нового языка необходимо перезапустить приложение.") },
            confirmButton = {
                Button(onClick = { showRestartDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Тема", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                val themeOptions = Theme.values().map { it.name }
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    themeOptions.forEachIndexed { index, label ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = themeOptions.size),
                            onClick = { onSave(currentSettings.copy(theme = Theme.values()[index])) },
                            selected = currentSettings.theme.name == label
                        ) {
                            Text(label)
                        }
                    }
                }
            }
        }
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Положение панели навигации", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                val navPanelOptions = NavPanelPosition.values().map { it.name }
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    navPanelOptions.forEachIndexed { index, label ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = navPanelOptions.size),
                            onClick = { onSave(currentSettings.copy(navPanelPosition = NavPanelPosition.values()[index])) },
                            selected = currentSettings.navPanelPosition.name == label
                        ) {
                            Text(label)
                        }
                    }
                }
            }
        }
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Язык (требуется перезапуск)", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                var expanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = languages[currentSettings.language] ?: currentSettings.language,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        languages.forEach { (code, name) ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    if (currentSettings.language != code) {
                                        onSave(currentSettings.copy(language = code))
                                        showRestartDialog = true
                                    }
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LaunchSettings(
    currentSettings: AppSettings,
    onSave: (AppSettings) -> Unit,
    onOpenJavaManager: () -> Unit
) {
    var showLaunchSettingsDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Запускать консоль при запуске игры", modifier = Modifier.weight(1f))
                    Switch(
                        checked = currentSettings.showConsoleOnLaunch,
                        onCheckedChange = { onSave(currentSettings.copy(showConsoleOnLaunch = it)) }
                    )
                }
                Divider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = onOpenJavaManager, modifier = Modifier.weight(1f)) {
                        Text("Управление Java")
                    }
                    Button(onClick = { showLaunchSettingsDialog = true }, modifier = Modifier.weight(1f)) {
                        Text("Глобальные настройки запуска")
                    }
                }
            }
        }
    }

    if (showLaunchSettingsDialog) {
        LaunchSettingsDialog(
            currentSettings = currentSettings,
            onDismiss = { showLaunchSettingsDialog = false },
            onSave = onSave
        )
    }
}

@Composable
private fun AboutScreen() {
    val asciiArt = """
   /'\_/`\          /\ \__                __             /\ \                              /\ \                     
/\      \     __  \ \ ,_\    __   _ __ /\_\     __     \ \ \         __     __  __    ___\ \ \___      __   _ __  
\ \ \__\ \  /'__`\ \ \ \/  /'__`\/\`'__\/\ \  /'__`\    \ \ \  __  /'__`\  /\ \/\ \  /'___\ \  _ `\  /'__`\/\`'__\
 \ \ \_/\ \/\ \L\.\_\ \ \_/\  __/\ \ \/ \ \ \/\ \L\.\_   \ \ \L\ \/\ \L\.\_\ \ \_\ \/\ \__/\ \ \ \ \/\  __/\ \ \/ 
  \ \_\\ \_\ \__/.\_\\ \__\ \____\\ \_\  \ \_\ \__/.\_\   \ \____/\ \__/.\_\\ \____/\ \____\\ \_\ \_\ \____\\ \_\ 
   \/_/ \/_/\/__/\/_/ \/__/\/____/ \/_/   \/_/\/__/\/_/    \/___/  \/__/\/_/ \/___/  \/____/ \/_/\/_/\/____/ \/_/ 
""".trimIndent()

    val versionText = when (AppInfo.version) {
        "Beta", "Canary", "Develop Build", "Community Build" -> "${AppInfo.version} (Build ${AppInfo.buildNumber})"
        else -> AppInfo.version // Для тегов (релизов) показываем только версию
    }

    val infoItems = mapOf(
        "Version" to versionText,
        "Source" to AppInfo.buildSource,
        "Gradle" to AppInfo.gradleVersion,
        "OS" to AppInfo.osInfo,
        "Render API" to AppInfo.renderApi,
        "Author" to "Chokopieum Software 2025-2026",
        "GitHub" to "https://github.com/Chokopieum-Software/MateriaKraft-Launcher"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF1E1E1E), // Dark console background
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            val promptColor = Color(0xFF6A9FB5)
            val commandColor = Color(0xFFC586C0)
            val textColor = Color(0xFFCE9178)
            val keyColor = Color(0xFF9CDCFE)

            Text(
                text = buildAnnotatedString {
                    withStyle(style = SpanStyle(color = promptColor)) {
                        append("user@materiakraft")
                    }
                    withStyle(style = SpanStyle(color = textColor)) {
                        append(":~$ ")
                    }
                    withStyle(style = SpanStyle(color = commandColor)) {
                        append("materia --info")
                    }
                },
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = asciiArt,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            infoItems.forEach { (key, value) ->
                Row {
                    Text(
                        text = "${key.padEnd(10)}: ",
                        fontFamily = FontFamily.Monospace,
                        color = keyColor,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (key == "GitHub") {
                        TextButton(onClick = { openUri(URI(value)) }, contentPadding = PaddingValues(0.dp)) {
                            Text(
                                text = value,
                                fontFamily = FontFamily.Monospace,
                                color = textColor,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    } else {
                        Text(
                            text = value,
                            fontFamily = FontFamily.Monospace,
                            color = textColor,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "LEGAL: NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.",
                fontFamily = FontFamily.Monospace,
                color = Color.Gray,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
