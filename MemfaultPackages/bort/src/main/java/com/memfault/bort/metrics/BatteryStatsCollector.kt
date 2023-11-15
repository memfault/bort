package com.memfault.bort.metrics

import android.os.RemoteException
import com.memfault.bort.settings.BatteryStatsSettings
import com.memfault.bort.shared.Logger
import javax.inject.Inject
import kotlin.time.Duration

class BatteryStatsCollector @Inject constructor(
    private val batteryStatsHistoryCollector: BatteryStatsHistoryCollector,
    private val batterystatsSummaryCollector: BatterystatsSummaryCollector,
    private val settings: BatteryStatsSettings,
    private val metrics: BuiltinMetricsStore,
) {
    suspend fun collect(heartbeatInterval: Duration): BatteryStatsResult {
        if (!settings.dataSourceEnabled) return BatteryStatsResult.EMPTY

        // The batteryStatsHistoryCollector will use the NEXT time from the previous run and use that as starting
        // point for the data to collect. In practice, this roughly matches the start of the current heartbeat period.
        // But, in case that got screwy for some reason, impose a somewhat arbitrary limit on how much batterystats data
        // we collect, because the history can grow *very* large. In the backend, any extra data before it, will get
        // clipped when aggregating, so it doesn't matter if there's more.
        val batteryStatsLimit = heartbeatInterval * 2

        val historyResult = try {
            batteryStatsHistoryCollector.collect(limit = batteryStatsLimit)
        } catch (e: RemoteException) {
            Logger.w("Unable to connect to ReporterService to run batterystats")
            return BatteryStatsResult.EMPTY
        } catch (e: Exception) {
            Logger.e("Failed to collect batterystats", mapOf(), e)
            metrics.increment(BATTERYSTATS_FAILED)
            return BatteryStatsResult.EMPTY
        }

        // Summary is only collected if we're using HRt, and successfully collected history.
        val summaryResult = if (settings.useHighResTelemetry && settings.collectSummary) {
            try {
                batterystatsSummaryCollector.collectSummaryCheckin()
            } catch (e: RemoteException) {
                Logger.w("Unable to connect to ReporterService to run batterystats")
                BatteryStatsResult.EMPTY
            }
        } else {
            BatteryStatsResult.EMPTY
        }

        return BatteryStatsResult(
            batteryStatsFileToUpload = historyResult.batteryStatsFileToUpload,
            batteryStatsHrt = historyResult.batteryStatsHrt + summaryResult.batteryStatsHrt,
            aggregatedMetrics = historyResult.aggregatedMetrics + summaryResult.aggregatedMetrics,
        )
    }
}
