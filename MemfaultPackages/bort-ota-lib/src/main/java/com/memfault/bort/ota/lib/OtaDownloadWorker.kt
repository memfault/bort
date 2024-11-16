package com.memfault.bort.ota.lib

import android.content.Context
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
import androidx.annotation.VisibleForTesting
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.memfault.bort.ota.lib.download.DownloadOtaService.Companion.setupForegroundNotification
import com.memfault.bort.ota.lib.download.NOTIFICATION_ID
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.NoOpJobReporter
import com.memfault.bort.shared.runAndTrackExceptions
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
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
    private val isAbDevice: IsAbDevice,
) : CoroutineWorker(appContext, params) {
    private var lastReportedPercentage = -1

    override suspend fun doWork(): Result = runAndTrackExceptions(jobName = "OtaDownloadWorker", NoOpJobReporter) {
        downloadWorkerRun(updater, otaRulesProvider, isAbDevice, applicationContext, ::maybeSetForeground)
    }

    /**
     * Create (or update with progress) foreground notification for this job.
     */
    private suspend fun maybeSetForeground(state: State) {
        val ota = state.ota() ?: return
        if (!otaRulesProvider.downloadRules(ota).useForegroundServiceForAbDownloads) {
            return
        }
        val progressPercentage = when (state) {
            is State.Finalizing -> 100
            is State.ReadyToInstall -> 100
            is State.RebootNeeded -> 100
            is State.UpdateDownloading -> state.progress
            else -> 0
        }

        // Avoid flooding with state updates unless the integer percentage changed
        if (progressPercentage != lastReportedPercentage) {
            val builder = setupForegroundNotification(applicationContext)
            builder.setProgress(100, progressPercentage, false)
            val foregroundInfo = ForegroundInfo(NOTIFICATION_ID, builder.build(), FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            setForeground(foregroundInfo)
            lastReportedPercentage = progressPercentage
        }
    }

    companion object {
        // Main logic broken out for testability.
        @VisibleForTesting
        internal suspend fun downloadWorkerRun(
            updater: Updater,
            otaRulesProvider: OtaRulesProvider,
            isAbDevice: IsAbDevice,
            appContext: Context,
            maybeSetForeground: suspend (State) -> Unit,
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
                if (isAbDevice()) {
                    // AB downloads are done by UpdateEngine in its own process (not by bort in a foreground service).
                    // Keep Bort running until the download is complete (by keeping this job running). This isn't
                    // foolproof, so we also schedule a task to wake bort up in-case it dies.
                    PeriodicDownloadCompletionWorker.schedule(appContext)

                    // If customer configured a foreground service, use it to keep this job running until the download
                    // completes.
                    maybeSetForeground(state)

                    updater.updateState.onEach { newState ->
                        // Update progress in notification.
                        maybeSetForeground(newState)
                    }.first { newState ->
                        // Block until not downloading (also add UpdateAvailable, as this can be the state when the task
                        // starts).
                        // This will keep the job running while the download is in-progress (either for the JobScheduler
                        // limit of 10 minutes, or for longer if a foreground service was configured above).
                        newState !is State.UpdateDownloading && newState !is State.UpdateAvailable
                    }
                    PeriodicDownloadCompletionWorker.cancel(appContext)
                }
                return Result.success()
            } else {
                return Result.retry()
            }
        }

        fun schedule(
            context: Context,
            settings: SoftwareUpdateSettingsProvider,
            rulesProvider: OtaRulesProvider,
            ota: Ota,
        ) {
            val rules = rulesProvider.downloadRules(ota)
            val networkConstraint = rules.overrideNetworkConstraint ?: settings.get().downloadNetworkTypeConstraint
            Logger.d("schedule OtaDownloadWorker: rules = $rules networkConstraint = $networkConstraint")

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
