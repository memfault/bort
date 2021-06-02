package com.memfault.bort.receivers

import android.content.Context
import android.content.Intent
import com.memfault.bort.BugReportFileUploadPayload
import com.memfault.bort.BugReportRequestTimeoutTask
import com.memfault.bort.INTENT_ACTION_BUGREPORT_FINISHED
import com.memfault.bort.INTENT_EXTRA_BUGREPORT_PATH
import com.memfault.bort.fileExt.deleteSilently
import com.memfault.bort.shared.INTENT_EXTRA_BUG_REPORT_REQUEST_ID
import com.memfault.bort.shared.Logger
import com.memfault.bort.uploader.BugReportFileUploadContinuation
import com.memfault.bort.uploader.enqueueFileUploadTask
import java.io.File

private const val WORK_TAG = "com.memfault.bort.work.tag.UPLOAD_BUGREPORT"

class BugReportReceiver : BortEnabledFilteringReceiver(
    setOf(INTENT_ACTION_BUGREPORT_FINISHED)
) {
    override fun onReceivedAndEnabled(context: Context, intent: Intent, action: String) {
        val bugreportPath = intent.getStringExtra(INTENT_EXTRA_BUGREPORT_PATH)
        val requestId = intent.getStringExtra(INTENT_EXTRA_BUG_REPORT_REQUEST_ID)
        val (success, request) = getPendingRequest(requestId, context)
        Logger.v("Got bugreport path: $bugreportPath, request: $request")
        Logger.logEvent("bugreport", "received")
        bugreportPath ?: return

        val file = File(bugreportPath)
        if (!success) {
            Logger.w("Bug report request timed out, not uploading!")
            file.deleteSilently()
            return
        }

        goAsync {
            val dropBoxDataSourceEnabledAndSupported = settingsProvider.dropBoxSettings.dataSourceEnabled &&
                bortSystemCapabilities.supportsCaliperDropBoxTraces()

            enqueueFileUploadTask(
                context = context,
                file = file,
                payload = BugReportFileUploadPayload(
                    processingOptions = BugReportFileUploadPayload.ProcessingOptions(
                        processAnrs = !dropBoxDataSourceEnabledAndSupported,
                        processJavaExceptions = !dropBoxDataSourceEnabledAndSupported,
                        processLastKmsg = !dropBoxDataSourceEnabledAndSupported,
                        processRecoveryKmsg = !dropBoxDataSourceEnabledAndSupported,
                        processTombstones = !dropBoxDataSourceEnabledAndSupported,
                    ),
                    requestId = requestId,
                ),
                getUploadConstraints = settingsProvider.httpApiSettings::uploadConstraints,
                debugTag = WORK_TAG,
                continuation = request?.let {
                    BugReportFileUploadContinuation(
                        request = it,
                    )
                },
                /* Already a .zip file, not much to gain by recompressing */
                shouldCompress = false,
                jitterDelayProvider = jitterDelayProvider,
            )
        }
    }

    private fun getPendingRequest(requestId: String?, context: Context) =
        pendingBugReportRequestAccessor.compareAndSwap(null) {
            if (it == null) false // timeout task has run already
            else (it.requestId == requestId).also { matches ->
                if (matches) BugReportRequestTimeoutTask.cancel(context)
            }
        }
}
