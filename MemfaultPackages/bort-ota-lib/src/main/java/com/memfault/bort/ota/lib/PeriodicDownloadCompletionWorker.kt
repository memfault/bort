package com.memfault.bort.ota.lib

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.memfault.bort.shared.runAndTrackExceptions
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

/**
 * A periodic worker that triggers an update check.
 */
@HiltWorker
class PeriodicDownloadCompletionWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val updater: Updater,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = runAndTrackExceptions(jobName = "PeriodicSoftwareUpdateWorker") {
        // This doesn't do anything except wake up the app.

        if (updater.badCurrentUpdateState() !is State.UpdateDownloading) {
            // If not downloading, then cancel this task (we only need it to run again if download hasn't finished).
            cancel(appContext)
        }
        Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PeriodicDownloadCompletionWorker>(
                15.minutes.toJavaDuration()
            ).apply {
                addTag(PERIODIC_DOWNLOAD_CHECK)
            }.build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC_DOWNLOAD_CHECK, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_DOWNLOAD_CHECK)
        }
    }
}
