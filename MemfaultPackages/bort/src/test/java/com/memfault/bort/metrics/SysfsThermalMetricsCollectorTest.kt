package com.memfault.bort.metrics

import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.memfault.bort.DumpsterClient
import com.memfault.bort.settings.MetricsSettings
import com.memfault.bort.settings.RateLimitingSettings
import com.memfault.bort.settings.SettingsFlow
import com.memfault.bort.settings.SettingsProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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
class SysfsThermalMetricsCollectorTest {

    @get:Rule
    val metricsDbTestEnvironment = MetricsDbTestEnvironment()

    private val dumpsterClient: DumpsterClient = mockk {
        coEvery { getSysfsThermalZones() } returns null
    }

    private var sysfsThermalEnabledValue = true
    private var sysfsThermalAllowlistValue: List<String> = listOf()

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
        override val collectMemory: Boolean get() = TODO("not used")
        override val thermalMetricsEnabled: Boolean get() = TODO("not used")
        override val thermalCollectLegacyMetrics: Boolean get() = TODO("not used")
        override val thermalCollectStatus: Boolean get() = TODO("not used")
        override val sysfsThermalEnabled: Boolean get() = sysfsThermalEnabledValue
        override val sysfsThermalAllowlist: List<String> get() = sysfsThermalAllowlistValue
        override val cpuInterestingProcesses: Set<String> get() = TODO("not used")
        override val cpuProcessReportingThreshold: Int get() = TODO("not used")
        override val cpuProcessLimitTopN: Int get() = TODO("not used")
        override val alwaysCreateCpuProcessMetrics: Boolean get() = TODO("not used")
        override val enableStatsdCollection: Boolean get() = TODO("not used")
        override val extraStatsDAtoms: List<Int> get() = TODO("not used")
    }

    private val settingsFlow = object : SettingsFlow {
        override val settings: Flow<SettingsProvider> = emptyFlow()
    }

    private val collector = SysfsThermalMetricsCollector(
        dumpsterClient = dumpsterClient,
        metricsSettings = metricsSettings,
        settingsFlow = settingsFlow,
    )

    @Test
    fun collectionDisabled() = runTest {
        sysfsThermalEnabledValue = false
        collector.collect()
        coVerify(exactly = 0) { dumpsterClient.getSysfsThermalZones() }
        val report = metricsDbTestEnvironment.dao.collectHeartbeat(
            endTimestampMs = System.currentTimeMillis(),
            endUptimeMs = System.currentTimeMillis(),
        )
        assertThat(report.hourlyHeartbeatReport.metrics).isEmpty()
    }

    @Test
    fun collectsAllZones() = runTest {
        coEvery { dumpsterClient.getSysfsThermalZones() } returns "acpitz\t43000\nx86_pkg_temp\t43000\n"
        collector.collect()
        val report = metricsDbTestEnvironment.dao.collectHeartbeat(
            endTimestampMs = System.currentTimeMillis(),
            endUptimeMs = System.currentTimeMillis(),
        )
        assertThat(report.hourlyHeartbeatReport.metrics["sysfs_thermal_acpitz_c.mean"]!!.double)
            .isCloseTo(43.0, delta = 0.001)
        assertThat(report.hourlyHeartbeatReport.metrics["sysfs_thermal_x86_pkg_temp_c.mean"]!!.double)
            .isCloseTo(43.0, delta = 0.001)
    }

    @Test
    fun allowlistFiltersZones() = runTest {
        sysfsThermalAllowlistValue = listOf("acpitz")
        coEvery { dumpsterClient.getSysfsThermalZones() } returns "acpitz\t43000\nx86_pkg_temp\t43000\n"
        collector.collect()
        val report = metricsDbTestEnvironment.dao.collectHeartbeat(
            endTimestampMs = System.currentTimeMillis(),
            endUptimeMs = System.currentTimeMillis(),
        )
        assertThat(report.hourlyHeartbeatReport.metrics["sysfs_thermal_acpitz_c.mean"]!!.double)
            .isCloseTo(43.0, delta = 0.001)
        assertThat(report.hourlyHeartbeatReport.metrics["sysfs_thermal_x86_pkg_temp_c.mean"]).isEqualTo(null)
    }

    @Test
    fun sanitisesZoneType() = runTest {
        coEvery { dumpsterClient.getSysfsThermalZones() } returns "INT3400 Thermal\t20000\n"
        collector.collect()
        val report = metricsDbTestEnvironment.dao.collectHeartbeat(
            endTimestampMs = System.currentTimeMillis(),
            endUptimeMs = System.currentTimeMillis(),
        )
        assertThat(report.hourlyHeartbeatReport.metrics["sysfs_thermal_INT3400-Thermal_c.mean"]!!.double)
            .isCloseTo(20.0, delta = 0.001)
    }

    @Test
    fun nullOutputFromDumpster() = runTest {
        coEvery { dumpsterClient.getSysfsThermalZones() } returns null
        collector.collect()
        val report = metricsDbTestEnvironment.dao.collectHeartbeat(
            endTimestampMs = System.currentTimeMillis(),
            endUptimeMs = System.currentTimeMillis(),
        )
        assertThat(report.hourlyHeartbeatReport.metrics).isEmpty()
    }

    @Test
    fun malformedLinesSkipped() = runTest {
        coEvery { dumpsterClient.getSysfsThermalZones() } returns "no-tab-here\nacpitz\t43000\n"
        collector.collect()
        val report = metricsDbTestEnvironment.dao.collectHeartbeat(
            endTimestampMs = System.currentTimeMillis(),
            endUptimeMs = System.currentTimeMillis(),
        )
        assertThat(report.hourlyHeartbeatReport.metrics["sysfs_thermal_acpitz_c.mean"]!!.double)
            .isCloseTo(43.0, delta = 0.001)
    }

    @Test
    fun sanitiseCompanion() {
        val expectations = mapOf(
            // Disallowed chars replaced with '-'
            "INT3400 Thermal" to "INT3400-Thermal",
            "some zone" to "some-zone",
            "cpu@core" to "cpu-core",
            "cpu:core0" to "cpu-core0",
            // Allowed chars pass through unchanged
            "x86_pkg_temp" to "x86_pkg_temp",
            "CPU/0" to "CPU/0",
            "acpitz" to "acpitz",
        )
        expectations.forEach { (input, expected) ->
            assertThat(SysfsThermalMetricsCollector.sanitise(input)).isEqualTo(expected)
        }
    }
}
