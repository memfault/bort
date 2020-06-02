package com.memfault.bort.receivers

import android.content.Context
import android.content.Intent
import com.memfault.bort.*
import com.memfault.bort.uploader.BugReportUploadScheduler
import java.io.File

class BugReportReceiver : SingleActionBroadcastReceiver(
    INTENT_ACTION_BUGREPORT_FINISHED
) {
    override fun onIntentReceived(context: Context, intent: Intent) {
        val bugreportPath = intent.getStringExtra(INTENT_EXTRA_BUGREPORT_PATH)
        Logger.v("Got bugreport path: $bugreportPath")
        bugreportPath ?: return

        val bugreportFile = File(bugreportPath)

        BugReportUploadScheduler(
            context,
            settingsProvider.bugReportNetworkConstraint()
        ).enqueue(bugreportFile)
    }
}
