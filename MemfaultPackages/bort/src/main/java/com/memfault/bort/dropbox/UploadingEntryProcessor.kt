package com.memfault.bort.dropbox

import android.os.DropBoxManager
import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.DropBoxEntryFileUploadMetadata
import com.memfault.bort.DropBoxEntryFileUploadPayload
import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.time.BootRelativeTime
import com.memfault.bort.time.BootRelativeTimeProvider
import com.memfault.bort.time.toAbsoluteTime
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.memfault.bort.uploader.EnqueueFileUpload
import java.io.File

abstract class UploadingEntryProcessor(
    private val tempFileFactory: TemporaryFileFactory,
    private val enqueueFileUpload: EnqueueFileUpload,
    private val bootRelativeTimeProvider: BootRelativeTimeProvider,
    private val deviceInfoProvider: DeviceInfoProvider,
    private val tokenBucketStore: TokenBucketStore,
) : EntryProcessor() {
    abstract val debugTag: String

    abstract suspend fun createMetadata(
        tempFile: File,
        tag: String,
        fileTime: AbsoluteTime?,
        entryTime: AbsoluteTime,
        collectionTime: BootRelativeTime
    ): DropBoxEntryFileUploadMetadata

    open fun getTokenBucketKey(entry: DropBoxManager.Entry, entryFile: File): String = entry.tag

    private fun allowedByRateLimit(entry: DropBoxManager.Entry, entryFile: File): Boolean =
        tokenBucketStore.edit { map ->
            val bucket = map.upsertBucket(getTokenBucketKey(entry, entryFile)) ?: return@edit false
            bucket.take()
        }

    override suspend fun process(entry: DropBoxManager.Entry, fileTime: AbsoluteTime?) {
        tempFileFactory.createTemporaryFile(entry.tag, ".txt").useFile { tempFile, preventDeletion ->
            tempFile.outputStream().use { outStream ->
                entry.inputStream.use {
                    inStream ->
                    inStream.copyTo(outStream)
                }
            }

            if (!allowedByRateLimit(entry, tempFile)) {
                return
            }

            val deviceInfo = deviceInfoProvider.getDeviceInfo()
            enqueueFileUpload(
                tempFile,
                DropBoxEntryFileUploadPayload(
                    hardwareVersion = deviceInfo.hardwareVersion,
                    deviceSerial = deviceInfo.deviceSerial,
                    softwareVersion = deviceInfo.softwareVersion,
                    metadata = createMetadata(
                        tempFile,
                        entry.tag,
                        fileTime,
                        entry.timeMillis.toAbsoluteTime(),
                        bootRelativeTimeProvider.now(),
                    )
                ),
                debugTag
            )

            preventDeletion()
        }
    }
}
