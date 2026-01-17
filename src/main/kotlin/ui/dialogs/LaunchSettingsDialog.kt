/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import funlauncher.AppSettings
import java.lang.management.ManagementFactory
import kotlin.math.roundToInt

@Composable
fun LaunchSettingsDialog(
    currentSettings: AppSettings,
    onDismiss: () -> Unit,
    onSave: (AppSettings) -> Unit
) {
    var maxRam by remember { mutableStateOf(currentSettings.maxRamMb) }
    var javaArgs by remember { mutableStateOf(currentSettings.javaArgs) }
    var envVars by remember { mutableStateOf(currentSettings.envVars) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Глобальные настройки запуска") },
        text = {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                val totalSystemMemory = remember {
                    (ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean).totalMemorySize / (1024 * 1024)
                }
                RamSelector(
                    currentRam = maxRam,
                    totalRam = totalSystemMemory.toInt(),
                    onRamChange = { maxRam = it }
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = javaArgs,
                    onValueChange = { javaArgs = it },
                    label = { Text("Аргументы Java") },
                    modifier = Modifier.fillMaxWidth().height(100.dp)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = envVars,
                    onValueChange = { envVars = it },
                    label = { Text("Переменные среды (VAR=VAL)") },
                    modifier = Modifier.fillMaxWidth().height(100.dp)
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(currentSettings.copy(maxRamMb = maxRam, javaArgs = javaArgs, envVars = envVars))
                onDismiss()
            }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
private fun RamSelector(
    currentRam: Int,
    totalRam: Int,
    onRamChange: (Int) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Выделение ОЗУ: $currentRam MB", modifier = Modifier.weight(1f))
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
