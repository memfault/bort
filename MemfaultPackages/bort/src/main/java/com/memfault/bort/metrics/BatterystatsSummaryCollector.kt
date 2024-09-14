package com.memfault.bort.metrics

import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.metrics.BatterystatsSummaryCollector.Companion.DP
import com.memfault.bort.metrics.HighResTelemetry.DataType.DoubleType
import com.memfault.bort.metrics.HighResTelemetry.Datum
import com.memfault.bort.metrics.HighResTelemetry.MetricType
import com.memfault.bort.metrics.HighResTelemetry.MetricType.Gauge
import com.memfault.bort.metrics.HighResTelemetry.Rollup
import com.memfault.bort.metrics.HighResTelemetry.RollupMetadata
import com.memfault.bort.parsers.BatteryStatsSummaryParser
import com.memfault.bort.parsers.BatteryStatsSummaryParser.BatteryState
import com.memfault.bort.parsers.BatteryStatsSummaryParser.BatteryStatsSummary
import com.memfault.bort.parsers.BatteryStatsSummaryParser.DischargeData
import com.memfault.bort.parsers.BatteryStatsSummaryParser.PowerUseItemData
import com.memfault.bort.parsers.BatteryStatsSummaryParser.PowerUseSummary
import com.memfault.bort.settings.BatteryStatsSettings
import com.memfault.bort.shared.BatteryStatsCommand
import com.memfault.bort.shared.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.float
import java.math.RoundingMode
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

/**
 * We periodically collect a batterystats `--checkin` report, which includes summary metrics describing the period
 * since the last full charge (we do not see these in a `-c --history-start` collection from
 * [BatteryStatsHistoryCollector]). There is no way to request metrics only covering the period since we last collected.
 *
 * To make this data useful, we persist the last result (using [batteryStatsSummaryProvider]) and subtract the values
 * from that to see the delta in all values since we last collected.
 *
 * These metrics are all accrued while [batteryRealtimeMs]/[totalRealtimeMs] increases, including across reboots (when
 * [startCount] increases). When the device comes *off* charge, all stats are reset, and the battery/total timestamps
 * are reset. When this happens, we ignore the previous persisted summary.
 *
 * No metrics are updated while the device is charging.
 *
 * The total power usage of all components summed can be more than the total battery drain - this is because e.g. the
 * screen component can be double-counted per package. This is still useful information to display.
 *
 * Future improvements:
 * - We could potentially run this task whenever the battery starts charging (based on a broadcast?) - as long as we can
 *   safely decouple this from the [MetricsCollectionTask] (this would probably need a separate report for the
 *   metrics to make sense).
 */
class BatterystatsSummaryCollector @Inject constructor(
    private val temporaryFileFactory: TemporaryFileFactory,
    private val runBatteryStats: RunBatteryStats,
    private val settings: BatteryStatsSettings,
    private val batteryStatsSummaryParser: BatteryStatsSummaryParser,
    private val batteryStatsSummaryProvider: BatteryStatsSummaryProvider,
    private val significantAppsProvider: SignificantAppsProvider,
) {
    suspend fun collectSummaryCheckin(): BatteryStatsResult {
        temporaryFileFactory.createTemporaryFile(
            "batterystats",
            suffix = ".txt",
        ).useFile { batteryStatsFile, _ ->
            withContext(Dispatchers.IO) {
                batteryStatsFile.outputStream().use {
                    runBatteryStats.runBatteryStats(
                        it,
                        BatteryStatsCommand(checkin = true),
                        settings.commandTimeout,
                    )
                }
            }
            val summary = batteryStatsSummaryParser.parse(batteryStatsFile)
            if (summary == null) {
                Logger.d("Could not parse batterystats summary")
                return BatteryStatsResult.EMPTY
            }
            val previous = batteryStatsSummaryProvider.get()
            batteryStatsSummaryProvider.set(summary)
            val diff = summary.diffFromPrevious(previous)
            val reportBatteryDuration = diff.batteryRealtimeMs.milliseconds

            val hrt = mutableSetOf<Rollup>()
            val report = mutableMapOf<String, JsonPrimitive>()
            val internalReport = mutableMapOf<String, JsonPrimitive>()

            fun addHrtRollup(
                name: String,
                value: JsonPrimitive,
                internal: Boolean = false,
                metricType: MetricType = Gauge,
            ) {
                val rollup = Rollup(
                    metadata = RollupMetadata(
                        stringKey = name,
                        metricType = metricType,
                        dataType = DoubleType,
                        internal = internal,
                    ),
                    data = listOf(Datum(t = summary.timestampMs, value = value)),
                )
                hrt.add(rollup)
            }

            // Screen off drain: old metric (to be deleted once we are using the new one, at some point)
            if (diff.screenOffRealtimeMs > 0 && diff.screenOffDrainPercent != null) {
                val screenOffBatteryDrainPerHour =
                    JsonPrimitive(diff.screenOffDrainPercent.proRataValuePerHour(diff.screenOffRealtimeMs.milliseconds))
                addHrtRollup(name = SCREEN_OFF_BATTERY_DRAIN_PER_HOUR, value = screenOffBatteryDrainPerHour)
                report[SCREEN_OFF_BATTERY_DRAIN_PER_HOUR] = screenOffBatteryDrainPerHour
            }

            // Screen off drain: new metrics
            if (diff.screenOffDrainPercent != null) {
                val screenOffDischargeDurationMs = JsonPrimitive(diff.screenOffRealtimeMs)
                addHrtRollup(name = SCREEN_OFF_DISCHARGE_DURATION_MS, value = screenOffDischargeDurationMs)
                report[SCREEN_OFF_DISCHARGE_DURATION_MS] = screenOffDischargeDurationMs
                val screenOffPercentDrop = JsonPrimitive(diff.screenOffDrainPercent.roundTo(DP))
                addHrtRollup(name = SCREEN_OFF_SOC_PCT_DROP, value = screenOffPercentDrop)
                report[SCREEN_OFF_SOC_PCT_DROP] = screenOffPercentDrop
            }

            // Screen on drain: old metric (to be deleted once we are using the new one, at some point)
            val screenOnRealtimeMs = diff.batteryRealtimeMs - diff.screenOffRealtimeMs
            if (screenOnRealtimeMs > 0 && diff.screenOnDrainPercent != null) {
                val screenOnBatteryDrainPerHour =
                    JsonPrimitive(diff.screenOnDrainPercent.proRataValuePerHour(screenOnRealtimeMs.milliseconds))
                addHrtRollup(name = SCREEN_ON_BATTERY_DRAIN_PER_HOUR, value = screenOnBatteryDrainPerHour)
                report[SCREEN_ON_BATTERY_DRAIN_PER_HOUR] = screenOnBatteryDrainPerHour
            }

            // Screen on drain: new metrics
            if (diff.screenOnDrainPercent != null) {
                val screenOnDischargeDurationMs = JsonPrimitive(screenOnRealtimeMs)
                addHrtRollup(name = SCREEN_ON_DISCHARGE_DURATION_MS, value = screenOnDischargeDurationMs)
                report[SCREEN_ON_DISCHARGE_DURATION_MS] = screenOnDischargeDurationMs
                val screenOnPercentDrop = JsonPrimitive(diff.screenOnDrainPercent.roundTo(DP))
                addHrtRollup(name = SCREEN_ON_SOC_PCT_DROP, value = screenOnPercentDrop)
                report[SCREEN_ON_SOC_PCT_DROP] = screenOnPercentDrop
            }

            if (summary.batteryState.estimatedBatteryCapacity > 0) {
                val estimatedCapacityMah = JsonPrimitive(summary.batteryState.estimatedBatteryCapacity)
                addHrtRollup(name = ESTIMATED_BATTERY_CAPACITY, value = estimatedCapacityMah)
                report[ESTIMATED_BATTERY_CAPACITY] = estimatedCapacityMah
            }

            if (summary.powerUseSummary.originalBatteryCapacity > 0 &&
                summary.batteryState.estimatedBatteryCapacity > 0
            ) {
                val originalBatteryCapacityMah = JsonPrimitive(summary.powerUseSummary.originalBatteryCapacity)
                addHrtRollup(
                    name = ORIGINAL_BATTERY_CAPACITY,
                    value = originalBatteryCapacityMah,
                )
                report[ORIGINAL_BATTERY_CAPACITY] = originalBatteryCapacityMah

                val computedCapacity = JsonPrimitive(summary.powerUseSummary.computedCapacityMah)

                /*
                Internal because it may differ from the ESTIMATED_BATTERY_CAPACITY metric we are already
                collecting. Long term we should compare these values and determine if they are the same or
                one is more accurate
                */
                addHrtRollup(
                    name = COMPUTED_BATTERY_CAPACITY,
                    value = computedCapacity,
                    internal = true,
                )

                val minCapacityMah = JsonPrimitive(summary.powerUseSummary.minCapacityMah)
                addHrtRollup(name = MIN_BATTERY_CAPACITY, value = minCapacityMah)
                report[MIN_BATTERY_CAPACITY] = minCapacityMah

                val maxCapacityMah = JsonPrimitive(summary.powerUseSummary.maxCapacityMah)
                addHrtRollup(name = MAX_BATTERY_CAPACITY, value = maxCapacityMah)
                report[MAX_BATTERY_CAPACITY] = maxCapacityMah

                val estimatedCapacityMah = JsonPrimitive(summary.batteryState.estimatedBatteryCapacity)
                val batteryStateOfHealth = JsonPrimitive(
                    ((estimatedCapacityMah.float / originalBatteryCapacityMah.float) * 100),
                )
                addHrtRollup(name = BATTERY_STATE_OF_HEALTH, value = batteryStateOfHealth)
                report[BATTERY_STATE_OF_HEALTH] = batteryStateOfHealth
            }

            if (reportBatteryDuration.isPositive()) {
                val componentMetricsApps = settings.componentMetrics
                val significantApps = significantAppsProvider.apps()

                // Per-component power usage summary (only HRT, because we can't store per-app metrics).
                diff.componentPowerUse.forEach { component ->
                    val componentName = component.name
                    val componentDrainPercent = component.totalPowerPercent.proRataValuePerHour(reportBatteryDuration)
                    if (componentDrainPercent > 0) {
                        val componentUsePerHourName = "$COMPONENT_USE_PER_HOUR$componentName"
                        addHrtRollup(
                            name = componentUsePerHourName,
                            value = JsonPrimitive(componentDrainPercent),
                        )

                        componentMetricsApps.firstOrNull { it == componentName }
                            ?.let {
                                val key = "$COMPONENT_USE_PER_HOUR$componentName"
                                report[key] = JsonPrimitive(componentDrainPercent)
                            }

                        significantApps.firstOrNull { it.packageName == componentName }
                            ?.let { match ->
                                val name = match.identifier
                                val key = "$COMPONENT_USE_PER_HOUR$name"
                                if (match.internal) {
                                    internalReport[key] = JsonPrimitive(componentDrainPercent)
                                } else {
                                    report[key] = JsonPrimitive(componentDrainPercent)
                                }
                            }
                    }
                }
            }

            return BatteryStatsResult(
                batteryStatsFileToUpload = null,
                batteryStatsHrt = hrt,
                aggregatedMetrics = report,
                internalAggregatedMetrics = internalReport,
            )
        }
    }

    companion object {
        const val SCREEN_OFF_BATTERY_DRAIN_PER_HOUR = "screen_off_battery_drain_%/hour"
        const val SCREEN_ON_BATTERY_DRAIN_PER_HOUR = "screen_on_battery_drain_%/hour"
        const val COMPONENT_USE_PER_HOUR = "battery_use_%/hour_"
        const val ESTIMATED_BATTERY_CAPACITY = "estimated_battery_capacity_mah"
        const val ORIGINAL_BATTERY_CAPACITY = "original_battery_capacity_mah"
        const val COMPUTED_BATTERY_CAPACITY = "computed_battery_capacity_mah"
        const val MIN_BATTERY_CAPACITY = "min_battery_capacity_mah"
        const val MAX_BATTERY_CAPACITY = "max_battery_capacity_mah"
        const val BATTERY_STATE_OF_HEALTH = "battery_state_of_health_%"
        const val SCREEN_ON_DISCHARGE_DURATION_MS = "battery_screen_on_discharge_duration_ms"
        const val SCREEN_ON_SOC_PCT_DROP = "battery_screen_on_soc_pct_drop"
        const val SCREEN_OFF_DISCHARGE_DURATION_MS = "battery_screen_off_discharge_duration_ms"
        const val SCREEN_OFF_SOC_PCT_DROP = "battery_screen_off_soc_pct_drop"
        const val DP = 2
    }
}

data class ComponentPowerUse(
    val name: String,
    val totalPowerMaH: Double,
    val totalPowerPercent: Double,
)

data class BatteryStatsSummaryResult(
    val batteryRealtimeMs: Long,
    val screenOffRealtimeMs: Long,
    val screenOnDrainPercent: Double?,
    val screenOffDrainPercent: Double?,
    val componentPowerUse: Set<ComponentPowerUse>,
)

fun BatteryStatsSummary.diffFromPrevious(previous: BatteryStatsSummary?): BatteryStatsSummaryResult {
    val diff = if (previous == null) {
        this
    } else if (
        // If battery realtime is lower, we are on a new charge cycle.
        previous.batteryState.batteryRealtimeMs > batteryState.batteryRealtimeMs ||
        // If battery cycle start is higher, we are on a new charge cycle.
        previous.batteryState.startClockTimeMs < batteryState.startClockTimeMs
    ) {
        this
    } else {
        BatteryStatsSummary(
            batteryState = batteryState - previous.batteryState,
            dischargeData = dischargeData - previous.dischargeData,
            powerUseItemData = powerUseItemData - previous.powerUseItemData,
            powerUseSummary = powerUseSummary,
            timestampMs = batteryState.batteryRealtimeMs - previous.batteryState.batteryRealtimeMs,
        )
    }
    val screenOnMah = diff.dischargeData.totalMaH.toDouble() - diff.dischargeData.totalMaHScreenOff.toDouble()
    val screenOnDrainPercent = if (batteryState.estimatedBatteryCapacity > 0) {
        (screenOnMah / batteryState.estimatedBatteryCapacity) * 100
    } else {
        null
    }
    val screenOffDrainPercent = if (batteryState.estimatedBatteryCapacity > 0) {
        (diff.dischargeData.totalMaHScreenOff.toDouble() / batteryState.estimatedBatteryCapacity) * 100
    } else {
        null
    }
    return BatteryStatsSummaryResult(
        batteryRealtimeMs = diff.batteryState.batteryRealtimeMs,
        screenOffRealtimeMs = diff.batteryState.screenOffRealtimeMs,
        screenOnDrainPercent = screenOnDrainPercent,
        screenOffDrainPercent = screenOffDrainPercent,
        componentPowerUse = diff.powerUseItemData
            .filter { it.totalPowerMaH > 0 && batteryState.estimatedBatteryCapacity > 0 }
            .map {
                ComponentPowerUse(
                    name = it.name,
                    totalPowerMaH = it.totalPowerMaH,
                    totalPowerPercent = (it.totalPowerMaH / batteryState.estimatedBatteryCapacity) * 100,
                )
            }
            .toSet(),
    )
}

private operator fun BatteryState.minus(other: BatteryState) = BatteryState(
    batteryRealtimeMs = batteryRealtimeMs - other.batteryRealtimeMs,
    startClockTimeMs = startClockTimeMs - other.startClockTimeMs,
    estimatedBatteryCapacity = estimatedBatteryCapacity,
    screenOffRealtimeMs = screenOffRealtimeMs - other.screenOffRealtimeMs,
)

private operator fun DischargeData.minus(other: DischargeData) = DischargeData(
    totalMaH = totalMaH - other.totalMaH,
    totalMaHScreenOff = totalMaHScreenOff - other.totalMaHScreenOff,
)

private operator fun Set<PowerUseItemData>.minus(other: Set<PowerUseItemData>) = map { newItem ->
    val prevItem = other.find { it.name == newItem.name } ?: return@map newItem
    PowerUseItemData(
        name = newItem.name,
        totalPowerMaH = newItem.totalPowerMaH - prevItem.totalPowerMaH,
    )
}.toSet()

private operator fun PowerUseSummary.minus(other: PowerUseSummary) = PowerUseSummary(
    originalBatteryCapacity = originalBatteryCapacity - other.originalBatteryCapacity,
    computedCapacityMah = computedCapacityMah - other.computedCapacityMah,
    minCapacityMah = minCapacityMah - other.minCapacityMah,
    maxCapacityMah = maxCapacityMah - other.maxCapacityMah,
)

private fun Double.proRataValuePerHour(period: Duration, dp: Int = DP) =
    ((this / period.inWholeMilliseconds.toDouble()) * 1.hours.inWholeMilliseconds.toDouble()).roundTo(dp)

fun Double.roundTo(n: Int): Double {
    return this.toBigDecimal().setScale(n, RoundingMode.HALF_UP).toDouble()
}
