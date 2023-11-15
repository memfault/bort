package com.memfault.bort.metrics

import com.memfault.bort.metrics.HighResTelemetry.DataType
import com.memfault.bort.metrics.HighResTelemetry.DataType.BooleanType
import com.memfault.bort.metrics.HighResTelemetry.DataType.DoubleType
import com.memfault.bort.metrics.HighResTelemetry.DataType.StringType
import com.memfault.bort.metrics.HighResTelemetry.Rollup
import com.memfault.bort.settings.MetricsSettings
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class DevicePropertiesStoreTest {
    private var writeToService = true
    private val metricsSettings = object : MetricsSettings {
        override val dataSourceEnabled: Boolean = true
        override val collectionInterval: Duration = 1.hours
        override val systemProperties: List<String> = emptyList()
        override val appVersions: List<String> = emptyList()
        override val maxNumAppVersions: Int = 10
        override val reporterCollectionInterval: Duration = 15.minutes
        override val propertiesUseMetricService: Boolean get() = writeToService
    }

    private val propertiesStore = DevicePropertiesStore(metricsSettings)

    companion object {
        private const val METRIC_NAME_STRING = "key_str"
        private const val METRIC_NAME_LONG = "key_long"
        private const val METRIC_NAME_INT = "key_int"
        private const val METRIC_NAME_DOUBLE = "key_dbl"
        private const val METRIC_NAME_BOOL = "key_bool"
        private const val METRIC_VALUE_STRING = "value"
        private val METRIC_JSON_STRING = JsonPrimitive(METRIC_VALUE_STRING)
        private const val METRIC_VALUE_LONG = 5555L
        private val METRIC_JSON_LONG = JsonPrimitive(METRIC_VALUE_LONG.toDouble())
        private const val METRIC_VALUE_INT = 44444
        private val METRIC_JSON_INT = JsonPrimitive(METRIC_VALUE_INT.toDouble())
        private const val METRIC_VALUE_DOUBLE = 33.33
        private val METRIC_JSON_DOUBLE = JsonPrimitive(METRIC_VALUE_DOUBLE)
        private const val METRIC_VALUE_BOOL = false
        private val METRIC_JSON_BOOL_REPORT = JsonPrimitive("0")
        private val METRIC_JSON_BOOL_HRT = JsonPrimitive(false)
        private const val TIMESTAMP = 1234L
    }

    private fun rollup(name: String, value: JsonPrimitive, dataType: DataType, internal: Boolean) = Rollup(
        metadata = HighResTelemetry.RollupMetadata(
            stringKey = name,
            metricType = HighResTelemetry.MetricType.Property,
            dataType = dataType,
            internal = internal,
        ),
        data = listOf(
            HighResTelemetry.Datum(
                t = TIMESTAMP,
                value = value,
            ),
        ),
    )

    private fun String.latest() = "$this.latest"

    @Test
    fun notStoredString() {
        writeToService = true
        propertiesStore.upsert(name = METRIC_NAME_STRING, value = METRIC_VALUE_STRING, internal = false)
        assertTrue(propertiesStore.metrics().isEmpty())
        assertTrue(propertiesStore.internalMetrics().isEmpty())
        assertTrue(propertiesStore.hrtRollups(TIMESTAMP).isEmpty())
    }

    @Test
    fun notStoredBool() {
        writeToService = true
        propertiesStore.upsert(name = METRIC_NAME_BOOL, value = METRIC_VALUE_BOOL, internal = false)
        assertTrue(propertiesStore.metrics().isEmpty())
        assertTrue(propertiesStore.internalMetrics().isEmpty())
        assertTrue(propertiesStore.hrtRollups(TIMESTAMP).isEmpty())
    }

    @Test
    fun storedString() {
        writeToService = false
        propertiesStore.upsert(name = METRIC_NAME_STRING, value = METRIC_VALUE_STRING, internal = false)
        assertEquals(
            mapOf(METRIC_NAME_STRING.latest() to JsonPrimitive(METRIC_VALUE_STRING)),
            propertiesStore.metrics(),
        )
        assertTrue(propertiesStore.internalMetrics().isEmpty())
        assertEquals(
            setOf(
                rollup(
                    name = METRIC_NAME_STRING,
                    value = JsonPrimitive(METRIC_VALUE_STRING),
                    dataType = StringType,
                    internal = false,
                ),
            ),
            propertiesStore.hrtRollups(TIMESTAMP),
        )
    }

    @Test
    fun storedInternalLong() {
        writeToService = false
        propertiesStore.upsert(name = METRIC_NAME_LONG, value = METRIC_VALUE_LONG, internal = true)
        assertTrue(propertiesStore.metrics().isEmpty())
        assertEquals(mapOf(METRIC_NAME_LONG.latest() to METRIC_JSON_LONG), propertiesStore.internalMetrics())
        assertEquals(
            setOf(
                rollup(
                    name = METRIC_NAME_LONG,
                    value = METRIC_JSON_LONG,
                    dataType = DoubleType,
                    internal = true,
                ),
            ),
            propertiesStore.hrtRollups(TIMESTAMP),
        )
    }

    @Test
    fun storedMultiple() {
        writeToService = false
        propertiesStore.upsert(name = METRIC_NAME_DOUBLE, value = METRIC_VALUE_DOUBLE, internal = false)
        propertiesStore.upsert(name = METRIC_NAME_LONG, value = METRIC_VALUE_LONG, internal = true)
        propertiesStore.upsert(name = METRIC_NAME_INT, value = METRIC_VALUE_INT, internal = true)
        propertiesStore.upsert(name = METRIC_NAME_BOOL, value = METRIC_VALUE_BOOL, internal = false)
        propertiesStore.upsert(name = METRIC_NAME_STRING, value = "initial_val", internal = false)
        propertiesStore.upsert(name = METRIC_NAME_STRING, value = METRIC_VALUE_STRING, internal = false)
        assertEquals(
            mapOf(
                METRIC_NAME_DOUBLE.latest() to METRIC_JSON_DOUBLE,
                METRIC_NAME_BOOL.latest() to METRIC_JSON_BOOL_REPORT,
                METRIC_NAME_STRING.latest() to METRIC_JSON_STRING,
            ),
            propertiesStore.metrics(),
        )
        assertEquals(
            mapOf(
                METRIC_NAME_LONG.latest() to METRIC_JSON_LONG,
                METRIC_NAME_INT.latest() to METRIC_JSON_INT,
            ),
            propertiesStore.internalMetrics(),
        )
        assertEquals(
            setOf(
                rollup(
                    name = METRIC_NAME_DOUBLE,
                    value = METRIC_JSON_DOUBLE,
                    dataType = DoubleType,
                    internal = false,
                ),
                rollup(
                    name = METRIC_NAME_LONG,
                    value = METRIC_JSON_LONG,
                    dataType = DoubleType,
                    internal = true,
                ),
                rollup(
                    name = METRIC_NAME_INT,
                    value = METRIC_JSON_INT,
                    dataType = DoubleType,
                    internal = true,
                ),
                rollup(
                    name = METRIC_NAME_BOOL,
                    value = METRIC_JSON_BOOL_HRT,
                    dataType = BooleanType,
                    internal = false,
                ),
                rollup(
                    name = METRIC_NAME_STRING,
                    value = METRIC_JSON_STRING,
                    dataType = StringType,
                    internal = false,
                ),
            ),
            propertiesStore.hrtRollups(TIMESTAMP),
        )
    }
}
