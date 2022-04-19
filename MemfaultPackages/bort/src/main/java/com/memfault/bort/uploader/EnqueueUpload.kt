package com.memfault.bort.uploader

import android.content.Context
import androidx.work.BackoffPolicy
import com.memfault.bort.FileUploadPayload
import com.memfault.bort.clientserver.CachedClientServerMode
import com.memfault.bort.clientserver.LinkedDeviceFileSender
import com.memfault.bort.clientserver.MarFileHoldingArea
import com.memfault.bort.clientserver.MarFileWriter
import com.memfault.bort.enqueueWorkOnce
import com.memfault.bort.fileExt.deleteSilently
import com.memfault.bort.ingress.IngressService
import com.memfault.bort.ingress.RebootEvent
import com.memfault.bort.settings.UploadConstraints
import com.memfault.bort.settings.UseMarUpload
import com.memfault.bort.shared.CLIENT_SERVER_FILE_UPLOAD_DROPBOX_TAG
import com.memfault.bort.shared.ClientServerMode
import com.memfault.bort.shared.JitterDelayProvider
import com.memfault.bort.time.CombinedTime
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.toJavaDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Enqueue a file or event for upload.
 *
 * Decides whether to use mar or legacy individual upload endpoints, and whether to use Bort client/server comms.
 */
@Singleton
class EnqueueUpload @Inject constructor(
    private val context: Context,
    private val linkedDeviceFileSender: LinkedDeviceFileSender,
    private val marFileWriter: MarFileWriter,
    private val ingressService: IngressService,
    private val enqueuePreparedUploadTask: EnqueuePreparedUploadTask,
    private val useMarUpload: UseMarUpload,
    private val marHoldingArea: MarFileHoldingArea,
    private val cachedClientServerMode: CachedClientServerMode,
) {
    fun enqueue(rebootEvent: RebootEvent, rebootTime: CombinedTime) {
        CoroutineScope(Dispatchers.IO).launch {
            if (useMarFile()) {
                val marFile = marFileWriter.createForReboot(rebootEvent, rebootTime)
                uploadMarFile(marFile)
            } else {
                ingressService.uploadRebootEvents(listOf(rebootEvent)).execute()
            }
        }
    }

    /**
     * Enqueue a file for upload.
     */
    fun enqueue(
        file: File,
        metadata: FileUploadPayload,
        debugTag: String,
        collectionTime: CombinedTime,
        continuation: FileUploadContinuation? = null,
        shouldCompress: Boolean = true,
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            // Exclude MarFileUploadPayload, so that we don't endlessly loop sending same file over loopback in E2E.
            if (useMarFile()) {
                val marFile = marFileWriter.createForFile(file, metadata, collectionTime)
                file.deleteSilently()
                uploadMarFile(marFile)
                continuation?.success(context)
            } else {
                enqueuePreparedUploadTask.upload(
                    file = file,
                    metadata = metadata,
                    continuation = continuation,
                    shouldCompress = shouldCompress,
                    debugTag = debugTag,
                )
            }
        }
    }

    private suspend fun uploadMarFile(marFile: File) {
        if (isClientServerClient()) {
            linkedDeviceFileSender.sendFileToLinkedDevice(marFile, CLIENT_SERVER_FILE_UPLOAD_DROPBOX_TAG)
        } else {
            marHoldingArea.addMarFile(marFile)
        }
    }

    private suspend fun useMarFile() = isClientServerClient() || useMarUpload()

    private suspend fun isClientServerClient() = cachedClientServerMode.get() == ClientServerMode.CLIENT
}

/**
 * Creates a task for a file to be uploaded using prepared upload.
 */
class EnqueuePreparedUploadTask @Inject constructor(
    private val context: Context,
    private val jitterDelayProvider: JitterDelayProvider,
    private val constraints: UploadConstraints,
) {
    fun upload(
        file: File,
        metadata: FileUploadPayload,
        debugTag: String,
        continuation: FileUploadContinuation?,
        shouldCompress: Boolean,
    ) {
        enqueueWorkOnce<FileUploadTask>(
            context,
            FileUploadTaskInput(file, metadata, continuation, shouldCompress).toWorkerInputData()
        ) {
            setInitialDelay(jitterDelayProvider.randomJitterDelay())
            setConstraints(constraints())
            setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DURATION.toJavaDuration())
            addTag(debugTag)
        }
    }
}
