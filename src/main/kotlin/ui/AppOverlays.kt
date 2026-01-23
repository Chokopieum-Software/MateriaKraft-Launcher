package ui

import androidx.compose.runtime.Composable
import funlauncher.managers.PathManager
import state.AppState
import ui.dialogs.*
import ui.screens.AccountScreen
import ui.screens.BuildSettingsScreen
import ui.screens.JavaManagerWindow
import ui.viewmodel.AppViewModel

@Composable
fun AppOverlays(
    viewModel: AppViewModel,
    appState: AppState,
    pathManager: PathManager
) {
    if (viewModel.showJavaManagerWindow) {
        JavaManagerWindow(
            onCloseRequest = { viewModel.showJavaManagerWindow = false },
            javaManager = viewModel.javaManager,
            javaDownloader = viewModel.javaDownloader,
            appSettings = appState.settings
        )
    }

    if (viewModel.showAddBuildDialog) {
        AddBuildDialog(
            pathManager = pathManager,
            onDismiss = { viewModel.showAddBuildDialog = false },
            onAdd = { name, version, type, imagePath ->
                viewModel.onAddBuild(name, version, type.name, imagePath)
            }
        )
    }

    viewModel.showBuildSettingsScreen?.let { build ->
        BuildSettingsScreen(
            build = build,
            pathManager = pathManager,
            globalSettings = appState.settings,
            onDismiss = { viewModel.showBuildSettingsScreen = null },
            onSave = { newName, newVersion, newType, newImagePath, javaPath, maxRam, javaArgs, envVars ->
                viewModel.onSaveBuildSettings(newName, newVersion, newType.name, newImagePath, javaPath, maxRam, javaArgs, envVars)
            }
        )
    }

    if (viewModel.showAccountScreen) {
        AccountScreen(
            accountManager = viewModel.accountManager,
            onAccountSelected = viewModel::onAccountSelected,
            onDismiss = { viewModel.showAccountScreen = false }
        )
    }

    // This should be a separate component, but for now let's keep it here
    if (viewModel.showGameConsole) {
        // GameConsoleView(...)
    }

    viewModel.showRamWarningDialog?.let { build ->
        RamWarningDialog(
            build = build,
            onDismiss = {
                viewModel.showRamWarningDialog = null
                viewModel.isLaunchingBuildId = null
            },
            onConfirm = { viewModel.onConfirmRamWarning(build) }
        )
    }

    viewModel.errorDialogMessage?.let { message ->
        ErrorDialog(
            message = message,
            onDismiss = { viewModel.errorDialogMessage = null }
        )
    }

    viewModel.buildToDelete?.let { build ->
        ConfirmDeleteDialog(
            build = build,
            onDismiss = { viewModel.buildToDelete = null },
            onConfirm = { viewModel.onConfirmDelete(build) }
        )
    }
}
