package com.memfault.bort.metrics

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.storage.StorageManager
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.shared.Logger
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Collects internal storage usage stats, and records them as metrics.
 */
class StorageStatsCollector @Inject constructor(
    private val context: Context,
) {
    private val freeBytesMetric = Reporting.report().numberProperty("storage.primary.bytes_free")
    private val totalBytesMetric = Reporting.report().numberProperty("storage.primary.bytes_total")
    private val usedBytesMetric = Reporting.report().numberProperty("storage.primary.bytes_used")
    private val percentageUsedMetric = Reporting.report().numberProperty("storage.primary.percentage_used")

    suspend fun collectStorageStats() = withContext(Dispatchers.IO) {
        Logger.v("collectStorageStats")
        val manager = storageManagerService() ?: return@withContext
        val stats = storageStatsService() ?: return@withContext
        val primaryVolume = manager.primaryStorageVolume.uuid?.let { UUID.fromString(it) }
            ?: StorageManager.UUID_DEFAULT
        val freeBytes = stats.getFreeBytes(primaryVolume)
        val totalBytes = stats.getTotalBytes(primaryVolume)
        val usedBytes = totalBytes - freeBytes
        val percentageUsed = usedBytes.toDouble() / totalBytes.toDouble()
        Logger.v(
            "collectStorageStats: freeBytes=$freeBytes / totalBytes=$totalBytes / " +
                "usedBytes=$usedBytes / percentageUsed=$percentageUsed"
        )
        freeBytesMetric.update(freeBytes)
        totalBytesMetric.update(totalBytes)
        usedBytesMetric.update(usedBytes)
        percentageUsedMetric.update(percentageUsed)
    }

    private fun storageManagerService() = context.getSystemService(StorageManager::class.java)
    private fun storageStatsService() = context.getSystemService(StorageStatsManager::class.java)
}
