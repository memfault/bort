package com.memfault.bort.metrics

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import assertk.all
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.prop
import assertk.assertions.text
import com.memfault.bort.metrics.custom.CustomReport
import com.memfault.bort.metrics.custom.MetricReport
import com.memfault.bort.metrics.database.CalculateDerivedAggregations
import com.memfault.bort.metrics.database.DbReportBuilder
import com.memfault.bort.metrics.database.HOURLY_HEARTBEAT_REPORT_TYPE
import com.memfault.bort.metrics.database.HrtFileFactory
import com.memfault.bort.metrics.database.MetricsDao
import com.memfault.bort.metrics.database.MetricsDb
import com.memfault.bort.reporting.AggregationType
import com.memfault.bort.reporting.DataType
import com.memfault.bort.reporting.MetricType
import com.memfault.bort.reporting.MetricValue
import com.memfault.bort.reporting.NumericAgg
import com.memfault.bort.reporting.NumericAgg.COUNT
import com.memfault.bort.reporting.NumericAgg.MAX
import com.memfault.bort.reporting.NumericAgg.MEAN
import com.memfault.bort.reporting.NumericAgg.MIN
import com.memfault.bort.reporting.NumericAgg.SUM
import com.memfault.bort.reporting.StateAgg
import com.memfault.bort.reporting.StateAgg.TIME_PER_HOUR
import com.memfault.bort.reporting.StateAgg.TIME_TOTALS
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import kotlin.time.Duration.Companion.hours

@RunWith(RobolectricTestRunner::class)
class MetricsDbRegressionTest {
    @get:Rule val tempFolder: TemporaryFolder = TemporaryFolder.builder().assureDeletion().build()

    private lateinit var db: MetricsDb
    private lateinit var dao: MetricsDao

    private val dbReportBuilder = DbReportBuilder { report ->
        report.copy(
            softwareVersion = "Android/aosp_arm64/generic_arm64:8.1.0/OC/root04302340:eng/test-keys::1.0.0",
        )
    }

    private suspend fun MetricsDao.insert(metricValue: MetricValue): Long =
        insert(metric = metricValue, dbReportBuilder = dbReportBuilder)

    private suspend fun MetricsDao.collectHeartbeat(
        hourlyHeartbeatReportType: String = HOURLY_HEARTBEAT_REPORT_TYPE,
        dailyHeartbeatReportType: String? = null,
        endTimestampMs: Long,
        hrtFileFactory: HrtFileFactory?,
        calculateDerivedAggregations: CalculateDerivedAggregations = CalculateDerivedAggregations { _, _, _, _ ->
            emptyList()
        },
        dailyHeartbeatReportMetricsForSessions: List<String>? = null,
    ): CustomReport = collectHeartbeat(
        hourlyHeartbeatReportType = hourlyHeartbeatReportType,
        dailyHeartbeatReportType = dailyHeartbeatReportType,
        endTimestampMs = endTimestampMs,
        hrtFileFactory = hrtFileFactory,
        calculateDerivedAggregations = calculateDerivedAggregations,
        dailyHeartbeatReportMetricsForSessions = dailyHeartbeatReportMetricsForSessions,
        dbReportBuilder = dbReportBuilder,
    )

    @Before
    fun createDB() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MetricsDb::class.java)
            .fallbackToDestructiveMigration()
            .allowMainThreadQueries()
            .build()
        dao = db.dao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun `regression HappyPathVersion1`() = runTest {
        dao.insert(
            MetricValue.fromJson(
                """
                {
                    "version": 1,
                    "timestampMs": 123456789,
                    "reportType": "heartbeat",
                    "eventName": "Screen State",
                    "aggregations": ["TIME_TOTALS", "UNKNOWN_AGGREGATION"],
                    "value": "On",
                    "newField": "newValue"
                }
                """,
            ),
            dbReportBuilder = dbReportBuilder,
        )

        dao.insert(
            MetricValue.fromJson(
                """
                {
                    "version": 1,
                    "timestampMs": 123456789,
                    "reportType": "heartbeat",
                    "eventName": "CPU Usage",
                    "aggregations": ["MIN", "MAX", "MEAN"],
                    "value": 6.34,
                    "newField": "newValue"
                }
                """,
            ),
        )

        val hrtFile = tempFolder.newFile()
        val hrtFileFactory = HrtFileFactory { hrtFile }

        val report = dao.collectHeartbeat("heartbeat", null, 123456789, hrtFileFactory = hrtFileFactory)

        assertThat(report.hourlyHeartbeatReport).all {
            prop(MetricReport::version).isEqualTo(1)
            prop(MetricReport::startTimestampMs).isEqualTo(123456789L)
            prop(MetricReport::endTimestampMs).isEqualTo(123456789L)
            prop(MetricReport::metrics)
                .isEqualTo(
                    mapOf(
                        "CPU Usage.min" to JsonPrimitive(6.34),
                        "CPU Usage.max" to JsonPrimitive(6.34),
                        "CPU Usage.mean" to JsonPrimitive(6.34),
                        "Screen State_On.total_secs" to JsonPrimitive(0),
                    ),
                )

            // check hd reports
            prop(MetricReport::hrt).isNotNull().text().isEqualTo(
                """{"schema_version":1,"start_time":123456789,"duration_ms":0,""" +
                    """"report_type":"heartbeat","producer":{"version":"1","id":"bort"},""" +
                    """"rollups":[{"metadata":{"string_key":"CPU Usage","metric_type":"gauge",""" +
                    """"data_type":"double","internal":false},"data":[{"t":123456789,"value":6.34}]},""" +
                    """{"metadata":{"string_key":"Screen State","metric_type":"property","data_type":"string",""" +
                    """"internal":false},"data":[{"t":123456789,"value":"On"}]}]}""",
            )
        }
    }

    @Test
    fun `regression TypeConsistency`() = runTest {
        dao.insert(
            MetricValue.fromJson(
                """
                {
                    "version": 1,
                    "timestampMs": 123456789,
                    "reportType": "heartbeat",
                    "eventName": "public_metric",
                    "aggregations": ["MIN"],
                    "value": 3
                }
                """,
            ),
        )

        dao.insert(
            MetricValue.fromJson(
                """
                {
                    "version": 1,
                    "timestampMs": 123456789,
                    "reportType": "heartbeat",
                    "eventName": "internal_metric",
                    "internal": true,
                    "aggregations": ["LATEST_VALUE"],
                    "value": "a"
                }
                """,
            ),
        )

        val report = dao.collectHeartbeat("heartbeat", null, 123456789, null)

        assertThat(report.hourlyHeartbeatReport).all {
            prop(MetricReport::version).isEqualTo(1)
            prop(MetricReport::startTimestampMs).isEqualTo(123456789)
            prop(MetricReport::endTimestampMs).isEqualTo(123456789)
            prop(MetricReport::reportType).isEqualTo("heartbeat")
            prop(MetricReport::metrics).isEqualTo(mapOf("public_metric.min" to JsonPrimitive(3.0)))
            prop(MetricReport::internalMetrics)
                .isEqualTo(
                    mapOf(
                        // Note that the .latest gets removed by [CustomMetrics], which isn't under test here.
                        "internal_metric.latest" to JsonPrimitive("a"),
                    ),
                )
        }
    }

    @Test
    fun `regression BasicRollupTest`() = runTest {
        dao.insert(
            MetricValue.fromJson(
                """
                {
                    "version": 3,
                    "timestampMs": 123456789,
                    "reportType": "heartbeat",
                    "eventName": "cpu_spiked",
                    "aggregations": ["COUNT"],
                    "dataType": "double",
                    "metricType": "counter",
                    "carryOver": false,
                    "value": 1
                }
                """,
            ),
        )

        dao.insert(
            MetricValue.fromJson(
                """
                {
                    "version": 3,
                    "timestampMs": 123456790,
                    "reportType": "heartbeat",
                    "eventName": "cpu_spiked",
                    "aggregations": ["COUNT"],
                    "dataType": "double",
                    "metricType": "counter",
                    "carryOver": false,
                    "value": 1
                }
                """,
            ),
        )

        dao.insert(
            MetricValue.fromJson(
                """
                {
                    "version": 3,
                    "timestampMs": 123456791,
                    "reportType": "heartbeat",
                    "eventName": "cpu_spiked",
                    "aggregations": ["COUNT"],
                    "dataType": "double",
                    "metricType": "counter",
                    "carryOver": false,
                    "value": 1
                }
                """,
            ),
        )

        val hrtFile = tempFolder.newFile()

        dao.collectHeartbeat(
            hourlyHeartbeatReportType = "heartbeat",
            endTimestampMs = 123456800,
            hrtFileFactory = { hrtFile },
        )

        @Suppress("LineLength")
        assertThat(hrtFile).text().isEqualTo(
            """{"schema_version":1,"start_time":123456789,"duration_ms":11,"report_type":"heartbeat","producer":{"version":"1","id":"bort"},"rollups":[{"metadata":{"string_key":"cpu_spiked","metric_type":"counter","data_type":"double","internal":false},"data":[{"t":123456789,"value":1.0},{"t":123456790,"value":1.0},{"t":123456791,"value":1.0}]}]}""",
        )
    }

    @Test
    fun `regression HappyPathVersion2WithHd`() = runTest {
        dao.insert(
            MetricValue.fromJson(
                """
                {
                    "version": 2,
                    "timestampMs": 123456789,
                    "reportType": "heartbeat",
                    "eventName": "screen_on",
                    "aggregations": ["MIN"],
                    "dataType": "boolean",
                    "metricType": "counter",
                    "carryOver": true,
                    "value": false
                }
                """,
            ),
        )

        dao.insert(
            MetricValue.fromJson(
                """
                {
                    "version": 2,
                    "timestampMs": 123456790,
                    "reportType": "heartbeat",
                    "eventName": "screen_on",
                    "aggregations": ["MIN"],
                    "dataType": "boolean",
                    "metricType": "counter",
                    "carryOver": true,
                    "value": true
                }
                """,
            ),
        )

        val hrtFile = tempFolder.newFile()

        val report = dao.collectHeartbeat(
            hourlyHeartbeatReportType = "heartbeat",
            endTimestampMs = 123456791L,
            hrtFileFactory = { hrtFile },
        )

        assertThat(report.hourlyHeartbeatReport).all {
            prop(MetricReport::reportType).isEqualTo("heartbeat")
            prop(MetricReport::startTimestampMs).isEqualTo(123456789)
            prop(MetricReport::endTimestampMs).isEqualTo(123456791)
            prop(MetricReport::version).isEqualTo(1)
            prop(MetricReport::metrics).isEqualTo(
                mapOf("screen_on.min" to JsonPrimitive(0.0)),
            )

            @Suppress("ktlint:standard:max-line-length")
            assertThat(hrtFile).text().isEqualTo(
                """{"schema_version":1,"start_time":123456789,"duration_ms":2,"report_type":"heartbeat","producer":{"version":"1","id":"bort"},"rollups":[{"metadata":{"string_key":"screen_on","metric_type":"counter","data_type":"boolean","internal":false},"data":[{"t":123456789,"value":false},{"t":123456790,"value":true}]}]}""",
            )
        }
    }

    @Test
    fun `regression Version2CarryOver`() = runTest {
        dao.insert(
            MetricValue.fromJson(
                """
                {
                    "version": 2,
                    "timestampMs": 123456789,
                    "reportType": "heartbeat",
                    "eventName": "screen_on",
                    "aggregations": ["MIN"],
                    "dataType": "boolean",
                    "metricType": "counter",
                    "carryOver": true,
                    "value": false
                }
                """,
            ),
        )

        dao.insert(
            MetricValue.fromJson(
                """
                {
                    "version": 2,
                    "timestampMs": 123456789,
                    "reportType": "heartbeat",
                    "eventName": "screen_on",
                    "aggregations": ["MIN"],
                    "dataType": "boolean",
                    "metricType": "counter",
                    "carryOver": true,
                    "value": false
                }
                """,
            ),
        )

        dao.insert(
            MetricValue.fromJson(
                """
                {
                    "version": 2,
                    "timestampMs": 123456790,
                    "reportType": "heartbeat",
                    "eventName": "screen_no_carry",
                    "aggregations": ["MIN"],
                    "dataType": "boolean",
                    "metricType": "counter",
                    "carryOver": false,
                    "value": true
                }
                """,
            ),
        )

        dao.insert(
            MetricValue.fromJson(
                """
                {
                    "version": 2,
                    "timestampMs": 123456790,
                    "reportType": "heartbeat",
                    "eventName": "screen_on",
                    "aggregations": ["MIN"],
                    "dataType": "boolean",
                    "metricType": "counter",
                    "carryOver": true,
                    "value": true
                }
                """,
            ),
        )

        dao.insert(
            MetricValue.fromJson(
                """
                {
                    "version": 2,
                    "timestampMs": 123456791,
                    "reportType": "heartbeat",
                    "eventName": "screen_off",
                    "aggregations": ["MIN"],
                    "dataType": "boolean",
                    "metricType": "counter",
                    "carryOver": true,
                    "value": true
                }
                """,
            ),
        )

        val hrtFile1 = tempFolder.newFile()

        dao.collectHeartbeat(
            hourlyHeartbeatReportType = "heartbeat",
            endTimestampMs = 123456791,
            hrtFileFactory = { hrtFile1 },
        )

        val hrtFile2 = tempFolder.newFile()

        dao.collectHeartbeat(
            hourlyHeartbeatReportType = "heartbeat",
            endTimestampMs = 123456792,
            hrtFileFactory = { hrtFile2 },
        )

        assertThat(hrtFile2).text().isEqualTo(
            """{"schema_version":1,"start_time":123456791,"duration_ms":1,"report_type":"heartbeat","producer":{"version":"1","id":"bort"},"rollups":[{"metadata":{"string_key":"screen_off","metric_type":"counter","data_type":"boolean","internal":false},"data":[{"t":123456791,"value":true}]},{"metadata":{"string_key":"screen_on","metric_type":"counter","data_type":"boolean","internal":false},"data":[{"t":123456791,"value":true}]}]}""",
        )
    }

    @Test
    fun `regression Version2Types`() = runTest {
        dao.insert(
            MetricValue.fromJson(
                """
                {
                    "version": 2,
                    "timestampMs": 123456789,
                    "reportType": "heartbeat",
                    "eventName": "screen_on",
                    "aggregations": ["MIN"],
                    "dataType": "boolean",
                    "metricType": "counter",
                    "carryOver": true,
                    "value": false
                }
                """,
            ),
        )

        dao.insert(
            MetricValue.fromJson(
                """
                {
                    "version": 2,
                    "timestampMs": 123456789,
                    "reportType": "heartbeat",
                    "eventName": "screen_type",
                    "aggregations": ["MIN"],
                    "dataType": "string",
                    "metricType": "counter",
                    "carryOver": true,
                    "value": "1"
                }
                """,
            ),
        )

        dao.insert(
            MetricValue.fromJson(
                """
                {
                    "version": 2,
                    "timestampMs": 123456790,
                    "reportType": "heartbeat",
                    "eventName": "screen_brightness",
                    "aggregations": ["MIN"],
                    "dataType": "double",
                    "metricType": "counter",
                    "carryOver": false,
                    "value": 2.0
                }
                """,
            ),
        )

        val hrtFile = tempFolder.newFile()

        dao.collectHeartbeat(
            hourlyHeartbeatReportType = "heartbeat",
            endTimestampMs = 123456791,
            hrtFileFactory = { hrtFile },
        )

        assertThat(hrtFile).text().isEqualTo(
            """{"schema_version":1,"start_time":123456789,"duration_ms":2,"report_type":"heartbeat","producer":{"version":"1","id":"bort"},"rollups":[{"metadata":{"string_key":"screen_brightness","metric_type":"counter","data_type":"double","internal":false},"data":[{"t":123456790,"value":2.0}]},{"metadata":{"string_key":"screen_on","metric_type":"counter","data_type":"boolean","internal":false},"data":[{"t":123456789,"value":false}]},{"metadata":{"string_key":"screen_type","metric_type":"counter","data_type":"string","internal":false},"data":[{"t":123456789,"value":"1"}]}]}""",
        )
    }

    @Test
    fun `regression TestAddMulti`() = runTest {
        dao.insert(
            MetricValue.fromJson(
                """
                {
                    "version": 1,
                    "timestampMs": 123456789,
                    "reportType": "heartbeat",
                    "eventName": "Screen State",
                    "aggregations": ["TIME_TOTALS", "UNKNOWN_AGGREGATION"],
                    "value": "On",
                    "newField": "newValue"
                }
                """,
            ),
        )

        dao.insert(
            MetricValue.fromJson(
                """
                {
                    "version": 1,
                    "timestampMs": 123456789,
                    "reportType": "heartbeat",
                    "eventName": "CPU Usage",
                    "aggregations": ["MIN", "MAX", "MEAN"],
                    "value": 6.34,
                    "newField": "newValue"
                }
                """,
            ),
        )

        val report = dao.collectHeartbeat(
            hourlyHeartbeatReportType = "heartbeat",
            endTimestampMs = 123456789,
            hrtFileFactory = null,
        )

        assertThat(report.hourlyHeartbeatReport).all {
            prop(MetricReport::reportType).isEqualTo("heartbeat")
            prop(MetricReport::startTimestampMs).isEqualTo(123456789)
            prop(MetricReport::endTimestampMs).isEqualTo(123456789)
            prop(MetricReport::version).isEqualTo(1)
            prop(MetricReport::metrics).isEqualTo(
                mapOf(
                    "CPU Usage.min" to JsonPrimitive(6.34),
                    "CPU Usage.max" to JsonPrimitive(6.34),
                    "CPU Usage.mean" to JsonPrimitive(6.34),
                    "Screen State_On.total_secs" to JsonPrimitive(0),
                ),
            )
        }
    }

    @Test
    fun `regression MinMaxTypeCasting`() = runTest {
        dao.insert(
            MetricValue.fromJson(
                """
                {
                    "version": 1,
                    "timestampMs": 123456789,
                    "reportType": "heartbeat",
                    "eventName": "CPU Usage",
                    "aggregations": ["MIN", "MAX", "MEAN"],
                    "value": 90,
                    "newField": "newValue"
                }
                """,
            ),
        )

        dao.insert(
            MetricValue.fromJson(
                """
                {
                    "version": 1,
                    "timestampMs": 123456789,
                    "reportType": "heartbeat",
                    "eventName": "CPU Usage",
                    "aggregations": ["MIN", "MAX", "MEAN"],
                    "value": 100,
                    "newField": "newValue"
                }
                """,
            ),
        )

        val report = dao.collectHeartbeat(
            hourlyHeartbeatReportType = "heartbeat",
            endTimestampMs = 123456789,
            hrtFileFactory = null,
        )

        assertThat(report.hourlyHeartbeatReport).all {
            prop(MetricReport::reportType).isEqualTo("heartbeat")
            prop(MetricReport::startTimestampMs).isEqualTo(123456789)
            prop(MetricReport::endTimestampMs).isEqualTo(123456789)
            prop(MetricReport::version).isEqualTo(1)
            prop(MetricReport::metrics).isEqualTo(
                mapOf(
                    "CPU Usage.min" to JsonPrimitive(90.0),
                    "CPU Usage.max" to JsonPrimitive(100.0),
                    "CPU Usage.mean" to JsonPrimitive(95.0),
                ),
            )
        }
    }

    // Regression test for carryovers not inserting the startTimestamp in the report table
    @Test
    fun `regression CarryOverReportStartTimestampNotSet`() = runTest {
        dao.insert(
            MetricValue.fromJson(
                """
                {
                    "version": 2,
                    "timestampMs": 0,
                    "reportType": "heartbeat",
                    "eventName": "screen_on",
                    "aggregations": ["TIME_PER_HOUR"],
                    "dataType": "string",
                    "metricType": "property",
                    "carryOver": true,
                    "value": "NONE"
                }
                """,
            ),
        )

        dao.insert(
            MetricValue.fromJson(
                """
                {
                    "version": 2,
                    "timestampMs": 1,
                    "reportType": "heartbeat",
                    "eventName": "screen_on",
                    "aggregations": ["TIME_PER_HOUR"],
                    "dataType": "string",
                    "metricType": "property",
                    "carryOver": true,
                    "value": "WIFI"
                }
                """,
            ),
        )

        dao.insert(
            MetricValue.fromJson(
                """
                {
                    "version": 2,
                    "timestampMs": 930000000,
                    "reportType": "heartbeat",
                    "eventName": "screen_on",
                    "aggregations": ["TIME_PER_HOUR"],
                    "dataType": "string",
                    "metricType": "property",
                    "carryOver": true,
                    "value": "NONE"
                }
                """,
            ),
        )

        val hrtFile = tempFolder.newFile()

        val report = dao.collectHeartbeat(
            hourlyHeartbeatReportType = "heartbeat",
            endTimestampMs = 230000000,
            hrtFileFactory = { hrtFile },
        )

        assertThat(report.hourlyHeartbeatReport).all {
            prop(MetricReport::reportType).isEqualTo("heartbeat")
            prop(MetricReport::startTimestampMs).isEqualTo(0)
            prop(MetricReport::endTimestampMs).isEqualTo(230000000)
            prop(MetricReport::version).isEqualTo(1)
            prop(MetricReport::metrics).isEqualTo(
                mapOf(
                    "screen_on_WIFI.secs/hour" to JsonPrimitive(3600),
                    "screen_on_NONE.secs/hour" to JsonPrimitive(0),
                ),
            )
        }
    }

    private fun metricValueKt(
        version: Int,
        reportType: String,
        timestamp: Long,
        eventName: String,
        internal: Boolean,
        aggregations: List<AggregationType>,
        stringVal: String?,
        numberVal: Double?,
        booleanVal: Boolean?,
        dataType: DataType,
        metricType: MetricType,
        carryOver: Boolean,
        reportName: String? = null,
    ): MetricValue = MetricValue(
        eventName,
        reportType,
        aggregations,
        internal,
        metricType,
        dataType,
        carryOver,
        timestamp,
        stringVal,
        numberVal,
        booleanVal,
        version,
        reportName,
    )

    @Test
    fun `regression HappyPathNumerics`() = runTest {
        dao.insert(
            metricValueKt(
                version = 1,
                reportType = "heartbeat",
                timestamp = 2345,
                eventName = "cpu_load",
                internal = false,
                aggregations = listOf(MIN, MAX, SUM, MEAN, COUNT, NumericAgg.LATEST_VALUE),
                stringVal = null,
                numberVal = 1.0,
                booleanVal = null,
                dataType = DataType.DOUBLE,
                metricType = MetricType.GAUGE,
                carryOver = false,
            ),
        )
        dao.insert(
            metricValueKt(
                version = 1,
                reportType = "heartbeat",
                timestamp = 3000,
                eventName = "cpu_load",
                internal = false, aggregations = listOf(MIN, MAX, SUM, MEAN, COUNT, NumericAgg.LATEST_VALUE),
                stringVal = null,
                numberVal = 2.0,
                booleanVal = null,
                dataType = DataType.DOUBLE,
                metricType = MetricType.GAUGE,
                carryOver = false,
            ),
        )

        val report = dao.collectHeartbeat("heartbeat", null, 6789, null)

        assertThat(report.hourlyHeartbeatReport).all {
            prop(MetricReport::metrics).isEqualTo(
                mapOf(
                    "cpu_load.min" to JsonPrimitive(1.0),
                    "cpu_load.max" to JsonPrimitive(2.0),
                    "cpu_load.sum" to JsonPrimitive(3.0),
                    "cpu_load.mean" to JsonPrimitive(1.5),
                    "cpu_load.count" to JsonPrimitive(2),
                    "cpu_load.latest" to JsonPrimitive(2.0),
                ),
            )
        }
    }

    @Test
    fun `regression HappyPathTimetotals`() = runTest {
        dao.insert(
            metricValueKt(
                version = 1,
                reportType = "heartbeat",
                timestamp = 2345,
                eventName = "screen",
                internal = false,
                aggregations = listOf(TIME_TOTALS),
                stringVal = "on",
                numberVal = null,
                booleanVal = null,
                dataType = DataType.STRING,
                metricType = MetricType.PROPERTY,
                carryOver = false,
            ),
        )
        dao.insert(
            metricValueKt(
                version = 1,
                reportType = "heartbeat",
                timestamp = 3345,
                eventName = "screen",
                internal = false,
                aggregations = listOf(TIME_TOTALS),
                stringVal = "off",
                numberVal = null,
                booleanVal = null,
                dataType = DataType.STRING,
                metricType = MetricType.PROPERTY,
                carryOver = false,
            ),
        )
        dao.insert(
            metricValueKt(
                version = 1,
                reportType = "heartbeat",
                timestamp = 4345,
                eventName = "screen",
                internal = false,
                aggregations = listOf(TIME_TOTALS),
                stringVal = "on",
                numberVal = null,
                booleanVal = null,
                dataType = DataType.STRING,
                metricType = MetricType.PROPERTY,
                carryOver = false,
            ),
        )
        dao.insert(
            metricValueKt(
                version = 1,
                reportType = "heartbeat",
                timestamp = 6345,
                eventName = "screen",
                internal = false,
                aggregations = listOf(TIME_TOTALS),
                stringVal = "off",
                numberVal = null,
                booleanVal = null,
                dataType = DataType.STRING,
                metricType = MetricType.PROPERTY,
                carryOver = false,
            ),
        )

        val report = dao.collectHeartbeat("heartbeat", null, 7345, null)

        assertThat(report.hourlyHeartbeatReport).all {
            prop(MetricReport::metrics).isEqualTo(
                mapOf(
                    "screen_on.total_secs" to JsonPrimitive(3),
                    "screen_off.total_secs" to JsonPrimitive(2),
                ),
            )
        }
    }

    @Test
    fun `regression TimePerHour`() = runTest {
        dao.insert(
            metricValueKt(
                version = 1,
                reportType = "heartbeat",
                timestamp = 1634074357043 + 1.hours.inWholeMilliseconds,
                eventName = "screen",
                internal = false,
                aggregations = listOf(TIME_PER_HOUR),
                stringVal = "on",
                numberVal = null,
                booleanVal = null,
                dataType = DataType.STRING,
                metricType = MetricType.PROPERTY,
                carryOver = false,
            ),
        )
        dao.insert(
            metricValueKt(
                version = 1,
                reportType = "heartbeat",
                timestamp = 1634074357043 + 3.hours.inWholeMilliseconds,
                eventName = "screen",
                internal = false,
                aggregations = listOf(TIME_PER_HOUR),
                stringVal = "off",
                numberVal = null,
                booleanVal = null,
                dataType = DataType.STRING,
                metricType = MetricType.PROPERTY,
                carryOver = false,
            ),
        )
        dao.insert(
            metricValueKt(
                version = 1,
                reportType = "heartbeat",
                timestamp = 1634074357043 + 1.hours.inWholeMilliseconds,
                eventName = "gps",
                internal = false,
                aggregations = listOf(TIME_PER_HOUR),
                stringVal = "on",
                numberVal = null,
                booleanVal = null,
                dataType = DataType.STRING,
                metricType = MetricType.PROPERTY,
                carryOver = false,
            ),
        )
        dao.insert(
            metricValueKt(
                version = 1,
                reportType = "heartbeat",
                timestamp = 1634074357043 + 3.hours.inWholeMilliseconds,
                eventName = "gps",
                internal = false,
                aggregations = listOf(TIME_PER_HOUR),
                stringVal = "off",
                numberVal = null,
                booleanVal = null,
                dataType = DataType.STRING,
                metricType = MetricType.PROPERTY,
                carryOver = false,
            ),
        )

        val report = dao.collectHeartbeat("heartbeat", null, 1634074357043 + 4.hours.inWholeMilliseconds, null)

        assertThat(report.hourlyHeartbeatReport).all {
            prop(MetricReport::metrics).isEqualTo(
                mapOf(
                    "screen_on.secs/hour" to JsonPrimitive(2400),
                    "screen_off.secs/hour" to JsonPrimitive(1200),
                    "gps_on.secs/hour" to JsonPrimitive(2400),
                    "gps_off.secs/hour" to JsonPrimitive(1200),
                ),
            )
        }
    }

    @Test
    fun `regression FinishNonExistingReport`() = runTest {
        val report = dao.collectHeartbeat("bogus", null, 12345, null)

        assertThat(report.hourlyHeartbeatReport).all {
            prop(MetricReport::metrics).isEmpty()
            prop(MetricReport::internalMetrics).isEmpty()
        }
    }

    @Test
    fun `regression MultipleReportsCrossContamination`() = runTest {
        dao.insert(
            metricValueKt(
                version = 1,
                reportType = "a",
                timestamp = 67890,
                eventName = "metric_a",
                internal = false,
                aggregations = listOf(SUM),
                stringVal = null,
                numberVal = 1.0,
                booleanVal = null,
                dataType = DataType.DOUBLE,
                metricType = MetricType.GAUGE,
                carryOver = false,
            ),
        )
        dao.insert(
            metricValueKt(
                version = 1,
                reportType = "b",
                timestamp = 67890,
                eventName = "metric_b",
                internal = false,
                aggregations = listOf(SUM),
                stringVal = null,
                numberVal = 1.0,
                booleanVal = null,
                dataType = DataType.DOUBLE,
                metricType = MetricType.GAUGE,
                carryOver = false,
            ),
        )

        val report = dao.collectHeartbeat("a", null, 98765, null)

        assertThat(report.hourlyHeartbeatReport).all {
            prop(MetricReport::metrics).isEqualTo(
                mapOf("metric_a.sum" to JsonPrimitive(1.0)),
            )
        }
    }

    @Test
    fun `regression TwoLastValueMetrics`() = runTest {
        dao.insert(
            metricValueKt(
                version = 1,
                reportType = "a",
                timestamp = 67890,
                eventName = "metric_a",
                internal = false,
                aggregations = listOf(StateAgg.LATEST_VALUE),
                stringVal = null,
                numberVal = 1.0,
                booleanVal = null,
                dataType = DataType.DOUBLE,
                metricType = MetricType.PROPERTY,
                carryOver = false,
            ),
        )
        dao.insert(
            metricValueKt(
                version = 1,
                reportType = "a",
                timestamp = 67890,
                eventName = "metric_b",
                internal = false,
                aggregations = listOf(StateAgg.LATEST_VALUE),
                stringVal = null,
                numberVal = 1.0,
                booleanVal = null,
                dataType = DataType.DOUBLE,
                metricType = MetricType.PROPERTY,
                carryOver = false,
            ),
        )

        val report = dao.collectHeartbeat("a", null, 98765, null)

        assertThat(report.hourlyHeartbeatReport).all {
            prop(MetricReport::metrics).isEqualTo(
                mapOf(
                    "metric_a.latest" to JsonPrimitive(1.0),
                    "metric_b.latest" to JsonPrimitive(1.0),
                ),
            )
        }
    }

    @Test
    fun `regression StartNextReport`() = runTest {
        dao.insert(
            metricValueKt(
                version = 1,
                reportType = "rolling_report",
                timestamp = 67890,
                eventName = "metric_a",
                internal = false,
                aggregations = listOf(StateAgg.LATEST_VALUE),
                stringVal = null,
                numberVal = 1.0,
                booleanVal = null,
                dataType = DataType.DOUBLE,
                metricType = MetricType.PROPERTY,
                carryOver = true,
            ),
        )

        val report1 = dao.collectHeartbeat("rolling_report", null, 98765, null)

        assertThat(report1.hourlyHeartbeatReport).all {
            prop(MetricReport::reportType).isEqualTo("rolling_report")
            prop(MetricReport::startTimestampMs).isEqualTo(67890)
            prop(MetricReport::endTimestampMs).isEqualTo(98765)
            prop(MetricReport::metrics).isEqualTo(
                mapOf("metric_a.latest" to JsonPrimitive(1.0)),
            )
        }

        dao.insert(
            metricValueKt(
                version = 1,
                reportType = "rolling_report",
                timestamp = 100000,
                eventName = "metric_b",
                internal = false,
                aggregations = listOf(StateAgg.LATEST_VALUE),
                stringVal = null,
                numberVal = 1.0,
                booleanVal = null,
                dataType = DataType.DOUBLE,
                metricType = MetricType.PROPERTY,
                carryOver = false,
            ),
        )

        val report2 = dao.collectHeartbeat("rolling_report", null, 100000, null)

        assertThat(report2.hourlyHeartbeatReport).all {
            prop(MetricReport::reportType).isEqualTo("rolling_report")
            prop(MetricReport::startTimestampMs).isEqualTo(98765)
            prop(MetricReport::endTimestampMs).isEqualTo(100000)
            prop(MetricReport::metrics).isEqualTo(
                mapOf(
                    "metric_a.latest" to JsonPrimitive(1.0),
                    "metric_b.latest" to JsonPrimitive(1.0),
                ),
            )
        }

        dao.insert(
            metricValueKt(
                version = 1,
                reportType = "rolling_report",
                timestamp = 500000,
                eventName = "metric_b",
                internal = false,
                aggregations = listOf(StateAgg.LATEST_VALUE),
                stringVal = null,
                numberVal = 1.0,
                booleanVal = null,
                dataType = DataType.DOUBLE,
                metricType = MetricType.PROPERTY,
                carryOver = false,
            ),
        )

        val report3 = dao.collectHeartbeat("rolling_report", null, 600000, null)

        assertThat(report3.hourlyHeartbeatReport).all {
            prop(MetricReport::reportType).isEqualTo("rolling_report")
            prop(MetricReport::startTimestampMs).isEqualTo(100000)
            prop(MetricReport::endTimestampMs).isEqualTo(600000)
            prop(MetricReport::metrics).isEqualTo(
                mapOf(
                    "metric_a.latest" to JsonPrimitive(1.0),
                    "metric_b.latest" to JsonPrimitive(1.0),
                ),
            )
        }

        val report4 = dao.collectHeartbeat("rolling_report", null, 700000, null)

        assertThat(report4.hourlyHeartbeatReport).all {
            prop(MetricReport::reportType).isEqualTo("rolling_report")
            prop(MetricReport::startTimestampMs).isEqualTo(600000)
            prop(MetricReport::endTimestampMs).isEqualTo(700000)
            prop(MetricReport::metrics).isEqualTo(
                mapOf(
                    "metric_a.latest" to JsonPrimitive(1.0),
                ),
            )
        }

        // Even if the previous autostarted report did not produce metrics, it should have updated the timestamp
        dao.insert(
            metricValueKt(
                version = 1,
                reportType = "rolling_report",
                timestamp = 800000,
                eventName = "metric_b",
                internal = false,
                aggregations = listOf(StateAgg.LATEST_VALUE),
                stringVal = null,
                numberVal = 1.0,
                booleanVal = null,
                dataType = DataType.DOUBLE,
                metricType = MetricType.PROPERTY,
                carryOver = false,
            ),
        )

        val report5 = dao.collectHeartbeat("rolling_report", null, 900000, null)

        assertThat(report5.hourlyHeartbeatReport).all {
            prop(MetricReport::reportType).isEqualTo("rolling_report")
            prop(MetricReport::startTimestampMs).isEqualTo(700000)
            prop(MetricReport::endTimestampMs).isEqualTo(900000)
            prop(MetricReport::metrics).isEqualTo(
                mapOf(
                    "metric_a.latest" to JsonPrimitive(1.0),
                    "metric_b.latest" to JsonPrimitive(1.0),
                ),
            )
        }
    }
}
