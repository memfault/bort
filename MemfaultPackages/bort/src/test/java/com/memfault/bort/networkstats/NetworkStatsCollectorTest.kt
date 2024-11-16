package com.memfault.bort.networkstats

import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isNotNull
import assertk.assertions.prop
import com.memfault.bort.PackageManagerClient
import com.memfault.bort.makeFakeSharedPreferences
import com.memfault.bort.metrics.HighResTelemetry
import com.memfault.bort.metrics.HighResTelemetry.DataType
import com.memfault.bort.metrics.HighResTelemetry.Datum
import com.memfault.bort.metrics.HighResTelemetry.MetricType
import com.memfault.bort.metrics.HighResTelemetry.RollupMetadata
import com.memfault.bort.metrics.MetricsDbTestEnvironment
import com.memfault.bort.metrics.SignificantApp
import com.memfault.bort.metrics.SignificantAppsProvider
import com.memfault.bort.metrics.custom.CustomMetrics
import com.memfault.bort.metrics.custom.CustomReport
import com.memfault.bort.metrics.custom.MetricReport
import com.memfault.bort.parsers.Package
import com.memfault.bort.parsers.PackageManagerReport
import com.memfault.bort.settings.NetworkUsageSettings
import com.memfault.bort.time.CombinedTime
import com.memfault.bort.time.boxed
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class NetworkStatsCollectorTest {
    private val fakeSharedPreferences = makeFakeSharedPreferences()
    private val lastNetworkStatsCollectionTimestamp = LastNetworkStatsCollectionTimestamp(fakeSharedPreferences)
    private val networkStatsQueries = mockk<NetworkStatsQueries>()
    private val packageManagerClient = mockk<PackageManagerClient> {
        coEvery { getPackageManagerReport() } answers { PackageManagerReport() }
    }
    private var checkLegacyMetrics = false

    private val fakeNetworkUsageSettings = object : NetworkUsageSettings {
        override var dataSourceEnabled: Boolean = true
        override var collectionReceiveThresholdKb: Long = 1000
        override var collectionTransmitThresholdKb: Long = 1000
        override val collectLegacyMetrics: Boolean get() = checkLegacyMetrics
    }

    private val significantAppsProvider = object : SignificantAppsProvider {
        val internalApps = listOf(
            SignificantApp(
                packageName = "com.memfault.bort",
                identifier = "bort",
                internal = true,
            ),
            SignificantApp(
                packageName = "com.memfault.bort.ota",
                identifier = "ota",
                internal = true,
            ),
        )
        val externalApps = mutableListOf<SignificantApp>()

        override fun internalApps(): List<SignificantApp> = internalApps
        override fun externalApps(): List<SignificantApp> = externalApps
    }

    private val collector = RealNetworkStatsCollector(
        lastNetworkStatsCollectionTimestamp = lastNetworkStatsCollectionTimestamp,
        networkStatsQueries = networkStatsQueries,
        packageManagerClient = packageManagerClient,
        networkUsageSettings = fakeNetworkUsageSettings,
        significantAppsProvider = significantAppsProvider,
    )

    private fun time(timeMs: Long) = CombinedTime(
        uptime = timeMs.milliseconds.boxed(),
        elapsedRealtime = timeMs.milliseconds.boxed(),
        linuxBootId = "bootid",
        bootCount = 1,
        timestamp = Instant.ofEpochMilli(timeMs),
    )

    private val fakeNetworkStatsSummary = NetworkStatsSummary(
        uid = NetworkStatsUid.fromUid(10_000),
        state = NetworkStatsState.ALL,
        metered = NetworkStatsMetered.ALL,
        roaming = NetworkStatsRoaming.ALL,
        defaultNetwork = null,
        connectivity = NetworkStatsConnectivity.ETHERNET,
        startEpochMillis = 0,
        endEpochMillis = 0,
        rxBytes = 1000,
        rxPackets = 1,
        txBytes = 2000,
        txPackets = 1,
    )

    @get:Rule()
    val metricsDbTestEnvironment: MetricsDbTestEnvironment = MetricsDbTestEnvironment().apply {
        highResMetricsEnabledValue = true
    }

    private val dao: CustomMetrics get() = metricsDbTestEnvironment.dao

    @Test fun `return null when disabled`() = runTest {
        fakeNetworkUsageSettings.dataSourceEnabled = false

        collector.collectAndRecord(time(2.hours.inWholeMilliseconds), time(1.hours.inWholeMilliseconds))

        dao.collectHeartbeat(endTimestampMs = 2.hours.inWholeMilliseconds).apply {
            assertThat(hourlyHeartbeatReport).prop(MetricReport::metrics).isEmpty()
        }
    }

    @Test fun `check total usage`() = runTest {
        coEvery {
            networkStatsQueries.getTotalUsage(start = any(), end = any(), connectivity = any())
        } coAnswers { call ->
            val connectivity = call.invocation.args[2] as NetworkStatsConnectivity
            fakeNetworkStatsSummary.copy(rxBytes = 10_000_000, connectivity = connectivity)
        }

        coEvery {
            networkStatsQueries.getUsageByApp(start = any(), end = any(), connectivity = any())
        } coAnswers { emptyMap() }

        collector.collectAndRecord(time(2.hours.inWholeMilliseconds), time(1.hours.inWholeMilliseconds))

        assertThat(dao.collectHeartbeat(endTimestampMs = 2.hours.inWholeMilliseconds)).all {
            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::metrics).containsOnly(
                "connectivity_recv_bytes" to JsonPrimitive(40_000_000.0),
                "connectivity_wifi_recv_bytes" to JsonPrimitive(10_000_000.0),
                "connectivity_mobile_recv_bytes" to JsonPrimitive(10_000_000.0),
                "connectivity_eth_recv_bytes" to JsonPrimitive(10_000_000.0),
                "connectivity_bt_recv_bytes" to JsonPrimitive(10_000_000.0),

                "connectivity_sent_bytes" to JsonPrimitive(8_000.0),
                "connectivity_wifi_sent_bytes" to JsonPrimitive(2_000.0),
                "connectivity_mobile_sent_bytes" to JsonPrimitive(2_000.0),
                "connectivity_eth_sent_bytes" to JsonPrimitive(2_000.0),
                "connectivity_bt_sent_bytes" to JsonPrimitive(2_000.0),
            )

            prop(CustomReport::hourlyHeartbeatReport).all {
                prop(MetricReport::internalMetrics).containsOnly(
                    "connectivity_comp_bort_recv_bytes" to JsonPrimitive(0.0),
                    "connectivity_comp_ota_recv_bytes" to JsonPrimitive(0.0),

                    "connectivity_comp_bort_sent_bytes" to JsonPrimitive(0.0),
                    "connectivity_comp_ota_sent_bytes" to JsonPrimitive(0.0),
                )

                prop(MetricReport::hrt).isNotNull()
                    .transform { hrt -> HighResTelemetry.decodeFromStream(hrt) }
                    .prop(HighResTelemetry::rollups)
                    .containsExactlyInAnyOrder(
                        rollup("connectivity_recv_bytes", 40_000_000.0),
                        rollup("connectivity_wifi_recv_bytes", 10_000_000.0),
                        rollup("connectivity_mobile_recv_bytes", 10_000_000.0),
                        rollup("connectivity_eth_recv_bytes", 10_000_000.0),
                        rollup("connectivity_bt_recv_bytes", 10_000_000.0),

                        rollup("connectivity_sent_bytes", 8_000.0),
                        rollup("connectivity_wifi_sent_bytes", 2_000.0),
                        rollup("connectivity_mobile_sent_bytes", 2_000.0),
                        rollup("connectivity_eth_sent_bytes", 2_000.0),
                        rollup("connectivity_bt_sent_bytes", 2_000.0),

                        rollup("connectivity_comp_bort_recv_bytes", 0.0, internal = true),
                        rollup("connectivity_comp_ota_recv_bytes", 0.0, internal = true),

                        rollup("connectivity_comp_bort_sent_bytes", 0.0, internal = true),
                        rollup("connectivity_comp_ota_sent_bytes", 0.0, internal = true),
                    )
            }
        }
    }

    @Test fun `check per app usage`() = runTest {
        coEvery {
            networkStatsQueries.getTotalUsage(start = any(), end = any(), connectivity = any())
        } coAnswers { null }

        coEvery {
            networkStatsQueries.getUsageByApp(start = any(), end = any(), connectivity = any())
        } coAnswers {
            val connectivity = call.invocation.args[2] as NetworkStatsConnectivity
            mapOf(10_000 to listOf(fakeNetworkStatsSummary.copy(txBytes = 10_000_000, connectivity = connectivity)))
        }

        coEvery {
            packageManagerClient.getPackageManagerReport()
        } coAnswers {
            PackageManagerReport(
                listOf(Package(id = "com.memfault.bort", userId = 10_000)),
            )
        }

        collector.collectAndRecord(time(2.hours.inWholeMilliseconds), time(1.hours.inWholeMilliseconds))

        assertThat(dao.collectHeartbeat(endTimestampMs = 2.hours.inWholeMilliseconds)).all {
            prop(CustomReport::hourlyHeartbeatReport).all {
                prop(MetricReport::metrics).containsOnly(
                    "connectivity_recv_bytes" to JsonPrimitive(0.0),
                    "connectivity_sent_bytes" to JsonPrimitive(0.0),
                )

                prop(MetricReport::internalMetrics).containsOnly(
                    "connectivity_comp_bort_recv_bytes" to JsonPrimitive(4_000.0),
                    "connectivity_comp_bort_eth_recv_bytes" to JsonPrimitive(1_000.0),
                    "connectivity_comp_bort_mobile_recv_bytes" to JsonPrimitive(1_000.0),
                    "connectivity_comp_bort_wifi_recv_bytes" to JsonPrimitive(1_000.0),
                    "connectivity_comp_bort_bt_recv_bytes" to JsonPrimitive(1_000.0),

                    "connectivity_comp_bort_sent_bytes" to JsonPrimitive(40_000_000.0),
                    "connectivity_comp_bort_eth_sent_bytes" to JsonPrimitive(10_000_000.0),
                    "connectivity_comp_bort_mobile_sent_bytes" to JsonPrimitive(10_000_000.0),
                    "connectivity_comp_bort_wifi_sent_bytes" to JsonPrimitive(10_000_000.0),
                    "connectivity_comp_bort_bt_sent_bytes" to JsonPrimitive(10_000_000.0),

                    "connectivity_comp_ota_recv_bytes" to JsonPrimitive(0.0),

                    "connectivity_comp_ota_sent_bytes" to JsonPrimitive(0.0),
                )

                prop(MetricReport::hrt).isNotNull()
                    .transform { hrt -> HighResTelemetry.decodeFromStream(hrt) }
                    .prop(HighResTelemetry::rollups)
                    .containsExactlyInAnyOrder(
                        rollup("connectivity_recv_bytes", 0.0),
                        rollup("connectivity_sent_bytes", 0.0),

                        rollup("connectivity_comp_com.memfault.bort_sent_bytes", 40_000_000.0),
                        rollup("connectivity_comp_com.memfault.bort_wifi_sent_bytes", 10_000_000.0),
                        rollup("connectivity_comp_com.memfault.bort_mobile_sent_bytes", 10_000_000.0),
                        rollup("connectivity_comp_com.memfault.bort_eth_sent_bytes", 10_000_000.0),
                        rollup("connectivity_comp_com.memfault.bort_bt_sent_bytes", 10_000_000.0),

                        rollup("connectivity_comp_bort_recv_bytes", 4_000.0, internal = true),
                        rollup("connectivity_comp_bort_wifi_recv_bytes", 1_000.0, internal = true),
                        rollup("connectivity_comp_bort_mobile_recv_bytes", 1_000.0, internal = true),
                        rollup("connectivity_comp_bort_eth_recv_bytes", 1_000.0, internal = true),
                        rollup("connectivity_comp_bort_bt_recv_bytes", 1_000.0, internal = true),

                        rollup("connectivity_comp_bort_sent_bytes", 40_000_000.0, internal = true),
                        rollup("connectivity_comp_bort_wifi_sent_bytes", 10_000_000.0, internal = true),
                        rollup("connectivity_comp_bort_mobile_sent_bytes", 10_000_000.0, internal = true),
                        rollup("connectivity_comp_bort_eth_sent_bytes", 10_000_000.0, internal = true),
                        rollup("connectivity_comp_bort_bt_sent_bytes", 10_000_000.0, internal = true),

                        rollup("connectivity_comp_ota_recv_bytes", 0.0, internal = true),

                        rollup("connectivity_comp_ota_sent_bytes", 0.0, internal = true),
                    )
            }
        }
    }

    @Test fun `always record total, ignore app usage below threshold`() = runTest {
        fakeNetworkUsageSettings.collectionReceiveThresholdKb = 3
        fakeNetworkUsageSettings.collectionTransmitThresholdKb = 3

        coEvery {
            networkStatsQueries.getTotalUsage(start = any(), end = any(), connectivity = any())
        } coAnswers { fakeNetworkStatsSummary.copy(rxBytes = 1) }

        coEvery {
            networkStatsQueries.getUsageByApp(start = any(), end = any(), connectivity = any())
        } coAnswers { call ->
            val connectivity = call.invocation.args[2] as NetworkStatsConnectivity
            mapOf(
                10_000 to listOf(
                    fakeNetworkStatsSummary.copy(
                        rxBytes = 2000,
                        txBytes = 5000,
                        connectivity = connectivity,
                    ),
                ),
                15_000 to listOf(
                    fakeNetworkStatsSummary.copy(
                        rxBytes = 4000,
                        txBytes = 2000,
                        connectivity = connectivity,
                    ),
                ),
            )
        }

        coEvery {
            packageManagerClient.getPackageManagerReport()
        } coAnswers {
            PackageManagerReport(
                listOf(
                    Package(id = "com.memfault.bort", userId = 10_000),
                    Package(id = "com.my.app", userId = 15_000),
                ),
            )
        }

        collector.collectAndRecord(time(2.hours.inWholeMilliseconds), time(1.hours.inWholeMilliseconds))

        assertThat(dao.collectHeartbeat(endTimestampMs = 2.hours.inWholeMilliseconds)).all {
            prop(CustomReport::hourlyHeartbeatReport).all {
                prop(MetricReport::metrics).containsOnly(
                    "connectivity_recv_bytes" to JsonPrimitive(4.0),
                    "connectivity_wifi_recv_bytes" to JsonPrimitive(1.0),
                    "connectivity_mobile_recv_bytes" to JsonPrimitive(1.0),
                    "connectivity_eth_recv_bytes" to JsonPrimitive(1.0),
                    "connectivity_bt_recv_bytes" to JsonPrimitive(1.0),

                    "connectivity_sent_bytes" to JsonPrimitive(8_000.0),
                    "connectivity_wifi_sent_bytes" to JsonPrimitive(2_000.0),
                    "connectivity_mobile_sent_bytes" to JsonPrimitive(2_000.0),
                    "connectivity_eth_sent_bytes" to JsonPrimitive(2_000.0),
                    "connectivity_bt_sent_bytes" to JsonPrimitive(2_000.0),
                )

                prop(MetricReport::internalMetrics).containsOnly(
                    "connectivity_comp_bort_recv_bytes" to JsonPrimitive(8_000.0),
                    "connectivity_comp_bort_eth_recv_bytes" to JsonPrimitive(2_000.0),
                    "connectivity_comp_bort_mobile_recv_bytes" to JsonPrimitive(2_000.0),
                    "connectivity_comp_bort_wifi_recv_bytes" to JsonPrimitive(2_000.0),
                    "connectivity_comp_bort_bt_recv_bytes" to JsonPrimitive(2_000.0),

                    "connectivity_comp_bort_sent_bytes" to JsonPrimitive(20_000.0),
                    "connectivity_comp_bort_eth_sent_bytes" to JsonPrimitive(5_000.0),
                    "connectivity_comp_bort_mobile_sent_bytes" to JsonPrimitive(5_000.0),
                    "connectivity_comp_bort_wifi_sent_bytes" to JsonPrimitive(5_000.0),
                    "connectivity_comp_bort_bt_sent_bytes" to JsonPrimitive(5_000.0),

                    "connectivity_comp_ota_recv_bytes" to JsonPrimitive(0.0),

                    "connectivity_comp_ota_sent_bytes" to JsonPrimitive(0.0),
                )

                prop(MetricReport::hrt).isNotNull()
                    .transform { hrt -> HighResTelemetry.decodeFromStream(hrt) }
                    .prop(HighResTelemetry::rollups)
                    .containsExactlyInAnyOrder(
                        rollup("connectivity_recv_bytes", 4.0),
                        rollup("connectivity_eth_recv_bytes", 1.0),
                        rollup("connectivity_wifi_recv_bytes", 1.0),
                        rollup("connectivity_mobile_recv_bytes", 1.0),
                        rollup("connectivity_bt_recv_bytes", 1.0),

                        rollup("connectivity_sent_bytes", 8_000.0),
                        rollup("connectivity_eth_sent_bytes", 2_000.0),
                        rollup("connectivity_wifi_sent_bytes", 2_000.0),
                        rollup("connectivity_mobile_sent_bytes", 2_000.0),
                        rollup("connectivity_bt_sent_bytes", 2_000.0),

                        rollup("connectivity_comp_bort_recv_bytes", 8_000.0, internal = true),
                        rollup("connectivity_comp_bort_wifi_recv_bytes", 2_000.0, internal = true),
                        rollup("connectivity_comp_bort_mobile_recv_bytes", 2_000.0, internal = true),
                        rollup("connectivity_comp_bort_eth_recv_bytes", 2_000.0, internal = true),
                        rollup("connectivity_comp_bort_bt_recv_bytes", 2_000.0, internal = true),

                        rollup("connectivity_comp_bort_sent_bytes", 20_000.0, internal = true),
                        rollup("connectivity_comp_bort_wifi_sent_bytes", 5_000.0, internal = true),
                        rollup("connectivity_comp_bort_mobile_sent_bytes", 5_000.0, internal = true),
                        rollup("connectivity_comp_bort_eth_sent_bytes", 5_000.0, internal = true),
                        rollup("connectivity_comp_bort_bt_sent_bytes", 5_000.0, internal = true),

                        rollup("connectivity_comp_ota_recv_bytes", 0.0, internal = true),

                        rollup("connectivity_comp_ota_sent_bytes", 0.0, internal = true),

                        rollup("connectivity_comp_com.memfault.bort_recv_bytes", 8_000.0),

                        rollup("connectivity_comp_com.memfault.bort_sent_bytes", 20_000.0),
                        rollup("connectivity_comp_com.memfault.bort_wifi_sent_bytes", 5_000.0),
                        rollup("connectivity_comp_com.memfault.bort_mobile_sent_bytes", 5_000.0),
                        rollup("connectivity_comp_com.memfault.bort_eth_sent_bytes", 5_000.0),
                        rollup("connectivity_comp_com.memfault.bort_bt_sent_bytes", 5_000.0),

                        rollup("connectivity_comp_com.my.app_sent_bytes", 8_000.0),

                        rollup("connectivity_comp_com.my.app_recv_bytes", value = 16_000.0),
                        rollup("connectivity_comp_com.my.app_wifi_recv_bytes", 4_000.0),
                        rollup("connectivity_comp_com.my.app_mobile_recv_bytes", 4_000.0),
                        rollup("connectivity_comp_com.my.app_eth_recv_bytes", 4_000.0),
                        rollup("connectivity_comp_com.my.app_bt_recv_bytes", 4_000.0),
                    )
            }
        }
    }

    @Test fun `bort and ota always track internal metrics`() = runTest {
        coEvery {
            networkStatsQueries.getTotalUsage(start = any(), end = any(), connectivity = any())
        } coAnswers { null }

        coEvery {
            networkStatsQueries.getUsageByApp(start = any(), end = any(), connectivity = any())
        } coAnswers {
            val connectivity = call.invocation.args[2] as NetworkStatsConnectivity
            mapOf(10_000 to listOf(fakeNetworkStatsSummary.copy(txBytes = 10_000_000, connectivity = connectivity)))
        }

        coEvery {
            packageManagerClient.getPackageManagerReport()
        } coAnswers {
            PackageManagerReport(
                listOf(Package(id = "com.memfault.bort", userId = 10_000)),
            )
        }

        collector.collectAndRecord(time(2.hours.inWholeMilliseconds), time(1.hours.inWholeMilliseconds))

        assertThat(dao.collectHeartbeat(endTimestampMs = 2.hours.inWholeMilliseconds)).all {
            prop(CustomReport::hourlyHeartbeatReport).all {
                prop(MetricReport::internalMetrics).containsOnly(
                    "connectivity_comp_bort_recv_bytes" to JsonPrimitive(4_000.0),
                    "connectivity_comp_bort_eth_recv_bytes" to JsonPrimitive(1_000.0),
                    "connectivity_comp_bort_mobile_recv_bytes" to JsonPrimitive(1_000.0),
                    "connectivity_comp_bort_wifi_recv_bytes" to JsonPrimitive(1_000.0),
                    "connectivity_comp_bort_bt_recv_bytes" to JsonPrimitive(1_000.0),

                    "connectivity_comp_bort_sent_bytes" to JsonPrimitive(40_000_000.0),
                    "connectivity_comp_bort_eth_sent_bytes" to JsonPrimitive(10_000_000.0),
                    "connectivity_comp_bort_mobile_sent_bytes" to JsonPrimitive(10_000_000.0),
                    "connectivity_comp_bort_wifi_sent_bytes" to JsonPrimitive(10_000_000.0),
                    "connectivity_comp_bort_bt_sent_bytes" to JsonPrimitive(10_000_000.0),

                    "connectivity_comp_ota_recv_bytes" to JsonPrimitive(0.0),

                    "connectivity_comp_ota_sent_bytes" to JsonPrimitive(0.0),
                )

                prop(MetricReport::hrt).isNotNull()
                    .transform { hrt -> HighResTelemetry.decodeFromStream(hrt) }
                    .prop(HighResTelemetry::rollups)
                    .containsExactlyInAnyOrder(
                        rollup("connectivity_recv_bytes", 0.0),
                        rollup("connectivity_sent_bytes", 0.0),

                        rollup("connectivity_comp_bort_recv_bytes", 4_000.0, internal = true),
                        rollup("connectivity_comp_bort_wifi_recv_bytes", 1_000.0, internal = true),
                        rollup("connectivity_comp_bort_mobile_recv_bytes", 1_000.0, internal = true),
                        rollup("connectivity_comp_bort_eth_recv_bytes", 1_000.0, internal = true),
                        rollup("connectivity_comp_bort_bt_recv_bytes", 1_000.0, internal = true),

                        rollup("connectivity_comp_bort_sent_bytes", 40_000_000.0, internal = true),
                        rollup("connectivity_comp_bort_wifi_sent_bytes", 10_000_000.0, internal = true),
                        rollup("connectivity_comp_bort_mobile_sent_bytes", 10_000_000.0, internal = true),
                        rollup("connectivity_comp_bort_eth_sent_bytes", 10_000_000.0, internal = true),
                        rollup("connectivity_comp_bort_bt_sent_bytes", 10_000_000.0, internal = true),

                        rollup("connectivity_comp_ota_recv_bytes", 0.0, internal = true),

                        rollup("connectivity_comp_ota_sent_bytes", 0.0, internal = true),

                        rollup("connectivity_comp_com.memfault.bort_sent_bytes", 40_000_000.0, internal = false),
                        rollup("connectivity_comp_com.memfault.bort_wifi_sent_bytes", 10_000_000.0, internal = false),
                        rollup("connectivity_comp_com.memfault.bort_mobile_sent_bytes", 10_000_000.0, internal = false),
                        rollup("connectivity_comp_com.memfault.bort_eth_sent_bytes", 10_000_000.0, internal = false),
                        rollup("connectivity_comp_com.memfault.bort_bt_sent_bytes", 10_000_000.0, internal = false),
                    )
            }
        }
    }

    @Test fun `query range invalid`() = runTest {
        collector.collectAndRecord(
            collectionTime = time(2.hours.inWholeMilliseconds),
            lastHeartbeatUptime = time(3.hours.inWholeMilliseconds),
        )

        assertThat(dao.collectHeartbeat(endTimestampMs = 4.hours.inWholeMilliseconds)).all {
            prop(CustomReport::hourlyHeartbeatReport).prop(MetricReport::metrics).isEmpty()
        }
    }

    @Test fun `check legacy metric keys`() = runTest {
        checkLegacyMetrics = true

        coEvery {
            networkStatsQueries.getTotalUsage(start = any(), end = any(), connectivity = any())
        } coAnswers { null }

        coEvery {
            networkStatsQueries.getUsageByApp(start = any(), end = any(), connectivity = any())
        } coAnswers {
            val connectivity = call.invocation.args[2] as NetworkStatsConnectivity
            mapOf(10_000 to listOf(fakeNetworkStatsSummary.copy(txBytes = 10_000_000, connectivity = connectivity)))
        }

        coEvery {
            packageManagerClient.getPackageManagerReport()
        } coAnswers {
            PackageManagerReport(
                listOf(Package(id = "com.memfault.bort", userId = 10_000)),
            )
        }

        collector.collectAndRecord(time(2.hours.inWholeMilliseconds), time(1.hours.inWholeMilliseconds))

        assertThat(dao.collectHeartbeat(endTimestampMs = 2.hours.inWholeMilliseconds)).all {
            prop(CustomReport::hourlyHeartbeatReport).all {
                prop(MetricReport::metrics).containsOnly(
                    "connectivity_recv_bytes" to JsonPrimitive(0.0),
                    "connectivity_sent_bytes" to JsonPrimitive(0.0),
                    "network.total.in.all.latest" to JsonPrimitive(0.0),
                    "network.total.out.all.latest" to JsonPrimitive(0.0),
                )

                prop(MetricReport::internalMetrics).containsOnly(
                    "connectivity_comp_bort_recv_bytes" to JsonPrimitive(4_000.0),
                    "connectivity_comp_bort_eth_recv_bytes" to JsonPrimitive(1_000.0),
                    "connectivity_comp_bort_mobile_recv_bytes" to JsonPrimitive(1_000.0),
                    "connectivity_comp_bort_wifi_recv_bytes" to JsonPrimitive(1_000.0),
                    "connectivity_comp_bort_bt_recv_bytes" to JsonPrimitive(1_000.0),

                    "network.app.in.all_bort" to JsonPrimitive(4.0),
                    "network.app.in.wifi_bort" to JsonPrimitive(1.0),
                    "network.app.in.mobile_bort" to JsonPrimitive(1.0),
                    "network.app.in.eth_bort" to JsonPrimitive(1.0),
                    "network.app.in.bt_bort" to JsonPrimitive(1.0),

                    "connectivity_comp_bort_sent_bytes" to JsonPrimitive(40_000_000.0),
                    "connectivity_comp_bort_eth_sent_bytes" to JsonPrimitive(10_000_000.0),
                    "connectivity_comp_bort_mobile_sent_bytes" to JsonPrimitive(10_000_000.0),
                    "connectivity_comp_bort_wifi_sent_bytes" to JsonPrimitive(10_000_000.0),
                    "connectivity_comp_bort_bt_sent_bytes" to JsonPrimitive(10_000_000.0),

                    "network.app.out.all_bort" to JsonPrimitive(40_000.0),
                    "network.app.out.wifi_bort" to JsonPrimitive(10_000.0),
                    "network.app.out.mobile_bort" to JsonPrimitive(10_000.0),
                    "network.app.out.eth_bort" to JsonPrimitive(10_000.0),
                    "network.app.out.bt_bort" to JsonPrimitive(10_000.0),

                    "connectivity_comp_ota_recv_bytes" to JsonPrimitive(0.0),

                    "network.app.in.all_ota" to JsonPrimitive(0.0),

                    "connectivity_comp_ota_sent_bytes" to JsonPrimitive(0.0),

                    "network.app.out.all_ota" to JsonPrimitive(0.0),
                )

                prop(MetricReport::hrt).isNotNull()
                    .transform { hrt -> HighResTelemetry.decodeFromStream(hrt) }
                    .prop(HighResTelemetry::rollups)
                    .containsExactlyInAnyOrder(
                        rollup("connectivity_recv_bytes", 0.0),
                        rollup("connectivity_sent_bytes", 0.0),

                        rollup("network.total.in.all", 0.0),
                        rollup("network.total.out.all", 0.0),

                        rollup("connectivity_comp_com.memfault.bort_sent_bytes", 40_000_000.0),
                        rollup("connectivity_comp_com.memfault.bort_wifi_sent_bytes", 10_000_000.0),
                        rollup("connectivity_comp_com.memfault.bort_mobile_sent_bytes", 10_000_000.0),
                        rollup("connectivity_comp_com.memfault.bort_eth_sent_bytes", 10_000_000.0),
                        rollup("connectivity_comp_com.memfault.bort_bt_sent_bytes", 10_000_000.0),

                        rollup("network.app.out.all_com.memfault.bort", 40_000.0),
                        rollup("network.app.out.wifi_com.memfault.bort", 10_000.0),
                        rollup("network.app.out.mobile_com.memfault.bort", 10_000.0),
                        rollup("network.app.out.eth_com.memfault.bort", 10_000.0),
                        rollup("network.app.out.bt_com.memfault.bort", 10_000.0),

                        rollup("connectivity_comp_bort_recv_bytes", 4_000.0, internal = true),
                        rollup("connectivity_comp_bort_wifi_recv_bytes", 1_000.0, internal = true),
                        rollup("connectivity_comp_bort_mobile_recv_bytes", 1_000.0, internal = true),
                        rollup("connectivity_comp_bort_eth_recv_bytes", 1_000.0, internal = true),
                        rollup("connectivity_comp_bort_bt_recv_bytes", 1_000.0, internal = true),

                        rollup("network.app.in.all_bort", 4.0, internal = true),
                        rollup("network.app.in.wifi_bort", 1.0, internal = true),
                        rollup("network.app.in.mobile_bort", 1.0, internal = true),
                        rollup("network.app.in.eth_bort", 1.0, internal = true),
                        rollup("network.app.in.bt_bort", 1.0, internal = true),

                        rollup("connectivity_comp_bort_sent_bytes", 40_000_000.0, internal = true),
                        rollup("connectivity_comp_bort_wifi_sent_bytes", 10_000_000.0, internal = true),
                        rollup("connectivity_comp_bort_mobile_sent_bytes", 10_000_000.0, internal = true),
                        rollup("connectivity_comp_bort_eth_sent_bytes", 10_000_000.0, internal = true),
                        rollup("connectivity_comp_bort_bt_sent_bytes", 10_000_000.0, internal = true),

                        rollup("network.app.out.all_bort", 40_000.0, internal = true),
                        rollup("network.app.out.wifi_bort", 10_000.0, internal = true),
                        rollup("network.app.out.mobile_bort", 10_000.0, internal = true),
                        rollup("network.app.out.eth_bort", 10_000.0, internal = true),
                        rollup("network.app.out.bt_bort", 10_000.0, internal = true),

                        rollup("connectivity_comp_ota_recv_bytes", 0.0, internal = true),

                        rollup("network.app.in.all_ota", 0.0, internal = true),

                        rollup("connectivity_comp_ota_sent_bytes", 0.0, internal = true),

                        rollup("network.app.out.all_ota", 0.0, internal = true),
                    )
            }
        }
    }

    private fun rollup(
        stringKey: String,
        value: Number,
        internal: Boolean = false,
    ) = HighResTelemetry.Rollup(
        metadata = RollupMetadata(
            stringKey = stringKey,
            metricType = MetricType.Gauge,
            dataType = DataType.DoubleType,
            internal = internal,
        ),
        data = listOf(
            Datum(
                t = 2.hours.inWholeMilliseconds,
                value = JsonPrimitive(value),
            ),
        ),
    )
}
