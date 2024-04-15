package com.memfault.bort.networkstats

import android.os.Process
import com.memfault.bort.PackageManagerClient
import com.memfault.bort.metrics.HighResTelemetry
import com.memfault.bort.metrics.HighResTelemetry.DataType.DoubleType
import com.memfault.bort.metrics.HighResTelemetry.Datum
import com.memfault.bort.metrics.HighResTelemetry.MetricType.Gauge
import com.memfault.bort.metrics.HighResTelemetry.RollupMetadata
import com.memfault.bort.metrics.SignificantAppsProvider
import com.memfault.bort.networkstats.NetworkStatsConnectivity.BLUETOOTH
import com.memfault.bort.networkstats.NetworkStatsConnectivity.ETHERNET
import com.memfault.bort.networkstats.NetworkStatsConnectivity.MOBILE
import com.memfault.bort.networkstats.NetworkStatsConnectivity.WIFI
import com.memfault.bort.parsers.PackageManagerReport
import com.memfault.bort.settings.NetworkUsageSettings
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
    val internalHeartbeatMetrics: Map<String, JsonPrimitive>,
) {

    companion object {
        val EMPTY = NetworkStatsResult(
            hrtRollup = emptySet(),
            heartbeatMetrics = emptyMap(),
            internalHeartbeatMetrics = emptyMap(),
        )

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
    private val lastNetworkStatsCollectionTimestamp: LastNetworkStatsCollectionTimestamp,
    private val networkStatsQueries: NetworkStatsQueries,
    private val packageManagerClient: PackageManagerClient,
    private val networkUsageSettings: NetworkUsageSettings,
    private val significantAppsProvider: SignificantAppsProvider,
) : NetworkStatsCollector {

    override suspend fun collect(
        collectionTime: CombinedTime,
        heartbeatInterval: Duration,
    ): NetworkStatsResult {
        if (!networkUsageSettings.dataSourceEnabled) {
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

        val ethernetUsage =
            networkStatsQueries.getTotalUsage(start = queryStartTime, end = queryEndTime, connectivity = ETHERNET)

        val mobileUsage =
            networkStatsQueries.getTotalUsage(start = queryStartTime, end = queryEndTime, connectivity = MOBILE)

        val wifiUsage =
            networkStatsQueries.getTotalUsage(start = queryStartTime, end = queryEndTime, connectivity = WIFI)

        val bluetoothUsage =
            networkStatsQueries.getTotalUsage(start = queryStartTime, end = queryEndTime, connectivity = BLUETOOTH)

        val totalEthernetUsage = recordTotalUsageMetric(
            inMetric = TOTAL_ETHERNET_IN_METRIC,
            outMetric = TOTAL_ETHERNET_OUT_METRIC,
            usage = ethernetUsage,
            queryEndTime = queryEndTime,
        )

        val totalMobileUsage = recordTotalUsageMetric(
            inMetric = TOTAL_MOBILE_IN_METRIC,
            outMetric = TOTAL_MOBILE_OUT_METRIC,
            usage = mobileUsage,
            queryEndTime = queryEndTime,
        )

        val totalWifiUsage = recordTotalUsageMetric(
            inMetric = TOTAL_WIFI_IN_METRIC,
            outMetric = TOTAL_WIFI_OUT_METRIC,
            usage = wifiUsage,
            queryEndTime = queryEndTime,
        )

        val totalBluetoothUsage = recordTotalUsageMetric(
            inMetric = TOTAL_BLUETOOTH_IN_METRIC,
            outMetric = TOTAL_BLUETOOTH_OUT_METRIC,
            usage = bluetoothUsage,
            queryEndTime = queryEndTime,
        )

        val totalUsages = listOf(wifiUsage, ethernetUsage, mobileUsage, bluetoothUsage)

        val totalAllUsage = recordTotalUsageMetric(
            inMetric = TOTAL_ALL_IN_METRIC,
            outMetric = TOTAL_ALL_OUT_METRIC,
            rxKB = totalUsages.map { it?.rxKB }.sumOf { it ?: 0L },
            txKB = totalUsages.map { it?.txKB }.sumOf { it ?: 0L },
            queryEndTime = queryEndTime,
        )

        val packageManagerReport = packageManagerClient.getPackageManagerReport()

        val perAppEthernetUsage = queryPerAppUsageMetric(
            packageManagerReport = packageManagerReport,
            connectivity = ETHERNET,
            queryStartTime = queryStartTime,
            queryEndTime = queryEndTime,
        )

        val perAppMobileUsage = queryPerAppUsageMetric(
            packageManagerReport = packageManagerReport,
            connectivity = MOBILE,
            queryStartTime = queryStartTime,
            queryEndTime = queryEndTime,
        )

        val perAppWifiUsage = queryPerAppUsageMetric(
            packageManagerReport = packageManagerReport,
            connectivity = WIFI,
            queryStartTime = queryStartTime,
            queryEndTime = queryEndTime,
        )

        val perAppBluetoothUsage = queryPerAppUsageMetric(
            packageManagerReport = packageManagerReport,
            connectivity = BLUETOOTH,
            queryStartTime = queryStartTime,
            queryEndTime = queryEndTime,
        )

        val perAppUsages = listOf(perAppEthernetUsage, perAppMobileUsage, perAppWifiUsage, perAppBluetoothUsage)
        val totalPerAppUsage = perAppUsages.flatMap { it.keys }.toSet()
            .associateWith { key ->
                NetworkStatsUsage.sum(
                    perAppEthernetUsage[key],
                    perAppMobileUsage[key],
                    perAppWifiUsage[key],
                    perAppBluetoothUsage[key],
                )
            }

        val perAppUsage = recordPerAppUsageMetric(
            ethernetUsage = perAppEthernetUsage,
            mobileUsage = perAppMobileUsage,
            wifiUsage = perAppWifiUsage,
            bluetoothUsage = perAppBluetoothUsage,
            totalUsage = totalPerAppUsage,
            networkUsageSettings = networkUsageSettings,
            queryEndTime = queryEndTime,
        )

        return NetworkStatsResult.merge(
            totalAllUsage,
            totalEthernetUsage,
            totalMobileUsage,
            totalWifiUsage,
            totalBluetoothUsage,
            perAppUsage,
        )
    }

    private fun recordTotalUsageMetric(
        inMetric: String,
        outMetric: String,
        usage: NetworkStatsSummary?,
        queryEndTime: Instant,
    ): NetworkStatsResult = usage?.let {
        recordTotalUsageMetric(
            inMetric = inMetric,
            outMetric = outMetric,
            rxKB = usage.rxKB,
            txKB = usage.txKB,
            queryEndTime = queryEndTime,
        )
    } ?: NetworkStatsResult.EMPTY

    private fun recordTotalUsageMetric(
        inMetric: String,
        outMetric: String,
        rxKB: Long,
        txKB: Long,
        queryEndTime: Instant,
    ): NetworkStatsResult {
        val hrtRollup = mutableSetOf<HighResTelemetry.Rollup>()
        val heartbeatMetrics = mutableMapOf<String, JsonPrimitive>()

        fun record(
            absoluteUsageKB: Long,
            metricName: String,
        ) {
            heartbeatMetrics["$metricName.latest"] = JsonPrimitive(absoluteUsageKB)
            hrtRollup += networkMetricHrtRollup(
                metricName = metricName,
                metricValue = absoluteUsageKB,
                collectionTime = queryEndTime,
            )
        }

        record(rxKB, inMetric)
        record(txKB, outMetric)

        return NetworkStatsResult(
            hrtRollup = hrtRollup,
            heartbeatMetrics = heartbeatMetrics,
            internalHeartbeatMetrics = emptyMap(),
        )
    }

    private suspend fun queryPerAppUsageMetric(
        packageManagerReport: PackageManagerReport,
        connectivity: NetworkStatsConnectivity,
        queryStartTime: Instant,
        queryEndTime: Instant,
    ): Map<String, NetworkStatsUsage> {
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
                usageByPackage[packageName] = NetworkStatsUsage(
                    rxKB = totalRxKB,
                    txKB = totalTxKB,
                )
            }
        }

        return usageByPackage
    }

    private suspend fun recordPerAppUsageMetric(
        ethernetUsage: Map<String, NetworkStatsUsage>,
        mobileUsage: Map<String, NetworkStatsUsage>,
        wifiUsage: Map<String, NetworkStatsUsage>,
        bluetoothUsage: Map<String, NetworkStatsUsage>,
        totalUsage: Map<String, NetworkStatsUsage>,
        networkUsageSettings: NetworkUsageSettings,
        queryEndTime: Instant,
    ): NetworkStatsResult {
        val hrtRollup = mutableSetOf<HighResTelemetry.Rollup>()
        val heartbeatMetrics = mutableMapOf<String, JsonPrimitive>()
        val internalHeartbeatMetrics = mutableMapOf<String, JsonPrimitive>()

        fun recordUsage(
            inName: String,
            outName: String,
            usage: NetworkStatsUsage?,
            internal: Boolean,
            recordZeroUsage: Boolean,
        ) {
            val absoluteUsageInKB = usage?.rxKB ?: 0L
            val absoluteUsageOutKB = usage?.txKB ?: 0L

            mapOf(
                inName to absoluteUsageInKB,
                outName to absoluteUsageOutKB,
            )
                .forEach { (metricName, metricValue) ->
                    if (recordZeroUsage || metricValue > 0) {
                        hrtRollup += networkMetricHrtRollup(
                            metricName = metricName,
                            metricValue = metricValue,
                            collectionTime = queryEndTime,
                            internal = internal,
                        )

                        if (internal) {
                            internalHeartbeatMetrics[metricName] = JsonPrimitive(metricValue)
                        } else {
                            heartbeatMetrics[metricName] = JsonPrimitive(metricValue)
                        }
                    }
                }
        }

        // Logs the total usage for all significant apps, even if there's no data. 0 can mean either the package
        // was not found, or there was no usage during the time period. Only log the ethernet/mobile/wifi usage
        // if it's non-zero.
        significantAppsProvider.apps()
            .forEach { app ->
                recordUsage(
                    inName = allAppInMetricName(app.identifier),
                    outName = allAppOutMetricName(app.identifier),
                    usage = totalUsage[app.packageName],
                    internal = app.internal,
                    recordZeroUsage = true,
                )

                mapOf(
                    ETHERNET to ethernetUsage[app.packageName],
                    MOBILE to mobileUsage[app.packageName],
                    WIFI to wifiUsage[app.packageName],
                    BLUETOOTH to bluetoothUsage[app.packageName],
                ).forEach { (connectivity, usageOrNull) ->
                    recordUsage(
                        inName = appInMetricName(connectivity, app.identifier),
                        outName = appOutMetricName(connectivity, app.identifier),
                        usage = usageOrNull,
                        internal = app.internal,
                        recordZeroUsage = false,
                    )
                }
            }

        fun rollupUsage(
            connectivity: NetworkStatsConnectivity?,
            usageByPackage: Map<String, NetworkStatsUsage>,
        ) {
            for ((packageName, usage) in usageByPackage) {
                // Logs the package too if it exceeds the specified threshold.
                if (usage.rxKB >= networkUsageSettings.collectionReceiveThresholdKb) {
                    hrtRollup += networkMetricHrtRollup(
                        metricName = connectivity?.let { appInMetricName(connectivity, packageName) }
                            ?: allAppInMetricName(packageName),
                        metricValue = usage.rxKB,
                        collectionTime = queryEndTime,
                    )
                }

                if (usage.txKB >= networkUsageSettings.collectionTransmitThresholdKb) {
                    hrtRollup += networkMetricHrtRollup(
                        metricName = connectivity?.let { appOutMetricName(connectivity, packageName) }
                            ?: allAppOutMetricName(packageName),
                        metricValue = usage.txKB,
                        collectionTime = queryEndTime,
                    )
                }
            }
        }

        rollupUsage(null, totalUsage)
        rollupUsage(ETHERNET, ethernetUsage)
        rollupUsage(WIFI, wifiUsage)
        rollupUsage(MOBILE, mobileUsage)
        rollupUsage(BLUETOOTH, mobileUsage)

        return NetworkStatsResult(
            hrtRollup = hrtRollup,
            heartbeatMetrics = heartbeatMetrics,
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
    ) {
        companion object {
            fun sum(vararg usages: NetworkStatsUsage?): NetworkStatsUsage = NetworkStatsUsage(
                rxKB = usages.sumOf { it?.rxKB ?: 0L },
                txKB = usages.sumOf { it?.txKB ?: 0L },
            )
        }
    }

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

        private fun allAppInMetricName(
            packageName: String,
        ) = "network.app.in.all_$packageName"

        private fun allAppOutMetricName(
            packageName: String,
        ) = "network.app.out.all_$packageName"

        private const val TOTAL_ALL_IN_METRIC = "network.total.in.all"
        private const val TOTAL_ALL_OUT_METRIC = "network.total.out.all"
        private const val TOTAL_BLUETOOTH_IN_METRIC = "network.total.in.bt"
        private const val TOTAL_BLUETOOTH_OUT_METRIC = "network.total.out.bt"
        private const val TOTAL_ETHERNET_IN_METRIC = "network.total.in.eth"
        private const val TOTAL_ETHERNET_OUT_METRIC = "network.total.out.eth"
        private const val TOTAL_MOBILE_IN_METRIC = "network.total.in.mobile"
        private const val TOTAL_MOBILE_OUT_METRIC = "network.total.out.mobile"
        private const val TOTAL_WIFI_IN_METRIC = "network.total.in.wifi"
        private const val TOTAL_WIFI_OUT_METRIC = "network.total.out.wifi"
    }
}
