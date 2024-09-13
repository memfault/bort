package com.memfault.bort.dropbox

import android.os.DropBoxManager
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.tokenbucket.Other
import com.memfault.bort.tokenbucket.TokenBucketStore
import java.io.File
import javax.inject.Inject

/**
 * Supports collecting "other" DropBoxManager Entries depending on the configured Bort SDK settings. Entries collected
 * in this way won't be processed by the backend, but they will show up on the Device Timeline. This should allow
 * us to quickly collect other DropBoxManager Entries in the future without an SDK update, so we can at least collect
 * the data.
 */
class OtherDropBoxEntryUploadingEntryProcessorDelegate @Inject constructor(
    @Other private val tokenBucketStore: TokenBucketStore,
    private val settingsProvider: SettingsProvider,
) : UploadingEntryProcessorDelegate {
    override val tags: List<String>
        get() = settingsProvider.dropBoxSettings.otherTags.toList()

    override val debugTag: String
        get() = "OTHER_TAGS"

    override fun allowedByRateLimit(
        tokenBucketKey: String,
        tag: String,
    ): Boolean = tokenBucketStore.allowedByRateLimit(tokenBucketKey = tokenBucketKey, tag = tag)

    override fun isTraceEntry(entry: DropBoxManager.Entry): Boolean = false

    override fun isCrash(entry: DropBoxManager.Entry, entryFile: File): Boolean = false
}
