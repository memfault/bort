package com.memfault.bort.clientserver

import android.content.Context
import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.MarFileSampledHoldingDir
import com.memfault.bort.Payload
import com.memfault.bort.clientserver.MarBatchingTask.Companion.enqueueOneTimeBatchMarFiles
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.requester.cleanupFiles
import com.memfault.bort.settings.BatchMarUploads
import com.memfault.bort.settings.CurrentSamplingConfig
import com.memfault.bort.settings.MarUnsampledMaxStorageAge
import com.memfault.bort.settings.MaxMarStorageBytes
import com.memfault.bort.settings.SamplingConfig
import com.memfault.bort.settings.shouldUpload
import com.memfault.bort.shared.CLIENT_SERVER_FILE_UPLOAD_DROPBOX_TAG
import com.memfault.bort.shared.ClientServerMode
import com.memfault.bort.shared.Logger
import com.memfault.bort.time.CombinedTimeProvider
import com.memfault.bort.uploader.EnqueuePreparedUploadTask
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * We keep all mar files waiting for upload in [sampledHoldingDirectory], then whenever [MarBatchingTask] runs (either
 * periodic, or one-time if invoke [oneTimeMarUpload] below), it will request a batched mar from this class.
 *
 * Files which are not eligible for upload under the current [SamplingConfig] are stored in the
 * [MarUnsampledHoldingArea], in case the sampling config changes later.
 *
 * If this is a [ClientServerMode] client, then only sampled files are sent to the server. Unsampled files are kept
 * locally, then sent to the server if the sampling config allows at a later time.
 *
 * Combined storage in the sampled + unsampled areas is bounded by [maxMarStorageBytes]. Every time a file is added, we
 * check usage and delete the oldest files (in unsampled first) until under the limit.
 */
@Singleton
class MarFileHoldingArea @Inject constructor(
    @MarFileSampledHoldingDir private val sampledHoldingDirectory: File,
    private val batchMarUploads: BatchMarUploads,
    private val marFileWriter: MarFileWriter,
    private val oneTimeMarUpload: OneTimeMarUpload,
    private val cachedClientServerMode: CachedClientServerMode,
    private val currentSamplingConfig: CurrentSamplingConfig,
    private val linkedDeviceFileSender: LinkedDeviceFileSender,
    private val unsampledHoldingArea: MarUnsampledHoldingArea,
    private val combinedTimeProvider: CombinedTimeProvider,
    private val maxMarStorageBytes: MaxMarStorageBytes,
    private val marMaxUnsampledAge: MarUnsampledMaxStorageAge,
) {
    // Note: coroutine mutex is not re-entrant!
    private val mutex = Mutex()
    private val sampledStorageMetric = Reporting.report().numberProperty(
        name = "mar_sampled_storage",
        internal = true,
    )
    private val unsampledStorageMetric = Reporting.report().numberProperty(
        name = "mar_unsampled_storage",
        internal = true,
    )
    private val sampledDeletedMetric = Reporting.report().counter(
        name = "mar_sampled_deleted",
        internal = true,
    )
    private val unsampledAgeMetric = Reporting.report().numberProperty(
        name = "mar_unsampled_max_age_ms",
        internal = true,
    )

    init {
        sampledHoldingDirectory.mkdirs()
    }

    /**
     * Adds a mar file directly to the "sampled" holding area, for next available upload.
     *
     * This should only ever be used for files received from another device via client/server (because
     * these other devices make their own decisions about which files should be sampled - they will only send sampled
     * files).
     */
    suspend fun addSampledMarFileDirectlyFromOtherDevice(file: File) = mutex.withLock {
        addSampledMarFileDirectlyInternal(file)
    }

    private suspend fun addSampledMarFileDirectlyInternal(file: File) {
        // If dev mode is enabled, don't batch mar files (let them upload individually, immediately).
        if (batchMarUploads()) {
            // Periodic task will batch+upload mar files, if we are batching.
            file.renameTo(File(sampledHoldingDirectory, file.name))
            // Whenever a mar file is added (to either sampled or sampled) we check whether we are over the storage
            // limit.
            cleanupIfRequired()
        } else {
            // Upload immediately, if not batching.
            // Note: we don't upload a specific file - we create a new task to upload all available files.
            oneTimeMarUpload.uploadMarFile(file)
        }
    }

    private suspend fun addSampledMarFile(mar: MarFileWithManifest) {
        // Sampled: upload now.
        if (cachedClientServerMode.isClient()) {
            linkedDeviceFileSender.sendFileToLinkedDevice(mar.marFile, CLIENT_SERVER_FILE_UPLOAD_DROPBOX_TAG)
        } else {
            addSampledMarFileDirectlyInternal(mar.marFile)
        }
    }

    suspend fun addMarFile(mar: MarFileWithManifest) {
        if (currentSamplingConfig.get().shouldUpload(mar.manifest)) {
            Logger.d("addMarFile: sampled $mar")
            addSampledMarFile(mar)
        } else {
            Logger.d("addMarFile: unsampled $mar")
            unsampledHoldingArea.add(mar)
            // Whenever a mar file is added (to either sampled or sampled) we check whether we are over the storage
            // limit.
            cleanupIfRequired()
        }
    }

    /**
     * Create a bundled mar file(s) from the mar files waiting in the "sampled" holding area.
     *
     * There is a mar file size limit, so multiple files may be created/returned.
     */
    suspend fun bundleMarFilesForUpload(): List<File> = mutex.withLock {
        sampledStorageMetric.update(sampledHoldingDirectory.directorySize())
        unsampledStorageMetric.update(unsampledHoldingArea.storageUsedBytes())
        val maxUnsampledAgeMs = unsampledHoldingArea.oldestFileUpdatedTimestampMs()?.let {
            combinedTimeProvider.now().timestamp.toEpochMilli() - it
        } ?: 0
        unsampledAgeMetric.update(maxUnsampledAgeMs)

        val pendingFiles = sampledHoldingDirectory.listFiles()?.asList() ?: emptyList()

        // Note: we always batch uploads at this point (even if the preference is disabled).
        return marFileWriter.batchMarFiles(pendingFiles)
    }

    suspend fun handleSamplingConfigChange(newConfig: SamplingConfig) = mutex.withLock {
        // Move files from unsampled -> sampled, if new sampling configuration allows.
        var addedFilesFromUnsampled = false
        unsampledHoldingArea.eligibleForUpload(newConfig).forEach { mar ->
            Logger.d("moving to sampled: $mar")
            addSampledMarFile(mar)
            unsampledHoldingArea.removeManifest(mar)
            addedFilesFromUnsampled = true
        }
        // Add a new mar entry, confirming that we processed this config revision.
        val deviceConfigMar = marFileWriter.createForDeviceConfig(newConfig.revision, combinedTimeProvider.now())
        addMarFile(deviceConfigMar)

        if (addedFilesFromUnsampled) {
            Logger.d("Triggering one-time mar upload after moving files from unsampled")
            oneTimeMarUpload.batchAndUpload()
        }
    }

    /**
     * Run cleanup for the holding areas - but only if we are over the storage limit.
     */
    private fun cleanupIfRequired() {
        // Limit for the unsampled area is whatever is not used by the sampled area.
        val sampledStorageUsedBytes = sampledHoldingDirectory.directorySize()
        val unsampledUsedBytes = unsampledHoldingArea.storageUsedBytes()
        val usedBytes = sampledStorageUsedBytes + unsampledUsedBytes
        val limitBytes = maxMarStorageBytes()
        val maxUnsampledAge = marMaxUnsampledAge()
        val cleanupForMaxUnsampledAge = when (maxUnsampledAge) {
            ZERO -> false
            else -> {
                val oldestUnsampledFileAgeMs = unsampledHoldingArea.oldestFileUpdatedTimestampMs()?.let {
                    combinedTimeProvider.now().timestamp.toEpochMilli() - it
                } ?: 0
                oldestUnsampledFileAgeMs > maxUnsampledAge.inWholeMilliseconds
            }
        }
        if (usedBytes > limitBytes || cleanupForMaxUnsampledAge) {
            cleanup(
                sampledStorageUsedBytes = sampledStorageUsedBytes,
                limitBytes = limitBytes,
                maxUnsampledAge = maxUnsampledAge
            )
        }
    }

    /**
     * Cleanup the holding areas: unsampled first, then also sampled if we are still over the limit.
     */
    private fun cleanup(sampledStorageUsedBytes: Long, limitBytes: Long, maxUnsampledAge: Duration) {
        // Only cleanup the sampled area if it is over limit.
        if (sampledStorageUsedBytes > limitBytes) {
            val result = cleanupFiles(dir = sampledHoldingDirectory, maxDirStorageBytes = limitBytes)
            if (result.deletedForStorageCount > 0) {
                Logger.d("Deleted ${result.deletedForStorageCount} sampled mar files to stay under storage limit")
                sampledDeletedMetric.incrementBy(result.deletedForStorageCount)
            }
        }

        val unsampledLimit = limitBytes - sampledHoldingDirectory.directorySize()
        unsampledHoldingArea.cleanup(unsampledLimit, maxUnsampledAge)
    }
}

class OneTimeMarUpload @Inject constructor(
    private val enqueuePreparedUploadTask: EnqueuePreparedUploadTask,
    private val deviceInfoProvider: DeviceInfoProvider,
    private val context: Context,
) {
    suspend fun uploadMarFile(marFileToUpload: File) {
        val deviceInfo = deviceInfoProvider.getDeviceInfo()
        enqueuePreparedUploadTask.upload(
            file = marFileToUpload,
            metadata = Payload.MarPayload(MarBatchingTask.createMarPayload(marFileToUpload, deviceInfo)),
            debugTag = MarBatchingTask.UPLOAD_TAG_MAR,
            continuation = null,
            shouldCompress = false,
            applyJitter = true,
        )
    }

    fun batchAndUpload() {
        enqueueOneTimeBatchMarFiles(context)
    }
}

fun File.directorySize(): Long = listFiles()?.filter { it.isFile }?.map { it.length() }?.sum() ?: 0

fun File.oldestFileUpdatedTimestamp(): Long? = listFiles()?.map { it.lastModified() }?.minOrNull()
