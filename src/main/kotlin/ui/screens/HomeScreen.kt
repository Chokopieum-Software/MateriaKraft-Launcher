/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import funlauncher.BuildType
import funlauncher.MinecraftBuild
import funlauncher.auth.Account
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ui.widgets.AvatarImage
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
    onSettingsBuildClick: (MinecraftBuild) -> Unit,
    currentAccount: Account?,
    onOpenAccountManager: () -> Unit,
    buildsPendingDeletion: Set<String>
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredBuilds = builds.filter { it.name.contains(searchQuery, ignoreCase = true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Поиск...") },
                            modifier = Modifier.width(350.dp).height(50.dp),
                            singleLine = true,
                            shape = RoundedCornerShape(50),
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Поиск") },
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent
                            )
                        )
                        Spacer(Modifier.width(16.dp))
                        FilledTonalButton(onClick = onAddBuildClick) {
                            Text("Создать")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenAccountManager) {
                        AvatarImage(
                            account = currentAccount,
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
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
            itemsIndexed(filteredBuilds, key = { _, build -> build.name }) { index, build ->
                AnimatedVisibility(
                    visible = build.name !in buildsPendingDeletion,
                    exit = shrinkVertically(animationSpec = tween(durationMillis = 300)) + fadeOut(animationSpec = tween(durationMillis = 250))
                ) {
                    BuildCard(
                        build = build,
                        isRunning = build == runningBuild,
                        isPreparing = build.name == isLaunchingBuildId,
                        onLaunchClick = { onLaunchClick(build) },
                        onOpenFolderClick = { onOpenFolderClick(build) },
                        onDeleteClick = { onDeleteBuildClick(build) },
                        onSettingsClick = { onSettingsBuildClick(build) },
                        index = index,
                        modifier = Modifier.animateItem(
                            placementSpec = spring( // Используем именованный аргумент placement
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    )
                }
            }
        }
    }
}

private fun formatBuildVersion(build: MinecraftBuild): String {
    return when (build.type) {
        BuildType.VANILLA -> build.version
        BuildType.FABRIC -> {
            val parts = build.version.split("-fabric-")
            if (parts.size == 2) "${parts[0]} - ${parts[1]}" else build.version
        }
        BuildType.FORGE -> {
            val parts = build.version.split("-forge-")
            if (parts.size == 2) "${parts[0]} - ${parts[1]}" else build.version
        }
        BuildType.QUILT -> {
            val parts = build.version.split("-quilt-")
            if (parts.size == 2) "${parts[0]} - ${parts[1]}" else build.version
        }
        BuildType.NEOFORGE -> {
            val parts = build.version.split("-neoforge-")
            if (parts.size == 2) "${parts[0]} - ${parts[1]}" else build.version
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
    onSettingsClick: () -> Unit,
    index: Int,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val animatedScale = remember { Animatable(0.8f) }
    val animatedAlpha = remember { Animatable(0f) }

    LaunchedEffect(key1 = build.name) {
        val delay = (index * 75L).coerceAtMost(375L)

        launch {
            delay(delay)
            animatedScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }

        launch {
            delay(delay)
            animatedAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 250)
            )
        }
    }

    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(build.imagePath) {
        if (build.imagePath != null) {
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
        modifier = modifier
            .graphicsLayer {
                scaleX = animatedScale.value
                scaleY = animatedScale.value
                alpha = animatedAlpha.value
            }
            .hoverable(interactionSource)
            .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp, hoveredElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16 / 9f)) {
                if (imageBitmap != null) {
                    Image(
                        bitmap = imageBitmap!!,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color(0xFF606060), Color(0xFF303030)))
                    ))
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
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
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = marqueeModifier
                        )

                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = formatBuildVersion(build),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, "Дополнительно")
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
                    LaunchButton(
                        onClick = onLaunchClick,
                        isPreparing = isPreparing,
                        isRunning = isRunning,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = onOpenFolderClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Folder, "Открыть папку")
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
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = when {
                isPreparing -> Color.Gray
                isRunning -> Color(0xFFD32F2F)
                else -> MaterialTheme.colorScheme.primary
            },
            contentColor = Color.White,
            disabledContainerColor = Color.Gray
        ),
        modifier = modifier
            .height(40.dp),
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
