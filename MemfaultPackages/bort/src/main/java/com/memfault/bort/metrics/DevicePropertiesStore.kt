package com.memfault.bort.metrics

import com.memfault.bort.metrics.HighResTelemetry.DataType
import com.memfault.bort.metrics.HighResTelemetry.DataType.BooleanType
import com.memfault.bort.metrics.HighResTelemetry.DataType.DoubleType
import com.memfault.bort.metrics.HighResTelemetry.DataType.StringType
import com.memfault.bort.metrics.HighResTelemetry.Datum
import com.memfault.bort.metrics.HighResTelemetry.MetricType.Property
import com.memfault.bort.metrics.HighResTelemetry.Rollup
import com.memfault.bort.metrics.HighResTelemetry.RollupMetadata
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.reporting.StateAgg
import com.memfault.bort.settings.MetricsSettings
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Stores device properties, ready to be uploaded.
 *
 * Depending on settings, this either forwards to the metrics service, or stores locally for collection by
 * [MetricsCollectionTask].
 *
 * Note: Deliberately not @Inject - this is only supposed to be injected by MetricsCollectionTask, so that it can pass
 * around to any consumers who might be writing metrics. This will avoid anything randomly writing metrics *not*
 * during the execution of the [MetricsCollectionTask].
 *
 * Only properties (i.e. `.latest`) are supported.
 *
 * This is likely a temporary measure, until we move metrics storage to Bort.
 */
class DevicePropertiesStore(
    private val metricsSettings: MetricsSettings,
) {
    private val metrics: MutableMap<String, JsonPrimitive> = mutableMapOf()
    private val internalMetrics: MutableMap<String, JsonPrimitive> = mutableMapOf()

    private fun storeLocally(name: String, value: JsonPrimitive, internal: Boolean) {
        if (internal) {
            internalMetrics[name] = value
        } else {
            metrics[name] = value
        }
    }

    fun upsert(name: String, value: String, internal: Boolean = false) {
        if (metricsSettings.propertiesUseMetricService) {
            Reporting.report().stringProperty(name = name, addLatestToReport = true, internal = internal).update(value)
        } else {
            storeLocally(name = name, value = JsonPrimitive(value), internal = internal)
        }
    }

    fun upsert(name: String, value: Double, internal: Boolean = false) {
        if (metricsSettings.propertiesUseMetricService) {
            Reporting.report().numberProperty(name = name, addLatestToReport = true, internal = internal).update(value)
        } else {
            storeLocally(name = name, value = JsonPrimitive(value), internal = internal)
        }
    }

    fun upsert(name: String, value: Long, internal: Boolean = false) {
        upsert(name = name, value = value.toDouble(), internal = internal)
    }

    fun upsert(name: String, value: Int, internal: Boolean = false) {
        upsert(name = name, value = value.toDouble(), internal = internal)
    }

    fun upsert(name: String, value: Boolean, internal: Boolean = false) {
        if (metricsSettings.propertiesUseMetricService) {
            Reporting.report()
                .boolStateTracker(name = name, aggregations = listOf(StateAgg.LATEST_VALUE), internal = internal)
                .state(value)
        } else {
            storeLocally(name = name, value = JsonPrimitive(value), internal = internal)
        }
    }

    /**
     * Convert bools to legacy string values. Native bools aren't supported in reports.
     */
    private fun JsonPrimitive.withLegacyBoolVal() = when (this.booleanOrNull) {
        true -> JsonPrimitive("1")
        false -> JsonPrimitive("0")
        null -> this
    }

    fun metrics(): Map<String, JsonPrimitive> = metrics.mapKeys { "${it.key}.latest" }
        .mapValues { it.value.withLegacyBoolVal() }

    fun internalMetrics(): Map<String, JsonPrimitive> = internalMetrics.mapKeys { "${it.key}.latest" }
        .mapValues { it.value.withLegacyBoolVal() }

    fun hrtRollups(timestampMs: Long): Set<Rollup> =
        metrics.map { it.toRollup(internal = false, timestampMs = timestampMs) }.toSet() +
            internalMetrics.map { it.toRollup(internal = true, timestampMs = timestampMs) }.toSet()

    companion object {
        private fun Map.Entry<String, JsonPrimitive>.toRollup(internal: Boolean, timestampMs: Long): Rollup = Rollup(
            metadata = RollupMetadata(
                stringKey = key,
                metricType = Property,
                dataType = value.dataType(),
                internal = internal,
            ),
            data = listOf(
                Datum(
                    t = timestampMs,
                    value = value,
                ),
            ),
        )

        private fun JsonPrimitive.dataType(): DataType = when {
            this.jsonPrimitive.booleanOrNull != null -> BooleanType
            this.jsonPrimitive.doubleOrNull != null -> DoubleType
            this.jsonPrimitive.isString -> StringType
            else -> StringType
        }
    }
}
