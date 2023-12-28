package com.memfault.bort.metrics

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.memfault.bort.TemporaryFile
import com.memfault.bort.dropbox.MetricReport
import com.memfault.bort.metrics.custom.CustomReport
import com.memfault.bort.metrics.database.Aggs
import com.memfault.bort.metrics.database.DbMetricMetadata
import com.memfault.bort.metrics.database.DbReport
import com.memfault.bort.metrics.database.MetricsDao
import com.memfault.bort.metrics.database.MetricsDb
import com.memfault.bort.reporting.DataType
import com.memfault.bort.reporting.DataType.DOUBLE
import com.memfault.bort.reporting.MetricType
import com.memfault.bort.reporting.MetricType.COUNTER
import com.memfault.bort.reporting.MetricValue
import com.memfault.bort.reporting.NumericAgg
import com.memfault.bort.reporting.NumericAgg.COUNT
import com.memfault.bort.reporting.NumericAgg.LATEST_VALUE
import com.memfault.bort.reporting.NumericAgg.MAX
import com.memfault.bort.reporting.StateAgg
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class MetricsDbTest {
    private lateinit var db: MetricsDb
    private lateinit var dao: MetricsDao

    @Before
    fun createDB() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, MetricsDb::class.java)
            .fallbackToDestructiveMigration().allowMainThreadQueries().build()
        dao = db.dao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndReport() {
        runTest {
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
            )
            dao.insert(counterValue)
            val report = dao.finishReport(reportType = "Heartbeat", endTimestampMs = 123456789, hrtFile = null)
            assertEquals(
                CustomReport(
                    report = MetricReport(
                        version = 1,
                        startTimestampMs = 123456788,
                        endTimestampMs = 123456789,
                        reportType = "Heartbeat",
                        metrics = mapOf("name.count" to JsonPrimitive(1)),
                        internalMetrics = mapOf(),
                    ),
                    hrt = null,
                ),
                report,
            )
        }
    }

    @Test
    fun aggregation_enums() {
        runTest {
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
            )
            dao.insert(val1)
            dao.insert(val2)
            dao.insert(val3)
            TemporaryFile().useFile { hrtFile, _ ->
                val report = dao.finishReport(reportType = "Heartbeat", endTimestampMs = 6000, hrtFile = hrtFile)
                assertEquals(
                    CustomReport(
                        report = MetricReport(
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
                        ),
                        hrt = hrtFile,
                    ),
                    report,
                )
                assertEquals(
                    """
                    {"schema_version":1,"start_time":1000,"duration_ms":5000,"report_type":"Heartbeat","producer":{"version":"1","id":"bort"},"rollups":[{"metadata":{"string_key":"key","metric_type":"property","data_type":"string","internal":false},"data":[{"t":1000,"value":"connected"},{"t":2000,"value":"disconnected"},{"t":4000,"value":"connected"}]}]}
                    """.trimIndent(),
                    hrtFile.readText(),
                )
            }
        }
    }

    @Test
    fun missingReport() {
        runTest {
            val report = dao.finishReport(reportType = "Heartbeat", endTimestampMs = 123456789, hrtFile = null)
            assertEquals(
                CustomReport(
                    report = MetricReport(
                        version = 1,
                        startTimestampMs = 123456789,
                        endTimestampMs = 123456789,
                        reportType = "Heartbeat",
                        metrics = mapOf(),
                        internalMetrics = mapOf(),
                    ),
                    hrt = null,
                ),
                report,
            )
        }
    }

    @Test
    fun ignoresEmptyHrtRollups() {
        runTest {
            dao.insertOrReplace(
                DbMetricMetadata(
                    reportType = "Heartbeat",
                    eventName = "novalues",
                    metricType = COUNTER,
                    dataType = DOUBLE,
                    carryOver = false,
                    aggregations = Aggs(emptyList()),
                    internal = false,
                ),
            )
            dao.insert(DbReport("Heartbeat", 1000))
            TemporaryFile().useFile { hrtFile, _ ->
                val report = dao.finishReport(reportType = "Heartbeat", endTimestampMs = 6000, hrtFile = hrtFile)
                assertEquals(
                    CustomReport(
                        report = MetricReport(
                            version = 1,
                            startTimestampMs = 1000,
                            endTimestampMs = 6000,
                            reportType = "Heartbeat",
                            metrics = mapOf(),
                            internalMetrics = mapOf(),
                        ),
                        hrt = hrtFile,
                    ),
                    report,
                )
                assertEquals(
                    """
                    {"schema_version":1,"start_time":1000,"duration_ms":5000,"report_type":"Heartbeat","producer":{"version":"1","id":"bort"},"rollups":[]}
                    """.trimIndent(),
                    hrtFile.readText(),
                )
            }
        }
    }

    @Test
    fun aggregation_count() {
        runTest {
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
                1.0,
                null,
                2,
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
                1.0,
                null,
                2,
            )
            dao.insert(val3)
            val report = dao.finishReport(reportType = "Heartbeat", endTimestampMs = 4, hrtFile = null)
            assertEquals(
                CustomReport(
                    report = MetricReport(
                        version = 1,
                        startTimestampMs = 1,
                        endTimestampMs = 4,
                        reportType = "Heartbeat",
                        metrics = mapOf("key.count" to JsonPrimitive(3)),
                        internalMetrics = mapOf(),
                    ),
                    hrt = null,
                ),
                report,
            )
        }
    }

    @Test
    fun aggregation_latest_max() {
        runTest {
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
            )
            dao.insert(val2)
            val report = dao.finishReport(reportType = "Heartbeat", endTimestampMs = 4, hrtFile = null)
            assertEquals(
                CustomReport(
                    report = MetricReport(
                        version = 1,
                        startTimestampMs = 1,
                        endTimestampMs = 4,
                        reportType = "Heartbeat",
                        metrics = mapOf(
                            "key.latest" to JsonPrimitive(1.0),
                            "key.max" to JsonPrimitive(4),
                        ),
                        internalMetrics = mapOf(),
                    ),
                    hrt = null,
                ),
                report,
            )
        }
    }

    @Test
    fun aggregation_mean_min_sum() {
        runTest {
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
            )
            dao.insert(val3)
            val report = dao.finishReport(reportType = "Heartbeat", endTimestampMs = 4, hrtFile = null)
            assertEquals(
                CustomReport(
                    report = MetricReport(
                        version = 1,
                        startTimestampMs = 1,
                        endTimestampMs = 4,
                        reportType = "Heartbeat",
                        metrics = mapOf(
                            "key.mean" to JsonPrimitive(3),
                            "key.min" to JsonPrimitive(1),
                            "key.sum" to JsonPrimitive(9),
                        ),
                        internalMetrics = mapOf(),
                    ),
                    hrt = null,
                ),
                report,
            )
        }
    }
}
