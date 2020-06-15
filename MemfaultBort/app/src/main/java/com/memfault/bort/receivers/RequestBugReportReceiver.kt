package com.memfault.bort.receivers

import android.content.Context
import android.content.Intent
import com.memfault.bort.*
import com.memfault.bort.requester.BugReportRequester

class RequestBugReportReceiver : BortEnabledFilteringReceiver(
    "com.memfault.intent.action.REQUEST_BUG_REPORT"
) {
    override fun onReceivedAndEnabled(context: Context, intent: Intent) {
        Logger.v("Received request for bug report")
        BugReportRequester(
            context
        ).request()
    }
}
