package com.memfault.bort.metrics

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
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ThermalMetricsCollectorTest {
    private var input: List<ThermalMetric> = emptyList()
    private val collectThermalDumpsys = object : CollectThermalDumpsys {
        override suspend fun invoke(): List<ThermalMetric> {
            return input
        }
    }
    private val thermalMetricReporter: ThermalMetricReporter = mockk(relaxed = true)
    private val collector = ThermalMetricsCollector(collectThermalDumpsys, thermalMetricReporter)

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
            ThermalMetric(value = 36.3, type = CPU, name = "CPU1", status = NONE),
            ThermalMetric(value = 37.2, type = CPU, name = "CPU3", status = NONE),
            ThermalMetric(value = 35.4, type = CPU, name = "CPU2", status = NONE),
            ThermalMetric(value = 36.2, type = CPU, name = "CPU0", status = NONE),
            ThermalMetric(value = 33.3, type = USB_PORT, name = "USB", status = NONE),
            ThermalMetric(value = 33.333, type = POWER_AMPLIFIER, name = "AMP", status = NONE),
        )
        collector.collect()
        verify {
            thermalMetricReporter.reportMetric("temp.cpu_0", 36.2)
            thermalMetricReporter.reportMetric("temp.cpu_1", 36.3)
            thermalMetricReporter.reportMetric("temp.cpu_2", 35.4)
            thermalMetricReporter.reportMetric("temp.cpu_3", 37.2)
            thermalMetricReporter.reportMetric("temp.cpu_4", 35.0)
            thermalMetricReporter.reportMetric("temp.cpu_5", 35.1)
            thermalMetricReporter.reportMetric("temp.gpu_0", 34.5)
            thermalMetricReporter.reportMetric("temp.gpu_1", 34.3)
            thermalMetricReporter.reportMetric("temp.skin_0", 33.136)
            thermalMetricReporter.reportMetric("temp.usb_0", 33.3)
            thermalMetricReporter.reportMetric("temp.amp_0", 33.333)
            thermalMetricReporter.reportMetric("temp.npu_0", 34.4)
            thermalMetricReporter.reportMetric("temp.npu_1", 34.7)
        }
        confirmVerified(thermalMetricReporter)
    }
}
