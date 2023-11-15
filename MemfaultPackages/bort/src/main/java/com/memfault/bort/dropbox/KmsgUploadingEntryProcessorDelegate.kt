package com.memfault.bort.dropbox

import android.os.DropBoxManager
import com.memfault.bort.tokenbucket.Kmsg
import com.memfault.bort.tokenbucket.TokenBucketStore
import javax.inject.Inject

class KmsgUploadingEntryProcessorDelegate @Inject constructor(
    @Kmsg private val tokenBucketStore: TokenBucketStore,
) : UploadingEntryProcessorDelegate {
    override val tags = listOf(
        "SYSTEM_LAST_KMSG",
        "SYSTEM_RECOVERY_KMSG",
    )
    override val debugTag: String
        get() = "UPLOAD_KMSG"

    override fun allowedByRateLimit(tokenBucketKey: String, tag: String): Boolean =
        tokenBucketStore.allowedByRateLimit(tokenBucketKey = tokenBucketKey, tag = tag)

    override fun isTraceEntry(entry: DropBoxManager.Entry): Boolean = false

    override fun isCrash(tag: String): Boolean = false
}
