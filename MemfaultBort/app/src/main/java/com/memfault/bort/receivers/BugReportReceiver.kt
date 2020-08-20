package com.memfault.bort.receivers

import android.content.Context
import android.content.Intent
import com.memfault.bort.*
import com.memfault.bort.uploader.BugReportUploader
import com.memfault.bort.uploader.makeBugreportUploadInputData
import java.io.File

private const val WORK_TAG = "com.memfault.bort.work.tag.UPLOAD"

class BugReportReceiver : BortEnabledFilteringReceiver(
    setOf(INTENT_ACTION_BUGREPORT_FINISHED)
) {
    override fun onReceivedAndEnabled(context: Context, intent: Intent, action: String) {
        val bugreportPath = intent.getStringExtra(INTENT_EXTRA_BUGREPORT_PATH)
        Logger.v("Got bugreport path: $bugreportPath")
        Logger.logEvent("bugreport", "received")
        bugreportPath ?: return

        val bugreportFile = File(bugreportPath)

        enqueueWorkOnce<BugReportUploader>(
            context,
            makeBugreportUploadInputData(bugreportFile.toString())
        ) {
            setConstraints(settingsProvider.uploadConstraints())
            addTag(WORK_TAG)
        }
    }
}
