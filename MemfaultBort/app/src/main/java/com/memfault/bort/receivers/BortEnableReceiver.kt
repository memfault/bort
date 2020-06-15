package com.memfault.bort.receivers

import android.content.Context
import android.content.Intent
import com.memfault.bort.*
import com.memfault.bort.requester.BugReportRequester

class BortEnableReceiver : SingleActionReceiver(
    INTENT_ACTION_BORT_ENABLE
) {

    override fun onIntentReceived(context: Context, intent: Intent) {
        val isNowEnabled = intent.getBooleanExtra(
            INTENT_EXTRA_BORT_ENABLED,
            !settingsProvider.isRuntimeEnableRequired()
        )
        val wasEnabled = bortEnabledProvider.isEnabled()
        Logger.test("wasEnabled=$wasEnabled isNowEnabled=$isNowEnabled")
        if (wasEnabled == isNowEnabled) {
            return
        }

        bortEnabledProvider.setEnabled(isNowEnabled)

        BugReportRequester(context).also {
            if (isNowEnabled) {
                it.requestPeriodic(
                    settingsProvider.bugReportRequestIntervalHours()
                )
            } else {
                it.cancelPeriodic()
            }
        }
    }
}
