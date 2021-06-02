package com.memfault.bort.dropbox

import android.os.DropBoxManager
import com.memfault.bort.shared.Logger
import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.time.toAbsoluteTime
import kotlin.time.seconds

abstract class EntryProcessor {
    abstract val tags: List<String>

    abstract suspend fun process(entry: DropBoxManager.Entry, fileTime: AbsoluteTime?)

    suspend fun process(entry: DropBoxManager.Entry) {
        // Call fstat() before entry.inputStream.use() because it will invalidate the file descriptor!
        val stat = entry.fstat()
        if (stat?.st_size == 0L) {
            Logger.w("Empty entry with tag ${entry.tag}")
            return
        }
        val fileTime = stat?.st_mtime?.seconds?.toAbsoluteTime()
        process(entry, fileTime)
    }

    fun tagPairs() = tags.map { tag -> tag to this }.toTypedArray()
}
