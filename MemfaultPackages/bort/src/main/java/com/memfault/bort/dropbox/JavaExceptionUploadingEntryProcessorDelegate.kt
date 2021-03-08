package com.memfault.bort.dropbox

import android.os.DropBoxManager
import com.memfault.bort.DropBoxEntryFileUploadMetadata
import com.memfault.bort.JavaExceptionFileUploadMetadata
import com.memfault.bort.TimezoneWithId
import com.memfault.bort.parsers.JavaExceptionParser
import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.time.BootRelativeTime
import com.memfault.bort.tokenbucket.tokenBucketKey
import java.io.File

class JavaExceptionUploadingEntryProcessorDelegate : UploadingEntryProcessorDelegate {
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

    override suspend fun getEntryInfo(entry: DropBoxManager.Entry, entryFile: File): EntryInfo =
        try {
            entryFile.inputStream().use {
                JavaExceptionParser(it).parse().let { exception ->
                    EntryInfo(
                        exception.tokenBucketKey(),
                        exception.packageName,
                    )
                }
            }
        } catch (e: Exception) {
            EntryInfo(entry.tag)
        }

    override suspend fun createMetadata(
        entryInfo: EntryInfo,
        tag: String,
        fileTime: AbsoluteTime?,
        entryTime: AbsoluteTime,
        collectionTime: BootRelativeTime
    ): DropBoxEntryFileUploadMetadata =
        JavaExceptionFileUploadMetadata(tag, fileTime, entryTime, collectionTime, TimezoneWithId.deviceDefault)
}
