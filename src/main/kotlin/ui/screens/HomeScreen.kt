/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import funlauncher.BuildType
import funlauncher.MinecraftBuild
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ui.viewmodel.HomeViewModel
import ui.widgets.AvatarImage
import ui.widgets.ImageLoader

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel
) {
    val filteredBuilds by rememberUpdatedState(viewModel.filteredBuilds)

    Scaffold(
        topBar = {
            HomeTopAppBar(
                searchQuery = viewModel.searchQuery,
                onSearchQueryChange = viewModel::onSearchQueryChanged,
                onAddBuildClick = viewModel::onAddBuildClick,
                onOpenAccountManager = viewModel::onOpenAccountManager,
                viewModel = viewModel
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        if (viewModel.builds.isEmpty()) {
            EmptyState(Modifier.fillMaxSize().padding(paddingValues))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 220.dp),
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(filteredBuilds, key = { _, build -> build.name }) { index, build ->
                    AnimatedVisibility(
                        visible = build.name !in viewModel.buildsPendingDeletion,
                        exit = shrinkVertically(animationSpec = tween(durationMillis = 300)) + fadeOut(animationSpec = tween(durationMillis = 250))
                    ) {
                        AnimatedBuildCard(
                            build = build,
                            isRunning = build == viewModel.runningBuild,
                            isPreparing = build.name == viewModel.isLaunchingBuildId,
                            onLaunchClick = { viewModel.onLaunchClick(build) },
                            onOpenFolderClick = { viewModel.onOpenFolderClick(build) },
                            onDeleteClick = { viewModel.onDeleteBuildClick(build) },
                            onSettingsClick = { viewModel.onSettingsBuildClick(build) },
                            index = index,
                            modifier = Modifier.animateItem(
                                placementSpec = spring(
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopAppBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onAddBuildClick: () -> Unit,
    onOpenAccountManager: () -> Unit,
    viewModel: HomeViewModel
) {
    TopAppBar(
        title = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
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
                    account = viewModel.currentAccount,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Сборки не найдены.\nНажмите \"Создать\", чтобы добавить новую.",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
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
private fun AnimatedBuildCard(
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

    BuildCard(
        build = build,
        isRunning = isRunning,
        isPreparing = isPreparing,
        onLaunchClick = onLaunchClick,
        onOpenFolderClick = onOpenFolderClick,
        onDeleteClick = onDeleteClick,
        onSettingsClick = onSettingsClick,
        modifier = modifier
            .graphicsLayer {
                scaleX = animatedScale.value
                scaleY = animatedScale.value
                alpha = animatedAlpha.value
            }
    )
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
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val imageBitmap = ImageLoader.rememberImageBitmap(build.imagePath)

    Card(
        modifier = modifier
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
                        bitmap = imageBitmap,
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
