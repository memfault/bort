package com.memfault.bort.networkstats

import android.os.Process
import com.memfault.bort.PackageManagerClient
import com.memfault.bort.metrics.SignificantAppsProvider
import com.memfault.bort.networkstats.NetworkStatsConnectivity.BLUETOOTH
import com.memfault.bort.networkstats.NetworkStatsConnectivity.ETHERNET
import com.memfault.bort.networkstats.NetworkStatsConnectivity.MOBILE
import com.memfault.bort.networkstats.NetworkStatsConnectivity.WIFI
import com.memfault.bort.parsers.PackageManagerReport
import com.memfault.bort.parsers.PackageManagerReport.Companion.PROCESS_UID_COMPONENT_MAP
import com.memfault.bort.reporting.NumericAgg.SUM
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.settings.NetworkUsageSettings
import com.memfault.bort.shared.Logger
import com.memfault.bort.time.BaseLinuxBootRelativeTime
import com.memfault.bort.time.CombinedTime
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import javax.inject.Inject
import kotlin.math.roundToLong
import kotlin.time.toJavaDuration

interface NetworkStatsCollector {
    suspend fun collect(
        collectionTime: CombinedTime,
        lastHeartbeatUptime: BaseLinuxBootRelativeTime,
    ): NetworkStatsResult?

    suspend fun record(
        collectionTime: CombinedTime,
        networkStatsResult: NetworkStatsResult,
    )

    suspend fun collectAndRecord(
        collectionTime: CombinedTime,
        lastHeartbeatUptime: BaseLinuxBootRelativeTime,
    ) {
        collect(collectionTime, lastHeartbeatUptime)
            ?.let {
                record(collectionTime, it)
            }
    }
}

data class NetworkStatsUsage(
    val rxBytes: Long,
    val txBytes: Long,
) {
    companion object {
        fun sum(vararg usages: NetworkStatsUsage?): NetworkStatsUsage = NetworkStatsUsage(
            rxBytes = usages.sumOf { it?.rxBytes ?: 0L },
            txBytes = usages.sumOf { it?.txBytes ?: 0L },
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
        lastHeartbeatUptime: BaseLinuxBootRelativeTime,
    ): NetworkStatsResult? {
        if (!networkUsageSettings.dataSourceEnabled) {
            return null
        }

        val lastCollectionTimestamp = lastNetworkStatsCollectionTimestamp.getValue()

        val queryStartTime = if (lastCollectionTimestamp > 0) {
            Instant.ofEpochMilli(lastCollectionTimestamp)
        } else {
            val heartbeatInterval = collectionTime.elapsedRealtime.duration
                .minus(lastHeartbeatUptime.elapsedRealtime.duration)
                .takeIf { it.isPositive() }

            if (heartbeatInterval != null) {
                collectionTime.timestamp.minus(heartbeatInterval.toJavaDuration())
            } else {
                null
            }
        }

        val queryEndTime = collectionTime.timestamp

        lastNetworkStatsCollectionTimestamp.setValue(queryEndTime.toEpochMilli())

        if (queryStartTime == null) {
            Logger.i(
                "Could not collect network stats, invalid query range " +
                    "[${lastHeartbeatUptime.elapsedRealtime.duration}, ${collectionTime.elapsedRealtime.duration}]",
            )
            return null
        }

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

    private fun maybeCollectLegacyMetric(metric: () -> String): String? =
        if (networkUsageSettings.collectLegacyMetrics) {
            metric()
        } else {
            null
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
            legacyInMetric = maybeCollectLegacyMetric { TOTAL_ETHERNET_IN_METRIC_LEGACY },
            legacyOutMetric = maybeCollectLegacyMetric { TOTAL_ETHERNET_OUT_METRIC_LEGACY },
        )

        recordTotalUsageMetric(
            inMetric = TOTAL_MOBILE_IN_METRIC,
            outMetric = TOTAL_MOBILE_OUT_METRIC,
            usage = networkStatsResult.mobileUsage,
            queryEndTime = queryEndTime,
            legacyInMetric = maybeCollectLegacyMetric { TOTAL_MOBILE_IN_METRIC_LEGACY },
            legacyOutMetric = maybeCollectLegacyMetric { TOTAL_MOBILE_OUT_METRIC_LEGACY },
        )

        recordTotalUsageMetric(
            inMetric = TOTAL_WIFI_IN_METRIC,
            outMetric = TOTAL_WIFI_OUT_METRIC,
            usage = networkStatsResult.wifiUsage,
            queryEndTime = queryEndTime,
            legacyInMetric = maybeCollectLegacyMetric { TOTAL_WIFI_IN_METRIC_LEGACY },
            legacyOutMetric = maybeCollectLegacyMetric { TOTAL_WIFI_OUT_METRIC_LEGACY },
        )

        recordTotalUsageMetric(
            inMetric = TOTAL_BLUETOOTH_IN_METRIC,
            outMetric = TOTAL_BLUETOOTH_OUT_METRIC,
            usage = networkStatsResult.bluetoothUsage,
            queryEndTime = queryEndTime,
            legacyInMetric = maybeCollectLegacyMetric { TOTAL_BLUETOOTH_IN_METRIC_LEGACY },
            legacyOutMetric = maybeCollectLegacyMetric { TOTAL_BLUETOOTH_OUT_METRIC_LEGACY },
        )

        recordTotalUsageMetric(
            inMetric = TOTAL_ALL_IN_METRIC,
            outMetric = TOTAL_ALL_OUT_METRIC,
            rxBytes = networkStatsResult.totalUsages.map { it?.rxBytes }.sumOf { it ?: 0L },
            txBytes = networkStatsResult.totalUsages.map { it?.txBytes }.sumOf { it ?: 0L },
            queryEndTime = queryEndTime,
            legacyInMetric = maybeCollectLegacyMetric { TOTAL_ALL_IN_METRIC_LEGACY },
            legacyOutMetric = maybeCollectLegacyMetric { TOTAL_ALL_OUT_METRIC_LEGACY },
        )

        recordPerAppUsageMetric(
            ethernetUsage = networkStatsResult.perAppEthernetUsage,
            mobileUsage = networkStatsResult.perAppMobileUsage,
            wifiUsage = networkStatsResult.perAppWifiUsage,
            bluetoothUsage = networkStatsResult.perAppBluetoothUsage,
            totalUsage = networkStatsResult.totalPerAppUsage,
            queryEndTime = queryEndTime,
        )
    }

    private fun recordTotalUsageMetric(
        inMetric: String,
        outMetric: String,
        usage: NetworkStatsSummary?,
        queryEndTime: Instant,
        legacyInMetric: String?,
        legacyOutMetric: String?,
    ) {
        usage?.let {
            recordTotalUsageMetric(
                inMetric = inMetric,
                outMetric = outMetric,
                rxBytes = usage.rxBytes,
                txBytes = usage.txBytes,
                queryEndTime = queryEndTime,
                legacyInMetric = legacyInMetric,
                legacyOutMetric = legacyOutMetric,
            )
        }
    }

    private fun recordTotalUsageMetric(
        inMetric: String,
        outMetric: String,
        rxBytes: Long,
        txBytes: Long,
        queryEndTime: Instant,
        legacyInMetric: String?,
        legacyOutMetric: String?,
    ) {
        fun record(
            absoluteUsage: Long,
            metricName: String,
        ) {
            Reporting.report()
                .distribution(name = metricName, aggregations = listOf(SUM))
                .record(
                    value = absoluteUsage,
                    timestamp = queryEndTime.toEpochMilli(),
                )
        }

        record(rxBytes, inMetric)
        record(txBytes, outMetric)
        legacyInMetric?.let { record(rxBytes.bytesToKb(), it) }
        legacyOutMetric?.let { record(txBytes.bytesToKb(), it) }
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
            val packageName = packageManagerReport.uidToName(uid)
            val totalRxBytes = summaries.sumOf { it.rxBytes }
            val totalTxBytes = summaries.sumOf { it.txBytes }

            val packageUsageOrNull = usageByPackage[packageName]
            if (packageUsageOrNull != null) {
                usageByPackage[packageName] = NetworkStatsUsage(
                    rxBytes = packageUsageOrNull.rxBytes + totalRxBytes,
                    txBytes = packageUsageOrNull.txBytes + totalTxBytes,
                )
            } else {
                usageByPackage[packageName] = NetworkStatsUsage(
                    rxBytes = totalRxBytes,
                    txBytes = totalTxBytes,
                )
            }
        }

        return usageByPackage
    }

    private fun recordPerAppUsageMetric(
        ethernetUsage: Map<String, NetworkStatsUsage>,
        mobileUsage: Map<String, NetworkStatsUsage>,
        wifiUsage: Map<String, NetworkStatsUsage>,
        bluetoothUsage: Map<String, NetworkStatsUsage>,
        totalUsage: Map<String, NetworkStatsUsage>,
        queryEndTime: Instant,
    ) {
        fun recordUsage(
            inName: String,
            outName: String,
            usage: NetworkStatsUsage?,
            internal: Boolean,
            recordZeroUsage: Boolean,
            legacyInName: String?,
            legacyOutName: String?,
        ) {
            val absoluteUsageInBytes = usage?.rxBytes ?: 0L
            val absoluteUsageOutBytes = usage?.txBytes ?: 0L

            mapOf(
                inName to absoluteUsageInBytes,
                outName to absoluteUsageOutBytes,
                legacyInName to absoluteUsageInBytes.bytesToKb(),
                legacyOutName to absoluteUsageOutBytes.bytesToKb(),
            )
                .forEach { (metricName, metricValue) ->
                    if (metricName != null && (recordZeroUsage || metricValue > 0)) {
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
                    legacyInName = maybeCollectLegacyMetric { allAppInMetricName(app.identifier, legacyName = true) },
                    legacyOutName = maybeCollectLegacyMetric { allAppOutMetricName(app.identifier, legacyName = true) },
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
                        legacyInName = maybeCollectLegacyMetric {
                            appInMetricName(
                                connectivity,
                                app.identifier,
                                legacyName = true,
                            )
                        },
                        legacyOutName = maybeCollectLegacyMetric {
                            appOutMetricName(
                                connectivity,
                                app.identifier,
                                legacyName = true,
                            )
                        },
                    )
                }
            }

        fun rollupUsage(
            connectivity: NetworkStatsConnectivity?,
            usageByPackage: Map<String, NetworkStatsUsage>,
        ) {
            fun addMetric(
                key: String,
                value: Long,
            ) {
                Reporting.report()
                    .distribution(name = key)
                    .record(
                        value = value,
                        timestamp = queryEndTime.toEpochMilli(),
                    )
            }

            for ((packageName, usage) in usageByPackage) {
                // Logs the package too if it exceeds the specified threshold.
                if (usage.rxBytes.bytesToKb() >= networkUsageSettings.collectionReceiveThresholdKb) {
                    if (connectivity != null) {
                        addMetric(appInMetricName(connectivity, packageName), usage.rxBytes)
                        if (networkUsageSettings.collectLegacyMetrics) {
                            addMetric(
                                appInMetricName(connectivity, packageName, legacyName = true),
                                usage.rxBytes.bytesToKb(),
                            )
                        }
                    } else {
                        addMetric(allAppInMetricName(packageName), usage.rxBytes)
                        if (networkUsageSettings.collectLegacyMetrics) {
                            addMetric(allAppInMetricName(packageName, legacyName = true), usage.rxBytes.bytesToKb())
                        }
                    }
                }

                if (usage.txBytes.bytesToKb() >= networkUsageSettings.collectionTransmitThresholdKb) {
                    if (connectivity != null) {
                        addMetric(appOutMetricName(connectivity, packageName), usage.txBytes)
                        if (networkUsageSettings.collectLegacyMetrics) {
                            addMetric(
                                appOutMetricName(connectivity, packageName, legacyName = true),
                                usage.txBytes.bytesToKb(),
                            )
                        }
                    } else {
                        addMetric(allAppOutMetricName(packageName), usage.txBytes)
                        if (networkUsageSettings.collectLegacyMetrics) {
                            addMetric(allAppOutMetricName(packageName, legacyName = true), usage.txBytes.bytesToKb())
                        }
                    }
                }
            }
        }

        rollupUsage(null, totalUsage)
        rollupUsage(ETHERNET, ethernetUsage)
        rollupUsage(WIFI, wifiUsage)
        rollupUsage(MOBILE, mobileUsage)
        rollupUsage(BLUETOOTH, mobileUsage)
    }

    private fun PackageManagerReport.uidToName(uid: Int): String =
        PROCESS_UID_COMPONENT_MAP[uid]
            ?: if (uid in Process.FIRST_APPLICATION_UID..Process.LAST_APPLICATION_UID) {
                packages.lastOrNull { it.userId == uid }?.id ?: "unknown"
            } else {
                // Every remaining system UID's usage is assigned to "android"
                "android"
            }

    companion object {
        private fun appInMetricName(
            connectivity: NetworkStatsConnectivity,
            packageName: String,
            legacyName: Boolean = false,
        ) = if (legacyName) {
            "network.app.in.${connectivity.shortName}_$packageName"
        } else {
            "connectivity_comp_${packageName}_${connectivity.shortName}_recv_bytes"
        }

        private fun appOutMetricName(
            connectivity: NetworkStatsConnectivity,
            packageName: String,
            legacyName: Boolean = false,
        ) = if (legacyName) {
            "network.app.out.${connectivity.shortName}_$packageName"
        } else {
            "connectivity_comp_${packageName}_${connectivity.shortName}_sent_bytes"
        }

        private fun allAppInMetricName(
            packageName: String,
            legacyName: Boolean = false,
        ) = if (legacyName) {
            "network.app.in.all_$packageName"
        } else {
            "connectivity_comp_${packageName}_recv_bytes"
        }

        private fun allAppOutMetricName(
            packageName: String,
            legacyName: Boolean = false,
        ) = if (legacyName) {
            "network.app.out.all_$packageName"
        } else {
            "connectivity_comp_${packageName}_sent_bytes"
        }

        private fun Long.bytesToKb() = (this / 1000.0).roundToLong()

        private const val TOTAL_ALL_IN_METRIC = "connectivity_recv_bytes"
        private const val TOTAL_ALL_OUT_METRIC = "connectivity_sent_bytes"
        private const val TOTAL_BLUETOOTH_IN_METRIC = "connectivity_bt_recv_bytes"
        private const val TOTAL_BLUETOOTH_OUT_METRIC = "connectivity_bt_sent_bytes"
        private const val TOTAL_ETHERNET_IN_METRIC = "connectivity_eth_recv_bytes"
        private const val TOTAL_ETHERNET_OUT_METRIC = "connectivity_eth_sent_bytes"
        private const val TOTAL_MOBILE_IN_METRIC = "connectivity_mobile_recv_bytes"
        private const val TOTAL_MOBILE_OUT_METRIC = "connectivity_mobile_sent_bytes"
        private const val TOTAL_WIFI_IN_METRIC = "connectivity_wifi_recv_bytes"
        private const val TOTAL_WIFI_OUT_METRIC = "connectivity_wifi_sent_bytes"

        private const val TOTAL_ALL_IN_METRIC_LEGACY = "network.total.in.all"
        private const val TOTAL_ALL_OUT_METRIC_LEGACY = "network.total.out.all"
        private const val TOTAL_BLUETOOTH_IN_METRIC_LEGACY = "network.total.in.bt"
        private const val TOTAL_BLUETOOTH_OUT_METRIC_LEGACY = "network.total.out.bt"
        private const val TOTAL_ETHERNET_IN_METRIC_LEGACY = "network.total.in.eth"
        private const val TOTAL_ETHERNET_OUT_METRIC_LEGACY = "network.total.out.eth"
        private const val TOTAL_MOBILE_IN_METRIC_LEGACY = "network.total.in.mobile"
        private const val TOTAL_MOBILE_OUT_METRIC_LEGACY = "network.total.out.mobile"
        private const val TOTAL_WIFI_IN_METRIC_LEGACY = "network.total.in.wifi"
        private const val TOTAL_WIFI_OUT_METRIC_LEGACY = "network.total.out.wifi"
    }
}
