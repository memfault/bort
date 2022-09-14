package com.memfault.bort.requester

import com.memfault.bort.fileExt.deleteSilently
import com.memfault.bort.shared.Logger
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.DurationUnit.MILLISECONDS
import kotlin.time.toDuration

/**
 * During cleanup, we delete the oldest files (by modified timestamp) until under the storage limit, in addition to
 * any file older than the maximum age.
 */
fun cleanupFiles(
    dir: File,
    maxDirStorageBytes: Long,
    maxFileAge: Duration = ZERO,
    timeNowMs: Long = System.currentTimeMillis(),
): FileCleanupResult {
    if (!dir.exists()) return FileCleanupResult()
    val filesNewestFirst =
        dir.listFiles()?.toList()?.sortedByDescending { it.lastModified() } ?: return FileCleanupResult()

    var bytesUsed: Long = 0
    val deleted = mutableListOf<String>()
    var deletedForAgeCount = 0
    var deletedForStorageCount = 0
    filesNewestFirst.filter { it.isFile }.forEach { file ->
        bytesUsed += file.length()
        if (bytesUsed > maxDirStorageBytes) {
            deleted.add("storage: ${file.name}")
            deletedForStorageCount++
            file.deleteSilently()
        } else if (maxFileAge != ZERO) {
            val age = (timeNowMs - file.lastModified()).toDuration(MILLISECONDS)
            if (age > maxFileAge) {
                deleted.add("age: ${file.name}")
                deletedForAgeCount++
                file.deleteSilently()
            }
        }
    }
    if (!deleted.isEmpty()) {
        Logger.i("file_cleanup_deleted", mapOf("deleted" to deleted))
    }
    return FileCleanupResult(
        deletedForStorageCount = deletedForStorageCount,
        deletedForAgeCount = deletedForAgeCount,
    )
}

data class FileCleanupResult(
    val deletedForStorageCount: Int = 0,
    val deletedForAgeCount: Int = 0,
)
