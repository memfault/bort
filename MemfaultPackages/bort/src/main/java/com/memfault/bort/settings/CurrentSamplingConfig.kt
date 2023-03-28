package com.memfault.bort.settings

import com.memfault.bort.CachedAsyncProperty
import com.memfault.bort.clientserver.MarFileHoldingArea
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.reporting.StateAgg
import com.memfault.bort.shared.Logger
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * This will store the fetched sampling config. But for now - it will just return the default config (until we are sure
 * that the config aspects from the server are fully defined).
 */
@Singleton
class CurrentSamplingConfig @Inject constructor(
    private val configPref: SamplingConfigPreferenceProvider,
    private val marFileHoldingArea: Provider<MarFileHoldingArea>,
    private val useMarUpload: UseMarUpload,
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

    suspend fun update(config: SamplingConfig) {
        updateMetrics()

        if (config == get()) return
        Logger.d("CurrentSamplingConfig...changed: $config")
        configPref.set(config)
        cachedProperty.invalidate()
        handleSamplingConfigChange(config)
    }

    private suspend fun handleSamplingConfigChange(newConfig: SamplingConfig) {
        if (useMarUpload()) {
            marFileHoldingArea.get().handleSamplingConfigChange(newConfig)
        }
    }

    suspend fun updateMetrics() {
        if (fleetSamplingSettings.loggingActive) loggingResolutionMetric.state(get().loggingResolution)
        if (fleetSamplingSettings.debuggingActive) debuggingResolutionMetric.state(get().debuggingResolution)
        if (fleetSamplingSettings.monitoringActive) monitoringResolutionMetric.state(get().monitoringResolution)
    }
}
