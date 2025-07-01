package com.memfault.bort.metrics

import androidx.annotation.VisibleForTesting
import com.memfault.bort.DumpsterClient
import com.memfault.bort.IO
import com.memfault.bort.reporting.NumericAgg
import com.memfault.bort.reporting.NumericAgg.MEAN
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.shared.Logger
import com.memfault.bort.time.CombinedTime
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

/**
 * Collects internal storage usage stats, and records them as metrics.
 */
class StorageStatsCollector
@Inject constructor(
    @IO private val ioCoroutineContext: CoroutineContext,
    private val dumpsterClient: DumpsterClient,
    private val diskSpaceProvider: DiskSpaceProvider,
    private val diskActivityProvider: DiskActivityProvider,
    private val diskActivityStorage: DiskActivityStorage,
    private val storageStatsReporter: StorageStatsReporter,
) {
    suspend fun collectStorageStats(collectionTime: CombinedTime) = withContext(ioCoroutineContext) {
        Logger.v("collectStorageStats")
        val freeBytes = diskSpaceProvider.getFreeBytes()
        val totalBytes = diskSpaceProvider.getTotalBytes()
        val usedBytes = totalBytes - freeBytes
        val percentageUsed = usedBytes.toDouble() / totalBytes.toDouble()
        Logger.v(
            "collectStorageStats: freeBytes=$freeBytes / totalBytes=$totalBytes / " +
                "usedBytes=$usedBytes / percentageUsed=$percentageUsed",
        )
        val now = collectionTime.timestamp.toEpochMilli()
        storageStatsReporter.reportUsage(freeBytes, totalBytes, usedBytes, percentageUsed, now)

        val storageWearInfo = dumpsterClient.getStorageWear()
            ?.let { StorageWearInfo.fromServiceOutput(it) }
        if (storageWearInfo != null) {
            storageStatsReporter.reportFlashWear(
                source = storageWearInfo.source,
                version = storageWearInfo.version,
                eol = storageWearInfo.eol,
                lifetimeA = storageWearInfo.lifetimeA,
                lifetimeB = storageWearInfo.lifetimeB,
                now = now,
            )
        }

        updateDiskActivityStorage(diskActivityProvider.getDiskActivity(), now)
    }

    private fun updateDiskActivityStorage(activity: DiskActivity, now: Long) {
        val activitySinceLastCollection = activity - diskActivityStorage.state

        for (stat in activitySinceLastCollection.stats) {
            storageStatsReporter.reportWrites(stat.deviceName, stat.sectorsWritten * activity.sectorSize, now)
        }

        diskActivityStorage.state = activity
    }
}

interface StorageStatsReporter {
    fun reportUsage(
        freeBytes: Long,
        totalBytes: Long,
        usedBytes: Long,
        percentageUsed: Double,
        now: Long,
    )

    fun reportFlashWear(
        source: String,
        version: String,
        eol: Int,
        lifetimeA: Int,
        lifetimeB: Int,
        now: Long,
    )
    fun reportWrites(deviceName: String, bytesWritten: Long, now: Long)
}

@Singleton
@ContributesBinding(SingletonComponent::class, boundType = StorageStatsReporter::class)
class RealStorageStatsReporter @Inject constructor() : StorageStatsReporter {
    private val legacyFreeBytesMetric =
        Reporting.report().distribution("storage.data.bytes_free", listOf(NumericAgg.LATEST_VALUE))
    private val legacyTotalBytesMetric =
        Reporting.report().distribution("storage.data.bytes_total", listOf(NumericAgg.LATEST_VALUE))
    private val legacyUsedBytesMetric =
        Reporting.report().distribution("storage.data.bytes_used", listOf(NumericAgg.LATEST_VALUE))
    private val legacyPercentageUsedMetric =
        Reporting.report().distribution("storage.data.percentage_used", listOf(NumericAgg.LATEST_VALUE))
    private val percentageUsedMetric =
        Reporting.report().distribution("storage_used_pct", listOf(NumericAgg.LATEST_VALUE))

    override fun reportUsage(freeBytes: Long, totalBytes: Long, usedBytes: Long, percentageUsed: Double, now: Long) {
        legacyFreeBytesMetric.record(freeBytes, timestamp = now)
        legacyTotalBytesMetric.record(totalBytes, timestamp = now)
        legacyUsedBytesMetric.record(usedBytes, timestamp = now)
        legacyPercentageUsedMetric.record(percentageUsed, timestamp = now)
        percentageUsedMetric.record(percentageUsed * 100, timestamp = now)
    }

    override fun reportFlashWear(
        source: String,
        version: String,
        eol: Int,
        lifetimeA: Int,
        lifetimeB: Int,
        now: Long,
    ) {
        val sourceString = if (source.isNotBlank()) "$source." else ""

        Reporting.report().stringProperty("disk_wear.${sourceString}version")
            .update(version, now)

        Reporting.report().stringProperty("disk_wear.${sourceString}pre_eol")
            .update(preEolAsString(eol), now)

        val lifetimeARemaining = lifetimeAsRemainingPct(lifetimeA)
        if (lifetimeARemaining != null) {
            Reporting.report().numberProperty("disk_wear.${sourceString}lifetime_remaining_pct")
                .update(lifetimeARemaining, now)
        }

        val lifetimeBRemaining = lifetimeAsRemainingPct(lifetimeB)
        if (lifetimeBRemaining != null) {
            Reporting.report().numberProperty("disk_wear.${sourceString}lifetime_b_remaining_pct")
                .update(lifetimeBRemaining, now)
        }
    }

    override fun reportWrites(deviceName: String, bytesWritten: Long, now: Long) {
        Reporting.report().distribution("disk_wear.$deviceName.bytes_written", aggregations = listOf(MEAN))
            .record(bytesWritten, now)
    }
}

/**
 * Converts the lifetime value to a lifetime remaining percentage according to this table:
 *
 * Value | Percentage of lifetime used
 * -------------------------------------
 * 0x00   | Undefined
 * 0x01   | 0-10%
 * 0x02   | 10-20%
 * 0x03   | 20-30%
 * 0x04   | 30-40%
 * 0x05   | 40-50%
 * 0x06   | 50-60%
 * 0x07   | 60-70%
 * 0x08   | 70-80%
 * 0x09   | 80-90%
 * 0x0A   | 90-100%
 * 0x0B   | 100% (end of life)
 * Others | Undefined
 */
@VisibleForTesting internal fun lifetimeAsRemainingPct(lifetime: Int): Int? =
    when (lifetime) {
        0 -> null // Undefined
        in 1..11 -> 100 - ((lifetime - 1) * 10) // Convert to percentage
        else -> null // Undefined for values outside the expected range
    }

/**
 * Converts the pre-eol value into a readable string:
 *
 * Value   | Pre-EOL Info
 * ----------------------
 * 0x00    | Not Defined
 * 0x01    | Normal
 * 0x02    | Warning
 * 0x03    | Urgent
 * Others  | Reserved
 */
internal fun preEolAsString(preEol: Int): String =
    when (preEol) {
        0 -> "Not Defined"
        1 -> "Normal"
        2 -> "Warning"
        3 -> "Urgent"
        else -> "Reserved"
    }

data class StorageWearInfo(
    val source: String,
    val eol: Int,
    val lifetimeA: Int,
    val lifetimeB: Int,
    val version: String,
) {
    companion object {
        fun fromServiceOutput(output: String): StorageWearInfo? {
            val stats = output.split(" ", limit = 5)
            if (stats.size < 3) {
                return null
            }

            return StorageWearInfo(
                eol = stats[0].toIntOrNull() ?: return null,
                lifetimeA = stats[1].toIntOrNull() ?: return null,
                lifetimeB = stats[2].toIntOrNull() ?: return null,
                source = stats.getOrNull(3).orEmpty(),
                // the remaining is the version
                version = stats.getOrNull(4).orEmpty(),
            )
        }
    }
}
