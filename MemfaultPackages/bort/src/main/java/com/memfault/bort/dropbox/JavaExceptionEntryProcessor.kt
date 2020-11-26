package com.memfault.bort.dropbox

import com.memfault.bort.AbsoluteTime
import com.memfault.bort.BootRelativeTime
import com.memfault.bort.BootRelativeTimeProvider
import com.memfault.bort.DropBoxEntryFileUploadMetadata
import com.memfault.bort.JavaExceptionFileUploadMetadata
import com.memfault.bort.TemporaryFileFactory
import java.io.File

class JavaExceptionEntryProcessor(
    tempFileFactory: TemporaryFileFactory,
    enqueueFileUpload: EnqueueFileUpload,
    bootRelativeTimeProvider: BootRelativeTimeProvider,
) : UploadingEntryProcessor(tempFileFactory, enqueueFileUpload, bootRelativeTimeProvider) {
    override val tags = listOf(
        "data_app_crash",
        "data_app_wtf",
        "system_app_crash",
        "system_app_wtf",
        "system_server_crash",
        "system_server_wtf",
    )
    override val debugTag: String
        get() = "UPLOAD_JAVA_EXCEPTION"

    override suspend fun createMetadata(
        tempFile: File,
        tag: String,
        fileTime: AbsoluteTime?,
        entryTime: AbsoluteTime,
        collectionTime: BootRelativeTime
    ): DropBoxEntryFileUploadMetadata =
        JavaExceptionFileUploadMetadata(tag, fileTime, entryTime, collectionTime)
}
