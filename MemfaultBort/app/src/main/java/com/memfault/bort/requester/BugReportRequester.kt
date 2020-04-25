package com.memfault.bort.requester

import android.content.Context
import androidx.work.*
import com.memfault.bort.SettingsProvider
import java.util.*
import java.util.concurrent.TimeUnit

private const val WORK_UNIQUE_NAME_PERIODIC = "com.memfault.bort.work.REQUEST_PERIODIC"
private const val WORK_UNIQUE_NAME = "com.memfault.bort.work.REQUEST"

class BugReportRequester(
    private val context: Context,
    private val settingsProvider: SettingsProvider
) {

    fun request(): UUID =
        OneTimeWorkRequestBuilder<BugReportRequestWorker>().build().also {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    WORK_UNIQUE_NAME,
                    // Replace so that any observers have the fresh request ID
                    ExistingWorkPolicy.REPLACE,
                    it
                )
        }.id

    fun requestPeriodic() =
        PeriodicWorkRequestBuilder<BugReportRequestWorker>(
            settingsProvider.bugReportRequestIntervalHours(),
            TimeUnit.HOURS
        ).build().also {
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_UNIQUE_NAME_PERIODIC,
                    ExistingPeriodicWorkPolicy.REPLACE,
                    it
                )
        }

}
