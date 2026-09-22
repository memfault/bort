package com.memfault.bort.metrics

import android.os.Process
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.memfault.bort.DumpsterClient
import com.memfault.bort.DumpsterServiceProvider
import com.memfault.bort.FakeCombinedTimeProvider
import com.memfault.bort.PackageManagerClient
import com.memfault.bort.parsers.Package
import com.memfault.bort.parsers.PackageManagerReport
import com.memfault.bort.process.ProcessExecutor
import com.memfault.bort.shared.APPLICATION_ID_MEMFAULT_USAGE_REPORTER
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

    private var prevUidIoStats = UidIoStats.EMPTY
    private val uidIoStatsStorage = object : UidIoStatsStorage {
        override var state: UidIoStats
            get() = prevUidIoStats
            set(value) {
                prevUidIoStats = value
            }
    }

    private val diskSpaceProvider = object : DiskSpaceProvider {
        override fun getFreeBytes(): Long = 1024
        override fun getTotalBytes(): Long = 4096
    }

    private val storageStatsReporter = mockk<StorageStatsReporter>(relaxed = true)

    private var packages = emptyList<Package>()
    private val packageManagerClient = mockk<PackageManagerClient> {
        coEvery { getPackageManagerReport() } answers { PackageManagerReport(packages) }
    }

    private var significantApps = emptyList<SignificantApp>()
    private val significantAppsProvider = object : SignificantAppsProvider {
        override fun internalApps(): List<SignificantApp> = significantApps.filter { it.internal }
        override fun externalApps(): List<SignificantApp> = significantApps.filter { !it.internal }
    }

    private var nextDiskActivity = DiskActivity.EMPTY
    private var nextUidIoStats = UidIoStats.EMPTY
    private val storageStatsCollector = StorageStatsCollector(
        ioCoroutineContext = coroutineContext,
        dumpsterClient = dumpsterClient,
        diskSpaceProvider = diskSpaceProvider,
        diskActivityProvider = object : DiskActivityProvider {
            override fun getDiskActivity(): DiskActivity = nextDiskActivity
        },
        diskActivityStorage = diskActivityStorage,
        uidIoStatsProvider = object : UidIoStatsProvider {
            override fun getUidIoStats(): UidIoStats = nextUidIoStats
        },
        uidIoStatsStorage = uidIoStatsStorage,
        storageStatsReporter = storageStatsReporter,
        significantAppsProvider = significantAppsProvider,
        packageManagerClient = packageManagerClient,
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
        uidIoStatsStorage.state = UidIoStats(bootId = "boot123", writtenBytes = 1000)
        nextUidIoStats = UidIoStats(bootId = "boot123", writtenBytes = 4096)

        storageStatsCollector.collectStorageStats(FakeCombinedTimeProvider.now())

        val combined = FakeCombinedTimeProvider.now
        val now = combined.timestamp.toEpochMilli()
        val elapsed = combined.elapsedRealtime.duration.inWholeMilliseconds
        verify { storageStatsReporter.reportUsage(1024, 4096, 3072, 0.75, now, uptime = elapsed) }
        verify { storageStatsReporter.reportFlashWear("source", "version", 1, 2, 3, now, uptime = elapsed) }
        verify { storageStatsReporter.reportWrites("device1", 2048 * sectorSize, now, uptime = elapsed) }
        verify { storageStatsReporter.reportWrites("device2", 1024 * sectorSize, now, uptime = elapsed) }
        verify { storageStatsReporter.reportWrites("device3", 512 * sectorSize, now, uptime = elapsed) }
        verify { storageStatsReporter.reportBortWrites(3096, now, uptime = elapsed) }
        confirmVerified(storageStatsReporter)
    }

    @Test fun `bort write bytes are accumulated as a delta within the same boot`() = runTest(coroutineContext) {
        val combined = FakeCombinedTimeProvider.now
        val now = combined.timestamp.toEpochMilli()
        val elapsed = combined.elapsedRealtime.duration.inWholeMilliseconds

        uidIoStatsStorage.state = UidIoStats(bootId = "boot-A", writtenBytes = 10_000)
        nextUidIoStats = UidIoStats(bootId = "boot-A", writtenBytes = 15_500)

        storageStatsCollector.collectStorageStats(FakeCombinedTimeProvider.now())

        verify { storageStatsReporter.reportBortWrites(5_500, now, uptime = elapsed) }
    }

    @Test fun `bort write bytes use full current value after a reboot`() = runTest(coroutineContext) {
        val combined = FakeCombinedTimeProvider.now
        val now = combined.timestamp.toEpochMilli()
        val elapsed = combined.elapsedRealtime.duration.inWholeMilliseconds

        uidIoStatsStorage.state = UidIoStats(bootId = "boot-old", writtenBytes = 999_000)
        nextUidIoStats = UidIoStats(bootId = "boot-new", writtenBytes = 2_048)

        storageStatsCollector.collectStorageStats(FakeCombinedTimeProvider.now())

        verify { storageStatsReporter.reportBortWrites(2_048, now, uptime = elapsed) }
    }

    @Test fun `bort write bytes clamp to zero when current is less than previous`() = runTest(coroutineContext) {
        val combined = FakeCombinedTimeProvider.now
        val now = combined.timestamp.toEpochMilli()
        val elapsed = combined.elapsedRealtime.duration.inWholeMilliseconds

        uidIoStatsStorage.state = UidIoStats(bootId = "boot-A", writtenBytes = 5_000)
        nextUidIoStats = UidIoStats(bootId = "boot-A", writtenBytes = 4_000)

        storageStatsCollector.collectStorageStats(FakeCombinedTimeProvider.now())

        verify { storageStatsReporter.reportBortWrites(0, now, uptime = elapsed) }
    }

    @Test fun `bort write bytes are not reported and storage is not updated when EMPTY is returned`() = runTest(
        coroutineContext,
    ) {
        uidIoStatsStorage.state = UidIoStats(bootId = "boot-A", writtenBytes = 5_000)
        nextUidIoStats = UidIoStats.EMPTY

        storageStatsCollector.collectStorageStats(FakeCombinedTimeProvider.now())

        verify(exactly = 0) { storageStatsReporter.reportBortWrites(any(), any(), any()) }
        assertThat(uidIoStatsStorage.state).isEqualTo(UidIoStats(bootId = "boot-A", writtenBytes = 5_000))
    }

    @Test fun `app writes are reported per significant app as a delta within the same boot`() = runTest(
        coroutineContext,
    ) {
        val combined = FakeCombinedTimeProvider.now
        val now = combined.timestamp.toEpochMilli()
        val elapsed = combined.elapsedRealtime.duration.inWholeMilliseconds

        packages = listOf(Package(id = "com.memfault.smartfridge", userId = 10045))
        significantApps = listOf(
            SignificantApp(
                packageName = "com.memfault.smartfridge",
                identifier = "com.memfault.smartfridge",
                internal = false,
            ),
        )
        uidIoStatsStorage.state = UidIoStats(
            bootId = "boot-A",
            writtenBytes = 0,
            writesByUid = mapOf(10045 to UidWrites(writeBytes = 1_000, logicalWriteBytes = 8_000)),
        )
        nextUidIoStats = UidIoStats(
            bootId = "boot-A",
            writtenBytes = 0,
            writesByUid = mapOf(10045 to UidWrites(writeBytes = 5_000, logicalWriteBytes = 9_500)),
        )

        storageStatsCollector.collectStorageStats(FakeCombinedTimeProvider.now())

        verify {
            storageStatsReporter.reportAppWrites(
                app = significantApps.single(),
                writes = UidWrites(writeBytes = 4_000, logicalWriteBytes = 1_500),
                now = now,
                uptime = elapsed,
            )
        }
    }

    @Test fun `app writes sum the uids that share a package name`() = runTest(coroutineContext) {
        val combined = FakeCombinedTimeProvider.now
        val now = combined.timestamp.toEpochMilli()
        val elapsed = combined.elapsedRealtime.duration.inWholeMilliseconds

        // Both uids resolve to "android", as every system uid without a component name does.
        packages = listOf(Package(id = "com.memfault.smartfridge", userId = 10045))
        significantApps = listOf(
            SignificantApp(packageName = "android", identifier = "android", internal = false),
        )
        uidIoStatsStorage.state = UidIoStats(
            bootId = "boot-A",
            writtenBytes = 0,
            writesByUid = mapOf(
                1234 to UidWrites(writeBytes = 50, logicalWriteBytes = 300),
                5678 to UidWrites(writeBytes = 100, logicalWriteBytes = 400),
            ),
        )
        nextUidIoStats = UidIoStats(
            bootId = "boot-A",
            writtenBytes = 0,
            writesByUid = mapOf(
                1234 to UidWrites(writeBytes = 150, logicalWriteBytes = 1_000),
                5678 to UidWrites(writeBytes = 300, logicalWriteBytes = 1_300),
            ),
        )

        storageStatsCollector.collectStorageStats(FakeCombinedTimeProvider.now())

        verify {
            storageStatsReporter.reportAppWrites(
                app = significantApps.single(),
                writes = UidWrites(writeBytes = 300, logicalWriteBytes = 1_600),
                now = now,
                uptime = elapsed,
            )
        }
    }

    @Test fun `app writes use the full current value after a reboot`() = runTest(coroutineContext) {
        val combined = FakeCombinedTimeProvider.now
        val now = combined.timestamp.toEpochMilli()
        val elapsed = combined.elapsedRealtime.duration.inWholeMilliseconds

        packages = listOf(Package(id = "com.memfault.smartfridge", userId = 10045))
        significantApps = listOf(
            SignificantApp(
                packageName = "com.memfault.smartfridge",
                identifier = "com.memfault.smartfridge",
                internal = false,
            ),
        )
        uidIoStatsStorage.state = UidIoStats(
            bootId = "boot-old",
            writtenBytes = 0,
            writesByUid = mapOf(10045 to UidWrites(writeBytes = 900_000, logicalWriteBytes = 999_000)),
        )
        nextUidIoStats = UidIoStats(
            bootId = "boot-new",
            writtenBytes = 0,
            writesByUid = mapOf(10045 to UidWrites(writeBytes = 2_048, logicalWriteBytes = 4_096)),
        )

        storageStatsCollector.collectStorageStats(FakeCombinedTimeProvider.now())

        verify {
            storageStatsReporter.reportAppWrites(
                app = significantApps.single(),
                writes = UidWrites(writeBytes = 2_048, logicalWriteBytes = 4_096),
                now = now,
                uptime = elapsed,
            )
        }
    }

    @Test fun `app writes are reported as zero for an app that is not installed`() = runTest(coroutineContext) {
        val combined = FakeCombinedTimeProvider.now
        val now = combined.timestamp.toEpochMilli()
        val elapsed = combined.elapsedRealtime.duration.inWholeMilliseconds

        packages = listOf(Package(id = "com.memfault.smartfridge", userId = 10045))
        significantApps = listOf(
            SignificantApp(packageName = "com.memfault.bort", identifier = "bort", internal = true),
        )
        uidIoStatsStorage.state = UidIoStats(
            bootId = "boot-A",
            writtenBytes = 0,
            writesByUid = mapOf(10045 to UidWrites(writeBytes = 1_000, logicalWriteBytes = 2_000)),
        )
        nextUidIoStats = UidIoStats(
            bootId = "boot-A",
            writtenBytes = 0,
            writesByUid = mapOf(10045 to UidWrites(writeBytes = 5_000, logicalWriteBytes = 9_000)),
        )

        storageStatsCollector.collectStorageStats(FakeCombinedTimeProvider.now())

        verify {
            storageStatsReporter.reportAppWrites(
                app = significantApps.single(),
                writes = UidWrites.ZERO,
                now = now,
                uptime = elapsed,
            )
        }
    }

    @Test fun `app writes only establish a baseline when upgrading from state without per uid writes`() = runTest(
        coroutineContext,
    ) {
        val combined = FakeCombinedTimeProvider.now
        val now = combined.timestamp.toEpochMilli()
        val elapsed = combined.elapsedRealtime.duration.inWholeMilliseconds

        packages = listOf(Package(id = "com.memfault.smartfridge", userId = 10045))
        significantApps = listOf(
            SignificantApp(
                packageName = "com.memfault.smartfridge",
                identifier = "com.memfault.smartfridge",
                internal = false,
            ),
        )
        // State written by a version of Bort that only tracked its own writes.
        uidIoStatsStorage.state = UidIoStats(bootId = "boot-A", writtenBytes = 10_000)
        nextUidIoStats = UidIoStats(
            bootId = "boot-A",
            writtenBytes = 12_000,
            writesByUid = mapOf(10045 to UidWrites(writeBytes = 900_000_000, logicalWriteBytes = 999_000_000)),
        )

        storageStatsCollector.collectStorageStats(FakeCombinedTimeProvider.now())

        verify {
            storageStatsReporter.reportAppWrites(
                app = significantApps.single(),
                writes = UidWrites.ZERO,
                now = now,
                uptime = elapsed,
            )
        }
        assertThat(uidIoStatsStorage.state.writesByUid).isEqualTo(nextUidIoStats.writesByUid)
    }

    @Test fun `app writes are reported in full for a uid first seen after a reboot`() = runTest(coroutineContext) {
        val combined = FakeCombinedTimeProvider.now
        val now = combined.timestamp.toEpochMilli()
        val elapsed = combined.elapsedRealtime.duration.inWholeMilliseconds

        packages = listOf(Package(id = "com.memfault.smartfridge", userId = 10045))
        significantApps = listOf(
            SignificantApp(
                packageName = "com.memfault.smartfridge",
                identifier = "com.memfault.smartfridge",
                internal = false,
            ),
        )
        uidIoStatsStorage.state = UidIoStats(bootId = "boot-old", writtenBytes = 10_000)
        nextUidIoStats = UidIoStats(
            bootId = "boot-new",
            writtenBytes = 12_000,
            writesByUid = mapOf(10045 to UidWrites(writeBytes = 2_048, logicalWriteBytes = 4_096)),
        )

        storageStatsCollector.collectStorageStats(FakeCombinedTimeProvider.now())

        verify {
            storageStatsReporter.reportAppWrites(
                app = significantApps.single(),
                writes = UidWrites(writeBytes = 2_048, logicalWriteBytes = 4_096),
                now = now,
                uptime = elapsed,
            )
        }
    }

    @Test fun `app writes are not reported when EMPTY is returned`() = runTest(coroutineContext) {
        significantApps = listOf(
            SignificantApp(packageName = "com.memfault.bort", identifier = "bort", internal = true),
        )
        uidIoStatsStorage.state = UidIoStats(bootId = "boot-A", writtenBytes = 5_000)
        nextUidIoStats = UidIoStats.EMPTY

        storageStatsCollector.collectStorageStats(FakeCombinedTimeProvider.now())

        verify(exactly = 0) { storageStatsReporter.reportAppWrites(any(), any(), any(), any()) }
    }

    @Test fun `app writes keep their baseline when packages cannot be resolved`() = runTest(coroutineContext) {
        val combined = FakeCombinedTimeProvider.now
        val now = combined.timestamp.toEpochMilli()
        val elapsed = combined.elapsedRealtime.duration.inWholeMilliseconds

        significantApps = listOf(
            SignificantApp(
                packageName = "com.memfault.smartfridge",
                identifier = "com.memfault.smartfridge",
                internal = false,
            ),
        )
        val baseline = mapOf(10045 to UidWrites(writeBytes = 1_000, logicalWriteBytes = 8_000))
        uidIoStatsStorage.state = UidIoStats(
            bootId = "boot-A",
            writtenBytes = 0,
            writesByUid = baseline,
        )

        // The package manager timed out.
        packages = emptyList()
        nextUidIoStats = UidIoStats(
            bootId = "boot-A",
            writtenBytes = 0,
            writesByUid = mapOf(10045 to UidWrites(writeBytes = 5_000, logicalWriteBytes = 9_500)),
        )

        storageStatsCollector.collectStorageStats(FakeCombinedTimeProvider.now())

        verify(exactly = 0) { storageStatsReporter.reportAppWrites(any(), any(), any(), any()) }
        assertThat(uidIoStatsStorage.state.writesByUid).isEqualTo(baseline)

        packages = listOf(Package(id = "com.memfault.smartfridge", userId = 10045))
        nextUidIoStats = UidIoStats(
            bootId = "boot-A",
            writtenBytes = 0,
            writesByUid = mapOf(10045 to UidWrites(writeBytes = 6_000, logicalWriteBytes = 10_000)),
        )

        storageStatsCollector.collectStorageStats(FakeCombinedTimeProvider.now())

        verify {
            storageStatsReporter.reportAppWrites(
                app = significantApps.single(),
                writes = UidWrites(writeBytes = 5_000, logicalWriteBytes = 2_000),
                now = now,
                uptime = elapsed,
            )
        }
    }

    @Test fun `usage reporter writes are not reported because it shares the system uid`() = runTest(
        coroutineContext,
    ) {
        val reporter = SignificantApp(
            packageName = APPLICATION_ID_MEMFAULT_USAGE_REPORTER,
            identifier = "reporter",
            internal = true,
        )
        packages = listOf(Package(id = APPLICATION_ID_MEMFAULT_USAGE_REPORTER, userId = Process.SYSTEM_UID))
        significantApps = listOf(reporter)
        uidIoStatsStorage.state = UidIoStats(
            bootId = "boot-A",
            writtenBytes = 0,
            writesByUid = mapOf(Process.SYSTEM_UID to UidWrites(writeBytes = 100, logicalWriteBytes = 400)),
        )
        nextUidIoStats = UidIoStats(
            bootId = "boot-A",
            writtenBytes = 0,
            writesByUid = mapOf(Process.SYSTEM_UID to UidWrites(writeBytes = 700, logicalWriteBytes = 900)),
        )

        storageStatsCollector.collectStorageStats(FakeCombinedTimeProvider.now())

        verify(exactly = 0) { storageStatsReporter.reportAppWrites(reporter, any(), any(), any()) }
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
