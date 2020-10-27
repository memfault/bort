package com.memfault.bort.requester

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.memfault.bort.Bort
import com.memfault.bort.shared.APPLICATION_ID_MEMFAULT_USAGE_REPORTER
import com.memfault.bort.shared.BugReportOptions
import com.memfault.bort.shared.INTENT_ACTION_BUG_REPORT_START
import com.memfault.bort.shared.Logger
import java.util.concurrent.TimeUnit

private const val WORK_UNIQUE_NAME_PERIODIC = "com.memfault.bort.work.REQUEST_PERIODIC"

private const val MINIMAL_INPUT_DATA_KEY = "minimal"

private fun BugReportOptions.toInputData(): Data = workDataOf(
    MINIMAL_INPUT_DATA_KEY to minimal
)

private fun Data.toBugReportOptions(): BugReportOptions = BugReportOptions(
    minimal = getBoolean(MINIMAL_INPUT_DATA_KEY, false)
)

internal fun requestBugReport(
    context: Context,
    options: BugReportOptions
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
        options.applyToIntent(this)
    }.also {
        context.sendBroadcast(it)
    }
    return true
}

class BugReportRequester(
    private val context: Context
) {

    fun request(options: BugReportOptions) = requestBugReport(context, options)

    fun requestPeriodic(
        requestIntervalHours: Long,
        options: BugReportOptions,
        initialDelayMinutes: Long? = null
    ) = PeriodicWorkRequestBuilder<BugReportRequestWorker>(
        requestIntervalHours,
        TimeUnit.HOURS
    ).also { builder ->
        initialDelayMinutes?.let { delay ->
            builder.setInitialDelay(delay, TimeUnit.MINUTES)
            builder.setInputData(options.toInputData())
        }
        Logger.test("Requesting bug report every $requestIntervalHours hours")
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
        if (requestBugReport(applicationContext, inputData.toBugReportOptions()))
            Result.success() else Result.failure()
}
