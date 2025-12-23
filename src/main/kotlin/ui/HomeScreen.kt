/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import funlauncher.MinecraftBuild
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 220.dp),
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

@OptIn(ExperimentalFoundationApi::class)
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
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    // Состояние для хранения изображения
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    // Логика асинхронной загрузки изображения
    LaunchedEffect(build.imagePath) {
        if (build.imagePath != null) {
            // Переходим в IO поток для работы с файлами
            withContext(Dispatchers.IO) {
                val file = File(build.imagePath)
                if (file.exists()) {
                    try {
                        file.inputStream().use { stream ->
                            imageBitmap = loadImageBitmap(stream)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        imageBitmap = null
                    }
                } else {
                    imageBitmap = null
                }
            }
        } else {
            imageBitmap = null
        }
    }

    Card(
        modifier = Modifier
            .hoverable(interactionSource)
            .fillMaxWidth()
            .aspectRatio(16 / 10f)
            .border(1.dp, Color.Black.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp, hoveredElevation = 8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // --- ФОН ---
            Box(modifier = Modifier.fillMaxSize()) {
                if (imageBitmap != null) {
                    // Отображаем картинку
                    Image(
                        bitmap = imageBitmap!!,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Затемнение (Overlay), чтобы белый текст читался на любой картинке
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f))
                    )
                } else {
                    // Заглушка (Градиент)
                    Box(modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color(0xFF606060), Color(0xFF303030)))
                    ))
                }
            }

            // --- КОНТЕНТ ---
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // --- ВЕРХНЯЯ ЧАСТЬ ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        val marqueeModifier = if (isHovered) {
                            Modifier.basicMarquee(
                                iterations = Int.MAX_VALUE,
                                animationMode = MarqueeAnimationMode.Immediately,
                                initialDelayMillis = 500,
                                velocity = 30.dp
                            )
                        } else {
                            Modifier
                        }

                        Text(
                            text = build.name,
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = Color.White,
                                shadow = Shadow(Color.Black, blurRadius = 4f)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = marqueeModifier
                        )

                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${build.type} ${build.version}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, "Дополнительно", tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Настройки") },
                                onClick = {
                                    println("!!! SETTINGS ITEM CLICKED in BuildCard !!!")
                                    onSettingsClick()
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Удалить") },
                                onClick = {
                                    println("!!! DELETE ITEM CLICKED in BuildCard !!!")
                                    onDeleteClick()
                                    showMenu = false
                                }
                            )
                        }
                    }
                }

                // --- НИЖНЯЯ ЧАСТЬ ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LaunchButton(
                        onClick = onLaunchClick,
                        isPreparing = isPreparing,
                        isRunning = isRunning,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = onOpenFolderClick,
                        modifier = Modifier.size(36.dp).background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.Default.Folder, "Открыть папку", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun LaunchButton(
    onClick: () -> Unit,
    isPreparing: Boolean,
    isRunning: Boolean,
    modifier: Modifier = Modifier
) {
    val gradientBrush = Brush.horizontalGradient(
        colors = listOf(Color(0xFF8A2BE2), Color(0xFF6A0DAD))
    )

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = when {
                isPreparing -> Color.Gray
                isRunning -> Color(0xFFD32F2F)
                else -> Color.Transparent
            },
            contentColor = Color.White,
            disabledContainerColor = Color.Gray
        ),
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (!isPreparing && !isRunning) gradientBrush else SolidColor(Color.Transparent))
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.White.copy(alpha = 0.5f), Color.White.copy(alpha = 0.1f))
                ),
                shape = RoundedCornerShape(8.dp)
            ),
        enabled = !isPreparing,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (isPreparing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
                Spacer(Modifier.width(8.dp))
                Text("ЗАПУСК...", style = MaterialTheme.typography.labelLarge)
            } else {
                Icon(
                    if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isRunning) "СТОП" else "ИГРАТЬ",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
