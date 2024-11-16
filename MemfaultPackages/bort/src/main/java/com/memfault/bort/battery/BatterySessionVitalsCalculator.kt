package com.memfault.bort.battery

import com.memfault.bort.metrics.database.CalculateDerivedAggregations
import com.memfault.bort.metrics.database.DerivedAggregation
import com.memfault.bort.reporting.DataType
import com.memfault.bort.reporting.MetricType
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

const val BATTERY_DISCHARGE_DURATION_METRIC = "battery_discharge_duration_ms"

const val BATTERY_SOC_DROP_METRIC = "battery_soc_pct_drop"

/**
 * Convert the [BATTERY_LEVEL_METRIC] and [BATTERY_CHARGING_METRIC] to the "battery_soc_pct_drop" and
 * "battery_discharge_duration_ms" device vitals.
 */
@ContributesMultibinding(SingletonComponent::class)
class BatterySessionVitalsCalculator @Inject constructor() : CalculateDerivedAggregations {
    override fun calculate(
        startTimestampMs: Long,
        endTimestampMs: Long,
        metrics: Map<String, JsonPrimitive>,
        internalMetrics: Map<String, JsonPrimitive>,
    ): List<DerivedAggregation> {
        val batteryDrop = internalMetrics["${BATTERY_LEVEL_METRIC}_drop"]?.doubleOrNull

        // For legacy reasons, boolean states are represented as 1 and 0 instead of true and false.
        val dischargeTotalSeconds = internalMetrics["${BATTERY_CHARGING_METRIC}_0.total_secs"]?.doubleOrNull

        // We only want to record both battery device vitals metrics or none of them. If the discharge duration is
        // 0 seconds, ignore the result as well.
        if (batteryDrop == null || dischargeTotalSeconds == null || dischargeTotalSeconds < 1.0) {
            return emptyList()
        }

        val dischargeTotalMs = dischargeTotalSeconds.seconds.inWholeMilliseconds.toDouble()

        return listOf(
            DerivedAggregation.create(
                metricName = BATTERY_SOC_DROP_METRIC,
                metricValue = batteryDrop,
                metricType = MetricType.GAUGE,
                dataType = DataType.DOUBLE,
                collectionTimeMs = endTimestampMs,
                internal = false,
            ),
            DerivedAggregation.create(
                metricName = BATTERY_DISCHARGE_DURATION_METRIC,
                metricValue = dischargeTotalMs,
                metricType = MetricType.GAUGE,
                dataType = DataType.DOUBLE,
                collectionTimeMs = endTimestampMs,
                internal = false,
            ),
        )
    }
}
