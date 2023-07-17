package com.memfault.bort.metrics

import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.metrics.HighResTelemetry.DataType.DoubleType
import com.memfault.bort.metrics.HighResTelemetry.Datum
import com.memfault.bort.metrics.HighResTelemetry.MetricType.Gauge
import com.memfault.bort.metrics.HighResTelemetry.Rollup
import com.memfault.bort.metrics.HighResTelemetry.RollupMetadata
import com.memfault.bort.parsers.BatteryStatsSummaryParser
import com.memfault.bort.parsers.BatteryStatsSummaryParser.BatteryState
import com.memfault.bort.parsers.BatteryStatsSummaryParser.BatteryStatsSummary
import com.memfault.bort.parsers.BatteryStatsSummaryParser.DischargeData
import com.memfault.bort.parsers.BatteryStatsSummaryParser.PowerUseItemData
import com.memfault.bort.settings.BatteryStatsSettings
import com.memfault.bort.shared.BatteryStatsCommand
import com.memfault.bort.shared.Logger
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive

/**
 * We periodically collect a batterystats `--checkin` report, which includes summary metrics describing the period
 * since the last full charge (we do not see these in a `-c --history-start` collection from
 * [BatteryStatsHistoryCollector]). There is no way to request metrics only covering the period since we last collected.
 *
 * To make this data useful, we persist the last result (using [batteryStatsSummaryProvider]) and subtract the values
 * from that to see the delta in all values since we last collected.
 *
 * These metrics are all accrued while [batteryRealtimeMs]/[totalRealtimeMs] increases, including accross reboots (when
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
) {
    suspend fun collectSummaryCheckin(): BatteryStatsResult {
        temporaryFileFactory.createTemporaryFile(
            "batterystats", suffix = ".txt"
        ).useFile { batteryStatsFile, _ ->
            withContext(Dispatchers.IO) {
                batteryStatsFile.outputStream().use {
                    runBatteryStats.runBatteryStats(
                        it, BatteryStatsCommand(checkin = true), settings.commandTimeout
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

            fun addHrtRollup(name: String, value: JsonPrimitive) {
                val rollup = Rollup(
                    metadata = RollupMetadata(
                        stringKey = name,
                        metricType = Gauge,
                        dataType = DoubleType,
                        internal = false,
                    ),
                    data = listOf(Datum(t = summary.timestampMs, value = value)),
                )
                hrt.add(rollup)
            }

            // Screen off drain
            if (diff.screenOffRealtimeMs > 0) {
                val screenOffBatteryDrainPerHour =
                    JsonPrimitive(diff.screenOffDrainPercent.proRataValuePerHour(diff.screenOffRealtimeMs.milliseconds))
                addHrtRollup(name = SCREEN_OFF_BATTERY_DRAIN_PER_HOUR, value = screenOffBatteryDrainPerHour)
                report[SCREEN_OFF_BATTERY_DRAIN_PER_HOUR] = screenOffBatteryDrainPerHour
            }

            // Screen on drain
            val screenOnRealtimeMs = diff.batteryRealtimeMs - diff.screenOffRealtimeMs
            if (screenOnRealtimeMs > 0) {
                val screenOnBatteryDrainPerHour =
                    JsonPrimitive(diff.screenOnDrainPercent.proRataValuePerHour(screenOnRealtimeMs.milliseconds))
                addHrtRollup(name = SCREEN_ON_BATTERY_DRAIN_PER_HOUR, value = screenOnBatteryDrainPerHour)
                report[SCREEN_ON_BATTERY_DRAIN_PER_HOUR] = screenOnBatteryDrainPerHour
            }

            if (summary.batteryState.estimatedBatteryCapacity > 0) {
                val estimatedCapacityMah = JsonPrimitive(summary.batteryState.estimatedBatteryCapacity)
                addHrtRollup(name = ESTIMATED_BATTERY_CAPACITY, value = estimatedCapacityMah)
                report[ESTIMATED_BATTERY_CAPACITY] = estimatedCapacityMah
            }

            if (reportBatteryDuration.isPositive()) {
                // Per-component power usage summary (only HRT, because we can't store per-app metrics).
                diff.componentPowerUse.forEach { component ->
                    val componentDrainPercent = component.totalPowerPercent.proRataValuePerHour(reportBatteryDuration)
                    if (componentDrainPercent > 0) {
                        addHrtRollup(
                            name = "$COMPONENT_USE_PER_HOUR${component.name}",
                            value = JsonPrimitive(componentDrainPercent),
                        )
                    }
                }
            }

            return BatteryStatsResult(
                batteryStatsFileToUpload = null,
                batteryStatsHrt = hrt,
                aggregatedMetrics = report,
            )
        }
    }

    companion object {
        const val SCREEN_OFF_BATTERY_DRAIN_PER_HOUR = "screen_off_battery_drain_%/hour"
        const val SCREEN_ON_BATTERY_DRAIN_PER_HOUR = "screen_on_battery_drain_%/hour"
        const val COMPONENT_USE_PER_HOUR = "battery_use_%/hour_"
        const val ESTIMATED_BATTERY_CAPACITY = "estimated_battery_capacity_mah"
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
    val screenOnDrainPercent: Double,
    val screenOffDrainPercent: Double,
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
            timestampMs = batteryState.batteryRealtimeMs - previous.batteryState.batteryRealtimeMs,
        )
    }
    val screenOnMah = diff.dischargeData.totalMaH.toDouble() - diff.dischargeData.totalMaHScreenOff.toDouble()
    val screenOnDrainPercent = (screenOnMah / batteryState.estimatedBatteryCapacity) * 100
    val screenOffDrainPercent =
        (diff.dischargeData.totalMaHScreenOff.toDouble() / batteryState.estimatedBatteryCapacity) * 100
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
            .toSet()
    )
}

private operator fun BatteryState.minus(other: BatteryState) = BatteryState(
    batteryRealtimeMs = batteryRealtimeMs - other.batteryRealtimeMs,
    startClockTimeMs = startClockTimeMs - other.startClockTimeMs,
    estimatedBatteryCapacity = estimatedBatteryCapacity,
    screenOffRealtimeMs = screenOffRealtimeMs - other.screenOffRealtimeMs
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

private fun Double.proRataValuePerHour(period: Duration, dp: Int = 2) =
    ((this / period.inWholeMilliseconds.toDouble()) * 1.hours.inWholeMilliseconds.toDouble()).roundTo(dp)

fun Double.roundTo(n: Int): Double {
    return "%.${n}f".format(this).toDouble()
}
