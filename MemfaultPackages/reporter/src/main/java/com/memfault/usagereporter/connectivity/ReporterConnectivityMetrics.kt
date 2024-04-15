package com.memfault.usagereporter.connectivity

import com.memfault.bort.connectivity.ConnectivityMetrics
import com.memfault.bort.scopes.Scope
import com.memfault.bort.scopes.Scoped
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/**
 * Contributes the [Scoped] [ConnectivityMetrics] into UsageReporter. [ConnectivityMetrics] can't be contributed
 * for all apps because it'll be added to OTA as well, while Bort wants to conditionally enable connectivity metrics
 * only if UsageReporter isn't installed.
 */
@ContributesMultibinding(SingletonComponent::class)
class ReporterConnectivityMetrics
@Inject constructor(
    private val connectivityMetrics: ConnectivityMetrics,
) : Scoped {
    override fun onEnterScope(scope: Scope) {
        scope.register(connectivityMetrics)
    }

    override fun onExitScope() = Unit
}
