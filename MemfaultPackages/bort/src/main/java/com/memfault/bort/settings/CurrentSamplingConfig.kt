package com.memfault.bort.settings

import com.memfault.bort.CachedAsyncProperty
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.reporting.StateAgg
import com.memfault.bort.shared.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * This will store the fetched sampling config.
 */
@Singleton
class CurrentSamplingConfig @Inject constructor(
    private val configPref: SamplingConfigPreferenceProvider,
    private val fleetSamplingSettings: FleetSamplingSettings,
) {
    private val debuggingResolutionMetric = Reporting.report()
        .stateTracker<Resolution>(name = "debugging.resolution", aggregations = listOf(StateAgg.LATEST_VALUE))
    private val monitoringResolutionMetric = Reporting.report()
        .stateTracker<Resolution>(name = "monitoring.resolution", aggregations = listOf(StateAgg.LATEST_VALUE))
    private val loggingResolutionMetric = Reporting.report()
        .stateTracker<Resolution>(name = "logging.resolution", aggregations = listOf(StateAgg.LATEST_VALUE))
    private val sessionsResolutionMetric = Reporting.report()
        .stateTracker<Resolution>(name = "sessions.resolution", aggregations = listOf(StateAgg.LATEST_VALUE))
    private val cachedProperty = CachedAsyncProperty {
        configPref.get()
    }
    private val revision = MutableStateFlow(0)

    suspend fun get(): SamplingConfig = cachedProperty.get()

    fun asFlow(): Flow<SamplingConfig> = revision.map { get() }

    suspend fun update(newConfig: SamplingConfig) {
        updateMetrics(newConfig)

        val existingConfig = get()
        if (newConfig != existingConfig) {
            Logger.d("CurrentSamplingConfig...changed: $newConfig")
            configPref.set(newConfig)
            cachedProperty.invalidate()
            revision.update { it + 1 }
        }
    }

    fun updateMetrics(newConfig: SamplingConfig) {
        if (fleetSamplingSettings.loggingActive) loggingResolutionMetric.state(newConfig.loggingResolution)
        if (fleetSamplingSettings.debuggingActive) debuggingResolutionMetric.state(newConfig.debuggingResolution)
        if (fleetSamplingSettings.monitoringActive) {
            monitoringResolutionMetric.state(newConfig.monitoringResolution)
            // There is no sampling.sessions_active setting - sessions are part of the monitoring aspect
            // server-side.
            sessionsResolutionMetric.state(newConfig.sessionsResolution)
        }
    }
}
