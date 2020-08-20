package com.memfault.bort.receivers

import android.content.Context
import android.content.Intent
import com.memfault.bort.*
import com.memfault.bort.requester.BugReportRequester

class SystemEventReceiver : BortEnabledFilteringReceiver(
    setOf(INTENT_ACTION_BOOT_COMPLETED, INTENT_ACTION_MY_PACKAGE_REPLACED)
) {

    private fun onPackageReplaced(context: Context) {
        BugReportRequester(
            context
        ).requestPeriodic(
            settingsProvider.bugReportRequestIntervalHours()
        )
    }

    private fun onBootCompleted(context: Context) {
        Logger.logEvent("boot")
        BugReportRequester(
            context
        ).requestPeriodic(
            settingsProvider.bugReportRequestIntervalHours(),
            settingsProvider.firstBugReportDelayAfterBootMinutes()
        )
    }

    override fun onReceivedAndEnabled(context: Context, intent: Intent, action: String) {
        when (action) {
            INTENT_ACTION_MY_PACKAGE_REPLACED -> onPackageReplaced(context)
            INTENT_ACTION_BOOT_COMPLETED -> onBootCompleted(context)
        }
    }
}
