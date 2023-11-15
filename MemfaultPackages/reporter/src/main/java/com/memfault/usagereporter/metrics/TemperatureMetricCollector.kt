package com.memfault.usagereporter.metrics

import android.content.Context
import android.os.HardwarePropertiesManager
import android.os.HardwarePropertiesManager.DEVICE_TEMPERATURE_CPU
import android.os.HardwarePropertiesManager.DEVICE_TEMPERATURE_SKIN
import android.os.HardwarePropertiesManager.TEMPERATURE_CURRENT
import com.memfault.bort.reporting.NumericAgg.MAX
import com.memfault.bort.reporting.NumericAgg.MEAN
import com.memfault.bort.reporting.NumericAgg.MIN
import com.memfault.bort.reporting.Reporting

class TemperatureMetricCollector(
    private val context: Context,
) : MetricCollector {
    private val report = Reporting.report()

    override fun collect() {
        collectForType(tag = "cpu", type = DEVICE_TEMPERATURE_CPU)
        collectForType(tag = "skin", type = DEVICE_TEMPERATURE_SKIN)
    }

    private fun collectForType(tag: String, type: Int) {
        val service = hardwarePropertiesService() ?: return
        val temps = service.getDeviceTemperatures(type, TEMPERATURE_CURRENT)
        temps.forEachIndexed { index, value ->
            report.distribution("temp.${tag}_$index", aggregations = listOf(MIN, MAX, MEAN)).record(value.toDouble())
        }
    }

    private fun hardwarePropertiesService() = context.getSystemService(HardwarePropertiesManager::class.java)
}
