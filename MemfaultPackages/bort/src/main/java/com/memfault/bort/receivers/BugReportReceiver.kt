package com.memfault.bort.receivers

import android.content.Context
import android.content.Intent
import com.memfault.bort.BugReportRequestStatus
import com.memfault.bort.BugReportRequestTimeoutTask
import com.memfault.bort.INTENT_ACTION_BUGREPORT_FINISHED
import com.memfault.bort.INTENT_EXTRA_BUGREPORT_PATH
import com.memfault.bort.PendingBugReportRequestAccessor
import com.memfault.bort.ProcessingOptions
import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.broadcastReply
import com.memfault.bort.clientserver.MarMetadata.BugReportMarMetadata
import com.memfault.bort.fileExt.deleteSilently
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.settings.ZipCompressionLevel
import com.memfault.bort.shared.INTENT_EXTRA_BUG_REPORT_REQUEST_ID
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.goAsync
import com.memfault.bort.time.CombinedTimeProvider
import com.memfault.bort.uploader.EnqueueUpload
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class BugReportReceiver : BortEnabledFilteringReceiver(
    setOf(INTENT_ACTION_BUGREPORT_FINISHED),
) {
    @Inject lateinit var settingsProvider: SettingsProvider

    @Inject lateinit var pendingBugReportRequestAccessor: PendingBugReportRequestAccessor

    @Inject lateinit var temporaryFileFactory: TemporaryFileFactory

    @Inject lateinit var enqueueUpload: EnqueueUpload

    @Inject lateinit var combinedTimeProvider: CombinedTimeProvider

    @Inject lateinit var zipCompressionLevel: ZipCompressionLevel

    override fun onReceivedAndEnabled(context: Context, intent: Intent, action: String) {
        val bugreportPath = intent.getStringExtra(INTENT_EXTRA_BUGREPORT_PATH)
        val requestId = intent.getStringExtra(INTENT_EXTRA_BUG_REPORT_REQUEST_ID)
        val (success, request) = getPendingRequest(requestId, context)
        Logger.v("Got bugreport path: $bugreportPath, request: $request")
        bugreportPath ?: return

        val file = File(bugreportPath)
        if (!success) {
            Logger.w("Bug report request timed out, not uploading!")
            file.deleteSilently()
            return
        }
        val collectionTime = combinedTimeProvider.now()

        goAsync {
            val dropBoxDataSourceEnabledAndSupported = settingsProvider.dropBoxSettings.dataSourceEnabled

            enqueueUpload.enqueue(
                file = file,
                metadata = BugReportMarMetadata(
                    bugReportFileName = file.name,
                    processingOptions = ProcessingOptions(
                        processAnrs = !dropBoxDataSourceEnabledAndSupported,
                        processJavaExceptions = !dropBoxDataSourceEnabledAndSupported,
                        processLastKmsg = !dropBoxDataSourceEnabledAndSupported,
                        processRecoveryKmsg = !dropBoxDataSourceEnabledAndSupported,
                        processTombstones = !dropBoxDataSourceEnabledAndSupported,
                    ),
                    requestId = requestId,
                ),
                collectionTime = collectionTime,
            )
            request?.broadcastReply(context, BugReportRequestStatus.OK_UPLOAD_QUEUED)
        }
    }

    private fun getPendingRequest(requestId: String?, context: Context) =
        pendingBugReportRequestAccessor.compareAndSwap(null) {
            if (it == null) {
                false // timeout task has run already
            } else {
                (it.requestId == requestId).also { matches ->
                    if (matches) BugReportRequestTimeoutTask.cancel(context)
                }
            }
        }
}
