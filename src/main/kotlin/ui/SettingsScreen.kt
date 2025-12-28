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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    init {
        val props = Properties()
        props.load(this.javaClass.classLoader.getResourceAsStream("app.properties"))
        version = props.getProperty("version")
        buildNumber = props.getProperty("buildNumber")
        buildSource = props.getProperty("buildSource", "Unknown")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(
    currentSettings: AppSettings,
    onSave: (AppSettings) -> Unit,
    onOpenJavaManager: () -> Unit
) {
    var showLaunchSettingsDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Настройки",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // --- Выбор темы ---
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Внешний вид", style = MaterialTheme.typography.titleMedium)
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
                    Spacer(Modifier.height(16.dp))
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

            // --- Настройки запуска ---
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Запуск игры", style = MaterialTheme.typography.titleMedium)
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

        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Chokopieum Software 2025",
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "OS: ${System.getProperty("os.name")} (${System.getProperty("os.arch")})",
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Version: ${AppInfo.version} (Build ${AppInfo.buildNumber}) | Source: ${AppInfo.buildSource}",
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(onClick = {
                openUri(URI("https://github.com/Chokopieum-Software/MateriaKraft-Launcher"))
            }) {
                Text("GitHub")
            }
            Text(
                text = "НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.",
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
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
