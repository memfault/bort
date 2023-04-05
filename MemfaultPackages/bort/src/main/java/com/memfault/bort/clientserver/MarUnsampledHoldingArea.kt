package com.memfault.bort.clientserver

import androidx.annotation.VisibleForTesting
import com.memfault.bort.BortJson
import com.memfault.bort.MarFileUnsampledHoldingDir
import com.memfault.bort.clientserver.MarFileWriter.Companion.MAR_EXTENSION
import com.memfault.bort.fileExt.deleteSilently
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.requester.cleanupFiles
import com.memfault.bort.requester.directorySize
import com.memfault.bort.settings.SamplingConfig
import com.memfault.bort.settings.shouldUpload
import com.memfault.bort.shared.Logger
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.text.Charsets.UTF_8
import kotlin.time.Duration
import kotlinx.serialization.SerializationException

/**
 * Mar files are placed in this holding area after creation, if they are not eligible for upload under the current
 * fleet sampling configuration.
 *
 * We store the manifest as a separate file (<marfile>.manifest) so that we can easily read this later, without
 * extracting it from the mar file.
 */
@Singleton
class MarUnsampledHoldingArea @Inject constructor(
    @MarFileUnsampledHoldingDir private val unsampledHoldingDirectory: File,
) {
    private val unsampledDeletedMetric = Reporting.report().counter(
        name = "mar_unsampled_deleted",
        internal = true,
    )

    init {
        unsampledHoldingDirectory.mkdirs()
    }

    fun add(mar: MarFileWithManifest) {
        mar.marFile.renameTo(File(unsampledHoldingDirectory, mar.marFile.name))
        // Persist manifest separately, for easy retrieval.
        val manifestFile = manifestFileForMar(mar.marFile)
        val manifestSerialized = BortJson.encodeToString(MarManifest.serializer(), mar.manifest)
        manifestFile.writeText(manifestSerialized, charset = UTF_8)
    }

    fun eligibleForUpload(samplingConfig: SamplingConfig): List<MarFileWithManifest> {
        val manifestFiles =
            unsampledHoldingDirectory.listFiles()?.asList()?.filter { it.extension == MANIFEST_EXTENSION }
                ?: emptyList()
        return manifestFiles.mapNotNull { manifestFile ->
            val manifestJson = manifestFile.readText(charset = UTF_8)
            val manifest = try {
                BortJson.decodeFromString(MarManifest.serializer(), manifestJson)
            } catch (e: SerializationException) {
                null
            }
            manifest?.let { _ ->
                if (samplingConfig.shouldUpload(manifest)) {
                    val marFile = marFileForManifest(manifestFile)
                    if (marFile.exists()) {
                        MarFileWithManifest(marFile, manifest)
                    } else null
                } else null
            }
        }
    }

    fun removeManifest(marFileWithManifest: MarFileWithManifest) {
        manifestFileForMar(marFileWithManifest.marFile).deleteSilently()
    }

    /**
     * Delete files to keep under the storage limit. The limit is computed by the caller (sampled holding area) taking
     * into account its used storage.
     */
    fun cleanup(limitBytes: Long, maxAge: Duration) {
        val result = cleanupFiles(dir = unsampledHoldingDirectory, maxDirStorageBytes = limitBytes, maxFileAge = maxAge)
        if (result.deletedForStorageCount > 0) {
            Logger.d("Deleted ${result.deletedForStorageCount} sampled mar files to stay under storage limit")
            unsampledDeletedMetric.incrementBy(result.deletedForStorageCount)
        }
        if (result.deletedForAgeCount > 0) {
            Logger.d("Deleted ${result.deletedForAgeCount} sampled mar files for max age")
            unsampledDeletedMetric.incrementBy(result.deletedForAgeCount)
        }

        // Now go through and delete any orphan files (i.e. manifest without mar, or vice versa).
        val files = unsampledHoldingDirectory.listFiles()?.asList() ?: emptyList()
        files.forEach { file ->
            val matchingFile = when (file.extension) {
                MANIFEST_EXTENSION -> marFileForManifest(file)
                MAR_EXTENSION -> manifestFileForMar(file)
                else -> null
            }
            val matchingFileExists = matchingFile?.exists() ?: false
            if (!matchingFileExists) {
                file.delete()
                unsampledDeletedMetric.increment()
            }
        }
    }

    fun storageUsedBytes(): Long = unsampledHoldingDirectory.directorySize()

    fun oldestFileUpdatedTimestampMs(): Long? = unsampledHoldingDirectory.oldestFileUpdatedTimestamp()

    fun deleteAllFiles() {
        cleanupFiles(dir = unsampledHoldingDirectory, maxDirStorageBytes = 0)
    }

    private fun marFileForManifest(manifestFile: File): File {
        val marFileName = manifestFile.name.removeSuffix(".$MANIFEST_EXTENSION")
        return File(unsampledHoldingDirectory, marFileName)
    }

    @VisibleForTesting
    internal fun manifestFileForMar(marFile: File): File {
        return File(unsampledHoldingDirectory, "${marFile.name}.$MANIFEST_EXTENSION")
    }

    companion object {
        private const val MANIFEST_EXTENSION = "manifest"
    }
}
