/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import funlauncher.auth.Account
import funlauncher.auth.AccountManager
import funlauncher.auth.MicrosoftAccount
import funlauncher.auth.OfflineAccount
import ui.viewmodel.AccountViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    accountManager: AccountManager,
    onDismiss: () -> Unit,
    onAccountSelected: (Account) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val viewModel = remember { AccountViewModel(accountManager, coroutineScope) }
    val accounts by viewModel.accounts.collectAsState()
    val isLoggingIn by viewModel.isLoggingIn.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var showAddAccountTypeDialog by remember { mutableStateOf(false) }
    var showAddOfflineAccountDialog by remember { mutableStateOf(false) }
    var accountToDelete by remember { mutableStateOf<Account?>(null) }
    var showBrowserLoginDialog by remember { mutableStateOf(false) }

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
            Scaffold(
                topBar = {
                    TopAppBar(title = { Text("Аккаунты") })
                },
                floatingActionButton = {
                    ExtendedFloatingActionButton(
                        text = { Text("Добавить") },
                        icon = { Icon(Icons.Default.Add, contentDescription = "Добавить аккаунт") },
                        onClick = { showAddAccountTypeDialog = true }
                    )
                }
            ) { paddingValues ->
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                    if (accounts.isEmpty()) {
                        Text("Нет добавленных аккаунтов.", modifier = Modifier.align(Alignment.Center))
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(accounts, key = { it.uuid ?: it.username }) { account ->
                                ListItem(
                                    headlineContent = { Text(account.username) },
                                    supportingContent = {
                                        when (account) {
                                            is OfflineAccount -> Text("Оффлайн")
                                            is MicrosoftAccount -> Text(if (account.isLicensed) "Microsoft" else "Microsoft (Xbox)")
                                        }
                                    },
                                    trailingContent = {
                                        IconButton(onClick = { accountToDelete = account }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = Color.Red)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().clickable { onAccountSelected(account) }
                                )
                            }
                        }
                    }
                    if (isLoggingIn) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
        }
    }

    if (showBrowserLoginDialog) {
        Dialog(onDismissRequest = {}) {
            Surface(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Продолжите в браузере...")
                }
            }
        }
    }

    if (showAddAccountTypeDialog) {
        AlertDialog(
            onDismissRequest = { showAddAccountTypeDialog = false },
            title = { Text("Выберите тип аккаунта") },
            text = { Text("Какой аккаунт вы хотите добавить?") },
            confirmButton = {
                Button(onClick = {
                    showAddAccountTypeDialog = false
                    showBrowserLoginDialog = true
                    viewModel.loginWithMicrosoft()
                    showBrowserLoginDialog = false
                }) { Text("Microsoft") }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showAddAccountTypeDialog = false
                        showAddOfflineAccountDialog = true
                    }
                ) { Text("Оффлайн") }
            }
        )
    }

    if (showAddOfflineAccountDialog) {
        var username by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddOfflineAccountDialog = false },
            title = { Text("Добавить оффлайн аккаунт") },
            text = {
                Column {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Имя пользователя") },
                        isError = errorMessage != null,
                        singleLine = true
                    )
                    errorMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.addOfflineAccount(username)
                    if (errorMessage == null) {
                        showAddOfflineAccountDialog = false
                    }
                }) { Text("Добавить") }
            },
            dismissButton = {
                Button(onClick = { showAddOfflineAccountDialog = false }) { Text("Отмена") }
            }
        )
    }

    accountToDelete?.let { account ->
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            title = { Text("Подтверждение") },
            text = { Text("Вы уверены, что хотите удалить аккаунт '${account.username}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAccount(account)
                        accountToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("Удалить") }
            },
            dismissButton = {
                Button(onClick = { accountToDelete = null }) { Text("Отмена") }
            }
        )
    }

    errorMessage?.let {
        AlertDialog(
            onDismissRequest = { viewModel.clearErrorMessage() },
            title = { Text("Ошибка") },
            text = { Text(it) },
            confirmButton = {
                Button(onClick = { viewModel.clearErrorMessage() }) { Text("OK") }
            }
        )
    }
}
