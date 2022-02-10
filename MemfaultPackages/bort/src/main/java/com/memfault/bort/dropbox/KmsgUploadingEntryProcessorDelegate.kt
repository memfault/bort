package com.memfault.bort.dropbox

import android.os.DropBoxManager
import com.memfault.bort.DropBoxEntryFileUploadMetadata
import com.memfault.bort.KmsgFileUploadMetadata
import com.memfault.bort.TimezoneWithId
import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.time.BootRelativeTime
import com.memfault.bort.tokenbucket.Kmsg
import com.memfault.bort.tokenbucket.TokenBucketStore
import javax.inject.Inject

class KmsgUploadingEntryProcessorDelegate @Inject constructor(
    @Kmsg override val tokenBucketStore: TokenBucketStore,
) : UploadingEntryProcessorDelegate {
    override val tags = listOf(
        "SYSTEM_LAST_KMSG",
        "SYSTEM_RECOVERY_KMSG",
    )
    override val debugTag: String
        get() = "UPLOAD_KMSG"

    override suspend fun createMetadata(
        entryInfo: EntryInfo,
        tag: String,
        fileTime: AbsoluteTime?,
        entryTime: AbsoluteTime,
        collectionTime: BootRelativeTime
    ): DropBoxEntryFileUploadMetadata =
        KmsgFileUploadMetadata(tag, fileTime, entryTime, collectionTime, TimezoneWithId.deviceDefault)

    override fun isTraceEntry(entry: DropBoxManager.Entry): Boolean = false
}
