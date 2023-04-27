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
    // Collect lastModified as a stable attribute so that it can't change during sorting.
    val filesNewestFirst = dir.listFiles()?.toList()
        ?.filter { it.isFile }
        ?.map { FileWithMetadata(it, it.lastModified()) }
        ?.sortedByDescending { it.lastModified }
        ?: return FileCleanupResult()

    var bytesUsed: Long = 0
    val deleted = mutableListOf<String>()
    var deletedForAgeCount = 0
    var deletedForStorageCount = 0
    filesNewestFirst.forEach { file ->
        bytesUsed += file.file.length()
        if (bytesUsed > maxDirStorageBytes) {
            deleted.add("storage: ${file.file.name}")
            deletedForStorageCount++
            file.file.deleteSilently()
        } else if (maxFileAge != ZERO) {
            val age = (timeNowMs - file.lastModified).toDuration(MILLISECONDS)
            if (age > maxFileAge) {
                deleted.add("age: ${file.file.name}")
                deletedForAgeCount++
                file.file.deleteSilently()
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

data class FileWithMetadata(
    val file: File,
    val lastModified: Long,
)

data class FileCleanupResult(
    val deletedForStorageCount: Int = 0,
    val deletedForAgeCount: Int = 0,
)

fun File.directorySize(): Long = listFiles()?.filter { it.isFile }?.map { it.length() }?.sum() ?: 0
