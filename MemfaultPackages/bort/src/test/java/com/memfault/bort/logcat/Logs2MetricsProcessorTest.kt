package com.memfault.bort.logcat

import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.containsOnly
import assertk.assertions.doesNotContainKey
import assertk.assertions.isEmpty
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.prop
import com.memfault.bort.logcat.Logs2MetricsRuleType.CountMatching
import com.memfault.bort.logcat.Logs2MetricsRuleType.Distribution
import com.memfault.bort.logcat.Logs2MetricsRuleType.StringProperty
import com.memfault.bort.logcat.Logs2MetricsRuleType.SumMatching
import com.memfault.bort.logcat.Logs2MetricsRuleType.Unknown
import com.memfault.bort.metrics.HighResTelemetry
import com.memfault.bort.metrics.HighResTelemetry.DataType
import com.memfault.bort.metrics.HighResTelemetry.Datum
import com.memfault.bort.metrics.HighResTelemetry.MetricType
import com.memfault.bort.metrics.HighResTelemetry.RollupMetadata
import com.memfault.bort.metrics.MetricsDbTestEnvironment
import com.memfault.bort.metrics.custom.CustomMetrics
import com.memfault.bort.metrics.custom.CustomReport
import com.memfault.bort.metrics.custom.MetricReport
import com.memfault.bort.parsers.LogcatLine
import com.memfault.bort.parsers.PackageManagerReport
import com.memfault.bort.settings.Logs2MetricsRules
import com.memfault.bort.shared.LogcatFilterSpec
import com.memfault.bort.shared.LogcatPriority.DEBUG
import com.memfault.bort.shared.LogcatPriority.ERROR
import com.memfault.bort.shared.LogcatPriority.INFO
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class Logs2MetricsProcessorTest {
    @get:Rule
    val metricsDbTestEnvironment = MetricsDbTestEnvironment().apply {
        highResMetricsEnabledValue = true
    }

    private val dao: CustomMetrics get() = metricsDbTestEnvironment.dao
    private val rules = Logs2MetricsRules {
        listOf(
            Logs2MetricsRule(
                type = CountMatching,
                filter = LogcatFilterSpec(tag = "SystemService", priority = INFO),
                pattern = "tag: Scheduled restart job, restart counter is at .*",
                metricName = "counter_metric",
            ),
            Logs2MetricsRule(
                type = CountMatching,
                filter = LogcatFilterSpec(tag = "bort", priority = DEBUG),
                pattern = "(.*): Running logcat...",
                metricName = "logcat_\$1",
            ),
            Logs2MetricsRule(
                type = CountMatching,
                filter = LogcatFilterSpec(tag = "bort", priority = ERROR),
                pattern = "batterystats error: (\\d+)",
                metricName = "batterystats_$1",
            ),
            Logs2MetricsRule(
                type = CountMatching,
                filter = LogcatFilterSpec(tag = "bort", priority = ERROR),
                pattern = "batterystats error: (\\d+)",
                metricName = "batterystats_b_$1_$2",
            ),
            Logs2MetricsRule(
                type = Unknown,
                filter = LogcatFilterSpec(tag = "bort", priority = ERROR),
                pattern = "bad_rule_pattern",
                metricName = "batterystats_bad_type",
            ),
            Logs2MetricsRule(
                type = SumMatching,
                filter = LogcatFilterSpec(tag = "Choreographer", priority = INFO),
                pattern = "^Skipped (\\d+) frames!  The application may be doing too much work on its main thread.$",
                metricName = "frames_dropped",
            ),
            Logs2MetricsRule(
                type = Distribution,
                filter = LogcatFilterSpec(tag = "Metrics", priority = INFO),
                pattern = "^Batch [A-Fa-f0-9]{8}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{12} " +
                    "\\[(\\d+) bytes\\] \\(Request\\) sent successfully.",
                metricName = "request_size",
            ),
            Logs2MetricsRule(
                type = StringProperty,
                filter = LogcatFilterSpec(tag = "ConnectivityService", priority = ERROR),
                pattern = "^BUG: NetworkAgentInfo\\{.*lost immutable capabilities:specifier changed.*BSSID=.*BSSID=" +
                    "([A-Fa-f0-9]{2}:[A-Fa-f0-9]{2}:[A-Fa-f0-9]{2}:[A-Fa-f0-9]{2}:[A-Fa-f0-9]{2}:[A-Fa-f0-9]{2}).*$",
                metricName = "wifi_bssid",
            ),
        )
    }
    private val processor = Logs2MetricsProcessor(rules)
    private val timestamp = Instant.now()
    private val heartbeatEndTimestamp = timestamp.plusMillis(1)
    private val packageManagerReport = PackageManagerReport()

    private fun rollup(
        stringKey: String,
        value: JsonPrimitive,
        t: Long,
        metricType: MetricType = MetricType.Event,
        dataType: DataType = DataType.StringType,
        internal: Boolean = false,
    ) = HighResTelemetry.Rollup(
        metadata = RollupMetadata(
            stringKey = stringKey,
            metricType = metricType,
            dataType = dataType,
            internal = internal,
        ),
        data = listOf(
            Datum(
                t = t,
                value = value,
            ),
        ),
    )

    private fun rollup(
        stringKey: String,
        metricType: MetricType,
        dataType: DataType,
        internal: Boolean = false,
        data: List<Datum>,
    ) = HighResTelemetry.Rollup(
        metadata = RollupMetadata(
            stringKey = stringKey,
            metricType = metricType,
            dataType = dataType,
            internal = internal,
        ),
        data = data,
    )

    @Test
    fun incrementCounter() = runTest {
        processor.process(
            LogcatLine(
                logTime = timestamp,
                message = "tag: Scheduled restart job, restart counter is at somethingsomething",
                tag = "SystemService",
                priority = INFO,
            ),
            packageManagerReport,
        )
        assertThat(dao.collectHeartbeat(endTimestampMs = heartbeatEndTimestamp.toEpochMilli())).all {
            prop(CustomReport::hourlyHeartbeatReport).all {
                prop(MetricReport::metrics).containsOnly(
                    "counter_metric.count" to JsonPrimitive(1),
                )

                prop(MetricReport::hrt).isNotNull()
                    .transform { hrt -> HighResTelemetry.decodeFromStream(hrt) }
                    .prop(HighResTelemetry::rollups)
                    .containsExactlyInAnyOrder(
                        *metricsDbTestEnvironment.dropBoxTagCountRollups(heartbeatEndTimestamp.toEpochMilli())
                            .toTypedArray(),

                        rollup(
                            stringKey = "counter_metric",
                            value = JsonPrimitive(
                                "tag: Scheduled restart job, restart counter is at somethingsomething",
                            ),
                            t = timestamp.toEpochMilli(),
                        ),
                    )
            }
        }
    }

    @Test
    fun metricNameInterpolation() = runTest {
        processor.process(
            LogcatLine(
                logTime = timestamp,
                message = "hello1: Running logcat...",
                tag = "bort",
                priority = DEBUG,
            ),
            packageManagerReport,
        )
        assertThat(dao.collectHeartbeat(endTimestampMs = heartbeatEndTimestamp.toEpochMilli())).all {
            prop(CustomReport::hourlyHeartbeatReport).all {
                prop(MetricReport::metrics).containsOnly(
                    "logcat_hello1.count" to JsonPrimitive(1),
                )

                prop(MetricReport::hrt).isNotNull()
                    .transform { hrt -> HighResTelemetry.decodeFromStream(hrt) }
                    .prop(HighResTelemetry::rollups)
                    .containsExactlyInAnyOrder(
                        *metricsDbTestEnvironment.dropBoxTagCountRollups(heartbeatEndTimestamp.toEpochMilli())
                            .toTypedArray(),

                        rollup(
                            stringKey = "logcat_hello1",
                            value = JsonPrimitive("hello1: Running logcat..."),
                            t = timestamp.toEpochMilli(),
                        ),
                    )
            }
        }
    }

    @Test
    fun metricNameInterpolation_extraGroup() = runTest {
        processor.process(
            LogcatLine(
                logTime = timestamp,
                message = "batterystats error: 467",
                tag = "bort",
                priority = ERROR,
            ),
            packageManagerReport,
        )
        assertThat(dao.collectHeartbeat(endTimestampMs = heartbeatEndTimestamp.toEpochMilli())).all {
            prop(CustomReport::hourlyHeartbeatReport).all {
                prop(MetricReport::metrics).containsOnly(
                    "batterystats_467.count" to JsonPrimitive(1),
                )
                // 2nd group didn't exist, so no match/no metric.
                prop(MetricReport::metrics).doesNotContainKey("batterystats_b_$1_$2.count")

                prop(MetricReport::hrt).isNotNull()
                    .transform { hrt -> HighResTelemetry.decodeFromStream(hrt) }
                    .prop(HighResTelemetry::rollups)
                    .containsExactlyInAnyOrder(
                        *metricsDbTestEnvironment.dropBoxTagCountRollups(heartbeatEndTimestamp.toEpochMilli())
                            .toTypedArray(),

                        rollup(
                            stringKey = "batterystats_467",
                            value = JsonPrimitive("batterystats error: 467"),
                            t = timestamp.toEpochMilli(),
                        ),
                    )
            }
        }
    }

    @Test
    fun sumMatching() = runTest {
        processor.process(
            LogcatLine(
                logTime = timestamp.minusSeconds(1),
                message = "Skipped 50 frames!  The application may be doing too much work on its main thread.",
                tag = "Choreographer",
                priority = INFO,
            ),
            packageManagerReport,
        )
        processor.process(
            LogcatLine(
                logTime = timestamp,
                message = "Skipped 150 frames!  The application may be doing too much work on its main thread.",
                tag = "Choreographer",
                priority = INFO,
            ),
            packageManagerReport,
        )
        assertThat(dao.collectHeartbeat(endTimestampMs = heartbeatEndTimestamp.toEpochMilli())).all {
            prop(CustomReport::hourlyHeartbeatReport).all {
                prop(MetricReport::metrics).containsOnly(
                    "frames_dropped.sum" to JsonPrimitive(200.0),
                )

                prop(MetricReport::hrt).isNotNull()
                    .transform { hrt -> HighResTelemetry.decodeFromStream(hrt) }
                    .prop(HighResTelemetry::rollups)
                    .containsExactlyInAnyOrder(
                        *metricsDbTestEnvironment.dropBoxTagCountRollups(heartbeatEndTimestamp.toEpochMilli())
                            .toTypedArray(),

                        rollup(
                            stringKey = "frames_dropped",
                            metricType = MetricType.Counter,
                            dataType = DataType.DoubleType,
                            data = listOf(
                                Datum(
                                    value = JsonPrimitive(50.0),
                                    t = timestamp.minusSeconds(1).toEpochMilli(),
                                ),
                                Datum(
                                    value = JsonPrimitive(150.0),
                                    t = timestamp.toEpochMilli(),
                                ),
                            ),
                        ),
                    )
            }
        }
    }

    @Test
    fun distribution() = runTest {
        processor.process(
            LogcatLine(
                logTime = timestamp.minusSeconds(2),
                message = "Batch 00000000-2a04-420a-bcbc-000000000000 [100 bytes] (Request) sent successfully.",
                tag = "Metrics",
                priority = INFO,
            ),
            packageManagerReport,
        )
        processor.process(
            LogcatLine(
                logTime = timestamp.minusSeconds(1),
                message = "Batch 00000000-2a04-420a-bcbc-000000000000 [500 bytes] (Request) sent successfully.",
                tag = "Metrics",
                priority = INFO,
            ),
            packageManagerReport,
        )
        processor.process(
            LogcatLine(
                logTime = timestamp,
                message = "Batch 00000000-2a04-420a-bcbc-000000000000 [900 bytes] (Request) sent successfully.",
                tag = "Metrics",
                priority = INFO,
            ),
            packageManagerReport,
        )
        assertThat(dao.collectHeartbeat(endTimestampMs = heartbeatEndTimestamp.toEpochMilli())).all {
            prop(CustomReport::hourlyHeartbeatReport).all {
                prop(MetricReport::metrics).containsOnly(
                    "request_size.min" to JsonPrimitive(100.0),
                    "request_size.mean" to JsonPrimitive(500.0),
                    "request_size.max" to JsonPrimitive(900.0),
                )

                prop(MetricReport::hrt).isNotNull()
                    .transform { hrt -> HighResTelemetry.decodeFromStream(hrt) }
                    .prop(HighResTelemetry::rollups)
                    .containsExactlyInAnyOrder(
                        *metricsDbTestEnvironment.dropBoxTagCountRollups(heartbeatEndTimestamp.toEpochMilli())
                            .toTypedArray(),

                        rollup(
                            stringKey = "request_size",
                            metricType = MetricType.Gauge,
                            dataType = DataType.DoubleType,
                            data = listOf(
                                Datum(
                                    value = JsonPrimitive(100.0),
                                    t = timestamp.minusSeconds(2).toEpochMilli(),
                                ),
                                Datum(
                                    value = JsonPrimitive(500.0),
                                    t = timestamp.minusSeconds(1).toEpochMilli(),
                                ),
                                Datum(
                                    value = JsonPrimitive(900.0),
                                    t = timestamp.toEpochMilli(),
                                ),
                            ),
                        ),
                    )
            }
        }
    }

    @Test
    fun stringProperty() = runTest {
        processor.process(
            LogcatLine(
                logTime = timestamp,
                message = "BUG: NetworkAgentInfo{network{100} BSSID=b0:1f:8c:50:ff:50, " +
                    "band=2, factorySerialNumber=1} lost immutable capabilities:specifier changed: " +
                    "WifiNetworkAgentSpecifier [WifiConfiguration=, SSID=${'"'}Guest${'"'}, BSSID=b0:1f:8c:50:ff:50, " +
                    "band=2, mMatchLocalOnlySpecifiers=false] -> WifiNetworkAgentSpecifier [WifiConfiguration=, " +
                    "SSID=${'"'}Guest${'"'}, BSSID=b0:1f:8c:00:ff:00, band=2, mMatchLocalOnlySpecifiers=false]",
                tag = "ConnectivityService",
                priority = ERROR,
            ),
            packageManagerReport,
        )
        assertThat(dao.collectHeartbeat(endTimestampMs = heartbeatEndTimestamp.toEpochMilli())).all {
            prop(CustomReport::hourlyHeartbeatReport).all {
                prop(MetricReport::metrics).containsOnly(
                    "wifi_bssid.latest" to JsonPrimitive("b0:1f:8c:00:ff:00"),
                )

                prop(MetricReport::hrt).isNotNull()
                    .transform { hrt -> HighResTelemetry.decodeFromStream(hrt) }
                    .prop(HighResTelemetry::rollups)
                    .containsExactlyInAnyOrder(
                        *metricsDbTestEnvironment.dropBoxTagCountRollups(heartbeatEndTimestamp.toEpochMilli())
                            .toTypedArray(),

                        rollup(
                            stringKey = "wifi_bssid",
                            metricType = MetricType.Property,
                            dataType = DataType.StringType,
                            data = listOf(
                                Datum(
                                    value = JsonPrimitive("b0:1f:8c:00:ff:00"),
                                    t = timestamp.toEpochMilli(),
                                ),
                            ),
                        ),
                    )
            }
        }
    }

    @Test
    fun unknownRuleTypeNotProcessed() = runTest {
        processor.process(
            LogcatLine(
                logTime = timestamp,
                message = "bad_rule_pattern",
                tag = "bort",
                priority = ERROR,
            ),
            packageManagerReport,
        )
        assertThat(dao.collectHeartbeat(endTimestampMs = heartbeatEndTimestamp.toEpochMilli())).all {
            prop(CustomReport::hourlyHeartbeatReport).all {
                prop(MetricReport::metrics).doesNotContainKey("batterystats_bad_type.count")
            }
        }
    }

    @Test
    fun filterMismatch_priority() = runTest {
        processor.process(
            LogcatLine(
                logTime = timestamp,
                message = "something: Scheduled restart job, restart counter is at 684",
                tag = "SystemService",
                priority = DEBUG,
            ),
            packageManagerReport,
        )
        assertThat(dao.collectHeartbeat(endTimestampMs = heartbeatEndTimestamp.toEpochMilli())).all {
            prop(CustomReport::hourlyHeartbeatReport).all {
                prop(MetricReport::metrics).isEmpty()
                prop(MetricReport::hrt).isNull()
            }
        }
    }

    @Test
    fun filterMismatch_tag() = runTest {
        processor.process(
            LogcatLine(
                logTime = timestamp,
                message = "something: Scheduled restart job, restart counter is at 684",
                tag = "SystemService2",
                priority = INFO,
            ),
            packageManagerReport,
        )
        assertThat(
            dao.collectHeartbeat(endTimestampMs = heartbeatEndTimestamp.toEpochMilli()),
        ).all {
            prop(CustomReport::hourlyHeartbeatReport).all {
                prop(MetricReport::metrics).isEmpty()
                prop(MetricReport::hrt).isNull()
            }
        }
    }
}
