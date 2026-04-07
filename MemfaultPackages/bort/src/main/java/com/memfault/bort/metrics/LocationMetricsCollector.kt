package com.memfault.bort.metrics

import com.memfault.bort.boot.LinuxBootId
import com.memfault.bort.process.ProcessExecutor
import com.memfault.bort.reporting.NumericAgg.LATEST_VALUE
import com.memfault.bort.reporting.NumericAgg.MAX
import com.memfault.bort.reporting.NumericAgg.MEAN
import com.memfault.bort.reporting.NumericAgg.MIN
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.settings.LocationSettings
import com.memfault.bort.settings.SettingsFlow
import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

fun interface CollectLocationDumpsys : suspend () -> GnssKpiMetrics?

@ContributesBinding(SingletonComponent::class)
class RealCollectLocationDumpsys @Inject constructor(
    private val processExecutor: ProcessExecutor,
    private val locationSettings: LocationSettings,
) : CollectLocationDumpsys {
    override suspend fun invoke(): GnssKpiMetrics? {
        val result = withTimeoutOrNull(locationSettings.commandTimeout) {
            processExecutor.execute(listOf("dumpsys", "location")) { inputStream ->
                inputStream.bufferedReader().use { parseLocationDumpsys(it) }
            }
        }
        return when (result) {
            null -> {
                Logger.w("location dumpsys timed out or process failed")
                null
            }
            is LocationDumpsysResult.NoMetricsFound -> {
                Logger.d("location dumpsys ran but found no GNSS metrics blocks")
                null
            }
            is LocationDumpsysResult.Success -> result.metrics
        }
    }
}

@ContributesMultibinding(scope = SingletonComponent::class)
class LocationMetricsCollector @Inject constructor(
    private val collectLocationDumpsys: CollectLocationDumpsys,
    private val locationSettings: LocationSettings,
    private val settingsFlow: SettingsFlow,
    private val gnssMetricsStorage: GnssMetricsStorage,
    private val readBootId: LinuxBootId,
) : MetricCollector {
    private val report = Reporting.report()

    override fun onChanged(): Flow<Unit> = settingsFlow.settings
        .map { it.locationSettings.dataSourceEnabled }
        .distinctUntilChanged()
        .map { }

    override suspend fun collect() {
        if (!locationSettings.dataSourceEnabled) return
        val metrics = try {
            collectLocationDumpsys()
        } catch (e: Exception) {
            Logger.w("Error collecting location dumpsys metrics", e)
            return
        } ?: return

        metrics.locationFailurePct?.let {
            report.distribution(METRIC_LOCATION_FAILURE_PCT, listOf(MEAN, MIN, MAX)).record(it)
        }
        metrics.ttffMeanSec?.let {
            report.distribution(METRIC_TTFF_MEAN_SEC, listOf(MEAN, MIN, MAX)).record(it)
        }
        metrics.ttffStddevSec?.let {
            report.distribution(METRIC_TTFF_STDDEV_SEC, listOf(MEAN)).record(it)
        }
        metrics.positionAccuracyMeanM?.let {
            report.distribution(METRIC_POSITION_ACCURACY_MEAN_M, listOf(MEAN, MIN, MAX)).record(it)
        }
        metrics.positionAccuracyStddevM?.let {
            report.distribution(METRIC_POSITION_ACCURACY_STDDEV_M, listOf(MEAN)).record(it)
        }
        metrics.cn0Top4MeanDbHz?.let {
            report.distribution(METRIC_CN0_TOP4_MEAN_DBHZ, listOf(MEAN, MIN, MAX)).record(it)
        }
        metrics.cn0Top4StddevDbHz?.let {
            report.distribution(METRIC_CN0_TOP4_STDDEV_DBHZ, listOf(MEAN)).record(it)
        }
        metrics.l5Cn0Top4MeanDbHz?.let {
            report.distribution(METRIC_L5_CN0_TOP4_MEAN_DBHZ, listOf(MEAN, MIN, MAX)).record(it)
        }
        metrics.l5Cn0Top4StddevDbHz?.let {
            report.distribution(METRIC_L5_CN0_TOP4_STDDEV_DBHZ, listOf(MEAN)).record(it)
        }
        val svRatio = metrics.svUsedInFixCount?.toDouble()
            ?.let { used ->
                metrics.svTotalCount?.toDouble()?.let { total ->
                    if (used > 0 &&
                        total > 0
                    ) {
                        used / total
                    } else {
                        null
                    }
                }
            }
        svRatio?.let {
            report.distribution(METRIC_SV_USED_IN_FIX_RATIO, listOf(MEAN, MIN, MAX)).record(it)
        }
        val svL5Ratio = metrics.svL5UsedInFixCount?.toDouble()
            ?.let { used ->
                metrics.svL5TotalCount?.toDouble()?.let { total ->
                    if (used > 0 &&
                        total > 0
                    ) {
                        used / total
                    } else {
                        null
                    }
                }
            }
        svL5Ratio?.let {
            report.distribution(METRIC_SV_L5_USED_IN_FIX_RATIO, listOf(MEAN, MIN, MAX)).record(it)
        }

        // Power Metrics block, these are cumulative. We store state and compute deltas since the last call.
        val deltas = gnssMetricsStorage.update(metrics, readBootId())
        deltas.cn0AboveThresholdTimeMin?.let {
            report.distribution(METRIC_CN0_ABOVE_20_DBHZ_TIME_MIN, listOf(LATEST_VALUE)).record(it)
        }
        deltas.cn0BelowThresholdTimeMin?.let {
            report.distribution(METRIC_CN0_BELOW_20_DBHZ_TIME_MIN, listOf(LATEST_VALUE)).record(it)
        }
        deltas.energyConsumedMah?.let {
            report.distribution(METRIC_ENERGY_CONSUMED_MAH, listOf(LATEST_VALUE)).record(it)
        }
    }

    companion object {
        const val METRIC_LOCATION_FAILURE_PCT = "gnss_location_failure_pct"
        const val METRIC_TTFF_MEAN_SEC = "gnss_ttff_mean_sec"
        const val METRIC_TTFF_STDDEV_SEC = "gnss_ttff_stddev_sec"
        const val METRIC_POSITION_ACCURACY_MEAN_M = "gnss_position_accuracy_mean_m"
        const val METRIC_POSITION_ACCURACY_STDDEV_M = "gnss_position_accuracy_stddev_m"
        const val METRIC_CN0_TOP4_MEAN_DBHZ = "gnss_cn0_top4_mean_dbhz"
        const val METRIC_CN0_TOP4_STDDEV_DBHZ = "gnss_cn0_top4_stddev_dbhz"
        const val METRIC_L5_CN0_TOP4_MEAN_DBHZ = "gnss_l5_cn0_top4_mean_dbhz"
        const val METRIC_L5_CN0_TOP4_STDDEV_DBHZ = "gnss_l5_cn0_top4_stddev_dbhz"
        const val METRIC_SV_USED_IN_FIX_RATIO = "gnss_sv_used_in_fix_ratio"
        const val METRIC_SV_L5_USED_IN_FIX_RATIO = "gnss_sv_l5_used_in_fix_ratio"
        const val METRIC_CN0_ABOVE_20_DBHZ_TIME_MIN = "gnss_cn0_above_20_dbhz_time_min"
        const val METRIC_CN0_BELOW_20_DBHZ_TIME_MIN = "gnss_cn0_below_20_dbhz_time_min"
        const val METRIC_ENERGY_CONSUMED_MAH = "gnss_energy_consumed_mah"
    }
}
