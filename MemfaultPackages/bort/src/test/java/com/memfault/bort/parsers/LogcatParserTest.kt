package com.memfault.bort.parsers

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import com.memfault.bort.shared.LogcatCommand
import com.memfault.bort.shared.LogcatFormat
import com.memfault.bort.shared.LogcatFormatModifier
import com.memfault.bort.shared.LogcatPriority.ERROR
import com.memfault.bort.shared.LogcatPriority.INFO
import com.memfault.bort.shared.LogcatPriority.WARN
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

class LogcatParserTest {
    @Test
    fun lastLineOk() = runTest {
        assertThat(
            LogcatParser(
                flowOf(
                    "2021-01-18 12:33:17.718860224 +0000     0     0 I chatty  : uid=0(root) logd identical 11 lines",
                    "2021-01-18 12:34:02.168273087 +0000  9008  9009 I ServiceManager: Waiting for service AtCmdFwd...",
                ),
                COMMAND,
                ::dummyUidParser,
            ).parse().toList().last(),
        ).isEqualTo(
            LogcatLine(
                logTime = Instant.ofEpochSecond(1610973242, 168273087),
                uid = 9008,
                lineUpToTag = "2021-01-18 12:34:02.168273087 +0000  9008  9009 I ServiceManager: ",
                message = "Waiting for service AtCmdFwd...",
                priority = INFO,
                tag = "ServiceManager",
                separator = false,
            ),
        )
    }

    @Test
    fun uidDecoding() = runTest {
        assertThat(
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
        ).isEqualTo(
            listOf(0, 2000, null, 1001),
        )
    }

    @Test
    fun testSeparators() = runTest {
        assertThat(
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
        ).isEqualTo(
            listOf(
                LogcatLine(
                    logTime = Instant.ofEpochSecond(1610973197, 718860224),
                    uid = 0,
                    lineUpToTag = "2021-01-18 12:33:17.718860224 +0000  root     0 I chatty  : ",
                    message = "uid=0(root) logd identical 11 lines",
                    priority = INFO,
                    tag = "chatty",
                    separator = false,
                ),
                LogcatLine(
                    logTime = null,
                    uid = null,
                    lineUpToTag = null,
                    message = "--------- beginning of kernel",
                    buffer = "kernel",
                    separator = true,
                ),
                LogcatLine(
                    logTime = Instant.ofEpochSecond(1610973242, 168273087),
                    uid = 9008,
                    lineUpToTag = "2021-01-18 12:34:02.168273087 +0000  9008  9009 I ServiceManager: ",
                    message = "Waiting for service AtCmdFwd...",
                    buffer = "kernel",
                    priority = INFO,
                    tag = "ServiceManager",
                    separator = false,
                ),
                LogcatLine(
                    logTime = null,
                    uid = null,
                    lineUpToTag = null,
                    message = "--------- switch to main",
                    buffer = "main",
                    separator = true,
                ),
                LogcatLine(
                    logTime = Instant.ofEpochSecond(1610973313, 22471886),
                    uid = 0,
                    lineUpToTag = "2021-01-18 12:35:13.022471886 +0000  root     0 E foo  : ",
                    message = "bar",
                    buffer = "main",
                    priority = ERROR,
                    tag = "foo",
                    separator = false,
                ),
            ),
        )
    }

    @Test
    fun coalescesAfterNonSeparator() = runTest {
        assertThat(
            LogcatParser(
                flowOf(
                    "2021-01-18 12:33:17.718860224 +0000  root     0 I chatty  : uid=0(root) logd identical 11 lines",
                    "--------- beginning of kernel",
                    "--------- switch to main",
                    "2021-01-18 12:35:13.022471886 +0000  root     0 E foo  : bar",
                    "--------- switch to kernel",
                    "--------- switch to radio",
                ),
                COMMAND,
                ::dummyUidParser,
            ).parse().toList(),
        ).containsExactly(
            LogcatLine(
                logTime = Instant.ofEpochSecond(1610973197, 718860224),
                uid = 0,
                lineUpToTag = "2021-01-18 12:33:17.718860224 +0000  root     0 I chatty  : ",
                message = "uid=0(root) logd identical 11 lines",
                priority = INFO,
                tag = "chatty",
                separator = false,
            ),
            LogcatLine(
                logTime = null,
                uid = null,
                lineUpToTag = null,
                message = "--------- switch to main",
                buffer = "main",
                separator = true,
            ),
            LogcatLine(
                logTime = Instant.ofEpochSecond(1610973313, 22471886),
                uid = 0,
                lineUpToTag = "2021-01-18 12:35:13.022471886 +0000  root     0 E foo  : ",
                message = "bar",
                buffer = "main",
                priority = ERROR,
                tag = "foo",
                separator = false,
            ),
            LogcatLine(
                logTime = null,
                uid = null,
                lineUpToTag = null,
                message = "--------- switch to radio",
                buffer = "radio",
                separator = true,
            ),
        )
    }

    @Test
    fun testLineWithPidTid() = runTest {
        assertThat(
            LogcatParser(
                flowOf(
                    "2021-01-18 12:34:02.168273087 +0000 shell 9008  9009 I ServiceManager: Waiting...",
                ),
                COMMAND,
                ::dummyUidParser,
            ).parse().toList(),
        ).isEqualTo(
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
        )
    }

    @Test
    fun lastLineNotOk() = runTest {
        assertThat(

            LogcatParser(
                flowOf("01-18 12:34:02.168273087  9008  9008 I ServiceManager: Waiting..."),
                COMMAND,
                ::dummyUidParser,
            ).parse().toList(),
        ).isEqualTo(
            listOf(LogcatLine(message = "01-18 12:34:02.168273087  9008  9008 I ServiceManager: Waiting...")),
        )
    }

    @Test
    fun emptyInput() = runTest {
        assertThat(

            LogcatParser(flowOf(), COMMAND, ::dummyUidParser).parse().toList(),
        ).isEqualTo(
            listOf<LogcatLine>(),
        )
    }

    @Test
    fun kernelOops() = runTest {
        assertThat(

            LogcatParser(
                flowOf(
                    "--------- beginning of kernel",
                    "2024-09-26 01:35:23.361577378 +0000  root     0     0 W         : ----" +
                        "--------[ cut here ]------------",
                ),
                COMMAND,
                ::dummyUidParser,
            ).parse().toList().last(),
        ).isEqualTo(
            LogcatLine(
                logTime = Instant.ofEpochSecond(1727314523, 361577378),
                uid = 0,
                lineUpToTag = "2024-09-26 01:35:23.361577378 +0000  root     0     0 W         : ",
                message = "------------[ cut here ]------------",
                priority = WARN,
                tag = null,
                buffer = "kernel",
                separator = false,
            ),
        )
    }

    @Test
    fun tagWithHyphen() = runTest {
        assertThat(
            LogcatParser(
                flowOf(
                    "2024-09-26 03:31:53.926404779 +0000  root 12635 12635 E fake-e2e-tag: Fake E2E log with error: 3",
                ),
                COMMAND,
                ::dummyUidParser,
            ).parse().toList().last(),
        ).isEqualTo(

            LogcatLine(
                logTime = Instant.ofEpochSecond(1727321513, 926404779),
                uid = 0,
                lineUpToTag = "2024-09-26 03:31:53.926404779 +0000  root 12635 12635 E fake-e2e-tag: ",
                message = "Fake E2E log with error: 3",
                priority = ERROR,
                tag = "fake-e2e-tag",
            ),
        )
    }

    @Test
    fun tagWithSpace() = runTest {
        assertThat(
            LogcatParser(
                flowOf(
                    "2024-09-26 03:31:53.926404779 +0000  root 12635 12635 E atmel_mxt_ts 4-004b: tag has a: space",
                ),
                COMMAND,
                ::dummyUidParser,
            ).parse().toList().last(),
        ).isEqualTo(

            LogcatLine(
                logTime = Instant.ofEpochSecond(1727321513, 926404779),
                uid = 0,
                lineUpToTag = "2024-09-26 03:31:53.926404779 +0000  root 12635 12635 E atmel_mxt_ts 4-004b: ",
                message = "tag has a: space",
                priority = ERROR,
                tag = "atmel_mxt_ts 4-004b",
            ),
        )
    }

    @Test
    fun moreWeirdLogcat() = runTest {
        assertThat(
            LogcatParser(
                flowOf(
                    "2024-09-26 03:31:53.926404779 +0000  root     0     0 I PMIC@SID2: PM660L v2.0.1 options: 0, 0, 0, 0",
                    "2024-09-26 03:31:53.926404779 +0000  root     0     0 I         : Advanced Linux Sound Architecture Driver Initialized.",
                    "2024-09-26 03:31:53.926404779 +0000  root     0     0 I CPU features: enabling workaround for Kryo2xx Silver erratum 845719",
                    "2024-09-26 03:31:53.926404779 +0000  root     0     0 I log_buf_len: 2097152 bytes",
                    "2024-09-26 03:31:53.926404779 +0000  root     0     0 I arch_timer: CPU7: Trapping CNTVCT access",
                    "2024-09-26 03:31:53.926404779 +0000  root     0     0 E i2c-msm-v2 c1b8000.i2c: error probe() failed with err:-517",
                ),
                COMMAND,
                ::dummyUidParser,
            ).parse().toList(),
        ).containsExactly(
            LogcatLine(
                logTime = Instant.ofEpochSecond(1727321513, 926404779),
                uid = 0,
                lineUpToTag = "2024-09-26 03:31:53.926404779 +0000  root     0     0 I PMIC@SID2: ",
                message = "PM660L v2.0.1 options: 0, 0, 0, 0",
                priority = INFO,
                tag = "PMIC@SID2",
            ),
            LogcatLine(
                logTime = Instant.ofEpochSecond(1727321513, 926404779),
                uid = 0,
                lineUpToTag = "2024-09-26 03:31:53.926404779 +0000  root     0     0 I         : ",
                message = "Advanced Linux Sound Architecture Driver Initialized.",
                priority = INFO,
                tag = null,
            ),
            LogcatLine(
                logTime = Instant.ofEpochSecond(1727321513, 926404779),
                uid = 0,
                lineUpToTag = "2024-09-26 03:31:53.926404779 +0000  root     0     0 I CPU features: ",
                message = "enabling workaround for Kryo2xx Silver erratum 845719",
                priority = INFO,
                tag = "CPU features",
            ),
            LogcatLine(
                logTime = Instant.ofEpochSecond(1727321513, 926404779),
                uid = 0,
                lineUpToTag = "2024-09-26 03:31:53.926404779 +0000  root     0     0 I log_buf_len: ",
                message = "2097152 bytes",
                priority = INFO,
                tag = "log_buf_len",
            ),
            LogcatLine(
                logTime = Instant.ofEpochSecond(1727321513, 926404779),
                uid = 0,
                lineUpToTag = "2024-09-26 03:31:53.926404779 +0000  root     0     0 I arch_timer: ",
                message = "CPU7: Trapping CNTVCT access",
                priority = INFO,
                tag = "arch_timer",
            ),
            LogcatLine(
                logTime = Instant.ofEpochSecond(1727321513, 926404779),
                uid = 0,
                lineUpToTag = "2024-09-26 03:31:53.926404779 +0000  root     0     0 E i2c-msm-v2 c1b8000.i2c: ",
                message = "error probe() failed with err:-517",
                priority = ERROR,
                tag = "i2c-msm-v2 c1b8000.i2c",
            ),
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
