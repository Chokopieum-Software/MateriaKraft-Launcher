package ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import funlauncher.AppSettings

@Composable
fun SettingsTab(
    currentSettings: AppSettings,
    onSave: (AppSettings) -> Unit,
    onOpenJavaManager: () -> Unit
) {
    var ramValue by remember { mutableStateOf(currentSettings.maxRamMb.toString()) }
    var javaArgs by remember { mutableStateOf(currentSettings.javaArgs) }
    var envVars by remember { mutableStateOf(currentSettings.envVars) }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Text("Настройки", style = MaterialTheme.typography.h5)
        Spacer(Modifier.height(16.dp))
        Text("ОЗУ (МБ):")
        OutlinedTextField(
            value = ramValue,
            onValueChange = { ramValue = it.filter(Char::isDigit) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text("Аргументы Java:")
        OutlinedTextField(
            value = javaArgs,
            onValueChange = { javaArgs = it },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text("Переменные среды (VAR=VAL):")
        OutlinedTextField(
            value = envVars,
            onValueChange = { envVars = it },
            modifier = Modifier.fillMaxWidth().height(100.dp)
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                val ram = ramValue.toIntOrNull() ?: 2048
                onSave(currentSettings.copy(maxRamMb = ram, javaArgs = javaArgs, envVars = envVars))
            },
            modifier = Modifier.align(Alignment.End)
        ) { Text("Сохранить") }
        Spacer(Modifier.weight(1f))
        Button(onClick = onOpenJavaManager, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Управление Java")
        }
    }
}
