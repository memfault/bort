package com.memfault.bort.metrics

import com.memfault.bort.metrics.custom.ReportType
import com.memfault.bort.metrics.database.CalculateDerivedAggregations
import com.memfault.bort.metrics.database.DerivedAggregation
import com.memfault.bort.reporting.DataType
import com.memfault.bort.reporting.MetricType
import com.memfault.bort.settings.MetricsSettings
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import javax.inject.Inject

/**
 * Just a reminder that this class is used before [AggregateMetricFilter] gets its hands on the names.
 */
@ContributesMultibinding(SingletonComponent::class)
class ThermalDerivedCalculator @Inject constructor(
    private val metricsSettings: MetricsSettings,
) : CalculateDerivedAggregations {
    override fun calculate(
        reportType: ReportType,
        startTimestampMs: Long,
        endTimestampMs: Long,
        metrics: Map<String, JsonPrimitive>,
        internalMetrics: Map<String, JsonPrimitive>,
    ): List<DerivedAggregation> {
        val result = mutableListOf<DerivedAggregation>()

        // Create aggregations across all CPUs
        val cpuMean = metrics.filter { it.key.matches(CPU_MEAN_REGEX) }.mapNotNull { it.value.doubleOrNull }.average()
        if (!cpuMean.isNaN()) {
            result.add(
                DerivedAggregation.create(
                    metricName = "thermal_cpu_c",
                    metricValue = cpuMean,
                    metricType = MetricType.GAUGE,
                    dataType = DataType.DOUBLE,
                    collectionTimeMs = endTimestampMs,
                    internal = false,
                ),
            )
        }
        val cpuMax = metrics.filter { it.key.matches(CPU_MAX_REGEX) }.mapNotNull { it.value.doubleOrNull }.maxOrNull()
        if (cpuMax != null && !cpuMax.isNaN()) {
            result.add(
                DerivedAggregation.create(
                    metricName = "thermal_cpu_c_max",
                    metricValue = cpuMax,
                    metricType = MetricType.GAUGE,
                    dataType = DataType.DOUBLE,
                    collectionTimeMs = endTimestampMs,
                    internal = false,
                ),
            )
        }

        // Create aggregations across all batteries
        val batteryMean = metrics.filter { it.key.matches(BATTERY_MEAN_REGEX) }.mapNotNull { it.value.doubleOrNull }
            .average()
        if (!batteryMean.isNaN()) {
            result.add(
                DerivedAggregation.create(
                    metricName = "thermal_battery_c",
                    metricValue = batteryMean,
                    metricType = MetricType.GAUGE,
                    dataType = DataType.DOUBLE,
                    collectionTimeMs = endTimestampMs,
                    internal = false,
                ),
            )
        }
        val batteryMax = metrics.filter { metric -> metric.key.matches(BATTERY_MAX_REGEX) }
            .mapNotNull { it.value.doubleOrNull }
            .maxOrNull()
        if (batteryMax != null && !batteryMax.isNaN()) {
            result.add(
                DerivedAggregation.create(
                    metricName = "thermal_battery_c_max",
                    metricValue = batteryMax,
                    metricType = MetricType.GAUGE,
                    dataType = DataType.DOUBLE,
                    collectionTimeMs = endTimestampMs,
                    internal = false,
                ),
            )
        }

        if (metricsSettings.thermalCollectLegacyMetrics) {
            val legacyMetricSensorTypes = listOf("cpu", "gpu", "skin", "usb", "amp")
            val aggregations = listOf("min", "mean", "max")
            // For e.g. thermal_cpu_CPU0_c.mean, create an extra metric called temp.cpu_0.mean
            legacyMetricSensorTypes.forEach { sensorType ->
                aggregations.forEach { aggregation ->
                    metrics.toList()
                        .filter { (key, _) -> key.matches(Regex("thermal_${sensorType}_.*_c.$aggregation")) }
                        .sortedBy { (key, _) -> key }
                        .forEachIndexed { index, (_, metric) ->
                            val value = metric.doubleOrNull
                            if (value == null || value.isNaN()) {
                                return@forEachIndexed
                            }
                            result.add(
                                DerivedAggregation.create(
                                    metricName = "temp.${sensorType}_$index.$aggregation",
                                    metricValue = value,
                                    metricType = MetricType.GAUGE,
                                    dataType = DataType.DOUBLE,
                                    collectionTimeMs = endTimestampMs,
                                    internal = false,
                                ),
                            )
                        }
                }
            }
        }

        return result
    }

    companion object {
        private val CPU_MEAN_REGEX = Regex("thermal_cpu_.*.mean")
        private val CPU_MAX_REGEX = Regex("thermal_cpu_.*.max")
        private val BATTERY_MEAN_REGEX = Regex("thermal_battery_.*.mean")
        private val BATTERY_MAX_REGEX = Regex("thermal_battery_.*.max")
    }
}
