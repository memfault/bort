package com.memfault.bort.metrics

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.memfault.bort.DumpsterClient
import com.memfault.bort.DumpsterServiceProvider
import com.memfault.bort.FakeCombinedTimeProvider
import com.memfault.bort.process.ProcessExecutor
import com.memfault.dumpster.IDumpster
import com.memfault.dumpster.IDumpsterBasicCommandListener
import io.mockk.coEvery
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

class StorageStatsCollectorTest {
    private val coroutineContext = StandardTestDispatcher()

    private val service = mockk<IDumpster> {
        every { version } returns IDumpster.VERSION_STORAGE_WEAR
        every { runBasicCommand(IDumpster.CMD_ID_STORAGE_WEAR, any()) } answers {
            (it.invocation.args[1] as IDumpsterBasicCommandListener).onFinished(0, "1 2 3 source version")
        }
    }
    private val processExecutor = mockk<ProcessExecutor> {
        coEvery<String?> { execute(any(), any()) } answers { null }
    }
    private val dumpsterClient = DumpsterClient(
        serviceProvider = object : DumpsterServiceProvider {
            override fun get(logIfMissing: Boolean): IDumpster = service
        },
        basicCommandTimeout = 5000,
        processExecutor = processExecutor,
    )

    private var prevActivity = DiskActivity.EMPTY
    private val diskActivityStorage = object : DiskActivityStorage {
        override var state: DiskActivity
            get() = prevActivity
            set(value) {
                prevActivity = value
            }
    }

    private val diskSpaceProvider = object : DiskSpaceProvider {
        override fun getFreeBytes(): Long = 1024
        override fun getTotalBytes(): Long = 4096
    }

    private val storageStatsReporter = mockk<StorageStatsReporter>(relaxed = true)

    private var nextDiskActivity = DiskActivity.EMPTY
    private val storageStatsCollector = StorageStatsCollector(
        ioCoroutineContext = coroutineContext,
        dumpsterClient = dumpsterClient,
        diskSpaceProvider = diskSpaceProvider,
        diskActivityProvider = object : DiskActivityProvider {
            override fun getDiskActivity(): DiskActivity = nextDiskActivity
        },
        diskActivityStorage = diskActivityStorage,
        storageStatsReporter = storageStatsReporter,
    )
    private val sectorSize = 512L

    @Test fun `stats gets reported correctly`() = runTest(coroutineContext) {
        val defaultDiskStat = DiskStat(
            major = 0,
            minor = 0,
            deviceName = "dummy",
            readsCompleted = 0L,
            readsMerged = 0L,
            sectorsRead = 0L,
            timeSpentReading = 0L,
            writesCompleted = 0L,
            writesMerged = 0L,
            sectorsWritten = 0L,
            timeSpentWriting = 0L,
            ioInProgress = 0L,
            timeSpentDoingIO = 0L,
            weightedTimeSpentDoingIO = 0L,
            discardsCompletedSuccessfully = 0L,
            discardsMerged = 0L,
            sectorsDiscarded = 0L,
            timeSpentDiscarding = 0L,
            flushRequestsCompletedSuccessfully = 0L,
            timeSpentFlushing = 0L,
        )

        diskActivityStorage.state = DiskActivity(
            bootId = "boot123",
            stats = listOf(
                defaultDiskStat.copy(deviceName = "device1", sectorsWritten = 2048),
                defaultDiskStat.copy(deviceName = "device2", sectorsWritten = 1024),
                defaultDiskStat.copy(deviceName = "device4", sectorsWritten = 1024),
            ),
            sectorSize = sectorSize,
        )
        nextDiskActivity = DiskActivity(
            bootId = "boot123",
            stats = listOf(
                defaultDiskStat.copy(deviceName = "device1", sectorsWritten = 4096),
                defaultDiskStat.copy(deviceName = "device2", sectorsWritten = 2048),
                defaultDiskStat.copy(deviceName = "device3", sectorsWritten = 512),
            ),
            sectorSize = sectorSize,
        )

        storageStatsCollector.collectStorageStats(FakeCombinedTimeProvider.now())

        val now = FakeCombinedTimeProvider.now.timestamp.toEpochMilli()
        verify { storageStatsReporter.reportUsage(1024, 4096, 3072, 0.75, now) }
        verify { storageStatsReporter.reportFlashWear("source", "version", 1, 2, 3, now) }
        verify { storageStatsReporter.reportWrites("device1", 2048 * sectorSize, now) }
        verify { storageStatsReporter.reportWrites("device2", 1024 * sectorSize, now) }
        verify { storageStatsReporter.reportWrites("device3", 512 * sectorSize, now) }
        confirmVerified(storageStatsReporter)
    }

    @Test fun `lifetime percentage is calculated correctly`() {
        assertThat(lifetimeAsRemainingPct(0)).isEqualTo(null)
        assertThat(lifetimeAsRemainingPct(0x1)).isEqualTo(100)
        assertThat(lifetimeAsRemainingPct(0x2)).isEqualTo(90)
        assertThat(lifetimeAsRemainingPct(0x3)).isEqualTo(80)
        assertThat(lifetimeAsRemainingPct(0x4)).isEqualTo(70)
        assertThat(lifetimeAsRemainingPct(0x5)).isEqualTo(60)
        assertThat(lifetimeAsRemainingPct(0x6)).isEqualTo(50)
        assertThat(lifetimeAsRemainingPct(0x7)).isEqualTo(40)
        assertThat(lifetimeAsRemainingPct(0x8)).isEqualTo(30)
        assertThat(lifetimeAsRemainingPct(0x9)).isEqualTo(20)
        assertThat(lifetimeAsRemainingPct(0xA)).isEqualTo(10)
        assertThat(lifetimeAsRemainingPct(0xB)).isEqualTo(0)
        assertThat(lifetimeAsRemainingPct(0xC)).isEqualTo(null)
    }
}
