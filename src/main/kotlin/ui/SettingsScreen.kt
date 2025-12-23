/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import funlauncher.AppSettings
import funlauncher.Theme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(
    currentSettings: AppSettings,
    onSave: (AppSettings) -> Unit,
    onOpenJavaManager: () -> Unit
) {
    var theme by remember { mutableStateOf(currentSettings.theme) }
    var showConsole by remember { mutableStateOf(currentSettings.showConsoleOnLaunch) }
    var showLaunchSettingsDialog by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    // Сохраняем простые изменения при изменении
    LaunchedEffect(theme, showConsole) {
        onSave(currentSettings.copy(theme = theme, showConsoleOnLaunch = showConsole))
    }

    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Настройки", style = MaterialTheme.typography.headlineMedium)

            // --- Выбор темы ---
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Внешний вид", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Box {
                        OutlinedTextField(
                            value = theme.name,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Тема") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Theme.values().forEach { themeOption ->
                                DropdownMenuItem(
                                    text = { Text(themeOption.name) },
                                    onClick = {
                                        theme = themeOption
                                        expanded = false
                                    }
                                )
                            }
                        }
                        Box(modifier = Modifier.matchParentSize().clickable { expanded = !expanded })
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
                            checked = showConsole,
                            onCheckedChange = { showConsole = it }
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

        Text(
            text = "НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.",
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
        )
    }


    if (showLaunchSettingsDialog) {
        LaunchSettingsDialog(
            currentSettings = currentSettings,
            onDismiss = { showLaunchSettingsDialog = false },
            onSave = onSave
        )
    }
}
