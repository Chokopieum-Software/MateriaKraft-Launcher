package ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import funlauncher.MinecraftBuild
import ColorAkcent

@Composable
fun LaunchTab(
    builds: List<MinecraftBuild>,
    selectedBuild: MinecraftBuild?,
    onBuildSelect: (MinecraftBuild) -> Unit,
    offlineUsername: String,
    onUsernameChange: (String) -> Unit,
    launchStatus: String,
    launchProgress: Float,
    isPreparing: Boolean,
    isGameRunning: Boolean,
    onLaunchClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        OutlinedTextField(
            value = offlineUsername,
            onValueChange = onUsernameChange,
            label = { Text("Никнейм") },
            enabled = !isGameRunning && !isPreparing
        )
        Spacer(Modifier.height(16.dp))
        var expanded by remember { mutableStateOf(false) }
        Box {
            Button(
                onClick = { expanded = true },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color.White),
                enabled = !isGameRunning && !isPreparing
            ) {
                Text(selectedBuild?.name ?: "Выберите сборку")
            }
            DropdownMenu(expanded, { expanded = false }) {
                builds.forEach { b ->
                    DropdownMenuItem({
                        onBuildSelect(b)
                        expanded = false
                    }) { Text(b.name) }
                }
            }
        }
        Spacer(Modifier.height(30.dp))
        if (isPreparing) {
            Text(launchStatus, style = MaterialTheme.typography.caption)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = launchProgress,
                modifier = Modifier.width(300.dp).height(10.dp),
                color = ColorAkcent
            )
            Spacer(Modifier.height(4.dp))
            Text("${(launchProgress * 100).toInt()}%")
        } else {
            Text(launchStatus, style = MaterialTheme.typography.caption)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onLaunchClick,
            enabled = !isPreparing,
            modifier = Modifier.size(200.dp, 60.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = if (isGameRunning) Color.Gray else ColorAkcent,
                contentColor = Color.White
            )
        ) {
            Text(
                text = when {
                    isPreparing -> "ЗАГРУЗКА..."
                    isGameRunning -> "ОСТАНОВИТЬ"
                    else -> "ИГРАТЬ"
                },
                style = MaterialTheme.typography.h5
            )
        }
    }
}
