package com.memfault.bort.metrics

import androidx.annotation.VisibleForTesting
import com.memfault.bort.IO
import com.memfault.bort.dagger.InjectSet
import com.memfault.bort.scopes.Scope
import com.memfault.bort.scopes.Scoped
import com.memfault.bort.scopes.coroutineScope
import com.memfault.bort.settings.BortEnabledProvider
import com.memfault.bort.settings.MetricsPollingIntervalFlow
import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface MetricCollector {
    suspend fun collect()
}

@ContributesMultibinding(SingletonComponent::class)
class MetricsPoller @Inject constructor(
    private val bortEnabledProvider: BortEnabledProvider,
    private val metricsPollingInterval: MetricsPollingIntervalFlow,
    private val collectors: InjectSet<MetricCollector>,
    @IO private val ioDispatcher: CoroutineContext,
) : Scoped {
    override fun onEnterScope(scope: Scope) {
        runPoller(scope.coroutineScope(ioDispatcher))
    }

    @VisibleForTesting
    internal fun runPoller(scope: CoroutineScope) = scope.launch {
        combine(bortEnabledProvider.isEnabledFlow(), metricsPollingInterval()) { enabled, interval ->
            PollerSettings(enabled, interval)
        }
            .distinctUntilChanged()
            .collectLatest {
                Logger.d("MetricsPoller: enabled=${it.enabled} interval=${it.interval}")
                while (it.enabled && it.interval.isPositive()) {
                    collectMetrics(scope)
                    delay(it.interval)
                }
            }
    }

    override fun onExitScope() = Unit

    private suspend fun collectMetrics(scope: CoroutineScope) {
        Logger.v("MetricsPoller: collectMetrics")
        collectors.forEach {
            scope.launch {
                runCatching {
                    withTimeout(COLLECTION_TIMEOUT) {
                        it.collect()
                    }
                }
            }
        }
    }

    private data class PollerSettings(
        val enabled: Boolean,
        val interval: Duration,
    )

    companion object {
        private val COLLECTION_TIMEOUT = 30.seconds
    }
}
