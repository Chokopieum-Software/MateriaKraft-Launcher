/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import funlauncher.game.MinecraftVersion
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionSelectionDialog(
    versions: List<MinecraftVersion>,
    onDismiss: () -> Unit,
    onVersionSelected: (MinecraftVersion) -> Unit
) {
    var showReleases by remember { mutableStateOf(true) }
    var showSnapshots by remember { mutableStateOf(true) }
    var showBetas by remember { mutableStateOf(false) }
    var showAlphas by remember { mutableStateOf(false) }

    val filteredVersions = versions.filter {
        (showReleases && it.type == "release") ||
        (showSnapshots && it.type == "snapshot") ||
        (showBetas && it.type == "old_beta") ||
        (showAlphas && it.type == "old_alpha")
    }

    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }

    LaunchedEffect(visibleState.currentState) {
        if (!visibleState.currentState && !visibleState.targetState) {
            onDismiss()
        }
    }

    Dialog(onDismissRequest = { visibleState.targetState = false }) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 8 },
            exit = fadeOut(tween(250)) + slideOutVertically(tween(250)) { it / 8 }
        ) {
            Card(modifier = Modifier.fillMaxWidth(0.8f).fillMaxHeight(0.8f)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Выберите версию", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = showReleases, onClick = { showReleases = !showReleases }, label = { Text("Релизы") })
                        FilterChip(selected = showSnapshots, onClick = { showSnapshots = !showSnapshots }, label = { Text("Снапшоты") })
                        FilterChip(selected = showBetas, onClick = { showBetas = !showBetas }, label = { Text("Бета") })
                        FilterChip(selected = showAlphas, onClick = { showAlphas = !showAlphas }, label = { Text("Альфа") })
                    }
                    Spacer(Modifier.height(16.dp))
                    LazyColumn {
                        items(filteredVersions) { version ->
                            ListItem(
                                headlineContent = { Text(version.id) },
                                supportingContent = { Text(formatDate(version.releaseTime)) },
                                modifier = Modifier.clickable { onVersionSelected(version) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatDate(dateTimeString: String): String {
    return try {
        val odt = OffsetDateTime.parse(dateTimeString)
        val formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale("ru"))
        odt.format(formatter)
    } catch (e: Exception) {
        dateTimeString // Fallback to raw string
    }
}
