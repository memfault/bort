package com.memfault.bort.clientserver

import androidx.annotation.VisibleForTesting
import com.memfault.bort.BortJson
import com.memfault.bort.BugReportFileUploadPayload
import com.memfault.bort.DeviceInfo
import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.DropBoxEntryFileUploadPayload
import com.memfault.bort.FileUploadPayload
import com.memfault.bort.HeartbeatFileUploadPayload
import com.memfault.bort.LogcatFileUploadPayload
import com.memfault.bort.SOFTWARE_TYPE
import com.memfault.bort.StructuredLogFileUploadPayload
import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.addZipEntry
import com.memfault.bort.clientserver.MarMetadata.BugReportMarMetadata
import com.memfault.bort.clientserver.MarMetadata.CustomDataRecordingMarMetadata
import com.memfault.bort.clientserver.MarMetadata.DeviceConfigMarMetadata
import com.memfault.bort.clientserver.MarMetadata.DropBoxMarMetadata
import com.memfault.bort.clientserver.MarMetadata.HeartbeatMarMetadata
import com.memfault.bort.clientserver.MarMetadata.LogcatMarMetadata
import com.memfault.bort.clientserver.MarMetadata.RebootMarMetadata
import com.memfault.bort.clientserver.MarMetadata.StructuredLogMarMetadata
import com.memfault.bort.ingress.RebootEvent
import com.memfault.bort.settings.CurrentSamplingConfig
import com.memfault.bort.settings.HttpApiSettings
import com.memfault.bort.settings.Resolution
import com.memfault.bort.settings.ZipCompressionLevel
import com.memfault.bort.settings.shouldUpload
import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.time.CombinedTime
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import kotlin.time.Duration
import kotlinx.serialization.encodeToString

class MarFileWriter @Inject constructor(
    private val deviceInfoProvider: DeviceInfoProvider,
    private val httpSettings: HttpApiSettings,
    private val temporaryFileFactory: TemporaryFileFactory,
    private val zipCompressionLevel: ZipCompressionLevel,
) {
    suspend fun createForFile(
        file: File?,
        metadata: FileUploadPayload,
        collectionTime: CombinedTime,
    ): MarFileWithManifest {
        val marManifest = metadata.asMarManifest(file, collectionTime)
        val marFile = createMarFile().useFile { marFile, preventDeletion ->
            writeMarFile(
                marFile = marFile,
                manifest = marManifest,
                inputFile = file,
                compressionLevel = zipCompressionLevel(),
            )
            preventDeletion()
            marFile
        }
        return MarFileWithManifest(marFile, marManifest)
    }

    suspend fun createForReboot(rebootEvent: RebootEvent, collectionTime: CombinedTime): MarFileWithManifest {
        val marManifest = rebootEvent.asMarManifest(collectionTime)
        val marFile = createMarFile().useFile { marFile, preventDeletion ->
            writeMarFile(
                marFile = marFile,
                manifest = marManifest,
                inputFile = null,
                compressionLevel = zipCompressionLevel(),
            )
            preventDeletion()
            marFile
        }
        return MarFileWithManifest(marFile, marManifest)
    }

    suspend fun createForDeviceConfig(revision: Int, collectionTime: CombinedTime): MarFileWithManifest {
        val marManifest = MarManifest(
            collectionTime = collectionTime,
            type = "android-device-config",
            device = device(),
            metadata = DeviceConfigMarMetadata(revision = revision),
            // All resolutions are set of OFF. This means that the file is always uploaded, regardless of device
            // fleet-sampling configuration.
            debuggingResolution = Resolution.OFF,
            loggingResolution = Resolution.OFF,
            monitoringResolution = Resolution.OFF,
        )
        val marFile = createMarFile().useFile { marFile, preventDeletion ->
            writeMarFile(
                marFile = marFile,
                manifest = marManifest,
                inputFile = null,
                compressionLevel = zipCompressionLevel(),
            )
            preventDeletion()
            marFile
        }
        return MarFileWithManifest(marFile, marManifest)
    }

    suspend fun createForCustomDataRecording(
        file: File,
        startTime: AbsoluteTime,
        duration: Duration,
        mimeTypes: List<String>,
        reason: String,
        collectionTime: CombinedTime,
    ): MarFileWithManifest {
        val marManifest = MarManifest(
            collectionTime = collectionTime,
            type = "custom-data-recording",
            device = device(),
            metadata = CustomDataRecordingMarMetadata(
                recordingFileName = file.name,
                startTime = startTime,
                durationMs = duration.inWholeMilliseconds,
                mimeTypes = mimeTypes,
                reason = reason,
            ),
            debuggingResolution = Resolution.NORMAL,
            loggingResolution = Resolution.NOT_APPLICABLE,
            monitoringResolution = Resolution.NOT_APPLICABLE,
        )
        val marFile = createMarFile().useFile { marFile, preventDeletion ->
            writeMarFile(
                marFile = marFile,
                manifest = marManifest,
                inputFile = file,
                compressionLevel = zipCompressionLevel(),
            )
            preventDeletion()
            marFile
        }
        return MarFileWithManifest(marFile, marManifest)
    }

    fun batchMarFiles(inputMarFiles: List<File>): List<File> {
        if (inputMarFiles.isEmpty()) return emptyList()
        val batches = splitFilesIntoBatchesForMaxSize(inputMarFiles)
        val batchedFiles = batches.map { batch ->
            createMarFile().useFile { file, preventDeletion ->
                writeBatchedMarFile(
                    marFile = file,
                    inputFiles = batch,
                    compressionLevel = zipCompressionLevel()
                )
                preventDeletion()
                file
            }
        }
        return batchedFiles
    }

    @VisibleForTesting
    internal fun splitFilesIntoBatchesForMaxSize(inputMarFiles: List<File>): List<List<File>> {
        // We can't know for sure exactly how much storage will be used when adding a file to a compressed zip - however
        // we do know the compressed size of each inidividual input mar file; it is unlikely to take more space in the
        // merged mar file (assuming the same compression rate is used).
        // To be safe, we add a tolerance to the size limit.
        val maxMarSize = (httpSettings.maxMarFileSizeBytes - MAR_SIZE_TOLERANCE_BYTES).coerceAtLeast(0)
        return inputMarFiles.chunkByElementSize(maxMarSize.toLong()) { file -> file.length() }
    }

    private fun createMarFile() = temporaryFileFactory.createTemporaryFile(suffix = ".$MAR_EXTENSION")

    private suspend fun device() =
        deviceInfoProvider.getDeviceInfo().asDevice(httpSettings.projectKey)

    private suspend fun RebootEvent.asMarManifest(collectionTime: CombinedTime): MarManifest =
        MarManifest(
            collectionTime = collectionTime,
            type = "android-reboot",
            device = device(),
            metadata = RebootMarMetadata(
                reason = event_info.reason,
                subreason = event_info.subreason,
                details = event_info.details,
            ),
            debuggingResolution = Resolution.NORMAL,
            loggingResolution = Resolution.NOT_APPLICABLE,
            monitoringResolution = Resolution.NOT_APPLICABLE,
        )

    /**
     * Check whether this file should be uploaded using the current sampling config. Member of this class because a mar
     * manifest is required to perform this check. Expected to be used when mar upload is disabled.
     */
    suspend fun checkShouldUpload(
        payload: FileUploadPayload,
        file: File?,
        fileCollectionTime: CombinedTime,
        currentSamplingConfig: CurrentSamplingConfig,
    ): Boolean = currentSamplingConfig.get().shouldUpload(payload.asMarManifest(file, fileCollectionTime))

    private suspend fun FileUploadPayload.asMarManifest(file: File?, fileCollectionTime: CombinedTime): MarManifest {
        val device = device()
        return when (this) {
            is HeartbeatFileUploadPayload -> MarManifest(
                collectionTime = collectionTime,
                type = "android-heartbeat",
                device = device,
                metadata = HeartbeatMarMetadata(
                    batteryStatsFileName = file?.name,
                    heartbeatIntervalMs = heartbeatIntervalMs,
                    customMetrics = customMetrics,
                    builtinMetrics = builtinMetrics,
                ),
                debuggingResolution = Resolution.NOT_APPLICABLE,
                loggingResolution = Resolution.NOT_APPLICABLE,
                monitoringResolution = Resolution.NORMAL,
            )
            is BugReportFileUploadPayload -> MarManifest(
                collectionTime = fileCollectionTime,
                type = "android-bugreport",
                device = device,
                metadata = BugReportMarMetadata(
                    bugReportFileName = file!!.name,
                    processingOptions = processingOptions,
                    requestId = requestId,
                ),
                debuggingResolution = Resolution.NORMAL,
                loggingResolution = Resolution.NOT_APPLICABLE,
                monitoringResolution = Resolution.NOT_APPLICABLE,
            )
            is DropBoxEntryFileUploadPayload -> MarManifest(
                collectionTime = fileCollectionTime,
                type = "android-dropbox",
                device = device,
                metadata = DropBoxMarMetadata(
                    entryFileName = file!!.name,
                    tag = metadata.tag,
                    entryTime = metadata.entryTime,
                    timezone = metadata.timezone,
                    cidReference = cidReference,
                    packages = metadata.packages ?: emptyList(),
                    fileTime = metadata.fileTime,
                ),
                debuggingResolution = Resolution.NORMAL,
                loggingResolution = Resolution.NOT_APPLICABLE,
                monitoringResolution = Resolution.NOT_APPLICABLE,
            )
            is LogcatFileUploadPayload -> MarManifest(
                collectionTime = fileCollectionTime,
                type = "android-logcat",
                device = device,
                metadata = LogcatMarMetadata(
                    logFileName = file!!.name,
                    command = command,
                    cid = cid,
                    nextCid = nextCid,
                    containsOops = containsOops,
                    collectionMode = collectionMode,
                ),
                debuggingResolution = debuggingResolution,
                loggingResolution = loggingResolution,
                monitoringResolution = Resolution.NOT_APPLICABLE,
            )
            is StructuredLogFileUploadPayload -> MarManifest(
                collectionTime = fileCollectionTime,
                type = "android-structured",
                device = device,
                metadata = StructuredLogMarMetadata(
                    logFileName = file!!.name,
                    cid = cid,
                    nextCid = nextCid,
                ),
                debuggingResolution = Resolution.NORMAL,
                loggingResolution = Resolution.NOT_APPLICABLE,
                monitoringResolution = Resolution.NOT_APPLICABLE,
            )
        }
    }

    companion object {
        private const val MANIFEST_NAME = "manifest.json"
        const val MAR_EXTENSION = "mar"

        /** Because we can't exactly predict how much storage will be used adding to a zip file, add some margin */
        internal const val MAR_SIZE_TOLERANCE_BYTES = 1_000_000

        /**
         * Split the list into several sublists, each containing a maximum total element value of [maxPerChunk], as
         * defined by [elementSize].
         *
         * If a single element is over the limit, it is still added (as its own chunk).
         */
        fun <E> List<E>.chunkByElementSize(maxPerChunk: Long, elementSize: (E) -> Long): List<List<E>> {
            val chunks = mutableListOf<List<E>>()
            var currentChunk = mutableListOf<E>()
            var currentChunkSize = 0L
            forEach { element ->
                val size = elementSize(element)
                if (currentChunkSize + size > maxPerChunk && !currentChunk.isEmpty()) {
                    chunks.add(currentChunk)
                    currentChunk = mutableListOf()
                    currentChunkSize = 0
                }
                currentChunk.add(element)
                currentChunkSize += size
            }
            chunks.add(currentChunk)
            return chunks
        }

        @VisibleForTesting
        internal fun writeMarFile(marFile: File, manifest: MarManifest, inputFile: File?, compressionLevel: Int) {
            ZipOutputStream(FileOutputStream(marFile)).use { out ->
                out.setLevel(compressionLevel)

                val folderUuid = UUID.randomUUID().toString()
                val dateString = manifest.collectionTime.timestamp.toString().replace(':', '-').replace('.', '-')
                val folder = "${marFile.name}/$dateString-$folderUuid/"
                out.putNextEntry(ZipEntry(folder))
                out.closeEntry()

                out.putNextEntry(ZipEntry("$folder$MANIFEST_NAME"))
                val metadataBytes = BortJson.encodeToString(manifest).encodeToByteArray()
                out.write(metadataBytes)
                out.closeEntry()

                inputFile?.apply {
                    inputStream().use { input ->
                        out.addZipEntry("$folder$name", input)
                    }
                }
            }
        }

        @VisibleForTesting
        internal fun writeBatchedMarFile(marFile: File, inputFiles: List<File>, compressionLevel: Int) {
            ZipOutputStream(FileOutputStream(marFile)).use { out ->
                out.setLevel(compressionLevel)

                inputFiles.forEach { inFile ->
                    ZipFile(inFile).use { zipIn ->
                        for (entry in zipIn.entries()) {
                            // The name includes the full directory structure: this includes the mar filename, so
                            // replace with the new mar filename.
                            val name = entry.name.replace(inFile.name, marFile.name)
                            out.addZipEntry(name, zipIn.getInputStream(entry))
                        }
                    }
                    inFile.delete()
                }
            }
        }
    }
}

fun DeviceInfo.asDevice(projectKey: String) = MarDevice(
    projectKey = projectKey,
    hardwareVersion = hardwareVersion,
    softwareVersion = softwareVersion,
    softwareType = SOFTWARE_TYPE,
    deviceSerial = deviceSerial,
)

data class MarFileWithManifest(
    val marFile: File,
    val manifest: MarManifest,
)
