package com.memfault.bort.dropbox

import android.os.DropBoxManager
import com.memfault.bort.AbsoluteTime
import com.memfault.bort.BootRelativeTime
import com.memfault.bort.BootRelativeTimeProvider
import com.memfault.bort.DropBoxEntryFileUploadMetadata
import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.toAbsoluteTime
import java.io.File

abstract class UploadingEntryProcessor(
    private val tempFileFactory: TemporaryFileFactory,
    private val enqueueFileUpload: EnqueueFileUpload,
    private val bootRelativeTimeProvider: BootRelativeTimeProvider,
) : EntryProcessor() {
    abstract val debugTag: String

    abstract suspend fun createMetadata(
        tempFile: File,
        tag: String,
        fileTime: AbsoluteTime?,
        entryTime: AbsoluteTime,
        collectionTime: BootRelativeTime
    ): DropBoxEntryFileUploadMetadata

    override suspend fun process(entry: DropBoxManager.Entry, fileTime: AbsoluteTime?) {
        tempFileFactory.createTemporaryFile(entry.tag, ".txt").useFile { tempFile, preventDeletion ->
            tempFile.outputStream().use { outStream ->
                entry.inputStream.use {
                    inStream ->
                    inStream.copyTo(outStream)
                }
            }

            enqueueFileUpload(
                tempFile,
                createMetadata(
                    tempFile,
                    entry.tag,
                    fileTime,
                    entry.timeMillis.toAbsoluteTime(),
                    bootRelativeTimeProvider.now(),
                ),
                debugTag
            )

            preventDeletion()
        }
    }
}
