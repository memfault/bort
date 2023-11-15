package com.memfault.bort.dropbox

import android.os.DropBoxManager
import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.clientserver.MarMetadata.StructuredLogMarMetadata
import com.memfault.bort.settings.StructuredLogEnabled
import com.memfault.bort.shared.Logger
import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.time.CombinedTimeProvider
import com.memfault.bort.tokenbucket.StructuredLog
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.memfault.bort.uploader.EnqueueUpload
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

private const val DROPBOX_ENTRY_TAG = "memfault_structured"

@ContributesMultibinding(SingletonComponent::class)
class StructuredLogEntryProcessor @Inject constructor(
    private val temporaryFileFactory: TemporaryFileFactory,
    private val deviceInfoProvider: DeviceInfoProvider,
    @StructuredLog private val tokenBucketStore: TokenBucketStore,
    private val enqueueUpload: EnqueueUpload,
    private val combinedTimeProvider: CombinedTimeProvider,
    private val structuredLogDataSourceEnabledConfig: StructuredLogEnabled,
) : EntryProcessor() {
    override val tags: List<String> = listOf(DROPBOX_ENTRY_TAG)

    private fun allowedByRateLimit(): Boolean =
        tokenBucketStore.takeSimple(key = DROPBOX_ENTRY_TAG, tag = "structured")

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
                file = tempFile,
                metadata = StructuredLogMarMetadata(
                    logFileName = tempFile.name,
                    cid = metadata.cid,
                    nextCid = metadata.nextCid,
                ),
                collectionTime = time,
            )

            preventDeletion()
        }
    }
}
