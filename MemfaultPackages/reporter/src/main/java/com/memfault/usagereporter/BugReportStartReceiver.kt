package com.memfault.usagereporter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.memfault.bort.android.SystemPropertiesProxy
import com.memfault.bort.shared.BugReportRequest
import com.memfault.bort.shared.INTENT_ACTION_BUG_REPORT_START
import com.memfault.bort.shared.Logger

private const val SERVICE_MEMFAULT_DUMPSTATE_RUNNER = "memfault_dumpstate_runner"
private const val DUMPSTATE_MEMFAULT_MINIMAL_PROPERTY = "dumpstate.memfault.minimal"
private const val DUMPSTATE_MEMFAULT_REQUEST_ID = "dumpstate.memfault.requestid"

class BugReportStartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != INTENT_ACTION_BUG_REPORT_START) return

        val request = try {
            BugReportRequest.fromIntent(intent)
        } catch (e: Exception) {
            Logger.e("Invalid bug report request", e)
            return
        }
        Logger.v("Starting $SERVICE_MEMFAULT_DUMPSTATE_RUNNER (options=$request)")
        SystemPropertiesProxy.setSafe(DUMPSTATE_MEMFAULT_MINIMAL_PROPERTY, if (request.options.minimal) "1" else "0")
        SystemPropertiesProxy.setSafe(DUMPSTATE_MEMFAULT_REQUEST_ID, request.requestId ?: "")
        SystemPropertiesProxy.setSafe("ctl.start", SERVICE_MEMFAULT_DUMPSTATE_RUNNER)
    }
}
