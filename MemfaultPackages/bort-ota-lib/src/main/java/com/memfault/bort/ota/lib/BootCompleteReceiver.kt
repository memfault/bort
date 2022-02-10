package com.memfault.bort.ota.lib

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.memfault.bort.shared.BuildConfig
import com.memfault.bort.shared.INTENT_ACTION_OTA_SETTINGS_CHANGED
import com.memfault.bort.shared.InternalMetric
import com.memfault.bort.shared.InternalMetric.Companion.OTA_BOOT_COMPLETED
import com.memfault.bort.shared.InternalMetric.Companion.OTA_REBOOT_UPDATE_ERROR
import com.memfault.bort.shared.InternalMetric.Companion.OTA_REBOOT_UPDATE_SUCCESS
import com.memfault.bort.shared.InternalMetric.Companion.sendMetric
import com.memfault.bort.shared.JitterDelayProvider
import com.memfault.bort.shared.JitterDelayProvider.ApplyJitter.APPLY
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.goAsync
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * This receiver ensures that the initial state of the updater is correctly set once the device boots. It deletes
 * the previous update file if it exists, schedules jobs and sets the updater state to idle.
 *
 * Note: this class is referred to by fully qualified name in Constants.OTA_RECEIVER_CLASS in bort-shared.
 */
class BootCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (Intent.ACTION_BOOT_COMPLETED == intent?.action) {
            onBootComplete(context)
        } else if (INTENT_ACTION_OTA_SETTINGS_CHANGED == intent?.action) {
            onSettingsUpdated(context)
        }
    }

    private fun onBootComplete(context: Context) {
        deleteUpdateFileIfExists()
        val updater = context.applicationContext.updater()
        context.sendMetric(InternalMetric(key = OTA_BOOT_COMPLETED))
        Logger.i(
            TAG_BOOT_COMPLETED,
            mapOf(
                PARAM_APP_VERSION_NAME to BuildConfig.APP_VERSION_NAME,
                PARAM_APP_VERSION_CODE to BuildConfig.APP_VERSION_CODE,
            )
        )

        schedulePeriodicUpdateCheck(context, updater.settings().updateCheckIntervalMs)
        goAsync {
            when (val currentState = updater.updateState.value) {
                // When booting from this state, a reboot to update was requested. If the version changed, report this
                // as a success, otherwise as an error.
                is State.RebootedForInstallation -> {
                    val updateSuccessful = currentState.updatingFromVersion != updater.settings().currentVersion
                    context.sendMetric(
                        InternalMetric(
                            key = if (updateSuccessful) OTA_REBOOT_UPDATE_SUCCESS else OTA_REBOOT_UPDATE_ERROR
                        )
                    )
                    // Emit actions if clients want to let users know about update failures
                    updater.triggerEvent(
                        if (updateSuccessful) Event.RebootToUpdateSucceeded
                        else Event.RebootToUpdateFailed
                    )
                }
            }
            updater.setState(State.Idle)
        }
    }

    private fun onSettingsUpdated(context: Context) {
        val updater = context.applicationContext.updater()
        updater.updateSettings(context)
        schedulePeriodicUpdateCheck(context, updater.settings().updateCheckIntervalMs)
    }

    private fun schedulePeriodicUpdateCheck(context: Context, updateCheckIntervalMs: Long) {
        Logger.d("schedulePeriodicUpdateCheck: $updateCheckIntervalMs")
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<PeriodicSoftwareUpdateWorker>(
            updateCheckIntervalMs, TimeUnit.MILLISECONDS
        ).apply {
            setConstraints(constraints)
            setInitialDelay(JitterDelayProvider(applyJitter = APPLY).randomJitterDelay())
        }.build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_UPDATE_WORK, ExistingPeriodicWorkPolicy.REPLACE, request)
    }

    private fun deleteUpdateFileIfExists() {
        File(OTA_PATH).let { if (it.exists()) it.delete() }
    }
}
