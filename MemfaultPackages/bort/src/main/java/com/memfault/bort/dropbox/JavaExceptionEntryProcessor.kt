package com.memfault.bort.dropbox

import android.os.DropBoxManager
import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.DropBoxEntryFileUploadMetadata
import com.memfault.bort.JavaExceptionFileUploadMetadata
import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.TimezoneWithId
import com.memfault.bort.parsers.JavaExceptionParser
import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.time.BootRelativeTime
import com.memfault.bort.time.BootRelativeTimeProvider
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.memfault.bort.tokenbucket.tokenBucketKey
import com.memfault.bort.uploader.EnqueueFileUpload
import java.io.File

class JavaExceptionEntryProcessor(
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
    tokenBucketStore,
) {
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

    override fun getTokenBucketKey(entry: DropBoxManager.Entry, entryFile: File): String =
        try {
            entryFile.inputStream().use {
                JavaExceptionParser(it).parse().tokenBucketKey()
            }
        } catch (e: Exception) {
            entry.tag
        }

    override suspend fun createMetadata(
        tempFile: File,
        tag: String,
        fileTime: AbsoluteTime?,
        entryTime: AbsoluteTime,
        collectionTime: BootRelativeTime
    ): DropBoxEntryFileUploadMetadata =
        JavaExceptionFileUploadMetadata(tag, fileTime, entryTime, collectionTime, TimezoneWithId.deviceDefault)
}
