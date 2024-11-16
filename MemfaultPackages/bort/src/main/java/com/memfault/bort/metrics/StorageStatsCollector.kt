package com.memfault.bort.metrics

import android.os.Environment
import com.memfault.bort.IO
import com.memfault.bort.reporting.NumericAgg
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.shared.Logger
import com.memfault.bort.time.CombinedTime
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

/**
 * Collects internal storage usage stats, and records them as metrics.
 */
class StorageStatsCollector
@Inject constructor(
    @IO private val ioCoroutineContext: CoroutineContext,
) {
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

    suspend fun collectStorageStats(collectionTime: CombinedTime) = withContext(ioCoroutineContext) {
        Logger.v("collectStorageStats")
        val freeBytes = Environment.getDataDirectory().freeSpace
        val totalBytes = Environment.getDataDirectory().totalSpace
        val usedBytes = totalBytes - freeBytes
        val percentageUsed = usedBytes.toDouble() / totalBytes.toDouble()
        Logger.v(
            "collectStorageStats: freeBytes=$freeBytes / totalBytes=$totalBytes / " +
                "usedBytes=$usedBytes / percentageUsed=$percentageUsed",
        )
        val now = collectionTime.timestamp.toEpochMilli()
        legacyFreeBytesMetric.record(freeBytes, timestamp = now)
        legacyTotalBytesMetric.record(totalBytes, timestamp = now)
        legacyUsedBytesMetric.record(usedBytes, timestamp = now)
        legacyPercentageUsedMetric.record(percentageUsed, timestamp = now)
        percentageUsedMetric.record(percentageUsed * 100, timestamp = now)
    }
}
