package com.memfault.bort.receivers

import android.content.Context
import android.content.Intent
import com.memfault.bort.*
import com.memfault.bort.requester.BugReportRequester

class MyPackageReplacedReceiver : BortEnabledFilteringReceiver(
    INTENT_ACTION_MY_PACKAGE_REPLACED
) {
    override fun onReceivedAndEnabled(context: Context, intent: Intent) {
        BugReportRequester(
            context
        ).requestPeriodic(
            settingsProvider.bugReportRequestIntervalHours()
        )
    }
}
