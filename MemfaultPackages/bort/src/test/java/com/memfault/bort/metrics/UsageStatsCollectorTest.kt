package com.memfault.bort.metrics

import android.app.usage.UsageEvents
import android.app.usage.UsageEvents.Event
import android.app.usage.UsageStatsManager
import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import assertk.assertions.isNotNull
import assertk.assertions.prop
import com.memfault.bort.metrics.HighResTelemetry.DataType.StringType
import com.memfault.bort.metrics.HighResTelemetry.Datum
import com.memfault.bort.metrics.HighResTelemetry.MetricType
import com.memfault.bort.metrics.HighResTelemetry.Rollup
import com.memfault.bort.metrics.HighResTelemetry.RollupMetadata
import com.memfault.bort.metrics.UsageEvent.DEVICE_SHUTDOWN
import com.memfault.bort.metrics.UsageEvent.DEVICE_STARTUP
import com.memfault.bort.metrics.UsageEvent.FOREGROUND_SERVICE_START
import com.memfault.bort.metrics.custom.CustomMetrics
import com.memfault.bort.metrics.custom.CustomReport
import com.memfault.bort.metrics.custom.MetricReport
import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.time.toAbsoluteTime
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.LinkedList
import java.util.Queue

@RunWith(RobolectricTestRunner::class)
class UsageStatsCollectorTest {
    @get:Rule()
    val metricsDbTestEnvironment = MetricsDbTestEnvironment().apply {
        highResMetricsEnabledValue = true
    }
    private val metrics: CustomMetrics get() = metricsDbTestEnvironment.dao
    private val events: Queue<TestEvent> = LinkedList()
    private val usageEvents: UsageEvents = mockk {
        every { getNextEvent(any()) } answers {
            val eventArg = arg<Event>(0)
            val nextEvent = events.poll()
            if (nextEvent != null) {
                eventArg.setHiddenProp("mEventType", nextEvent.type.code)
                eventArg.setHiddenProp("mTimeStamp", nextEvent.timestamp.timestamp.toEpochMilli())
                eventArg.setHiddenProp("mPackage", nextEvent.pkg)
                true
            } else {
                false
            }
        }
    }
    private val usageStatsManager: UsageStatsManager = mockk {
        every { queryEvents(any(), any()) } answers { usageEvents }
    }
    private val collector = UsageStatsCollector(usageStatsManager)

    private fun <T> Any.setHiddenProp(name: String, value: T) {
        javaClass.getDeclaredField(name).set(this, value)
    }

    data class TestEvent(
        val type: UsageEvent,
        val timestamp: AbsoluteTime,
        val pkg: String,
    )

    @Test
    fun collectShutdownAndStartupEvents() = runTest {
        events.add(TestEvent(DEVICE_SHUTDOWN, 1L.toAbsoluteTime(), "android.instant_app"))
        events.add(TestEvent(DEVICE_SHUTDOWN, 2L.toAbsoluteTime(), "android"))
        events.add(TestEvent(DEVICE_STARTUP, 3L.toAbsoluteTime(), "android"))
        events.add(TestEvent(FOREGROUND_SERVICE_START, 4L.toAbsoluteTime(), "android"))
        collector.collectUsageStats(from = 1L.toAbsoluteTime(), to = 4L.toAbsoluteTime())
        assertThat(metrics.collectHeartbeat(endTimestampMs = 4)).all {
            prop(CustomReport::hourlyHeartbeatReport).all {
                prop(MetricReport::metrics).isEmpty()

                prop(MetricReport::hrt).isNotNull()
                    .transform { hrt -> HighResTelemetry.decodeFromStream(hrt) }
                    .prop(HighResTelemetry::rollups)
                    .containsExactlyInAnyOrder(
                        *metricsDbTestEnvironment.dropBoxTagCountRollups(4).toTypedArray(),

                        Rollup(
                            metadata = RollupMetadata(
                                stringKey = "device-powered",
                                metricType = MetricType.Event,
                                dataType = StringType,
                                internal = false,
                            ),
                            data = listOf(
                                Datum(t = 2, value = JsonPrimitive("shutdown")),
                                Datum(t = 3, value = JsonPrimitive("booted")),
                            ),
                        ),
                    )
            }
        }
    }

    @Test
    fun noStartTime() = runTest {
        events.add(TestEvent(DEVICE_SHUTDOWN, 1L.toAbsoluteTime(), "android.instant_app"))
        events.add(TestEvent(DEVICE_SHUTDOWN, 2L.toAbsoluteTime(), "android"))
        events.add(TestEvent(DEVICE_STARTUP, 3L.toAbsoluteTime(), "android"))
        events.add(TestEvent(FOREGROUND_SERVICE_START, 4L.toAbsoluteTime(), "android"))
        collector.collectUsageStats(null, to = 4L.toAbsoluteTime())
        assertThat(metrics.collectHeartbeat(endTimestampMs = 4)).all {
            prop(CustomReport::hourlyHeartbeatReport).all {
                prop(MetricReport::metrics).isEmpty()

                prop(MetricReport::hrt).isNotNull()
                    .transform { hrt -> HighResTelemetry.decodeFromStream(hrt) }
                    .prop(HighResTelemetry::rollups)
                    .containsExactlyInAnyOrder(
                        *metricsDbTestEnvironment.dropBoxTagCountRollups(4).toTypedArray(),

                        Rollup(
                            metadata = RollupMetadata(
                                stringKey = "device-powered",
                                metricType = MetricType.Event,
                                dataType = StringType,
                                internal = false,
                            ),
                            data = listOf(
                                Datum(t = 2, value = JsonPrimitive("shutdown")),
                                Datum(t = 3, value = JsonPrimitive("booted")),
                            ),
                        ),
                    )
            }
        }
    }
}
