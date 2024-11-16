package com.memfault.bort.metrics

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.memfault.bort.DumpsterClient
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
        every { parseProcStat("") } answers { usage }
    }
    private val reporter: CpuMetricReporter = mockk(relaxed = true)
    private val collector = CpuMetricsCollector(dumpsterClient, cpuUsageStorage, cpuMetricsParser, reporter)

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
        )
        usage = CpuUsage(
            ticksUser = 2, // diff = 1
            ticksNice = 2, // diff = 0
            ticksSystem = 4, // diff = 1
            ticksIdle = 19, // diff = 15
            ticksIoWait = 7, // diff = 2
            ticksIrq = 7, // diff = 1
            ticksSoftIrq = 7, // diff = 0
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
        )
        collector.collect()
        verify { reporter wasNot Called }
    }
}
