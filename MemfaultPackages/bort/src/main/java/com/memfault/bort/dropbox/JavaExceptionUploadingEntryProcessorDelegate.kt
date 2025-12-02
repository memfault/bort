package com.memfault.bort.dropbox

import android.os.DropBoxManager
import com.memfault.bort.parsers.JavaExceptionParser
import com.memfault.bort.settings.DropBoxSettings
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
    private val dropBoxSettings: DropBoxSettings,
) : UploadingEntryProcessorDelegate {
    override val tags = listOf(
        "data_app_crash",
        "data_app_wtf",
        "system_app_crash",
        "system_app_wtf",
        "system_server_crash",
        "system_server_wtf",
    )

    private fun allowedByRateLimit(tokenBucketKey: String, tag: String): Boolean {
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

    private fun isIgnored(tag: String, exception: com.memfault.bort.parsers.JavaException): Boolean = if (isWtf(tag)) {
        val commonIgnoredWtfs = if (dropBoxSettings.ignoreCommonWtfs) COMMON_IGNORED_WTFS else emptySet()
        val wtfIgnorePatterns = commonIgnoredWtfs + dropBoxSettings.ignoredWtfs
        wtfIgnorePatterns
            .any { ignorePattern ->
                val matchPattern = ignorePattern.removeSuffix(".*")
                exception.exceptionClass?.startsWith(matchPattern) == true ||
                    exception.exceptionMessage?.startsWith(matchPattern) == true
            }
    } else {
        false
    }

    override suspend fun getEntryInfo(entry: DropBoxManager.Entry, entryFile: File): EntryInfo =
        try {
            entryFile.inputStream().use { inputStream ->
                inputStream.bufferedReader().use { bufferedReader ->
                    bufferedReader.useLines { lines ->
                        val exception = JavaExceptionParser(lines).parse()
                        val tokenBucketKey = exception.tokenBucketKey()
                        val allowedByRateLimit = allowedByRateLimit(tokenBucketKey = tokenBucketKey, tag = entry.tag)
                        EntryInfo(
                            tokenBucketKey = tokenBucketKey,
                            packageName = exception.packageName,
                            allowedByRateLimit = allowedByRateLimit,
                            ignored = isIgnored(entry.tag, exception),
                            isTrace = true,
                            isCrash = isCrash(entry),
                            crashTag = null,
                        )
                    }
                }
            }
        } catch (e: Exception) {
            EntryInfo(
                tokenBucketKey = entry.tag,
                allowedByRateLimit = allowedByRateLimit(tokenBucketKey = entry.tag, tag = entry.tag),
                ignored = false,
                isTrace = true,
                isCrash = isCrash(entry),
                crashTag = null,
            )
        }

    private fun isCrash(entry: DropBoxManager.Entry): Boolean {
        if (entry.tag in operationalCrashesExclusions()) {
            return false
        }

        return !isWtf(entry.tag)
    }

    companion object {
        private fun isWtf(tag: String): Boolean = tag.endsWith("wtf")

        private val COMMON_IGNORED_WTFS = setOf(
            "No service published for: appwidget at android.app.SystemServiceRegistry.onServiceNotFound.*",
            "EXTRA_USER_HANDLE missing or invalid, value=0.*",
            "Failed to read field SystemLocale.*",
            "BUG: NetworkAgentInfo.*",
            "Attempt to decrement existing alarm count 0 by 1 for uid 1000.*",
            "Removed TIME_TICK alarm.*",
            "requesting nits when no mapping exists.*",
            "Could not open /sys/kernel/tracing/instances/bootreceiver/trace_pipe.*",
        )
    }
}
