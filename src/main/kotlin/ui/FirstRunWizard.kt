/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import funlauncher.auth.AccountManager
import funlauncher.AppSettings
import funlauncher.NavPanelPosition
import funlauncher.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirstRunWizard(
    accountManager: AccountManager,
    initialTheme: Theme,
    onThemeChange: (Theme) -> Unit,
    onWizardComplete: (AppSettings) -> Unit
) {
    var navPanelPosition by remember { mutableStateOf(NavPanelPosition.Left) }
    var isAuthenticating by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var authSuccess by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    fun handleLogin() {
        scope.launch(Dispatchers.IO) {
            isAuthenticating = true
            authError = null
            authSuccess = false
            try {
                val success = accountManager.loginWithMicrosoft()
                if (success) {
                    authSuccess = true
                } else {
                    authError = "Не удалось войти в аккаунт Microsoft."
                }
            } catch (e: Exception) {
                authError = "Критическая ошибка: ${e.message}"
            } finally {
                isAuthenticating = false
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Добро пожаловать в Materia!", style = MaterialTheme.typography.headlineLarge)
            Text("Давайте настроим лаунчер для первого запуска.", style = MaterialTheme.typography.bodyMedium)

            Spacer(modifier = Modifier.height(16.dp))

            // --- Вход в аккаунт ---
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Аккаунт", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    when {
                        isAuthenticating -> CircularProgressIndicator()
                        authSuccess -> Text("Вы успешно вошли!", color = MaterialTheme.colorScheme.primary)
                        else -> Button(onClick = ::handleLogin) {
                            Text("Войти через Microsoft")
                        }
                    }
                    authError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // --- Настройки внешнего вида ---
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Внешний вид", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(16.dp))

                    Text("Тема", style = MaterialTheme.typography.titleMedium)
                    val themeOptions = Theme.values().map { it.name }
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        themeOptions.forEachIndexed { index, label ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = themeOptions.size),
                                onClick = { onThemeChange(Theme.values()[index]) },
                                selected = initialTheme.name == label
                            ) { Text(label) }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Text("Положение панели навигации", style = MaterialTheme.typography.titleMedium)
                    val navPanelOptions = NavPanelPosition.values().map { it.name }
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        navPanelOptions.forEachIndexed { index, label ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = navPanelOptions.size),
                                onClick = { navPanelPosition = NavPanelPosition.values()[index] },
                                selected = navPanelPosition.name == label
                            ) { Text(label) }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- Кнопка завершения ---
            Button(
                onClick = {
                    val finalSettings = AppSettings(
                        theme = initialTheme,
                        navPanelPosition = navPanelPosition
                        // Остальные настройки останутся по умолчанию
                    )
                    onWizardComplete(finalSettings)
                },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Готово", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
