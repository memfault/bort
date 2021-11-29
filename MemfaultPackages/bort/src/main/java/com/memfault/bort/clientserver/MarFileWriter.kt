package com.memfault.bort.clientserver

import androidx.annotation.VisibleForTesting
import com.memfault.bort.BortJson
import com.memfault.bort.BugReportFileUploadPayload
import com.memfault.bort.COMPRESSION_LEVEL_HIGHEST
import com.memfault.bort.DeviceInfo
import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.DropBoxEntryFileUploadPayload
import com.memfault.bort.FileUploadPayload
import com.memfault.bort.HeartbeatFileUploadPayload
import com.memfault.bort.LogcatFileUploadPayload
import com.memfault.bort.MarFileUploadPayload
import com.memfault.bort.SOFTWARE_TYPE
import com.memfault.bort.StructuredLogFileUploadPayload
import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.addZipEntry
import com.memfault.bort.clientserver.MarMetadata.BugReportMarMetadata
import com.memfault.bort.clientserver.MarMetadata.DropBoxMarMetadata
import com.memfault.bort.clientserver.MarMetadata.HeartbeatMarMetadata
import com.memfault.bort.clientserver.MarMetadata.LogcatMarMetadata
import com.memfault.bort.clientserver.MarMetadata.RebootMarMetadata
import com.memfault.bort.clientserver.MarMetadata.StructuredLogMarMetadata
import com.memfault.bort.ingress.RebootEvent
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.time.CombinedTime
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.encodeToString

class MarFileWriter(
    private val deviceInfoProvider: DeviceInfoProvider,
    private val settingsProvider: SettingsProvider,
    private val temporaryFileFactory: TemporaryFileFactory,
) {
    suspend fun createForFile(file: File?, metadata: FileUploadPayload, collectionTime: CombinedTime): File {
        val marManifest = metadata.asMarManifest(file, collectionTime)
        return temporaryFileFactory.createTemporaryFile(suffix = "mar").useFile { marFile, preventDeletion ->
            writeMarFile(marFile = marFile, manifest = marManifest, inputFile = file)
            preventDeletion()
            marFile
        }
    }

    suspend fun createForReboot(rebootEvent: RebootEvent, collectionTime: CombinedTime): File {
        val marManifest = rebootEvent.asMarManifest(collectionTime)
        return temporaryFileFactory.createTemporaryFile(suffix = "mar").useFile { marFile, preventDeletion ->
            writeMarFile(marFile = marFile, manifest = marManifest, inputFile = null)
            preventDeletion()
            marFile
        }
    }

    private suspend fun device() =
        deviceInfoProvider.getDeviceInfo().asDevice(settingsProvider.httpApiSettings.projectKey)

    suspend fun RebootEvent.asMarManifest(collectionTime: CombinedTime): MarManifest =
        MarManifest(
            collectionTime = collectionTime,
            type = "android-reboot",
            device = device(),
            metadata = RebootMarMetadata(
                reason = event_info.reason,
                subreason = event_info.subreason,
                details = event_info.details,
            )
        )

    suspend fun FileUploadPayload.asMarManifest(file: File?, fileCollectionTime: CombinedTime): MarManifest {
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
                )
            )
            is BugReportFileUploadPayload -> MarManifest(
                collectionTime = fileCollectionTime,
                type = "android-bugreport",
                device = device,
                metadata = BugReportMarMetadata(
                    bugReportFileName = file!!.name,
                    processingOptions = processingOptions,
                    requestId = requestId,
                )
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
                )
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
                )
            )
            is StructuredLogFileUploadPayload -> MarManifest(
                collectionTime = fileCollectionTime,
                type = "android-structured",
                device = device,
                metadata = StructuredLogMarMetadata(
                    logFileName = file!!.name,
                    cid = cid,
                    nextCid = nextCid,
                )
            )
            // Ideally we wouldn't have to do this, but prepareduploader needs it to be like this for now.
            is MarFileUploadPayload -> throw IllegalArgumentException("Can't store MarFileUploadPayload as MarManifest")
        }
    }

    companion object {
        private const val MANIFEST_NAME = "manifest.json"

        @VisibleForTesting
        internal fun writeMarFile(marFile: File, manifest: MarManifest, inputFile: File?) {
            ZipOutputStream(FileOutputStream(marFile)).use { out ->
                out.setLevel(COMPRESSION_LEVEL_HIGHEST)

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
    }
}

fun DeviceInfo.asDevice(projectKey: String) = MarDevice(
    projectKey = projectKey,
    hardwareVersion = hardwareVersion,
    softwareVersion = softwareVersion,
    softwareType = SOFTWARE_TYPE,
    deviceSerial = deviceSerial,
)
