package com.memfault.bort.requester

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.memfault.bort.APPLICATION_ID_MEMFAULT_USAGE_REPORTER
import com.memfault.bort.INTENT_ACTION_BUG_REPORT_START
import com.memfault.bort.Logger
import com.memfault.bort.isBuildTypeBlacklisted

internal class BugReportRequestWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : Worker(appContext, workerParameters) {

    override fun doWork(): Result {
        if (isBuildTypeBlacklisted()) {
            return Result.failure()
        }
        Logger.v("Sending $INTENT_ACTION_BUG_REPORT_START to $APPLICATION_ID_MEMFAULT_USAGE_REPORTER")
        Intent(INTENT_ACTION_BUG_REPORT_START).apply {
            component = ComponentName(
                APPLICATION_ID_MEMFAULT_USAGE_REPORTER,
                "${APPLICATION_ID_MEMFAULT_USAGE_REPORTER}.BugReportStartReceiver"
            )
        }.also {
            applicationContext.sendBroadcast(
                it,
                Manifest.permission.DUMP
            )
        }
        return Result.success()
    }
}


