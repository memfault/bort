package com.memfault.bort.uploader

import android.app.Application
import androidx.work.BackoffPolicy
import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.IO
import com.memfault.bort.Payload
import com.memfault.bort.clientserver.MarFileHoldingArea
import com.memfault.bort.clientserver.MarFileWithManifest
import com.memfault.bort.clientserver.MarFileWriter
import com.memfault.bort.clientserver.MarMetadata
import com.memfault.bort.clientserver.MarMetadata.Companion.createManifest
import com.memfault.bort.enqueueWorkOnce
import com.memfault.bort.fileExt.deleteSilently
import com.memfault.bort.settings.ProjectKey
import com.memfault.bort.settings.Resolution
import com.memfault.bort.settings.UploadConstraints
import com.memfault.bort.shared.JitterDelayProvider
import com.memfault.bort.shared.Logger
import com.memfault.bort.time.CombinedTime
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlin.time.toJavaDuration

/**
 * Enqueue a file or event for upload.
 *
 * Decides whether to use mar or legacy individual upload endpoints, and whether to use Bort client/server comms.
 */
@Singleton
class EnqueueUpload @Inject constructor(
    private val marFileWriter: MarFileWriter,
    private val marHoldingArea: MarFileHoldingArea,
    private val deviceInfoProvider: DeviceInfoProvider,
    private val projectKey: ProjectKey,
    @IO private val ioCoroutineContext: CoroutineContext,
) {
    /**
     * Enqueue a file for upload.
     */
    suspend fun enqueue(
        file: File?,
        metadata: MarMetadata,
        collectionTime: CombinedTime,
        /**
         * Set to override the monitoring resolution from its default.
         * Null/unset = default for type (as defined during manifest creation).
         **/
        overrideMonitoringResolution: Resolution? = null,
        /**
         * Set to override the debugging resolution from its default.
         * Null/unset = default for type (as defined during manifest creation).
         *
         * Note: the only type that ever changes a resolution is logging for the debugging aspect. Add more overrides
         * here if we ever need them.
         **/
        overrideDebuggingResolution: Resolution? = null,
        overrideSoftwareVersion: String? = null,
    ): Result<MarFileWithManifest> = withContext(ioCoroutineContext) {
        var manifest = createManifest(metadata, collectionTime, deviceInfoProvider, projectKey)
        overrideMonitoringResolution?.let {
            manifest = manifest.copy(monitoringResolution = it)
        }
        overrideDebuggingResolution?.let {
            manifest = manifest.copy(debuggingResolution = it)
        }
        overrideSoftwareVersion?.let {
            manifest = manifest.copy(
                device = manifest.device.copy(softwareVersion = it),
            )
        }
        val marFileWriteResult = marFileWriter.createMarFile(file, manifest)

        file?.deleteSilently()
        marFileWriteResult
            .onSuccess { marFile -> marHoldingArea.addMarFile(marFile) }
            .onFailure { e -> Logger.w("Error writing mar file.", e) }
    }
}

/**
 * Creates a task for a file to be uploaded using prepared upload.
 */
class EnqueuePreparedUploadTask @Inject constructor(
    private val application: Application,
    private val jitterDelayProvider: JitterDelayProvider,
    private val constraints: UploadConstraints,
) {
    fun upload(
        file: File,
        metadata: Payload,
        debugTag: String,
        shouldCompress: Boolean,
        applyJitter: Boolean,
    ) {
        enqueueWorkOnce<FileUploadTask>(
            application,
            FileUploadTaskInput(file, metadata, shouldCompress).toWorkerInputData(),
        ) {
            if (applyJitter) {
                setInitialDelay(jitterDelayProvider.randomJitterDelay().toJavaDuration())
            }
            setConstraints(constraints())
            setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DURATION.toJavaDuration())
            addTag(debugTag)
        }
    }
}
