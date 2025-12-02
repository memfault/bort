package com.memfault.bort.metrics

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import com.memfault.bort.metrics.custom.ReportType.Hourly
import com.memfault.bort.metrics.database.DerivedAggregation
import com.memfault.bort.reporting.DataType
import com.memfault.bort.reporting.MetricType
import com.memfault.bort.settings.MetricsSettings
import com.memfault.bort.settings.RateLimitingSettings
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import kotlin.time.Duration

class ThermalDerivedCalculatorTest {
    private fun calculator(legacyMetrics: Boolean) = ThermalDerivedCalculator(
        object : MetricsSettings {
            override val dataSourceEnabled: Boolean get() = TODO("Not used")
            override val dailyHeartbeatEnabled: Boolean get() = TODO("Not used")
            override val sessionsRateLimitingSettings: RateLimitingSettings get() = TODO("Not used")
            override val collectionInterval: Duration get() = TODO("Not used")
            override val systemProperties: List<String> get() = TODO("Not used")
            override val appVersions: List<String> get() = TODO("Not used")
            override val maxNumAppVersions: Int get() = TODO("Not used")
            override val reporterCollectionInterval: Duration get() = TODO("Not used")
            override val cachePackageManagerReport: Boolean get() = TODO("Not used")
            override val recordImei: Boolean get() = TODO("Not used")
            override val operationalCrashesExclusions: List<String> get() = TODO("Not used")
            override val operationalCrashesComponentGroups: JsonObject get() = TODO("not used")
            override val pollingInterval: Duration get() = TODO("Not used")
            override val collectMemory: Boolean get() = TODO("Not used")
            override val thermalMetricsEnabled: Boolean get() = TODO("Not used")
            override val thermalCollectLegacyMetrics: Boolean get() = legacyMetrics
            override val thermalCollectStatus: Boolean get() = TODO("Not used")
            override val cpuInterestingProcesses: Set<String> get() = TODO("not used")
            override val cpuProcessReportingThreshold: Int get() = TODO("not used")
            override val cpuProcessLimitTopN: Int get() = TODO("not used")
            override val alwaysCreateCpuProcessMetrics: Boolean get() = TODO("not used")
            override val enableStatsdCollection: Boolean get() = TODO("not used")
            override val extraStatsDAtoms: List<Int> get() = TODO("not used")
        },
    )

    @Test
    fun calculateMetrics() {
        val metrics = mapOf(
            "thermal_cpu_CPU0_c.min" to JsonPrimitive(1.0),
            "thermal_cpu_CPU0_c.mean" to JsonPrimitive(2.0),
            "thermal_cpu_CPU0_c.max" to JsonPrimitive(5.5),
            "thermal_cpu_CPU1_c.min" to JsonPrimitive(1.5),
            "thermal_cpu_CPU1_c.mean" to JsonPrimitive(2.5),
            "thermal_cpu_CPU1_c.max" to JsonPrimitive(6.0),
            "thermal_gpu_GPU0_c.min" to JsonPrimitive(2.0),
            "thermal_gpu_GPU0_c.mean" to JsonPrimitive(3.0),
            "thermal_gpu_GPU0_c.max" to JsonPrimitive(10.5),
            "thermal_battery_bat-0_c.min" to JsonPrimitive(2.5),
            "thermal_battery_bat-0_c.mean" to JsonPrimitive(3.5),
            "thermal_battery_bat-0_c.max" to JsonPrimitive(7.0),
            "thermal_battery_bat-1_c.min" to JsonPrimitive(3.0),
            "thermal_battery_bat-1_c.mean" to JsonPrimitive(4.0),
            "thermal_battery_bat-1_c.max" to JsonPrimitive(7.5),
        )
        assertThat(calculator(legacyMetrics = false).calculate(Hourly, 0, 0, metrics, emptyMap()))
            .containsExactly(
                derivedMetric("thermal_cpu_c", 2.25),
                derivedMetric("thermal_cpu_c_max", 6.0),
                derivedMetric("thermal_battery_c", 3.75),
                derivedMetric("thermal_battery_c_max", 7.5),
            )
    }

    @Test
    fun calculateMetricsWithLegacyMetrics() {
        val metrics = mapOf(
            "thermal_cpu_CPU0_c.min" to JsonPrimitive(1.0),
            "thermal_cpu_CPU0_c.mean" to JsonPrimitive(2.0),
            "thermal_cpu_CPU0_c.max" to JsonPrimitive(5.5),
            "thermal_cpu_CPU1_c.min" to JsonPrimitive(1.5),
            "thermal_cpu_CPU1_c.mean" to JsonPrimitive(2.5),
            "thermal_cpu_CPU1_c.max" to JsonPrimitive(6.0),
            "thermal_gpu_GPU0_c.min" to JsonPrimitive(2.0),
            "thermal_gpu_GPU0_c.mean" to JsonPrimitive(3.0),
            "thermal_gpu_GPU0_c.max" to JsonPrimitive(10.5),
            "thermal_battery_bat-0_c.min" to JsonPrimitive(2.5),
            "thermal_battery_bat-0_c.mean" to JsonPrimitive(3.5),
            "thermal_battery_bat-0_c.max" to JsonPrimitive(7.0),
            "thermal_battery_bat-1_c.min" to JsonPrimitive(3.0),
            "thermal_battery_bat-1_c.mean" to JsonPrimitive(4.0),
            "thermal_battery_bat-1_c.max" to JsonPrimitive(7.5),
        )
        assertThat(calculator(legacyMetrics = true).calculate(Hourly, 0, 0, metrics, emptyMap()))
            .containsExactlyInAnyOrder(
                derivedMetric("thermal_cpu_c", 2.25),
                derivedMetric("thermal_cpu_c_max", 6.0),
                derivedMetric("thermal_battery_c", 3.75),
                derivedMetric("thermal_battery_c_max", 7.5),
                // Legacy metrics
                derivedMetric("temp.cpu_0.min", 1.0),
                derivedMetric("temp.cpu_0.mean", 2.0),
                derivedMetric("temp.cpu_0.max", 5.5),
                derivedMetric("temp.cpu_1.min", 1.5),
                derivedMetric("temp.cpu_1.mean", 2.5),
                derivedMetric("temp.cpu_1.max", 6.0),
                derivedMetric("temp.gpu_0.min", 2.0),
                derivedMetric("temp.gpu_0.mean", 3.0),
                derivedMetric("temp.gpu_0.max", 10.5),
            )
    }

    @Test
    fun missingMetricsNoOutput() {
        val metrics = mapOf(
            "thermal_gpu_GPU0_c.min" to JsonPrimitive(2.0),
            "thermal_gpu_GPU0_c.mean" to JsonPrimitive(3.0),
            "thermal_gpu_GPU0_c.max" to JsonPrimitive(10.5),
        )
        assertThat(calculator(legacyMetrics = false).calculate(Hourly, 0, 0, metrics, emptyMap()))
            .isEmpty()
    }

    private fun derivedMetric(name: String, value: Double) = DerivedAggregation.create(
        metricName = name,
        metricValue = value,
        metricType = MetricType.GAUGE,
        dataType = DataType.DOUBLE,
        collectionTimeMs = 0,
        internal = false,
    )
}
