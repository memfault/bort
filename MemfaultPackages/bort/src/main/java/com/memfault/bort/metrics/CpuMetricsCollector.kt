package com.memfault.bort.metrics

import android.content.SharedPreferences
import com.memfault.bort.DumpsterClient
import com.memfault.bort.PackageManagerClient
import com.memfault.bort.boot.LinuxBootId
import com.memfault.bort.metrics.CpuUsage.Companion.percentUsage
import com.memfault.bort.metrics.CpuUsage.Companion.totalTicks
import com.memfault.bort.metrics.ProcessUsage.Companion.percentUsage
import com.memfault.bort.reporting.NumericAgg
import com.memfault.bort.reporting.NumericAgg.MEAN
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.settings.MetricsSettings
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
open class CpuMetricsCollector @Inject constructor(
    private val dumpsterClient: DumpsterClient,
    private val cpuUsageStorage: CpuUsageStorage,
    private val cpuMetricsParser: CpuMetricsParser,
    private val cpuMetricReporter: CpuMetricReporter,
    private val metricsSettings: MetricsSettings,
    private val packageManagerClient: PackageManagerClient,
) : MetricCollector {
    override suspend fun collect() {
        val procStat = dumpsterClient.getProcStat()
        if (procStat == null) {
            Logger.i("couldn't get procstat")
            return
        }

        val procPidStat = dumpsterClient.getProcPidStat()
        if (procPidStat == null) {
            Logger.i("couldn't get procPidStat")
            // We can still report the overall CPU usage, so we continue
        }

        val packageManagerReport = packageManagerClient.getPackageManagerReport()
        val cpuMetrics = cpuMetricsParser.parseCpuUsage(procStat, procPidStat, packageManagerReport) ?: return
        val previous = cpuUsageStorage.state
        cpuUsageStorage.state = cpuMetrics
        val sinceLast = cpuMetrics.diffFromPrevious(previous)
        sinceLast.percentUsage()?.let { percentUsage ->
            cpuMetricReporter.reportUsage(percentUsage)
        }

        sinceLast.perProcessUsage.toList()
            .sortedBy { (_, usage) -> -usage.percentUsage(sinceLast) }
            .filterIndexed { index, (_, usage) ->
                index < metricsSettings.cpuProcessLimitTopN ||
                    usage.processName in metricsSettings.cpuInterestingProcesses
            }
            .forEach { (process, usage) ->
                val percentUsage = usage.percentUsage(sinceLast)
                val isInteresting = usage.processName in metricsSettings.cpuInterestingProcesses
                if (percentUsage >= metricsSettings.cpuProcessReportingThreshold || isInteresting) {
                    cpuMetricReporter.reportProcessUsage(
                        process,
                        percentUsage,
                        createMetric = isInteresting || metricsSettings.alwaysCreateCpuProcessMetrics,
                    )
                }
            }
    }
}

interface CpuUsageStorage {
    var state: CpuUsage
}

@Singleton
@ContributesBinding(SingletonComponent::class, boundType = CpuUsageStorage::class)
class RealCpuUsageStorage @Inject constructor(
    sharedPreferences: SharedPreferences,
    readBootId: LinuxBootId,
) : CpuUsageStorage, SerializedCachedPreferenceKeyProvider<CpuUsage>(
    sharedPreferences,
    CpuUsage.EMPTY.copy(bootId = readBootId()),
    CpuUsage.serializer(),
    "CPU_USAGE",
)

fun CpuUsage.diffFromPrevious(previous: CpuUsage): CpuUsage = if (this.totalTicks() < previous.totalTicks() ||
    this.bootId != previous.bootId
) {
    // Likely rebooted - don't subtract previous readings
    this
} else {
    val perProcessUsageInPeriod = perProcessUsage.mapNotNull { (key, currentUsage) ->
        val previousUsage = previous.perProcessUsage[key] ?: return@mapNotNull null
        key to currentUsage.copy(
            stime = currentUsage.stime - previousUsage.stime,
            utime = currentUsage.utime - previousUsage.utime,
        )
    }.toMap()

    CpuUsage(
        ticksUser = this.ticksUser - previous.ticksUser,
        ticksNice = this.ticksNice - previous.ticksNice,
        ticksSystem = this.ticksSystem - previous.ticksSystem,
        ticksIdle = this.ticksIdle - previous.ticksIdle,
        ticksIoWait = this.ticksIoWait - previous.ticksIoWait,
        ticksIrq = this.ticksIrq - previous.ticksIrq,
        ticksSoftIrq = this.ticksSoftIrq - previous.ticksSoftIrq,
        perProcessUsage = perProcessUsageInPeriod,
        bootId = this.bootId,
    )
}

interface CpuMetricReporter {
    fun reportUsage(usagePercent: Double)
    fun reportProcessUsage(process: String, percentUsage: Double, createMetric: Boolean)
}

@ContributesBinding(SingletonComponent::class)
class RealCpuMetricReporter @Inject constructor() : CpuMetricReporter {
    override fun reportUsage(usagePercent: Double) {
        CPU_METRIC.record(usagePercent)
    }

    override fun reportProcessUsage(process: String, percentUsage: Double, createMetric: Boolean) {
        val aggregations = if (createMetric) {
            listOf(MEAN)
        } else {
            emptyList()
        }
        cpuUsageMetricForProcess(process, aggregations).record(percentUsage)
    }

    private fun cpuUsageMetricForProcess(process: String, aggregations: List<NumericAgg>) =
        Reporting.report().distribution("cpu_usage_${process}_pct", aggregations)

    companion object {
        const val METRIC_KEY_CPU_USAGE = "cpu_usage_pct"
        private val CPU_METRIC = Reporting.report().distribution(METRIC_KEY_CPU_USAGE, aggregations = listOf(MEAN))
    }
}
