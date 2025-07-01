package com.memfault.bort.metrics

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import org.junit.Test
import java.io.File

class DiskActivityParserTest {
    private val sectorSizeBytes = 4096L
    private val parser = DiskActivityParser(
        object : DiskInfoProvider {
            override fun getSectorSizeBytes(): Long = sectorSizeBytes
            override fun getBlockDevices(): List<String> = listOf("sda", "sdb", "mmcblk0", "mmcblk1")
        },
    ) { "boot-id-1" }

    @Test fun `correctly parses data from a real device`() {
        val diskStats = parser.parse(realWorldOutput.inTempFile())

        assertThat(diskStats.sectorSize).isEqualTo(sectorSizeBytes)
        assertThat(diskStats.bootId).isEqualTo("boot-id-1")

        // 8       0 sda 31170 42441 4787096 32784 19626 1454 1701264 30600 0 50272 70089 1151 0 1818064 5309 4547 1394
        // 8      16 sdb 5 0 40 2 0 0 0 0 0 4 2 0 0 0 0 0 0

        assertThat(diskStats.stats).isEqualTo(
            listOf(
                DiskStat(
                    major = 8,
                    minor = 0,
                    deviceName = "sda",
                    readsCompleted = 31170L,
                    readsMerged = 42441L,
                    sectorsRead = 4787096L,
                    timeSpentReading = 32784L,
                    writesCompleted = 19626L,
                    writesMerged = 1454L,
                    sectorsWritten = 1701264L,
                    timeSpentWriting = 30600L,
                    ioInProgress = 0L,
                    timeSpentDoingIO = 50272L,
                    weightedTimeSpentDoingIO = 70089L,
                    discardsCompletedSuccessfully = 1151L,
                    discardsMerged = 0L,
                    sectorsDiscarded = 1818064L,
                    timeSpentDiscarding = 5309L,
                    flushRequestsCompletedSuccessfully = 4547L,
                    timeSpentFlushing = 1394L,
                ),
                DiskStat(
                    major = 8,
                    minor = 16,
                    deviceName = "sdb",
                    readsCompleted = 5L,
                    readsMerged = 0L,
                    sectorsRead = 40L,
                    timeSpentReading = 2L,
                    writesCompleted = 0L,
                    writesMerged = 0L,
                    sectorsWritten = 0L,
                    timeSpentWriting = 0L,
                    ioInProgress = 0L,
                    timeSpentDoingIO = 4L,
                    weightedTimeSpentDoingIO = 2L,
                    discardsCompletedSuccessfully = 0,
                    discardsMerged = 0,
                    sectorsDiscarded = 0,
                    timeSpentDiscarding = 0,
                    flushRequestsCompletedSuccessfully = 0,
                    timeSpentFlushing = 0,
                ),
            ),
        )
    }

    @Test fun `returns the empty state when the file cannot be read`() {
        val diskStats = parser.parse(File("/does/not/exist"))
        assertThat(diskStats).isEqualTo(DiskActivity.EMPTY)
    }

    @Test fun `ignores invalid lines`() {
        assertThat(
            parser.parse(
                """some
invalid
   8       0 sda 31170 42441 4787096 32784 19626 1454 1701264 30600 0 50272 70089 1151 0 1818064 5309 4547 1394
inputs
                """.trimIndent().inTempFile(),
            ),
        )
            .isEqualTo(
                DiskActivity(
                    bootId = "boot-id-1",
                    stats = listOf(
                        DiskStat(
                            major = 8,
                            minor = 0,
                            deviceName = "sda",
                            readsCompleted = 31170L,
                            readsMerged = 42441L,
                            sectorsRead = 4787096L,
                            timeSpentReading = 32784L,
                            writesCompleted = 19626L,
                            writesMerged = 1454L,
                            sectorsWritten = 1701264L,
                            timeSpentWriting = 30600L,
                            ioInProgress = 0L,
                            timeSpentDoingIO = 50272L,
                            weightedTimeSpentDoingIO = 70089L,
                            discardsCompletedSuccessfully = 1151L,
                            discardsMerged = 0L,
                            sectorsDiscarded = 1818064L,
                            timeSpentDiscarding = 5309L,
                            flushRequestsCompletedSuccessfully = 4547L,
                            timeSpentFlushing = 1394L,
                        ),
                    ),
                    sectorSize = sectorSizeBytes,
                ),
            )
    }

    @Test fun `calculates the number of written bytes with the minus operator`() {
        val diskStats1 = parser.parse(
            """
    8       0 sda 31170 42441 4787096 32784 19626 1454 1701264 30600 0 50272 70089 1151 0 1818064 5309 4547 1394
    8       0 sdb 0 0 0 0 0 0 300 0 0 0 0 0 0 0 0 0 0
            """.trimIndent().inTempFile(),
        )
        val diskStats2 = parser.parse(
            """
    8       0 sda 31170 42441 4787096 32784 19626 1454 1711100 30600 0 50272 70089 1151 0 1818064 5309 4547 1394
    8       0 sdb 0 0 0 0 0 0 500 0 0 0 0 0 0 0 0 0 0
            """.trimIndent().inTempFile(),
        )

        val difference = diskStats2 - diskStats1

        assertThat(difference.stats).hasSize(2)
        assertThat(difference.stats[0]).isEqualTo(
            DiskStat(
                major = 8,
                minor = 0,
                deviceName = "sda",
                readsCompleted = 0L,
                readsMerged = 0L,
                sectorsRead = 0L,
                timeSpentReading = 0L,
                writesCompleted = 0L,
                writesMerged = 0L,
                sectorsWritten = 1711100L - 1701264L,
                timeSpentWriting = 0L,
                ioInProgress = 0L,
                timeSpentDoingIO = 0L,
                weightedTimeSpentDoingIO = 0L,
                discardsCompletedSuccessfully = 0L,
                discardsMerged = 0L,
                sectorsDiscarded = 0L,
                timeSpentDiscarding = 0L,
                flushRequestsCompletedSuccessfully = 0L,
                timeSpentFlushing = 0L,
            ),
        )
        assertThat(difference.stats[1]).isEqualTo(
            DiskStat(
                major = 8,
                minor = 0,
                deviceName = "sdb",
                readsCompleted = 0L,
                readsMerged = 0L,
                sectorsRead = 0L,
                timeSpentReading = 0L,
                writesCompleted = 0L,
                writesMerged = 0L,
                sectorsWritten = (500L - 300L),
                timeSpentWriting = 0L,
                ioInProgress = 0L,
                timeSpentDoingIO = 0L,
                weightedTimeSpentDoingIO = 0L,
                discardsCompletedSuccessfully = 0L,
                discardsMerged = 0L,
                sectorsDiscarded = 0L,
                timeSpentDiscarding = 0L,
                flushRequestsCompletedSuccessfully = 0L,
                timeSpentFlushing = 0L,
            ),
        )
    }

    @Test fun `minus operator does not consider previous entries if they are from another boot`() {
        val diskStats1 = parser.parse(
            """
    8       0 sda 31170 42441 4787096 32784 19626 1454 1701264 30600 0 50272 70089 1151 0 1818064 5309 4547 1394
    8       0 sdb 0 0 0 0 0 0 300 0 0 0 0 0 0 0 0 0 0
            """.trimIndent().inTempFile(),
        )
        val diskStats2 = parser.parse(
            """
    8       0 sda 31170 42441 4787096 32784 19626 1454 1701264 30600 0 50272 81080 1151 0 1818064 5309 4547 1394
    8       0 sdb 0 0 0 0 0 0 500 0 0 0 0 0 0 0 0 0 0
            """.trimIndent().inTempFile(),
        ).copy(bootId = "next")

        val difference = diskStats2 - diskStats1

        assertThat(difference.stats).hasSize(2)
        assertThat(difference.stats[0]).isEqualTo(
            DiskStat(
                major = 8,
                minor = 0,
                deviceName = "sda",
                readsCompleted = 31170L,
                readsMerged = 42441L,
                sectorsRead = 4787096L,
                timeSpentReading = 32784L,
                writesCompleted = 19626L,
                writesMerged = 1454L,
                sectorsWritten = 1701264L,
                timeSpentWriting = 30600L,
                ioInProgress = 0L,
                timeSpentDoingIO = 50272L,
                weightedTimeSpentDoingIO = 81080L,
                discardsCompletedSuccessfully = 1151L,
                discardsMerged = 0L,
                sectorsDiscarded = 1818064L,
                timeSpentDiscarding = 5309L,
                flushRequestsCompletedSuccessfully = 4547L,
                timeSpentFlushing = 1394L,
            ),
        )
        assertThat(difference.stats[1]).isEqualTo(
            DiskStat(
                major = 8,
                minor = 0,
                deviceName = "sdb",
                readsCompleted = 0L,
                readsMerged = 0L,
                sectorsRead = 0L,
                timeSpentReading = 0L,
                writesCompleted = 0L,
                writesMerged = 0L,
                sectorsWritten = 500L,
                timeSpentWriting = 0L,
                ioInProgress = 0L,
                timeSpentDoingIO = 0L,
                weightedTimeSpentDoingIO = 0L,
                discardsCompletedSuccessfully = 0L,
                discardsMerged = 0L,
                sectorsDiscarded = 0L,
                timeSpentDiscarding = 0L,
                flushRequestsCompletedSuccessfully = 0L,
                timeSpentFlushing = 0L,
            ),
        )
    }

    @Test fun `correctly ignores partitions and only parses aggregated stats for primary block devices`() {
        val parsed = parser.parse(
            trickyOutput.inTempFile(),
        )

        assertThat(parsed.stats.map { it.deviceName }).containsExactlyInAnyOrder(
            "sda",
            "sdb",
            "mmcblk0",
            "mmcblk1",
        )
    }

    companion object {
        const val realWorldOutput = """1       0 ram0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
   1       1 ram1 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
   1       2 ram2 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
   1       3 ram3 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
   1       4 ram4 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
   1       5 ram5 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
   1       6 ram6 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
   1       7 ram7 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
   1       8 ram8 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
   1       9 ram9 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
   1      10 ram10 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
   1      11 ram11 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
   1      12 ram12 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
   1      13 ram13 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
   1      14 ram14 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
   1      15 ram15 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
   7       0 loop0 12 0 296 9 0 0 0 0 0 28 9 0 0 0 0 0 0
   7       8 loop1 38 3 3984 10 0 0 0 0 0 36 10 0 0 0 0 0 0
   7      16 loop2 69 2 9032 20 0 0 0 0 0 72 20 0 0 0 0 0 0
   7      24 loop3 123 20 14672 34 0 0 0 0 0 148 34 0 0 0 0 0 0
   7      32 loop4 20 1 456 3 0 0 0 0 0 44 3 0 0 0 0 0 0
   7      40 loop5 75 0 11968 20 0 0 0 0 0 68 20 0 0 0 0 0 0
   7      48 loop6 80 2 10496 38 0 0 0 0 0 132 38 0 0 0 0 0 0
   7      56 loop7 320 1 41744 121 0 0 0 0 0 200 121 0 0 0 0 0 0
   7      64 loop8 20 0 248 5 0 0 0 0 0 32 5 0 0 0 0 0 0
   7      72 loop9 59 0 5696 19 0 0 0 0 0 104 19 0 0 0 0 0 0
   7      80 loop10 501 21 46512 319 0 0 0 0 0 648 319 0 0 0 0 0 0
   7      88 loop11 50 0 4760 26 0 0 0 0 0 76 26 0 0 0 0 0 0
   7      96 loop12 25 0 1504 5 0 0 0 0 0 52 5 0 0 0 0 0 0
   7     104 loop13 99 2 16112 59 0 0 0 0 0 132 59 0 0 0 0 0 0
   7     112 loop14 9 34 344 13 0 0 0 0 0 28 13 0 0 0 0 0 0
   7     120 loop15 7 17 192 25 0 0 0 0 0 28 25 0 0 0 0 0 0
   8       0 sda 31170 42441 4787096 32784 19626 1454 1701264 30600 0 50272 70089 1151 0 1818064 5309 4547 1394
   8       1 sda1 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
   8       2 sda2 296 8 9208 89 52 55 840 49 0 580 149 55 0 32568 9 0 0
   8       3 sda3 7 0 112 0 4 0 16 0 0 24 1 0 0 0 0 0 0
   8       4 sda4 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
   8       5 sda5 20 0 3112 2 0 0 0 0 0 32 2 0 0 0 0 0 0
   8       6 sda6 16049 1080 3732680 7559 0 0 0 0 0 10956 7559 0 0 0 0 0 0
   8       7 sda7 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
   8       8 sda8 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
   8       9 sda9 54 8 664 21 18 1 232 23 0 92 70 8 0 168 25 0 0
   8      10 sda10 2 0 2048 0 0 0 0 0 0 4 0 0 0 0 0 0 0
   8      11 sda11 22 56 768 2 0 0 0 0 0 20 2 0 0 0 0 0 0
   8      12 sda12 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
   8      13 sda13 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
   8      14 sda14 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
   8      15 sda15 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
 259       0 sda16 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
 259       1 sda17 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
 259       2 sda18 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
 259       3 sda19 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
 259       4 sda20 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
 259       5 sda21 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
 259       6 sda22 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
 259       7 sda23 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
 259       8 sda24 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
 259       9 sda25 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
 259      10 sda26 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
 259      11 sda27 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
 259      12 sda28 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
 259      13 sda29 14715 41289 1038464 25102 19552 1398 1700176 30526 0 41928 60903 1088 0 1785328 5274 0 0
   8      16 sdb 5 0 40 2 0 0 0 0 0 4 2 0 0 0 0 0 0
   8      17 sdb1 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
   8      18 sdb2 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
   8      19 sdb3 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
   8      20 sdb4 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
   8      21 sdb5 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
 254       0 dm-0 19 0 3048 8 0 0 0 0 0 32 8 0 0 0 0 0 0
 254       1 dm-1 1527 0 626936 1056 0 0 0 0 0 3072 1056 0 0 0 0 0 0
 254       2 dm-2 6610 0 1286920 2472 0 0 0 0 0 4552 2472 0 0 0 0 0 0
 254       3 dm-3 23 0 472 16 0 0 0 0 0 48 16 0 0 0 0 0 0
 254       4 dm-4 3640 0 759352 2264 0 0 0 0 0 2576 2264 0 0 0 0 0 0
 254       5 dm-5 3821 0 861896 1520 0 0 0 0 0 2932 1520 0 0 0 0 0 0
 254       6 dm-6 422 0 194016 140 0 0 0 0 0 648 140 0 0 0 0 0 0
   7     128 loop16 610 4 41400 355 0 0 0 0 0 772 355 0 0 0 0 0 0
   7     136 loop17 33 0 3224 15 0 0 0 0 0 64 15 0 0 0 0 0 0
   7     144 loop18 48 0 3400 69 0 0 0 0 0 148 69 0 0 0 0 0 0
   7     152 loop19 7 17 192 10 0 0 0 0 0 24 10 0 0 0 0 0 0
   7     160 loop20 271 1 37608 265 0 0 0 0 0 440 265 0 0 0 0 0 0
   7     168 loop21 33 0 2440 30 0 0 0 0 0 84 30 0 0 0 0 0 0
   7     176 loop22 192 3 44088 289 0 0 0 0 0 300 289 0 0 0 0 0 0
   7     184 loop23 503 17 50848 596 0 0 0 0 0 684 596 0 0 0 0 0 0
   7     192 loop24 99 2 10456 53 0 0 0 0 0 156 53 0 0 0 0 0 0
   7     200 loop25 27 0 1072 35 0 0 0 0 0 84 35 0 0 0 0 0 0
   7     208 loop26 17 0 224 24 0 0 0 0 0 56 24 0 0 0 0 0 0
   7     216 loop27 4709 2 72024 4690 0 0 0 0 0 384 4690 0 0 0 0 0 0
   7     224 loop28 24 0 1144 7 0 0 0 0 0 44 7 0 0 0 0 0 0
   7     232 loop29 83 0 9056 101 0 0 0 0 0 236 101 0 0 0 0 0 0
   7     240 loop30 137 2 12112 72 0 0 0 0 0 160 72 0 0 0 0 0 0
   7     248 loop31 104 1 12184 84 0 0 0 0 0 136 84 0 0 0 0 0 0
   7     256 loop32 110 11 12144 59 0 0 0 0 0 144 59 0 0 0 0 0 0
   7     264 loop33 130 2 18128 68 0 0 0 0 0 156 68 0 0 0 0 0 0
   7     272 loop34 550 7 69456 584 0 0 0 0 0 924 584 0 0 0 0 0 0
   7     280 loop35 1151 29 136160 1187 0 0 0 0 0 1040 1187 0 0 0 0 0 0
   7     288 loop36 643 2 107792 632 0 0 0 0 0 724 632 0 0 0 0 0 0
   7     296 loop37 9 0 72 4 0 0 0 0 0 12 4 0 0 0 0 0 0
   7     304 loop38 28 0 1696 9 0 0 0 0 0 64 9 0 0 0 0 0 0
   7     312 loop39 66 1 9448 41 0 0 0 0 0 120 41 0 0 0 0 0 0
   7     320 loop40 217 4 18376 99 0 0 0 0 0 360 99 0 0 0 0 0 0
 254       8 dm-8 57 0 9368 44 0 0 0 0 0 116 44 0 0 0 0 0 0
 254       9 dm-9 24 0 1664 12 0 0 0 0 0 60 12 0 0 0 0 0 0
 254      10 dm-10 195 0 18168 112 0 0 0 0 0 364 112 0 0 0 0 0 0
 254      11 dm-11 1132 0 135776 1368 0 0 0 0 0 1064 1368 0 0 0 0 0 0
 254      13 dm-13 476 0 68808 612 0 0 0 0 0 932 612 0 0 0 0 0 0
 254      14 dm-14 106 0 12024 68 0 0 0 0 0 140 68 0 0 0 0 0 0
 254      20 dm-20 112 0 17968 48 0 0 0 0 0 152 48 0 0 0 0 0 0
 254      21 dm-21 94 0 12096 112 0 0 0 0 0 132 112 0 0 0 0 0 0
 254      22 dm-22 607 0 107488 712 0 0 0 0 0 724 712 0 0 0 0 0 0
 254      23 dm-23 5 0 40 4 0 0 0 0 0 8 4 0 0 0 0 0 0
 254      26 dm-26 125 0 12000 88 0 0 0 0 0 160 88 0 0 0 0 0 0
 254      29 dm-29 72 0 8968 136 0 0 0 0 0 244 136 0 0 0 0 0 0
 254      30 dm-30 20 0 1112 0 0 0 0 0 0 40 0 0 0 0 0 0 0
 254      35 dm-35 4672 0 71712 5048 0 0 0 0 0 380 5048 0 0 0 0 0 0
 254      37 dm-37 15 0 208 20 0 0 0 0 0 52 20 0 0 0 0 0 0
 254      38 dm-38 23 0 1040 32 0 0 0 0 0 76 32 0 0 0 0 0 0
 254      39 dm-39 470 0 50448 808 0 0 0 0 0 888 808 0 0 0 0 0 0
 254      40 dm-40 83 0 10312 56 0 0 0 0 0 156 56 0 0 0 0 0 0
 254      41 dm-41 151 0 43736 296 0 0 0 0 0 296 296 0 0 0 0 0 0
 254      42 dm-42 27 0 2392 52 0 0 0 0 0 80 52 0 0 0 0 0 0
 254      43 dm-43 246 0 37400 392 0 0 0 0 0 496 392 0 0 0 0 0 0
 253       0 zram0 166152 0 1329216 1264 323309 0 2586472 5980 0 32084 7244 0 0 0 0 0 0
 254      44 dm-44 56001 0 1038272 367892 20036 0 1700176 35812 0 42296 409696 1088 0 1785328 5992 0 0"""

        const val trickyOutput = realWorldOutput + "\n" + """
 253       0 mmcblk0 166152 0 1329216 1264 323309 0 2586472 5980 0 32084 7244 0 0 0 0 0 0
 253       0 mmcblk0p1 166152 0 1329216 1264 323309 0 2586472 5980 0 32084 7244 0 0 0 0 0 0
 253       0 mmcblk1 166152 0 1329216 1264 323309 0 2586472 5980 0 32084 7244 0 0 0 0 0 0
 253       0 mmcblk1p1 166152 0 1329216 1264 323309 0 2586472 5980 0 32084 7244 0 0 0 0 0 0
 253       0 mmcblk1p2 166152 0 1329216 1264 323309 0 2586472 5980 0 32084 7244 0 0 0 0 0 0
        """
    }
}

private fun String.inTempFile(): File =
    File.createTempFile("DiskActivityParserTest-Stats", ".txt").apply {
        deleteOnExit()
        writeText(this@inTempFile)
    }
