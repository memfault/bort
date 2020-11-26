package com.memfault.bort.dropbox

import com.memfault.bort.AbsoluteTime
import com.memfault.bort.BootRelativeTime
import com.memfault.bort.BootRelativeTimeProvider
import com.memfault.bort.DropBoxEntryFileUploadMetadata
import com.memfault.bort.KmsgFileUploadMetadata
import com.memfault.bort.TemporaryFileFactory
import java.io.File

class KmsgEntryProcessor(
    tempFileFactory: TemporaryFileFactory,
    enqueueFileUpload: EnqueueFileUpload,
    bootRelativeTimeProvider: BootRelativeTimeProvider,
) : UploadingEntryProcessor(tempFileFactory, enqueueFileUpload, bootRelativeTimeProvider) {
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
        KmsgFileUploadMetadata(tag, fileTime, entryTime, collectionTime)
}
