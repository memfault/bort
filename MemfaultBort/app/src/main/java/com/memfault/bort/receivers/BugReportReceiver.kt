package com.memfault.bort.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.memfault.bort.INTENT_ACTION_BUGREPORT_FINISHED
import com.memfault.bort.INTENT_EXTRA_BUGREPORT_PATH
import com.memfault.bort.Logger
import com.memfault.bort.SettingsProvider
import com.memfault.bort.uploader.BugReportUploadScheduler
import java.io.File

class BugReportReceiver : BroadcastReceiver() {
    private val settingsProvider = SettingsProvider()

    override fun onReceive(context: Context?, intent: Intent?) {
        Logger.d("onReceive ${intent?.action}")
        intent ?: return
        context ?: return
        when {
            intent.action != INTENT_ACTION_BUGREPORT_FINISHED -> return
        }
        val bugreportPath = intent.getStringExtra(INTENT_EXTRA_BUGREPORT_PATH)
        Logger.v("Got bugreport path: $bugreportPath")
        bugreportPath ?: return

        val bugreportFile = File(bugreportPath)

        BugReportUploadScheduler(
            context,
            settingsProvider
        ).enqueue(bugreportFile)
    }
}
