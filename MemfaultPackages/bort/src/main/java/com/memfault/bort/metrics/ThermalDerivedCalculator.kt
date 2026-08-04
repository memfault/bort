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
        startUptimeMs: Long,
        endUptimeMs: Long,
    ): List<DerivedAggregation> {
        val result = mutableListOf<DerivedAggregation>()

        fun maxOf(regex: Regex): Double? =
            metrics.filter { it.key.matches(regex) }.mapNotNull { it.value.doubleOrNull }.maxOrNull()
                ?.takeUnless { it.isNaN() }

        // Single max temp per package type
        maxOf(CPU_MAX_REGEX)?.let { result.add(derived("thermal_cpu_c_max", it, endTimestampMs, endUptimeMs)) }
        maxOf(GPU_MAX_REGEX)?.let { result.add(derived("thermal_gpu_c_max", it, endTimestampMs, endUptimeMs)) }
        maxOf(NPU_MAX_REGEX)?.let { result.add(derived("thermal_npu_c_max", it, endTimestampMs, endUptimeMs)) }
        maxOf(SKIN_MAX_REGEX)?.let { result.add(derived("thermal_skin_c_max", it, endTimestampMs, endUptimeMs)) }

        // Single max throttling status per package type
        maxOf(CPU_STATUS_MAX_REGEX)?.let {
            result.add(derived("thermal_status_cpu_max", it, endTimestampMs, endUptimeMs))
        }
        maxOf(GPU_STATUS_MAX_REGEX)?.let {
            result.add(derived("thermal_status_gpu_max", it, endTimestampMs, endUptimeMs))
        }
        maxOf(NPU_STATUS_MAX_REGEX)?.let {
            result.add(derived("thermal_status_npu_max", it, endTimestampMs, endUptimeMs))
        }

        // Create aggregations across all batteries
        val batteryMean = metrics.filter { it.key.matches(BATTERY_MEAN_REGEX) }.mapNotNull { it.value.doubleOrNull }
            .average()
        if (!batteryMean.isNaN()) {
            result.add(derived("thermal_battery_c", batteryMean, endTimestampMs, endUptimeMs))
        }
        maxOf(BATTERY_MAX_REGEX)?.let { result.add(derived("thermal_battery_c_max", it, endTimestampMs, endUptimeMs)) }

        if (metricsSettings.thermalCollectLegacyMetrics) {
            val legacyMetricSensorTypes = listOf("cpu", "gpu", "skin", "usb", "amp")
            val aggregations = listOf("min", "mean", "max")
            // For e.g. thermal_cpu_CPU0_c.mean, create an extra metric called temp.cpu_0.mean
            legacyMetricSensorTypes.forEach { sensorType ->
                aggregations.forEach { aggregation ->
                    metrics.toList()
                        .filter { (key, _) -> key.matches(Regex("thermal_${sensorType}_.*_c\\.$aggregation$")) }
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
                                    collectionUptimeMs = endUptimeMs,
                                    internal = false,
                                ),
                            )
                        }
                }
            }
        }

        return result
    }

    private fun derived(name: String, value: Double, endTimestampMs: Long, endUptimeMs: Long) =
        DerivedAggregation.create(
            metricName = name,
            metricValue = value,
            metricType = MetricType.GAUGE,
            dataType = DataType.DOUBLE,
            collectionTimeMs = endTimestampMs,
            collectionUptimeMs = endUptimeMs,
            internal = false,
        )

    companion object {
        private val CPU_MAX_REGEX = Regex("thermal_cpu_.*\\.max$")
        private val GPU_MAX_REGEX = Regex("thermal_gpu_.*\\.max$")
        private val NPU_MAX_REGEX = Regex("thermal_npu_.*\\.max$")
        private val SKIN_MAX_REGEX = Regex("thermal_skin_.*\\.max$")
        private val BATTERY_MEAN_REGEX = Regex("thermal_battery_.*\\.mean$")
        private val BATTERY_MAX_REGEX = Regex("thermal_battery_.*\\.max$")
        private val CPU_STATUS_MAX_REGEX = Regex("thermal_status_cpu_.*\\.max$")
        private val GPU_STATUS_MAX_REGEX = Regex("thermal_status_gpu_.*\\.max$")
        private val NPU_STATUS_MAX_REGEX = Regex("thermal_status_npu_.*\\.max$")
    }
}
