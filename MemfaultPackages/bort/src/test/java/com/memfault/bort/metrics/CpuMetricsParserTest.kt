package com.memfault.bort.metrics

import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.memfault.bort.metrics.CpuUsage.Companion.percentUsage
import com.memfault.bort.metrics.CpuUsage.Companion.totalTicks
import com.memfault.bort.parsers.Package
import com.memfault.bort.parsers.PackageManagerReport
import org.junit.Test

class CpuMetricsParserTest {
    private val parser = CpuMetricsParser { "boot-id-1" }
    private var packageManagerReport = PackageManagerReport()

    private val samsungOutput = """
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
    """.trimIndent()

    @Test
    fun testSamsungOutput() {
        val metrics = parser.parseCpuUsage(samsungOutput, null, packageManagerReport)
        assertThat(metrics).isEqualTo(
            CpuUsage(
                ticksUser = 8830083,
                ticksNice = 2521748,
                ticksSystem = 9745235,
                ticksIdle = 125386365,
                ticksIoWait = 353146,
                ticksIrq = 2190102,
                ticksSoftIrq = 484077,
                bootId = "boot-id-1",
            ),
        )
        assertThat(metrics!!.totalTicks()).isEqualTo(149510756)
        assertThat(metrics.percentUsage()!!).isCloseTo(value = 16.1355554914056, delta = 0.00000000001)
    }

    @Test
    fun testEmulatorOutput() {
        val output = """
cpu  798 241 1694 66292 57 0 34 1 0 0
cpu0 508 107 842 33036 46 0 13 0 0 0
cpu1 290 133 852 33256 10 0 21 0 0 0
intr 199026 6 13 0 0 4 0 0 0 1 0 3 53 3 0 0 0 0 0 0 0 0 122 0 0 0 13394 0 1 0 8704 0 88 0 10 0 42370 0 481 0 13185 0 141 498 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 1 0 25 75 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
ctxt 674478
btime 1727203104
processes 2217
procs_running 1
procs_blocked 0
softirq 69386 3 7798 81 253 27825 0 570 12988 0 19868
        """.trimIndent()
        val metrics = parser.parseCpuUsage(output, null, packageManagerReport)
        assertThat(metrics).isEqualTo(
            CpuUsage(
                ticksUser = 798,
                ticksNice = 241,
                ticksSystem = 1694,
                ticksIdle = 66292,
                ticksIoWait = 57,
                ticksIrq = 0,
                ticksSoftIrq = 34,
                bootId = "boot-id-1",
            ),
        )
        assertThat(metrics!!.totalTicks()).isEqualTo(69116)
        assertThat(metrics.percentUsage()!!).isCloseTo(value = 4.0858845998032, delta = 0.00000000001)
    }

    @Test
    fun testInvalidOutput_noCpuLine() {
        val output = """
cpu3 605663 212935 618025 17371080 27420 60263 2624 224 0 0
        """.trimIndent()
        val metrics = parser.parseCpuUsage(output, null, packageManagerReport)
        assertThat(metrics).isNull()
    }

    @Test
    fun testInvalidOutput_invalidCpuLine() {
        val output = """
cpu  8830083 2521748 9745235 125386365 353146
        """.trimIndent()
        val metrics = parser.parseCpuUsage(output, null, packageManagerReport)
        assertThat(metrics).isNull()
    }

    @Test
    fun testInvalidOutput_invalidNumber() {
        val output = """
cpu  8830083 2521748 9745235 125386365.6 353146 2190102 484077 4443 0 0
        """.trimIndent()
        val metrics = parser.parseCpuUsage(output, null, packageManagerReport)
        assertThat(metrics).isNull()
    }

    @Test
    fun testProcPidStat() {
        // output with system ui and bort
        val procPidStats = """
            1004 1384975 (bort) S 6100 6100 6100 0 -1 4194560 14140843 0 199203 0 99492 12235 0 0 20 0 33 0 33901634 3475324928 48574 18446744073709551615 109746831666496 109746832295648 140730914628256 0 0 0 0 69634 1082134264 0 0 0 17 14 0 0 0 0 0 109746832308256 109746832308368 109747862519808 140730914634223 140730914634662 140730914634662 140730914639839 0
            1001 1384979 (systemui) S 6100 6100 6100 0 -1 4194560 14140843 0 199203 0 99492 12235 0 0 20 0 33 0 33901634 3475324928 48574 18446744073709551615 109746831666496 109746832295648 140730914628256 0 0 0 0 69634 1082134264 0 0 0 17 14 0 0 0 0 0 109746832308256 109746832308368 109747862519808 140730914634223 140730914634662 140730914634662 140730914639839 0
            10050 1384980 (MemfaultDumpster) S 6100 6100 6100 0 -1 4194560 14140843 0 199203 0 99492 12235 0 0 20 0 33 0 33901634 3475324928 48574 18446744073709551615 109746831666496 109746832295648 140730914628256 0 0 0 0 69634 1082134264 0 0 0 17 14 0 0 0 0 0 109746832308256 109746832308368 109747862519808 140730914634223 140730914634662 140730914634662 140730914639839 0
            10051 1384981 (Web Content) S 6100 6100 6100 0 -1 4194560 14140843 0 199203 0 99492 12235 0 0 20 0 33 0 33901634 3475324928 48574 18446744073709551615 109746831666496 109746832295648 140730914628256 0 0 0 0 69634 1082134264 0 0 0 17 14 0 0 0 0 0 109746832308256 109746832308368 109747862519808 140730914634223 140730914634662 140730914634662 140730914639839 0
        """.trimIndent()

        packageManagerReport = PackageManagerReport(
            packages = listOf(
                Package(
                    id = "com.memfault.bort",
                    userId = 1004,
                    versionCode = 1,
                    versionName = "1.0",
                ),
                Package(
                    id = "com.android.systemui",
                    userId = 1001,
                    versionCode = 1,
                    versionName = "1.0",
                ),
            ),
        )

        val metrics = parser.parseCpuUsage(samsungOutput, procPidStats, packageManagerReport)
        assertThat(metrics).isEqualTo(
            CpuUsage(
                ticksUser = 8830083,
                ticksNice = 2521748,
                ticksSystem = 9745235,
                ticksIdle = 125386365,
                ticksIoWait = 353146,
                ticksIrq = 2190102,
                ticksSoftIrq = 484077,
                perProcessUsage = mapOf(
                    "com.memfault.bort" to ProcessUsage(
                        processName = "com.memfault.bort",
                        uid = 1004,
                        pid = 1384975,
                        utime = 99492,
                        stime = 12235,
                    ),
                    "com.android.systemui" to ProcessUsage(
                        processName = "com.android.systemui",
                        uid = 1001,
                        pid = 1384979,
                        utime = 99492,
                        stime = 12235,
                    ),
                    "MemfaultDumpster" to ProcessUsage(
                        processName = "MemfaultDumpster",
                        uid = 10050,
                        pid = 1384980,
                        utime = 99492,
                        stime = 12235,
                    ),
                    "Web Content" to ProcessUsage(
                        processName = "Web Content",
                        uid = 10051,
                        pid = 1384981,
                        utime = 99492,
                        stime = 12235,
                    ),
                ),
                bootId = "boot-id-1",
            ),
        )
    }

    @Test
    fun testProcPidStatUnexpectedOutput() {
        // output with system ui and bort
        val procPidStats = """
            1004 1384975 (bort) S 6100 
            1001 1384979 (systemui) S parsing 6100 6100 0 
            invalid (MemfaultDumpster) S 6100 6100 6100
        """.trimIndent()

        val metrics = parser.parseCpuUsage(samsungOutput, procPidStats, packageManagerReport)
        assertThat(metrics).isEqualTo(
            CpuUsage(
                ticksUser = 8830083,
                ticksNice = 2521748,
                ticksSystem = 9745235,
                ticksIdle = 125386365,
                ticksIoWait = 353146,
                ticksIrq = 2190102,
                ticksSoftIrq = 484077,
                perProcessUsage = mapOf(),
                bootId = "boot-id-1",
            ),
        )
    }

    @Test
    fun testProcPidStatDisallowedCharacters() {
        // output with system ui and bort
        val procPidStats = """
            10051 1384981 (Web Content\1) S 6100 6100 6100 0 -1 4194560 14140843 0 199203 0 99492 12235 0 0 20 0 33 0 33901634 3475324928 48574 18446744073709551615 109746831666496 109746832295648 140730914628256 0 0 0 0 69634 1082134264 0 0 0 17 14 0 0 0 0 0 109746832308256 109746832308368 109747862519808 140730914634223 140730914634662 140730914634662 140730914639839 0
        """.trimIndent()

        val metrics = parser.parseCpuUsage(samsungOutput, procPidStats, packageManagerReport)
        assertThat(metrics).isEqualTo(
            CpuUsage(
                ticksUser = 8830083,
                ticksNice = 2521748,
                ticksSystem = 9745235,
                ticksIdle = 125386365,
                ticksIoWait = 353146,
                ticksIrq = 2190102,
                ticksSoftIrq = 484077,
                perProcessUsage = mapOf(
                    "Web Content_1" to ProcessUsage(
                        processName = "Web Content_1",
                        uid = 10051,
                        pid = 1384981,
                        utime = 99492,
                        stime = 12235,
                    ),
                ),
                bootId = "boot-id-1",
            ),
        )
    }
}
