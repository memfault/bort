package com.memfault.bort.metrics

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.memfault.bort.metrics.TemperatureType.BATTERY
import com.memfault.bort.metrics.TemperatureType.BCL_CURRENT
import com.memfault.bort.metrics.TemperatureType.BCL_PERCENTAGE
import com.memfault.bort.metrics.TemperatureType.BCL_VOLTAGE
import com.memfault.bort.metrics.TemperatureType.CPU
import com.memfault.bort.metrics.TemperatureType.GPU
import com.memfault.bort.metrics.TemperatureType.NPU
import com.memfault.bort.metrics.TemperatureType.POWER_AMPLIFIER
import com.memfault.bort.metrics.TemperatureType.SKIN
import com.memfault.bort.metrics.TemperatureType.USB_PORT
import com.memfault.bort.metrics.ThrottlingSeverity.NONE
import com.memfault.bort.metrics.ThrottlingSeverity.SEVERE
import com.memfault.bort.settings.MetricsSettings
import com.memfault.bort.settings.RateLimitingSettings
import com.memfault.bort.settings.SettingsFlow
import com.memfault.bort.settings.SettingsProvider
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Test
import kotlin.time.Duration

class ThermalMetricsCollectorTest {
    private var input: List<ThermalMetric> = emptyList()
    private val collectThermalDumpsys = CollectThermalDumpsys { input }
    private val thermalMetricReporter: ThermalMetricReporter = mockk(relaxed = true)
    private var collectionEnabled = true
    private val metricsSettings = object : MetricsSettings {
        override val dataSourceEnabled: Boolean get() = TODO("not used")
        override val dailyHeartbeatEnabled: Boolean get() = TODO("not used")
        override val sessionsRateLimitingSettings: RateLimitingSettings get() = TODO("not used")
        override val collectionInterval: Duration get() = TODO("not used")
        override val systemProperties: List<String> get() = TODO("not used")
        override val appVersions: List<String> get() = TODO("not used")
        override val maxNumAppVersions: Int get() = TODO("not used")
        override val reporterCollectionInterval: Duration get() = TODO("not used")
        override val cachePackageManagerReport: Boolean get() = TODO("not used")
        override val recordImei: Boolean get() = TODO("not used")
        override val operationalCrashesExclusions: List<String> get() = TODO("not used")
        override val operationalCrashesComponentGroups: JsonObject get() = TODO("not used")
        override val pollingInterval: Duration get() = TODO("not used")
        override val collectMemory: Boolean get() = TODO("Not used")
        override val thermalMetricsEnabled: Boolean get() = collectionEnabled
        override val thermalCollectLegacyMetrics: Boolean get() = TODO("not used")
        override val thermalCollectStatus: Boolean
            get() = TODO("Not yet implemented")
        override val cpuInterestingProcesses: Set<String> get() = TODO("not used")
        override val cpuProcessReportingThreshold: Int get() = TODO("not used")
        override val cpuProcessLimitTopN: Int get() = TODO("not used")
        override val alwaysCreateCpuProcessMetrics: Boolean get() = TODO("not used")
    }

    private val settingsFlow = object : SettingsFlow {
        override val settings: Flow<SettingsProvider> = emptyFlow()
    }
    private val collector = ThermalMetricsCollector(
        collectThermalDumpsys = collectThermalDumpsys,
        thermalMetricReporter = thermalMetricReporter,
        metricsSettings = metricsSettings,
        settingsFlow = settingsFlow,
    )

    @Test
    fun filtersAndSortsMetrics() = runTest {
        input = listOf(
            ThermalMetric(value = 11.0, type = BCL_PERCENTAGE, name = "socd", status = NONE),
            ThermalMetric(value = 29.0, type = BATTERY, name = "battery", status = NONE),
            ThermalMetric(value = 33.136, type = SKIN, name = "skin", status = NONE),
            ThermalMetric(value = 34.7, type = NPU, name = "nsp1", status = NONE),
            ThermalMetric(value = 34.5, type = GPU, name = "GPU0", status = NONE),
            ThermalMetric(value = 34.4, type = NPU, name = "nsp0", status = NONE),
            ThermalMetric(value = 35.1, type = CPU, name = "CPU5", status = NONE),
            ThermalMetric(value = 4.384, type = BCL_VOLTAGE, name = "vbat", status = NONE),
            ThermalMetric(value = 7.056, type = BCL_CURRENT, name = "vcur", status = NONE),
            ThermalMetric(value = 34.3, type = GPU, name = "GPU1", status = NONE),
            ThermalMetric(value = 35.0, type = CPU, name = "CPU4", status = NONE),
            ThermalMetric(value = 36.3, type = CPU, name = "CPU/1", status = NONE),
            ThermalMetric(value = 37.2, type = CPU, name = "CPU3", status = NONE),
            ThermalMetric(value = 35.4, type = CPU, name = "CPU 2", status = NONE),
            ThermalMetric(value = 36.2, type = CPU, name = "CPU0", status = NONE),
            ThermalMetric(value = 33.3, type = USB_PORT, name = "USB", status = NONE),
            ThermalMetric(value = 33.333, type = POWER_AMPLIFIER, name = "AMP", status = SEVERE),
        )
        collector.collect()
        verify {
            thermalMetricReporter.reportMetric(
                tempName = "thermal_battery_battery_c",
                tempValue = 29.0,
                statusName = "thermal_status_battery_battery",
                statusValue = NONE,
            )
            thermalMetricReporter.reportMetric(
                tempName = "thermal_cpu_CPU0_c",
                tempValue = 36.2,
                statusName = "thermal_status_cpu_CPU0",
                statusValue = NONE,
            )
            thermalMetricReporter.reportMetric(
                tempName = "thermal_cpu_CPU-1_c",
                tempValue = 36.3,
                statusName = "thermal_status_cpu_CPU-1",
                statusValue = NONE,
            )
            thermalMetricReporter.reportMetric(
                tempName = "thermal_cpu_CPU-2_c",
                tempValue = 35.4,
                statusName = "thermal_status_cpu_CPU-2",
                statusValue = NONE,
            )
            thermalMetricReporter.reportMetric(
                tempName = "thermal_cpu_CPU3_c",
                tempValue = 37.2,
                statusName = "thermal_status_cpu_CPU3",
                statusValue = NONE,
            )
            thermalMetricReporter.reportMetric(
                tempName = "thermal_cpu_CPU4_c",
                tempValue = 35.0,
                statusName = "thermal_status_cpu_CPU4",
                statusValue = NONE,
            )
            thermalMetricReporter.reportMetric(
                tempName = "thermal_cpu_CPU5_c",
                tempValue = 35.1,
                statusName = "thermal_status_cpu_CPU5",
                statusValue = NONE,
            )
            thermalMetricReporter.reportMetric(
                tempName = "thermal_gpu_GPU0_c",
                tempValue = 34.5,
                statusName = "thermal_status_gpu_GPU0",
                statusValue = NONE,
            )
            thermalMetricReporter.reportMetric(
                tempName = "thermal_gpu_GPU1_c",
                tempValue = 34.3,
                statusName = "thermal_status_gpu_GPU1",
                statusValue = NONE,
            )
            thermalMetricReporter.reportMetric(
                tempName = "thermal_skin_skin_c",
                tempValue = 33.136,
                statusName = "thermal_status_skin_skin",
                statusValue = NONE,
            )
            thermalMetricReporter.reportMetric(
                tempName = "thermal_usb_USB_c",
                tempValue = 33.3,
                statusName = "thermal_status_usb_USB",
                statusValue = NONE,
            )
            thermalMetricReporter.reportMetric(
                tempName = "thermal_amp_AMP_c",
                tempValue = 33.333,
                statusName = "thermal_status_amp_AMP",
                statusValue = SEVERE,
            )
            thermalMetricReporter.reportMetric(
                tempName = "thermal_npu_nsp0_c",
                tempValue = 34.4,
                statusName = "thermal_status_npu_nsp0",
                statusValue = NONE,
            )
            thermalMetricReporter.reportMetric(
                tempName = "thermal_npu_nsp1_c",
                tempValue = 34.7,
                statusName = "thermal_status_npu_nsp1",
                statusValue = NONE,
            )
        }
        confirmVerified(thermalMetricReporter)
    }

    @Test
    fun collectionDisabled() = runTest {
        collectionEnabled = false
        input = listOf(
            ThermalMetric(value = 11.0, type = BCL_PERCENTAGE, name = "socd", status = NONE),
            ThermalMetric(value = 29.0, type = BATTERY, name = "battery", status = NONE),
            ThermalMetric(value = 33.136, type = SKIN, name = "skin", status = NONE),
            ThermalMetric(value = 34.7, type = NPU, name = "nsp1", status = NONE),
            ThermalMetric(value = 34.5, type = GPU, name = "GPU0", status = NONE),
            ThermalMetric(value = 34.4, type = NPU, name = "nsp0", status = NONE),
            ThermalMetric(value = 35.1, type = CPU, name = "CPU5", status = NONE),
            ThermalMetric(value = 4.384, type = BCL_VOLTAGE, name = "vbat", status = NONE),
            ThermalMetric(value = 7.056, type = BCL_CURRENT, name = "vcur", status = NONE),
            ThermalMetric(value = 34.3, type = GPU, name = "GPU1", status = NONE),
            ThermalMetric(value = 35.0, type = CPU, name = "CPU4", status = NONE),
            ThermalMetric(value = 36.3, type = CPU, name = "CPU1", status = NONE),
            ThermalMetric(value = 37.2, type = CPU, name = "CPU3", status = NONE),
            ThermalMetric(value = 35.4, type = CPU, name = "CPU2", status = NONE),
            ThermalMetric(value = 36.2, type = CPU, name = "CPU0", status = NONE),
            ThermalMetric(value = 33.3, type = USB_PORT, name = "USB", status = NONE),
            ThermalMetric(value = 33.333, type = POWER_AMPLIFIER, name = "AMP", status = NONE),
        )
        collector.collect()
        confirmVerified(thermalMetricReporter)
    }

    @Test
    fun sanitiseNames() = runTest {
        assertThat(ThermalMetricsCollector.sanitise("CPU 0")).isEqualTo("CPU-0")
        assertThat(ThermalMetricsCollector.sanitise("CPU/0")).isEqualTo("CPU-0")
        assertThat(ThermalMetricsCollector.sanitise("CPU_0")).isEqualTo("CPU-0")
        assertThat(ThermalMetricsCollector.sanitise("C PU@0")).isEqualTo("C-PU-0")
        assertThat(ThermalMetricsCollector.sanitise("CPU@0")).isEqualTo("CPU-0")
    }
}
