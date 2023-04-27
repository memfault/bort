package com.memfault.bort

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.memfault.bort.shared.BugReportRequest
import com.memfault.bort.shared.INTENT_ACTION_BUG_REPORT_REQUEST_REPLY
import com.memfault.bort.shared.INTENT_EXTRA_BUG_REPORT_REQUEST_STATUS

enum class BugReportRequestStatus(val value: String) {
    OK_UPLOAD_QUEUED("OK_UPLOAD_QUEUED"),
    ERROR_ALREADY_PENDING("ERROR_ALREADY_PENDING"),
    ERROR_TIMEOUT("ERROR_TIMEOUT"),
    ERROR_SDK_NOT_ENABLED("ERROR_SDK_NOT_ENABLED"),
    ERROR_RATE_LIMITED("ERROR_RATE_LIMITED"),
}

fun BugReportRequest.broadcastReply(context: Context, status: BugReportRequestStatus) {
    replyReceiver?.let {
        Intent(INTENT_ACTION_BUG_REPORT_REQUEST_REPLY).apply {
            component = ComponentName(it.pkg, it.cls)
            applyToIntent(this)
            putExtra(INTENT_EXTRA_BUG_REPORT_REQUEST_STATUS, status.value)
        }.also {
            context.sendBroadcast(it)
        }
    }
}
