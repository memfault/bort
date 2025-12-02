package com.memfault.bort.settings

import com.memfault.bort.CachedAsyncProperty
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.reporting.StateAgg
import com.memfault.bort.shared.Logger
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
    private val cachedProperty = CachedAsyncProperty {
        configPref.get()
    }

    suspend fun get(): SamplingConfig = cachedProperty.get()

    suspend fun update(newConfig: SamplingConfig) {
        updateMetrics(newConfig)

        val existingConfig = get()
        if (newConfig != existingConfig) {
            Logger.d("CurrentSamplingConfig...changed: $newConfig")
            configPref.set(newConfig)
            cachedProperty.invalidate()
        }
    }

    fun updateMetrics(newConfig: SamplingConfig) {
        if (fleetSamplingSettings.loggingActive) loggingResolutionMetric.state(newConfig.loggingResolution)
        if (fleetSamplingSettings.debuggingActive) debuggingResolutionMetric.state(newConfig.debuggingResolution)
        if (fleetSamplingSettings.monitoringActive) monitoringResolutionMetric.state(newConfig.monitoringResolution)
    }
}
