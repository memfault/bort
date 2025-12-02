package com.memfault.bort.dropbox

import android.os.DropBoxManager
import com.memfault.bort.settings.OperationalCrashesExclusions
import com.memfault.bort.shared.Logger
import com.memfault.bort.tokenbucket.Kmsg
import com.memfault.bort.tokenbucket.TokenBucketStore
import java.io.File
import javax.inject.Inject

class KmsgUploadingEntryProcessorDelegate @Inject constructor(
    @Kmsg private val tokenBucketStore: TokenBucketStore,
    private val operationalCrashesExclusions: OperationalCrashesExclusions,
) : UploadingEntryProcessorDelegate {
    override val tags = listOf(
        "SYSTEM_LAST_KMSG",
        "SYSTEM_RECOVERY_KMSG",
    )

    private fun allowedByRateLimit(
        tokenBucketKey: String,
        tag: String,
    ): Boolean =
        tokenBucketStore.allowedByRateLimit(tokenBucketKey = tokenBucketKey, tag = tag)

    private fun isCrash(
        entry: DropBoxManager.Entry,
        entryFile: File,
    ): Boolean {
        val exclusions = operationalCrashesExclusions()
        if (entry.tag in exclusions) {
            return false
        }

        return try {
            entryFile.useLines { lines ->
                val match = lines.firstNotNullOfOrNull { line -> LAST_BOOT_REASON_REGEX.matchEntire(line) }
                match?.groupValues?.get(1)?.contains(KERNEL_PANIC) == true
            }
        } catch (e: Exception) {
            Logger.e("Unable to parse Kmsg", e)
            false
        }
    }

    override suspend fun getEntryInfo(entry: DropBoxManager.Entry, entryFile: File): EntryInfo {
        val isCrash = isCrash(entry, entryFile)
        return EntryInfo(
            tokenBucketKey = entry.tag,
            allowedByRateLimit = allowedByRateLimit(tokenBucketKey = entry.tag, tag = entry.tag),
            ignored = false,
            isTrace = isCrash,
            isCrash = isCrash,
            crashTag = "panic",
        )
    }

    companion object {
        private const val KERNEL_PANIC = "kernel_panic"
        private val LAST_BOOT_REASON_REGEX = Regex("Last boot reason: (.*)")
    }
}
