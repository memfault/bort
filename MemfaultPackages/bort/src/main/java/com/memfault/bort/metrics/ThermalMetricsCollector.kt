package com.memfault.bort.metrics

import com.memfault.bort.reporting.NumericAgg.MAX
import com.memfault.bort.reporting.NumericAgg.MEAN
import com.memfault.bort.reporting.NumericAgg.MIN
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.settings.MetricsSettings
import com.memfault.bort.settings.SettingsFlow
import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@ContributesMultibinding(scope = SingletonComponent::class)
class ThermalMetricsCollector @Inject constructor(
    private val collectThermalDumpsys: CollectThermalDumpsys,
    private val thermalMetricReporter: ThermalMetricReporter,
    private val metricsSettings: MetricsSettings,
    private val settingsFlow: SettingsFlow,
) : MetricCollector {
    override fun onChanged(): Flow<Unit> = settingsFlow.settings
        .map { settings ->
            settings.metricsSettings.thermalMetricsEnabled to settings.metricsSettings.thermalCollectStatus
        }.distinctUntilChanged()
        .map { }

    override suspend fun collect() {
        if (!metricsSettings.thermalMetricsEnabled) {
            return
        }
        try {
            val metrics = collectThermalDumpsys()
            metrics.forEach {
                Logger.d("thermal metric: $it")
            }
            TemperatureType.entries.filter { it.collectMetric }.forEach { type ->
                metrics.filter { it.type == type }
                    // Sort by name, so that we have stable indexes.
                    .sortedBy { it.name }
                    .forEach { metric ->
                        val name = sanitise(metric.name)
                        thermalMetricReporter.reportMetric(
                            tempName = "thermal_${type.tag}_${name}_c",
                            tempValue = metric.value,
                            statusName = "thermal_status_${type.tag}_$name",
                            statusValue = metric.status,
                        )
                    }
            }
        } catch (e: Exception) {
            Logger.w("Error collecting thermal", e)
        }
    }

    companion object {
        private val regex = Regex("[^a-zA-Z0-9]")

        fun sanitise(name: String) = regex.replace(name, "-")
    }
}

interface ThermalMetricReporter {
    fun reportMetric(tempName: String, tempValue: Double, statusName: String, statusValue: ThrottlingSeverity)
}

@ContributesBinding(SingletonComponent::class)
class RealThermalMetricReporter
@Inject constructor(
    private val metricsSettings: MetricsSettings,
) : ThermalMetricReporter {
    private val report = Reporting.report()

    override fun reportMetric(
        tempName: String,
        tempValue: Double,
        statusName: String,
        statusValue: ThrottlingSeverity,
    ) {
        report.distribution(tempName, aggregations = listOf(MIN, MAX, MEAN))
            .record(tempValue)

        if (metricsSettings.thermalCollectStatus) {
            report.distribution(statusName, aggregations = listOf(MAX))
                .record(statusValue.value.toLong())
        }
    }
}

/**
 * From https://cs.android.com/android/platform/superproject/main/+/main:prebuilts/vndk/v30/x86/include/generated-headers/hardware/interfaces/thermal/2.0/android.hardware.thermal@2.0_genc++_headers/gen/android/hardware/thermal/2.0/types.h;l=27?q=TemperatureType&sq=
 */
enum class TemperatureType(
    val value: Int,
    val tag: String,
    val collectMetric: Boolean,
) {
    UNKNOWN(value = -1, tag = "unknown", collectMetric = false),
    CPU(value = 0, tag = "cpu", collectMetric = true),
    GPU(value = 1, tag = "gpu", collectMetric = true),
    BATTERY(value = 2, tag = "battery", collectMetric = true),
    SKIN(value = 3, tag = "skin", collectMetric = true),
    USB_PORT(value = 4, tag = "usb", collectMetric = true),
    POWER_AMPLIFIER(value = 5, tag = "amp", collectMetric = true),

    /**
     * Battery Charge Limit - virtual thermal sensors
     */
    BCL_VOLTAGE(value = 6, tag = "bcl_voltage", collectMetric = false),
    BCL_CURRENT(value = 7, tag = "bcl_current", collectMetric = false),
    BCL_PERCENTAGE(value = 8, tag = "bcl_pct", collectMetric = false),

    /**
     * Neural Processing Unit
     */
    NPU(value = 9, tag = "npu", collectMetric = true),
    ;

    companion object {
        fun fromInt(value: Int): TemperatureType = entries.find { it.value == value } ?: UNKNOWN
    }
}

/**
 * From https://cs.android.com/android/platform/superproject/main/+/main:prebuilts/vndk/v30/arm/include/generated-headers/hardware/interfaces/thermal/2.0/android.hardware.thermal@2.0_genc++_headers/gen/android/hardware/thermal/2.0/types.h;l=63?q=ThrottlingSeverity
 */
enum class ThrottlingSeverity(
    val value: Int,
) {
    UNKNOWN(-1),

    /**
     * Not under throttling.
     */
    NONE(0),

    /**
     * Light throttling where UX is not impacted.
     */
    // ::android::hardware::thermal::V2_0::ThrottlingSeverity.NONE implicitly + 1
    LIGHT(1),

    /**
     * Moderate throttling where UX is not largely impacted.
     */
    // ::android::hardware::thermal::V2_0::ThrottlingSeverity.LIGHT implicitly + 1
    MODERATE(2),

    /**
     * Severe throttling where UX is largely impacted.
     * Similar to 1.0 throttlingThreshold.
     */
    // ::android::hardware::thermal::V2_0::ThrottlingSeverity.MODERATE implicitly + 1
    SEVERE(3),

    /**
     * Platform has done everything to reduce power.
     */
    // ::android::hardware::thermal::V2_0::ThrottlingSeverity.SEVERE implicitly + 1
    CRITICAL(4),

    /**
     * Key components in platform are shutting down due to thermal condition.
     * Device functionalities will be limited.
     */
    // ::android::hardware::thermal::V2_0::ThrottlingSeverity.CRITICAL implicitly + 1
    EMERGENCY(5),

    /**
     * Need shutdown immediately.
     */
    // ::android::hardware::thermal::V2_0::ThrottlingSeverity.EMERGENCY implicitly + 1
    SHUTDOWN(6),
    ;

    companion object {
        fun fromInt(value: Int): ThrottlingSeverity = entries.find { it.value == value } ?: UNKNOWN
    }
}

data class ThermalMetric(
    val value: Double,
    val type: TemperatureType,
    val name: String,
    val status: ThrottlingSeverity,
)
