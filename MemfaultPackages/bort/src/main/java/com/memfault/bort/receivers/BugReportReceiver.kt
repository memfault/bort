package com.memfault.bort.receivers

import android.content.Context
import android.content.Intent
import com.memfault.bort.BugReportFileUploadMetadata
import com.memfault.bort.INTENT_ACTION_BUGREPORT_FINISHED
import com.memfault.bort.INTENT_EXTRA_BUGREPORT_PATH
import com.memfault.bort.shared.Logger
import com.memfault.bort.uploader.enqueueFileUploadTask
import java.io.File

private const val WORK_TAG = "com.memfault.bort.work.tag.UPLOAD_BUGREPORT"

class BugReportReceiver : BortEnabledFilteringReceiver(
    setOf(INTENT_ACTION_BUGREPORT_FINISHED)
) {
    override fun onReceivedAndEnabled(context: Context, intent: Intent, action: String) {
        val bugreportPath = intent.getStringExtra(INTENT_EXTRA_BUGREPORT_PATH)
        Logger.v("Got bugreport path: $bugreportPath")
        Logger.logEvent("bugreport", "received")
        bugreportPath ?: return

        enqueueFileUploadTask(
            context = context,
            file = File(bugreportPath),
            metadata = BugReportFileUploadMetadata(),
            uploadConstraints = settingsProvider.httpApiSettings.uploadConstraints,
            debugTag = WORK_TAG,
        )
    }
}