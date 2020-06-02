package com.memfault.bort.receivers

import android.content.Context
import android.content.Intent
import com.memfault.bort.*
import com.memfault.bort.requester.BugReportRequester

class TestRequestBugreportReceiver : SingleActionBroadcastReceiver(
    "com.memfault.intent.action.TEST_REQUEST_BUGREPORT"
) {
    override fun onIntentReceived(context: Context, intent: Intent) {
        Logger.v("Requesting periodic bug report from TestRequestBugreportReceiver")
        BugReportRequester(
            context
        ).request()
    }
}
