package com.memfault.bort.metrics.statsd

import android.os.SystemClock
import com.memfault.bort.dagger.InjectSet
import com.memfault.bort.metrics.statsd.proto.Atom
import com.memfault.bort.metrics.statsd.proto.StatsdConfig
import com.memfault.bort.scopes.Scope
import com.memfault.bort.scopes.Scoped
import com.memfault.bort.settings.MetricsSettings
import com.memfault.bort.shared.Logger
import com.memfault.bort.time.CombinedTime
import com.memfault.bort.time.CombinedTimeProvider
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Collects metrics from statsd. StatsD collects events and produces reports based
 * on a given configuration. This class sets up a statsd report that captures event
 * metrics. These are then collected periodically and converted to memfault
 * reporting events.
 */
@ContributesMultibinding(SingletonComponent::class)
@Singleton
class StatsdMetricCollector @Inject constructor(
    private val statsdManagerService: StatsdManagerService,
    private val timeProvider: CombinedTimeProvider,
    private val metricsSettings: MetricsSettings,
    private val eventMetricListeners: InjectSet<StatsdEventMetricListener>,
) : Scoped {
    override fun onEnterScope(scope: Scope) {
        if (metricsSettings.enableStatsdCollection) {
            runCatching {
                statsdManagerService.addConfig(STATSD_CONFIG_KEY, buildConfig())
            }.onFailure { cause ->
                Logger.e("Unable to add statsd config", cause)
            }
        }
    }

    override fun onExitScope() {
    }

    private fun buildConfig(): StatsdConfig = statsdConfig(STATSD_CONFIG_KEY) {
        allowFromSystem()
        allowFromLowMemoryKiller()
        allowFromWifi()
        whitelistAllAtomIds()

        val eventMetricAtoms = eventMetricListeners
            .flatMap { it.atoms() }
            .toSet()

        for (atom in eventMetricAtoms + metricsSettings.extraStatsDAtoms) {
            eventMetric {
                +simpleMatcher(atom)
            }
        }
    }

    fun collect() {
        if (!metricsSettings.enableStatsdCollection) {
            return
        }
        Logger.v("StatsdMetricCollector collecting")

        val collectionTimeNanos = SystemClock.elapsedRealtimeNanos()
        val now = timeProvider.now()

        val reports = statsdManagerService.getReports(STATSD_CONFIG_KEY)

        for (report in reports) {
            for (metric in report.metrics) {
                val eventMetrics = metric.event_metrics
                if (eventMetrics != null) {
                    for (data in eventMetrics.data_) {
                        if (data.atom != null && data.elapsed_timestamp_nanos != null) {
                            reportEventMetric(
                                now = now,
                                collectionTimeNanos = collectionTimeNanos,
                                eventElapsedRealtimeNanos = data.elapsed_timestamp_nanos,
                                atom = data.atom,
                            )
                        }
                        if (data.aggregated_atom_info != null) {
                            val atom = data.aggregated_atom_info.atom
                            if (atom != null) {
                                for (eventElapsedRealTimeNanos in data.aggregated_atom_info.elapsed_timestamp_nanos) {
                                    reportEventMetric(
                                        now = now,
                                        collectionTimeNanos = collectionTimeNanos,
                                        eventElapsedRealtimeNanos = eventElapsedRealTimeNanos,
                                        atom = atom,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Logger.v("StatsdMetricCollector collection ended")
    }

    private fun reportEventMetric(
        now: CombinedTime,
        collectionTimeNanos: Long,
        eventElapsedRealtimeNanos: Long,
        atom: Atom,
    ) {
        val timeBetweenEventAndCollection = (collectionTimeNanos - eventElapsedRealtimeNanos).nanoseconds
        val eventTimestampMillis = now.minus(timeBetweenEventAndCollection).timestamp.toEpochMilli()
        eventMetricListeners.forEach { listener ->
            listener.reportEventMetric(
                eventTimestampMillis = eventTimestampMillis,
                atom = atom,
            )
        }
    }

    companion object {
        private const val STATSD_CONFIG_KEY = 0xB047BEEF // Bort beef?
    }
}
