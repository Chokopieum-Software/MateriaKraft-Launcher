package funlauncher

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Глобальная CoroutineScope для долгоживущих задач приложения, которые не должны
 * отменяться при закрытии компонентов пользовательского интерфейса.
 */
val ApplicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
