package com.memfault.bort.metrics

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.memfault.bort.BortSystemCapabilities
import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.DumpsterClient
import com.memfault.bort.FakeCombinedTimeProvider
import com.memfault.bort.InstallationIdProvider
import com.memfault.bort.PackageManagerClient
import com.memfault.bort.TaskResult.SUCCESS
import com.memfault.bort.chronicler.ClientRateLimitCollector
import com.memfault.bort.clientserver.MarDevice
import com.memfault.bort.clientserver.MarFileWithManifest
import com.memfault.bort.clientserver.MarManifest
import com.memfault.bort.clientserver.MarMetadata.HeartbeatMarMetadata
import com.memfault.bort.makeFakeSharedPreferences
import com.memfault.bort.metrics.custom.CustomMetrics
import com.memfault.bort.metrics.custom.CustomReport
import com.memfault.bort.metrics.custom.MetricReport
import com.memfault.bort.metrics.statsd.StatsdMetricCollector
import com.memfault.bort.networkstats.NetworkStatsCollector
import com.memfault.bort.parsers.PackageManagerReport
import com.memfault.bort.settings.Resolution.NORMAL
import com.memfault.bort.settings.Resolution.NOT_APPLICABLE
import com.memfault.bort.storage.AppStorageStatsCollector
import com.memfault.bort.storage.DatabaseSizeCollector
import com.memfault.bort.time.BaseLinuxBootRelativeTime
import com.memfault.bort.time.BoxedDuration
import com.memfault.bort.time.CombinedTime
import com.memfault.bort.time.CombinedTimeProvider
import com.memfault.bort.time.LinuxBootRelativeTime
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.memfault.bort.uploader.EnqueueUpload
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

class MetricsCollectionTaskTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val sessionStartMs = 100L
    private val sessionEndMs = 200L
    private val collectionTimeMs = 300L

    private val mockCollectionTime = CombinedTime(
        timestamp = Instant.ofEpochMilli(collectionTimeMs),
        uptime = BoxedDuration(collectionTimeMs.milliseconds),
        elapsedRealtime = BoxedDuration(collectionTimeMs.milliseconds),
        linuxBootId = "1",
        bootCount = 1,
    )

    private val mockLastHeartbeatUptime = LinuxBootRelativeTime(
        uptime = BoxedDuration(0.milliseconds),
        elapsedRealtime = BoxedDuration(0.milliseconds),
        linuxBootId = "1",
    )

    private val mockSession = MetricReport(
        startTimestampMs = sessionStartMs,
        endTimestampMs = sessionEndMs,
        metrics = emptyMap(),
        internalMetrics = emptyMap(),
        reportName = "test-session",
        softwareVersion = "1.0.0",
        version = 1,
        reportType = "Session",
        hrt = null,
    )

    private val mockHourlyReport = MetricReport(
        startTimestampMs = 0L,
        endTimestampMs = collectionTimeMs,
        metrics = emptyMap(),
        internalMetrics = emptyMap(),
        softwareVersion = null,
        version = 1,
        reportType = "Heartbeat",
        hrt = null,
    )

    private val mockCustomReport = CustomReport(
        hourlyHeartbeatReport = mockHourlyReport,
        dailyHeartbeatReport = null,
        sessions = listOf(mockSession),
    )

    private val marDevice = MarDevice(
        projectKey = "projectKey",
        hardwareVersion = "hardwareVersion",
        softwareVersion = "1.0.0",
        softwareType = "softwareType",
        deviceSerial = "deviceSerial",
    )

    private val heartbeatManifest = MarManifest(
        collectionTime = FakeCombinedTimeProvider.now(),
        type = "test",
        device = marDevice,
        metadata = HeartbeatMarMetadata(
            batteryStatsFileName = null,
            heartbeatIntervalMs = 0,
            customMetrics = emptyMap(),
            builtinMetrics = emptyMap(),
            reportType = "heartbeat",
            reportName = null,
        ),
        debuggingResolution = NOT_APPLICABLE,
        loggingResolution = NOT_APPLICABLE,
        monitoringResolution = NORMAL,
    )

    private val enqueueUpload: EnqueueUpload = mockk {
        coEvery { enqueue(any(), any(), any(), any(), any(), any()) } answers {
            Result.success(
                MarFileWithManifest(
                    marFile = folder.newFile(),
                    manifest = heartbeatManifest,
                ),
            )
        }
    }

    private val combinedTimeProvider = object : CombinedTimeProvider {
        override fun now(): CombinedTime = mockCollectionTime
    }

    private val lastHeartbeatEndTimeProvider = object : LastHeartbeatEndTimeProvider {
        override var lastEnd: BaseLinuxBootRelativeTime
            get() = mockLastHeartbeatUptime
            set(_) = Unit
    }

    private val tokenBucketStore: TokenBucketStore = mockk {
        every { takeSimple(any(), any(), any()) } returns true
    }

    private val packageManagerClient: PackageManagerClient = mockk {
        coEvery { getPackageManagerReport() } returns PackageManagerReport()
    }

    private val systemPropertiesCollector: SystemPropertiesCollector = mockk {
        coEvery { collect() } returns null
    }

    private val customMetrics: CustomMetrics = mockk {
        coEvery { startedHeartbeatOrNull() } returns null
        coEvery { collectHeartbeat(any(), any()) } returns mockCustomReport
    }

    private val storageStatsCollector: StorageStatsCollector = mockk {
        coEvery { collectStorageStats(any()) } just Runs
    }

    private val networkStatsCollector: NetworkStatsCollector = mockk {
        coEvery { collectAndRecord(any(), any()) } just Runs
    }

    private val appVersionsCollector: AppVersionsCollector = mockk {
        coEvery { collect() } returns null
    }

    private val dumpsterClient: DumpsterClient = mockk {
        coEvery { availableVersion() } returns null
    }

    private val bortSystemCapabilities: BortSystemCapabilities = mockk {
        every { isBortLite() } returns false
        coEvery { supportsCaliperMetrics() } returns false
    }

    private val installationIdProvider: InstallationIdProvider = object : InstallationIdProvider {
        override fun id(): String = UUID.randomUUID().toString()
    }

    private val batteryStatsCollector: BatteryStatsCollector = mockk {
        coEvery { collect(any(), any()) } returns mockk {
            every { batteryStatsFileToUpload } returns null
            every { aggregatedMetrics } returns emptyMap()
            every { internalAggregatedMetrics } returns emptyMap()
            every { batteryStatsHrt } returns emptySet()
        }
    }

    private val crashHandler: CrashHandler = object : CrashHandler {
        override suspend fun onCrash(componentName: String?, crashTimestamp: Instant) = Unit
        override fun onBoot() = Unit
        override fun process() = Unit
    }

    private val clientRateLimitCollector: ClientRateLimitCollector = mockk {
        coEvery { collect(any(), any()) } just Runs
    }

    private val appStorageStatsCollector: AppStorageStatsCollector = mockk {
        coEvery { collect(any()) } returns emptyList()
    }

    private val databaseSizeCollector: DatabaseSizeCollector = mockk {
        coEvery { collect(any()) } returns emptyList()
    }

    private val deviceInfoProvider: DeviceInfoProvider = mockk {
        coEvery { getDeviceInfo() } returns mockk { every { softwareVersion } returns "1.0.0" }
    }

    private val everCollectedMetricsPreferenceProvider = EverCollectedMetricsPreferenceProvider(
        sharedPreferences = makeFakeSharedPreferences(),
    )

    private val usageStatsCollector: UsageStatsCollector = mockk {
        coEvery { collectUsageStats(any(), any()) } just Runs
    }

    private val statsDMetricCollector: StatsdMetricCollector = mockk {
        every { collect() } just Runs
    }

    private val task = MetricsCollectionTask(
        enqueueUpload = enqueueUpload,
        combinedTimeProvider = combinedTimeProvider,
        lastHeartbeatEndTimeProvider = lastHeartbeatEndTimeProvider,
        tokenBucketStore = tokenBucketStore,
        packageManagerClient = packageManagerClient,
        systemPropertiesCollector = systemPropertiesCollector,
        customMetrics = customMetrics,
        storageStatsCollector = storageStatsCollector,
        networkStatsCollector = networkStatsCollector,
        appVersionsCollector = appVersionsCollector,
        dumpsterClient = dumpsterClient,
        bortSystemCapabilities = bortSystemCapabilities,
        installationIdProvider = installationIdProvider,
        batteryStatsCollector = batteryStatsCollector,
        crashHandler = crashHandler,
        clientRateLimitCollector = clientRateLimitCollector,
        appStorageStatsCollector = appStorageStatsCollector,
        databaseSizeCollector = databaseSizeCollector,
        deviceInfoProvider = deviceInfoProvider,
        everCollectedMetricsPreferenceProvider = everCollectedMetricsPreferenceProvider,
        usageStatsCollector = usageStatsCollector,
        statsDMetricCollector = statsDMetricCollector,
    )

    @Test
    fun `session end timestamp is valid and matches collection time`() = runTest {
        val result = task.doWork(Unit)

        assertThat(result).isEqualTo(SUCCESS)

        coVerify {
            enqueueUpload.enqueue(
                file = null,
                metadata = any(),
                collectionTime = withArg { time ->
                    assertThat(time.timestamp.toEpochMilli()).isEqualTo(sessionEndMs)
                },
                overrideMonitoringResolution = null,
                overrideDebuggingResolution = null,
                overrideSoftwareVersion = "1.0.0",
            )
        }
    }
}
