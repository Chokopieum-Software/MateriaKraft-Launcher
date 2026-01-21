package ui

import androidx.compose.runtime.Composable
import funlauncher.BuildType
import funlauncher.MinecraftBuild
import funlauncher.auth.Account
import funlauncher.auth.AccountManager
import funlauncher.managers.JavaManager
import funlauncher.managers.PathManager
import funlauncher.net.JavaDownloader
import state.AppState
import ui.dialogs.AddBuildDialog
import ui.dialogs.ConfirmDeleteDialog
import ui.dialogs.ErrorDialog
import ui.dialogs.RamWarningDialog
import ui.screens.AccountScreen
import ui.screens.BuildSettingsScreen
import ui.screens.JavaManagerWindow
import ui.windows.GameConsoleWindow

/**
 * Компонент, который управляет отображением всех модальных окон и диалогов поверх основного интерфейса.
 */
@Composable
fun AppOverlays(
    appState: AppState,
    showJavaManagerWindow: Boolean,
    onCloseJavaManagerWindow: () -> Unit,
    javaManager: JavaManager,
    javaDownloader: JavaDownloader,
    showAddBuildDialog: Boolean,
    onCloseAddBuildDialog: () -> Unit,
    onAddBuild: (String, String, BuildType, String?) -> Unit,
    pathManager: PathManager,
    buildSettingsToShow: MinecraftBuild?,
    onCloseBuildSettings: () -> Unit,
    onSaveBuildSettings: (String, String, BuildType, String?, String?, Int?, String?, String?) -> Unit,
    showAccountScreen: Boolean,
    onCloseAccountScreen: () -> Unit,
    onAccountSelected: (Account) -> Unit,
    accountManager: AccountManager,
    showGameConsole: Boolean,
    onCloseGameConsole: () -> Unit,
    ramWarningDialogToShow: MinecraftBuild?,
    onCloseRamWarningDialog: () -> Unit,
    onConfirmRamWarning: (MinecraftBuild) -> Unit,
    errorDialogMessage: String?,
    onDismissErrorDialog: () -> Unit,
    buildToDelete: MinecraftBuild?,
    onDismissDeleteDialog: () -> Unit,
    onConfirmDelete: (MinecraftBuild) -> Unit
) {
    if (showJavaManagerWindow) {
        JavaManagerWindow(
            onCloseRequest = onCloseJavaManagerWindow,
            javaManager = javaManager,
            javaDownloader = javaDownloader,
            appSettings = appState.settings
        )
    }
    if (showAddBuildDialog) {
        AddBuildDialog(
            onDismiss = onCloseAddBuildDialog,
            onAdd = onAddBuild,
            pathManager = pathManager
        )
    }
    buildSettingsToShow?.let { build ->
        BuildSettingsScreen(
            build = build,
            globalSettings = appState.settings,
            onDismiss = onCloseBuildSettings,
            onSave = onSaveBuildSettings,
            pathManager = pathManager
        )
    }
    if (showAccountScreen) {
        AccountScreen(
            accountManager = accountManager,
            onDismiss = onCloseAccountScreen,
            onAccountSelected = onAccountSelected
        )
    }
    if (showGameConsole) {
        GameConsoleWindow(onCloseRequest = onCloseGameConsole)
    }
    ramWarningDialogToShow?.let { build ->
        RamWarningDialog(
            build = build,
            onDismiss = onCloseRamWarningDialog,
            onConfirm = { onConfirmRamWarning(build) }
        )
    }
    errorDialogMessage?.let { message ->
        ErrorDialog(
            message = message,
            onDismiss = onDismissErrorDialog
        )
    }
    buildToDelete?.let { build ->
        ConfirmDeleteDialog(
            build = build,
            onDismiss = onDismissDeleteDialog,
            onConfirm = { onConfirmDelete(build) }
        )
    }
}
