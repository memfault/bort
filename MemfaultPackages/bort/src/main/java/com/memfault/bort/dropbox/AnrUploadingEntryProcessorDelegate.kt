package com.memfault.bort.dropbox

import android.os.DropBoxManager
import com.memfault.bort.AnrFileUploadMetadata
import com.memfault.bort.DropBoxEntryFileUploadMetadata
import com.memfault.bort.TimezoneWithId
import com.memfault.bort.parsers.AnrParser
import com.memfault.bort.shared.Logger
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

    override suspend fun getEntryInfo(entry: DropBoxManager.Entry, entryFile: File): EntryInfo = try {
        entryFile.inputStream().use {
            EntryInfo(entry.tag, AnrParser(it).parse().packageName)
        }
    } catch (ex: Exception) {
        Logger.w("Unable to parse ANR", ex)
        EntryInfo(entry.tag)
    }

    override suspend fun createMetadata(
        entryInfo: EntryInfo,
        tag: String,
        fileTime: AbsoluteTime?,
        entryTime: AbsoluteTime,
        collectionTime: BootRelativeTime
    ): DropBoxEntryFileUploadMetadata =
        AnrFileUploadMetadata(tag, fileTime, entryTime, collectionTime, TimezoneWithId.deviceDefault)
}
