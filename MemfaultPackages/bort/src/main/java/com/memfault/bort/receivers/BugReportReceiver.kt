package com.memfault.bort.receivers

import android.content.Context
import android.content.Intent
import com.memfault.bort.BortSystemCapabilities
import com.memfault.bort.BugReportFileUploadPayload
import com.memfault.bort.BugReportRequestTimeoutTask
import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.INTENT_ACTION_BUGREPORT_FINISHED
import com.memfault.bort.INTENT_EXTRA_BUGREPORT_PATH
import com.memfault.bort.PendingBugReportRequestAccessor
import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.addFileToZip
import com.memfault.bort.fileExt.deleteSilently
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.shared.INTENT_EXTRA_BUG_REPORT_REQUEST_ID
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.goAsync
import com.memfault.bort.time.CombinedTimeProvider
import com.memfault.bort.uploader.BugReportFileUploadContinuation
import com.memfault.bort.uploader.EnqueueUpload
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val WORK_TAG = "com.memfault.bort.work.tag.UPLOAD_BUGREPORT"

@AndroidEntryPoint
class BugReportReceiver : BortEnabledFilteringReceiver(
    setOf(INTENT_ACTION_BUGREPORT_FINISHED)
) {
    @Inject lateinit var settingsProvider: SettingsProvider
    @Inject lateinit var pendingBugReportRequestAccessor: PendingBugReportRequestAccessor
    @Inject lateinit var bortSystemCapabilities: BortSystemCapabilities
    @Inject lateinit var temporaryFileFactory: TemporaryFileFactory
    @Inject lateinit var enqueueUpload: EnqueueUpload
    @Inject lateinit var combinedTimeProvider: CombinedTimeProvider
    @Inject lateinit var deviceInfoProvider: DeviceInfoProvider

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
        val collectionTime = combinedTimeProvider.now()

        goAsync {
            withContext(Dispatchers.IO) {
                // Add bort internal logs to the bugreport .zip file, if we have any.
                temporaryFileFactory.createTemporaryFile(
                    prefix = "internallog",
                    suffix = null,
                ).useFile { tempFile, _ ->
                    val hasInternalLogFile = Logger.uploadAndDeleteLogFile(tempFile)
                    if (hasInternalLogFile) {
                        addFileToZip(zipFile = file, newFile = tempFile, newfileName = "bort_logs.txt")
                    }
                }
            }

            val dropBoxDataSourceEnabledAndSupported = settingsProvider.dropBoxSettings.dataSourceEnabled &&
                bortSystemCapabilities.supportsCaliperDropBoxTraces()
            val deviceInfo = deviceInfoProvider.getDeviceInfo()

            enqueueUpload.enqueue(
                file = file,
                metadata = BugReportFileUploadPayload(
                    hardwareVersion = deviceInfo.hardwareVersion,
                    deviceSerial = deviceInfo.deviceSerial,
                    softwareVersion = deviceInfo.softwareVersion,
                    processingOptions = BugReportFileUploadPayload.ProcessingOptions(
                        processAnrs = !dropBoxDataSourceEnabledAndSupported,
                        processJavaExceptions = !dropBoxDataSourceEnabledAndSupported,
                        processLastKmsg = !dropBoxDataSourceEnabledAndSupported,
                        processRecoveryKmsg = !dropBoxDataSourceEnabledAndSupported,
                        processTombstones = !dropBoxDataSourceEnabledAndSupported,
                    ),
                    requestId = requestId,
                ),
                debugTag = WORK_TAG,
                collectionTime = collectionTime,
                continuation = request?.let {
                    BugReportFileUploadContinuation(
                        request = it,
                    )
                },
                /* Already a .zip file, not much to gain by recompressing */
                shouldCompress = false,
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
