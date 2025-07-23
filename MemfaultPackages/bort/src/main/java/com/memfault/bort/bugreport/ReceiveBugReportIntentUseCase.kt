package com.memfault.bort.bugreport

import android.app.Application
import android.content.Intent
import com.memfault.bort.BugReportRequestTimeoutTask
import com.memfault.bort.INTENT_EXTRA_BUGREPORT_PATH
import com.memfault.bort.ProcessingOptions
import com.memfault.bort.clientserver.MarMetadata.BugReportMarMetadata
import com.memfault.bort.fileExt.deleteSilently
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.shared.INTENT_EXTRA_BUG_REPORT_REQUEST_ID
import com.memfault.bort.shared.Logger
import com.memfault.bort.time.CombinedTimeProvider
import com.memfault.bort.uploader.EnqueueUpload
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject

interface ReceiveBugReportIntentUseCase {
    suspend fun onReceivedBugReport(intent: Intent)
}

@ContributesBinding(SingletonComponent::class)
class RealReceiveBugReportIntentUseCase
@Inject constructor(
    private val application: Application,
    private val settingsProvider: SettingsProvider,
    private val pendingBugReportRequestAccessor: PendingBugReportRequestAccessor,
    private val enqueueUpload: EnqueueUpload,
    private val combinedTimeProvider: CombinedTimeProvider,
) : ReceiveBugReportIntentUseCase {
    override suspend fun onReceivedBugReport(intent: Intent) {
        val bugreportPath = intent.getStringExtra(INTENT_EXTRA_BUGREPORT_PATH)
        val requestId = intent.getStringExtra(INTENT_EXTRA_BUG_REPORT_REQUEST_ID)
        val (success, request) = getPendingRequest(requestId)

        Logger.v("Got bugreport path: $bugreportPath, request: $request")
        bugreportPath ?: return

        val file = File(bugreportPath)
        if (!success) {
            Logger.w("Bug report request timed out, not uploading!")
            file.deleteSilently()
            return
        }

        val collectionTime = combinedTimeProvider.now()
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
                request = request,
            ),
            collectionTime = collectionTime,
        )
        request?.broadcastReply(application, BugReportRequestStatus.OK_UPLOAD_QUEUED)
    }

    private fun getPendingRequest(requestId: String?) =
        pendingBugReportRequestAccessor.compareAndSwap(null) { request ->
            if (request == null) {
                false // timeout task has run already
            } else {
                val matches = request.requestId == requestId
                if (matches) {
                    BugReportRequestTimeoutTask.cancel(application)
                }
                matches
            }
        }
}
