package com.memfault.bort.networkstats

import android.os.Process
import com.memfault.bort.BortBuildConfig
import com.memfault.bort.PackageManagerClient
import com.memfault.bort.metrics.HighResTelemetry
import com.memfault.bort.metrics.HighResTelemetry.DataType.DoubleType
import com.memfault.bort.metrics.HighResTelemetry.Datum
import com.memfault.bort.metrics.HighResTelemetry.MetricType.Gauge
import com.memfault.bort.metrics.HighResTelemetry.RollupMetadata
import com.memfault.bort.networkstats.NetworkStatsConnectivity.ETHERNET
import com.memfault.bort.networkstats.NetworkStatsConnectivity.MOBILE
import com.memfault.bort.networkstats.NetworkStatsConnectivity.WIFI
import com.memfault.bort.parsers.PackageManagerReport
import com.memfault.bort.settings.NetworkUsageSettings
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.time.CombinedTime
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import javax.inject.Inject
import kotlin.time.Duration

interface NetworkStatsCollector {
    suspend fun collect(
        collectionTime: CombinedTime,
        heartbeatInterval: Duration,
    ): NetworkStatsResult
}

data class NetworkStatsResult(
    val hrtRollup: Set<HighResTelemetry.Rollup>,
    val heartbeatMetrics: Map<String, JsonPrimitive>,
    val internalHeartbeatMetrics: Map<String, JsonPrimitive> = emptyMap(),
) {

    companion object {
        val EMPTY = NetworkStatsResult(hrtRollup = emptySet(), heartbeatMetrics = emptyMap())

        fun merge(vararg results: NetworkStatsResult): NetworkStatsResult {
            val hrtRollup = mutableSetOf<HighResTelemetry.Rollup>()
            val heartbeatMetrics = mutableMapOf<String, JsonPrimitive>()
            val internalHeartbeatMetrics = mutableMapOf<String, JsonPrimitive>()

            results.forEach { result ->
                hrtRollup.addAll(result.hrtRollup)
                heartbeatMetrics.putAll(result.heartbeatMetrics)
                internalHeartbeatMetrics.putAll(result.internalHeartbeatMetrics)
            }

            return NetworkStatsResult(
                hrtRollup = hrtRollup,
                heartbeatMetrics = heartbeatMetrics,
                internalHeartbeatMetrics = internalHeartbeatMetrics,
            )
        }
    }
}

@ContributesBinding(SingletonComponent::class)
class RealNetworkStatsCollector
@Inject constructor(
    private val bortBuildConfig: BortBuildConfig,
    private val lastNetworkStatsCollectionTimestamp: LastNetworkStatsCollectionTimestamp,
    private val networkStatsQueries: NetworkStatsQueries,
    private val packageManagerClient: PackageManagerClient,
    private val settingsProvider: SettingsProvider,
) : NetworkStatsCollector {

    override suspend fun collect(
        collectionTime: CombinedTime,
        heartbeatInterval: Duration,
    ): NetworkStatsResult {
        if (!settingsProvider.networkUsageSettings.dataSourceEnabled) {
            return NetworkStatsResult.EMPTY
        }

        val lastCollectionTimestamp = lastNetworkStatsCollectionTimestamp.getValue()

        val queryStartTime = if (lastCollectionTimestamp > 0) {
            Instant.ofEpochMilli(lastCollectionTimestamp)
        } else {
            collectionTime.minus(heartbeatInterval).timestamp
        }
        val queryEndTime = collectionTime.timestamp

        lastNetworkStatsCollectionTimestamp.setValue(queryEndTime.toEpochMilli())

        val networkUsageSettings = settingsProvider.networkUsageSettings

        val totalEthernetUsage = queryTotalUsageMetric(
            inMetric = TOTAL_ETHERNET_IN_METRIC,
            outMetric = TOTAL_ETHERNET_OUT_METRIC,
            networkUsageSettings = networkUsageSettings,
            connectivity = ETHERNET,
            queryStartTime = queryStartTime,
            queryEndTime = queryEndTime,
        )

        val totalMobileUsage = queryTotalUsageMetric(
            inMetric = TOTAL_MOBILE_IN_METRIC,
            outMetric = TOTAL_MOBILE_OUT_METRIC,
            networkUsageSettings = networkUsageSettings,
            connectivity = MOBILE,
            queryStartTime = queryStartTime,
            queryEndTime = queryEndTime,
        )

        val totalWifiUsage = queryTotalUsageMetric(
            inMetric = TOTAL_WIFI_IN_METRIC,
            outMetric = TOTAL_WIFI_OUT_METRIC,
            networkUsageSettings = networkUsageSettings,
            connectivity = WIFI,
            queryStartTime = queryStartTime,
            queryEndTime = queryEndTime,
        )

        val packageManagerReport = packageManagerClient.getPackageManagerReport()

        val perAppEthernetUsage = queryPerAppUsageMetric(
            bortBuildConfig = bortBuildConfig,
            packageManagerReport = packageManagerReport,
            networkUsageSettings = networkUsageSettings,
            connectivity = ETHERNET,
            queryStartTime = queryStartTime,
            queryEndTime = queryEndTime,
        )

        val perAppMobileUsage = queryPerAppUsageMetric(
            bortBuildConfig = bortBuildConfig,
            packageManagerReport = packageManagerReport,
            networkUsageSettings = networkUsageSettings,
            connectivity = MOBILE,
            queryStartTime = queryStartTime,
            queryEndTime = queryEndTime,
        )

        val perAppWifiUsage = queryPerAppUsageMetric(
            bortBuildConfig = bortBuildConfig,
            packageManagerReport = packageManagerReport,
            networkUsageSettings = networkUsageSettings,
            connectivity = WIFI,
            queryStartTime = queryStartTime,
            queryEndTime = queryEndTime,
        )

        return NetworkStatsResult.merge(
            totalEthernetUsage,
            totalMobileUsage,
            totalWifiUsage,
            perAppEthernetUsage,
            perAppMobileUsage,
            perAppWifiUsage,
        )
    }

    private suspend fun queryTotalUsageMetric(
        inMetric: String,
        outMetric: String,
        networkUsageSettings: NetworkUsageSettings,
        connectivity: NetworkStatsConnectivity,
        queryStartTime: Instant,
        queryEndTime: Instant,
    ): NetworkStatsResult {
        val usage =
            networkStatsQueries.getTotalUsage(start = queryStartTime, end = queryEndTime, connectivity = connectivity)

        if (usage == null) {
            return NetworkStatsResult.EMPTY
        }

        val hrtRollup = mutableSetOf<HighResTelemetry.Rollup>()
        val heartbeatMetrics = mutableMapOf<String, JsonPrimitive>()

        if (usage.rxKB >= networkUsageSettings.collectionReceiveThresholdKb) {
            heartbeatMetrics["$inMetric.latest"] = JsonPrimitive(usage.rxKB)
            hrtRollup += networkMetricHrtRollup(
                metricName = inMetric,
                metricValue = usage.rxKB,
                collectionTime = queryEndTime,
            )
        }

        if (usage.txKB >= networkUsageSettings.collectionTransmitThresholdKb) {
            heartbeatMetrics["$outMetric.latest"] = JsonPrimitive(usage.txKB)
            hrtRollup += networkMetricHrtRollup(
                metricName = outMetric,
                metricValue = usage.txKB,
                collectionTime = queryEndTime,
            )
        }

        return NetworkStatsResult(hrtRollup = hrtRollup, heartbeatMetrics = heartbeatMetrics)
    }

    private suspend fun queryPerAppUsageMetric(
        bortBuildConfig: BortBuildConfig,
        packageManagerReport: PackageManagerReport,
        networkUsageSettings: NetworkUsageSettings,
        connectivity: NetworkStatsConnectivity,
        queryStartTime: Instant,
        queryEndTime: Instant,
    ): NetworkStatsResult {
        val usagesByApp =
            networkStatsQueries.getUsageByApp(start = queryStartTime, end = queryEndTime, connectivity = connectivity)

        // Converts each package UID to a readable package string, and then sums up the network usage by that
        // package string instead of just the UID. Several different packages may share the same UID, or we might
        // map several UIDs to "unknown", so we can't just assume the UID to package string is unique.
        val usageByPackage = mutableMapOf<String, NetworkStatsUsage>()
        for ((uid, summaries) in usagesByApp) {
            val packageName = packageManagerReport.uuidToName(uid)
            val totalRxKB = summaries.sumOf { it.rxKB }
            val totalTxKB = summaries.sumOf { it.txKB }

            val packageUsageOrNull = usageByPackage[packageName]
            if (packageUsageOrNull != null) {
                usageByPackage[packageName] = NetworkStatsUsage(
                    rxKB = packageUsageOrNull.rxKB + totalRxKB,
                    txKB = packageUsageOrNull.txKB + totalTxKB,
                )
            } else {
                usageByPackage[packageName] = NetworkStatsUsage(rxKB = totalRxKB, txKB = totalTxKB)
            }
        }

        val hrtRollup = mutableSetOf<HighResTelemetry.Rollup>()
        val internalHeartbeatMetrics = mutableMapOf<String, JsonPrimitive>()

        val bortUsage = usageByPackage[bortBuildConfig.bortAppId]
        val otaUsage = usageByPackage[bortBuildConfig.otaAppId]

        fun rollupInternalUsage(inName: String, outName: String, usage: NetworkStatsUsage?) {
            hrtRollup += networkMetricHrtRollup(
                metricName = inName,
                metricValue = usage?.rxKB ?: 0.0,
                collectionTime = queryEndTime,
                internal = true,
            )
            hrtRollup += networkMetricHrtRollup(
                metricName = outName,
                metricValue = usage?.txKB ?: 0.0,
                collectionTime = queryEndTime,
                internal = true,
            )
            internalHeartbeatMetrics[inName] = JsonPrimitive(usage?.rxKB ?: 0.0)
            internalHeartbeatMetrics[outName] = JsonPrimitive(usage?.txKB ?: 0.0)
        }

        rollupInternalUsage(
            inName = appInMetricName(connectivity, "bort"),
            outName = appOutMetricName(connectivity, "bort"),
            usage = bortUsage,
        )

        rollupInternalUsage(
            inName = appInMetricName(connectivity, "ota"),
            outName = appOutMetricName(connectivity, "ota"),
            usage = otaUsage,
        )

        for ((packageName, usage) in usageByPackage) {
            // Logs the package if it exceeds the specified threshold.

            if (usage.rxKB >= networkUsageSettings.collectionReceiveThresholdKb) {
                hrtRollup += networkMetricHrtRollup(
                    metricName = appInMetricName(connectivity, packageName),
                    metricValue = usage.rxKB,
                    collectionTime = queryEndTime,
                )
            }

            if (usage.txKB >= networkUsageSettings.collectionTransmitThresholdKb) {
                hrtRollup += networkMetricHrtRollup(
                    metricName = appOutMetricName(connectivity, packageName),
                    metricValue = usage.txKB,
                    collectionTime = queryEndTime,
                )
            }
        }

        return NetworkStatsResult(
            hrtRollup = hrtRollup,
            heartbeatMetrics = emptyMap(),
            internalHeartbeatMetrics = internalHeartbeatMetrics,
        )
    }

    private fun PackageManagerReport.uuidToName(uid: Int): String = when (uid) {
        in Process.FIRST_APPLICATION_UID..Process.LAST_APPLICATION_UID ->
            packages.lastOrNull { it.userId == uid }?.id ?: "unknown"
        // Every system UID's usage is assigned to "android"
        else -> "android"
    }

    private data class NetworkStatsUsage(
        val rxKB: Long,
        val txKB: Long,
    )

    private fun networkMetricHrtRollup(
        metricName: String,
        metricValue: Number,
        collectionTime: Instant,
        internal: Boolean = false,
    ) = HighResTelemetry.Rollup(
        metadata = RollupMetadata(
            stringKey = metricName,
            metricType = Gauge,
            dataType = DoubleType,
            internal = internal,
        ),
        data = listOf(Datum(t = collectionTime.toEpochMilli(), value = JsonPrimitive(metricValue))),
    )

    companion object {
        private fun appInMetricName(
            connectivity: NetworkStatsConnectivity,
            packageName: String,
        ) = "network.app.in.${connectivity.shortName}_$packageName"

        private fun appOutMetricName(
            connectivity: NetworkStatsConnectivity,
            packageName: String,
        ) = "network.app.out.${connectivity.shortName}_$packageName"

        private const val TOTAL_ETHERNET_IN_METRIC = "network.total.in.eth"
        private const val TOTAL_ETHERNET_OUT_METRIC = "network.total.out.eth"
        private const val TOTAL_MOBILE_IN_METRIC = "network.total.in.mobile"
        private const val TOTAL_MOBILE_OUT_METRIC = "network.total.out.mobile"
        private const val TOTAL_WIFI_IN_METRIC = "network.total.in.wifi"
        private const val TOTAL_WIFI_OUT_METRIC = "network.total.out.wifi"
    }
}
