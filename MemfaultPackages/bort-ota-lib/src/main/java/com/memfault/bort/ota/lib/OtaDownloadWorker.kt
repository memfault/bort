package com.memfault.bort.ota.lib

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.runAndTrackExceptions
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

/**
 * A periodic worker that triggers an OTA download.
 */
@HiltWorker
class OtaDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val updater: Updater,
    private val otaRulesProvider: OtaRulesProvider,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = runAndTrackExceptions(jobName = "OtaDownloadWorker") {
        downloadWorkerRun(updater, otaRulesProvider)
    }

    companion object {
        // Main logic broken out for testability.
        @VisibleForTesting
        internal suspend fun downloadWorkerRun(
            updater: Updater,
            otaRulesProvider: OtaRulesProvider,
        ): Result {
            Logger.d("OtaDownloadWorker")
            val state = updater.badCurrentUpdateState()
            if (state !is State.UpdateAvailable) {
                Logger.d("OtaDownloadWorker: state !is State.UpdateAvailable")
                return Result.failure()
            }
            val ota = state.ota

            val doDownload = OtaRules.shouldAutoDownloadOtaUpdate(
                ota = ota,
                otaRulesProvider = otaRulesProvider,
            )

            if (doDownload) {
                updater.perform(Action.DownloadUpdate)
                return Result.success()
            } else {
                return Result.retry()
            }
        }

        fun schedule(
            context: Context,
            settings: SoftwareUpdateSettingsProvider,
            rulesProvider: OtaRulesProvider,
            ota: Ota
        ) {
            Logger.d("schedule OtaDownloadWorker")

            val rules = rulesProvider.downloadRules(ota)
            val networkConstraint = rules.overrideNetworkConstraint ?: settings.get().downloadNetworkTypeConstraint

            // Note: we can't use a setRequiresDeviceIdle() constraint - this is incompatible with using a backoff
            // policy.
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(networkConstraint)
                .setRequiresStorageNotLow(rules.requiresStorageNotLowConstraint)
                .setRequiresBatteryNotLow(rules.requiresBatteryNotLowConstraint)
                .setRequiresCharging(rules.requiresChargingConstraint)
                .build()

            val request = OneTimeWorkRequestBuilder<OtaDownloadWorker>().apply {
                setConstraints(constraints)
                addTag(SCHEDULED_DOWNLOAD_WORK)
                setBackoffCriteria(BackoffPolicy.LINEAR, 15.minutes.toJavaDuration())
            }.build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(SCHEDULED_DOWNLOAD_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
