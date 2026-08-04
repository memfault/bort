package com.memfault.bort.metrics

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test
import java.io.File

class UidIoStatsParserTest {
    private val parser = UidIoStatsParser { "boot-id-1" }

    @Test fun `parses write bytes for a known uid`() {
        // Format: uid fg_read_bytes fg_write_bytes bg_read_bytes bg_write_bytes fg_rchar fg_wchar bg_rchar bg_wchar fg_fsync bg_fsync
        // 10045 2043 229 0 0 137059 257945 2945024 294912 0 34
        val stats = parser.parse(uid = 10045, SAMPLE_OUTPUT.inTempFile())

        assertThat(stats).isEqualTo(
            UidIoStats(
                bootId = "boot-id-1",
                writtenBytes = 229L + 0L, // fg_write_bytes + bg_write_bytes
            ),
        )
    }

    @Test fun `returns zero written bytes when uid is not in file`() {
        val stats = parser.parse(uid = 99999, SAMPLE_OUTPUT.inTempFile())

        assertThat(stats).isEqualTo(UidIoStats(bootId = "boot-id-1", writtenBytes = 0))
    }

    @Test fun `returns empty when file does not exist`() {
        val stats = parser.parse(uid = 10045, File("/does/not/exist"))

        assertThat(stats).isEqualTo(UidIoStats.EMPTY)
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
        // fg_write_bytes=200, bg_write_bytes=400
        assertThat(stats).isEqualTo(
            UidIoStats(bootId = "boot-id-1", writtenBytes = 200L + 400L),
        )
    }

    @Test fun `sums foreground and background write bytes`() {
        val stats = parser.parse(
            uid = 1000,
            file = "1000 111 222 333 444 555 666 777 888 5 6".inTempFile(),
        )
        // fg_write_bytes = 222, bg_write_bytes = 444
        assertThat(stats.writtenBytes).isEqualTo(666L)
    }

    @Test fun `uid with zero write bytes is returned as zero`() {
        val stats = parser.parse(uid = 10011, SAMPLE_OUTPUT.inTempFile())
        // 10011 0 0 0 0 14836 406 0 0 0 0 → fg_write_bytes=0 bg_write_bytes=0
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
