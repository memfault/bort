package com.memfault.bort.dropbox

import android.os.DropBoxManager
import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.time.toAbsoluteTime

abstract class EntryProcessor {
    abstract val tags: List<String>

    abstract suspend fun process(entry: DropBoxManager.Entry, fileTime: AbsoluteTime?)

    suspend fun process(entry: DropBoxManager.Entry) {
        // Call fstat() before entry.inputStream.use() because it will invalidate the file descriptor!
        val fileTime = entry.fstat()?.st_mtime?.toAbsoluteTime()
        process(entry, fileTime)
    }

    fun tagPairs() = tags.map { tag -> tag to this }.toTypedArray()
}
