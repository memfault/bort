package com.memfault.usagereporter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.memfault.bort.android.SystemPropertiesProxy
import com.memfault.bort.bugreport.BugReportRequestStatus.OK_GENERATING
import com.memfault.bort.bugreport.broadcastReply
import com.memfault.bort.shared.BugReportRequest
import com.memfault.bort.shared.INTENT_ACTION_BUG_REPORT_START
import com.memfault.bort.shared.Logger

private const val SERVICE_MEMFAULT_DUMPSTATE_RUNNER = "memfault_dumpstate_runner"
private const val SERVICE_MEMFAULT_DUMPSTATE_RUNNER_N = "memfault_dumpr"
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
        request.broadcastReply(context, OK_GENERATING)

        Logger.v("Starting $SERVICE_MEMFAULT_DUMPSTATE_RUNNER (options=$request)")
        SystemPropertiesProxy.setSafe(DUMPSTATE_MEMFAULT_MINIMAL_PROPERTY, if (request.options.minimal) "1" else "0")
        SystemPropertiesProxy.setSafe(DUMPSTATE_MEMFAULT_REQUEST_ID, request.requestId ?: "")
        val serviceName =
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                SERVICE_MEMFAULT_DUMPSTATE_RUNNER_N
            } else {
                SERVICE_MEMFAULT_DUMPSTATE_RUNNER
            }
        SystemPropertiesProxy.setSafe("ctl.start", serviceName)
    }
}
