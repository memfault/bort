package com.memfault.bort.dropbox

import android.os.DropBoxManager
import com.memfault.bort.DevMode
import com.memfault.bort.DropBoxEntryFileUploadMetadata
import com.memfault.bort.JavaExceptionFileUploadMetadata
import com.memfault.bort.TimezoneWithId
import com.memfault.bort.parsers.JavaExceptionParser
import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.time.BootRelativeTime
import com.memfault.bort.tokenbucket.JavaException
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.memfault.bort.tokenbucket.Wtf
import com.memfault.bort.tokenbucket.WtfTotal
import com.memfault.bort.tokenbucket.takeSimple
import com.memfault.bort.tokenbucket.tokenBucketKey
import java.io.File
import javax.inject.Inject

class JavaExceptionUploadingEntryProcessorDelegate @Inject constructor(
    @JavaException private val javaExceptionTokenBucketStore: TokenBucketStore,
    @Wtf private val wtfTokenBucketStore: TokenBucketStore,
    @WtfTotal private val wtfTotalTokenBucketStore: TokenBucketStore,
    private val devMode: DevMode,
) : UploadingEntryProcessorDelegate {
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

    override fun allowedByRateLimit(tokenBucketKey: String, tag: String): Boolean {
        if (devMode.isEnabled()) return true

        return if (tag.endsWith("wtf")) {
            // We limit WTFs using both a bucketed (by stacktrace) and total.
            val allowedByBucketed = wtfTokenBucketStore.allowedByRateLimit(tokenBucketKey = tokenBucketKey, tag = tag)
            // Don't also take from the total bucket if we aren't uploading.
            if (!allowedByBucketed) return false
            wtfTotalTokenBucketStore.takeSimple(tag = tag)
        } else {
            javaExceptionTokenBucketStore.allowedByRateLimit(tokenBucketKey = tokenBucketKey, tag = tag)
        }
    }

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
        collectionTime: BootRelativeTime,
    ): DropBoxEntryFileUploadMetadata =
        JavaExceptionFileUploadMetadata(tag, fileTime, entryTime, collectionTime, TimezoneWithId.deviceDefault)
}
