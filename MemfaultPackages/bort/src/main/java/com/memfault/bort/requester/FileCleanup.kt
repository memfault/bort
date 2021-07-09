package com.memfault.bort.requester

import com.memfault.bort.fileExt.deleteSilently
import com.memfault.bort.metrics.BUG_REPORT_DELETED_OLD
import com.memfault.bort.metrics.BUG_REPORT_DELETED_STORAGE
import com.memfault.bort.metrics.metrics
import com.memfault.bort.shared.Logger
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.toDuration

/**
 * Cleanup old bugreport files that:
 * - Are waiting for connectivity, to be uploaded
 * - Failed to be deleted (perhaps because of a bug/crash)
 *
 * We set a maximum amount of storage that can be used by stored bugreports. That might be exceeded between
 * cleanup task executions. We also set maximum age for stored bugreports.
 *
 * During cleanup, we delete the oldest bugreports (by modified timestamp) until under the storage limit, in addition to
 * any older than the maximum age.
 *
 * Note that we cannot tell which reports are still queued for upload as WorkManager tasks.
 */
internal fun cleanupBugReports(
    bugReportDir: File,
    maxBugReportStorageBytes: Int,
    maxBugReportAge: Duration,
    timeNowMs: Long,
) {
    if (!bugReportDir.exists()) return
    val filesNewestFirst = bugReportDir.listFiles()?.toList()?.sortedByDescending { it.lastModified() } ?: return

    var bytesUsed: Long = 0
    filesNewestFirst.filter { it.isFile }.forEach { file ->
        bytesUsed += file.length()
        if (bytesUsed > maxBugReportStorageBytes) {
            Logger.d("cleanupBugReports: Over bugreport storage limit: deleting ${file.name}")
            metrics()?.increment(BUG_REPORT_DELETED_STORAGE)
            file.deleteSilently()
        } else if (maxBugReportAge != ZERO) {
            val age = (timeNowMs - file.lastModified()).toDuration(TimeUnit.MILLISECONDS)
            if (age > maxBugReportAge) {
                Logger.d("Older than max bugreport age: deleting ${file.name}")
                metrics()?.increment(BUG_REPORT_DELETED_OLD)
                file.deleteSilently()
            }
        }
    }
}
