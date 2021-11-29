package com.memfault.bort.dropbox

import android.os.DropBoxManager
import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.StructuredLogFileUploadPayload
import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.settings.ConfigValue
import com.memfault.bort.shared.Logger
import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.time.CombinedTimeProvider
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.memfault.bort.uploader.EnqueueUpload

private const val DROPBOX_ENTRY_TAG = "memfault_structured"
class StructuredLogEntryProcessor(
    private val temporaryFileFactory: TemporaryFileFactory,
    private val deviceInfoProvider: DeviceInfoProvider,
    private val tokenBucketStore: TokenBucketStore,
    private val enqueueUpload: EnqueueUpload,
    private val combinedTimeProvider: CombinedTimeProvider,
    private val structuredLogDataSourceEnabledConfig: ConfigValue<Boolean>,
) : EntryProcessor() {
    override val tags: List<String> = listOf(DROPBOX_ENTRY_TAG)

    private fun allowedByRateLimit(): Boolean =
        tokenBucketStore.edit { map ->
            map.upsertBucket(DROPBOX_ENTRY_TAG)?.take(tag = "structured") ?: false
        }

    override suspend fun process(entry: DropBoxManager.Entry, fileTime: AbsoluteTime?) {
        if (!structuredLogDataSourceEnabledConfig()) {
            return
        }

        if (!allowedByRateLimit()) {
            return
        }

        temporaryFileFactory.createTemporaryFile(entry.tag, "txt").useFile { tempFile, preventDeletion ->
            val deviceInfo = deviceInfoProvider.getDeviceInfo()
            val metadata = try {
                tempFile.outputStream().use { outStream ->
                    entry.inputStream.use { inStream ->
                        inStream ?: return@useFile

                        StructuredLogStreamingParser(inStream, outStream, deviceInfo)
                            .parse()
                    }
                }
            } catch (ex: StructuredLogParseException) {
                Logger.w("Failed to parse structured log entry", ex)
                return@useFile
            }

            val time = combinedTimeProvider.now()
            enqueueUpload.enqueue(
                tempFile,
                StructuredLogFileUploadPayload(
                    cid = metadata.cid,
                    nextCid = metadata.nextCid,
                    hardwareVersion = deviceInfo.hardwareVersion,
                    deviceSerial = deviceInfo.deviceSerial,
                    softwareVersion = deviceInfo.softwareVersion,
                    collectionTime = time,
                ),
                DROPBOX_ENTRY_TAG,
                collectionTime = time,
            )

            preventDeletion()
        }
    }
}
