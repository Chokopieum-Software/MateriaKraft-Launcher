package ui.viewmodel

import androidx.compose.runtime.mutableStateOf
import funlauncher.auth.Account
import funlauncher.auth.AccountManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AccountViewModel(
    private val accountManager: AccountManager,
    private val coroutineScope: CoroutineScope
) {
    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    private val _isLoggingIn = MutableStateFlow(false)
    val isLoggingIn: StateFlow<Boolean> = _isLoggingIn.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        loadAccounts()
    }

    fun loadAccounts() {
        coroutineScope.launch {
            _accounts.value = accountManager.loadAccounts()
        }
    }

    fun loginWithMicrosoft() {
        coroutineScope.launch {
            _isLoggingIn.value = true
            val success = withContext(Dispatchers.IO) {
                accountManager.loginWithMicrosoft()
            }
            if (success) {
                loadAccounts()
            } else {
                _errorMessage.value = "Не удалось войти через Microsoft."
            }
            _isLoggingIn.value = false
        }
    }

    fun addOfflineAccount(username: String) {
        coroutineScope.launch {
            val newAccount = funlauncher.auth.OfflineAccount(
                username = username,
                uuid = "00000000-0000-0000-0000-000000000000",
                accessToken = "0"
            )
            if (accountManager.addAccount(newAccount)) {
                loadAccounts()
            } else {
                _errorMessage.value = "Не удалось добавить аккаунт. Возможно, аккаунт с таким именем уже существует или у вас нет лицензионного аккаунта."
            }
        }
    }

    fun deleteAccount(account: Account) {
        coroutineScope.launch {
            accountManager.deleteAccount(account)
            loadAccounts()
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }
}
