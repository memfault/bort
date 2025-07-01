package com.memfault.bort.metrics

import android.app.ActivityManager
import android.app.ActivityManager.MemoryInfo
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEmpty
import com.memfault.bort.settings.MetricsSettings
import com.memfault.bort.settings.RateLimitingSettings
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class MemoryMetricsCollectorTest {
    @get:Rule
    val metricsDbTestEnvironment = MetricsDbTestEnvironment().apply {
        highResMetricsEnabledValue = true
    }
    private var totalMem = 0L
    private var availMem = 0L
    private val activityManager: ActivityManager = mockk {
        every { getMemoryInfo(any()) } answers {
            arg<MemoryInfo>(0).totalMem = totalMem
            arg<MemoryInfo>(0).availMem = availMem
        }
    }
    private var collectMemoryValue = true
    private val metricsSettings = object : MetricsSettings {
        override val dataSourceEnabled: Boolean get() = TODO("not used")
        override val dailyHeartbeatEnabled: Boolean get() = TODO("not used")
        override val sessionsRateLimitingSettings: RateLimitingSettings get() = TODO("not used")
        override val collectionInterval: Duration get() = TODO("not used")
        override val systemProperties: List<String> get() = TODO("not used")
        override val appVersions: List<String> get() = TODO("not used")
        override val maxNumAppVersions: Int get() = TODO("not used")
        override val reporterCollectionInterval: Duration get() = TODO("not used")
        override val cachePackageManagerReport: Boolean get() = TODO("not used")
        override val recordImei: Boolean get() = TODO("not used")
        override val operationalCrashesExclusions: List<String> get() = TODO("not used")
        override val operationalCrashesComponentGroups: JsonObject get() = TODO("not used")
        override val pollingInterval: Duration get() = TODO("not used")
        override val collectMemory: Boolean get() = collectMemoryValue
        override val thermalMetricsEnabled: Boolean get() = TODO("Not used")
        override val thermalCollectLegacyMetrics: Boolean get() = TODO("Not used")
        override val thermalCollectStatus: Boolean get() = TODO("not used")
        override val cpuInterestingProcesses: Set<String> get() = TODO("not used")
        override val cpuProcessReportingThreshold: Int get() = TODO("not used")
        override val cpuProcessLimitTopN: Int get() = TODO("not used")
        override val alwaysCreateCpuProcessMetrics: Boolean get() = TODO("not used")
    }
    private val collector = MemoryMetricsCollector(activityManager, metricsSettings)

    @Test
    fun collectsMemoryMetrics() = runTest {
        totalMem = 1000
        availMem = 83 // used = 91.7%
        collector.collect()

        availMem = 71 // used = 92.9%
        collector.collect()

        val report = metricsDbTestEnvironment.dao.collectHeartbeat(
            endTimestampMs = System.currentTimeMillis(),
        )
        assertThat(report.hourlyHeartbeatReport.metrics["memory_pct"]!!.double).isCloseTo(92.3, delta = 0.001)
        assertThat(report.hourlyHeartbeatReport.metrics["memory_pct_max"]!!.double).isCloseTo(92.9, delta = 0.001)
    }

    @Test
    fun doesNotCollectMemoryMetrics() = runTest {
        collectMemoryValue = false

        totalMem = 1000
        availMem = 83 // used = 91.7%
        collector.collect()

        availMem = 71 // used = 92.9%
        collector.collect()

        val report = metricsDbTestEnvironment.dao.collectHeartbeat(
            endTimestampMs = System.currentTimeMillis(),
        )
        assertThat(report.hourlyHeartbeatReport.metrics).isEmpty()
    }
}
