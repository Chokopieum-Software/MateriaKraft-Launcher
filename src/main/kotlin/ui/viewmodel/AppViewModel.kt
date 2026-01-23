package ui.viewmodel

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sun.management.OperatingSystemMXBean
import funlauncher.*
import funlauncher.auth.Account
import funlauncher.auth.AccountManager
import funlauncher.game.MLGDClient
import funlauncher.game.MinecraftInstaller
import funlauncher.game.VersionMetadataFetcher
import funlauncher.managers.BuildManager
import funlauncher.managers.CacheManager
import funlauncher.managers.JavaManager
import funlauncher.managers.PathManager
import funlauncher.net.JavaDownloader
import kotlinx.coroutines.*
import org.chokopieum.software.mlgd.StatusResponse
import state.AppState
import ui.AppTab
import java.lang.management.ManagementFactory

class AppViewModel(
    private val appState: AppState,
    val buildManager: BuildManager,
    val javaManager: JavaManager,
    val accountManager: AccountManager,
    val javaDownloader: JavaDownloader,
    val versionMetadataFetcher: VersionMetadataFetcher,
    private val onSettingsChange: (AppSettings) -> Unit
) {
    val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // --- UI State ---
    var currentTab by mutableStateOf(AppTab.Home)
    val buildList = mutableStateListOf(*appState.builds.toTypedArray())
    val buildsPendingDeletion = mutableStateListOf<String>()
    var accounts by mutableStateOf(appState.accounts)
    var currentAccount by mutableStateOf(accounts.firstOrNull())

    var daemonStatus by mutableStateOf(StatusResponse(running = false))
    val runningBuild by derivedStateOf {
        if (daemonStatus.running) buildList.find { it.name == daemonStatus.buildName } else null
    }

    var showAddBuildDialog by mutableStateOf(false)
    var showJavaManagerWindow by mutableStateOf(false)
    var showAccountScreen by mutableStateOf(false)
    var showDownloadsPopup by mutableStateOf(false)
    var showBuildSettingsScreen by mutableStateOf<MinecraftBuild?>(null)
    var errorDialogMessage by mutableStateOf<String?>(null)
    var buildToDelete by mutableStateOf<MinecraftBuild?>(null)
    var showGameConsole by mutableStateOf(false)
    var showRamWarningDialog by mutableStateOf<MinecraftBuild?>(null)

    var isLaunchingBuildId by mutableStateOf<String?>(null)
    var showCheckmark by mutableStateOf(false)

    init {
        pollDaemonStatus()
        synchronizeBuilds()
    }

    private fun pollDaemonStatus() {
        viewModelScope.launch {
            while (true) {
                val newStatus = withContext(Dispatchers.IO) { MLGDClient.getStatus() }
                if (newStatus != daemonStatus) {
                    daemonStatus = newStatus
                    if (!newStatus.running) {
                        isLaunchingBuildId = null
                    }
                }
                delay(2000) // Poll every 2 seconds
            }
        }
    }

    private fun synchronizeBuilds() {
        viewModelScope.launch {
            val (synchronizedBuilds, newCount) = withContext(Dispatchers.IO) {
                buildManager.synchronizeBuilds()
            }
            if (synchronizedBuilds.size != buildList.size || synchronizedBuilds != buildList) {
                buildList.clear()
                buildList.addAll(synchronizedBuilds)
            }
            // TODO: Show snackbar message about new builds
        }
    }

    fun refreshBuilds() {
        viewModelScope.launch {
            val freshBuilds = withContext(Dispatchers.IO) {
                buildManager.loadBuilds()
            }
            buildList.clear()
            buildList.addAll(freshBuilds)
        }
    }

    private suspend fun launchMinecraft(build: MinecraftBuild, javaPath: String, account: Account) {
        runCatching {
            if (appState.settings.showConsoleOnLaunch) {
                showGameConsole = true
            }
            val installer = MinecraftInstaller(build, buildManager)
            val finalMaxRam = build.maxRamMb ?: appState.settings.maxRamMb
            val finalJavaArgs = build.javaArgs ?: appState.settings.javaArgs
            val finalEnvVars = build.envVars ?: appState.settings.envVars

            val launchConfig = withContext(Dispatchers.IO) {
                installer.createLaunchConfig(
                    account = account,
                    javaPath = javaPath,
                    maxRamMb = finalMaxRam,
                    javaArgs = finalJavaArgs,
                    envVars = finalEnvVars,
                    showConsole = appState.settings.showConsoleOnLaunch
                )
            }
            daemonStatus = withContext(Dispatchers.IO) { MLGDClient.launch(launchConfig) }
        }.onFailure { e ->
            e.printStackTrace()
            errorDialogMessage = "Ошибка запуска: ${e.message}"
            isLaunchingBuildId = null
        }
    }

    private fun performLaunch(build: MinecraftBuild) {
        isLaunchingBuildId = build.name
        viewModelScope.launch {
            val useAutoJava = build.javaPath.isNullOrBlank() && appState.settings.javaPath.isBlank()
            val account = currentAccount ?: run {
                errorDialogMessage = "Сначала выберите аккаунт!"
                isLaunchingBuildId = null
                return@launch
            }

            if (useAutoJava) {
                val recommendedVersion = javaManager.getRecommendedJavaVersion(build.version)
                val installations = withContext(Dispatchers.IO) { javaManager.findJavaInstallations() }
                val exactJava = (installations.launcher + installations.system).firstOrNull { it.version == recommendedVersion && it.is64Bit }

                if (exactJava != null) {
                    launchMinecraft(build, exactJava.path, account)
                } else {
                    javaDownloader.downloadAndUnpack(recommendedVersion) { result ->
                        viewModelScope.launch {
                            result.fold(
                                onSuccess = { downloadedJava -> launchMinecraft(build, downloadedJava.path, account) },
                                onFailure = {
                                    errorDialogMessage = "Не удалось скачать Java: ${it.message}"
                                    isLaunchingBuildId = null
                                }
                            )
                        }
                    }
                }
            } else {
                val finalJavaPath = build.javaPath ?: appState.settings.javaPath
                launchMinecraft(build, finalJavaPath, account)
            }
        }
    }

    fun onLaunchClick(build: MinecraftBuild) {
        if (currentAccount == null) {
            errorDialogMessage = "Сначала выберите аккаунт!"
            return
        }
        if (daemonStatus.running) {
            // TODO: Show snackbar that a game is already running
            return
        }

        val osBean = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean
        val freeMemory = osBean.freeMemorySize / (1024 * 1024)
        val allocatedRam = build.maxRamMb ?: appState.settings.maxRamMb

        if (allocatedRam > freeMemory) {
            showRamWarningDialog = build
        } else {
            performLaunch(build)
        }
    }

    fun onConfirmRamWarning(build: MinecraftBuild) {
        showRamWarningDialog = null
        performLaunch(build)
    }

    fun onDeleteBuildClick(build: MinecraftBuild) {
        buildToDelete = build
    }

    fun onConfirmDelete(build: MinecraftBuild) {
        viewModelScope.launch {
            buildsPendingDeletion.add(build.name)
            buildToDelete = null
            delay(400)
            withContext(Dispatchers.IO) { buildManager.deleteBuild(build.name) }
            buildList.removeIf { it.name == build.name }
            buildsPendingDeletion.remove(build.name)
        }
    }

    fun onAddBuild(name: String, version: String, type: String, imagePath: String?) {
        viewModelScope.launch {
            runCatching {
                val buildType = BuildType.valueOf(type)
                withContext(Dispatchers.IO) { buildManager.addBuild(name, version, buildType, imagePath) }
                refreshBuilds()
                showAddBuildDialog = false
            }.onFailure { e ->
                errorDialogMessage = e.message
            }
        }
    }

    fun onSaveBuildSettings(newName: String, newVersion: String, newType: String, newImagePath: String?, javaPath: String?, maxRam: Int?, javaArgs: String?, envVars: String?) {
        viewModelScope.launch {
            runCatching {
                val buildType = BuildType.valueOf(newType)
                showBuildSettingsScreen?.let {
                    withContext(Dispatchers.IO) {
                        buildManager.updateBuildSettings(
                            oldName = it.name,
                            newName = newName,
                            newVersion = newVersion,
                            newType = buildType,
                            newImagePath = newImagePath,
                            newJavaPath = javaPath,
                            newMaxRam = maxRam,
                            newJavaArgs = javaArgs,
                            newEnvVars = envVars
                        )
                    }
                }
                refreshBuilds()
                showBuildSettingsScreen = null
            }.onFailure { e ->
                errorDialogMessage = e.message
            }
        }
    }
    
    fun onAccountSelected(account: Account) {
        currentAccount = account
        showAccountScreen = false
    }

    fun onSettingsChanged(newSettings: AppSettings) {
        onSettingsChange(newSettings)
    }

    fun cancelScope() {
        viewModelScope.cancel()
    }
}
