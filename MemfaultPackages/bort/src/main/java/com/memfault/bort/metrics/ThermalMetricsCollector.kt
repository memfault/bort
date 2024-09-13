package com.memfault.bort.metrics

import com.memfault.bort.reporting.NumericAgg.MAX
import com.memfault.bort.reporting.NumericAgg.MEAN
import com.memfault.bort.reporting.NumericAgg.MIN
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

@ContributesMultibinding(scope = SingletonComponent::class)
class ThermalMetricsCollector @Inject constructor(
    private val collectThermalDumpsys: CollectThermalDumpsys,
    private val thermalMetricReporter: ThermalMetricReporter,
) : MetricCollector {
    override suspend fun collect() {
        try {
            val metrics = collectThermalDumpsys()
            metrics.forEach {
                Logger.d("thermal metric: $it")
            }
            TemperatureType.entries.filter { it.collectMetric }.forEach { type ->
                metrics.filter { it.type == type }
                    // Sort by name, so that we have stable indexes.
                    .sortedBy { it.name }
                    .forEachIndexed { index, metric ->
                        thermalMetricReporter.reportMetric(
                            name = "temp.${type.tag}_$index",
                            metric.value,
                        )
                    }
            }
        } catch (e: Exception) {
            Logger.w("Error collecting thermal", e)
        }
    }
}

interface ThermalMetricReporter {
    fun reportMetric(name: String, value: Double)
}

@ContributesBinding(SingletonComponent::class)
class RealThermalMetricReporter @Inject constructor() : ThermalMetricReporter {
    private val report = Reporting.report()

    override fun reportMetric(
        name: String,
        value: Double,
    ) {
        report.distribution(name, aggregations = listOf(MIN, MAX, MEAN))
            .record(value)
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
    UNKNOWN(-1, "unknown", collectMetric = false),
    CPU(0, "cpu", collectMetric = true),
    GPU(1, "gpu", collectMetric = true),
    BATTERY(2, "battery", collectMetric = false),
    SKIN(3, "skin", collectMetric = true),
    USB_PORT(4, "usb", collectMetric = true),
    POWER_AMPLIFIER(5, "amp", collectMetric = true),

    /**
     * Battery Charge Limit - virtual thermal sensors
     */
    BCL_VOLTAGE(6, "bcl_voltage", collectMetric = false),
    BCL_CURRENT(7, "bcl_current", collectMetric = false),
    BCL_PERCENTAGE(8, "bcl_pct", collectMetric = false),

    /**
     * Neural Processing Unit
     */
    NPU(9, "npu", collectMetric = true),
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
    LIGHT(1) /* ::android::hardware::thermal::V2_0::ThrottlingSeverity.NONE implicitly + 1 */,

    /**
     * Moderate throttling where UX is not largely impacted.
     */
    MODERATE(2) /* ::android::hardware::thermal::V2_0::ThrottlingSeverity.LIGHT implicitly + 1 */,

    /**
     * Severe throttling where UX is largely impacted.
     * Similar to 1.0 throttlingThreshold.
     */
    SEVERE(3) /* ::android::hardware::thermal::V2_0::ThrottlingSeverity.MODERATE implicitly + 1 */,

    /**
     * Platform has done everything to reduce power.
     */
    CRITICAL(4) /* ::android::hardware::thermal::V2_0::ThrottlingSeverity.SEVERE implicitly + 1 */,

    /**
     * Key components in platform are shutting down due to thermal condition.
     * Device functionalities will be limited.
     */
    EMERGENCY(5) /* ::android::hardware::thermal::V2_0::ThrottlingSeverity.CRITICAL implicitly + 1 */,

    /**
     * Need shutdown immediately.
     */
    SHUTDOWN(6) /* ::android::hardware::thermal::V2_0::ThrottlingSeverity.EMERGENCY implicitly + 1 */,
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
