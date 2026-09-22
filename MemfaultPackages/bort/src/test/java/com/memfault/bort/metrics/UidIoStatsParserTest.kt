package com.memfault.bort.metrics

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test
import java.io.File

class UidIoStatsParserTest {
    private val parser = UidIoStatsParser { "boot-id-1" }

    @Test fun `parses write bytes for a known uid`() {
        // 10045 2043 229 0 0 137059 257945 2945024 294912 0 34
        val stats = parser.parse(uid = 10045, SAMPLE_OUTPUT.inTempFile())

        assertThat(stats.bootId).isEqualTo("boot-id-1")
        assertThat(stats.writtenBytes).isEqualTo(0L + 294912L) // fg_write_bytes + bg_write_bytes
    }

    @Test fun `returns zero written bytes when uid is not in file`() {
        val stats = parser.parse(uid = 99999, SAMPLE_OUTPUT.inTempFile())

        assertThat(stats.writtenBytes).isEqualTo(0L)
    }

    @Test fun `returns empty when file does not exist`() {
        val stats = parser.parse(uid = 10045, File("/does/not/exist"))

        assertThat(stats).isEqualTo(UidIoStats.EMPTY)
    }

    @Test fun `parses writes for every uid in the file`() {
        val stats = parser.parse(uid = 10045, SAMPLE_OUTPUT.inTempFile())

        assertThat(stats.writesByUid).isEqualTo(
            mapOf(
                0 to UidWrites(writeBytes = 344231936, logicalWriteBytes = 332210064),
                1021 to UidWrites(writeBytes = 0, logicalWriteBytes = 3587),
                10019 to UidWrites(writeBytes = 0, logicalWriteBytes = 382 + 8488),
                1000 to UidWrites(writeBytes = 2625536, logicalWriteBytes = 5624724),
                10011 to UidWrites(writeBytes = 0, logicalWriteBytes = 406),
                10045 to UidWrites(writeBytes = 294912, logicalWriteBytes = 229 + 257945),
            ),
        )
    }

    @Test fun `skips malformed lines`() {
        val stats = parser.parse(
            uid = 1000,
            file = """
                not a valid line
                10045 2043 229 0 0 137059 257945 2945024 294912 0 34
                1000 100 200 300 400 500 600 700 800 0 0
            """.trimIndent().inTempFile(),
        )
        // fg_write_bytes=400, bg_write_bytes=800
        assertThat(stats.writtenBytes).isEqualTo(400L + 800L)
        assertThat(stats.writesByUid.keys).isEqualTo(setOf(10045, 1000))
    }

    @Test fun `skips lines with too few columns to hold the background counters`() {
        val stats = parser.parse(
            uid = 1000,
            file = "1000 111 222 333 444 555 666 777".inTempFile(),
        )

        assertThat(stats).isEqualTo(UidIoStats(bootId = "boot-id-1", writtenBytes = 0))
    }

    @Test fun `skips lines with more columns than the known layout`() {
        val stats = parser.parse(
            uid = 1000,
            file = "1000 111 222 333 444 555 666 777 888 5 6 999".inTempFile(),
        )

        assertThat(stats).isEqualTo(UidIoStats(bootId = "boot-id-1", writtenBytes = 0))
    }

    @Test fun `skips lines with the right column count but a non-numeric uid`() {
        val stats = parser.parse(
            uid = 1000,
            file = """
                1000 111 222 333 444 555 666 777 888 5 6
                notauid 1 111 222 333 444 555 666 777 888 5
            """.trimIndent().inTempFile(),
        )

        assertThat(stats.writtenBytes).isEqualTo(1332L)
        assertThat(stats.writesByUid.keys).isEqualTo(setOf(1000))
    }

    @Test fun `sums foreground and background write bytes`() {
        val stats = parser.parse(
            uid = 1000,
            file = "1000 111 222 333 444 555 666 777 888 5 6".inTempFile(),
        )
        // fg_write_bytes = 444, bg_write_bytes = 888
        assertThat(stats.writtenBytes).isEqualTo(1332L)
    }

    @Test fun `sums foreground and background wchar as logical write bytes`() {
        val stats = parser.parse(
            uid = 1000,
            file = "1000 111 222 333 444 555 666 777 888 5 6".inTempFile(),
        )
        // fg_wchar = 222, bg_wchar = 666
        assertThat(stats.writesByUid[1000]?.logicalWriteBytes).isEqualTo(888L)
    }

    @Test fun `uid with zero write bytes is returned as zero`() {
        val stats = parser.parse(uid = 10011, SAMPLE_OUTPUT.inTempFile())
        // 10011 0 0 0 0 14836 406 0 0 0 0 -> fg_write_bytes=0 bg_write_bytes=0
        assertThat(stats.writtenBytes).isEqualTo(0L)
    }

    companion object {
        // Trimmed excerpt from a real /proc/uid_io/stats
        const val SAMPLE_OUTPUT = """0 321445721 332210064 481840128 344231936 0 0 0 0 57 0
1021 293731 3587 2146304 0 0 0 0 0 0 0
10019 13052 382 0 0 0 8488 0 0 0 0
1000 129193692 5624724 432943104 2625536 0 0 0 0 267 0
10011 0 0 0 0 14836 406 0 0 0 0
10045 2043 229 0 0 137059 257945 2945024 294912 0 34"""
    }
}

private fun String.inTempFile(): File =
    File.createTempFile("UidIoStatsParserTest", ".txt").apply {
        deleteOnExit()
        writeText(this@inTempFile)
    }
