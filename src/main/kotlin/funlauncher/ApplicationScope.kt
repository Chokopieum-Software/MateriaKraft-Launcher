package funlauncher

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * A global CoroutineScope that lives as long as the application.
 * Use this for long-running background tasks that should not be cancelled
 * when a Composable leaves the composition.
 */
object ApplicationScope {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
