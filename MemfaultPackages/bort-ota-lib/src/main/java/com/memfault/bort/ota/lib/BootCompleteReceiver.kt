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
import com.memfault.bort.shared.InternalMetric
import com.memfault.bort.shared.InternalMetric.Companion.OTA_BOOT_COMPLETED
import com.memfault.bort.shared.InternalMetric.Companion.sendMetric
import com.memfault.bort.shared.Logger
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * This receiver ensures that the initial state of the updater is correctly set once the device boots. It deletes
 * the previous update file if it exists, schedules jobs and sets the updater state to idle.
 */
class BootCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (Intent.ACTION_BOOT_COMPLETED == intent?.action) {
            onBootComplete(context)
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

        schedulePeriodicUpdateCheck(context, updater.settings.updateCheckIntervalMs)
        goAsync {
            updater.setState(State.Idle)
        }
    }

    private fun schedulePeriodicUpdateCheck(context: Context, updateCheckIntervalMs: Long) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<PeriodicSoftwareUpdateWorker>(
            updateCheckIntervalMs, TimeUnit.MILLISECONDS
        ).apply {
            setConstraints(constraints)
        }.build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_UPDATE_WORK, ExistingPeriodicWorkPolicy.REPLACE, request)
    }

    private fun deleteUpdateFileIfExists() {
        File(OTA_PATH).let { if (it.exists()) it.delete() }
    }
}

private fun BroadcastReceiver.goAsync(
    coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    block: suspend () -> Unit
) {
    val result = goAsync()
    coroutineScope.launch {
        try {
            block()
        } finally {
            // Always call finish(), even if the coroutineScope was cancelled
            result.finish()
        }
    }
}
