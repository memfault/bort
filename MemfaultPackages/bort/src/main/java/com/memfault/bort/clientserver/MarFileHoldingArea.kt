package com.memfault.bort.clientserver

import android.app.Application
import com.memfault.bort.BortJson
import com.memfault.bort.DevMode
import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.MarFileSampledHoldingDir
import com.memfault.bort.MarFileUnsampledHoldingDir
import com.memfault.bort.Payload
import com.memfault.bort.clientserver.MarBatchingTask.Companion.enqueueOneTimeBatchMarFiles
import com.memfault.bort.clientserver.MarMetadata.BugReportMarMetadata
import com.memfault.bort.clientserver.MarMetadata.Companion.createManifest
import com.memfault.bort.clientserver.MarMetadata.DeviceConfigMarMetadata
import com.memfault.bort.fileExt.deleteSilently
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.requester.cleanupFiles
import com.memfault.bort.requester.directorySize
import com.memfault.bort.settings.BatchMarUploads
import com.memfault.bort.settings.CurrentSamplingConfig
import com.memfault.bort.settings.MarSampledMaxStorageAge
import com.memfault.bort.settings.MarUnsampledMaxStorageAge
import com.memfault.bort.settings.MarUnsampledMaxStorageBytes
import com.memfault.bort.settings.MaxMarStorageBytes
import com.memfault.bort.settings.ProjectKey
import com.memfault.bort.settings.SamplingConfig
import com.memfault.bort.settings.UnbatchBugReportUploads
import com.memfault.bort.settings.shouldUpload
import com.memfault.bort.shared.CLIENT_SERVER_FILE_UPLOAD_DROPBOX_TAG
import com.memfault.bort.shared.ClientServerMode
import com.memfault.bort.shared.Logger
import com.memfault.bort.time.CombinedTimeProvider
import com.memfault.bort.uploader.EnqueuePreparedUploadTask
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import org.jetbrains.annotations.VisibleForTesting
import java.io.File
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.toJavaDuration

/**
 * We keep all mar files waiting for upload in [sampledHoldingDirectory], then whenever [MarBatchingTask] runs (either
 * periodic, or one-time if invoke [oneTimeMarUpload] below), it will request a batched mar from this class.
 *
 * Files which are not eligible for upload under the current [SamplingConfig] are stored in the
 * [unsampledHoldingDirectory], in case the sampling config changes later.
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
    @MarFileUnsampledHoldingDir private val unsampledHoldingDirectory: File,
    private val batchMarUploads: BatchMarUploads,
    private val marFileWriter: MarFileWriter,
    private val oneTimeMarUpload: OneTimeMarUpload,
    private val cachedClientServerMode: CachedClientServerMode,
    private val currentSamplingConfig: CurrentSamplingConfig,
    private val linkedDeviceFileSender: LinkedDeviceFileSender,
    private val combinedTimeProvider: CombinedTimeProvider,
    private val maxMarStorageBytes: MaxMarStorageBytes,
    private val marMaxSampledAge: MarSampledMaxStorageAge,
    private val marMaxUnsampledAge: MarUnsampledMaxStorageAge,
    private val marMaxUnsampledBytes: MarUnsampledMaxStorageBytes,
    private val deviceInfoProvider: DeviceInfoProvider,
    private val projectKey: ProjectKey,
    private val devMode: DevMode,
    private val unbatchBugReportUploads: UnbatchBugReportUploads,
) {
    // Note: coroutine mutex is not re-entrant!
    private val mutex = Mutex()
    private val sampledStorageMetric = Reporting.report().distribution(
        name = "mar_sampled_storage",
        internal = true,
    )
    private val unsampledStorageMetric = Reporting.report().distribution(
        name = "mar_unsampled_storage",
        internal = true,
    )
    private val sampledDeletedMetric = Reporting.report().counter(
        name = "mar_sampled_deleted",
        internal = true,
    )
    private val unsampledAgeMetric = Reporting.report().distribution(
        name = "mar_unsampled_max_age_ms",
        internal = true,
    )
    private val unsampledDeletedMetric = Reporting.report().counter(
        name = "mar_unsampled_deleted",
        internal = true,
    )

    init {
        sampledHoldingDirectory.mkdirs()
        unsampledHoldingDirectory.mkdirs()
    }

    /**
     * Adds a mar file directly to the "sampled" holding area, for next available upload.
     *
     * This should only ever be used for files received from another device via client/server (because
     * these other devices make their own decisions about which files should be sampled - they will only send sampled
     * files).
     */
    suspend fun addSampledMarFileDirectlyFromOtherDevice(file: File) = mutex.withLock {
        addSampledMarFileDirectlyInternal(file = file, marManifest = null)
    }

    private suspend fun addSampledMarFileDirectlyInternal(file: File, marManifest: MarManifest?) {
        val isBugReport = marManifest?.metadata is BugReportMarMetadata
        val isDevModeEnabled = devMode.isEnabled()

        val uploadImmediately = isDevModeEnabled || (isBugReport && unbatchBugReportUploads())

        when {
            uploadImmediately -> {
                oneTimeMarUpload.uploadMarFile(file)
            }

            batchMarUploads() -> {
                // Periodic task will batch+upload mar files, if we are batching.
                file.renameTo(File(sampledHoldingDirectory, file.name))
                // Whenever a mar file is added (to either sampled or sampled) we check whether we are over the storage
                // limit.
                cleanupIfRequired()
            }

            else -> {
                // Upload immediately, if not batching.
                // Note: we don't upload a specific file - we create a new task to upload all available files.
                oneTimeMarUpload.uploadMarFile(file)
            }
        }
    }

    private suspend fun addSampledMarFile(mar: MarFileWithManifest) {
        // Sampled: upload now.
        if (cachedClientServerMode.isClient()) {
            linkedDeviceFileSender.sendFileToLinkedDevice(mar.marFile, CLIENT_SERVER_FILE_UPLOAD_DROPBOX_TAG)
        } else {
            addSampledMarFileDirectlyInternal(file = mar.marFile, marManifest = mar.manifest)
        }
    }

    suspend fun addMarFile(mar: MarFileWithManifest) {
        if (currentSamplingConfig.get().shouldUpload(mar.manifest)) {
            Logger.d("addMarFile: sampled $mar")
            addSampledMarFile(mar)
        } else {
            Logger.d("addMarFile: unsampled $mar")
            addUnsampledMarFile(mar)
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
        sampledStorageMetric.record(sampledHoldingDirectory.directorySize())
        unsampledStorageMetric.record(unsampledHoldingDirectory.directorySize())
        val maxUnsampledAgeMs = unsampledHoldingDirectory.oldestFileUpdatedTimestamp()?.let {
            combinedTimeProvider.now().timestamp.toEpochMilli() - it
        } ?: 0
        unsampledAgeMetric.record(maxUnsampledAgeMs)

        val pendingFiles = sampledHoldingDirectory.listFiles()?.asList() ?: emptyList()

        // Note: we always batch uploads at this point (even if the preference is disabled).
        return marFileWriter.batchMarFiles(pendingFiles)
    }

    suspend fun handleSamplingConfigChange(
        newConfig: SamplingConfig,
        dataUploadStartDate: Instant?,
    ) = mutex.withLock {
        // Move files from unsampled -> sampled, if new sampling configuration allows.
        var addedFilesFromUnsampled = false
        unsampledEligibleForUpload(newConfig, dataUploadStartDate).forEach { mar ->
            Logger.d("moving to sampled: $mar")
            addSampledMarFile(mar)
            manifestFileForMar(unsampledHoldingDirectory, mar.marFile).deleteSilently()
            addedFilesFromUnsampled = true
        }

        if (addedFilesFromUnsampled) {
            Logger.d("Triggering one-time mar upload after moving files from unsampled")
            Logger.test("Fleet-sampling one-time upload")
            oneTimeMarUpload.batchAndUpload()
        }
    }

    suspend fun addDeviceConfigMarEntry(revision: Int) {
        val deviceConfigManifest = createManifest(
            metadata = DeviceConfigMarMetadata(revision = revision),
            collectionTime = combinedTimeProvider.now(),
            deviceInfoProvider = deviceInfoProvider,
            projectKey = projectKey,
        )
        val deviceConfigMarResult = marFileWriter.createMarFile(file = null, manifest = deviceConfigManifest)
        deviceConfigMarResult
            .onSuccess { marFile -> addMarFile(marFile) }
            .onFailure { e -> Logger.w("Error writing deviceConfigMar file.", e) }
    }

    fun deleteAllFiles() {
        cleanupFiles(dir = sampledHoldingDirectory, maxDirStorageBytes = 0)
        cleanupFiles(dir = unsampledHoldingDirectory, maxDirStorageBytes = 0)
    }

    /**
     * Run cleanup for the holding areas - but only if we are over the storage limit.
     */
    @VisibleForTesting
    internal fun cleanupIfRequired() {
        // Limit for the unsampled area is whatever is not used by the sampled area.
        val sampledStorageUsedBytes = sampledHoldingDirectory.directorySize()
        val unsampledUsedBytes = unsampledHoldingDirectory.directorySize()
        val usedBytes = sampledStorageUsedBytes + unsampledUsedBytes
        val limitBytes = maxMarStorageBytes()
        val maxSampledAge = marMaxSampledAge()
        val maxUnsampledAge = marMaxUnsampledAge()

        val now = combinedTimeProvider.now().timestamp
        val cleanupForMaxUnsampledAge = when (maxUnsampledAge) {
            ZERO -> false
            else -> {
                val oldestUnsampledFileAge = unsampledHoldingDirectory.oldestFileUpdatedTimestamp()
                    ?.let { Instant.ofEpochMilli(it) }
                if (oldestUnsampledFileAge != null) {
                    Duration.between(oldestUnsampledFileAge, now) > maxUnsampledAge.toJavaDuration()
                } else {
                    false
                }
            }
        }
        val cleanupForMaxSampledAge = when (maxSampledAge) {
            ZERO -> false
            else -> {
                val oldestSampledFileAge = sampledHoldingDirectory.oldestFileUpdatedTimestamp()
                    ?.let { Instant.ofEpochMilli(it) }
                if (oldestSampledFileAge != null) {
                    Duration.between(oldestSampledFileAge, now) > maxSampledAge.toJavaDuration()
                } else {
                    false
                }
            }
        }
        val unsampledOverLimit = unsampledUsedBytes > marMaxUnsampledBytes()
        if (usedBytes > limitBytes || cleanupForMaxUnsampledAge || cleanupForMaxSampledAge || unsampledOverLimit) {
            // Only cleanup the sampled area if it is over limit.
            if (sampledStorageUsedBytes > limitBytes || cleanupForMaxSampledAge) {
                val result = cleanupFiles(
                    dir = sampledHoldingDirectory,
                    maxDirStorageBytes = limitBytes,
                    maxFileAge = maxSampledAge,
                    timeNowMs = now.toEpochMilli(),
                )
                if (result.deletedForAgeCount > 0) {
                    Logger.d("Deleted ${result.deletedForAgeCount} sampled mar files to stay under age limit")
                    sampledDeletedMetric.incrementBy(result.deletedForAgeCount)
                }
                if (result.deletedForStorageCount > 0) {
                    Logger.d("Deleted ${result.deletedForStorageCount} sampled mar files to stay under storage limit")
                    sampledDeletedMetric.incrementBy(result.deletedForStorageCount)
                }
            }

            val unsampledLimit = limitBytes - sampledHoldingDirectory.directorySize()
            val unsampledLimitBytes = minOf(unsampledLimit, marMaxUnsampledBytes())

            val result = cleanupFiles(
                dir = unsampledHoldingDirectory,
                maxDirStorageBytes = unsampledLimitBytes,
                maxFileAge = maxUnsampledAge,
                timeNowMs = now.toEpochMilli(),
            )
            if (result.deletedForStorageCount > 0) {
                Logger.d("Deleted ${result.deletedForStorageCount} unsampled mar files to stay under storage limit")
                unsampledDeletedMetric.incrementBy(result.deletedForStorageCount)
            }
            if (result.deletedForAgeCount > 0) {
                Logger.d("Deleted ${result.deletedForAgeCount} unsampled mar files for max age")
                unsampledDeletedMetric.incrementBy(result.deletedForAgeCount)
            }
        }

        // Now go through and delete any orphan files (i.e. manifest without mar, or vice versa).
        val files = unsampledHoldingDirectory.listFiles()?.asList() ?: emptyList()
        files.forEach { file ->
            val matchingFile = when (file.extension) {
                MANIFEST_EXTENSION -> marFileForManifest(unsampledHoldingDirectory, file)
                MarFileWriter.MAR_EXTENSION -> manifestFileForMar(unsampledHoldingDirectory, file)
                else -> null
            }
            val matchingFileExists = matchingFile?.exists() ?: false
            if (!matchingFileExists) {
                file.delete()
                unsampledDeletedMetric.increment()
            }
        }
    }

    private fun addUnsampledMarFile(mar: MarFileWithManifest) {
        mar.marFile.renameTo(File(unsampledHoldingDirectory, mar.marFile.name))
        // Persist manifest separately, for easy retrieval.
        val manifestFile = manifestFileForMar(unsampledHoldingDirectory, mar.marFile)
        val manifestSerialized = BortJson.encodeToString(MarManifest.serializer(), mar.manifest)
        manifestFile.writeText(manifestSerialized, charset = Charsets.UTF_8)
    }

    private fun MarManifest.shouldUpload(dataUploadStartDate: Instant?): Boolean = if (dataUploadStartDate == null) {
        // No date provided, upload this file.
        true
    } else if (collectionTime.timestamp.isAfter(dataUploadStartDate)) {
        // If the file was collected after the upload date, upload it.
        true
    } else {
        // If the file was collected an arbitrarily long time in the past (2 times max unsampled duration),
        // but hasn't been cleaned up yet, then upload the file anyways just in case there was a clock issue
        // because we don't know that the actual clock on the device is correct.
        Duration.between(collectionTime.timestamp, dataUploadStartDate).abs() >=
            marMaxUnsampledAge().times(2).toJavaDuration()
    }

    @VisibleForTesting
    internal fun unsampledEligibleForUpload(
        samplingConfig: SamplingConfig,
        dataUploadStartDate: Instant?,
    ): List<MarFileWithManifest> {
        val manifestFiles =
            unsampledHoldingDirectory.listFiles()?.asList()?.filter { it.extension == MANIFEST_EXTENSION }
                ?: emptyList()

        return manifestFiles.mapNotNull { manifestFile ->
            val manifestJson = manifestFile.readText(charset = Charsets.UTF_8)
            val marManifest = try {
                BortJson.decodeFromString(MarManifest.serializer(), manifestJson)
            } catch (e: SerializationException) {
                null
            }
            marManifest?.let { manifest ->
                if (samplingConfig.shouldUpload(manifest) && manifest.shouldUpload(dataUploadStartDate)) {
                    val marFile = marFileForManifest(unsampledHoldingDirectory, manifestFile)
                    if (marFile.exists()) {
                        MarFileWithManifest(marFile, manifest)
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        }
    }

    companion object {
        @VisibleForTesting
        internal fun marFileForManifest(holdingDirectory: File, manifestFile: File): File {
            val marFileName = manifestFile.name.removeSuffix(".$MANIFEST_EXTENSION")
            return File(holdingDirectory, marFileName)
        }

        @VisibleForTesting
        internal fun manifestFileForMar(holdingDirectory: File, marFile: File): File =
            File(holdingDirectory, "${marFile.name}.$MANIFEST_EXTENSION")

        private const val MANIFEST_EXTENSION = "manifest"
    }
}

class OneTimeMarUpload @Inject constructor(
    private val enqueuePreparedUploadTask: EnqueuePreparedUploadTask,
    private val deviceInfoProvider: DeviceInfoProvider,
    private val application: Application,
) {
    suspend fun uploadMarFile(marFileToUpload: File) {
        val deviceInfo = deviceInfoProvider.getDeviceInfo()
        enqueuePreparedUploadTask.upload(
            file = marFileToUpload,
            metadata = Payload.MarPayload(MarBatchingTask.createMarPayload(marFileToUpload, deviceInfo)),
            debugTag = MarBatchingTask.UPLOAD_TAG_MAR,
            shouldCompress = false,
            applyJitter = true,
        )
    }

    fun batchAndUpload() {
        enqueueOneTimeBatchMarFiles(application)
    }
}

fun File.oldestFileUpdatedTimestamp(): Long? = listFiles()?.minOfOrNull { it.lastModified() }
