package com.memfault.usagereporter

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.memfault.bort.reporting.NumericAgg
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.requester.cleanupFiles
import com.memfault.bort.requester.directorySize
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.NoOpJobReporter
import com.memfault.bort.shared.runAndTrackExceptions
import com.memfault.usagereporter.clientserver.RealSendfileQueue
import com.memfault.usagereporter.clientserver.clientServerUploadsDir
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration

@HiltWorker
class ReporterFileCleanupTask
@AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val reporterSettings: ReporterSettingsPreferenceProvider,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result =
        runAndTrackExceptions(jobName = "ReporterFileCleanupTask", NoOpJobReporter) {
            cleanupCacheDir()
            RealSendfileQueue.cleanup(clientServerUploadsDir(appContext), reporterSettings)
            Result.success()
        }

    private fun cleanupCacheDir() {
        val tempDir = appContext.cacheDir
        reporterTempStorageUsedMetric.record(tempDir.directorySize())
        val result = cleanupFiles(
            dir = tempDir,
            maxDirStorageBytes = reporterSettings.maxReporterTempStorageBytes,
            maxFileAge = reporterSettings.maxReporterTempStorageAge,
        )
        val deleted = result.deletedForStorageCount + result.deletedForAgeCount
        if (deleted > 0) {
            Logger.d("Deleted $deleted UsageReporter temp files to stay under storage limit")
            reporterTempDeletedMetric.incrementBy(deleted)
        }
    }

    companion object {
        private val CLEANUP_PERIOD = 12.hours.toJavaDuration()
        private val WORK_UNIQUE_NAME = "reporter_cleanup"
        private val reporterTempDeletedMetric = Reporting.report().counter(
            name = "reporter_temp_deleted",
            internal = true,
        )
        private val reporterTempStorageUsedMetric = Reporting.report().distribution(
            name = "reporter_temp_storage_bytes",
            aggregations = listOf(NumericAgg.LATEST_VALUE),
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
