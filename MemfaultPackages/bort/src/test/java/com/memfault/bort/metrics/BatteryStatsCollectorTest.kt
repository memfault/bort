package com.memfault.bort.metrics

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.memfault.bort.android.FakeDeviceFeatures
import com.memfault.bort.settings.BatteryStatsSettings
import com.memfault.bort.time.CombinedTime
import com.memfault.bort.time.LinuxBootRelativeTime
import com.memfault.bort.time.boxed
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class BatteryStatsCollectorTest {
    private val batteryStatsHistoryCollector: BatteryStatsHistoryCollector = mockk()
    private val batterystatsSummaryCollector: BatterystatsSummaryCollector = mockk()
    private val metrics: BuiltinMetricsStore = mockk(relaxed = true)
    private val settings = object : BatteryStatsSettings {
        override val dataSourceEnabled = true
        override val commandTimeout = 1.minutes
        override val useHighResTelemetry: Boolean = false
        override val collectSummary: Boolean = false
        override val componentMetrics: List<String> = emptyList()
    }

    private fun time(timeMs: Long) = CombinedTime(
        uptime = timeMs.milliseconds.boxed(),
        elapsedRealtime = timeMs.milliseconds.boxed(),
        linuxBootId = "bootid",
        bootCount = 1,
        timestamp = Instant.ofEpochMilli(timeMs),
    )

    @Test
    fun noBatteryReturnsEmptyWithoutCollectingHistory() = runTest {
        val collector = BatteryStatsCollector(
            batteryStatsHistoryCollector = batteryStatsHistoryCollector,
            batterystatsSummaryCollector = batterystatsSummaryCollector,
            settings = settings,
            metrics = metrics,
            deviceFeatures = FakeDeviceFeatures(hasBattery = false),
        )

        val result = collector.collect(
            collectionTime = time(2000),
            lastHeartbeatUptime = LinuxBootRelativeTime(
                uptime = 1000.milliseconds.boxed(),
                elapsedRealtime = 1000.milliseconds.boxed(),
                linuxBootId = "bootid",
            ),
        )

        assertThat(result).isEqualTo(BatteryStatsResult.EMPTY)
        coVerify(exactly = 0) { batteryStatsHistoryCollector.collect(any(), any()) }
    }

    @Test
    fun hasBatteryCollectsHistory() = runTest {
        coEvery { batteryStatsHistoryCollector.collect(any(), any()) } returns BatteryStatsResult.EMPTY
        val collector = BatteryStatsCollector(
            batteryStatsHistoryCollector = batteryStatsHistoryCollector,
            batterystatsSummaryCollector = batterystatsSummaryCollector,
            settings = settings,
            metrics = metrics,
            deviceFeatures = FakeDeviceFeatures(hasBattery = true),
        )

        collector.collect(
            collectionTime = time(2000),
            lastHeartbeatUptime = LinuxBootRelativeTime(
                uptime = 1000.milliseconds.boxed(),
                elapsedRealtime = 1000.milliseconds.boxed(),
                linuxBootId = "bootid",
            ),
        )

        coVerify(exactly = 1) { batteryStatsHistoryCollector.collect(any(), any()) }
    }
}
