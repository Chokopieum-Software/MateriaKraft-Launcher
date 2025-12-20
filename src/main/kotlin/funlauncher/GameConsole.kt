package funlauncher

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object GameConsole {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs = _logs.asStateFlow()

    fun addLog(log: String) {
        _logs.value = _logs.value + log
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
