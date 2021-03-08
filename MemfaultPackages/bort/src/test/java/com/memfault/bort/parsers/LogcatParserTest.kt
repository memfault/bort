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
            LogcatLine(
                Instant.ofEpochSecond(1610973242, 168273087),
                9008,
                "2021-01-18 12:34:02.168273087 +0000  9008  9009 I ServiceManager: ",
                "Waiting for service AtCmdFwd...",
            ),
            LogcatParser(
                sequenceOf(
                    "2021-01-18 12:33:17.718860224 +0000     0     0 I chatty  : uid=0(root) logd identical 11 lines",
                    "2021-01-18 12:34:02.168273087 +0000  9008  9009 I ServiceManager: Waiting for service AtCmdFwd...",
                ),
                COMMAND,
                ::dummyUidParser
            ).parse().asIterable().last()
        )
    }

    @Test
    fun uidDecoding() {
        assertEquals(
            listOf(0, 2000, null, 1001),
            LogcatParser(
                sequenceOf(
                    "2021-01-18 12:33:17.718860224 +0000  root      0 I chatty  : uid=0(root) logd identical 11 lines",
                    "2021-01-18 12:34:02.168273087 +0000  shell  9009 I ServiceManager: Waiting ...",
                    "2021-01-18 12:34:02.168273087 +0000  unknown  9009 I ServiceManager: Waiting ...",
                    "2021-01-18 12:34:02.168273087 +0000  radio  9009 I ServiceManager: Waiting ...",
                ),
                COMMAND,
                ::dummyUidParser
            ).parse().map { it.uid }.toList()
        )
    }

    @Test
    fun testSeparators() {
        assertEquals(
            listOf(
                LogcatLine(
                    Instant.ofEpochSecond(1610973197, 718860224),
                    0,
                    "2021-01-18 12:33:17.718860224 +0000  root     0 I chatty  : ",
                    "uid=0(root) logd identical 11 lines"
                ),
                LogcatLine(null, null, null, "--------- beginning of kernel"),
                LogcatLine(
                    Instant.ofEpochSecond(1610973242, 168273087),
                    9008,
                    "2021-01-18 12:34:02.168273087 +0000  9008  9009 I ServiceManager: ",
                    "Waiting for service AtCmdFwd...",
                ),
            ),
            LogcatParser(
                sequenceOf(
                    "2021-01-18 12:33:17.718860224 +0000  root     0 I chatty  : uid=0(root) logd identical 11 lines",
                    "--------- beginning of kernel",
                    "2021-01-18 12:34:02.168273087 +0000  9008  9009 I ServiceManager: Waiting for service AtCmdFwd...",
                ),
                COMMAND,
                ::dummyUidParser
            ).parse().toList()
        )
    }

    @Test
    fun testLineWithPidTid() {
        assertEquals(
            listOf(
                LogcatLine(
                    Instant.ofEpochSecond(1610973242, 168273087),
                    2000,
                    "2021-01-18 12:34:02.168273087 +0000 shell 9008  9009 I ServiceManager: ",
                    "Waiting...",
                ),
            ),
            LogcatParser(
                sequenceOf(
                    "2021-01-18 12:34:02.168273087 +0000 shell 9008  9009 I ServiceManager: Waiting...",
                ),
                COMMAND,
                ::dummyUidParser
            ).parse().toList()
        )
    }

    @Test
    fun lastLineNotOk() {
        assertEquals(
            listOf(LogcatLine(message = "01-18 12:34:02.168273087  9008  9008 I ServiceManager: Waiting...")),
            LogcatParser(
                sequenceOf("01-18 12:34:02.168273087  9008  9008 I ServiceManager: Waiting..."),
                COMMAND,
                ::dummyUidParser
            ).parse().toList()
        )
    }

    @Test
    fun emptyInput() {
        assertEquals(
            listOf<LogcatLine>(),
            LogcatParser(sequenceOf(), COMMAND, ::dummyUidParser).parse().toList()
        )
    }
}

private val COMMAND = LogcatCommand(
    format = LogcatFormat.THREADTIME,
    formatModifiers = listOf(
        LogcatFormatModifier.NSEC,
        LogcatFormatModifier.UTC,
        LogcatFormatModifier.YEAR,
        LogcatFormatModifier.UID,
    ),
)

// On real devices we use android.os.Process.getUidForName but here we'll use
//  a dummy lookup table
private fun dummyUidParser(uid: String) = when (uid) {
    "root" -> 0
    "radio" -> 1001
    "shell" -> 2000
    else -> -1
}
