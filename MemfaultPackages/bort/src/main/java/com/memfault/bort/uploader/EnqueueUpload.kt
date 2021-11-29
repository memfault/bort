package com.memfault.bort.uploader

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import com.memfault.bort.CachedAsyncProperty
import com.memfault.bort.DumpsterClient
import com.memfault.bort.FileUploadPayload
import com.memfault.bort.MarFileUploadPayload
import com.memfault.bort.clientserver.MarFileWriter
import com.memfault.bort.clientserver.ServerFileSender
import com.memfault.bort.enqueueWorkOnce
import com.memfault.bort.fileExt.deleteSilently
import com.memfault.bort.ingress.IngressService
import com.memfault.bort.ingress.RebootEvent
import com.memfault.bort.shared.ClientServerMode
import com.memfault.bort.shared.JitterDelayProvider
import com.memfault.bort.time.CombinedTime
import java.io.File
import kotlin.time.toJavaDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Enqueue a file or event for upload.
 *
 * Decides whether to use mar or legacy individual upload endpoints, and whether to use Bort client/server comms.
 */
class EnqueueUpload(
    private val context: Context,
    private val serverFileSender: ServerFileSender,
    private val marFileWriter: MarFileWriter,
    private val ingressService: IngressService,
    private val dumpsterClient: DumpsterClient,
    private val enqueuePreparedUploadTask: EnqueuePreparedUploadTask,
) {
    private val clientServerMode = CachedAsyncProperty {
        ClientServerMode.decode(dumpsterClient.getprop()?.get(ClientServerMode.SYSTEM_PROP))
    }

    fun enqueue(rebootEvent: RebootEvent, rebootTime: CombinedTime) {
        CoroutineScope(Dispatchers.IO).launch {
            if (isClientServerClient()) {
                val marFile = marFileWriter.createForReboot(rebootEvent, rebootTime)
                serverFileSender.sendFileToBortServer(marFile)
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
            if (isClientServerClient() && metadata !is MarFileUploadPayload) {
                val marFile = marFileWriter.createForFile(file, metadata, collectionTime)
                file.deleteSilently()
                serverFileSender.sendFileToBortServer(marFile)
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

    private suspend fun isClientServerClient() = clientServerMode.get() == ClientServerMode.CLIENT
}

/**
 * Creates a task for a file to be uploaded using prepared upload.
 */
class EnqueuePreparedUploadTask(
    private val context: Context,
    private val jitterDelayProvider: JitterDelayProvider,
    private val constraints: () -> Constraints,
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
