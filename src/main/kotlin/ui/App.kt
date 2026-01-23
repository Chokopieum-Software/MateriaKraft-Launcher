package ui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import funlauncher.NavPanelPosition
import funlauncher.managers.CacheManager
import funlauncher.managers.PathManager
import funlauncher.net.DownloadManager
import kotlinx.coroutines.delay
import org.chokopieum.software.materia_launcher.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import state.AppState
import ui.screens.*
import ui.theme.AnimatedAppTheme
import ui.viewmodel.AppViewModel
import ui.viewmodel.HomeViewModel
import ui.widgets.BeautifulCircularProgressIndicator
import ui.widgets.DownloadsPopup

// Перечисление для вкладок навигации в приложении.
enum class AppTab { Home, Modifications, Settings }

/**
 * Главный компонент приложения, который управляет состоянием и отображением основного интерфейса.
 * @param viewModel ViewModel, управляющая состоянием и логикой UI.
 * @param appState Текущее состояние приложения (необходимое для некоторых дочерних компонентов).
 * @param pathManager Менеджер путей к файлам приложения.
 * @param cacheManager Менеджер кэша.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun App(
    viewModel: AppViewModel,
    appState: AppState, // appState is still needed for theme and some screen-specific logic
    pathManager: PathManager,
    cacheManager: CacheManager
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val homeViewModel = remember { HomeViewModel(viewModel) }

    // Эффект для отображения "галочки" после завершения всех загрузок.
    LaunchedEffect(DownloadManager.tasks.size) {
        if (DownloadManager.tasks.isEmpty() && !viewModel.showCheckmark) {
            viewModel.showCheckmark = true
            delay(2000)
            viewModel.showCheckmark = false
        }
    }

    // Основная тема и разметка приложения.
    AnimatedAppTheme(appState.settings.theme) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.background
        ) {
            // Анимированные отступы для контента в зависимости от положения навигационной панели.
            val contentPaddingStart by animateDpAsState(if (appState.settings.navPanelPosition == NavPanelPosition.Left && viewModel.currentTab != AppTab.Modifications) 96.dp else 0.dp)
            val contentPaddingBottom by animateDpAsState(if (appState.settings.navPanelPosition == NavPanelPosition.Bottom && viewModel.currentTab != AppTab.Modifications) 80.dp else 0.dp)

            Box(modifier = Modifier.fillMaxSize()) {
                // Плавный переход между экранами (вкладками).
                Box(modifier = Modifier.fillMaxSize().padding(start = contentPaddingStart, bottom = contentPaddingBottom)) {
                    Crossfade(targetState = viewModel.currentTab, animationSpec = tween(300)) { tab ->
                        when (tab) {
                            AppTab.Home -> HomeScreen(homeViewModel)
                            AppTab.Modifications -> ModificationsScreen(
                                onBack = { viewModel.currentTab = AppTab.Home },
                                navPanelPosition = appState.settings.navPanelPosition,
                                buildManager = viewModel.buildManager,
                                onModpackInstalled = {
                                    viewModel.refreshBuilds()
                                    viewModel.currentTab = AppTab.Home
                                },
                                pathManager = pathManager,
                                snackbarHostState = snackbarHostState,
                                cacheManager = cacheManager
                            )
                            AppTab.Settings -> SettingsTab(
                                currentSettings = appState.settings,
                                onSave = viewModel::onSettingsChanged,
                                onOpenJavaManager = { viewModel.showJavaManagerWindow = true },
                                accountManager = viewModel.accountManager,
                                coroutineScope = scope,
                                versionMetadataFetcher = viewModel.versionMetadataFetcher,
                                snackbarHostState = snackbarHostState
                            )
                        }
                    }
                }

                // Навигационные панели
                AppNavigation(
                    currentTab = viewModel.currentTab,
                    onTabSelected = { viewModel.currentTab = it },
                    navPanelPosition = appState.settings.navPanelPosition
                )

                // Кнопка (FAB) для отображения статуса и списка загрузок.
                DownloadsFab(
                    show = DownloadManager.tasks.isNotEmpty() || viewModel.showCheckmark,
                    showCheckmark = viewModel.showCheckmark,
                    showPopup = viewModel.showDownloadsPopup,
                    onTogglePopup = { viewModel.showDownloadsPopup = !viewModel.showDownloadsPopup }
                )
            }
        }

        // Компонент для отображения всех оверлеев (диалоги, всплывающие окна).
        AppOverlays(
            viewModel = viewModel,
            appState = appState,
            pathManager = pathManager
        )
    }
}

@Composable
private fun BoxScope.AppNavigation( // Changed to BoxScope receiver
    currentTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    navPanelPosition: NavPanelPosition
) {
    // Боковая навигационная панель (слева).
    AnimatedVisibility(
        visible = navPanelPosition == NavPanelPosition.Left && currentTab != AppTab.Modifications,
        enter = slideInHorizontally(initialOffsetX = { -it }),
        exit = slideOutHorizontally(targetOffsetX = { -it }),
        modifier = Modifier.align(Alignment.CenterStart)
    ) {
        NavigationRail(
            modifier = Modifier
                .padding(start = 16.dp)
                .height(300.dp)
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp)),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.fillMaxHeight().padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                NavigationRailItem(
                    selected = currentTab == AppTab.Home,
                    onClick = { onTabSelected(AppTab.Home) },
                    icon = { Icon(Icons.Default.Home, contentDescription = stringResource(Res.string.tab_home)) },
                    label = { Text(stringResource(Res.string.tab_home)) }
                )
                Spacer(Modifier.height(16.dp))
                NavigationRailItem(
                    selected = currentTab == AppTab.Modifications,
                    onClick = { onTabSelected(AppTab.Modifications) },
                    icon = { Icon(Icons.Default.Build, contentDescription = stringResource(Res.string.tab_modifications)) },
                    label = { Text(stringResource(Res.string.tab_modifications)) }
                )
                Spacer(Modifier.height(16.dp))
                NavigationRailItem(
                    selected = currentTab == AppTab.Settings,
                    onClick = { onTabSelected(AppTab.Settings) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = stringResource(Res.string.tab_settings)) },
                    label = { Text(stringResource(Res.string.tab_settings)) }
                )
            }
        }
    }

    // Нижняя навигационная панель.
    AnimatedVisibility(
        visible = navPanelPosition == NavPanelPosition.Bottom && currentTab != AppTab.Modifications,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        NavigationBar(
            modifier = Modifier
                .padding(bottom = 16.dp)
                .width(300.dp)
                .height(64.dp)
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp)),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            NavigationBarItem(
                selected = currentTab == AppTab.Home,
                onClick = { onTabSelected(AppTab.Home) },
                icon = { Icon(Icons.Default.Home, contentDescription = stringResource(Res.string.tab_home)) },
                label = { Text(stringResource(Res.string.tab_home)) }
            )
            NavigationBarItem(
                selected = currentTab == AppTab.Modifications,
                onClick = { onTabSelected(AppTab.Modifications) },
                icon = { Icon(Icons.Default.Build, contentDescription = stringResource(Res.string.tab_modifications)) },
                label = { Text(stringResource(Res.string.tab_modifications)) }
            )
            NavigationBarItem(
                selected = currentTab == AppTab.Settings,
                onClick = { onTabSelected(AppTab.Settings) },
                icon = { Icon(Icons.Default.Settings, contentDescription = stringResource(Res.string.tab_settings)) },
                label = { Text(stringResource(Res.string.tab_settings)) }
            )
        }
    }
}

@Composable
private fun BoxScope.DownloadsFab(
    show: Boolean,
    showCheckmark: Boolean,
    showPopup: Boolean,
    onTogglePopup: () -> Unit
) {
    AnimatedVisibility(
        visible = show,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
    ) {
        Box {
            FloatingActionButton(
                onClick = onTogglePopup,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) {
                when {
                    showCheckmark -> Icon(Icons.Default.Check, contentDescription = "Загрузка завершена")
                    DownloadManager.tasks.isNotEmpty() -> BeautifulCircularProgressIndicator(
                        size = 24.dp,
                        strokeWidth = 3.dp,
                        primaryColor = MaterialTheme.colorScheme.primary,
                        secondaryColor = MaterialTheme.colorScheme.tertiary
                    )
                    else -> Icon(Icons.Default.Download, contentDescription = "Загрузки")
                }
            }
            if (showPopup) {
                DownloadsPopup(onDismissRequest = onTogglePopup)
            }
        }
    }
}
