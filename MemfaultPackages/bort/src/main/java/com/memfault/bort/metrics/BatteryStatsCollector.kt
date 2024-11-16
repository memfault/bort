package com.memfault.bort.metrics

import android.os.RemoteException
import com.memfault.bort.settings.BatteryStatsSettings
import com.memfault.bort.shared.Logger
import com.memfault.bort.time.BaseLinuxBootRelativeTime
import com.memfault.bort.time.CombinedTime
import javax.inject.Inject

class BatteryStatsCollector @Inject constructor(
    private val batteryStatsHistoryCollector: BatteryStatsHistoryCollector,
    private val batterystatsSummaryCollector: BatterystatsSummaryCollector,
    private val settings: BatteryStatsSettings,
    private val metrics: BuiltinMetricsStore,
) {
    suspend fun collect(
        collectionTime: CombinedTime,
        lastHeartbeatUptime: BaseLinuxBootRelativeTime,
    ): BatteryStatsResult {
        if (!settings.dataSourceEnabled) return BatteryStatsResult.EMPTY

        val historyResult = try {
            batteryStatsHistoryCollector.collect(
                collectionTime = collectionTime,
                lastHeartbeatUptime = lastHeartbeatUptime,
            )
        } catch (e: RemoteException) {
            Logger.w("Unable to connect to ReporterService to run batterystats")
            BatteryStatsResult.EMPTY
        } catch (e: Exception) {
            Logger.e("Failed to collect batterystats history", mapOf(), e)
            metrics.increment(BATTERYSTATS_FAILED)
            BatteryStatsResult.EMPTY
        }

        // Summary is only collected if we're using HRT, and successfully collected history.
        val summaryResult = if (settings.useHighResTelemetry && settings.collectSummary) {
            try {
                batterystatsSummaryCollector.collectSummaryCheckin()
            } catch (e: RemoteException) {
                Logger.w("Unable to connect to ReporterService to run batterystats")
                BatteryStatsResult.EMPTY
            } catch (e: Exception) {
                Logger.e("Failed to collect batterystats summary", mapOf(), e)
                metrics.increment(BATTERYSTATS_FAILED)
                BatteryStatsResult.EMPTY
            }
        } else {
            BatteryStatsResult.EMPTY
        }

        return BatteryStatsResult(
            batteryStatsFileToUpload = historyResult.batteryStatsFileToUpload,
            batteryStatsHrt = historyResult.batteryStatsHrt + summaryResult.batteryStatsHrt,
            aggregatedMetrics = historyResult.aggregatedMetrics + summaryResult.aggregatedMetrics,
            internalAggregatedMetrics = historyResult.internalAggregatedMetrics +
                summaryResult.internalAggregatedMetrics,
        )
    }
}
