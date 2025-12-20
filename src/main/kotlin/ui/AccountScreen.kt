package ui

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
import funlauncher.Account
import funlauncher.AccountManager
import funlauncher.OfflineAccount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    accountManager: AccountManager,
    onDismiss: () -> Unit,
    onAccountSelected: (Account) -> Unit,
    onAccountsUpdated: () -> Unit
) {
    var accounts by remember { mutableStateOf(accountManager.getAccounts()) }
    var showAddAccountTypeDialog by remember { mutableStateOf(false) }
    var showAddOfflineAccountDialog by remember { mutableStateOf(false) }
    var accountToDelete by remember { mutableStateOf<Account?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
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
                        items(accounts) { account ->
                            ListItem(
                                headlineContent = { Text(account.username) },
                                supportingContent = {
                                    when (account) {
                                        is OfflineAccount -> Text("Оффлайн")
                                        else -> Text("Неизвестный тип")
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
                    // TODO: Реализовать добавление Mojang аккаунта
                }) { Text("Mojang") }
            },
            dismissButton = {
                Button(onClick = {
                    showAddAccountTypeDialog = false
                    showAddOfflineAccountDialog = true
                }) { Text("Оффлайн") }
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
                    val newAccount = OfflineAccount(username)
                    if (accountManager.addAccount(newAccount)) {
                        accounts = accountManager.getAccounts()
                        onAccountsUpdated()
                        showAddOfflineAccountDialog = false
                        errorMessage = null
                    } else {
                        errorMessage = "Аккаунт с таким именем уже существует."
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
                        accountManager.deleteAccount(account)
                        accounts = accountManager.getAccounts()
                        onAccountsUpdated()
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
}
