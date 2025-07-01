package com.memfault.bort.metrics

import android.app.Application
import android.os.StatFs
import com.memfault.bort.boot.LinuxBootId
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.Serializable
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A parser for /proc/diskstats output.
 * This implementation only tracking writes to block devices that are not loop devices, device-mapped or ramdisks.
 * It also skips logical partitions and only gathers the aggregate writes for each block device.
 */
@Singleton
class DiskActivityParser @Inject constructor(
    sectorSizeProvider: DiskInfoProvider,
    private val readBootId: LinuxBootId,
) {
    private val sectorSizeBytes: Long by lazy {
        sectorSizeProvider.getSectorSizeBytes()
    }

    private val realBlockDevices: Set<String> by lazy {
        sectorSizeProvider.getBlockDevices()
            .filter { blockDevice -> LOGICAL_DEVICE_PREFIXES.none { blockDevice.startsWith(it) } }
            .toSet()
    }

    fun parse(diskStatsFile: File): DiskActivity =
        try {
            DiskActivity(
                bootId = readBootId(),
                stats = parseStatsFile(diskStatsFile),
                sectorSize = sectorSizeBytes,
            )
        } catch (e: IOException) {
            Logger.w("Unable to read disk stats from file: ${diskStatsFile.path}", e)
            DiskActivity.EMPTY
        }

    private fun parseStatsFile(diskStats: File) =
        diskStats.useLines { lines ->
            lines.filter { it.isNotBlank() }
                .mapNotNull { DiskStat.fromProcStatLine(it) }
                .filter { it.deviceName in realBlockDevices }
                .toList()
        }

    companion object {
        // List of prefixes for non-physical devices that we want to ignore
        val LOGICAL_DEVICE_PREFIXES = listOf(
            "loop",
            "dm-",
            "ram",
            "zram",
        )
    }
}

@Serializable
data class DiskActivity(
    val bootId: String,
    val sectorSize: Long,
    val stats: List<DiskStat>,
) {
    operator fun minus(other: DiskActivity): DiskActivity {
        // If the bootId is different, we consider the previous activity as empty
        val previousActivity = if (bootId == other.bootId) {
            other
        } else {
            EMPTY
        }

        val newStats = stats.map { currentStat ->
            val previousStat = previousActivity.stats.find { it.deviceName == currentStat.deviceName }
            if (previousStat != null) {
                currentStat.copy(
                    readsCompleted = currentStat.readsCompleted - previousStat.readsCompleted,
                    readsMerged = currentStat.readsMerged - previousStat.readsMerged,
                    sectorsRead = currentStat.sectorsRead - previousStat.sectorsRead,
                    timeSpentReading = currentStat.timeSpentReading - previousStat.timeSpentReading,
                    writesCompleted = currentStat.writesCompleted - previousStat.writesCompleted,
                    writesMerged = currentStat.writesMerged - previousStat.writesMerged,
                    sectorsWritten = currentStat.sectorsWritten - previousStat.sectorsWritten,
                    timeSpentWriting = currentStat.timeSpentWriting - previousStat.timeSpentWriting,
                    ioInProgress = currentStat.ioInProgress - previousStat.ioInProgress,
                    timeSpentDoingIO = currentStat.timeSpentDoingIO - previousStat.timeSpentDoingIO,
                    weightedTimeSpentDoingIO =
                    currentStat.weightedTimeSpentDoingIO - previousStat.weightedTimeSpentDoingIO,
                    discardsCompletedSuccessfully = currentStat.discardsCompletedSuccessfully?.minus(
                        previousStat.discardsCompletedSuccessfully ?: 0,
                    ),
                    discardsMerged = currentStat.discardsMerged?.minus(previousStat.discardsMerged ?: 0),
                    sectorsDiscarded = currentStat.sectorsDiscarded?.minus(previousStat.sectorsDiscarded ?: 0),
                    timeSpentDiscarding = currentStat.timeSpentDiscarding?.minus(previousStat.timeSpentDiscarding ?: 0),
                    flushRequestsCompletedSuccessfully = currentStat.flushRequestsCompletedSuccessfully?.minus(
                        previousStat.flushRequestsCompletedSuccessfully ?: 0,
                    ),
                    timeSpentFlushing = currentStat.timeSpentFlushing?.minus(previousStat.timeSpentFlushing ?: 0),
                )
            } else {
                currentStat // No previous stat, return current stat as is
            }
        }.filter { it.sectorsWritten > 0 } // Filter out devices with no writes

        return DiskActivity(
            bootId = bootId,
            stats = newStats,
            sectorSize = sectorSize,
        )
    }

    companion object {
        val EMPTY = DiskActivity(
            bootId = "",
            stats = emptyList(),
            sectorSize = RealDiskInfoProvider.DEFAULT_BLOCK_SIZE,
        )
    }
}

/**
 * Data class representing disk statistics for a block device as given by procstat.
 * Reference: https://www.kernel.org/doc/Documentation/block/stat.txt
 * Sample output from /proc/diskstats:
 *  259       0 nvme1n1 264 0 9040 12 0 0 0 0 0 6 12 0 0 0 0 0 0
 *  259       1 nvme1n1p1 50 0 1184 1 0 0 0 0 0 2 1 0 0 0 0 0 0
 *  259       2 nvme1n1p2 58 0 2528 2 0 0 0 0 0 3 2 0 0 0 0 0 0
 *  259       3 nvme1n1p3 58 0 2528 5 0 0 0 0 0 6 5 0 0 0 0 0 0
 *  259       4 nvme0n1 3688665 249359 208943502 605119 17638920 58526380 939391914 35098669 0 4083663 36383735 0 0 0 0 3598614 679946
 *  259       5 nvme0n1p1 1372 768 5116 1466 2 0 2 0 0 11 1466 0 0 0 0 0 0
 *  259       6 nvme0n1p2 3687200 248591 208935602 603648 17638917 58526380 939391912 35098669 1 4166024 35702318 0 0 0 0 0 0
 */
@Serializable
data class DiskStat(
    val major: Int,
    val minor: Int,
    val deviceName: String,
    val readsCompleted: Long,
    val readsMerged: Long,
    val sectorsRead: Long,
    val timeSpentReading: Long,
    val writesCompleted: Long,
    val writesMerged: Long,
    val sectorsWritten: Long,
    val timeSpentWriting: Long,
    val ioInProgress: Long,
    val timeSpentDoingIO: Long,
    val weightedTimeSpentDoingIO: Long,
    /* These are available from 4.18 onwards */
    val discardsCompletedSuccessfully: Long?,
    val discardsMerged: Long?,
    val sectorsDiscarded: Long?,
    val timeSpentDiscarding: Long?,
    /* These are available from 5.5 onwards */
    val flushRequestsCompletedSuccessfully: Long?,
    val timeSpentFlushing: Long?,
) {
    companion object {
        private val splitRegex = "\\s+".toRegex()

        fun fromProcStatLine(line: String): DiskStat? {
            val parts = line.trim().split(splitRegex)
            if (parts.size < 14) return null

            return DiskStat(
                major = parts[0].toIntOrNull() ?: return null,
                minor = parts[1].toIntOrNull() ?: return null,
                deviceName = parts[2],
                readsCompleted = parts[3].toLongOrNull() ?: return null,
                readsMerged = parts[4].toLongOrNull() ?: return null,
                sectorsRead = parts[5].toLongOrNull() ?: return null,
                timeSpentReading = parts[6].toLongOrNull() ?: return null,
                writesCompleted = parts[7].toLongOrNull() ?: return null,
                writesMerged = parts[8].toLongOrNull() ?: return null,
                sectorsWritten = parts[9].toLongOrNull() ?: return null,
                timeSpentWriting = parts[10].toLongOrNull() ?: return null,
                ioInProgress = parts[11].toLongOrNull() ?: return null,
                timeSpentDoingIO = parts[12].toLongOrNull() ?: return null,
                weightedTimeSpentDoingIO = parts[13].toLongOrNull() ?: return null,
                discardsCompletedSuccessfully = parts.getOrNull(14)?.toLongOrNull(),
                discardsMerged = parts.getOrNull(15)?.toLongOrNull(),
                sectorsDiscarded = parts.getOrNull(16)?.toLongOrNull(),
                timeSpentDiscarding = parts.getOrNull(17)?.toLongOrNull(),
                flushRequestsCompletedSuccessfully = parts.getOrNull(18)?.toLongOrNull(),
                timeSpentFlushing = parts.getOrNull(19)?.toLongOrNull(),
            )
        }
    }
}

interface DiskInfoProvider {
    fun getSectorSizeBytes(): Long
    fun getBlockDevices(): List<String>
}

@Singleton
@ContributesBinding(SingletonComponent::class, boundType = DiskInfoProvider::class)
class RealDiskInfoProvider @Inject constructor(
    private val context: Application,
) : DiskInfoProvider {
    override fun getSectorSizeBytes(): Long =
        try {
            val stat = StatFs(context.dataDir.path)
            if (stat.blockSizeLong > 0) {
                stat.blockSizeLong
            } else {
                DEFAULT_BLOCK_SIZE
            }
        } catch (e: IllegalArgumentException) {
            DEFAULT_BLOCK_SIZE
        }

    override fun getBlockDevices(): List<String> {
        val files = File("/sys/block").listFiles()
        if (files == null) {
            Reporting.report().event(name = "disk_stats.error", countInReport = true, internal = true)
                .add(value = "Cannot read contents of /sys/block")
            return listOf()
        }
        return files.filter { it.isDirectory }
            .map { it.name }
            .toList()
    }

    companion object {
        // This is a common sector size, but may not be accurate for all devices
        const val DEFAULT_BLOCK_SIZE = 512L
    }
}
