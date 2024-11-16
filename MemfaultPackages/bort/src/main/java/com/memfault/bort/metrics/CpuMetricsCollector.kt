package com.memfault.bort.metrics

import android.content.SharedPreferences
import com.memfault.bort.DumpsterClient
import com.memfault.bort.metrics.CpuUsage.Companion.percentUsage
import com.memfault.bort.metrics.CpuUsage.Companion.totalTicks
import com.memfault.bort.reporting.NumericAgg.MEAN
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.SerializedCachedPreferenceKeyProvider
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * We store the previous CPU readings, so that we can compare the current readings against them to see the difference.
 */
@ContributesMultibinding(scope = SingletonComponent::class)
class CpuMetricsCollector @Inject constructor(
    private val dumpsterClient: DumpsterClient,
    private val cpuUsageStorage: CpuUsageStorage,
    private val cpuMetricsParser: CpuMetricsParser,
    private val cpuMetricReporter: CpuMetricReporter,
) : MetricCollector {
    override suspend fun collect() {
        val procStat = dumpsterClient.getProcStat()
        if (procStat == null) {
            Logger.i("couldn't get procstat")
            return
        }
        val cpuMetrics = cpuMetricsParser.parseProcStat(procStat) ?: return
        val previous = cpuUsageStorage.state
        cpuUsageStorage.state = cpuMetrics
        val sinceLast = cpuMetrics.diffFromPrevious(previous)
        sinceLast.percentUsage()?.let { percentUsage ->
            cpuMetricReporter.reportUsage(percentUsage)
        }
    }
}

interface CpuUsageStorage {
    var state: CpuUsage
}

@Singleton
@ContributesBinding(SingletonComponent::class, boundType = CpuUsageStorage::class)
class ReaCpuUsageStorage @Inject constructor(
    sharedPreferences: SharedPreferences,
) : CpuUsageStorage, SerializedCachedPreferenceKeyProvider<CpuUsage>(
    sharedPreferences,
    CpuUsage.EMPTY,
    CpuUsage.serializer(),
    "CPU_USAGE",
)

fun CpuUsage.diffFromPrevious(previous: CpuUsage): CpuUsage = if (this.totalTicks() < previous.totalTicks()) {
    // Likely rebooted - don't subtract previous readings
    this
} else {
    CpuUsage(
        ticksUser = this.ticksUser - previous.ticksUser,
        ticksNice = this.ticksNice - previous.ticksNice,
        ticksSystem = this.ticksSystem - previous.ticksSystem,
        ticksIdle = this.ticksIdle - previous.ticksIdle,
        ticksIoWait = this.ticksIoWait - previous.ticksIoWait,
        ticksIrq = this.ticksIrq - previous.ticksIrq,
        ticksSoftIrq = this.ticksSoftIrq - previous.ticksSoftIrq,
    )
}

interface CpuMetricReporter {
    fun reportUsage(usagePercent: Double)
}

@ContributesBinding(SingletonComponent::class)
class RealCpuMetricReporter @Inject constructor() : CpuMetricReporter {
    override fun reportUsage(usagePercent: Double) {
        CPU_METRIC.record(usagePercent)
    }

    companion object {
        const val METRIC_KEY_CPU_USAGE = "cpu_usage_pct"
        private val CPU_METRIC = Reporting.report().distribution(METRIC_KEY_CPU_USAGE, aggregations = listOf(MEAN))
    }
}
