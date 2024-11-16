package com.memfault.bort.parsers

import com.memfault.bort.shared.LogcatCommand
import com.memfault.bort.shared.LogcatFormat
import com.memfault.bort.shared.LogcatFormatModifier
import com.memfault.bort.shared.LogcatPriority.ERROR
import com.memfault.bort.shared.LogcatPriority.INFO
import com.memfault.bort.shared.LogcatPriority.WARN
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class LogcatParserTest {
    @Test
    fun lastLineOk() = runTest {
        assertEquals(
            LogcatLine(
                logTime = Instant.ofEpochSecond(1610973242, 168273087),
                uid = 9008,
                lineUpToTag = "2021-01-18 12:34:02.168273087 +0000  9008  9009 I ServiceManager: ",
                message = "Waiting for service AtCmdFwd...",
                priority = INFO,
                tag = "ServiceManager",
            ),
            LogcatParser(
                flowOf(
                    "2021-01-18 12:33:17.718860224 +0000     0     0 I chatty  : uid=0(root) logd identical 11 lines",
                    "2021-01-18 12:34:02.168273087 +0000  9008  9009 I ServiceManager: Waiting for service AtCmdFwd...",
                ),
                COMMAND,
                ::dummyUidParser,
            ).parse().toList().last(),
        )
    }

    @Test
    fun uidDecoding() = runTest {
        assertEquals(
            listOf(0, 2000, null, 1001),
            LogcatParser(
                flowOf(
                    "2021-01-18 12:33:17.718860224 +0000  root      0 I chatty  : uid=0(root) logd identical 11 lines",
                    "2021-01-18 12:34:02.168273087 +0000  shell  9009 I ServiceManager: Waiting ...",
                    "2021-01-18 12:34:02.168273087 +0000  unknown  9009 I ServiceManager: Waiting ...",
                    "2021-01-18 12:34:02.168273087 +0000  radio  9009 I ServiceManager: Waiting ...",
                ),
                COMMAND,
                ::dummyUidParser,
            ).parse().toList().map { it.uid }.toList(),
        )
    }

    @Test
    fun testSeparators() = runTest {
        assertEquals(
            /* expected = */
            listOf(
                LogcatLine(
                    logTime = Instant.ofEpochSecond(1610973197, 718860224),
                    uid = 0,
                    lineUpToTag = "2021-01-18 12:33:17.718860224 +0000  root     0 I chatty  : ",
                    message = "uid=0(root) logd identical 11 lines",
                    priority = INFO,
                    tag = "chatty",
                ),
                LogcatLine(
                    logTime = null,
                    uid = null,
                    lineUpToTag = null,
                    message = "--------- beginning of kernel",
                    buffer = "kernel",
                ),
                LogcatLine(
                    logTime = Instant.ofEpochSecond(1610973242, 168273087),
                    uid = 9008,
                    lineUpToTag = "2021-01-18 12:34:02.168273087 +0000  9008  9009 I ServiceManager: ",
                    message = "Waiting for service AtCmdFwd...",
                    buffer = "kernel",
                    priority = INFO,
                    tag = "ServiceManager",
                ),
                LogcatLine(
                    logTime = null,
                    uid = null,
                    lineUpToTag = null,
                    message = "--------- switch to main",
                    buffer = "main",
                ),
                LogcatLine(
                    logTime = Instant.ofEpochSecond(1610973313, 22471886),
                    uid = 0,
                    lineUpToTag = "2021-01-18 12:35:13.022471886 +0000  root     0 E foo  : ",
                    message = "bar",
                    buffer = "main",
                    priority = ERROR,
                    tag = "foo",
                ),
            ),
            /* actual = */
            LogcatParser(
                flowOf(
                    "2021-01-18 12:33:17.718860224 +0000  root     0 I chatty  : uid=0(root) logd identical 11 lines",
                    "--------- beginning of kernel",
                    "2021-01-18 12:34:02.168273087 +0000  9008  9009 I ServiceManager: Waiting for service AtCmdFwd...",
                    "--------- switch to main",
                    "2021-01-18 12:35:13.022471886 +0000  root     0 E foo  : bar",
                ),
                COMMAND,
                ::dummyUidParser,
            ).parse().toList(),
        )
    }

    @Test
    fun testLineWithPidTid() = runTest {
        assertEquals(
            listOf(
                LogcatLine(
                    logTime = Instant.ofEpochSecond(1610973242, 168273087),
                    uid = 2000,
                    lineUpToTag = "2021-01-18 12:34:02.168273087 +0000 shell 9008  9009 I ServiceManager: ",
                    message = "Waiting...",
                    priority = INFO,
                    tag = "ServiceManager",
                ),
            ),
            LogcatParser(
                flowOf(
                    "2021-01-18 12:34:02.168273087 +0000 shell 9008  9009 I ServiceManager: Waiting...",
                ),
                COMMAND,
                ::dummyUidParser,
            ).parse().toList(),
        )
    }

    @Test
    fun lastLineNotOk() = runTest {
        assertEquals(
            listOf(LogcatLine(message = "01-18 12:34:02.168273087  9008  9008 I ServiceManager: Waiting...")),
            LogcatParser(
                flowOf("01-18 12:34:02.168273087  9008  9008 I ServiceManager: Waiting..."),
                COMMAND,
                ::dummyUidParser,
            ).parse().toList(),
        )
    }

    @Test
    fun emptyInput() = runTest {
        assertEquals(
            listOf<LogcatLine>(),
            LogcatParser(flowOf(), COMMAND, ::dummyUidParser).parse().toList(),
        )
    }

    @Test
    fun kernelOops() = runTest {
        assertEquals(
            LogcatLine(
                logTime = Instant.ofEpochSecond(1727314523, 361577378),
                uid = 0,
                lineUpToTag = "2024-09-26 01:35:23.361577378 +0000  root     0     0 W         : ",
                message = "------------[ cut here ]------------",
                priority = WARN,
                tag = null,
                buffer = "kernel",
            ),
            LogcatParser(
                flowOf(
                    "--------- beginning of kernel",
                    "2024-09-26 01:35:23.361577378 +0000  root     0     0 W         : ----" +
                        "--------[ cut here ]------------",
                ),
                COMMAND,
                ::dummyUidParser,
            ).parse().toList().last(),
        )
    }

    @Test
    fun tagWithHyphen() = runTest {
        assertEquals(
            LogcatLine(
                logTime = Instant.ofEpochSecond(1727321513, 926404779),
                uid = 0,
                lineUpToTag = "2024-09-26 03:31:53.926404779 +0000  root 12635 12635 E fake-e2e-tag: ",
                message = "Fake E2E log with error: 3",
                priority = ERROR,
                tag = "fake-e2e-tag",
            ),
            LogcatParser(
                flowOf(
                    "2024-09-26 03:31:53.926404779 +0000  root 12635 12635 E fake-e2e-tag: Fake E2E log with error: 3",
                ),
                COMMAND,
                ::dummyUidParser,
            ).parse().toList().last(),
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
