package com.memfault.bort.metrics

import com.memfault.bort.parsers.BatteryStatsHistoryMetricLogger
import com.memfault.bort.settings.BatteryStatsSettings
import com.memfault.bort.test.util.TestTemporaryFileFactory
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.slot
import java.io.File
import java.io.OutputStream
import java.lang.Exception
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

data class FakeNextBatteryStatsHistoryStartProvider(
    override var historyStart: Long
) : NextBatteryStatsHistoryStartProvider

class BatteryStatsHistoryCollectorTest {
    lateinit var collector: BatteryStatsHistoryCollector
    lateinit var nextBatteryStatsHistoryStartProvider: NextBatteryStatsHistoryStartProvider
    lateinit var mockRunBatteryStats: RunBatteryStats
    lateinit var batteryStatsOutputByHistoryStart: Map<Long, String>
    var tempFile: File? = null
    private val batteryStatsHistoryMetricLogger: BatteryStatsHistoryMetricLogger = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        nextBatteryStatsHistoryStartProvider = FakeNextBatteryStatsHistoryStartProvider(0)
        val outputStreamSlot = slot<OutputStream>()
        val historyStartSlot = slot<Long>()
        mockRunBatteryStats = mockk()
        coEvery {
            mockRunBatteryStats.runBatteryStats(capture(outputStreamSlot), capture(historyStartSlot), any())
        } coAnswers {
            batteryStatsOutputByHistoryStart[historyStartSlot.captured].let { output ->
                checkNotNull(output)
                output.byteInputStream().use {
                    it.copyTo(outputStreamSlot.captured)
                }
            }
        }
        val settings = object : BatteryStatsSettings {
            override val dataSourceEnabled = true
            override val commandTimeout = 1.minutes
            override val useHighResTelemetry: Boolean = false
        }
        collector = BatteryStatsHistoryCollector(
            TestTemporaryFileFactory,
            nextBatteryStatsHistoryStartProvider,
            mockRunBatteryStats,
            settings,
            batteryStatsHistoryMetricLogger,
        )
    }

    @AfterEach
    fun tearDown() {
        tempFile?.delete()
    }

    @Test
    fun simpleCase() {
        nextBatteryStatsHistoryStartProvider.historyStart = 1000
        batteryStatsOutputByHistoryStart = mapOf(
            1000L to """
                9,h,0:TIME:123456
                NEXT: 1100
            """.trimIndent(),
        )
        runBlocking {
            tempFile = collector.collect(limit = 100.seconds).batteryStatsFileToUpload
        }
        coVerifyOrder {
            mockRunBatteryStats.runBatteryStats(any(), 1000, 1.minutes)
        }
        assertEquals(1100, nextBatteryStatsHistoryStartProvider.historyStart)
        assertEquals(batteryStatsOutputByHistoryStart[1000], tempFile?.readText())
    }

    @Test
    fun historyStartInFuture() {
        /**
         * Tests that the collector detects when the historyStart is in the future according to the batterystats
         * output (TIME missing), resets the historyStart to 0 and re-runs batterystats:
         */
        nextBatteryStatsHistoryStartProvider.historyStart = 1000
        batteryStatsOutputByHistoryStart = mapOf(
            1000L to "NEXT: 500",
            0L to """
                9,h,0:TIME:123456
                NEXT: 505
            """.trimIndent()
        )
        runBlocking {
            tempFile = collector.collect(limit = 1.hours).batteryStatsFileToUpload
        }
        coVerifyOrder {
            mockRunBatteryStats.runBatteryStats(any(), 1000, 1.minutes)
            mockRunBatteryStats.runBatteryStats(any(), 0, 1.minutes)
        }
        assertEquals(505, nextBatteryStatsHistoryStartProvider.historyStart)
        assertEquals(batteryStatsOutputByHistoryStart[0], tempFile?.readText())
    }

    @Test
    fun historyStartExceedsLimit() {
        /**
         * Tests that the collector detects when the historyStart is so "early" that the output limit is exceeded,
         * adjusts historyStart to (NEXT - limit) and re-runs batterystats:
         */
        nextBatteryStatsHistoryStartProvider.historyStart = 1000
        batteryStatsOutputByHistoryStart = mapOf(
            1000L to """
                9,h,0:TIME:123456
                NEXT: 200000
            """.trimIndent(),
            100000L to """
                9,h,0:TIME:123456
                NEXT: 100005
            """.trimIndent()
        )
        runBlocking {
            tempFile = collector.collect(limit = 100.seconds).batteryStatsFileToUpload
        }
        coVerifyOrder {
            mockRunBatteryStats.runBatteryStats(any(), 1000, 1.minutes)
            mockRunBatteryStats.runBatteryStats(any(), 100000, 1.minutes)
        }
        assertEquals(100005, nextBatteryStatsHistoryStartProvider.historyStart)
        assertEquals(batteryStatsOutputByHistoryStart[100000], tempFile?.readText())
    }

    @Test
    fun historyStartInFutureThenExceedsLimit() {
        /**
         * Tests that the collector detects when the historyStart is so "early" that the output limit is exceeded,
         * adjusts historyStart to (NEXT - limit) and re-runs batterystats:
         */
        nextBatteryStatsHistoryStartProvider.historyStart = 1000
        batteryStatsOutputByHistoryStart = mapOf(
            1000L to "NEXT: 0",
            0L to """
                9,h,0:TIME:123456
                NEXT: 200000
            """.trimIndent(),
            100000L to """
                9,h,0:TIME:123456
                NEXT: 100005
            """.trimIndent()
        )
        runBlocking {
            tempFile = collector.collect(limit = 100.seconds).batteryStatsFileToUpload
        }
        coVerifyOrder {
            mockRunBatteryStats.runBatteryStats(any(), 1000, 1.minutes)
            mockRunBatteryStats.runBatteryStats(any(), 0, 1.minutes)
            mockRunBatteryStats.runBatteryStats(any(), 100000, 1.minutes)
        }
        assertEquals(100005, nextBatteryStatsHistoryStartProvider.historyStart)
        assertEquals(batteryStatsOutputByHistoryStart[100000], tempFile?.readText())
    }

    @Test
    fun throwsWhenTooManyAttempts() {
        /**
         * Tests that the collector throws in case it felt like it needed to run batterystats more than twice.
         * This should never happen in practice, but we want to avoid getting into an infinite loop in case
         * batterystats' output is funny.
         */
        nextBatteryStatsHistoryStartProvider.historyStart = 1000
        batteryStatsOutputByHistoryStart = mapOf(
            1000L to """
                NEXT: 1000
            """.trimIndent(),
        )
        assertThrows<Exception> {
            runBlocking {
                tempFile = collector.collect(limit = 100.seconds).batteryStatsFileToUpload
            }
        }
    }
}
