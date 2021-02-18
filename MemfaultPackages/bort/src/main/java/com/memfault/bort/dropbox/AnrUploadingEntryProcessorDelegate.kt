package com.memfault.bort.dropbox

import com.memfault.bort.AnrFileUploadMetadata
import com.memfault.bort.DropBoxEntryFileUploadMetadata
import com.memfault.bort.TimezoneWithId
import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.time.BootRelativeTime
import java.io.File

class AnrUploadingEntryProcessorDelegate : UploadingEntryProcessorDelegate {
    override val tags = listOf(
        "data_app_anr",
        "system_app_anr",
        "system_server_anr"
    )
    override val debugTag: String
        get() = "UPLOAD_ANR"

    override suspend fun createMetadata(
        tempFile: File,
        tag: String,
        fileTime: AbsoluteTime?,
        entryTime: AbsoluteTime,
        collectionTime: BootRelativeTime
    ): DropBoxEntryFileUploadMetadata =
        AnrFileUploadMetadata(tag, fileTime, entryTime, collectionTime, TimezoneWithId.deviceDefault)
}
