package com.memfault.bort.networkstats

import assertk.Assert
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import com.memfault.bort.PackageManagerClient
import com.memfault.bort.makeFakeSharedPreferences
import com.memfault.bort.metrics.HighResTelemetry
import com.memfault.bort.metrics.HighResTelemetry.DataType
import com.memfault.bort.metrics.HighResTelemetry.Datum
import com.memfault.bort.metrics.HighResTelemetry.MetricType
import com.memfault.bort.metrics.HighResTelemetry.RollupMetadata
import com.memfault.bort.metrics.SignificantApp
import com.memfault.bort.metrics.SignificantAppsProvider
import com.memfault.bort.parsers.Package
import com.memfault.bort.parsers.PackageManagerReport
import com.memfault.bort.settings.NetworkUsageSettings
import com.memfault.bort.time.CombinedTime
import com.memfault.bort.time.boxed
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

private fun <K, V> Assert<Map<K, V>>.containsExactlyInAnyOrder(vararg pairs: Pair<K, V>) =
    transform { m -> m.map { (k, v) -> k to v } }
        .containsExactlyInAnyOrder(*pairs)

class NetworkStatsCollectorTest {
    private val fakeSharedPreferences = makeFakeSharedPreferences()
    private val lastNetworkStatsCollectionTimestamp = LastNetworkStatsCollectionTimestamp(fakeSharedPreferences)
    private val networkStatsQueries = mockk<NetworkStatsQueries>()
    private val packageManagerClient = mockk<PackageManagerClient> {
        coEvery { getPackageManagerReport() } answers { PackageManagerReport() }
    }

    private val fakeNetworkUsageSettings = object : NetworkUsageSettings {
        override var dataSourceEnabled: Boolean = true
        override var collectionReceiveThresholdKb: Long = 1000
        override var collectionTransmitThresholdKb: Long = 1000
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
        uptime = Duration.ZERO.boxed(),
        elapsedRealtime = Duration.ZERO.boxed(),
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

    @Test fun `return EMPTY when disabled`() = runTest {
        fakeNetworkUsageSettings.dataSourceEnabled = false

        val result = collector.collect(time(2.hours.inWholeMilliseconds), 1.hours)

        assert(result == NetworkStatsResult.EMPTY)
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

        val result = collector.collect(time(2.hours.inWholeMilliseconds), 1.hours)

        assertThat(result.heartbeatMetrics).containsExactlyInAnyOrder(
            "network.total.in.all.latest" to JsonPrimitive(40_000L),
            "network.total.in.wifi.latest" to JsonPrimitive(10_000L),
            "network.total.in.mobile.latest" to JsonPrimitive(10_000L),
            "network.total.in.eth.latest" to JsonPrimitive(10_000L),
            "network.total.in.bt.latest" to JsonPrimitive(10_000L),

            "network.total.out.all.latest" to JsonPrimitive(8L),
            "network.total.out.wifi.latest" to JsonPrimitive(2L),
            "network.total.out.mobile.latest" to JsonPrimitive(2L),
            "network.total.out.eth.latest" to JsonPrimitive(2L),
            "network.total.out.bt.latest" to JsonPrimitive(2L),
        )

        assertThat(result.internalHeartbeatMetrics).containsExactlyInAnyOrder(
            "network.app.in.all_bort" to JsonPrimitive(0L),
            "network.app.in.all_ota" to JsonPrimitive(0L),

            "network.app.out.all_bort" to JsonPrimitive(0L),
            "network.app.out.all_ota" to JsonPrimitive(0L),
        )

        assertThat(result.hrtRollup).containsExactlyInAnyOrder(
            rollup("network.total.in.all", 40_000L),
            rollup("network.total.in.wifi", 10_000L),
            rollup("network.total.in.mobile", 10_000L),
            rollup("network.total.in.eth", 10_000L),
            rollup("network.total.in.bt", 10_000L),

            rollup("network.total.out.all", 8L),
            rollup("network.total.out.wifi", 2L),
            rollup("network.total.out.mobile", 2L),
            rollup("network.total.out.eth", 2L),
            rollup("network.total.out.bt", 2L),

            rollup("network.app.in.all_bort", 0L, internal = true),
            rollup("network.app.out.all_bort", 0L, internal = true),

            rollup("network.app.in.all_ota", 0L, internal = true),
            rollup("network.app.out.all_ota", 0L, internal = true),
        )
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

        val result = collector.collect(time(2.hours.inWholeMilliseconds), 1.hours)

        assertThat(result.heartbeatMetrics).containsExactlyInAnyOrder(
            "network.total.in.all.latest" to JsonPrimitive(0L),
            "network.total.out.all.latest" to JsonPrimitive(0L),
        )

        assertThat(result.internalHeartbeatMetrics).containsExactlyInAnyOrder(
            "network.app.in.all_bort" to JsonPrimitive(4L),
            "network.app.in.wifi_bort" to JsonPrimitive(1L),
            "network.app.in.mobile_bort" to JsonPrimitive(1L),
            "network.app.in.eth_bort" to JsonPrimitive(1L),
            "network.app.in.bt_bort" to JsonPrimitive(1L),

            "network.app.out.all_bort" to JsonPrimitive(40_000L),
            "network.app.out.wifi_bort" to JsonPrimitive(10_000L),
            "network.app.out.mobile_bort" to JsonPrimitive(10_000L),
            "network.app.out.eth_bort" to JsonPrimitive(10_000L),
            "network.app.out.bt_bort" to JsonPrimitive(10_000L),

            "network.app.in.all_ota" to JsonPrimitive(0L),

            "network.app.out.all_ota" to JsonPrimitive(0L),
        )

        assertThat(result.hrtRollup).containsExactlyInAnyOrder(
            rollup("network.total.in.all", 0L),
            rollup("network.total.out.all", 0L),

            rollup("network.app.out.all_com.memfault.bort", 40_000L),
            rollup("network.app.out.wifi_com.memfault.bort", 10_000L),
            rollup("network.app.out.mobile_com.memfault.bort", 10_000L),
            rollup("network.app.out.eth_com.memfault.bort", 10_000L),
            rollup("network.app.out.bt_com.memfault.bort", 10_000L),

            rollup("network.app.in.all_bort", 4L, internal = true),
            rollup("network.app.in.wifi_bort", 1L, internal = true),
            rollup("network.app.in.mobile_bort", 1L, internal = true),
            rollup("network.app.in.eth_bort", 1L, internal = true),
            rollup("network.app.in.bt_bort", 1L, internal = true),

            rollup("network.app.out.all_bort", 40_000L, internal = true),
            rollup("network.app.out.wifi_bort", 10_000L, internal = true),
            rollup("network.app.out.mobile_bort", 10_000L, internal = true),
            rollup("network.app.out.eth_bort", 10_000L, internal = true),
            rollup("network.app.out.bt_bort", 10_000L, internal = true),

            rollup("network.app.in.all_ota", 0L, internal = true),

            rollup("network.app.out.all_ota", 0L, internal = true),
        )
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

        val result = collector.collect(time(2.hours.inWholeMilliseconds), 1.hours)

        assertThat(result.heartbeatMetrics).containsExactlyInAnyOrder(
            "network.total.in.all.latest" to JsonPrimitive(0L),
            "network.total.in.wifi.latest" to JsonPrimitive(0L),
            "network.total.in.mobile.latest" to JsonPrimitive(0L),
            "network.total.in.eth.latest" to JsonPrimitive(0L),
            "network.total.in.bt.latest" to JsonPrimitive(0L),

            "network.total.out.all.latest" to JsonPrimitive(8L),
            "network.total.out.wifi.latest" to JsonPrimitive(2L),
            "network.total.out.mobile.latest" to JsonPrimitive(2L),
            "network.total.out.eth.latest" to JsonPrimitive(2L),
            "network.total.out.bt.latest" to JsonPrimitive(2L),
        )

        assertThat(result.internalHeartbeatMetrics).containsExactlyInAnyOrder(
            "network.app.in.all_bort" to JsonPrimitive(8L),
            "network.app.in.wifi_bort" to JsonPrimitive(2L),
            "network.app.in.mobile_bort" to JsonPrimitive(2L),
            "network.app.in.eth_bort" to JsonPrimitive(2L),
            "network.app.in.bt_bort" to JsonPrimitive(2L),

            "network.app.out.all_bort" to JsonPrimitive(20L),
            "network.app.out.wifi_bort" to JsonPrimitive(5L),
            "network.app.out.mobile_bort" to JsonPrimitive(5L),
            "network.app.out.eth_bort" to JsonPrimitive(5L),
            "network.app.out.bt_bort" to JsonPrimitive(5L),

            "network.app.in.all_ota" to JsonPrimitive(0L),

            "network.app.out.all_ota" to JsonPrimitive(0L),
        )

        assertThat(result.hrtRollup).containsExactlyInAnyOrder(
            rollup("network.total.in.all", 0L),
            rollup("network.total.in.eth", 0L),
            rollup("network.total.in.wifi", 0L),
            rollup("network.total.in.mobile", 0L),
            rollup("network.total.in.bt", 0L),

            rollup("network.total.out.all", 8L),
            rollup("network.total.out.eth", 2L),
            rollup("network.total.out.wifi", 2L),
            rollup("network.total.out.mobile", 2L),
            rollup("network.total.out.bt", 2L),

            rollup("network.app.in.all_bort", 8L, internal = true),
            rollup("network.app.in.wifi_bort", 2L, internal = true),
            rollup("network.app.in.mobile_bort", 2L, internal = true),
            rollup("network.app.in.eth_bort", 2L, internal = true),
            rollup("network.app.in.bt_bort", 2L, internal = true),

            rollup("network.app.out.all_bort", 20L, internal = true),
            rollup("network.app.out.wifi_bort", 5L, internal = true),
            rollup("network.app.out.mobile_bort", 5L, internal = true),
            rollup("network.app.out.eth_bort", 5L, internal = true),
            rollup("network.app.out.bt_bort", 5L, internal = true),

            rollup("network.app.in.all_ota", 0L, internal = true),

            rollup("network.app.out.all_ota", 0L, internal = true),

            rollup("network.app.in.all_com.memfault.bort", 8L),

            rollup("network.app.out.all_com.memfault.bort", 20L),
            rollup("network.app.out.wifi_com.memfault.bort", 5L),
            rollup("network.app.out.mobile_com.memfault.bort", 5L),
            rollup("network.app.out.eth_com.memfault.bort", 5L),
            rollup("network.app.out.bt_com.memfault.bort", 5L),

            rollup("network.app.out.all_com.my.app", 8L),

            rollup("network.app.in.all_com.my.app", 16L),
            rollup("network.app.in.wifi_com.my.app", 4L),
            rollup("network.app.in.mobile_com.my.app", 4L),
            rollup("network.app.in.eth_com.my.app", 4L),
            rollup("network.app.in.bt_com.my.app", 4L),
        )
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

        val result = collector.collect(time(2.hours.inWholeMilliseconds), 1.hours)

        assertThat(result.internalHeartbeatMetrics).containsExactlyInAnyOrder(
            "network.app.in.all_bort" to JsonPrimitive(4L),
            "network.app.in.eth_bort" to JsonPrimitive(1L),
            "network.app.in.mobile_bort" to JsonPrimitive(1L),
            "network.app.in.wifi_bort" to JsonPrimitive(1L),
            "network.app.in.bt_bort" to JsonPrimitive(1L),

            "network.app.out.all_bort" to JsonPrimitive(40_000L),
            "network.app.out.eth_bort" to JsonPrimitive(10_000L),
            "network.app.out.mobile_bort" to JsonPrimitive(10_000L),
            "network.app.out.wifi_bort" to JsonPrimitive(10_000L),
            "network.app.out.bt_bort" to JsonPrimitive(10_000L),

            "network.app.in.all_ota" to JsonPrimitive(0L),

            "network.app.out.all_ota" to JsonPrimitive(0L),
        )

        assertThat(result.hrtRollup).containsExactlyInAnyOrder(
            rollup("network.total.in.all", 0L),
            rollup("network.total.out.all", 0L),

            rollup("network.app.in.all_bort", 4L, internal = true),
            rollup("network.app.in.wifi_bort", 1L, internal = true),
            rollup("network.app.in.mobile_bort", 1L, internal = true),
            rollup("network.app.in.eth_bort", 1L, internal = true),
            rollup("network.app.in.bt_bort", 1L, internal = true),

            rollup("network.app.out.all_bort", 40_000L, internal = true),
            rollup("network.app.out.wifi_bort", 10_000L, internal = true),
            rollup("network.app.out.mobile_bort", 10_000L, internal = true),
            rollup("network.app.out.eth_bort", 10_000L, internal = true),
            rollup("network.app.out.bt_bort", 10_000L, internal = true),

            rollup("network.app.in.all_ota", 0L, internal = true),

            rollup("network.app.out.all_ota", 0L, internal = true),

            rollup("network.app.out.all_com.memfault.bort", 40_000L, internal = false),
            rollup("network.app.out.wifi_com.memfault.bort", 10_000L, internal = false),
            rollup("network.app.out.mobile_com.memfault.bort", 10_000L, internal = false),
            rollup("network.app.out.eth_com.memfault.bort", 10_000L, internal = false),
            rollup("network.app.out.bt_com.memfault.bort", 10_000L, internal = false),
        )
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

fun cartesianProduct(vararg lists: List<*>): Sequence<List<*>> =
    lists.asSequence()
        .fold(sequenceOf(emptyList<Any?>())) { acc, running ->
            acc.flatMap { list -> running.map { element -> list + element } }
        }
