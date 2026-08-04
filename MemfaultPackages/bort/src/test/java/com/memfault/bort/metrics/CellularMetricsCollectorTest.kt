package com.memfault.bort.metrics

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.CellSignalStrength
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.memfault.bort.settings.MetricsSettings
import com.memfault.bort.settings.RateLimitingSettings
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class CellularMetricsCollectorTest {
    @get:Rule
    val metricsDbTestEnvironment = MetricsDbTestEnvironment().apply {
        highResMetricsEnabledValue = true
    }
    private var collectCellularValue = true
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
        override val collectCellular: Boolean get() = collectCellularValue
        override val thermalMetricsEnabled: Boolean get() = TODO("not used")
        override val thermalCollectLegacyMetrics: Boolean get() = TODO("not used")
        override val thermalCollectStatus: Boolean get() = TODO("not used")
        override val sysfsThermalEnabled: Boolean get() = TODO("not used")
        override val sysfsThermalAllowlist: List<String> get() = TODO("not used")
        override val cpuInterestingProcesses: Set<String> get() = TODO("not used")
        override val cpuProcessReportingThreshold: Int get() = TODO("not used")
        override val cpuProcessLimitTopN: Int get() = TODO("not used")
        override val alwaysCreateCpuProcessMetrics: Boolean get() = TODO("not used")
        override val enableStatsdCollection: Boolean get() = TODO("not used")
        override val extraStatsDAtoms: List<Int> get() = TODO("not used")
    }

    @Suppress("DEPRECATION")
    @Test
    fun collectsCellularMetrics() = runTest {
        val signalStrength = mockk<SignalStrength>(relaxed = true)
        every { signalStrength.level } returns 3
        every { signalStrength.gsmSignalStrength } returns 14

        val telephony = mockk<TelephonyManager>(relaxed = true)
        every { telephony.networkOperator } returns "310410"
        every { telephony.dataNetworkType } returns TelephonyManager.NETWORK_TYPE_LTE
        every { telephony.simState } returns TelephonyManager.SIM_STATE_READY

        val application = mockApplication(telephony)
        val signalStrengthProvider = CellularSignalStrengthProvider { signalStrength }

        val collector = CellularMetricsCollector(
            application,
            signalStrengthProvider,
            metricsSettings,
            Build.VERSION_CODES.O,
        )

        collector.collect()
        collector.collect()

        val report = metricsDbTestEnvironment.dao.collectHeartbeat(
            endTimestampMs = System.currentTimeMillis(),
            endUptimeMs = System.currentTimeMillis(),
        )
        assertThat(report.hourlyHeartbeatReport.metrics["cellular_mcc.latest"]!!.jsonPrimitive.content).isEqualTo("310")
        assertThat(report.hourlyHeartbeatReport.metrics["cellular_mnc.latest"]!!.jsonPrimitive.content).isEqualTo("410")
        assertThat(report.hourlyHeartbeatReport.metrics["cellular_rat.latest"]!!.jsonPrimitive.content).isEqualTo("LTE")
        assertThat(
            report.hourlyHeartbeatReport.metrics["cellular_sim_state.latest"]!!.jsonPrimitive.content,
        ).isEqualTo("READY")
        assertThat(report.hourlyHeartbeatReport.metrics["cellular_signal_level.latest"]!!.double).isEqualTo(3.0)
        assertThat(report.hourlyHeartbeatReport.metrics["cellular_signal_dbm.latest"]!!.double).isEqualTo(-85.0)
    }

    @Suppress("DEPRECATION")
    @Test
    fun collectsCellularStringMetricsWithoutSignal() = runTest {
        val telephony = mockk<TelephonyManager>(relaxed = true)
        every { telephony.networkOperator } returns "310410"
        every { telephony.dataNetworkType } returns TelephonyManager.NETWORK_TYPE_LTE
        every { telephony.simState } returns TelephonyManager.SIM_STATE_READY

        val application = mockApplication(telephony)
        val signalStrengthProvider = CellularSignalStrengthProvider { null }

        val collector = CellularMetricsCollector(
            application,
            signalStrengthProvider,
            metricsSettings,
            Build.VERSION_CODES.O,
        )

        collector.collect()
        collector.collect()

        val report = metricsDbTestEnvironment.dao.collectHeartbeat(
            endTimestampMs = System.currentTimeMillis(),
            endUptimeMs = System.currentTimeMillis(),
        )
        assertThat(report.hourlyHeartbeatReport.metrics["cellular_mcc.latest"]!!.jsonPrimitive.content).isEqualTo("310")
        assertThat(report.hourlyHeartbeatReport.metrics["cellular_mnc.latest"]!!.jsonPrimitive.content).isEqualTo("410")
        assertThat(report.hourlyHeartbeatReport.metrics["cellular_rat.latest"]!!.jsonPrimitive.content).isEqualTo("LTE")
        assertThat(
            report.hourlyHeartbeatReport.metrics["cellular_sim_state.latest"]!!.jsonPrimitive.content,
        ).isEqualTo("READY")
    }

    @Config(sdk = [Build.VERSION_CODES.Q])
    @Test
    fun collectsLteMetrics() = runTest {
        val lte = mockk<CellSignalStrengthLte>(relaxed = true) {
            every { dbm } returns -95
            every { rsrp } returns -95
            every { rsrq } returns -11
        }
        val signalStrength = mockSignalStrength(cellSignalStrengths = listOf(lte))

        collector(signalStrength, Build.VERSION_CODES.Q).run {
            collect()
            collect()
        }

        val metrics = collectHeartbeatMetrics()
        assertThat(metrics["cellular_lte_rsrp_dbm.latest"]!!.double).isEqualTo(-95.0)
        assertThat(metrics["cellular_lte_rsrq_db.latest"]!!.double).isEqualTo(-11.0)
        assertThat(metrics["cellular_nr_rsrp_dbm.latest"]).isNull()
        assertThat(metrics["cellular_nr_rsrq_db.latest"]).isNull()
    }

    /**
     * On NSA 5G the device is anchored on LTE while also carrying an NR leg, so both report signal
     * strengths and each must land in its own metric.
     */
    @Config(sdk = [Build.VERSION_CODES.Q])
    @Test
    fun collectsLteAndNrMetricsSeparatelyOnNsa() = runTest {
        val lte = mockk<CellSignalStrengthLte>(relaxed = true) {
            every { dbm } returns -95
            every { rsrp } returns -95
            every { rsrq } returns -11
        }
        val nr = mockk<CellSignalStrengthNr>(relaxed = true) {
            every { dbm } returns -110
            every { ssRsrp } returns -110
            every { ssRsrq } returns -14
        }
        val signalStrength = mockSignalStrength(cellSignalStrengths = listOf(lte, nr))

        collector(signalStrength, Build.VERSION_CODES.Q).run {
            collect()
            collect()
        }

        val metrics = collectHeartbeatMetrics()
        assertThat(metrics["cellular_lte_rsrp_dbm.latest"]!!.double).isEqualTo(-95.0)
        assertThat(metrics["cellular_lte_rsrq_db.latest"]!!.double).isEqualTo(-11.0)
        assertThat(metrics["cellular_nr_rsrp_dbm.latest"]!!.double).isEqualTo(-110.0)
        assertThat(metrics["cellular_nr_rsrq_db.latest"]!!.double).isEqualTo(-14.0)
    }

    /**
     * Bort Lite is not a privileged system app, so it doesn't hold the phone-state permission that
     * getDataNetworkType() requires. The remaining metrics need no permission and must still be
     * collected.
     */
    @Suppress("DEPRECATION")
    @Test
    fun collectsRemainingMetricsWhenNetworkTypePermissionDenied() = runTest {
        val signalStrength = mockk<SignalStrength>(relaxed = true) {
            every { level } returns 3
            every { gsmSignalStrength } returns 14
        }
        val telephony = mockk<TelephonyManager>(relaxed = true) {
            every { networkOperator } returns "310410"
            every { dataNetworkType } returns TelephonyManager.NETWORK_TYPE_LTE
            every { simState } returns TelephonyManager.SIM_STATE_READY
        }
        val application = mockApplication(telephony, phoneStatePermission = false)

        CellularMetricsCollector(
            application,
            { signalStrength },
            metricsSettings,
            Build.VERSION_CODES.O,
        ).run {
            collect()
            collect()
        }

        val metrics = collectHeartbeatMetrics()
        assertThat(metrics["cellular_rat.latest"]).isNull()
        assertThat(metrics["cellular_mcc.latest"]!!.jsonPrimitive.content).isEqualTo("310")
        assertThat(metrics["cellular_mnc.latest"]!!.jsonPrimitive.content).isEqualTo("410")
        assertThat(metrics["cellular_sim_state.latest"]!!.jsonPrimitive.content).isEqualTo("READY")
        assertThat(metrics["cellular_signal_level.latest"]!!.double).isEqualTo(3.0)
        assertThat(metrics["cellular_signal_dbm.latest"]!!.double).isEqualTo(-85.0)
    }

    private fun mockApplication(
        telephony: TelephonyManager,
        phoneStatePermission: Boolean = true,
    ) = mockk<Application> {
        every { getSystemService(TelephonyManager::class.java) } returns telephony
        every { checkSelfPermission(any()) } returns
            if (phoneStatePermission) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
    }

    private fun mockSignalStrength(cellSignalStrengths: List<CellSignalStrength>): SignalStrength =
        mockk<SignalStrength>(relaxed = true) {
            every { level } returns 3
            every { getCellSignalStrengths() } returns cellSignalStrengths
            every { getCellSignalStrengths(CellSignalStrengthLte::class.java) } returns
                cellSignalStrengths.filterIsInstance<CellSignalStrengthLte>()
            every { getCellSignalStrengths(CellSignalStrengthNr::class.java) } returns
                cellSignalStrengths.filterIsInstance<CellSignalStrengthNr>()
        }

    private fun collector(
        signalStrength: SignalStrength?,
        androidSdkVersion: Int,
    ): CellularMetricsCollector {
        val telephony = mockk<TelephonyManager>(relaxed = true) {
            every { networkOperator } returns "310410"
            @Suppress("DEPRECATION")
            every { dataNetworkType } returns TelephonyManager.NETWORK_TYPE_LTE
            every { simState } returns TelephonyManager.SIM_STATE_READY
        }
        val application = mockApplication(telephony)
        return CellularMetricsCollector(
            application,
            { signalStrength },
            metricsSettings,
            androidSdkVersion,
        )
    }

    private suspend fun collectHeartbeatMetrics(): Map<String, JsonPrimitive> =
        metricsDbTestEnvironment.dao.collectHeartbeat(
            endTimestampMs = System.currentTimeMillis(),
            endUptimeMs = System.currentTimeMillis(),
        ).hourlyHeartbeatReport.metrics

    @Suppress("DEPRECATION")
    @Test
    fun doesNotCollectCellularMetricsWhenDisabled() = runTest {
        collectCellularValue = false

        val signalStrength = mockk<SignalStrength>(relaxed = true)
        every { signalStrength.level } returns 3

        val telephony = mockk<TelephonyManager>(relaxed = true)
        every { telephony.networkOperator } returns "310410"
        every { telephony.dataNetworkType } returns TelephonyManager.NETWORK_TYPE_LTE
        every { telephony.simState } returns TelephonyManager.SIM_STATE_READY

        val application = mockApplication(telephony)
        val signalStrengthProvider = CellularSignalStrengthProvider { signalStrength }

        val collector = CellularMetricsCollector(
            application,
            signalStrengthProvider,
            metricsSettings,
            Build.VERSION_CODES.O,
        )

        collector.collect()

        val report = metricsDbTestEnvironment.dao.collectHeartbeat(
            endTimestampMs = System.currentTimeMillis(),
            endUptimeMs = System.currentTimeMillis(),
        )
        assertThat(report.hourlyHeartbeatReport.metrics).isEmpty()
    }
}
