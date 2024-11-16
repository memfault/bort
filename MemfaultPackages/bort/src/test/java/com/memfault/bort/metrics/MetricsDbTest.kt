package com.memfault.bort.metrics

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import assertk.assertAll
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.text
import com.memfault.bort.metrics.custom.CustomReport
import com.memfault.bort.metrics.custom.MetricReport
import com.memfault.bort.metrics.database.Aggs
import com.memfault.bort.metrics.database.CalculateDerivedAggregations
import com.memfault.bort.metrics.database.DbDump
import com.memfault.bort.metrics.database.DbMetricMetadata
import com.memfault.bort.metrics.database.DbReport
import com.memfault.bort.metrics.database.DbReportBuilder
import com.memfault.bort.metrics.database.HOURLY_HEARTBEAT_REPORT_TYPE
import com.memfault.bort.metrics.database.HrtFileFactory
import com.memfault.bort.metrics.database.MetricsDao
import com.memfault.bort.metrics.database.MetricsDb
import com.memfault.bort.reporting.DataType
import com.memfault.bort.reporting.MetricType
import com.memfault.bort.reporting.MetricValue
import com.memfault.bort.reporting.NumericAgg
import com.memfault.bort.reporting.NumericAgg.COUNT
import com.memfault.bort.reporting.NumericAgg.LATEST_VALUE
import com.memfault.bort.reporting.NumericAgg.MAX
import com.memfault.bort.reporting.StateAgg
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

private const val SOFTWARE_VERSION = "Android/aosp_arm64/generic_arm64:8.1.0/OC/root04302340:eng/test-keys::1.0.0"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class MetricsDbTest {
    @get:Rule val tempFolder: TemporaryFolder = TemporaryFolder.builder().assureDeletion().build()

    private lateinit var db: MetricsDb
    private lateinit var dao: MetricsDao

    private val dbReportBuilder = DbReportBuilder { report ->
        report.copy(
            softwareVersion = SOFTWARE_VERSION,
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
            .fallbackToDestructiveMigration().allowMainThreadQueries().build()
        dao = db.dao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndReport() = runTest {
        val counterValue = MetricValue(
            "name",
            "Heartbeat",
            listOf(NumericAgg.COUNT),
            false,
            MetricType.COUNTER,
            DataType.DOUBLE,
            false,
            123456788,
            null,
            1.0,
            null,
            2,
            null,
        )
        dao.insert(counterValue)
        val report = dao.collectHeartbeat(endTimestampMs = 123456789, hrtFileFactory = null)
        assertThat(report).isEqualTo(
            CustomReport(
                hourlyHeartbeatReport = MetricReport(
                    version = 1,
                    startTimestampMs = 123456788,
                    endTimestampMs = 123456789,
                    reportType = "Heartbeat",
                    metrics = mapOf("name.count" to JsonPrimitive(1)),
                    internalMetrics = mapOf(),
                    reportName = null,
                    hrt = null,
                    softwareVersion = SOFTWARE_VERSION,
                ),
                dailyHeartbeatReport = null,
            ),
        )
    }

    @Test
    fun aggregation_enums() = runTest {
        val val1 = MetricValue(
            "key",
            "Heartbeat",
            listOf(StateAgg.LATEST_VALUE, StateAgg.TIME_PER_HOUR, StateAgg.TIME_TOTALS),
            false,
            MetricType.PROPERTY,
            DataType.STRING,
            false,
            1000,
            "connected",
            null,
            null,
            2,
            null,
        )
        val val2 = MetricValue(
            "key",
            "Heartbeat",
            listOf(StateAgg.LATEST_VALUE, StateAgg.TIME_PER_HOUR, StateAgg.TIME_TOTALS),
            false,
            MetricType.PROPERTY,
            DataType.STRING,
            false,
            2000,
            "disconnected",
            null,
            null,
            2,
            null,
        )
        val val3 = MetricValue(
            "key",
            "Heartbeat",
            listOf(StateAgg.LATEST_VALUE, StateAgg.TIME_PER_HOUR, StateAgg.TIME_TOTALS),
            false,
            MetricType.PROPERTY,
            DataType.STRING,
            false,
            4000,
            "connected",
            null,
            null,
            2,
            null,
        )
        dao.insert(val1)
        dao.insert(val2)
        dao.insert(val3)

        val hrtFile = tempFolder.newFile()
        val hrtFileFactory = HrtFileFactory { hrtFile }

        val report = dao.collectHeartbeat(endTimestampMs = 6000, hrtFileFactory = hrtFileFactory)
        assertThat(report).isEqualTo(
            CustomReport(
                hourlyHeartbeatReport = MetricReport(
                    version = 1,
                    startTimestampMs = 1000,
                    endTimestampMs = 6000,
                    reportType = "Heartbeat",
                    metrics = mapOf(
                        "key.latest" to JsonPrimitive("connected"),
                        "key_connected.secs/hour" to JsonPrimitive((3.0 / 5.0 * 3600).toLong()),
                        "key_disconnected.secs/hour" to JsonPrimitive((2.0 / 5.0 * 3600).toLong()),
                        "key_connected.total_secs" to JsonPrimitive(3),
                        "key_disconnected.total_secs" to JsonPrimitive(2),
                    ),
                    internalMetrics = mapOf(),
                    hrt = hrtFile,
                    softwareVersion = SOFTWARE_VERSION,
                ),
                dailyHeartbeatReport = null,
            ),
        )
        assertThat(hrtFile).text().isEqualTo(
            """
                {"schema_version":1,"start_time":1000,"duration_ms":5000,"report_type":"Heartbeat","producer":{"version":"1","id":"bort"},"rollups":[{"metadata":{"string_key":"key","metric_type":"property","data_type":"string","internal":false},"data":[{"t":1000,"value":"connected"},{"t":2000,"value":"disconnected"},{"t":4000,"value":"connected"}]}]}
            """.trimIndent(),
        )
    }

    @Test
    fun aggregation_string_carryOver() = runTest {
        val val1 = MetricValue(
            "key",
            "Heartbeat",
            listOf(StateAgg.LATEST_VALUE, StateAgg.TIME_PER_HOUR, StateAgg.TIME_TOTALS),
            false,
            MetricType.PROPERTY,
            DataType.STRING,
            true,
            1000,
            "connected",
            null,
            null,
            2,
            null,
        )
        dao.insert(val1)

        val report1 = dao.collectHeartbeat(endTimestampMs = 2000, hrtFileFactory = null)
        assertThat(report1).isEqualTo(
            CustomReport(
                hourlyHeartbeatReport = MetricReport(
                    version = 1,
                    startTimestampMs = 1000,
                    endTimestampMs = 2000,
                    reportType = "Heartbeat",
                    metrics = mapOf(
                        "key.latest" to JsonPrimitive("connected"),
                        "key_connected.secs/hour" to JsonPrimitive(1.hours.inWholeSeconds),
                        "key_connected.total_secs" to JsonPrimitive(1),
                    ),
                    internalMetrics = mapOf(),
                    hrt = null,
                    softwareVersion = SOFTWARE_VERSION,
                ),
                dailyHeartbeatReport = null,
            ),
        )

        val val2 = MetricValue(
            "key",
            "Heartbeat",
            listOf(StateAgg.LATEST_VALUE, StateAgg.TIME_PER_HOUR, StateAgg.TIME_TOTALS),
            false,
            MetricType.PROPERTY,
            DataType.STRING,
            true,
            4000,
            "disconnected",
            null,
            null,
            2,
            null,
        )
        dao.insert(val2)
        val val3 = MetricValue(
            "key",
            "Heartbeat",
            listOf(StateAgg.LATEST_VALUE, StateAgg.TIME_PER_HOUR, StateAgg.TIME_TOTALS),
            false,
            MetricType.PROPERTY,
            DataType.STRING,
            true,
            6000,
            "connected",
            null,
            null,
            2,
            null,
        )
        dao.insert(val3)

        val hrtFile = tempFolder.newFile()
        val hrtFileFactory = HrtFileFactory { hrtFile }

        val report2 = dao.collectHeartbeat(endTimestampMs = 8000, hrtFileFactory = hrtFileFactory)
        assertThat(report2).isEqualTo(
            CustomReport(
                hourlyHeartbeatReport = MetricReport(
                    version = 1,
                    startTimestampMs = 2000,
                    endTimestampMs = 8000,
                    reportType = "Heartbeat",
                    metrics = mapOf(
                        "key.latest" to JsonPrimitive("connected"),
                        "key_connected.secs/hour" to JsonPrimitive((4.0 / 6.0).hours.inWholeSeconds),
                        "key_disconnected.secs/hour" to JsonPrimitive((2.0 / 6.0).hours.inWholeSeconds),
                        "key_connected.total_secs" to JsonPrimitive(4),
                        "key_disconnected.total_secs" to JsonPrimitive(2),
                    ),
                    internalMetrics = mapOf(),
                    hrt = hrtFile,
                    softwareVersion = SOFTWARE_VERSION,
                ),
                dailyHeartbeatReport = null,
            ),
        )
        assertThat(hrtFile).text().isEqualTo(
            """
                {"schema_version":1,"start_time":2000,"duration_ms":6000,"report_type":"Heartbeat","producer":{"version":"1","id":"bort"},"rollups":[{"metadata":{"string_key":"key","metric_type":"property","data_type":"string","internal":false},"data":[{"t":2000,"value":"connected"},{"t":4000,"value":"disconnected"},{"t":6000,"value":"connected"}]}]}
            """.trimIndent(),
        )
    }

    @Test
    fun missingReport() = runTest {
        val report = dao.collectHeartbeat(endTimestampMs = 123456789, hrtFileFactory = null)
        assertThat(report).isEqualTo(
            CustomReport(
                hourlyHeartbeatReport = MetricReport(
                    version = 1,
                    startTimestampMs = 123456789,
                    endTimestampMs = 123456789,
                    reportType = "Heartbeat",
                    metrics = mapOf(),
                    internalMetrics = mapOf(),
                    hrt = null,
                    softwareVersion = null,
                ),
                dailyHeartbeatReport = null,
            ),
        )
    }

    @Test
    fun ignoresEmptyHrtRollups() = runTest {
        val reportId = dao.insert(DbReport(type = "Heartbeat", startTimeMs = 1000))
        dao.insertOrReplace(
            DbMetricMetadata(
                reportId = reportId,
                eventName = "novalues",
                metricType = MetricType.COUNTER,
                dataType = DataType.DOUBLE,
                carryOver = false,
                aggregations = Aggs(emptyList()),
                internal = false,
            ),
        )

        val hrtFile = tempFolder.newFile()
        val hrtFileFactory = HrtFileFactory { hrtFile }

        val report = dao.collectHeartbeat(endTimestampMs = 6000, hrtFileFactory = hrtFileFactory)
        assertThat(report).isEqualTo(
            CustomReport(
                hourlyHeartbeatReport = MetricReport(
                    version = 1,
                    startTimestampMs = 1000,
                    endTimestampMs = 6000,
                    reportType = "Heartbeat",
                    metrics = mapOf(),
                    internalMetrics = mapOf(),
                    hrt = hrtFile,
                    softwareVersion = null,
                ),
                dailyHeartbeatReport = null,
            ),
        )
        assertThat(hrtFile).text().isEqualTo(
            """
                {"schema_version":1,"start_time":1000,"duration_ms":5000,"report_type":"Heartbeat","producer":{"version":"1","id":"bort"},"rollups":[]}
            """.trimIndent(),
        )
    }

    @Test
    fun aggregation_count() = runTest {
        val val1 = MetricValue(
            "key",
            "Heartbeat",
            listOf(COUNT),
            false,
            MetricType.COUNTER,
            DataType.DOUBLE,
            false,
            1,
            null,
            1.0,
            null,
            2,
            null,
        )
        dao.insert(val1)
        val val2 = MetricValue(
            "key",
            "Heartbeat",
            listOf(COUNT),
            false,
            MetricType.COUNTER,
            DataType.DOUBLE,
            false,
            2,
            null,
            5.0,
            null,
            2,
            null,
        )
        dao.insert(val2)
        val val3 = MetricValue(
            "key",
            "Heartbeat",
            listOf(COUNT),
            false,
            MetricType.COUNTER,
            DataType.DOUBLE,
            false,
            3,
            null,
            11.5,
            null,
            2,
            null,
        )
        dao.insert(val3)
        val stringval1 = MetricValue(
            "stringkey",
            "Heartbeat",
            listOf(COUNT),
            false,
            MetricType.PROPERTY,
            DataType.STRING,
            false,
            4,
            "string",
            null,
            null,
            2,
            null,
        )
        dao.insert(stringval1)
        val report = dao.collectHeartbeat(endTimestampMs = 4, hrtFileFactory = null)
        assertThat(report).isEqualTo(
            CustomReport(
                hourlyHeartbeatReport = MetricReport(
                    version = 1,
                    startTimestampMs = 1,
                    endTimestampMs = 4,
                    reportType = "Heartbeat",
                    metrics = mapOf(
                        "key.count" to JsonPrimitive(3),
                        "stringkey.count" to JsonPrimitive(1),
                    ),
                    internalMetrics = mapOf(),
                    hrt = null,
                    softwareVersion = SOFTWARE_VERSION,
                ),
                dailyHeartbeatReport = null,
            ),
        )
    }

    @Test
    fun aggregation_latest_max() = runTest {
        val val1 = MetricValue(
            "key",
            "Heartbeat",
            listOf(LATEST_VALUE, MAX),
            false,
            MetricType.COUNTER,
            DataType.DOUBLE,
            false,
            1,
            null,
            4.0,
            null,
            2,
            null,
        )
        dao.insert(val1)
        val val2 = MetricValue(
            "key",
            "Heartbeat",
            listOf(LATEST_VALUE, MAX),
            false,
            MetricType.COUNTER,
            DataType.DOUBLE,
            false,
            2,
            null,
            1.0,
            null,
            2,
            null,
        )
        dao.insert(val2)
        val report = dao.collectHeartbeat(endTimestampMs = 4, hrtFileFactory = null)
        assertThat(report).isEqualTo(
            CustomReport(
                hourlyHeartbeatReport = MetricReport(
                    version = 1,
                    startTimestampMs = 1,
                    endTimestampMs = 4,
                    reportType = "Heartbeat",
                    metrics = mapOf(
                        "key.latest" to JsonPrimitive(1.0),
                        "key.max" to JsonPrimitive(4.0),
                    ),
                    internalMetrics = mapOf(),
                    hrt = null,
                    softwareVersion = SOFTWARE_VERSION,
                ),
                dailyHeartbeatReport = null,
            ),
        )
    }

    @Test
    fun aggregation_mean_min_sum() = runTest {
        val val1 = MetricValue(
            "key",
            "Heartbeat",
            listOf(NumericAgg.MEAN, NumericAgg.MIN, NumericAgg.SUM),
            false,
            MetricType.COUNTER,
            DataType.DOUBLE,
            false,
            1,
            null,
            1.0,
            null,
            2,
            null,
        )
        dao.insert(val1)
        val val2 = MetricValue(
            "key",
            "Heartbeat",
            listOf(NumericAgg.MEAN, NumericAgg.MIN, NumericAgg.SUM),
            false,
            MetricType.COUNTER,
            DataType.DOUBLE,
            false,
            2,
            null,
            5.0,
            null,
            2,
            null,
        )
        dao.insert(val2)
        val val3 = MetricValue(
            "key",
            "Heartbeat",
            listOf(NumericAgg.MEAN, NumericAgg.MIN, NumericAgg.SUM),
            false,
            MetricType.COUNTER,
            DataType.DOUBLE,
            false,
            3,
            null,
            3.0,
            null,
            2,
            null,
        )
        dao.insert(val3)
        val report = dao.collectHeartbeat(endTimestampMs = 4, hrtFileFactory = null)
        assertThat(report).isEqualTo(
            CustomReport(
                hourlyHeartbeatReport = MetricReport(
                    version = 1,
                    startTimestampMs = 1,
                    endTimestampMs = 4,
                    reportType = "Heartbeat",
                    metrics = mapOf(
                        "key.mean" to JsonPrimitive(3.0),
                        "key.min" to JsonPrimitive(1.0),
                        "key.sum" to JsonPrimitive(9.0),
                    ),
                    internalMetrics = mapOf(),
                    hrt = null,
                    softwareVersion = SOFTWARE_VERSION,
                ),
                dailyHeartbeatReport = null,
            ),
        )
    }

    @Test
    fun carryOver_property() = runTest {
        val val1 = MetricValue(
            "key",
            "Heartbeat",
            listOf(LATEST_VALUE),
            false,
            MetricType.COUNTER,
            DataType.DOUBLE,
            true,
            1,
            null,
            1.0,
            null,
            2,
            null,
        )
        dao.insert(val1)

        assertAll {
            val dump = dao.dump()
            assertThat(dump.reports).hasSize(1)
            assertThat(dump.metadata).hasSize(1)
            assertThat(dump.values).hasSize(1)
        }

        val report1 = dao.collectHeartbeat(endTimestampMs = 4, hrtFileFactory = null)
        assertThat(report1).isEqualTo(
            CustomReport(
                hourlyHeartbeatReport = MetricReport(
                    version = 1,
                    startTimestampMs = 1,
                    endTimestampMs = 4,
                    reportType = "Heartbeat",
                    metrics = mapOf(
                        "key.latest" to JsonPrimitive(1.0),
                    ),
                    internalMetrics = mapOf(),
                    hrt = null,
                    softwareVersion = SOFTWARE_VERSION,
                ),
                dailyHeartbeatReport = null,
            ),
        )

        assertAll {
            val dump = dao.dump()
            assertThat(dump.reports).hasSize(1)
            assertThat(dump.metadata).hasSize(1)
            assertThat(dump.values).hasSize(1)
        }

        val report2 = dao.collectHeartbeat(endTimestampMs = 8, hrtFileFactory = null)
        assertThat(report2).isEqualTo(
            CustomReport(
                hourlyHeartbeatReport = MetricReport(
                    version = 1,
                    startTimestampMs = 4,
                    endTimestampMs = 8,
                    reportType = "Heartbeat",
                    metrics = mapOf(
                        "key.latest" to JsonPrimitive(1.0),
                    ),
                    internalMetrics = mapOf(),
                    hrt = null,
                    softwareVersion = SOFTWARE_VERSION,
                ),
                dailyHeartbeatReport = null,
            ),
        )
    }

    @Test
    fun carryOver_expired() = runTest {
        val val1 = MetricValue(
            "key",
            "Heartbeat",
            listOf(LATEST_VALUE),
            false,
            MetricType.COUNTER,
            DataType.DOUBLE,
            true,
            1,
            null,
            1.0,
            null,
            2,
            null,
        )
        dao.insert(val1)
        val val2 = MetricValue(
            "nocarry",
            "Heartbeat",
            emptyList(),
            false,
            MetricType.GAUGE,
            DataType.DOUBLE,
            false,
            1,
            null,
            1.0,
            null,
            2,
            null,
        )
        dao.insert(val2)

        assertAll {
            val dump = dao.dump()
            assertThat(dump.reports).hasSize(1)
            assertThat(dump.metadata).hasSize(2)
            assertThat(dump.values).hasSize(2)
        }

        val hrtFile1 = tempFolder.newFile()
        val hrtFileFactory = object : HrtFileFactory {
            var file: File? = null
            override fun create(): File = checkNotNull(file)
        }
        hrtFileFactory.file = hrtFile1

        val report1 = dao.collectHeartbeat(
            endTimestampMs = 3.days.inWholeMilliseconds + 1,
            hrtFileFactory = hrtFileFactory,
        )
        assertThat(report1).isEqualTo(
            CustomReport(
                hourlyHeartbeatReport = MetricReport(
                    version = 1,
                    startTimestampMs = 1,
                    endTimestampMs = 3.days.inWholeMilliseconds + 1,
                    reportType = "Heartbeat",
                    metrics = mapOf(
                        "key.latest" to JsonPrimitive(1.0),
                    ),
                    internalMetrics = mapOf(),
                    hrt = hrtFile1,
                    softwareVersion = SOFTWARE_VERSION,
                ),
                dailyHeartbeatReport = null,
            ),
        )

        assertAll {
            val dump = dao.dump()
            assertThat(dump.reports).hasSize(1)
            assertThat(dump.metadata).hasSize(1)
            assertThat(dump.values).hasSize(1)
        }

        assertThat(hrtFile1).text().isEqualTo(
            """
                {"schema_version":1,"start_time":1,"duration_ms":${3.days.inWholeMilliseconds},"report_type":"Heartbeat","producer":{"version":"1","id":"bort"},"rollups":[{"metadata":{"string_key":"key","metric_type":"counter","data_type":"double","internal":false},"data":[{"t":1,"value":1.0}]},{"metadata":{"string_key":"nocarry","metric_type":"gauge","data_type":"double","internal":false},"data":[{"t":1,"value":1.0}]}]}
            """.trimIndent(),
        )

        val hrtFile2 = tempFolder.newFile()
        hrtFileFactory.file = hrtFile2

        val report2 = dao.collectHeartbeat(
            endTimestampMs = 6.days.inWholeMilliseconds + 1,
            hrtFileFactory = hrtFileFactory,
        )
        assertThat(report2).isEqualTo(
            CustomReport(
                hourlyHeartbeatReport = MetricReport(
                    version = 1,
                    startTimestampMs = 3.days.inWholeMilliseconds + 1,
                    endTimestampMs = 6.days.inWholeMilliseconds + 1,
                    reportType = "Heartbeat",
                    metrics = mapOf(
                        "key.latest" to JsonPrimitive(1.0),
                    ),
                    internalMetrics = mapOf(),
                    hrt = hrtFile2,
                    softwareVersion = SOFTWARE_VERSION,
                ),
                dailyHeartbeatReport = null,
            ),
        )

        assertThat(dao.dump()).isEqualTo(DbDump())

        assertThat(hrtFile2).text().isEqualTo(
            """
                {"schema_version":1,"start_time":${3.days.inWholeMilliseconds + 1},"duration_ms":${3.days.inWholeMilliseconds},"report_type":"Heartbeat","producer":{"version":"1","id":"bort"},"rollups":[{"metadata":{"string_key":"key","metric_type":"counter","data_type":"double","internal":false},"data":[{"t":${3.days.inWholeMilliseconds + 1},"value":1.0}]}]}
            """.trimIndent(),
        )

        val hrtFile3 = tempFolder.newFile()
        hrtFileFactory.file = hrtFile3

        val report3 = dao.collectHeartbeat(
            endTimestampMs = (6.days + 1.hours).inWholeMilliseconds,
            hrtFileFactory = hrtFileFactory,
        )
        assertThat(report3).isEqualTo(
            CustomReport(
                hourlyHeartbeatReport = MetricReport(
                    version = 1,
                    startTimestampMs = (6.days + 1.hours).inWholeMilliseconds,
                    endTimestampMs = (6.days + 1.hours).inWholeMilliseconds,
                    reportType = "Heartbeat",
                    metrics = mapOf(),
                    internalMetrics = mapOf(),
                    hrt = null,
                    softwareVersion = null,
                ),
                dailyHeartbeatReport = null,
            ),
        )

        assertThat(dao.dump()).isEqualTo(DbDump())

        assertThat(hrtFile3).text().isEmpty()
    }
}
