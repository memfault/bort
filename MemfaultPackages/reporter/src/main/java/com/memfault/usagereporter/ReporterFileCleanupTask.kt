package com.memfault.usagereporter

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.requester.cleanupFiles
import com.memfault.bort.requester.directorySize
import com.memfault.bort.shared.Logger
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration

class ReporterFileCleanupTask(private val appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val tempDir = appContext.cacheDir
        reporterTempStorageUsedMetric.update(tempDir.directorySize())
        val result = cleanupFiles(
            dir = tempDir,
            maxDirStorageBytes = UsageReporter.reporterSettings.maxReporterTempStorageBytes,
            maxFileAge = UsageReporter.reporterSettings.maxReporterTempStorageAge
        )
        val deleted = result.deletedForStorageCount + result.deletedForAgeCount
        if (deleted > 0) {
            Logger.d("Deleted $deleted UsageReporter temp files to stay under storage limit")
            reporterTempDeletedMetric.incrementBy(deleted)
        }
        return Result.success()
    }

    companion object {
        private val CLEANUP_PERIOD = 12.hours.toJavaDuration()
        private val WORK_UNIQUE_NAME = "reporter_cleanup"
        private val reporterTempDeletedMetric = Reporting.report().counter(
            name = "reporter_temp_deleted",
            internal = true,
        )
        private val reporterTempStorageUsedMetric = Reporting.report().numberProperty(
            name = "reporter_temp_storage_bytes",
            internal = true,
        )

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReporterFileCleanupTask>(CLEANUP_PERIOD).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
