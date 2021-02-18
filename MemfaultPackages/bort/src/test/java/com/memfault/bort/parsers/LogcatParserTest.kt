package com.memfault.bort.parsers

import com.memfault.bort.shared.LogcatCommand
import com.memfault.bort.shared.LogcatFormat
import com.memfault.bort.shared.LogcatFormatModifier
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LogcatParserTest {
    @Test
    fun lastLineOk() {
        assertEquals(
            LogcatReport(lastLogTime = Instant.ofEpochSecond(1610973242, 168273087)),
            LogcatParser(
                """
                2021-01-18 12:33:17.718860224 +0000     0     0 I chatty  : uid=0(root) logd identical 11 lines
                2021-01-18 12:34:02.168273087 +0000  9008  9008 I ServiceManager: Waiting for service AtCmdFwd...
                """.trimIndent().byteInputStream(),
                COMMAND,
            ).parse()
        )
    }

    @Test
    fun lastLineNotOk() {
        assertEquals(
            LogcatReport(lastLogTime = null),
            LogcatParser(
                """01-18 12:34:02.168273087  9008  9008 I ServiceManager: Waiting...""".byteInputStream(), COMMAND,
            ).parse()
        )
    }

    @Test
    fun emptyInput() {
        assertEquals(
            LogcatReport(lastLogTime = null),
            LogcatParser("".byteInputStream(), COMMAND).parse()
        )
    }
}

private val COMMAND = LogcatCommand(
    format = LogcatFormat.THREADTIME,
    formatModifiers = listOf(
        LogcatFormatModifier.NSEC,
        LogcatFormatModifier.UTC,
        LogcatFormatModifier.YEAR,
    ),
)
