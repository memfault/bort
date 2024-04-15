package com.memfault.bort.networkstats

import android.app.usage.NetworkStats.Bucket
import android.app.usage.NetworkStatsManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import kotlin.math.roundToLong

interface NetworkStatsQueries {

    /**
     * Returns a [NetworkStatsSummary] between the given [start] and [end].
     */
    suspend fun getTotalUsage(
        start: Instant,
        end: Instant,
        connectivity: NetworkStatsConnectivity,
    ): NetworkStatsSummary?

    /**
     * Returns all the [NetworkStatsSummary]s between the given [start] and [end], grouped by uid.
     *
     * Summaries are sorted in ascending order in time.
     */
    suspend fun getUsageByApp(
        start: Instant,
        end: Instant,
        connectivity: NetworkStatsConnectivity,
    ): Map<Int, List<NetworkStatsSummary>>
}

sealed class NetworkStatsUid(
    val uidValue: Int,
    val shortName: String,
) {
    data class AppUid(val uid: Int) : NetworkStatsUid(uidValue = uid, shortName = uid.toString())
    data object RemovedUid : NetworkStatsUid(uidValue = Bucket.UID_REMOVED, shortName = "removed")
    data object TetheringUid : NetworkStatsUid(uidValue = Bucket.UID_TETHERING, shortName = "tethering")
    data class SystemUid(val uid: Int) : NetworkStatsUid(uidValue = uid, shortName = "system")

    companion object {
        fun fromUid(uid: Int): NetworkStatsUid =
            when (uid) {
                in Process.FIRST_APPLICATION_UID..Process.LAST_APPLICATION_UID -> AppUid(uid)
                RemovedUid.uidValue -> RemovedUid
                TetheringUid.uidValue -> TetheringUid
                else -> SystemUid(uid)
            }
    }
}

@Suppress("DEPRECATION")
enum class NetworkStatsConnectivity(
    val connectivityManagerNetworkType: Int,
    val shortName: String,
) {
    MOBILE(ConnectivityManager.TYPE_MOBILE, "mobile"),
    ETHERNET(ConnectivityManager.TYPE_ETHERNET, "eth"),
    WIFI(ConnectivityManager.TYPE_WIFI, "wifi"),
    BLUETOOTH(ConnectivityManager.TYPE_BLUETOOTH, "bt"),
}

enum class NetworkStatsState {
    ALL,
    DEFAULT,
    FOREGROUND,
}

enum class NetworkStatsMetered {
    ALL,
    METERED,
    UNMETERED,
}

enum class NetworkStatsRoaming {
    ALL,
    ROAMING,
    NOT_ROAMING,
}

enum class NetworkStatsDefaultNetwork {
    ALL,
    DEFAULT,
    NOT_DEFAULT,
}

data class NetworkStatsSummary(
    val uid: NetworkStatsUid,
    val state: NetworkStatsState,
    val metered: NetworkStatsMetered,
    val roaming: NetworkStatsRoaming,
    val defaultNetwork: NetworkStatsDefaultNetwork?,
    val connectivity: NetworkStatsConnectivity,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    /**
     * Received Bytes.
     */
    val rxBytes: Long,
    val rxPackets: Long,
    /**
     * Sent Bytes.
     */
    val txBytes: Long,
    val txPackets: Long,
) {
    val rxKB: Long = (rxBytes / 1000.0).roundToLong()
    val txKB: Long = (txBytes / 1000.0).roundToLong()

    companion object {
        fun fromBucket(
            bucket: Bucket,
            connectivity: NetworkStatsConnectivity,
        ): NetworkStatsSummary =
            NetworkStatsSummary(
                uid = NetworkStatsUid.fromUid(bucket.uid),
                state = when (bucket.state) {
                    Bucket.STATE_FOREGROUND -> NetworkStatsState.FOREGROUND
                    Bucket.STATE_DEFAULT -> NetworkStatsState.DEFAULT
                    else -> NetworkStatsState.ALL
                },
                metered = when (bucket.metered) {
                    Bucket.METERED_YES -> NetworkStatsMetered.METERED
                    Bucket.METERED_NO -> NetworkStatsMetered.UNMETERED
                    else -> NetworkStatsMetered.ALL
                },
                roaming = when (bucket.roaming) {
                    Bucket.ROAMING_YES -> NetworkStatsRoaming.ROAMING
                    Bucket.ROAMING_NO -> NetworkStatsRoaming.NOT_ROAMING
                    else -> NetworkStatsRoaming.ALL
                },
                defaultNetwork = if (Build.VERSION.SDK_INT >= 28) {
                    when (bucket.defaultNetworkStatus) {
                        Bucket.DEFAULT_NETWORK_YES -> NetworkStatsDefaultNetwork.DEFAULT
                        Bucket.DEFAULT_NETWORK_NO -> NetworkStatsDefaultNetwork.NOT_DEFAULT
                        else -> NetworkStatsDefaultNetwork.ALL
                    }
                } else {
                    null
                },
                connectivity = connectivity,
                startEpochMillis = bucket.startTimeStamp,
                endEpochMillis = bucket.endTimeStamp,
                rxBytes = bucket.rxBytes,
                rxPackets = bucket.rxPackets,
                txBytes = bucket.txBytes,
                txPackets = bucket.txPackets,
            )
    }
}

@ContributesBinding(SingletonComponent::class)
class RealNetworkStatsQueries
@Inject constructor(
    private val networkStatsManager: NetworkStatsManager,
) : NetworkStatsQueries {
    override suspend fun getTotalUsage(
        start: Instant,
        end: Instant,
        connectivity: NetworkStatsConnectivity,
    ): NetworkStatsSummary? = withContext(Dispatchers.IO) {
        try {
            networkStatsManager.querySummaryForDevice(
                connectivity.connectivityManagerNetworkType,
                null,
                start.toEpochMilli(),
                end.toEpochMilli(),
            )?.let { bucket ->
                NetworkStatsSummary.fromBucket(bucket, connectivity)
            }
        } catch (e: SecurityException) {
            Logger.w("Error getting network stats", e)
            null
        }
    }

    override suspend fun getUsageByApp(
        start: Instant,
        end: Instant,
        connectivity: NetworkStatsConnectivity,
    ): Map<Int, List<NetworkStatsSummary>> = withContext(Dispatchers.IO) {
        val latestUsageByApp = mutableMapOf<Int, List<NetworkStatsSummary>>()

        networkStatsManager.querySummary(
            connectivity.connectivityManagerNetworkType,
            null,
            start.toEpochMilli(),
            end.toEpochMilli(),
        ).use { networkStatsOrNull ->
            val bucket = Bucket()
            while (networkStatsOrNull?.hasNextBucket() == true) {
                networkStatsOrNull.getNextBucket(bucket)

                val nextSummary = NetworkStatsSummary.fromBucket(bucket, connectivity)

                val previousSummary = latestUsageByApp[nextSummary.uid.uidValue] ?: emptyList()
                latestUsageByApp[nextSummary.uid.uidValue] = previousSummary + nextSummary
            }
        }

        latestUsageByApp
    }
}
