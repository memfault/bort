package com.memfault.bort.dropbox

import android.os.DropBoxManager
import com.memfault.bort.parsers.JavaExceptionParser
import com.memfault.bort.settings.OperationalCrashesExclusions
import com.memfault.bort.tokenbucket.JavaException
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.memfault.bort.tokenbucket.Wtf
import com.memfault.bort.tokenbucket.WtfTotal
import com.memfault.bort.tokenbucket.tokenBucketKey
import java.io.File
import javax.inject.Inject

class JavaExceptionUploadingEntryProcessorDelegate @Inject constructor(
    @JavaException private val javaExceptionTokenBucketStore: TokenBucketStore,
    @Wtf private val wtfTokenBucketStore: TokenBucketStore,
    @WtfTotal private val wtfTotalTokenBucketStore: TokenBucketStore,
    private val operationalCrashesExclusions: OperationalCrashesExclusions,
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

    override val crashTag: String? = null

    override fun allowedByRateLimit(tokenBucketKey: String, tag: String): Boolean {
        return if (isWtf(tag)) {
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
                        tokenBucketKey = exception.tokenBucketKey(),
                        packageName = exception.packageName,
                    )
                }
            }
        } catch (e: Exception) {
            EntryInfo(entry.tag)
        }

    override fun isCrash(entry: DropBoxManager.Entry, entryFile: File): Boolean {
        if (entry.tag in operationalCrashesExclusions()) {
            return false
        }

        return !isWtf(entry.tag)
    }

    companion object {
        private fun isWtf(tag: String): Boolean = tag.endsWith("wtf")
    }
}
