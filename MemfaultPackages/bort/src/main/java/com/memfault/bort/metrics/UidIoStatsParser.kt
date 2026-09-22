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
            val entries = file.useLines { lines ->
                lines.mapNotNull { UidIoEntry.fromLine(it) }.toList()
            }
            UidIoStats(
                bootId = readBootId(),
                writtenBytes = entries.find { it.uid == uid }?.writes?.writeBytes ?: 0,
                writesByUid = entries.associate { it.uid to it.writes },
            )
        } catch (e: IOException) {
            Logger.w("Unable to read uid io stats from ${file.path}", e)
            UidIoStats.EMPTY
        }
}

/**
 * Represents the IO accounting for a single UID from /proc/uid_io/stats.
 *
 * The kernel prints the foreground counters, then the background ones, then the fsync counts:
 * uid fg_rchar fg_wchar fg_read_bytes fg_write_bytes bg_rchar bg_wchar bg_read_bytes bg_write_bytes fg_fsync bg_fsync
 * Reference: https://android.googlesource.com/kernel/common/+/refs/heads/android-mainline/drivers/misc/uid_sys_stats.c
 *
 * write_bytes (indices 4 and 8) counts bytes written to disk after page-cache flushing, making it a
 * proxy for disk wear. wchar (indices 2 and 6) counts bytes handed to write syscalls.
 *
 * Lines that don't have exactly [COLUMN_COUNT] fields are skipped: the field offsets are only
 * meaningful for the layout above, so a kernel emitting anything else is not worth guessing at.
 */
private data class UidIoEntry(
    val uid: Int,
    val writes: UidWrites,
) {
    companion object {
        private const val COLUMN_COUNT = 11
        private val splitRegex = "\\s+".toRegex()

        fun fromLine(line: String): UidIoEntry? {
            val parts = line.trim().split(splitRegex)
            if (parts.size != COLUMN_COUNT) return null
            val uid = parts[0].toIntOrNull() ?: return null
            val fgLogicalWriteBytes = parts[2].toLongOrNull() ?: return null
            val fgWriteBytes = parts[4].toLongOrNull() ?: return null
            val bgLogicalWriteBytes = parts[6].toLongOrNull() ?: return null
            val bgWriteBytes = parts[8].toLongOrNull() ?: return null
            return UidIoEntry(
                uid = uid,
                writes = UidWrites(
                    writeBytes = fgWriteBytes + bgWriteBytes,
                    logicalWriteBytes = fgLogicalWriteBytes + bgLogicalWriteBytes,
                ),
            )
        }
    }
}

/**
 * Bytes written by one UID: [writeBytes] as seen by the block layer, [logicalWriteBytes] as seen by
 * the write syscalls that caused them.
 */
@Serializable
data class UidWrites(
    val writeBytes: Long,
    val logicalWriteBytes: Long,
) {
    operator fun plus(other: UidWrites): UidWrites = UidWrites(
        writeBytes = writeBytes + other.writeBytes,
        logicalWriteBytes = logicalWriteBytes + other.logicalWriteBytes,
    )

    /** Clamped at zero: the counters restart whenever a UID's entry disappears and comes back. */
    fun since(previous: UidWrites): UidWrites = UidWrites(
        writeBytes = maxOf(0, writeBytes - previous.writeBytes),
        logicalWriteBytes = maxOf(0, logicalWriteBytes - previous.logicalWriteBytes),
    )

    companion object {
        val ZERO = UidWrites(writeBytes = 0, logicalWriteBytes = 0)
    }
}

@Serializable
data class UidIoStats(
    val bootId: String,
    val writtenBytes: Long,
    val writesByUid: Map<Int, UidWrites> = emptyMap(),
) {
    companion object {
        val EMPTY = UidIoStats(bootId = "", writtenBytes = 0)
    }
}
