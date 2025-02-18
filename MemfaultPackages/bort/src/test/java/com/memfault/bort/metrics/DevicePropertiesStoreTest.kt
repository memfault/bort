package com.memfault.bort.metrics

import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isNotNull
import assertk.assertions.prop
import assertk.assertions.text
import com.memfault.bort.metrics.HighResTelemetry.DataType
import com.memfault.bort.metrics.HighResTelemetry.DataType.BooleanType
import com.memfault.bort.metrics.HighResTelemetry.DataType.DoubleType
import com.memfault.bort.metrics.HighResTelemetry.DataType.StringType
import com.memfault.bort.metrics.HighResTelemetry.Rollup
import com.memfault.bort.metrics.custom.CustomReport
import com.memfault.bort.metrics.custom.MetricReport
import com.memfault.bort.shared.BortSharedJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DevicePropertiesStoreTest {
    @get:Rule(order = 1)
    val metricsDbTestEnvironment: MetricsDbTestEnvironment =
        MetricsDbTestEnvironment().apply {
            highResMetricsEnabledValue = true
        }

    private val propertiesStore = DevicePropertiesStore()

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
    }

    private fun rollup(
        name: String,
        dataType: DataType,
        internal: Boolean,
        value: JsonPrimitive,
        timestampMs: Long,
    ) = rollup(
        name = name,
        dataType = dataType,
        internal = internal,
        data = listOf(
            HighResTelemetry.Datum(
                t = timestampMs,
                value = value,
            ),
        ),
    )

    private fun rollup(
        name: String,
        dataType: DataType,
        internal: Boolean,
        data: List<HighResTelemetry.Datum>,
    ) = Rollup(
        metadata = HighResTelemetry.RollupMetadata(
            stringKey = name,
            metricType = HighResTelemetry.MetricType.Property,
            dataType = dataType,
            internal = internal,
        ),
        data = data,
    )

    private fun String.latest() = "$this.latest"

    @Test
    fun storedString_internal() = runTest {
        val heartbeatTimestamp = System.currentTimeMillis()

        propertiesStore.upsert(
            name = METRIC_NAME_STRING,
            value = METRIC_VALUE_STRING,
            internal = true,
            timestamp = heartbeatTimestamp,
        )

        assertThat(
            metricsDbTestEnvironment.dao.collectHeartbeat(
                endTimestampMs = heartbeatTimestamp + 1,
            ),
        ).all {
            prop(CustomReport::hourlyHeartbeatReport).all {
                prop(MetricReport::metrics).isEmpty()
                prop(MetricReport::internalMetrics).containsOnly(
                    METRIC_NAME_STRING to METRIC_JSON_STRING,
                )
                prop(MetricReport::hrt).isNotNull().text()
                    .transform { BortSharedJson.decodeFromString(HighResTelemetry.serializer(), it) }
                    .all {
                        prop(HighResTelemetry::rollups).containsExactlyInAnyOrder(
                            *metricsDbTestEnvironment.dropBoxTagCountRollups(heartbeatTimestamp + 1).toTypedArray(),

                            rollup(
                                name = METRIC_NAME_STRING,
                                value = METRIC_JSON_STRING,
                                dataType = StringType,
                                internal = true,
                                timestampMs = heartbeatTimestamp,
                            ),
                        )
                    }
            }
        }
    }

    @Test
    fun storedBool() = runTest {
        val heartbeatTimestamp = System.currentTimeMillis()

        propertiesStore.upsert(
            name = METRIC_NAME_BOOL,
            value = METRIC_VALUE_BOOL,
            internal = false,
            timestamp = heartbeatTimestamp,
        )

        assertThat(
            metricsDbTestEnvironment.dao.collectHeartbeat(
                endTimestampMs = heartbeatTimestamp + 1,
            ),
        ).all {
            prop(CustomReport::hourlyHeartbeatReport).all {
                prop(MetricReport::metrics).containsOnly(
                    METRIC_NAME_BOOL.latest() to METRIC_JSON_BOOL_REPORT,
                )
                prop(MetricReport::internalMetrics).isEmpty()
                prop(MetricReport::hrt).isNotNull().text()
                    .transform { BortSharedJson.decodeFromString(HighResTelemetry.serializer(), it) }
                    .all {
                        prop(HighResTelemetry::rollups).containsExactlyInAnyOrder(
                            *metricsDbTestEnvironment.dropBoxTagCountRollups(heartbeatTimestamp + 1).toTypedArray(),

                            rollup(
                                name = METRIC_NAME_BOOL,
                                value = METRIC_JSON_BOOL_HRT,
                                dataType = BooleanType,
                                internal = false,
                                timestampMs = heartbeatTimestamp,
                            ),
                        )
                    }
            }
        }
    }

    @Test
    fun storedString() = runTest {
        val heartbeatTimestamp = System.currentTimeMillis()

        propertiesStore.upsert(
            name = METRIC_NAME_STRING,
            value = METRIC_VALUE_STRING,
            internal = false,
            heartbeatTimestamp,
        )

        assertThat(
            metricsDbTestEnvironment.dao.collectHeartbeat(
                endTimestampMs = heartbeatTimestamp,
            ),
        ).all {
            prop(CustomReport::hourlyHeartbeatReport).all {
                prop(MetricReport::metrics).containsOnly(
                    METRIC_NAME_STRING.latest() to JsonPrimitive(METRIC_VALUE_STRING),
                )
                prop(MetricReport::internalMetrics).isEmpty()
                prop(MetricReport::hrt).isNotNull().text()
                    .transform { BortSharedJson.decodeFromString(HighResTelemetry.serializer(), it) }
                    .all {
                        prop(HighResTelemetry::rollups).containsExactlyInAnyOrder(
                            *metricsDbTestEnvironment.dropBoxTagCountRollups(heartbeatTimestamp).toTypedArray(),

                            rollup(
                                name = METRIC_NAME_STRING,
                                value = JsonPrimitive(METRIC_VALUE_STRING),
                                dataType = StringType,
                                internal = false,
                                timestampMs = heartbeatTimestamp,
                            ),
                        )
                    }
            }
        }
    }

    @Test
    fun storedInternalLong() = runTest {
        val heartbeatTimestamp = System.currentTimeMillis()

        propertiesStore.upsert(
            name = METRIC_NAME_LONG,
            value = METRIC_VALUE_LONG,
            internal = true,
            timestamp = heartbeatTimestamp,
        )

        assertThat(
            metricsDbTestEnvironment.dao.collectHeartbeat(
                endTimestampMs = heartbeatTimestamp + 1,
            ),
        ).all {
            prop(CustomReport::hourlyHeartbeatReport).all {
                prop(MetricReport::metrics).isEmpty()
                prop(MetricReport::internalMetrics).containsOnly(
                    METRIC_NAME_LONG to METRIC_JSON_LONG,
                )
                prop(MetricReport::hrt).isNotNull().text()
                    .transform { BortSharedJson.decodeFromString(HighResTelemetry.serializer(), it) }
                    .all {
                        prop(HighResTelemetry::rollups).containsExactlyInAnyOrder(
                            *metricsDbTestEnvironment.dropBoxTagCountRollups(heartbeatTimestamp + 1).toTypedArray(),

                            rollup(
                                name = METRIC_NAME_LONG,
                                value = METRIC_JSON_LONG,
                                dataType = DoubleType,
                                internal = true,
                                timestampMs = heartbeatTimestamp,
                            ),
                        )
                    }
            }
        }
    }

    @Test
    fun storedMultiple() = runTest {
        val heartbeatTimestamp = System.currentTimeMillis()

        propertiesStore.upsert(
            name = METRIC_NAME_DOUBLE,
            value = METRIC_VALUE_DOUBLE,
            internal = false,
            heartbeatTimestamp,
        )
        propertiesStore.upsert(name = METRIC_NAME_LONG, value = METRIC_VALUE_LONG, internal = true, heartbeatTimestamp)
        propertiesStore.upsert(name = METRIC_NAME_INT, value = METRIC_VALUE_INT, internal = true, heartbeatTimestamp)
        propertiesStore.upsert(name = METRIC_NAME_BOOL, value = METRIC_VALUE_BOOL, internal = false, heartbeatTimestamp)
        propertiesStore.upsert(
            name = METRIC_NAME_STRING,
            value = "initial_val",
            internal = false,
            heartbeatTimestamp,
        )
        propertiesStore.upsert(
            name = METRIC_NAME_STRING,
            value = METRIC_VALUE_STRING,
            internal = false,
            heartbeatTimestamp + 1,
        )

        assertThat(
            metricsDbTestEnvironment.dao.collectHeartbeat(
                endTimestampMs = heartbeatTimestamp + 1,
            ),
        ).all {
            prop(CustomReport::hourlyHeartbeatReport).all {
                prop(MetricReport::metrics).containsOnly(
                    METRIC_NAME_DOUBLE.latest() to METRIC_JSON_DOUBLE,
                    METRIC_NAME_BOOL.latest() to METRIC_JSON_BOOL_REPORT,
                    METRIC_NAME_STRING.latest() to METRIC_JSON_STRING,
                )
                prop(MetricReport::internalMetrics).containsOnly(
                    METRIC_NAME_LONG to METRIC_JSON_LONG,
                    METRIC_NAME_INT to METRIC_JSON_INT,
                )
                prop(MetricReport::hrt).isNotNull().text()
                    .transform { BortSharedJson.decodeFromString(HighResTelemetry.serializer(), it) }
                    .all {
                        prop(HighResTelemetry::rollups).containsExactlyInAnyOrder(
                            *metricsDbTestEnvironment.dropBoxTagCountRollups(heartbeatTimestamp + 1).toTypedArray(),

                            rollup(
                                name = METRIC_NAME_DOUBLE,
                                value = METRIC_JSON_DOUBLE,
                                dataType = DoubleType,
                                internal = false,
                                timestampMs = heartbeatTimestamp,
                            ),
                            rollup(
                                name = METRIC_NAME_LONG,
                                value = METRIC_JSON_LONG,
                                dataType = DoubleType,
                                internal = true,
                                timestampMs = heartbeatTimestamp,
                            ),
                            rollup(
                                name = METRIC_NAME_INT,
                                value = METRIC_JSON_INT,
                                dataType = DoubleType,
                                internal = true,
                                timestampMs = heartbeatTimestamp,
                            ),
                            rollup(
                                name = METRIC_NAME_BOOL,
                                value = METRIC_JSON_BOOL_HRT,
                                dataType = BooleanType,
                                internal = false,
                                timestampMs = heartbeatTimestamp,
                            ),
                            rollup(
                                name = METRIC_NAME_STRING,
                                dataType = StringType,
                                internal = false,
                                data = listOf(
                                    HighResTelemetry.Datum(
                                        t = heartbeatTimestamp,
                                        value = JsonPrimitive("initial_val"),
                                    ),
                                    HighResTelemetry.Datum(
                                        t = heartbeatTimestamp + 1,
                                        value = METRIC_JSON_STRING,
                                    ),
                                ),
                            ),
                        )
                    }
            }
        }
    }
}
