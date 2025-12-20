package ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import funlauncher.MinecraftBuild


val ColorAkcent = Color(0xCDFF0000)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    builds: List<MinecraftBuild>,
    runningBuild: MinecraftBuild?,
    onLaunchClick: (MinecraftBuild) -> Unit,
    onOpenFolderClick: (MinecraftBuild) -> Unit,
    onAddBuildClick: () -> Unit,
    isLaunchingBuildId: String?,
    onDeleteBuildClick: (MinecraftBuild) -> Unit,
    onSettingsBuildClick: (MinecraftBuild) -> Unit
) {
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddBuildClick) {
                Icon(Icons.Default.Add, contentDescription = "Добавить сборку")
            }
        }
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 180.dp),
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(builds) { build ->
                BuildCard(
                    build = build,
                    isRunning = build == runningBuild,
                    isPreparing = build.name == isLaunchingBuildId,
                    onLaunchClick = { onLaunchClick(build) },
                    onOpenFolderClick = { onOpenFolderClick(build) },
                    onDeleteClick = { onDeleteBuildClick(build) },
                    onSettingsClick = { onSettingsBuildClick(build) }
                )
            }
        }
    }
}

@Composable
private fun BuildCard(
    build: MinecraftBuild,
    isRunning: Boolean,
    isPreparing: Boolean,
    onLaunchClick: () -> Unit,
    onOpenFolderClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = build.name,
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${build.type} ${build.version}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Дополнительно")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Настройки") },
                                onClick = {
                                    onSettingsClick()
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Удалить") },
                                onClick = {
                                    onDeleteClick()
                                    showMenu = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onLaunchClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when {
                                isPreparing -> Color.Gray
                                isRunning -> Color.Red
                                else -> Color(0xFF4CAF50)
                            }
                        ),
                        modifier = Modifier.weight(1f),
                        enabled = !isPreparing,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (isPreparing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Text("ЗАПУСК...", style = MaterialTheme.typography.labelLarge)
                            } else {
                                Icon(
                                    if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    if (isRunning) "СТОП" else "ИГРАТЬ",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = onOpenFolderClick,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = "Открыть папку")
                    }
                }
            }
        }
    }
}
