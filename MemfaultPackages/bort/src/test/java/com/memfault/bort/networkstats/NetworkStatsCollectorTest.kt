package com.memfault.bort.networkstats

import com.memfault.bort.BortBuildConfig
import com.memfault.bort.PackageManagerClient
import com.memfault.bort.makeFakeSharedPreferences
import com.memfault.bort.metrics.HighResTelemetry.DataType
import com.memfault.bort.metrics.HighResTelemetry.MetricType
import com.memfault.bort.parsers.Package
import com.memfault.bort.parsers.PackageManagerReport
import com.memfault.bort.settings.NetworkUsageSettings
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.time.CombinedTime
import com.memfault.bort.time.boxed
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.long
import org.junit.Test
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

class NetworkStatsCollectorTest {

    private val bortBuildConfig = object : BortBuildConfig {
        override val bortAppId: String = "com.memfault.bort"
        override val otaAppId: String? = null
    }
    private val fakeSharedPreferences = makeFakeSharedPreferences()
    private val lastNetworkStatsCollectionTimestamp = LastNetworkStatsCollectionTimestamp(fakeSharedPreferences)
    private val networkStatsQueries = mockk<NetworkStatsQueries>()
    private val packageManagerClient = mockk<PackageManagerClient> {
        coEvery { getPackageManagerReport() } answers { PackageManagerReport() }
    }

    private val fakeNetworkUsageSettings = object : NetworkUsageSettings {
        override var dataSourceEnabled: Boolean = true
        override val collectionReceiveThresholdKb: Long = 1000
        override val collectionTransmitThresholdKb: Long = 1000
    }
    private val settingsProvider = mockk<SettingsProvider> {
        every { networkUsageSettings } answers { fakeNetworkUsageSettings }
    }

    private val collector = RealNetworkStatsCollector(
        bortBuildConfig = bortBuildConfig,
        lastNetworkStatsCollectionTimestamp = lastNetworkStatsCollectionTimestamp,
        networkStatsQueries = networkStatsQueries,
        packageManagerClient = packageManagerClient,
        settingsProvider = settingsProvider,
    )

    private val perAppStringKeys = cartesianProduct(
        listOf("eth", "mobile", "wifi"),
        listOf("in", "out"),
        listOf("bort", "ota"),
    )
        .map { (connectivity, transfer, app) -> "network.app.$transfer.${connectivity}_$app" }

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
        txBytes = 1000,
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

        assert(result.hrtRollup.size == 15) { result.hrtRollup }
        assert(result.heartbeatMetrics.size == 3) { result.heartbeatMetrics }
        assert(result.internalHeartbeatMetrics.size == 12) { result.internalHeartbeatMetrics }

        val hrtStringKeys = perAppStringKeys +
            listOf("eth", "mobile", "wifi").map { "network.total.in.$it" }

        assert(result.hrtRollup.map { it.metadata.stringKey }.containsAll(hrtStringKeys.toList()))

        result.hrtRollup.forEach { rollup ->
            assert(rollup.metadata.dataType == DataType.DoubleType) { rollup }
            assert(rollup.metadata.metricType == MetricType.Gauge) { rollup }
            assert(rollup.metadata.internal == (rollup.metadata.stringKey in perAppStringKeys)) { rollup }
        }

        result.heartbeatMetrics.forEach { (metricName, metricValue) ->
            assert(metricName.startsWith("network.total.in.")) { metricName }
            assert(metricName.endsWith(".latest")) { metricName }
            assert(metricValue.long == 10_000L) { metricValue }
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

        val result = collector.collect(time(2.hours.inWholeMilliseconds), 1.hours)

        assert(result.hrtRollup.size == 15) { result.hrtRollup }
        assert(result.heartbeatMetrics.isEmpty()) { result.heartbeatMetrics }
        assert(result.internalHeartbeatMetrics.size == 12) { result.internalHeartbeatMetrics }

        val hrtStringKeys = perAppStringKeys +
            listOf("eth", "mobile", "wifi").map { "network.app.out.${it}_com.memfault.bort" }

        assert(result.hrtRollup.map { it.metadata.stringKey }.containsAll(hrtStringKeys.toList()))

        result.hrtRollup.forEach { rollup ->
            assert(rollup.metadata.dataType == DataType.DoubleType) { rollup }
            assert(rollup.metadata.metricType == MetricType.Gauge) { rollup }
            assert(rollup.metadata.internal == rollup.metadata.stringKey in perAppStringKeys) { rollup }
        }
    }

    @Test fun `ignore usage if below threshold`() = runTest {
        coEvery {
            networkStatsQueries.getTotalUsage(start = any(), end = any(), connectivity = any())
        } coAnswers { fakeNetworkStatsSummary.copy(rxBytes = 1) }

        coEvery {
            networkStatsQueries.getUsageByApp(start = any(), end = any(), connectivity = any())
        } coAnswers { call ->
            val connectivity = call.invocation.args[2] as NetworkStatsConnectivity
            mapOf(10_000 to listOf(fakeNetworkStatsSummary.copy(rxBytes = 1, connectivity = connectivity)))
        }

        val result = collector.collect(time(2.hours.inWholeMilliseconds), 1.hours)

        assert(result.hrtRollup.size == 12) { result.hrtRollup }
        assert(result.heartbeatMetrics.isEmpty()) { result.heartbeatMetrics }
        assert(result.internalHeartbeatMetrics.size == 12) { result.internalHeartbeatMetrics }

        assert(result.hrtRollup.map { it.metadata.stringKey }.containsAll(perAppStringKeys.toList()))
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

        val result = collector.collect(time(1.hours.inWholeMilliseconds), 1.hours)

        assert(result.hrtRollup.size == 15) { result.hrtRollup }
        assert(result.heartbeatMetrics.isEmpty()) { result.heartbeatMetrics }
        assert(result.internalHeartbeatMetrics.size == 12) { result.internalHeartbeatMetrics }

        val hrtStringKeys = perAppStringKeys +
            listOf("eth", "mobile", "wifi").map { "network.app.out.${it}_com.memfault.bort" }

        assert(result.hrtRollup.map { it.metadata.stringKey }.containsAll(hrtStringKeys.toList()))

        result.hrtRollup.forEach { rollup ->
            assert(rollup.metadata.dataType == DataType.DoubleType) { rollup }
            assert(rollup.metadata.metricType == MetricType.Gauge) { rollup }
            assert(rollup.metadata.internal == rollup.metadata.stringKey in perAppStringKeys) { rollup }
        }

        assert(result.internalHeartbeatMetrics["network.app.in.eth_bort"]?.doubleOrNull == 1.0) { result }
        assert(result.internalHeartbeatMetrics["network.app.in.mobile_bort"]?.doubleOrNull == 1.0) { result }
        assert(result.internalHeartbeatMetrics["network.app.in.wifi_bort"]?.doubleOrNull == 1.0) { result }

        assert(result.internalHeartbeatMetrics["network.app.out.eth_bort"]?.doubleOrNull == 10_000.0) { result }
        assert(result.internalHeartbeatMetrics["network.app.out.mobile_bort"]?.doubleOrNull == 10_000.0) { result }
        assert(result.internalHeartbeatMetrics["network.app.out.wifi_bort"]?.doubleOrNull == 10_000.0) { result }

        assert(result.internalHeartbeatMetrics["network.app.in.eth_ota"]?.doubleOrNull == 0.0) { result }
        assert(result.internalHeartbeatMetrics["network.app.in.mobile_ota"]?.doubleOrNull == 0.0) { result }
        assert(result.internalHeartbeatMetrics["network.app.in.wifi_ota"]?.doubleOrNull == 0.0) { result }

        assert(result.internalHeartbeatMetrics["network.app.out.eth_ota"]?.doubleOrNull == 0.0) { result }
        assert(result.internalHeartbeatMetrics["network.app.out.mobile_ota"]?.doubleOrNull == 0.0) { result }
        assert(result.internalHeartbeatMetrics["network.app.out.wifi_ota"]?.doubleOrNull == 0.0) { result }
    }
}

fun cartesianProduct(vararg lists: List<*>): Sequence<List<*>> =
    lists.asSequence()
        .fold(sequenceOf(emptyList<Any?>())) { acc, running ->
            acc.flatMap { list -> running.map { element -> list + element } }
        }
