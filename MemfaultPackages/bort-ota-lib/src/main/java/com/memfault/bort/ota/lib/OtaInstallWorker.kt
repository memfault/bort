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
 * A periodic worker that triggers an OTA install/reboot.
 */
@HiltWorker
class OtaInstallWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val updater: Updater,
    private val otaRulesProvider: OtaRulesProvider,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = runAndTrackExceptions(jobName = "OtaDownloadWorker") {
        installWorkerRun(updater, otaRulesProvider)
    }

    companion object {
        // Main logic broken out for testability.
        @VisibleForTesting
        internal suspend fun installWorkerRun(
            updater: Updater,
            otaRulesProvider: OtaRulesProvider,
        ): Result {
            Logger.d("OtaInstallWorker")
            val state = updater.badCurrentUpdateState()
            val isAbRebootNeeded: Boolean
            val isRecoveryInstallNeeded: Boolean
            val ota: Ota
            when (state) {
                is State.RebootNeeded -> {
                    isAbRebootNeeded = true
                    isRecoveryInstallNeeded = false
                    ota = state.ota
                }

                is State.ReadyToInstall -> {
                    isAbRebootNeeded = false
                    isRecoveryInstallNeeded = true
                    ota = state.ota
                }

                else -> {
                    Logger.d("OtaInstallWorker: invalid state: $state")
                    return Result.failure()
                }
            }

            val doInstall = OtaRules.shouldAutoInstallOtaUpdate(
                ota = ota,
                otaRulesProvider = otaRulesProvider,
            )

            if (doInstall) {
                if (isAbRebootNeeded) {
                    updater.perform(Action.Reboot)
                } else if (isRecoveryInstallNeeded) {
                    updater.perform(Action.InstallUpdate)
                }
            } else {
                return Result.retry()
            }
            return Result.success()
        }

        fun schedule(context: Context, rulesProvider: OtaRulesProvider, ota: Ota) {
            Logger.d("schedule OtaInstallWorker")

            val rules = rulesProvider.installRules(ota)

            // Note: we can't use a setRequiresDeviceIdle() constraint - this is incompatible with using a backoff
            // policy.
            val constraints = Constraints.Builder()
                .setRequiresStorageNotLow(rules.requiresStorageNotLowConstraint)
                .setRequiresBatteryNotLow(rules.requiresBatteryNotLowConstraint)
                .setRequiresCharging(rules.requiresChargingConstraint)
                .build()

            val request = OneTimeWorkRequestBuilder<OtaInstallWorker>().apply {
                setConstraints(constraints)
                addTag(SCHEDULED_INSTALL_WORK)
                setBackoffCriteria(BackoffPolicy.LINEAR, 15.minutes.toJavaDuration())
            }.build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    SCHEDULED_INSTALL_WORK,
                    ExistingWorkPolicy.APPEND_OR_REPLACE, request
                )
        }
    }
}
