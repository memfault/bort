package com.memfault.bort.metrics

import android.app.Application
import android.content.Intent
import android.os.BatteryManager
import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isLessThan
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.prop
import assertk.assertions.single
import com.google.testing.junit.testparameterinjector.TestParameter
import com.memfault.bort.BortAlwaysEnabledProvider
import com.memfault.bort.battery.BATTERY_CHARGING_METRIC
import com.memfault.bort.battery.BATTERY_DISCHARGE_DURATION_METRIC
import com.memfault.bort.battery.BATTERY_LEVEL_METRIC
import com.memfault.bort.battery.BATTERY_SOC_DROP_METRIC
import com.memfault.bort.battery.LastBatteryCycleCountState
import com.memfault.bort.battery.LastBatteryCycleCountStateProvider
import com.memfault.bort.battery.LastBatteryVitalsState
import com.memfault.bort.battery.LastBatteryVitalsStateProvider
import com.memfault.bort.battery.RealBatterySessionVitals
import com.memfault.bort.connectivity.CONNECTIVITY_TYPE_METRIC
import com.memfault.bort.connectivity.ConnectivityState
import com.memfault.bort.connectivity.ConnectivityState.BLUETOOTH
import com.memfault.bort.connectivity.ConnectivityState.NONE
import com.memfault.bort.connectivity.ConnectivityState.WIFI
import com.memfault.bort.connectivity.ConnectivityTimeCalculator.Companion.CONNECTED_TIME_METRIC
import com.memfault.bort.connectivity.ConnectivityTimeCalculator.Companion.EXPECTED_TIME_METRIC
import com.memfault.bort.metrics.CrashFreeHoursMetricLogger.Companion.OPERATIONAL_CRASHES_METRIC_KEY
import com.memfault.bort.metrics.DropBoxTraceCountDerivedAggregations.Companion.DROP_BOX_TAGS
import com.memfault.bort.metrics.custom.CustomMetrics
import com.memfault.bort.metrics.custom.CustomReport
import com.memfault.bort.metrics.custom.MetricReport
import com.memfault.bort.metrics.database.DbDump
import com.memfault.bort.metrics.database.MetricsDb
import com.memfault.bort.reporting.NumericAgg
import com.memfault.bort.reporting.NumericAgg.COUNT
import com.memfault.bort.reporting.NumericAgg.MAX
import com.memfault.bort.reporting.NumericAgg.MEAN
import com.memfault.bort.reporting.NumericAgg.MIN
import com.memfault.bort.reporting.NumericAgg.SUM
import com.memfault.bort.reporting.RemoteMetricsService
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.reporting.StateAgg
import com.memfault.bort.reporting.StateAgg.TIME_PER_HOUR
import com.memfault.bort.reporting.StateAgg.TIME_TOTALS
import com.memfault.bort.time.BoxedDuration
import com.memfault.bort.time.CombinedTime
import com.memfault.bort.time.CombinedTimeProvider
import com.memfault.bort.tokenbucket.RealTokenBucketFactory
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestParameterInjector
import org.robolectric.annotation.Config
import java.time.Instant
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val ALL_STATE_AGGREGATIONS = listOf(StateAgg.LATEST_VALUE, TIME_TOTALS, TIME_PER_HOUR)
private val ALL_NUMERIC_AGGREGATIONS = listOf(NumericAgg.LATEST_VALUE, SUM, COUNT, MIN, MEAN, MAX)

private class FakeLastBatteryVitalsStateProvider : LastBatteryVitalsStateProvider {
    override var state = LastBatteryVitalsState()
}

private class FakeLastBatteryCycleCountStateProvider : LastBatteryCycleCountStateProvider {
    override var state = LastBatteryCycleCountState()
}

private val Int.hoursMs: Long
    get() = hours.inWholeMilliseconds

@RunWith(RobolectricTestParameterInjector::class)
@Config(sdk = [26])
class MetricsIntegrationTest {

    @get:Rule
    val metricsDbTestEnvironment = MetricsDbTestEnvironment().apply {
        highResMetricsEnabledValue = true
    }

    private val db: MetricsDb get() = metricsDbTestEnvironment.db
    private val dao: CustomMetrics get() = metricsDbTestEnvironment.dao

    @Test
    fun `happy path heartbeat`() = runTest {
        Reporting.report().counter("test", sumInReport = true).increment()

        val report = dao.collectHeartbeat(
            endTimestampMs = System.currentTimeMillis(),
            endUptimeMs = System.currentTimeMillis(),
        )

        assertThat(report.hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
            "test.sum" to JsonPrimitive(1.0),
        )

        assertThat(db.dao().dump()).isEqualTo(DbDump())
    }

    @Test
    fun `heartbeat carryover`() = runTest {
        Reporting.report().stringProperty("carryover", addLatestToReport = true)
            .update("test")

        val report1 = dao.collectHeartbeat(
            endTimestampMs = System.currentTimeMillis(),
            endUptimeMs = System.currentTimeMillis(),
        )

        assertThat(report1.hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
            "carryover.latest" to JsonPrimitive("test"),
        )

        val report2 = dao.collectHeartbeat(
            endTimestampMs = System.currentTimeMillis(),
            endUptimeMs = System.currentTimeMillis(),
        )

        assertThat(report2.hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
            "carryover.latest" to JsonPrimitive("test"),
        )
    }

    @Test
    fun `happy path daily heartbeats`() = runTest {
        metricsDbTestEnvironment.dailyHeartbeatEnabledValue = true

        val now = System.currentTimeMillis() - 1.days.inWholeMilliseconds

        Reporting.report().counter("test", sumInReport = true)
            .increment(timestamp = now, uptime = now + 100.hours.inWholeMilliseconds)

        assertThat(
            dao.collectHeartbeat(
                endTimestampMs = now + 1.hours.inWholeMilliseconds,
                endUptimeMs =
                now + 1.hours.inWholeMilliseconds,
            ),
        ).all {
            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
                "test.sum" to JsonPrimitive(1.0),
            )
            prop(CustomReport::dailyHeartbeatReport).isNull()
        }

        Reporting.report().counter("test", sumInReport = true)
            .increment(timestamp = now + 1.hours.inWholeMilliseconds, uptime = 1.hours.inWholeMilliseconds)

        assertThat(
            dao.collectHeartbeat(
                endTimestampMs = now + 24.hours.inWholeMilliseconds,
                endUptimeMs =
                now + 120.hours.inWholeMilliseconds,
            ),
        ).all {
            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
                "test.sum" to JsonPrimitive(1.0),
            )
            prop(CustomReport::dailyHeartbeatReport).isNotNull().all {
                prop(MetricReport::startTimestampMs).isEqualTo(now)
                prop(MetricReport::endTimestampMs).isEqualTo(now + 24.hours.inWholeMilliseconds)
                prop(MetricReport::startUptimeMs).isEqualTo(now + 100.hours.inWholeMilliseconds)
                prop(MetricReport::endUptimeMs).isEqualTo(now + 120.hours.inWholeMilliseconds)
                prop(MetricReport::reportType).isNotNull()
                prop(MetricReport::reportName).isNull()
                prop(MetricReport::metrics).containsOnly(
                    "test.sum" to JsonPrimitive(2.0),
                )
                prop(MetricReport::internalMetrics).isEmpty()
            }
        }

        assertThat(db.dao().dump()).isEqualTo(DbDump())
    }

    @Test
    fun `daily heartbeats all aggregations`() = runTest {
        metricsDbTestEnvironment.dailyHeartbeatEnabledValue = true

        val now = System.currentTimeMillis()

        val distribution = Reporting.report().distribution("dist", aggregations = ALL_NUMERIC_AGGREGATIONS)
        val stateTracker = Reporting.report().stringStateTracker("state", aggregations = ALL_STATE_AGGREGATIONS)
        val property = Reporting.report().stringProperty("prop", addLatestToReport = true)

        distribution.record(0L, timestamp = now, uptime = now)
        distribution.record(60L, timestamp = now + 1.hoursMs, uptime = now + 1.hoursMs)
        distribution.record(120, timestamp = now + 2.hoursMs, uptime = now + 2.hoursMs)
        stateTracker.state("ON", timestamp = now + 2.hoursMs, uptime = now + 2.hoursMs)
        stateTracker.state("OFF", timestamp = now + 4.hoursMs, uptime = now + 4.hoursMs)
        property.update("carry", timestamp = 3.hoursMs, uptime = now + 3.hoursMs)

        assertThat(dao.collectHeartbeat(endTimestampMs = now + 6.hoursMs, endUptimeMs = now + 6.hoursMs)).all {
            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
                "dist.latest" to JsonPrimitive(120.0),
                "dist.sum" to JsonPrimitive(180.0),
                "dist.mean" to JsonPrimitive(60.0),
                "dist.min" to JsonPrimitive(0.0),
                "dist.max" to JsonPrimitive(120.0),
                "dist.count" to JsonPrimitive(3),
                "state_ON.secs/hour" to JsonPrimitive(1200L),
                "state_OFF.secs/hour" to JsonPrimitive(1200L),
                "state_ON.total_secs" to JsonPrimitive(7200L),
                "state_OFF.total_secs" to JsonPrimitive(7200L),
                "state_ON.mean_time_in_state_ms" to JsonPrimitive(2.hoursMs),
                "state.latest" to JsonPrimitive("OFF"),
                "prop.latest" to JsonPrimitive("carry"),
            )
            prop(CustomReport::dailyHeartbeatReport).isNull()
        }

        distribution.record(240L, timestamp = now + 6.hoursMs, uptime = now + 6.hoursMs)
        stateTracker.state("NONE", timestamp = now + 9.hoursMs, uptime = now + 9.hoursMs)
        property.update("carried", timestamp = now + 9.hoursMs, uptime = now + 9.hoursMs)

        assertThat(
            dao.collectHeartbeat(
                endTimestampMs = now + 12.hours.inWholeMilliseconds,
                endUptimeMs =
                now + 12.hours.inWholeMilliseconds,
            ),
        ).all {
            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
                "dist.latest" to JsonPrimitive(240.0),
                "dist.sum" to JsonPrimitive(240.0),
                "dist.mean" to JsonPrimitive(240.0),
                "dist.min" to JsonPrimitive(240.0),
                "dist.max" to JsonPrimitive(240.0),
                "dist.count" to JsonPrimitive(1),
                "state_OFF.secs/hour" to JsonPrimitive(1800L),
                "state_NONE.secs/hour" to JsonPrimitive(1800L),
                "state_OFF.total_secs" to JsonPrimitive(10800L),
                "state_NONE.total_secs" to JsonPrimitive(10800L),
                "state_OFF.mean_time_in_state_ms" to JsonPrimitive(5.hoursMs),
                "state.latest" to JsonPrimitive("NONE"),
                "prop.latest" to JsonPrimitive("carried"),
            )
            prop(CustomReport::dailyHeartbeatReport).isNull()
        }

        distribution.record(480L, timestamp = now + 18.hoursMs, uptime = now + 18.hoursMs)
        stateTracker.state("OFF", timestamp = now + 21.hoursMs, uptime = now + 21.hoursMs)

        assertThat(
            dao.collectHeartbeat(
                endTimestampMs = now + 24.hours.inWholeMilliseconds,
                endUptimeMs =
                now + 24.hours.inWholeMilliseconds,
            ),
        ).all {
            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
                "dist.latest" to JsonPrimitive(480.0),
                "dist.sum" to JsonPrimitive(480.0),
                "dist.mean" to JsonPrimitive(480.0),
                "dist.min" to JsonPrimitive(480.0),
                "dist.max" to JsonPrimitive(480.0),
                "dist.count" to JsonPrimitive(1),
                "state_NONE.secs/hour" to JsonPrimitive(2700L),
                "state_OFF.secs/hour" to JsonPrimitive(900L),
                "state_NONE.total_secs" to JsonPrimitive(32400L),
                "state_OFF.total_secs" to JsonPrimitive(10800L),
                "state_NONE.mean_time_in_state_ms" to JsonPrimitive(12.hoursMs),
                "state.latest" to JsonPrimitive("OFF"),
                "prop.latest" to JsonPrimitive("carried"),
            )

            prop(CustomReport::dailyHeartbeatReport).isNotNull().all {
                prop(MetricReport::startTimestampMs).isEqualTo(now)
                prop(MetricReport::endTimestampMs).isEqualTo(now + 24.hours.inWholeMilliseconds)
                prop(MetricReport::reportType).isNotNull()
                prop(MetricReport::reportName).isNull()
                prop(MetricReport::metrics).containsOnly(
                    "dist.latest" to JsonPrimitive(480.0),
                    "dist.sum" to JsonPrimitive(900.0),
                    "dist.mean" to JsonPrimitive(180.0),
                    "dist.min" to JsonPrimitive(0.0),
                    "dist.max" to JsonPrimitive(480.0),
                    "dist.count" to JsonPrimitive(5),
                    "state_NONE.secs/hour" to JsonPrimitive(1800L),
                    "state_OFF.secs/hour" to JsonPrimitive(1200L),
                    "state_ON.secs/hour" to JsonPrimitive(300L),
                    "state_NONE.total_secs" to JsonPrimitive(43200L),
                    "state_OFF.total_secs" to JsonPrimitive(28800L),
                    "state_ON.total_secs" to JsonPrimitive(7200L),
                    "state_ON.mean_time_in_state_ms" to JsonPrimitive(2.hoursMs),
                    "state_NONE.mean_time_in_state_ms" to JsonPrimitive(12.hoursMs),
                    "state_OFF.mean_time_in_state_ms" to JsonPrimitive(5.hoursMs),
                    "state.latest" to JsonPrimitive("OFF"),
                    "prop.latest" to JsonPrimitive("carried"),
                )
                prop(MetricReport::internalMetrics).isEmpty()
            }
        }
    }

    @Test
    fun `heartbeat all numeric aggregations`() = runTest {
        Reporting.report().distribution("dist", ALL_NUMERIC_AGGREGATIONS)
            .record(0, timestamp = 0, uptime = 0)

        Reporting.report().distribution("dist", ALL_NUMERIC_AGGREGATIONS)
            .record(100, timestamp = 1, uptime = 1)

        assertThat(dao.collectHeartbeat(endTimestampMs = 1, endUptimeMs = 1)).all {
            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
                "dist.latest" to JsonPrimitive(100.0),
                "dist.sum" to JsonPrimitive(100.0),
                "dist.count" to JsonPrimitive(2),
                "dist.min" to JsonPrimitive(0.0),
                "dist.mean" to JsonPrimitive(50.0),
                "dist.max" to JsonPrimitive(100.0),
            )
        }
    }

    @Test
    fun `heartbeat all state aggregations`() = runTest {
        Reporting.report().stringStateTracker("dist", ALL_STATE_AGGREGATIONS)
            .state("1", timestamp = 0, uptime = 0)

        Reporting.report().stringStateTracker("dist", ALL_STATE_AGGREGATIONS)
            .state("2", timestamp = 1.hours.inWholeMilliseconds, uptime = 1.hours.inWholeMilliseconds)

        assertThat(
            dao.collectHeartbeat(
                endTimestampMs = 2.hours.inWholeMilliseconds,
                endUptimeMs = 2.hours.inWholeMilliseconds,
            ),
        ).all {
            prop(CustomReport::hourlyHeartbeatReport).all {
                prop(MetricReport::startTimestampMs).isEqualTo(0)
                prop(MetricReport::endTimestampMs)
                    .isEqualTo(2.hours.inWholeMilliseconds)
                prop(MetricReport::metrics).containsOnly(
                    "dist.latest" to JsonPrimitive("2"),
                    "dist_1.secs/hour" to JsonPrimitive(30.minutes.inWholeSeconds),
                    "dist_1.total_secs" to JsonPrimitive(1.hours.inWholeSeconds),
                    "dist_1.mean_time_in_state_ms" to JsonPrimitive(1.hours.inWholeMilliseconds),
                    "dist_2.secs/hour" to JsonPrimitive(30.minutes.inWholeSeconds),
                    "dist_2.total_secs" to JsonPrimitive(1.hours.inWholeSeconds),
                )
            }
        }
    }

    @Test
    fun `happy path session`() = runTest {
        Reporting.session("session-1").apply {
            start(timestampMs = 0, uptimeMs = 0)
            counter("test", sumInReport = true).increment(timestamp = 0, uptime = 0)
        }

        val report1 = dao.collectHeartbeat(endTimestampMs = 1, endUptimeMs = 1)

        assertThat(report1.sessions).isEmpty()

        Reporting.session("session-1").finish(timestampMs = 2, uptimeMs = 2)

        val report2 = dao.collectHeartbeat(endTimestampMs = 3, endUptimeMs = 2)

        assertThat(report2.sessions).single()
        assertThat(report2.sessions).single().prop(MetricReport::reportType).isEqualTo("Session")
        assertThat(report2.sessions).single().prop(MetricReport::reportName).isEqualTo("session-1")
        assertThat(report2.sessions).single().prop(MetricReport::startTimestampMs).isEqualTo(0)
        assertThat(report2.sessions).single().prop(MetricReport::endTimestampMs).isEqualTo(2)
        assertThat(report2.sessions).single().prop(MetricReport::metrics).isEqualTo(
            mapOf("test.sum" to JsonPrimitive(1.0)),
        )

        assertThat(db.dao().dump()).isEqualTo(DbDump())
    }

    @Test
    fun `happy path start session`() = runTest {
        Reporting.session("session-1").apply {
            start(timestampMs = 0, uptimeMs = 10)
            finish(timestampMs = 1, uptimeMs = 20)
        }

        val report1 = dao.collectHeartbeat(endTimestampMs = 1, endUptimeMs = 20)

        assertThat(report1.sessions).single()
        assertThat(report1.sessions).single().prop(MetricReport::reportType).isEqualTo("Session")
        assertThat(report1.sessions).single().prop(MetricReport::reportName).isEqualTo("session-1")
        assertThat(report1.sessions).single().prop(MetricReport::startTimestampMs).isEqualTo(0)
        assertThat(report1.sessions).single().prop(MetricReport::endTimestampMs).isEqualTo(1)
        assertThat(report1.sessions).single().prop(MetricReport::startUptimeMs).isEqualTo(10)
        assertThat(report1.sessions).single().prop(MetricReport::endUptimeMs).isEqualTo(20)
        assertThat(report1.sessions).single().prop(MetricReport::metrics).isEqualTo(
            mapOf(),
        )
        assertThat(report1.sessions).single().prop(MetricReport::internalMetrics).isEmpty()
    }

    @Test
    fun `happy path with session`() = runTest {
        val now = System.currentTimeMillis()

        Reporting.session("session-1").apply {
            start()
            counter("test", sumInReport = true).increment()
            finish()
        }

        val report1 = dao.collectHeartbeat(
            endTimestampMs = System.currentTimeMillis(),
            endUptimeMs = System.currentTimeMillis(),
        )

        assertThat(report1.sessions).single()
        assertThat(report1.sessions).single().prop(MetricReport::reportType).isEqualTo("Session")
        assertThat(report1.sessions).single().prop(MetricReport::reportName).isEqualTo("session-1")
        assertThat(report1.sessions).single().prop(MetricReport::startTimestampMs).isGreaterThanOrEqualTo(now)
        assertThat(report1.sessions).single().prop(MetricReport::endTimestampMs).isLessThan(System.currentTimeMillis())
        assertThat(report1.sessions).single().prop(MetricReport::startTimestampMs).isGreaterThanOrEqualTo(now)
        assertThat(report1.sessions).single().prop(MetricReport::endUptimeMs).isLessThan(System.currentTimeMillis())
        assertThat(report1.sessions).single().prop(MetricReport::metrics).isEqualTo(
            mapOf("test.sum" to JsonPrimitive(1.0)),
        )
    }

    @Test
    fun `start session reserved names`(
        @TestParameter(
            value = [
                "heartbeat",
                "daily-heartbeat",
                "HEARTBEAT",
                "hello@memfault.com",
                "",
            ],
        ) sessionName: String,
    ) = runTest {
        assertThat(RemoteMetricsService.isSessionNameValid(sessionName)).isNotNull()
    }

    @Test
    fun `start session noop`() = runTest {
        Reporting.session("session-1").apply {
            start(timestampMs = 0, uptimeMs = 0)
            start(timestampMs = 1, uptimeMs = 1)
            finish(timestampMs = 2, uptimeMs = 2)
        }

        val report1 = dao.collectHeartbeat(endTimestampMs = 2, endUptimeMs = 2)

        assertThat(report1.sessions).single()
        assertThat(report1.sessions).single().prop(MetricReport::reportType).isEqualTo("Session")
        assertThat(report1.sessions).single().prop(MetricReport::reportName).isEqualTo("session-1")
        assertThat(report1.sessions).single().prop(MetricReport::startTimestampMs).isEqualTo(0)
        assertThat(report1.sessions).single().prop(MetricReport::endTimestampMs).isEqualTo(2)
        assertThat(report1.sessions).single().prop(MetricReport::startUptimeMs).isEqualTo(0)
        assertThat(report1.sessions).single().prop(MetricReport::endUptimeMs).isEqualTo(2)
        assertThat(report1.sessions).single().prop(MetricReport::metrics).isEqualTo(
            mapOf(),
        )
        assertThat(report1.sessions).single().prop(MetricReport::internalMetrics).isEmpty()
    }

    @Test
    fun `session no carryover`() = runTest {
        Reporting.session("session-1").apply {
            start(timestampMs = 0, uptimeMs = 0)
            stringProperty("carryover", addLatestToReport = true)
                .update("test", timestamp = 0, uptime = 0)
            finish(timestampMs = 2, uptimeMs = 2)
        }

        dao.collectHeartbeat(endTimestampMs = 3, endUptimeMs = 3).apply {
            assertThat(sessions).single().prop(MetricReport::startTimestampMs).isEqualTo(0)
            assertThat(sessions).single().prop(MetricReport::endTimestampMs).isEqualTo(2)
            assertThat(sessions).single().prop(MetricReport::startUptimeMs).isEqualTo(0)
            assertThat(sessions).single().prop(MetricReport::endUptimeMs).isEqualTo(2)
            assertThat(sessions).single().prop(MetricReport::metrics).isEqualTo(
                mapOf("carryover.latest" to JsonPrimitive("test")),
            )
        }

        dao.collectHeartbeat(endTimestampMs = 4, endUptimeMs = 4).apply {
            assertThat(sessions).isEmpty()
        }
    }

    @Test
    fun `session noop finish`() = runTest {
        Reporting.session("session-1").apply {
            start(timestampMs = 0, uptimeMs = 0)
            stringProperty("carryover", addLatestToReport = true)
                .update("test", timestamp = 0, uptime = 0)
            finish(timestampMs = 1, uptimeMs = 1)
        }

        dao.collectHeartbeat(endTimestampMs = 2, endUptimeMs = 2).apply {
            assertThat(sessions).single().prop(MetricReport::startTimestampMs).isEqualTo(0)
            assertThat(sessions).single().prop(MetricReport::endTimestampMs).isEqualTo(1)
            assertThat(sessions).single().prop(MetricReport::startUptimeMs).isEqualTo(0)
            assertThat(sessions).single().prop(MetricReport::endUptimeMs).isEqualTo(1)
            assertThat(sessions).single().prop(MetricReport::metrics).isEqualTo(
                mapOf("carryover.latest" to JsonPrimitive("test")),
            )
        }

        Reporting.session("session-1").finish(timestampMs = 3, uptimeMs = 3)

        dao.collectHeartbeat(endTimestampMs = 4, endUptimeMs = 4).apply {
            assertThat(sessions).isEmpty()
        }
    }

    @Test
    fun `session 0 duration`() = runTest {
        Reporting.session("session-1").apply {
            start(timestampMs = 0, uptimeMs = 0)
            stringStateTracker("state", aggregations = ALL_STATE_AGGREGATIONS)
                .state("1", timestamp = 0, uptime = 0)
            finish(timestampMs = 0, uptimeMs = 0)
        }

        dao.collectHeartbeat(endTimestampMs = 1, endUptimeMs = 1).apply {
            assertThat(sessions).single().prop(MetricReport::startTimestampMs).isEqualTo(0)
            assertThat(sessions).single().prop(MetricReport::endTimestampMs).isEqualTo(0)
            assertThat(sessions).single().prop(MetricReport::startUptimeMs).isEqualTo(0)
            assertThat(sessions).single().prop(MetricReport::endUptimeMs).isEqualTo(0)
            assertThat(sessions).single().prop(MetricReport::metrics).containsOnly(
                "state.latest" to JsonPrimitive("1"),
                "state_1.total_secs" to JsonPrimitive(0L),
                "state_1.secs/hour" to JsonPrimitive(0L),
            )
        }

        Reporting.session("session-1").finish(timestampMs = 3, uptimeMs = 3)

        dao.collectHeartbeat(endTimestampMs = 4, endUptimeMs = 0).apply {
            assertThat(sessions).isEmpty()
        }
    }

    @Test
    fun `session multiple`() = runTest {
        Reporting.session("session-a").apply {
            start(timestampMs = 0, uptimeMs = 0)
            stringProperty("carryover", addLatestToReport = true)
                .update("a", timestamp = 0, uptime = 0)
            finish(timestampMs = 1, uptimeMs = 1)
        }

        Reporting.session("session-b").apply {
            start(timestampMs = 1, uptimeMs = 1)
            stringProperty("carryover", addLatestToReport = true)
                .update("b", timestamp = 1, uptime = 1)
            finish(timestampMs = 2, uptimeMs = 2)
        }

        dao.collectHeartbeat(endTimestampMs = 2, endUptimeMs = 2).apply {
            assertThat(sessions).hasSize(2)

            assertThat(sessions).index(0).prop(MetricReport::reportName).isEqualTo("session-a")
            assertThat(sessions).index(0).prop(MetricReport::startTimestampMs).isEqualTo(0)
            assertThat(sessions).index(0).prop(MetricReport::endTimestampMs).isEqualTo(1)
            assertThat(sessions).index(0).prop(MetricReport::startUptimeMs).isEqualTo(0)
            assertThat(sessions).index(0).prop(MetricReport::endUptimeMs).isEqualTo(1)
            assertThat(sessions).index(0).prop(MetricReport::metrics).isEqualTo(
                mapOf(
                    "carryover.latest" to JsonPrimitive("a"),
                ),
            )

            assertThat(sessions).index(1).prop(MetricReport::reportName).isEqualTo("session-b")
            assertThat(sessions).index(1).prop(MetricReport::startTimestampMs).isEqualTo(1)
            assertThat(sessions).index(1).prop(MetricReport::endTimestampMs).isEqualTo(2)
            assertThat(sessions).index(1).prop(MetricReport::startUptimeMs).isEqualTo(1)
            assertThat(sessions).index(1).prop(MetricReport::endUptimeMs).isEqualTo(2)
            assertThat(sessions).index(1).prop(MetricReport::metrics).isEqualTo(
                mapOf(
                    "carryover.latest" to JsonPrimitive("b"),
                ),
            )
        }
    }

    @Test
    fun `session repeated session all numeric aggregations`() = runTest {
        Reporting.session("session").apply {
            start(timestampMs = 0, uptimeMs = 0)
            distribution("dist", ALL_NUMERIC_AGGREGATIONS)
                .record(5, timestamp = 0, uptime = 0)
            finish(timestampMs = 1, uptimeMs = 1)

            start(timestampMs = 2, uptimeMs = 2)
            distribution("dist", ALL_NUMERIC_AGGREGATIONS)
                .record(10, timestamp = 2, uptime = 2)
            distribution("dist", ALL_NUMERIC_AGGREGATIONS)
                .record(20, timestamp = 3, uptime = 3)
            finish(timestampMs = 4, uptimeMs = 4)
        }

        dao.collectHeartbeat(endTimestampMs = 4, endUptimeMs = 4).apply {
            assertThat(sessions).hasSize(2)

            assertThat(sessions).index(0).prop(MetricReport::reportName).isEqualTo("session")
            assertThat(sessions).index(0).prop(MetricReport::startTimestampMs).isEqualTo(0)
            assertThat(sessions).index(0).prop(MetricReport::endTimestampMs).isEqualTo(1)
            assertThat(sessions).index(0).prop(MetricReport::startUptimeMs).isEqualTo(0)
            assertThat(sessions).index(0).prop(MetricReport::endUptimeMs).isEqualTo(1)
            assertThat(sessions).index(0).prop(MetricReport::metrics).isEqualTo(
                mapOf(
                    "dist.latest" to JsonPrimitive(5.0),
                    "dist.sum" to JsonPrimitive(5.0),
                    "dist.count" to JsonPrimitive(1),
                    "dist.min" to JsonPrimitive(5.0),
                    "dist.mean" to JsonPrimitive(5.0),
                    "dist.max" to JsonPrimitive(5.0),
                ),
            )

            assertThat(sessions).index(1).prop(MetricReport::reportName).isEqualTo("session")
            assertThat(sessions).index(1).prop(MetricReport::startTimestampMs).isEqualTo(2)
            assertThat(sessions).index(1).prop(MetricReport::endTimestampMs).isEqualTo(4)
            assertThat(sessions).index(1).prop(MetricReport::startUptimeMs).isEqualTo(2)
            assertThat(sessions).index(1).prop(MetricReport::endUptimeMs).isEqualTo(4)
            assertThat(sessions).index(1).prop(MetricReport::metrics).isEqualTo(
                mapOf(
                    "dist.latest" to JsonPrimitive(20.0),
                    "dist.sum" to JsonPrimitive(30.0),
                    "dist.count" to JsonPrimitive(2),
                    "dist.min" to JsonPrimitive(10.0),
                    "dist.mean" to JsonPrimitive(15.0),
                    "dist.max" to JsonPrimitive(20.0),
                ),
            )
        }
    }

    @Test
    fun `report state aggregations`() = runTest {
        val state = Reporting.report().boolStateTracker("bool", listOf(TIME_TOTALS, TIME_PER_HOUR))
        state.state(true, 0, 0)

        dao.collectHeartbeat(endTimestampMs = 4, endUptimeMs = 4).apply {
            assertThat(hourlyHeartbeatReport).all {
                prop(MetricReport::startTimestampMs).isEqualTo(0)
                prop(MetricReport::endTimestampMs).isEqualTo(4)
                prop(MetricReport::startUptimeMs).isEqualTo(0)
                prop(MetricReport::endUptimeMs).isEqualTo(4)
                prop(MetricReport::metrics).containsOnly(
                    "bool_1.total_secs" to JsonPrimitive(0),
                    "bool_1.secs/hour" to JsonPrimitive(3600),
                )
            }
        }

        val report2EndMs = 3.minutes.inWholeMilliseconds - 1.seconds.inWholeMilliseconds + 4
        dao.collectHeartbeat(endTimestampMs = report2EndMs, endUptimeMs = report2EndMs).apply {
            assertThat(hourlyHeartbeatReport).all {
                prop(MetricReport::startTimestampMs).isEqualTo(4)
                prop(MetricReport::endTimestampMs).isEqualTo(report2EndMs)
                prop(MetricReport::startUptimeMs).isEqualTo(4)
                prop(MetricReport::endUptimeMs).isEqualTo(report2EndMs)
                prop(MetricReport::metrics).containsOnly(
                    "bool_1.total_secs" to JsonPrimitive(3.minutes.inWholeSeconds - 1),
                    "bool_1.secs/hour" to JsonPrimitive(3600),
                )
            }
        }

        val report3EndMs = 2.hours.inWholeMilliseconds + 3.minutes.inWholeMilliseconds
        dao.collectHeartbeat(endTimestampMs = report3EndMs, endUptimeMs = report3EndMs).apply {
            assertThat(hourlyHeartbeatReport).all {
                prop(MetricReport::startTimestampMs).isEqualTo(report2EndMs)
                prop(MetricReport::endTimestampMs).isEqualTo(report3EndMs)
                prop(MetricReport::startUptimeMs).isEqualTo(report2EndMs)
                prop(MetricReport::endUptimeMs).isEqualTo(report3EndMs)
                prop(MetricReport::metrics).containsOnly(
                    "bool_1.total_secs" to JsonPrimitive(2.hours.inWholeSeconds),
                    "bool_1.secs/hour" to JsonPrimitive(3600),
                )
            }
        }
    }

    @Test
    fun `report state aggregations changes with carryOver`() = runTest {
        val state = Reporting.report().boolStateTracker("bool", listOf(TIME_TOTALS, TIME_PER_HOUR))
        state.state(true, 0, uptime = 0)

        val report1End = 1.hours.inWholeMilliseconds
        dao.collectHeartbeat(endTimestampMs = report1End, endUptimeMs = report1End).apply {
            assertThat(hourlyHeartbeatReport).all {
                prop(MetricReport::startTimestampMs).isEqualTo(0)
                prop(MetricReport::endTimestampMs).isEqualTo(report1End)
                prop(MetricReport::metrics).containsOnly(
                    "bool_1.total_secs" to JsonPrimitive(3600),
                    "bool_1.secs/hour" to JsonPrimitive(3600),
                )
            }
        }

        state.state(false, 1.hours.inWholeMilliseconds + 5.seconds.inWholeMilliseconds, uptime = 0)

        val report2End = 1.hours.inWholeMilliseconds + 15.minutes.inWholeMilliseconds
        dao.collectHeartbeat(endTimestampMs = report2End, endUptimeMs = report2End).apply {
            assertThat(hourlyHeartbeatReport).all {
                prop(MetricReport::startTimestampMs).isEqualTo(report1End)
                prop(MetricReport::endTimestampMs).isEqualTo(report2End)
                prop(MetricReport::startUptimeMs).isEqualTo(report1End)
                prop(MetricReport::endUptimeMs).isEqualTo(report2End)
                prop(MetricReport::metrics).containsOnly(
                    "bool_1.mean_time_in_state_ms" to JsonPrimitive(
                        1.hours.inWholeMilliseconds + 5.seconds.inWholeMilliseconds,
                    ),

                    "bool_1.total_secs" to JsonPrimitive(5),
                    "bool_0.total_secs" to JsonPrimitive(895),
                    "bool_1.secs/hour" to JsonPrimitive(20),
                    "bool_0.secs/hour" to JsonPrimitive(3580),
                )
            }
        }
    }

    @Test
    fun `expire sessions older than 1 day`() = runTest {
        Reporting.session(
            "session",
        ).start(timestampMs = 15.minutes.inWholeMilliseconds, uptimeMs = 15.minutes.inWholeMilliseconds)

        val sync = Reporting.session("session").sync()

        sync.record(true, timestamp = 15.minutes.inWholeMilliseconds, uptime = 15.minutes.inWholeMilliseconds)

        val reportEndMs = (1.days + 15.minutes).inWholeMilliseconds
        dao.collectHeartbeat(endTimestampMs = reportEndMs, endUptimeMs = reportEndMs).apply {
            assertThat(sessions).hasSize(1)

            assertThat(sessions).single().prop(MetricReport::reportName).isEqualTo("session")
            assertThat(sessions).single().prop(MetricReport::startTimestampMs).isEqualTo(15.minutes.inWholeMilliseconds)
            assertThat(sessions).single().prop(MetricReport::endTimestampMs).isEqualTo(reportEndMs)
            assertThat(sessions).single().prop(MetricReport::startUptimeMs).isEqualTo(15.minutes.inWholeMilliseconds)
            assertThat(sessions).single().prop(MetricReport::endUptimeMs).isEqualTo(reportEndMs)

            assertThat(sessions).single().prop(MetricReport::metrics).isEqualTo(
                mapOf(
                    "sync_successful" to JsonPrimitive(1.0),
                ),
            )
        }
    }

    @Test
    fun `add operational_crashes to all reports`() = runTest {
        metricsDbTestEnvironment.excludeEverPresentMetrics = false

        Reporting.report().event("event", latestInReport = true).add("test", timestamp = 1, uptime = 1)
        val session1 = Reporting.session("session-1")
        session1.start(timestampMs = 2, uptimeMs = 2)
        val session2 = Reporting.session("session-2")
        session2.start(timestampMs = 3, uptimeMs = 3)

        val stringProperty = session2.stringProperty("string")
        stringProperty.update("test-string", timestamp = 4, uptime = 4)
        val numberProperty = session2.numberProperty("number")
        numberProperty.update(2L, timestamp = 5, uptime = 5)

        Reporting.session("session-3").apply {
            start(timestampMs = 6, uptimeMs = 6)
            boolStateTracker("bool", aggregations = listOf(StateAgg.LATEST_VALUE))
                .state(true, timestamp = 7, uptime = 7)
            finish(timestampMs = 8, uptimeMs = 8)
        }

        Reporting.report()
            .counter(name = OPERATIONAL_CRASHES_METRIC_KEY)
            .increment(timestamp = 9, uptime = 9)

        dao.collectHeartbeat(endTimestampMs = 10, endUptimeMs = 10).apply {
            assertThat(hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
                "event.latest" to JsonPrimitive("test"),

                OPERATIONAL_CRASHES_METRIC_KEY to JsonPrimitive(1.0),

                *DROP_BOX_TAGS.map { tag -> tag to JsonPrimitive(0.0) }.toTypedArray(),
            )

            assertThat(sessions).hasSize(1)

            assertThat(sessions).single().prop(MetricReport::reportName).isEqualTo("session-3")
            assertThat(sessions).single().prop(MetricReport::metrics).isEqualTo(
                mapOf(
                    "operational_crashes" to JsonPrimitive(0.0),
                    "bool.latest" to JsonPrimitive("1"),
                ),
            )
        }

        Reporting.report()
            .counter(name = OPERATIONAL_CRASHES_METRIC_KEY)
            .incrementBy(3, timestamp = 11, uptime = 11)

        session1.finish(timestampMs = 12, uptimeMs = 12)
        session2.finish(timestampMs = 13, uptimeMs = 13)

        dao.collectHeartbeat(endTimestampMs = 14, endUptimeMs = 14).apply {
            assertThat(hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
                OPERATIONAL_CRASHES_METRIC_KEY to JsonPrimitive(3.0),

                *DROP_BOX_TAGS.map { tag -> tag to JsonPrimitive(0.0) }.toTypedArray(),
            )

            assertThat(sessions).hasSize(2)

            assertThat(sessions).index(0).prop(MetricReport::reportName).isEqualTo("session-1")
            assertThat(sessions).index(0).prop(MetricReport::metrics).isEqualTo(
                mapOf(OPERATIONAL_CRASHES_METRIC_KEY to JsonPrimitive(4.0)),
            )

            assertThat(sessions).index(1).prop(MetricReport::reportName).isEqualTo("session-2")
            assertThat(sessions).index(1).prop(MetricReport::metrics).isEqualTo(
                mapOf(
                    OPERATIONAL_CRASHES_METRIC_KEY to JsonPrimitive(4.0),
                    "string.latest" to JsonPrimitive("test-string"),
                    "number.latest" to JsonPrimitive(2.0),
                ),
            )
        }
    }

    @Test
    fun `add sync and sync_memfault to all sessions`() = runTest {
        Reporting.report().stringStateTracker("stringState").state("that", timestamp = 1, uptime = 1)
        val session1 = Reporting.session("session-1")
        session1.start(timestampMs = 2, uptimeMs = 2)
        val session2 = Reporting.session("session-2")
        session2.start(timestampMs = 3, uptimeMs = 3)

        val memfaultSof = Reporting.report().successOrFailure("sync_memfault")
        memfaultSof.success(timestamp = 4, uptime = 4)
        memfaultSof.success(timestamp = 5, uptime = 4)
        memfaultSof.failure(timestamp = 6, uptime = 6)
        val sof = Reporting.report().sync()
        sof.failure(timestamp = 7, uptime = 7)
        sof.failure(timestamp = 8, uptime = 8)

        session2.sync().success(timestamp = 9, uptime = 9)
        session2.sync().failure(timestamp = 10, uptime = 10)

        session1.finish(timestampMs = 11, uptimeMs = 11)
        session2.finish(timestampMs = 12, uptimeMs = 12)

        memfaultSof.failure(timestamp = 13, uptime = 13)
        sof.success(timestamp = 14, uptime = 14)

        dao.collectHeartbeat(endTimestampMs = 15, endUptimeMs = 15).apply {
            assertThat(hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
                "sync_memfault_successful" to JsonPrimitive(2.0),
                "sync_memfault_failure" to JsonPrimitive(2.0),
                "sync_failure" to JsonPrimitive(2.0),
                "sync_successful" to JsonPrimitive(1.0),
            )

            assertThat(sessions).hasSize(2)

            assertThat(sessions).index(0).prop(MetricReport::reportName).isEqualTo("session-1")
            assertThat(sessions).index(0).prop(MetricReport::metrics).isEqualTo(
                mapOf(
                    "sync_memfault_successful" to JsonPrimitive(2.0),
                    "sync_memfault_failure" to JsonPrimitive(1.0),
                    "sync_failure" to JsonPrimitive(2.0),
                ),
            )

            assertThat(sessions).index(1).prop(MetricReport::reportName).isEqualTo("session-2")
            assertThat(sessions).index(1).prop(MetricReport::metrics).isEqualTo(
                mapOf(
                    "sync_memfault_successful" to JsonPrimitive(2.0),
                    "sync_memfault_failure" to JsonPrimitive(1.0),
                    "sync_failure" to JsonPrimitive(3.0),
                    "sync_successful" to JsonPrimitive(1.0),
                ),
            )
        }
    }

    @Test
    fun `add connectivity time vitals to all sessions`() = runTest {
        val now = System.currentTimeMillis()

        val connectivityMetric = Reporting.report()
            .stateTracker<ConnectivityState>(
                name = CONNECTIVITY_TYPE_METRIC,
                aggregations = listOf(TIME_PER_HOUR, TIME_TOTALS),
            )

        connectivityMetric.state(NONE, now, now)

        assertThat(
            dao.collectHeartbeat(
                endTimestampMs = now + 2.seconds.inWholeMilliseconds,
                endUptimeMs =
                now + 2.seconds.inWholeMilliseconds,
            ),
        ).all {
            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
                CONNECTED_TIME_METRIC to JsonPrimitive(0.0),
                EXPECTED_TIME_METRIC to JsonPrimitive(2_000.0),
                "connectivity.type_NONE.secs/hour" to JsonPrimitive(3600),
                "connectivity.type_NONE.total_secs" to JsonPrimitive(2),
            )

            prop(CustomReport::sessions).isEmpty()
        }

        Reporting.session("session-1").start(now + 4.seconds.inWholeMilliseconds, now + 4.seconds.inWholeMilliseconds)

        connectivityMetric.state(BLUETOOTH, now + 10.seconds.inWholeMilliseconds, now + 10.seconds.inWholeMilliseconds)

        assertThat(
            dao.collectHeartbeat(
                endTimestampMs = now + 12.seconds.inWholeMilliseconds,
                endUptimeMs =
                now + 12.seconds.inWholeMilliseconds,
            ),
        ).all {
            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
                CONNECTED_TIME_METRIC to JsonPrimitive(2_000.0),
                EXPECTED_TIME_METRIC to JsonPrimitive(10_000.0),
                "connectivity.type_NONE.secs/hour" to JsonPrimitive(2880),
                "connectivity.type_NONE.total_secs" to JsonPrimitive(8),
                "connectivity.type_NONE.mean_time_in_state_ms" to JsonPrimitive(10000),
                "connectivity.type_BLUETOOTH.secs/hour" to JsonPrimitive(720),
                "connectivity.type_BLUETOOTH.total_secs" to JsonPrimitive(2),
            )

            prop(CustomReport::sessions).isEmpty()
        }

        connectivityMetric.state(WIFI, now + 14.seconds.inWholeMilliseconds, now + 14.seconds.inWholeMilliseconds)

        Reporting.session(
            "session-1",
        ).finish(now + 16.seconds.inWholeMilliseconds, now + 16.seconds.inWholeMilliseconds)

        assertThat(
            dao.collectHeartbeat(
                endTimestampMs = now + 28.seconds.inWholeMilliseconds,
                endUptimeMs = now + 28.seconds.inWholeMilliseconds,
            ),
        ).all {
            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
                CONNECTED_TIME_METRIC to JsonPrimitive(16_000.0),
                EXPECTED_TIME_METRIC to JsonPrimitive(16_000.0),
                "connectivity.type_BLUETOOTH.secs/hour" to JsonPrimitive(450),
                "connectivity.type_BLUETOOTH.total_secs" to JsonPrimitive(2),
                "connectivity.type_BLUETOOTH.mean_time_in_state_ms" to JsonPrimitive(4000),
                "connectivity.type_WIFI.secs/hour" to JsonPrimitive(3150),
                "connectivity.type_WIFI.total_secs" to JsonPrimitive(14),
            )

            prop(CustomReport::sessions).single().prop(MetricReport::metrics).containsOnly(
                CONNECTED_TIME_METRIC to JsonPrimitive(6_000.0),
                EXPECTED_TIME_METRIC to JsonPrimitive(12_000.0),

                // Calculations
                // "connectivity.type_NONE.secs/hour" to JsonPrimitive(1800),
                // "connectivity.type_NONE.total_secs" to JsonPrimitive(6),
                // "connectivity.type_BLUETOOTH.secs/hour" to JsonPrimitive(1200),
                // "connectivity.type_BLUETOOTH.total_secs" to JsonPrimitive(4),
                // "connectivity.type_WIFI.secs/hour" to JsonPrimitive(600),
                // "connectivity.type_WIFI.total_secs" to JsonPrimitive(2),
            )

            prop(CustomReport::sessions).single().prop(MetricReport::internalMetrics).isEmpty()
        }
    }

    private val combinedTimeProvider = object : CombinedTimeProvider {
        override fun now(): CombinedTime = CombinedTime(
            uptime = BoxedDuration(10.minutes),
            elapsedRealtime = BoxedDuration(20.minutes),
            linuxBootId = "0000-0001",
            bootCount = 10,
            timestamp = Instant.ofEpochMilli(System.currentTimeMillis()),
        )
    }

    @Test
    fun `add battery vitals to all sessions`() = runTest {
        val now = System.currentTimeMillis()

        val batterySessionVitals = RealBatterySessionVitals(
            application = mockk(),
            combinedTimeProvider = combinedTimeProvider,
            batteryManager = mockk(),
            lastBatteryVitalsStateProvider = FakeLastBatteryVitalsStateProvider(),
            lastBatteryCycleCountStateProvider = FakeLastBatteryCycleCountStateProvider(),
            bortEnabledProvider = BortAlwaysEnabledProvider(),
            defaultCoroutineContext = testScheduler,
        )

        batterySessionVitals.record(isCharging = false, level = 80.0, now, uptimeMs = now)

        // Record a Hourly Heartbeat metric so reports are actually generated.
        Reporting.report().sync().success(timestamp = now, uptime = now)

        val session1 = Reporting.session("session-1")
        session1.start(now, uptimeMs = now)

        Reporting.session("session-2").apply {
            start(now + 2.seconds.inWholeMilliseconds, now + 2.seconds.inWholeMilliseconds)
            finish(now + 12.seconds.inWholeMilliseconds, now + 12.seconds.inWholeMilliseconds)

            start(now + 20.seconds.inWholeMilliseconds, now + 20.seconds.inWholeMilliseconds)
            batterySessionVitals.record(
                isCharging = false,
                level = 50.0,
                now + 22.seconds.inWholeMilliseconds,
                now + 22.seconds.inWholeMilliseconds,
            )
            finish(now + 24.seconds.inWholeMilliseconds, now + 24.seconds.inWholeMilliseconds)

            batterySessionVitals.record(
                isCharging = false,
                level = 40.0,
                now + 30.seconds.inWholeMilliseconds,
                now + 30.seconds.inWholeMilliseconds,
            )
            start(now + 30.seconds.inWholeMilliseconds, now + 30.seconds.inWholeMilliseconds)
        }

        assertThat(
            dao.collectHeartbeat(
                endTimestampMs = now + 30.seconds.inWholeMilliseconds,
                endUptimeMs =
                now + 30.seconds.inWholeMilliseconds,
            ),
        ).all {
            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
                "sync_successful" to JsonPrimitive(1.0),
            )

            prop(CustomReport::sessions).hasSize(2)

            prop(CustomReport::sessions).index(0).all {
                prop(MetricReport::reportName).isEqualTo("session-2")
                prop(MetricReport::metrics).containsOnly(
                    BATTERY_SOC_DROP_METRIC to JsonPrimitive(0.0),
                    BATTERY_DISCHARGE_DURATION_METRIC to JsonPrimitive(10_000.0),
                )
            }

            prop(CustomReport::sessions).index(1).all {
                prop(MetricReport::reportName).isEqualTo("session-2")
                prop(MetricReport::metrics).containsOnly(
                    BATTERY_SOC_DROP_METRIC to JsonPrimitive(30.0),
                    BATTERY_DISCHARGE_DURATION_METRIC to JsonPrimitive(4_000.0),
                )
            }
        }

        // Record a Hourly Heartbeat metric so reports are actually generated.
        Reporting.report().sync().success(
            timestamp = now + 30.seconds.inWholeMilliseconds,
            now + 30.seconds.inWholeMilliseconds,
        )

        batterySessionVitals.record(
            isCharging = true,
            level = 40.0,
            now + 40.seconds.inWholeMilliseconds,
            now + 40.seconds.inWholeMilliseconds,
        )
        batterySessionVitals.record(
            isCharging = true,
            level = 60.0,
            now + 50.seconds.inWholeMilliseconds,
            now + 50.seconds.inWholeMilliseconds,
        )
        Reporting.session(
            "session-2",
        ).finish(now + 60.seconds.inWholeMilliseconds, now + 60.seconds.inWholeMilliseconds)
        session1.finish(now + 60.seconds.inWholeMilliseconds, now + 60.seconds.inWholeMilliseconds)

        assertThat(
            dao.collectHeartbeat(
                endTimestampMs = now + 60.seconds.inWholeMilliseconds,
                now + 60.seconds.inWholeMilliseconds,
            ),
        ).all {
            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
                "sync_successful" to JsonPrimitive(1.0),
            )

            prop(CustomReport::sessions).hasSize(2)

            prop(CustomReport::sessions).index(0).all {
                prop(MetricReport::reportName).isEqualTo("session-1")
                prop(MetricReport::startTimestampMs).isEqualTo(now)
                prop(MetricReport::endTimestampMs).isEqualTo(now + 60.seconds.inWholeMilliseconds)
                prop(MetricReport::startUptimeMs).isEqualTo(now)
                prop(MetricReport::endUptimeMs).isEqualTo(now + 60.seconds.inWholeMilliseconds)
                prop(MetricReport::metrics).containsOnly(
                    BATTERY_SOC_DROP_METRIC to JsonPrimitive(40.0),
                    BATTERY_DISCHARGE_DURATION_METRIC to JsonPrimitive(40_000.0),
                    "sync_successful" to JsonPrimitive(1.0),
                )
            }

            prop(CustomReport::sessions).index(1).all {
                prop(MetricReport::reportName).isEqualTo("session-2")
                prop(MetricReport::startTimestampMs).isEqualTo(now + 30.seconds.inWholeMilliseconds)
                prop(MetricReport::endTimestampMs).isEqualTo(now + 60.seconds.inWholeMilliseconds)
                prop(MetricReport::startUptimeMs).isEqualTo(now + 30.seconds.inWholeMilliseconds)
                prop(MetricReport::endUptimeMs).isEqualTo(now + 60.seconds.inWholeMilliseconds)
                prop(MetricReport::metrics).containsOnly(
                    BATTERY_SOC_DROP_METRIC to JsonPrimitive(0.0),
                    BATTERY_DISCHARGE_DURATION_METRIC to JsonPrimitive(10_000.0),
                    "sync_successful" to JsonPrimitive(1.0),
                )
            }
        }
    }

    @Test
    fun `battery vitals drops`() = runTest {
        val now = System.currentTimeMillis()

        val batterySessionVitals = RealBatterySessionVitals(
            application = mockk(),
            combinedTimeProvider = combinedTimeProvider,
            batteryManager = mockk(),
            lastBatteryVitalsStateProvider = FakeLastBatteryVitalsStateProvider(),
            lastBatteryCycleCountStateProvider = FakeLastBatteryCycleCountStateProvider(),
            bortEnabledProvider = BortAlwaysEnabledProvider(),
            defaultCoroutineContext = testScheduler,
        )

        batterySessionVitals.record(isCharging = false, level = 50.0, now, now)

        // Record a Hourly Heartbeat metric so reports are actually generated.
        Reporting.report().sync().success(timestamp = now, now)

        Reporting.session("session").start(now, now)

        batterySessionVitals.record(
            isCharging = false,
            level = 45.0,
            now + 1.seconds.inWholeMilliseconds,
            now + 1.seconds.inWholeMilliseconds,
        )
        batterySessionVitals.record(
            isCharging = false,
            level = 40.0,
            now + 2.seconds.inWholeMilliseconds,
            now + 2.seconds.inWholeMilliseconds,
        )
        batterySessionVitals.record(
            isCharging = false,
            level = 35.0,
            now + 3.seconds.inWholeMilliseconds,
            now + 3.seconds.inWholeMilliseconds,
        )
        batterySessionVitals.record(
            isCharging = false,
            level = 30.0,
            now + 4.seconds.inWholeMilliseconds,
            now + 4.seconds.inWholeMilliseconds,
        )
        batterySessionVitals.record(
            isCharging = true,
            level = 35.0,
            now + 5.seconds.inWholeMilliseconds,
            now + 5.seconds.inWholeMilliseconds,
        )
        batterySessionVitals.record(
            isCharging = true,
            level = 40.0,
            now + 6.seconds.inWholeMilliseconds,
            now + 6.seconds.inWholeMilliseconds,
        )
        batterySessionVitals.record(
            isCharging = true,
            level = 45.0,
            now + 7.seconds.inWholeMilliseconds,
            now + 7.seconds.inWholeMilliseconds,
        )
        batterySessionVitals.record(
            isCharging = true,
            level = 50.0,
            now + 8.seconds.inWholeMilliseconds,
            now + 8.seconds.inWholeMilliseconds,
        )
        batterySessionVitals.record(
            isCharging = true,
            level = 55.0,
            now + 9.seconds.inWholeMilliseconds,
            now + 9.seconds.inWholeMilliseconds,
        )
        batterySessionVitals.record(
            isCharging = true,
            level = 60.0,
            now + 10.seconds.inWholeMilliseconds,
            now + 10.seconds.inWholeMilliseconds,
        )
        batterySessionVitals.record(
            isCharging = false,
            level = 55.0,
            now + 15.seconds.inWholeMilliseconds,
            now + 15.seconds.inWholeMilliseconds,
        )
        batterySessionVitals.record(
            isCharging = false,
            level = 50.0,
            now + 20.seconds.inWholeMilliseconds,
            now + 20.seconds.inWholeMilliseconds,
        )
        batterySessionVitals.record(
            isCharging = false,
            level = 45.0,
            now + 25.seconds.inWholeMilliseconds,
            now + 25.seconds.inWholeMilliseconds,
        )
        batterySessionVitals.record(
            isCharging = false,
            level = 40.0,
            now + 30.seconds.inWholeMilliseconds,
            now + 30.seconds.inWholeMilliseconds,
        )

        Reporting.session("session").finish(now + 60.seconds.inWholeMilliseconds, now + 60.seconds.inWholeMilliseconds)

        assertThat(
            dao.collectHeartbeat(
                endTimestampMs = now + 60.seconds.inWholeMilliseconds,
                endUptimeMs =
                now + 60.seconds.inWholeMilliseconds,
            ),
        ).all {
            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
                "sync_successful" to JsonPrimitive(1.0),
            )

            prop(CustomReport::sessions).single().all {
                prop(MetricReport::reportName).isEqualTo("session")
                prop(MetricReport::startTimestampMs).isEqualTo(now)
                prop(MetricReport::endTimestampMs).isEqualTo(now + 60.seconds.inWholeMilliseconds)
                prop(MetricReport::startUptimeMs).isEqualTo(now)
                prop(MetricReport::endUptimeMs).isEqualTo(now + 60.seconds.inWholeMilliseconds)
                prop(MetricReport::metrics).containsOnly(
                    BATTERY_SOC_DROP_METRIC to JsonPrimitive(40.0),
                    BATTERY_DISCHARGE_DURATION_METRIC to JsonPrimitive(50_000.0),
                )
            }
        }
    }

    @Test
    fun `battery vitals only charging`() = runTest {
        val now = System.currentTimeMillis()

        val batterySessionVitals = RealBatterySessionVitals(
            application = mockk(),
            combinedTimeProvider = combinedTimeProvider,
            batteryManager = mockk(),
            lastBatteryVitalsStateProvider = FakeLastBatteryVitalsStateProvider(),
            lastBatteryCycleCountStateProvider = FakeLastBatteryCycleCountStateProvider(),
            bortEnabledProvider = BortAlwaysEnabledProvider(),
            defaultCoroutineContext = testScheduler,
        )

        batterySessionVitals.record(isCharging = true, level = 50.0, now, now)

        // Record a Hourly Heartbeat metric so reports are actually generated.
        Reporting.report().sync().success(timestamp = now, uptime = now)

        Reporting.session("session").apply {
            start(now, now)
            finish(now + 60.seconds.inWholeMilliseconds, now + 60.seconds.inWholeMilliseconds)
        }

        assertThat(
            dao.collectHeartbeat(
                endTimestampMs = now + 60.seconds.inWholeMilliseconds,
                endUptimeMs =
                now + 60.seconds.inWholeMilliseconds,
            ),
        ).all {
            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
                "sync_successful" to JsonPrimitive(1.0),
            )

            prop(CustomReport::sessions).single().all {
                prop(MetricReport::reportName).isEqualTo("session")
                prop(MetricReport::startTimestampMs).isEqualTo(now)
                prop(MetricReport::endTimestampMs).isEqualTo(now + 60.seconds.inWholeMilliseconds)
                prop(MetricReport::startUptimeMs).isEqualTo(now)
                prop(MetricReport::endUptimeMs).isEqualTo(now + 60.seconds.inWholeMilliseconds)
                prop(MetricReport::metrics).isEmpty()
            }
        }
    }

    @Test
    fun `battery vitals ignore if no discharge even if somehow drain`() = runTest {
        val now = System.currentTimeMillis()

        val batterySessionVitals = RealBatterySessionVitals(
            application = mockk(),
            combinedTimeProvider = combinedTimeProvider,
            batteryManager = mockk(),
            lastBatteryVitalsStateProvider = FakeLastBatteryVitalsStateProvider(),
            lastBatteryCycleCountStateProvider = FakeLastBatteryCycleCountStateProvider(),
            bortEnabledProvider = BortAlwaysEnabledProvider(),
            defaultCoroutineContext = testScheduler,
        )

        batterySessionVitals.record(isCharging = true, level = 50.0, now, now)
        batterySessionVitals.record(
            isCharging = true,
            level = 40.0,
            now + 30.seconds.inWholeMilliseconds,
            now + 30.seconds.inWholeMilliseconds,
        )

        // Record a Hourly Heartbeat metric so reports are actually generated.
        Reporting.report().sync().success(timestamp = now, uptime = now)

        Reporting.session("session").apply {
            start(now, now)
            finish(now + 60.seconds.inWholeMilliseconds, now + 60.seconds.inWholeMilliseconds)
        }

        assertThat(
            dao.collectHeartbeat(
                endTimestampMs = now + 60.seconds.inWholeMilliseconds,
                endUptimeMs =
                now + 60.seconds.inWholeMilliseconds,
            ),
        ).all {
            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
                "sync_successful" to JsonPrimitive(1.0),
            )

            prop(CustomReport::sessions).single().all {
                prop(MetricReport::reportName).isEqualTo("session")
                prop(MetricReport::startTimestampMs).isEqualTo(now)
                prop(MetricReport::endTimestampMs).isEqualTo(now + 60.seconds.inWholeMilliseconds)
                prop(MetricReport::metrics).isEmpty()
            }
        }
    }

    @Test
    fun `battery vitals counts drain even if charging`() = runTest {
        val now = System.currentTimeMillis()

        val batterySessionVitals = RealBatterySessionVitals(
            application = mockk(),
            combinedTimeProvider = combinedTimeProvider,
            batteryManager = mockk(),
            lastBatteryVitalsStateProvider = FakeLastBatteryVitalsStateProvider(),
            lastBatteryCycleCountStateProvider = FakeLastBatteryCycleCountStateProvider(),
            bortEnabledProvider = BortAlwaysEnabledProvider(),
            defaultCoroutineContext = testScheduler,
        )

        batterySessionVitals.record(isCharging = true, level = 50.0, now, now)
        batterySessionVitals.record(
            isCharging = true,
            level = 40.0,
            now + 10.seconds.inWholeMilliseconds,
            now + 10.seconds.inWholeMilliseconds,
        )
        batterySessionVitals.record(
            isCharging = false,
            level = 35.0,
            now + 20.seconds.inWholeMilliseconds,
            now + 20.seconds.inWholeMilliseconds,
        )
        batterySessionVitals.record(
            isCharging = false,
            level = 30.0,
            now + 30.seconds.inWholeMilliseconds,
            now + 30.seconds.inWholeMilliseconds,
        )
        batterySessionVitals.record(
            isCharging = false,
            level = 25.0,
            now + 40.seconds.inWholeMilliseconds,
            now + 40.seconds.inWholeMilliseconds,
        )
        batterySessionVitals.record(
            isCharging = false,
            level = 20.0,
            now + 50.seconds.inWholeMilliseconds,
            now + 50.seconds.inWholeMilliseconds,
        )
        batterySessionVitals.record(
            isCharging = false,
            level = 15.0,
            now + 60.seconds.inWholeMilliseconds,
            now + 60.seconds.inWholeMilliseconds,
        )

        // Record a Hourly Heartbeat metric so reports are actually generated.
        Reporting.report().sync().success(timestamp = now, uptime = now)

        Reporting.session("session").apply {
            start(now, now)
            finish(now + 60.seconds.inWholeMilliseconds, now + 60.seconds.inWholeMilliseconds)
        }

        assertThat(
            dao.collectHeartbeat(
                endTimestampMs = now + 60.seconds.inWholeMilliseconds,
                endUptimeMs =
                now + 60.seconds.inWholeMilliseconds,
            ),
        ).all {
            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
                "sync_successful" to JsonPrimitive(1.0),
            )

            prop(CustomReport::sessions).single().all {
                prop(MetricReport::reportName).isEqualTo("session")
                prop(MetricReport::startTimestampMs).isEqualTo(now)
                prop(MetricReport::endTimestampMs).isEqualTo(now + 60.seconds.inWholeMilliseconds)
                prop(MetricReport::startUptimeMs).isEqualTo(now)
                prop(MetricReport::endUptimeMs).isEqualTo(now + 60.seconds.inWholeMilliseconds)
                prop(MetricReport::metrics).containsOnly(
                    BATTERY_SOC_DROP_METRIC to JsonPrimitive(35.0),
                    BATTERY_DISCHARGE_DURATION_METRIC to JsonPrimitive(40_000.0),
                )
            }
        }
    }

    @Test
    fun `ValueAggregations keeps carryover value`() = runTest {
        val now = System.currentTimeMillis()

        Reporting.report().stringStateTracker("state", listOf(TIME_TOTALS))
            .state(
                "on",
                timestamp = now - 60.seconds.inWholeMilliseconds,
                uptime = now - 60.seconds.inWholeMilliseconds,
            )

        assertThat(dao.collectHeartbeat(endTimestampMs = now, endUptimeMs = now)).all {
            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
                "state_on.total_secs" to JsonPrimitive(60),
            )
        }

        Reporting.report().stringStateTracker("state", listOf(TIME_TOTALS))
            .state("off", timestamp = now + 5.seconds.inWholeMilliseconds, uptime = now + 5.seconds.inWholeMilliseconds)

        assertThat(
            dao.collectHeartbeat(
                endTimestampMs = now + 30.seconds.inWholeMilliseconds,
                endUptimeMs =
                now + 30.seconds.inWholeMilliseconds,
            ),
        ).all {
            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
                "state_off.total_secs" to JsonPrimitive(25),
                "state_on.total_secs" to JsonPrimitive(5),
                "state_on.mean_time_in_state_ms" to JsonPrimitive(65000),
            )
        }
    }

    @Test
    fun `ValueAggregations ValueDrop only latest carry over`() = runTest {
        val now = System.currentTimeMillis()

        val batterySessionVitals = RealBatterySessionVitals(
            application = mockk(),
            combinedTimeProvider = combinedTimeProvider,
            batteryManager = mockk(),
            lastBatteryVitalsStateProvider = FakeLastBatteryVitalsStateProvider(),
            lastBatteryCycleCountStateProvider = FakeLastBatteryCycleCountStateProvider(),
            bortEnabledProvider = BortAlwaysEnabledProvider(),
            defaultCoroutineContext = testScheduler,
        )

        batterySessionVitals.record(
            isCharging = false,
            level = 50.0,
            now - 60.seconds.inWholeMilliseconds,
            now - 60.seconds.inWholeMilliseconds,
        )

        // Record a Hourly Heartbeat metric so reports are actually generated.
        Reporting.report().sync().success(timestamp = now, uptime = now)

        assertThat(dao.collectHeartbeat(endTimestampMs = now, endUptimeMs = now)).all {
            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
                "sync_successful" to JsonPrimitive(1.0),
            )

            prop(CustomReport::sessions).isEmpty()
        }

        Reporting.session("session").start(now, now)

        // Set the battery to 30, but right at the moment when the next heartbeat starts, ignoring the original
        // level of 50.
        batterySessionVitals.record(isCharging = false, level = 25.0, now, now)

        // Record a Hourly Heartbeat metric so reports are actually generated.
        Reporting.report().sync().success(timestamp = now, now)

        Reporting.session("session").finish(now + 30.seconds.inWholeMilliseconds, now + 30.seconds.inWholeMilliseconds)

        assertThat(
            dao.collectHeartbeat(
                endTimestampMs = now + 60.seconds.inWholeMilliseconds,
                endUptimeMs =
                now + 60.seconds.inWholeMilliseconds,
            ),
        ).all {
            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
                "sync_successful" to JsonPrimitive(1.0),
            )

            prop(CustomReport::sessions).single().all {
                prop(MetricReport::reportName).isEqualTo("session")
                prop(MetricReport::startTimestampMs).isEqualTo(now)
                prop(MetricReport::endTimestampMs).isEqualTo(now + 30.seconds.inWholeMilliseconds)
                prop(MetricReport::startUptimeMs).isEqualTo(now)
                prop(MetricReport::endUptimeMs).isEqualTo(now + 30.seconds.inWholeMilliseconds)
                prop(MetricReport::metrics).containsOnly(
                    "sync_successful" to JsonPrimitive(1.0),
                    BATTERY_SOC_DROP_METRIC to JsonPrimitive(0.0),
                    BATTERY_DISCHARGE_DURATION_METRIC to JsonPrimitive(30.seconds.inWholeMilliseconds.toDouble()),
                )
            }
        }
    }

    @Test
    fun `Thermal aggregations`() = runTest {
        val cpu1 = Reporting.report().distribution("thermal_cpu_CPU1_c", listOf(MIN, MAX, MEAN))
        cpu1.record(3.5)
        cpu1.record(5.0)
        val cpu2 = Reporting.report().distribution("thermal_cpu_xxx_c", listOf(MIN, MAX, MEAN))
        cpu2.record(9.0)
        cpu2.record(1.0)
        val battery = Reporting.report().distribution("thermal_battery_bat0_c", listOf(MIN, MAX, MEAN))
        battery.record(1.0)
        battery.record(2.0)
        battery.record(4.5)

        assertThat(
            dao.collectHeartbeat(endTimestampMs = System.currentTimeMillis(), endUptimeMs = System.currentTimeMillis()),
        ).all {
            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
                // Per-sensor
                "thermal_cpu_CPU1_c" to JsonPrimitive(4.25),
                "thermal_cpu_CPU1_c_max" to JsonPrimitive(5.0),
                "thermal_cpu_xxx_c" to JsonPrimitive(5.0),
                "thermal_cpu_xxx_c_max" to JsonPrimitive(9.0),
                "thermal_battery_bat0_c" to JsonPrimitive(2.5),
                "thermal_battery_bat0_c_max" to JsonPrimitive(4.5),
                // CPU averages
                "thermal_cpu_c" to JsonPrimitive(4.625),
                "thermal_cpu_c_max" to JsonPrimitive(9.0),
                // Battery averages
                "thermal_battery_c" to JsonPrimitive(2.5),
                "thermal_battery_c_max" to JsonPrimitive(4.5),
                // Note that .min aggregations have been removed
            )
        }
    }

    @Test
    fun `Thermal aggregations with legacy metrics`() = runTest {
        metricsDbTestEnvironment.thermalCollectLegacyMetricsValue = true

        val cpu1 = Reporting.report().distribution("thermal_cpu_CPU1_c", listOf(MIN, MAX, MEAN))
        cpu1.record(3.5)
        cpu1.record(5.0)
        val cpu2 = Reporting.report().distribution("thermal_cpu_xxx_c", listOf(MIN, MAX, MEAN))
        cpu2.record(9.0)
        cpu2.record(1.0)
        val battery = Reporting.report().distribution("thermal_battery_bat0_c", listOf(MIN, MAX, MEAN))
        battery.record(1.0)
        battery.record(2.0)
        battery.record(4.5)

        assertThat(
            dao.collectHeartbeat(endTimestampMs = System.currentTimeMillis(), endUptimeMs = System.currentTimeMillis()),
        ).all {
            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
                // Per-sensor
                "thermal_cpu_CPU1_c" to JsonPrimitive(4.25),
                "thermal_cpu_CPU1_c_max" to JsonPrimitive(5.0),
                "thermal_cpu_xxx_c" to JsonPrimitive(5.0),
                "thermal_cpu_xxx_c_max" to JsonPrimitive(9.0),
                "thermal_battery_bat0_c" to JsonPrimitive(2.5),
                "thermal_battery_bat0_c_max" to JsonPrimitive(4.5),
                // CPU averages
                "thermal_cpu_c" to JsonPrimitive(4.625),
                "thermal_cpu_c_max" to JsonPrimitive(9.0),
                // Battery averages
                "thermal_battery_c" to JsonPrimitive(2.5),
                "thermal_battery_c_max" to JsonPrimitive(4.5),
                // Note that .min aggregations have been removed for new metrics, but are still here for legacy metrics
                "temp.cpu_0.min" to JsonPrimitive(3.5),
                "temp.cpu_0.mean" to JsonPrimitive(4.25),
                "temp.cpu_0.max" to JsonPrimitive(5.0),
                "temp.cpu_1.min" to JsonPrimitive(1.0),
                "temp.cpu_1.mean" to JsonPrimitive(5.0),
                "temp.cpu_1.max" to JsonPrimitive(9.0),
            )
        }
    }

    @Test
    fun `Internal metrics renamed correctly`() = runTest {
        val internalMetric =
            Reporting.report().stringProperty("internal_metric", addLatestToReport = true, internal = true)
        internalMetric.update("test value")

        assertThat(
            dao.collectHeartbeat(endTimestampMs = System.currentTimeMillis(), endUptimeMs = System.currentTimeMillis()),
        ).all {
            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::internalMetrics).containsOnly(
                "internal_metric" to JsonPrimitive("test value"),
            )
        }
    }

    @Test
    fun `battery vitals deduplication - repeated identical values write only one DB row`() = runTest {
        val now = System.currentTimeMillis()
        val batterySessionVitals = RealBatterySessionVitals(
            application = mockk(),
            combinedTimeProvider = combinedTimeProvider,
            batteryManager = mockk(),
            lastBatteryVitalsStateProvider = FakeLastBatteryVitalsStateProvider(),
            lastBatteryCycleCountStateProvider = FakeLastBatteryCycleCountStateProvider(),
            bortEnabledProvider = BortAlwaysEnabledProvider(),
            defaultCoroutineContext = testScheduler,
        )

        // First call always records
        batterySessionVitals.record(isCharging = false, level = 80.0, timestampMs = now, uptimeMs = now)
        // Repeated identical values - should be deduplicated
        batterySessionVitals.record(isCharging = false, level = 80.0, timestampMs = now + 1000, uptimeMs = now + 1000)
        batterySessionVitals.record(isCharging = false, level = 80.0, timestampMs = now + 2000, uptimeMs = now + 2000)
        // Changed level - should record
        batterySessionVitals.record(isCharging = false, level = 70.0, timestampMs = now + 3000, uptimeMs = now + 3000)
        // Same again - deduplicated
        batterySessionVitals.record(isCharging = false, level = 70.0, timestampMs = now + 4000, uptimeMs = now + 4000)

        val dump = db.dao().dump()
        val levelMetadataId = dump.metadata.first { it.eventName == BATTERY_LEVEL_METRIC }.id
        val chargingMetadataId = dump.metadata.first { it.eventName == BATTERY_CHARGING_METRIC }.id

        assertThat(dump.values.filter { it.metadataId == levelMetadataId }.map { it.numberVal })
            .containsOnly(80.0, 70.0)
        assertThat(dump.values.filter { it.metadataId == chargingMetadataId })
            .hasSize(2)
    }

    @Test
    fun `battery cycle count deduplication - repeated identical values write only one DB row`() = runTest {
        val now = System.currentTimeMillis()
        val batterySessionVitals = RealBatterySessionVitals(
            application = mockk(),
            combinedTimeProvider = combinedTimeProvider,
            batteryManager = mockk(),
            lastBatteryVitalsStateProvider = FakeLastBatteryVitalsStateProvider(),
            lastBatteryCycleCountStateProvider = FakeLastBatteryCycleCountStateProvider(),
            bortEnabledProvider = BortAlwaysEnabledProvider(),
            defaultCoroutineContext = testScheduler,
        )

        batterySessionVitals.record(
            isCharging = false,
            level = 80.0,
            timestampMs = now,
            uptimeMs = now,
            cycleCount = 100,
        )
        batterySessionVitals.record(
            isCharging = false,
            level = 80.0,
            timestampMs = now + 1000,
            uptimeMs = now + 1000,
            cycleCount = 100,
        )
        batterySessionVitals.record(
            isCharging = false,
            level = 80.0,
            timestampMs = now + 2000,
            uptimeMs = now + 2000,
            cycleCount = 101,
        )
        batterySessionVitals.record(
            isCharging = false,
            level = 80.0,
            timestampMs = now + 3000,
            uptimeMs = now + 3000,
            cycleCount = 101,
        )

        val dump = db.dao().dump()
        val cycleMetadataId = dump.metadata.first { it.eventName == "battery.charge_cycle_count" }.id

        assertThat(dump.values.filter { it.metadataId == cycleMetadataId }.map { it.numberVal })
            .containsOnly(100.0, 101.0)
    }

    @Test
    fun `battery vitals deduplication resets after report collection`() = runTest {
        val now = System.currentTimeMillis()
        val application = mockk<Application>()
        every { application.registerReceiver(isNull(), any()) } returns null
        val batterySessionVitals = RealBatterySessionVitals(
            application = application,
            combinedTimeProvider = combinedTimeProvider,
            batteryManager = mockk(),
            lastBatteryVitalsStateProvider = FakeLastBatteryVitalsStateProvider(),
            lastBatteryCycleCountStateProvider = FakeLastBatteryCycleCountStateProvider(),
            bortEnabledProvider = BortAlwaysEnabledProvider(),
            defaultCoroutineContext = testScheduler,
        )

        // First report: record battery at 80%
        batterySessionVitals.record(isCharging = false, level = 80.0, timestampMs = now, uptimeMs = now)
        // Duplicate - should be skipped
        batterySessionVitals.record(isCharging = false, level = 80.0, timestampMs = now + 1000, uptimeMs = now + 1000)

        // Report boundary: collect heartbeat, then reset dedup state
        dao.collectHeartbeat(endTimestampMs = now + 2000, endUptimeMs = now + 2000)
        batterySessionVitals.onReportCollected()

        // Second report: same battery level - must write an initial sample for VALUE_DROP to work
        batterySessionVitals.record(isCharging = false, level = 80.0, timestampMs = now + 3000, uptimeMs = now + 4000)
        // This duplicate should still be skipped within the same report
        batterySessionVitals.record(isCharging = false, level = 80.0, timestampMs = now + 4000, uptimeMs = now + 4000)

        val dump = db.dao().dump()
        val levelMetadataId = dump.metadata.first { it.eventName == BATTERY_LEVEL_METRIC }.id

        // Exactly 2 rows: one from report 1, one from report 2 (not the duplicates)
        val levelValues = dump.values.filter { it.metadataId == levelMetadataId }
        assertThat(levelValues).hasSize(2)
        assertThat(levelValues.map { it.numberVal }).containsOnly(80.0)
    }

    @Test
    fun `cycle count is re-seeded from sticky broadcast after report collection`() = runTest {
        val now = System.currentTimeMillis()
        val stickyIntent = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_LEVEL, 75)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_PLUGGED, 0)
            putExtra("android.os.extra.CYCLE_COUNT", 42)
        }
        val application = mockk<Application>()
        every { application.registerReceiver(isNull(), any()) } returns stickyIntent
        val batterySessionVitals = RealBatterySessionVitals(
            application = application,
            combinedTimeProvider = combinedTimeProvider,
            batteryManager = mockk(),
            lastBatteryVitalsStateProvider = FakeLastBatteryVitalsStateProvider(),
            lastBatteryCycleCountStateProvider = FakeLastBatteryCycleCountStateProvider(),
            bortEnabledProvider = BortAlwaysEnabledProvider(),
            defaultCoroutineContext = testScheduler,
        )

        batterySessionVitals.record(
            isCharging = false,
            level = 75.0,
            timestampMs = now,
            uptimeMs = now,
            cycleCount = 42,
        )

        dao.collectHeartbeat(endTimestampMs = now + 2000, endUptimeMs = now + 2000)
        batterySessionVitals.onReportCollected()

        val dump = db.dao().dump()
        val cycleMetadataId = dump.metadata.first { it.eventName == "battery.charge_cycle_count" }.id
        val cycleValues = dump.values.filter { it.metadataId == cycleMetadataId }
        assertThat(cycleValues).hasSize(1)
        assertThat(cycleValues.map { it.numberVal }).containsOnly(42.0)
    }

    @Test(timeout = 10_000)
    fun `Re-entrant call doesn't lock`() = runTest {
        metricsDbTestEnvironment.sessionTokenBucketFactory = RealTokenBucketFactory(
            defaultCapacity = 0,
            defaultPeriod = 1.days,
            metrics = BuiltinMetricsStore(),
        )

        val time = System.currentTimeMillis()

        Reporting.session("session")
        val anySession = Reporting.session("session")
        anySession.start(time, time)
        anySession.start(time + 1, time + 1)
        anySession.finish(time + 3, time + 3)

        assertThat(
            dao.collectHeartbeat(endTimestampMs = System.currentTimeMillis(), endUptimeMs = System.currentTimeMillis()),
        ).all {
            prop(CustomReport::hourlyHeartbeatReport).all {
                prop(MetricReport::internalMetrics).all {
                    contains("rate_limit_applied_session" to JsonPrimitive(2.0))
                }
            }
            prop(CustomReport::sessions).isEmpty()
        }
    }
}
