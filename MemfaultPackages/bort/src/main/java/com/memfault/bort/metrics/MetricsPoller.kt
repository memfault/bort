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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface MetricCollector {
    fun onChanged(): Flow<Any> = flowOf(Unit)
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
        combine(
            bortEnabledProvider.isEnabledFlow(),
            metricsPollingInterval(),
        ) { enabled, interval ->
            PollerSettings(enabled, interval)
        }
            .distinctUntilChanged()
            .flatMapLatest { settings ->
                combine(collectors.map { collector -> collector.onChanged() }) { settings }
            }
            .collectLatest { settings ->
                Logger.d("MetricsPoller: enabled=${settings.enabled} interval=${settings.interval}")
                while (settings.enabled && settings.interval.isPositive()) {
                    collectMetrics(scope)
                    delay(settings.interval)
                }
            }
    }

    override fun onExitScope() = Unit

    private suspend fun collectMetrics(scope: CoroutineScope) {
        Logger.v("MetricsPoller: collectMetrics")
        collectors.forEach { collector ->
            scope.launch {
                runCatching {
                    withTimeout(COLLECTION_TIMEOUT) {
                        collector.collect()
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
