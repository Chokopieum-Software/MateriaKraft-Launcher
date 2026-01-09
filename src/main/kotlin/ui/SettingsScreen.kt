/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import funlauncher.AppSettings
import funlauncher.NavPanelPosition
import funlauncher.Theme
import openUri
import java.net.URI
import java.util.Properties

object AppInfo {
    val version: String
    val buildNumber: String
    val buildSource: String
    val gradleVersion: String
    init {
        val props = Properties()
        props.load(this.javaClass.classLoader.getResourceAsStream("app.properties"))
        version = props.getProperty("version")
        buildNumber = props.getProperty("buildNumber")
        buildSource = props.getProperty("buildSource", "Unknown")
        gradleVersion = props.getProperty("gradleVersion", "N/A")
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
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Materia Launcher",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Version: ${AppInfo.version} (Build ${AppInfo.buildNumber})",
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Source: ${AppInfo.buildSource} | Gradle: ${AppInfo.gradleVersion}",
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        Text(
            text = "Chokopieum Software 2025-2026",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        TextButton(onClick = {
            openUri(URI("https://github.com/Chokopieum-Software/MateriaKraft-Launcher"))
        }) {
            Text("GitHub")
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "OS: ${System.getProperty("os.name")} (${System.getProperty("os.arch")})",
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
        Text(
            text = "НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.",
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
