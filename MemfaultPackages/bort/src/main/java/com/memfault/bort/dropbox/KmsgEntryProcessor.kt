package com.memfault.bort.dropbox

import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.DropBoxEntryFileUploadMetadata
import com.memfault.bort.KmsgFileUploadMetadata
import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.TimezoneWithId
import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.time.BootRelativeTime
import com.memfault.bort.time.BootRelativeTimeProvider
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.memfault.bort.uploader.EnqueueFileUpload
import java.io.File

class KmsgEntryProcessor(
    tempFileFactory: TemporaryFileFactory,
    enqueueFileUpload: EnqueueFileUpload,
    bootRelativeTimeProvider: BootRelativeTimeProvider,
    deviceInfoProvider: DeviceInfoProvider,
    tokenBucketStore: TokenBucketStore,
) : UploadingEntryProcessor(
    tempFileFactory,
    enqueueFileUpload,
    bootRelativeTimeProvider,
    deviceInfoProvider,
    tokenBucketStore
) {
    override val tags = listOf(
        "SYSTEM_LAST_KMSG",
        "SYSTEM_RECOVERY_KMSG",
    )
    override val debugTag: String
        get() = "UPLOAD_KMSG"

    override suspend fun createMetadata(
        tempFile: File,
        tag: String,
        fileTime: AbsoluteTime?,
        entryTime: AbsoluteTime,
        collectionTime: BootRelativeTime
    ): DropBoxEntryFileUploadMetadata =
        KmsgFileUploadMetadata(tag, fileTime, entryTime, collectionTime, TimezoneWithId.deviceDefault)
}
