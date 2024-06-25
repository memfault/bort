package com.memfault.bort.networkstats

import android.os.Process
import com.memfault.bort.PackageManagerClient
import com.memfault.bort.metrics.SignificantAppsProvider
import com.memfault.bort.networkstats.NetworkStatsConnectivity.BLUETOOTH
import com.memfault.bort.networkstats.NetworkStatsConnectivity.ETHERNET
import com.memfault.bort.networkstats.NetworkStatsConnectivity.MOBILE
import com.memfault.bort.networkstats.NetworkStatsConnectivity.WIFI
import com.memfault.bort.parsers.PackageManagerReport
import com.memfault.bort.reporting.NumericAgg.SUM
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.settings.NetworkUsageSettings
import com.memfault.bort.time.CombinedTime
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import javax.inject.Inject
import kotlin.time.Duration

interface NetworkStatsCollector {
    suspend fun collect(
        collectionTime: CombinedTime,
        heartbeatInterval: Duration,
    ): NetworkStatsResult?

    suspend fun record(
        collectionTime: CombinedTime,
        networkStatsResult: NetworkStatsResult,
    )

    suspend fun collectAndRecord(
        collectionTime: CombinedTime,
        heartbeatInterval: Duration,
    ) {
        collect(collectionTime, heartbeatInterval)
            ?.let {
                record(collectionTime, it)
            }
    }
}

data class NetworkStatsUsage(
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

data class NetworkStatsResult(
    val ethernetUsage: NetworkStatsSummary?,
    val mobileUsage: NetworkStatsSummary?,
    val wifiUsage: NetworkStatsSummary?,
    val bluetoothUsage: NetworkStatsSummary?,
    val perAppEthernetUsage: Map<String, NetworkStatsUsage>,
    val perAppMobileUsage: Map<String, NetworkStatsUsage>,
    val perAppWifiUsage: Map<String, NetworkStatsUsage>,
    val perAppBluetoothUsage: Map<String, NetworkStatsUsage>,
) {
    val totalUsages = listOf(wifiUsage, ethernetUsage, mobileUsage, bluetoothUsage)

    val totalPerAppUsage: Map<String, NetworkStatsUsage>
        get() {
            val perAppUsages = listOf(perAppEthernetUsage, perAppMobileUsage, perAppWifiUsage, perAppBluetoothUsage)
            return perAppUsages.flatMap { it.keys }.toSet()
                .associateWith { key ->
                    NetworkStatsUsage.sum(
                        perAppEthernetUsage[key],
                        perAppMobileUsage[key],
                        perAppWifiUsage[key],
                        perAppBluetoothUsage[key],
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
    ): NetworkStatsResult? {
        if (!networkUsageSettings.dataSourceEnabled) {
            return null
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

        return NetworkStatsResult(
            ethernetUsage = ethernetUsage,
            mobileUsage = mobileUsage,
            wifiUsage = wifiUsage,
            bluetoothUsage = bluetoothUsage,
            perAppEthernetUsage = perAppEthernetUsage,
            perAppMobileUsage = perAppMobileUsage,
            perAppWifiUsage = perAppWifiUsage,
            perAppBluetoothUsage = perAppBluetoothUsage,
        )
    }

    override suspend fun record(
        collectionTime: CombinedTime,
        networkStatsResult: NetworkStatsResult,
    ) {
        val queryEndTime = collectionTime.timestamp

        recordTotalUsageMetric(
            inMetric = TOTAL_ETHERNET_IN_METRIC,
            outMetric = TOTAL_ETHERNET_OUT_METRIC,
            usage = networkStatsResult.ethernetUsage,
            queryEndTime = queryEndTime,
        )

        recordTotalUsageMetric(
            inMetric = TOTAL_MOBILE_IN_METRIC,
            outMetric = TOTAL_MOBILE_OUT_METRIC,
            usage = networkStatsResult.mobileUsage,
            queryEndTime = queryEndTime,
        )

        recordTotalUsageMetric(
            inMetric = TOTAL_WIFI_IN_METRIC,
            outMetric = TOTAL_WIFI_OUT_METRIC,
            usage = networkStatsResult.wifiUsage,
            queryEndTime = queryEndTime,
        )

        recordTotalUsageMetric(
            inMetric = TOTAL_BLUETOOTH_IN_METRIC,
            outMetric = TOTAL_BLUETOOTH_OUT_METRIC,
            usage = networkStatsResult.bluetoothUsage,
            queryEndTime = queryEndTime,
        )

        recordTotalUsageMetric(
            inMetric = TOTAL_ALL_IN_METRIC,
            outMetric = TOTAL_ALL_OUT_METRIC,
            rxKB = networkStatsResult.totalUsages.map { it?.rxKB }.sumOf { it ?: 0L },
            txKB = networkStatsResult.totalUsages.map { it?.txKB }.sumOf { it ?: 0L },
            queryEndTime = queryEndTime,
        )

        recordPerAppUsageMetric(
            ethernetUsage = networkStatsResult.perAppEthernetUsage,
            mobileUsage = networkStatsResult.perAppMobileUsage,
            wifiUsage = networkStatsResult.perAppWifiUsage,
            bluetoothUsage = networkStatsResult.perAppBluetoothUsage,
            totalUsage = networkStatsResult.totalPerAppUsage,
            networkUsageSettings = networkUsageSettings,
            queryEndTime = queryEndTime,
        )
    }

    private fun recordTotalUsageMetric(
        inMetric: String,
        outMetric: String,
        usage: NetworkStatsSummary?,
        queryEndTime: Instant,
    ) {
        usage?.let {
            recordTotalUsageMetric(
                inMetric = inMetric,
                outMetric = outMetric,
                rxKB = usage.rxKB,
                txKB = usage.txKB,
                queryEndTime = queryEndTime,
            )
        }
    }

    private fun recordTotalUsageMetric(
        inMetric: String,
        outMetric: String,
        rxKB: Long,
        txKB: Long,
        queryEndTime: Instant,
    ) {
        fun record(
            absoluteUsageKB: Long,
            metricName: String,
        ) {
            Reporting.report()
                .distribution(name = metricName, aggregations = listOf(SUM))
                .record(
                    value = absoluteUsageKB,
                    timestamp = queryEndTime.toEpochMilli(),
                )
        }

        record(rxKB, inMetric)
        record(txKB, outMetric)
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
    ) {
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
                        Reporting.report()
                            .distribution(
                                name = metricName,
                                listOf(SUM),
                                internal = internal,
                            )
                            .record(
                                value = metricValue,
                                timestamp = queryEndTime.toEpochMilli(),
                            )
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
                    Reporting.report()
                        .distribution(
                            name = connectivity?.let { appInMetricName(connectivity, packageName) }
                                ?: allAppInMetricName(packageName),
                        )
                        .record(
                            value = usage.rxKB,
                            timestamp = queryEndTime.toEpochMilli(),
                        )
                }

                if (usage.txKB >= networkUsageSettings.collectionTransmitThresholdKb) {
                    Reporting.report()
                        .distribution(
                            name = connectivity?.let { appOutMetricName(connectivity, packageName) }
                                ?: allAppOutMetricName(packageName),
                        )
                        .record(
                            value = usage.txKB,
                            timestamp = queryEndTime.toEpochMilli(),
                        )
                }
            }
        }

        rollupUsage(null, totalUsage)
        rollupUsage(ETHERNET, ethernetUsage)
        rollupUsage(WIFI, wifiUsage)
        rollupUsage(MOBILE, mobileUsage)
        rollupUsage(BLUETOOTH, mobileUsage)
    }

    private fun PackageManagerReport.uuidToName(uid: Int): String = when (uid) {
        in Process.FIRST_APPLICATION_UID..Process.LAST_APPLICATION_UID ->
            packages.lastOrNull { it.userId == uid }?.id ?: "unknown"
        // Every system UID's usage is assigned to "android"
        else -> "android"
    }

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
