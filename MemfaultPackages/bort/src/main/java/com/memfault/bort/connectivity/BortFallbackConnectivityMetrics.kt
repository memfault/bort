package com.memfault.bort.connectivity

import android.content.pm.PackageManager
import com.memfault.bort.IO
import com.memfault.bort.scopes.Scope
import com.memfault.bort.scopes.Scoped
import com.memfault.bort.scopes.coroutineScope
import com.memfault.bort.shared.APPLICATION_ID_MEMFAULT_USAGE_REPORTER
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

/**
 * Gathers the Connectivity metrics on Bort if UsageReporter isn't installed, like when Bort lite is running.
 *
 * This is collected optimistically - Bort isn't persistent so it can be killed by Android whenever it pleases.
 * We rely on the fact that Memfault is started fairly periodically due to WorkManager tasks (DropBoxGetEntriesTask
 * runs at a 15m periodic interval), so it's likely we'll get at least 15m granularity from Bort, if UsageReporter
 * isn't installed.
 *
 * In the future, we could additionally schedule a WorkManager task that simply waits for a validated Network,
 * using [androidx.work.Constraints.requiredNetworkType], which would at least log when we gain Network connectivity.
 */
@ContributesMultibinding(SingletonComponent::class)
class BortFallbackConnectivityMetrics
@Inject constructor(
    private val packageManager: PackageManager,
    @IO private val ioCoroutineContext: CoroutineContext,
    private val connectivityMetrics: ConnectivityMetrics,
) : Scoped {
    override fun onEnterScope(scope: Scope) {
        scope.coroutineScope()
            .launch {
                if (!isUsageReporterInstalled()) {
                    scope.register(connectivityMetrics)
                }
            }
    }

    override fun onExitScope() = Unit

    private suspend fun isUsageReporterInstalled(): Boolean = withContext(ioCoroutineContext) {
        try {
            packageManager.getApplicationInfo(APPLICATION_ID_MEMFAULT_USAGE_REPORTER, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
