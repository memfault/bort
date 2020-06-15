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
): Boolean {
    if (!Bort.appComponents().isEnabled()) {
        return false
    }

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
    return true
}

class BugReportRequester(
    private val context: Context
) {

    fun request() = requestBugReport(context)

    fun requestPeriodic(
        bugReportRequestIntervalHours: Long,
        initialDelayMinutes: Long? = null
    ) = PeriodicWorkRequestBuilder<BugReportRequestWorker>(
            bugReportRequestIntervalHours,
            TimeUnit.HOURS
        ).also { builder ->
            initialDelayMinutes?.let { delay ->
                builder.setInitialDelay(delay, TimeUnit.MINUTES)
            }
            Logger.test("Requesting bug report every $bugReportRequestIntervalHours hours")
        }.build().also {
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_UNIQUE_NAME_PERIODIC,
                    ExistingPeriodicWorkPolicy.KEEP,
                    it
                )
        }

    fun cancelPeriodic() {
        Logger.test("Cancelling periodic work $WORK_UNIQUE_NAME_PERIODIC")
        WorkManager.getInstance(context)
            .cancelUniqueWork(WORK_UNIQUE_NAME_PERIODIC)
    }

}

internal open class BugReportRequestWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : Worker(appContext, workerParameters) {

    override fun doWork(): Result =
        if (requestBugReport(applicationContext))
            Result.success() else Result.failure()
}
