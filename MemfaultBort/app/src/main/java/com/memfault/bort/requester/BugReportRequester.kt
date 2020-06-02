package com.memfault.bort.requester

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.work.*
import com.memfault.bort.APPLICATION_ID_MEMFAULT_USAGE_REPORTER
import com.memfault.bort.Bort
import com.memfault.bort.INTENT_ACTION_BUG_REPORT_START
import com.memfault.bort.Logger
import java.util.concurrent.TimeUnit

private const val WORK_UNIQUE_NAME_PERIODIC = "com.memfault.bort.work.REQUEST_PERIODIC"

internal fun requestBugReport(
    context: Context
) {
    Logger.v("Sending $INTENT_ACTION_BUG_REPORT_START to $APPLICATION_ID_MEMFAULT_USAGE_REPORTER")
    Intent(INTENT_ACTION_BUG_REPORT_START).apply {
        component = ComponentName(
            APPLICATION_ID_MEMFAULT_USAGE_REPORTER,
            "$APPLICATION_ID_MEMFAULT_USAGE_REPORTER.BugReportStartReceiver"
        )
    }.also {
        context.sendBroadcast(
            it,
            Manifest.permission.DUMP
        )
    }
}

class BugReportRequester(
    private val context: Context
) {

    fun request() = requestBugReport(context)

    fun requestPeriodic(bugReportRequestIntervalHours: Long) =
        PeriodicWorkRequestBuilder<BugReportRequestWorker>(
            bugReportRequestIntervalHours,
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

internal open class BugReportRequestWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : Worker(appContext, workerParameters) {

    override fun doWork(): Result {
        val settingsProvider = Bort.appComponents().settingsProvider

        if (settingsProvider.isBuildTypeBlacklisted()) {
            return Result.failure()
        }

        requestBugReport(applicationContext)

        return Result.success()
    }
}
