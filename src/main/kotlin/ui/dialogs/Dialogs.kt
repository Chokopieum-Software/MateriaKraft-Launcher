package ui.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import funlauncher.MinecraftBuild
import funlauncher.managers.BuildManager
import funlauncher.managers.PathManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ConfirmDeleteDialog(
    build: MinecraftBuild,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Подтверждение") },
        text = { Text("Вы уверены, что хотите удалить сборку '${build.name}'?") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) { Text("Удалить") }
        },
        dismissButton = { Button(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
fun RamWarningDialog(
    build: MinecraftBuild,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Предупреждение") },
        text = { Text("Вы выделили больше ОЗУ, чем доступно в системе. Это может привести к проблемам с производительностью или запуском. Продолжить?") },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Все равно запустить") }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
fun ErrorDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ошибка") },
        text = { Text(message) },
        confirmButton = { Button(onClick = onDismiss) { Text("OK") } }
    )
}
