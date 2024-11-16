package com.memfault.bort.metrics

import android.app.ActivityManager
import android.app.ActivityManager.MemoryInfo
import com.memfault.bort.metrics.BatterystatsSummaryCollector.Companion.DP
import com.memfault.bort.reporting.NumericAgg.MAX
import com.memfault.bort.reporting.NumericAgg.MEAN
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.settings.MetricsSettings
import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

@ContributesMultibinding(scope = SingletonComponent::class)
class MemoryMetricsCollector @Inject constructor(
    private val activityManager: ActivityManager,
    private val metricsSettings: MetricsSettings,
) : MetricCollector {
    companion object {
        private val MEMORY_METRIC = Reporting
            .report()
            .distribution(name = "memory_pct", aggregations = listOf(MEAN, MAX))
    }

    override suspend fun collect() {
        if (!metricsSettings.collectMemory) {
            return
        }
        val memInfo = MemoryInfo()
        try {
            activityManager.getMemoryInfo(memInfo)
            val usedPercent = (memInfo.totalMem - memInfo.availMem).toDouble() / memInfo.totalMem.toDouble() * 100
            MEMORY_METRIC.record(usedPercent.roundTo(DP))
        } catch (e: Exception) {
            Logger.w("MemoryMetricsCollector: Couldn't get meminfo")
        }
    }
}
