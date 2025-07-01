package com.memfault.bort.metrics

import com.memfault.bort.boot.LinuxBootId
import com.memfault.bort.metrics.CpuUsage.Companion.totalTicks
import com.memfault.bort.parsers.PackageManagerReport
import com.memfault.bort.shared.Logger
import kotlinx.serialization.Serializable
import javax.inject.Inject

class CpuMetricsParser @Inject constructor(
    readLinuxBootId: LinuxBootId,
) {
    private val bootId: String by lazy { readLinuxBootId() }
    private val disallowedMetricCharactersMatcher: Regex by lazy { "\\\\".toRegex() }

    /**
     * Example output:
     cpu  8830083 2521748 9745235 125386365 353146 2190102 484077 4443 0 0
     cpu0 2103003 415263 2338004 12538423 59240 661188 188620 646 0 0
     cpu1 2176556 415405 2195070 12603394 63368 676651 233006 699 0 0
     cpu2 651759 206391 742922 17148277 27766 92672 10317 866 0 0
     cpu3 605663 212935 618025 17371080 27420 60263 2624 224 0 0
     cpu4 624652 217248 636216 17327264 28370 61202 2228 230 0 0
     cpu5 1272053 480309 1532204 14929817 65905 312832 23380 805 0 0
     cpu6 1261903 489965 1535307 14938049 68007 296366 23252 795 0 0
     cpu7 134492 84227 147483 18530057 13067 28924 645 176 0 0
     intr 1012773442 0 418632763 29069241 0 0 0 188473085 0 0 0 0 198706010 0 0 0 0 0 42 1 0 0 20818305 19 0 0 0 0 0 0 0 0 0 0 15635 8610 4898 5296 172184 1499 0 0 11147 0 17 0 0 0 0 15219512 0 104 8 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 1508964 6529734 6367420 5040733 4199577 4470010 12070646 10157537 3244336 0 0 42386 2893 5 0 14 0 0 0 0 0 0 0 0 1383554 0 3238 0 0 923 0 0 0 0 0 0 4164 3160021 18810 2164343 133 0 0 0 0 0 0 0 0 0 0 0 0 15470 2101168 9 0 0 0 0 287056 267908 1316 7187 631845 10530201 115 747262 26209 311 781997 9436 0 170 1471279 1214 1 813809 0 0 0 8505 0 0 0 0 9 5 5284 8848 1 0 0 1 0 0 0 0 0 0 1 0 0 2 3 3 0 2 0 0 1 1 0 1317 1608 0 0 0 0 0 11719 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 38 0 4937 0 19 0 1 19 50 0 38 19 162 0 0 21 0 0 3300 2893 15 0 0 0 0 0 0 0 0 0 0 0 0 0 11817 0 24 54 29 3 1204286 0 0 0 6687964 0 26382 0 16 2013955 65381 8 385 2139240 251529 965304 0 0 5508535 0 0 3953 215869 315010 556879 0 0 7110915 1831539 1684003 0 1588297 1399819 1634940 0 15583 177147 0 0 0 2972026 5495 0 0 12889 3182 7269 0 0 170900 37280 58534 0 0 38704 9753 3192 0 0 78241 15680 4326 19602 39295 0 6 294 36 0 1154 0 0 0 0 62320 331 360 527 0 0 7500208 648378 0 0 0 0 0 0 0 0 0 1962 0 0 0 0 1962 0 0 0 36 0 0 11781 0 0 0 0 2508 299022 15639534 463518 1597 1344 0 0 12 0 0 0 0 0 0 0 0 0 0 0 0 4 0 712 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
     ctxt 1455696451
     btime 1726876714
     processes 1390756
     procs_running 1
     procs_blocked 0
     softirq 156980225 2317304 35376543 36190 18741811 1093954 0 12933582 24229258 30301 62221282
     */
    internal fun parseCpuUsage(
        procStatOutput: String,
        procPidStatOutput: String?,
        packageManagerReport: PackageManagerReport,
    ): CpuUsage? {
        val usage = parseProcStatOutput(procStatOutput) ?: return null
        return usage.copy(
            perProcessUsage =
            procPidStatOutput?.let { parseProcPidStatOutput(it, packageManagerReport) } ?: emptyMap(),
        )
    }

    /**
     * Parses the contents of procPidStatOutput into a map of process names to ProcessUsage objects.
     *
     * Each line has the process uid followed by the contents of /proc/<pid>/stat, separated by a space.
     */
    private fun parseProcPidStatOutput(
        procPidStatOutput: String,
        packageManagerReport: PackageManagerReport,
    ): Map<String, ProcessUsage> =
        PROC_PID_STAT_REGEX.findAll(procPidStatOutput).mapNotNull { matchResult ->
            val (uidStr, pid, comm, _, utimeStr, stimeStr) = matchResult.destructured
            val uid = uidStr.toInt()
            // prefer the package name from the package manager report, but fall back to the comm name
            val processName = packageManagerReport.findByUid(uid).firstOrNull()?.id ?: comm
            ProcessUsage(
                processName = sanitizeProcessName(processName),
                uid = uid,
                pid = pid.toIntOrNull() ?: return@mapNotNull null,
                utime = utimeStr.toLongOrNull() ?: return@mapNotNull null,
                stime = stimeStr.toLongOrNull() ?: return@mapNotNull null,
            )
        }.associateBy { it.processName }

    private fun sanitizeProcessName(process: String, replacement: String = "_"): String =
        process.replace(disallowedMetricCharactersMatcher, replacement)

    private fun parseProcStatOutput(procStatOutput: String): CpuUsage? {
        procStatOutput.lines().forEach { line ->
            if (line.startsWith(PREFIX_CPU)) {
                try {
                    val stats = line.removePrefix(PREFIX_CPU).trim().split(" ").map { it.toLong() }
                    return CpuUsage(
                        ticksUser = stats[POS_USER],
                        ticksNice = stats[POS_NICE],
                        ticksSystem = stats[POS_SYSTEM],
                        ticksIdle = stats[POS_IDLE],
                        ticksIoWait = stats[POS_IOWAIT],
                        ticksIrq = stats[POS_IRQ],
                        ticksSoftIrq = stats[POS_SOFTIRQ],
                        bootId = bootId,
                    )
                } catch (e: NumberFormatException) {
                    Logger.i("Error parsing '$line'")
                } catch (e: IndexOutOfBoundsException) {
                    Logger.i("Error parsing '$line'")
                }
            }
        }
        return null
    }

    companion object {
        private const val PREFIX_CPU = "cpu "
        private val POS_USER = 0
        private val POS_NICE = 1
        private val POS_SYSTEM = 2
        private val POS_IDLE = 3
        private val POS_IOWAIT = 4
        private val POS_IRQ = 5
        private val POS_SOFTIRQ = 6

        private val PROC_PID_STAT_REGEX =
            """(\d+)\s+(\d+)\s+\((.*?)\)\s+(\S+)(?:\s+\S+){10}\s+(\d+)\s+(\d+)""".toRegex()
    }
}

@Serializable
data class ProcessUsage(
    // Either a package name inferred by uid or the comm name from /proc/<pid>/stat
    val processName: String,

    // Parsed via /proc/<pid>/status in MemfaultDumpster
    val uid: Int,

    // Content of /proc/<pid>/stat
    // see https://man7.org/linux/man-pages/man5/proc_pid_stat.5.html
    val pid: Int,
    val utime: Long,
    val stime: Long,
) {
    companion object {
        private fun ProcessUsage.totalTicks() = utime + stime

        fun ProcessUsage.percentUsage(totalCpuUsage: CpuUsage): Double {
            val processCpuTime = totalTicks()
            return if (processCpuTime == 0L) {
                0.0
            } else {
                processCpuTime.toDouble() * 100 / totalCpuUsage.totalTicks()
            }
        }
    }
}

/**
 * Bear in mind when changing this that values will be persisted: default values will be required for any new
 * fields.
 *
 *  A CPU tick represents one cycle of CPU time, incrementing monotonically.
 *
 *  Ticks can be classified as active (user, system) or idle (idle, iowait, irq, etc.).
 *  CPU utilization at any moment can be computed as the ratio of active ticks
 *  to total ticks over a time interval.
 */
@Serializable
data class CpuUsage(
    val ticksUser: Long,
    val ticksNice: Long,
    val ticksSystem: Long,
    val ticksIdle: Long,
    val ticksIoWait: Long,
    val ticksIrq: Long,
    val ticksSoftIrq: Long,
    val perProcessUsage: Map<String, ProcessUsage> = emptyMap(),
    val bootId: String,
) {
    companion object {
        fun CpuUsage.totalTicks() = ticksUser + ticksNice + ticksSystem + ticksIdle + ticksIoWait + ticksIrq +
            ticksSoftIrq
        fun CpuUsage.percentUsage(): Double? {
            val totalTicks = totalTicks().toDouble()
            if (totalTicks <= 0) {
                Logger.i("totalTicks <= 0")
                return null
            }
            return (totalTicks - ticksIdle) * 100 / totalTicks
        }
        val EMPTY = CpuUsage(0, 0, 0, 0, 0, 0, 0, emptyMap(), "")
    }
}
