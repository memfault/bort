package com.memfault.bort.bugreport

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.memfault.bort.shared.BugReportRequest

private const val INTENT_ACTION_BUG_REPORT_REQUEST_REPLY = "com.memfault.intent.action.BUG_REPORT_REQUEST_REPLY"
private const val INTENT_EXTRA_BUG_REPORT_REQUEST_STATUS = "com.memfault.intent.extra.BUG_REPORT_REQUEST_STATUS"

enum class BugReportRequestStatus(val value: String) {
    OK_REQUESTED("OK_REQUESTED"),
    OK_GENERATING("OK_GENERATING"),
    OK_GENERATED("OK_GENERATED"),
    OK_UPLOAD_QUEUED("OK_UPLOAD_QUEUED"),
    ERROR_ALREADY_PENDING("ERROR_ALREADY_PENDING"),
    ERROR_CONSTRAINTS_NOT_SATISFIED("ERROR_CONSTRAINTS_NOT_SATISFIED"),
    ERROR_GENERATING_TIMEOUT("ERROR_TIMEOUT"),
    ERROR_GENERATED_TIMEOUT("ERROR_GENERATED_TIMEOUT"),
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
