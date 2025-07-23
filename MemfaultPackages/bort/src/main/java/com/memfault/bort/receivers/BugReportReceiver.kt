package com.memfault.bort.receivers

import android.content.Context
import android.content.Intent
import com.memfault.bort.INTENT_ACTION_BUGREPORT_FINISHED
import com.memfault.bort.bugreport.ReceiveBugReportIntentUseCase
import com.memfault.bort.shared.goAsync
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BugReportReceiver : BortEnabledFilteringReceiver(
    setOf(INTENT_ACTION_BUGREPORT_FINISHED),
) {
    @Inject lateinit var receiveBugReportIntentUseCase: ReceiveBugReportIntentUseCase

    override fun onReceivedAndEnabled(context: Context, intent: Intent, action: String) = goAsync {
        if (action == INTENT_ACTION_BUGREPORT_FINISHED) {
            receiveBugReportIntentUseCase.onReceivedBugReport(intent)
        }
    }
}
