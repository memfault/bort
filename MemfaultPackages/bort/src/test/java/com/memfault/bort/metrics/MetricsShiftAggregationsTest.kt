package com.memfault.bort.metrics

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.prop
import com.google.testing.junit.testparameterinjector.TestParameter
import com.memfault.bort.metrics.custom.CustomMetrics
import com.memfault.bort.metrics.custom.CustomReport
import com.memfault.bort.metrics.custom.MetricReport
import com.memfault.bort.reporting.NumericAgg
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.reporting.StateAgg.TIME_TOTALS
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestParameterInjector
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@RunWith(RobolectricTestParameterInjector::class)
@Config(sdk = [26])
class MetricsShiftAggregationsTest {

    private val Int.minutesMs: Long
        get() = minutes.inWholeMilliseconds
    private val Int.hoursMs: Long
        get() = hours.inWholeMilliseconds

    @get:Rule
    val metricsDbTestEnvironment = MetricsDbTestEnvironment().apply {
        highResMetricsEnabledValue = true
    }

    private val dao: CustomMetrics get() = metricsDbTestEnvironment.dao

    @Test
    fun `happy path bool`() = runTest {
        val stateTracker = Reporting.report().boolStateTracker("state", listOf(TIME_TOTALS))

        val now = System.currentTimeMillis()

        stateTracker.state(true, timestamp = now)
        stateTracker.state(false, timestamp = now + 5.minutesMs)
        stateTracker.state(true, timestamp = now + 15.minutesMs)
        stateTracker.state(false, timestamp = now + 20.minutesMs)
        stateTracker.state(true, timestamp = now + 30.minutesMs)
        stateTracker.state(false, timestamp = now + 35.minutesMs)
        stateTracker.state(true, timestamp = now + 45.minutesMs)

        val report = dao.collectHeartbeat(endTimestampMs = now + 45.minutesMs)

        assertThat(report.hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
            "state_1.total_secs" to JsonPrimitive(15.minutes.inWholeSeconds),
            "state_0.total_secs" to JsonPrimitive(30.minutes.inWholeSeconds),
            "state_1.mean_time_in_state_ms" to JsonPrimitive(5.minutesMs),
            "state_0.mean_time_in_state_ms" to JsonPrimitive(10.minutesMs),
        )
    }

    @Test
    fun `happy path enum`() = runTest {
        val stateTracker = Reporting.report().stateTracker<NumericAgg>("state", listOf(TIME_TOTALS))

        val now = System.currentTimeMillis()

        stateTracker.state(NumericAgg.MIN, timestamp = now)
        stateTracker.state(NumericAgg.MAX, timestamp = now + 5.minutesMs)
        stateTracker.state(NumericAgg.MIN, timestamp = now + 15.minutesMs)
        stateTracker.state(NumericAgg.MAX, timestamp = now + 20.minutesMs)
        stateTracker.state(NumericAgg.MIN, timestamp = now + 30.minutesMs)
        stateTracker.state(NumericAgg.MAX, timestamp = now + 35.minutesMs)
        stateTracker.state(NumericAgg.MIN, timestamp = now + 45.minutesMs)

        val report = dao.collectHeartbeat(endTimestampMs = now + 45.minutesMs)

        assertThat(report.hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
            "state_MIN.total_secs" to JsonPrimitive(15.minutes.inWholeSeconds),
            "state_MAX.total_secs" to JsonPrimitive(30.minutes.inWholeSeconds),
            "state_MIN.mean_time_in_state_ms" to JsonPrimitive(5.minutesMs),
            "state_MAX.mean_time_in_state_ms" to JsonPrimitive(10.minutesMs),
        )
    }

    @Test
    fun `happy path string`() = runTest {
        val stateTracker = Reporting.report().stringStateTracker("state", listOf(TIME_TOTALS))

        val now = System.currentTimeMillis()

        stateTracker.state("true", timestamp = now)
        stateTracker.state("false", timestamp = now + 5.minutesMs)
        stateTracker.state("true", timestamp = now + 15.minutesMs)
        stateTracker.state("false", timestamp = now + 20.minutesMs)
        stateTracker.state("true", timestamp = now + 30.minutesMs)
        stateTracker.state("false", timestamp = now + 35.minutesMs)
        stateTracker.state("true", timestamp = now + 45.minutesMs)

        val report = dao.collectHeartbeat(endTimestampMs = now + 45.minutesMs)

        assertThat(report.hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
            "state_true.total_secs" to JsonPrimitive(15.minutes.inWholeSeconds),
            "state_false.total_secs" to JsonPrimitive(30.minutes.inWholeSeconds),
            "state_true.mean_time_in_state_ms" to JsonPrimitive(5.minutesMs),
            "state_false.mean_time_in_state_ms" to JsonPrimitive(10.minutesMs),
        )
    }

    @Test
    fun `multiple heartbeats repeating state commits average`() = runTest {
        val stateTracker = Reporting.report().stringStateTracker("state", listOf(TIME_TOTALS))

        val now = System.currentTimeMillis()

        stateTracker.state("true", timestamp = now)

        assertThat(dao.collectHeartbeat(endTimestampMs = now + 1.hoursMs)).all {
            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
                "state_true.total_secs" to JsonPrimitive(1.hours.inWholeSeconds),
            )
        }

        stateTracker.state("true", timestamp = now + 2.hoursMs)

        assertThat(dao.collectHeartbeat(endTimestampMs = now + 2.hoursMs)).all {
            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
                "state_true.total_secs" to JsonPrimitive(1.hours.inWholeSeconds),
            )
        }

        stateTracker.state("true", timestamp = now + 3.hoursMs)
        stateTracker.state("true", timestamp = now + 4.hoursMs)

        assertThat(dao.collectHeartbeat(endTimestampMs = now + 6.hoursMs)).all {
            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
                "state_true.total_secs" to JsonPrimitive(4.hours.inWholeSeconds),
            )
        }

        stateTracker.state("true", timestamp = now + 8.hoursMs)
        stateTracker.state("false", timestamp = now + 10.hoursMs)
        stateTracker.state("false", timestamp = now + 12.hoursMs)

        assertThat(dao.collectHeartbeat(endTimestampMs = now + 12.hoursMs)).all {
            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
                "state_true.total_secs" to JsonPrimitive(4.hours.inWholeSeconds),
                "state_false.total_secs" to JsonPrimitive(2.hours.inWholeSeconds),

                "state_true.mean_time_in_state_ms" to JsonPrimitive(10.hours.inWholeMilliseconds),
            )
        }
    }

    @Test
    fun `multiple heartbeats continues shift`() = runTest {
        val stateTracker = Reporting.report().stringStateTracker("state", listOf(TIME_TOTALS))

        val now = System.currentTimeMillis()

        stateTracker.state("true", timestamp = now)

        assertThat(dao.collectHeartbeat(endTimestampMs = now + 1.hoursMs)).all {
            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
                "state_true.total_secs" to JsonPrimitive(1.hours.inWholeSeconds),
            )
        }

        stateTracker.state("false", timestamp = now + 4.hoursMs)

        assertThat(dao.collectHeartbeat(endTimestampMs = now + 8.hoursMs)).all {
            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
                "state_true.total_secs" to JsonPrimitive(3.hours.inWholeSeconds),
                "state_false.total_secs" to JsonPrimitive(4.hours.inWholeSeconds),

                // true: 4-0=4
                "state_true.mean_time_in_state_ms" to JsonPrimitive(4.hours.inWholeMilliseconds),
            )
        }

        stateTracker.state("true", timestamp = now + 10.hoursMs)
        stateTracker.state("false", timestamp = now + 12.hoursMs)
        stateTracker.state("true", timestamp = now + 14.hoursMs)
        stateTracker.state("false", timestamp = now + 16.hoursMs)

        assertThat(dao.collectHeartbeat(endTimestampMs = now + 16.hoursMs)).all {
            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
                "state_true.total_secs" to JsonPrimitive(4.hours.inWholeSeconds),
                "state_false.total_secs" to JsonPrimitive(4.hours.inWholeSeconds),

                // true: 12-10=2, 16-14=2, avg=2
                "state_true.mean_time_in_state_ms" to JsonPrimitive(2.hours.inWholeMilliseconds),
                // false: 10-4=6, 14-12=2, avg=4
                "state_false.mean_time_in_state_ms" to JsonPrimitive(4.hours.inWholeMilliseconds),
            )
        }
    }

    @Test
    fun `multiple heartbeats repeating state keeps oldest shift`() = runTest {
        val stateTracker = Reporting.report().stringStateTracker("state", listOf(TIME_TOTALS))

        val now = System.currentTimeMillis()

        stateTracker.state("1", timestamp = now)

        assertThat(dao.collectHeartbeat(endTimestampMs = now + 2.hoursMs)).all {
            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
                "state_1.total_secs" to JsonPrimitive(2.hours.inWholeSeconds),
            )
        }

        stateTracker.state("1", timestamp = now + 2.hoursMs)
        stateTracker.state("1", timestamp = now + 4.hoursMs)

        assertThat(dao.collectHeartbeat(endTimestampMs = now + 4.hoursMs)).all {
            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
                "state_1.total_secs" to JsonPrimitive(2.hours.inWholeSeconds),
            )
        }

        stateTracker.state("1", timestamp = now + 6.hoursMs)
        stateTracker.state("1", timestamp = now + 8.hoursMs)

        assertThat(dao.collectHeartbeat(endTimestampMs = now + 8.hoursMs)).all {
            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
                "state_1.total_secs" to JsonPrimitive(4.hours.inWholeSeconds),
            )
        }

        stateTracker.state("2", timestamp = now + 10.hoursMs)
        stateTracker.state("2", timestamp = now + 12.hoursMs)

        assertThat(dao.collectHeartbeat(endTimestampMs = now + 12.hoursMs)).all {
            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
                "state_1.total_secs" to JsonPrimitive(2.hours.inWholeSeconds),
                "state_2.total_secs" to JsonPrimitive(2.hours.inWholeSeconds),

                "state_1.mean_time_in_state_ms" to JsonPrimitive(10.hours.inWholeMilliseconds),
            )
        }

        stateTracker.state("1", timestamp = now + 14.hoursMs)
        stateTracker.state("2", timestamp = now + 16.hoursMs)

        assertThat(dao.collectHeartbeat(endTimestampMs = now + 16.hoursMs)).all {
            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
                "state_1.total_secs" to JsonPrimitive(2.hours.inWholeSeconds),
                "state_2.total_secs" to JsonPrimitive(2.hours.inWholeSeconds),

                "state_1.mean_time_in_state_ms" to JsonPrimitive(2.hours.inWholeMilliseconds),
                "state_2.mean_time_in_state_ms" to JsonPrimitive(4.hours.inWholeMilliseconds),
            )
        }
    }

    @Test
    fun `test daily is the same`(@TestParameter dailyHeartbeatEnabled: Boolean) = runTest {
        metricsDbTestEnvironment.dailyHeartbeatEnabledValue = dailyHeartbeatEnabled

        val now = System.currentTimeMillis()

        val stateTracker = Reporting.report().stringStateTracker("state", aggregations = listOf(TIME_TOTALS))

        stateTracker.state("ON", timestamp = now + 2.hoursMs)
        stateTracker.state("OFF", timestamp = now + 2.hoursMs)
        stateTracker.state("ON", timestamp = now + 2.hoursMs)
        stateTracker.state("ON", timestamp = now + 2.hoursMs)
        stateTracker.state("ON", timestamp = now + 3.hoursMs)
        stateTracker.state("ON", timestamp = now + 4.hoursMs)
        stateTracker.state("OFF", timestamp = now + 4.hoursMs)
        stateTracker.state("OFF", timestamp = now + 6.hoursMs)
        stateTracker.state("NONE", timestamp = now + 8.hoursMs)
        stateTracker.state("ON", timestamp = now + 8.hoursMs)
        stateTracker.state("ON", timestamp = now + 10.hoursMs)
        stateTracker.state("ON", timestamp = now + 12.hoursMs)

        assertThat(dao.collectHeartbeat(endTimestampMs = now + 26.hoursMs)).all {
            val metrics = arrayOf(
                "state_ON.total_secs" to JsonPrimitive(20.hours.inWholeSeconds),
                "state_OFF.total_secs" to JsonPrimitive(4.hours.inWholeSeconds),
                "state_NONE.total_secs" to JsonPrimitive(0),
                "state_ON.mean_time_in_state_ms" to JsonPrimitive(2.hoursMs),
                "state_OFF.mean_time_in_state_ms" to JsonPrimitive(4.hoursMs),
            )

            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::metrics)
                .containsOnly(*metrics)

            if (dailyHeartbeatEnabled) {
                prop(CustomReport::dailyHeartbeatReport).isNotNull().prop(MetricReport::metrics)
                    .containsOnly(*metrics)
            } else {
                prop(CustomReport::dailyHeartbeatReport).isNull()
            }
        }
    }
}
