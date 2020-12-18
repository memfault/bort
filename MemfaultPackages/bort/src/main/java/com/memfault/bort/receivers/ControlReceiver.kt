package com.memfault.bort.receivers

import android.content.Context
import android.content.Intent
import com.memfault.bort.INTENT_ACTION_BORT_ENABLE
import com.memfault.bort.INTENT_ACTION_BUG_REPORT_REQUESTED
import com.memfault.bort.INTENT_EXTRA_BORT_ENABLED
import com.memfault.bort.requester.BugReportRequester
import com.memfault.bort.requester.MetricsCollectionRequester
import com.memfault.bort.requester.requestBugReport
import com.memfault.bort.shared.BugReportOptions
import com.memfault.bort.shared.Logger
import com.memfault.bort.uploader.sendSdkEnabledEvent

/** Base receiver to handle events that control the SDK. */
abstract class BaseControlReceiver : FilteringReceiver(
    setOf(INTENT_ACTION_BORT_ENABLE, INTENT_ACTION_BUG_REPORT_REQUESTED)
) {

    private fun onBugReportRequested(context: Context, options: BugReportOptions) {
        Logger.v("Received request for bug report")
        if (!bortEnabledProvider.isEnabled()) {
            Logger.w("Bort not enabled; not sending request")
            return
        }
        requestBugReport(context, options)
    }

    private fun onBortEnabled(context: Context, intent: Intent) {
        val isNowEnabled = intent.getBooleanExtra(
            INTENT_EXTRA_BORT_ENABLED,
            !settingsProvider.isRuntimeEnableRequired
        )
        val wasEnabled = bortEnabledProvider.isEnabled()
        Logger.test("wasEnabled=$wasEnabled isNowEnabled=$isNowEnabled")
        Logger.logEventBortSdkEnabled(isNowEnabled)
        if (wasEnabled == isNowEnabled) {
            return
        }

        bortEnabledProvider.setEnabled(isNowEnabled)

        listOf(
            MetricsCollectionRequester(context, settingsProvider.metricsSettings),
            BugReportRequester(context, settingsProvider.bugReportSettings),
        ).forEach {
            if (isNowEnabled) {
                it.startPeriodic()
            } else {
                it.cancelPeriodic()
            }
        }

        sendSdkEnabledEvent(
            ingressService,
            isNowEnabled,
            deviceIdProvider,
            settingsProvider.sdkVersionInfo
        )
    }

    override fun onIntentReceived(context: Context, intent: Intent, action: String) {
        when (action) {
            INTENT_ACTION_BUG_REPORT_REQUESTED -> onBugReportRequested(context, BugReportOptions.fromIntent(intent))
            INTENT_ACTION_BORT_ENABLE -> onBortEnabled(context, intent)
        }
    }
}

@Deprecated("Please target ControlReceiver")
class RequestBugReportReceiver : BaseControlReceiver()

@Deprecated("Please target ControlReceiver")
class BortEnableReceiver : BaseControlReceiver()

class ShellControlReceiver : BaseControlReceiver()

class ControlReceiver : BaseControlReceiver()
