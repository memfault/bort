package com.memfault.bort.metrics

import com.memfault.bort.boot.LinuxBootId
import com.memfault.bort.shared.Logger
import kotlinx.serialization.Serializable
import java.io.File
import java.io.IOException
import javax.inject.Inject

class UidIoStatsParser @Inject constructor(
    private val readBootId: LinuxBootId,
) {
    fun parse(uid: Int, file: File): UidIoStats =
        try {
            val entry = file.useLines { lines ->
                lines.mapNotNull { UidIoEntry.fromLine(it) }
                    .find { it.uid == uid }
            }
            UidIoStats(
                bootId = readBootId(),
                writtenBytes = entry?.let { it.fgWriteBytes + it.bgWriteBytes } ?: 0,
            )
        } catch (e: IOException) {
            Logger.w("Unable to read uid io stats from ${file.path}", e)
            UidIoStats.EMPTY
        }
}

/**
 * Represents the IO accounting for a single UID from /proc/uid_io/stats.
 *
 * Format: uid fg_read_bytes fg_write_bytes bg_read_bytes bg_write_bytes fg_rchar fg_wchar bg_rchar bg_wchar fg_fsync bg_fsync
 * Reference: https://android.googlesource.com/kernel/common/+/refs/heads/android-mainline/drivers/misc/uid_sys_stats.c
 *
 * We use write_bytes (indices 2 and 4) which counts actual bytes written to disk after page-cache
 * flushing, making it a proxy for disk wear rather than raw write syscall volume.
 */
private data class UidIoEntry(
    val uid: Int,
    val fgWriteBytes: Long,
    val bgWriteBytes: Long,
) {
    companion object {
        private val splitRegex = "\\s+".toRegex()

        fun fromLine(line: String): UidIoEntry? {
            val parts = line.trim().split(splitRegex)
            if (parts.size < 5) return null
            return UidIoEntry(
                uid = parts[0].toIntOrNull() ?: return null,
                fgWriteBytes = parts[2].toLongOrNull() ?: return null,
                bgWriteBytes = parts[4].toLongOrNull() ?: return null,
            )
        }
    }
}

@Serializable
data class UidIoStats(
    val bootId: String,
    val writtenBytes: Long,
) {
    companion object {
        val EMPTY = UidIoStats(bootId = "", writtenBytes = 0)
    }
}
