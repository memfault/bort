package com.memfault.bort.metrics

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.memfault.bort.DumpsterClient
import com.memfault.bort.PackageManagerClient
import com.memfault.bort.settings.MetricsSettings
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CpuMetricsCollectorTest {
    private val dumpsterClient: DumpsterClient = mockk {
        coEvery { getProcStat() } answers { "" }
        coEvery { getProcPidStat() } answers { null }
    }
    private var prevUsage: CpuUsage = CpuUsage.EMPTY
    private val cpuUsageStorage = object : CpuUsageStorage {
        override var state: CpuUsage
            get() = prevUsage
            set(value) {
                prevUsage = value
            }
    }
    private var usage: CpuUsage? = null
    private val cpuMetricsParser: CpuMetricsParser = mockk {
        every { parseCpuUsage("", any(), any()) } answers { usage }
    }
    private val reporter: CpuMetricReporter = mockk(relaxed = true)
    private val packageManagerClient = mockk<PackageManagerClient>(relaxed = true)
    private val metricsSettings: MetricsSettings = mockk(relaxed = true) {
        every { cpuProcessLimitTopN } returns 3
        every { cpuInterestingProcesses } returns setOf("com.android.veryinteresting")
        every { cpuProcessReportingThreshold } returns 10
    }
    private val collector =
        CpuMetricsCollector(
            dumpsterClient,
            cpuUsageStorage,
            cpuMetricsParser,
            reporter,
            metricsSettings,
            packageManagerClient,
        )

    @Test
    fun noPrevious() = runTest {
        prevUsage = CpuUsage.EMPTY
        usage = CpuUsage(
            ticksUser = 1,
            ticksNice = 3,
            ticksSystem = 3,
            ticksIdle = 4,
            ticksIoWait = 1,
            ticksIrq = 2,
            ticksSoftIrq = 2,
            bootId = "",
        )
        collector.collect()
        assertThat(prevUsage).isEqualTo(usage)
        verify { reporter.reportUsage(75.0) }
        confirmVerified(reporter)
    }

    @Test
    fun differentBootIdHasNoPrevious() = runTest {
        prevUsage = CpuUsage(
            ticksUser = 1,
            ticksNice = 1,
            ticksSystem = 1,
            ticksIdle = 1,
            ticksIoWait = 1,
            ticksIrq = 1,
            ticksSoftIrq = 1,
            bootId = "boot-id-1",
        )
        usage = CpuUsage(
            ticksUser = 1,
            ticksNice = 3,
            ticksSystem = 3,
            ticksIdle = 4,
            ticksIoWait = 1,
            ticksIrq = 2,
            ticksSoftIrq = 2,
            bootId = "boot-id-2",
        )
        collector.collect()
        assertThat(prevUsage).isEqualTo(usage)
        verify { reporter.reportUsage(75.0) }
        confirmVerified(reporter)
    }

    @Test
    fun hasPrevious() = runTest {
        prevUsage = CpuUsage(
            ticksUser = 1,
            ticksNice = 2,
            ticksSystem = 3,
            ticksIdle = 4,
            ticksIoWait = 5,
            ticksIrq = 6,
            ticksSoftIrq = 7,
            bootId = "1",
        )
        usage = CpuUsage(
            ticksUser = 2, // diff = 1
            ticksNice = 2, // diff = 0
            ticksSystem = 4, // diff = 1
            ticksIdle = 19, // diff = 15
            ticksIoWait = 7, // diff = 2
            ticksIrq = 7, // diff = 1
            ticksSoftIrq = 7, // diff = 0
            bootId = "1",
        )
        collector.collect()
        assertThat(prevUsage).isEqualTo(usage)
        verify { reporter.reportUsage(25.0) }
        confirmVerified(reporter)
    }

    @Test
    fun noValue() = runTest {
        prevUsage = CpuUsage(
            ticksUser = 11,
            ticksNice = 12,
            ticksSystem = 13,
            ticksIdle = 14,
            ticksIoWait = 15,
            ticksIrq = 16,
            ticksSoftIrq = 17,
            bootId = "1",
        )
        usage = null
        collector.collect()
        verify { reporter wasNot Called }
    }

    @Test
    fun zeroTotalTicks() = runTest {
        prevUsage = CpuUsage.EMPTY
        usage = CpuUsage(
            ticksUser = 0,
            ticksNice = 0,
            ticksSystem = 0,
            ticksIdle = 0,
            ticksIoWait = 0,
            ticksIrq = 0,
            ticksSoftIrq = 0,
            bootId = "1",
        )
        collector.collect()
        verify { reporter wasNot Called }
    }

    @Test
    fun perProcessUsageCpuThreshold() = runTest {
        prevUsage = CpuUsage(
            ticksUser = 1,
            ticksNice = 2,
            ticksSystem = 3,
            ticksIdle = 4,
            ticksIoWait = 5,
            ticksIrq = 6,
            ticksSoftIrq = 7,
            perProcessUsage = mapOf(
                "com.memfault.bort" to ProcessUsage(
                    processName = "com.memfault.bort",
                    uid = 1004,
                    pid = 1384975,
                    utime = 2,
                    stime = 3,
                ),
                "com.android.systemui_____.with_invalid_chars" to ProcessUsage(
                    processName = "com.android.systemui_____.with_invalid_chars",
                    uid = 1001,
                    pid = 1384979,
                    utime = 4,
                    stime = 5,
                ),
                "MemfaultDumpster" to ProcessUsage(
                    processName = "MemfaultDumpster",
                    uid = 1003,
                    pid = 1384980,
                    utime = 1,
                    stime = 2,
                ),
            ),
            bootId = "1",
        )
        usage = CpuUsage(
            ticksUser = 2, // diff = 1
            ticksNice = 2, // diff = 0
            ticksSystem = 4, // diff = 1
            ticksIdle = 19, // diff = 15
            ticksIoWait = 7, // diff = 2
            ticksIrq = 7, // diff = 1
            ticksSoftIrq = 7, // diff = 0
            perProcessUsage = mapOf(
                "com.memfault.bort" to ProcessUsage(
                    processName = "com.memfault.bort",
                    uid = 1004,
                    pid = 1384975,
                    utime = 8,
                    stime = 2,
                ),
                "com.android.systemui_____.with_invalid_chars" to ProcessUsage(
                    processName = "com.android.systemui_____.with_invalid_chars",
                    uid = 1001,
                    pid = 1384979,
                    utime = 5,
                    stime = 6,
                ),
                "MemfaultDumpster" to ProcessUsage(
                    processName = "MemfaultDumpster",
                    uid = 1003,
                    pid = 1384980,
                    utime = 2,
                    stime = 2,
                ),
            ),
            bootId = "1",
        )
        collector.collect()
        assertThat(prevUsage).isEqualTo(usage)

        verify { reporter.reportUsage(25.0) }
        verify { reporter.reportProcessUsage("com.memfault.bort", 25.0, createMetric = false) }
        verify {
            reporter.reportProcessUsage("com.android.systemui_____.with_invalid_chars", 10.0, createMetric = false)
        }
        // MemfaultDumpster got 5% usage, which is below the cpu usage threshold

        confirmVerified(reporter)
    }

    @Test
    fun perProcessUsageTopN() = runTest {
        val prevPerProcess = (1..10) // 10 processes
            .map { it.toString() to ProcessUsage("com.app.$it", 1000 + it, 10000 + it, 0, 0) }
            .plus(
                "com.android.veryinteresting" to ProcessUsage(
                    processName = "com.android.veryinteresting",
                    uid = 3001,
                    pid = 1384979,
                    utime = 8,
                    stime = 5,
                ),
            )
        val currentPerProcess = (1..10) // 10 processes
            .map { it.toString() to ProcessUsage("com.app.$it", 1000 + it, 10000 + it, 10 - it.toLong(), 5) }
            .plus(
                "com.android.veryinteresting" to ProcessUsage(
                    processName = "com.android.veryinteresting",
                    uid = 3001,
                    pid = 1384979,
                    utime = 9,
                    stime = 5,
                ),
            )

        prevUsage = CpuUsage(
            ticksUser = 1,
            ticksNice = 2,
            ticksSystem = 3,
            ticksIdle = 4,
            ticksIoWait = 5,
            ticksIrq = 6,
            ticksSoftIrq = 7,
            perProcessUsage = prevPerProcess.toMap(),
            bootId = "1",
        )
        usage = CpuUsage(
            ticksUser = 2, // diff = 1
            ticksNice = 2, // diff = 0
            ticksSystem = 4, // diff = 1
            ticksIdle = 19, // diff = 15
            ticksIoWait = 7, // diff = 2
            ticksIrq = 7, // diff = 1
            ticksSoftIrq = 7, // diff = 0
            perProcessUsage = currentPerProcess.toMap(),
            bootId = "1",
        )
        collector.collect()
        assertThat(prevUsage).isEqualTo(usage)

        verify { reporter.reportUsage(25.0) }

        // We are limiting to the top 3 processes, sorted by cpu usage, but we should still get interesting processes
        verify { reporter.reportProcessUsage("1", 70.0, createMetric = false) }
        verify { reporter.reportProcessUsage("2", 65.0, createMetric = false) }
        verify { reporter.reportProcessUsage("3", 60.0, createMetric = false) }
        verify { reporter.reportProcessUsage("com.android.veryinteresting", 5.0, createMetric = true) }

        confirmVerified(reporter)
    }
}
