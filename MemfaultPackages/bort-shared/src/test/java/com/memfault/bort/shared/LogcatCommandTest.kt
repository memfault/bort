package com.memfault.bort.shared

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class LogcatCommandTest {
    @Test
    fun filterSpecs() {
        assertThat(
            LogcatCommand(
                filterSpecs = listOf(
                    LogcatFilterSpec(tag = Logger.getTag(), priority = LogcatPriority.DEBUG),
                    LogcatFilterSpec(priority = LogcatPriority.ERROR),
                ),
            ).toList(),
        ).isEqualTo(
            listOf("logcat", "-d", "TAG:D", "*:E"),
        )
    }

    @Test
    fun format() {
        assertThat(
            LogcatCommand(format = LogcatFormat.BRIEF).toList(),
        ).isEqualTo(
            listOf("logcat", "-d", "-v", "brief"),
        )
    }

    @Test
    fun formatModifiers() {
        assertThat(
            LogcatCommand(
                formatModifiers = listOf(
                    LogcatFormatModifier.COLOR,
                    LogcatFormatModifier.DESCRIPTIVE,
                    LogcatFormatModifier.EPOCH,
                    LogcatFormatModifier.MONOTONIC,
                    LogcatFormatModifier.PRINTABLE,
                    LogcatFormatModifier.UID,
                    LogcatFormatModifier.USEC,
                    LogcatFormatModifier.UTC,
                    LogcatFormatModifier.YEAR,
                    LogcatFormatModifier.ZONE,
                    LogcatFormatModifier.NSEC,
                ),
            ).toList(),
        ).isEqualTo(
            listOf(
                "logcat", "-d",
                "-v", "color",
                "-v", "descriptive",
                "-v", "epoch",
                "-v", "monotonic",
                "-v", "printable",
                "-v", "uid",
                "-v", "usec",
                "-v", "UTC",
                "-v", "year",
                "-v", "zone",
                "-v", "nsec",
            ),
        )
    }

    @Test
    fun dividers() {
        assertThat(

            LogcatCommand(dividers = true).toList(),
        ).containsExactly("logcat", "-D", "-d")
    }

    @Test
    fun clear() {
        assertThat(

            LogcatCommand(clear = true).toList(),
        ).containsExactly("logcat", "-c", "-d")
    }

    @Test
    fun maxCount() {
        assertThat(

            LogcatCommand(maxCount = 4).toList(),
        ).containsExactly("logcat", "-d", "-m", "4")
    }

    @Test
    fun recentCount() {
        assertThat(

            LogcatCommand(recentCount = 4).toList(),
        ).containsExactly("logcat", "-d", "-T", "4")
    }

    @Test
    fun recentSinceAndroid6OrOlder() {
        val zeroOffset = ZoneOffset.ofTotalSeconds(0)
        assertThat(
            LogcatCommand(
                recentSince = LocalDateTime.ofEpochSecond(0, 123456789, zeroOffset),
                getSdkVersion = { 23 },
            ).toList(),
        ).containsExactly("logcat", "-d", "-T", "01-01 00:00:00.123456789")
    }

    @Test
    fun recentSince() {
        val zeroOffset = ZoneOffset.ofTotalSeconds(0)
        assertThat(

            LogcatCommand(
                recentSince = LocalDateTime.ofEpochSecond(987654321, 123456789, zeroOffset),
                getSdkVersion = { 24 },
            ).toList(),
        ).containsExactly("logcat", "-d", "-T", "987654321.123456789")
    }

    @Test
    fun getBufferSize() {
        assertThat(

            LogcatCommand(getBufferSize = true).toList(),
        ).containsExactly("logcat", "-d", "-g")
    }

    @Test
    fun last() {
        assertThat(

            LogcatCommand(last = true).toList(),
        ).containsExactly("logcat", "-d", "-L")
    }

    @Test
    fun buffers() {
        assertThat(
            LogcatCommand(
                buffers = listOf(
                    LogcatBufferId.MAIN,
                    LogcatBufferId.RADIO,
                    LogcatBufferId.EVENTS,
                    LogcatBufferId.SYSTEM,
                    LogcatBufferId.CRASH,
                    LogcatBufferId.STATS,
                    LogcatBufferId.SECURITY,
                    LogcatBufferId.KERNEL,
                    LogcatBufferId.ALL,
                ),
            ).toList(),
        ).isEqualTo(

            listOf(
                "logcat",
                "-b", "main",
                "-b", "radio",
                "-b", "events",
                "-b", "system",
                "-b", "crash",
                "-b", "stats",
                "-b", "security",
                "-b", "kernel",
                "-b", "all",
                "-d",
            ),
        )
    }

    @Test
    fun binary() {
        assertThat(
            LogcatCommand(binary = true).toList(),
        ).containsExactly("logcat", "-d", "-B")
    }

    @Test
    fun statistics() {
        assertThat(
            LogcatCommand(statistics = true).toList(),
        ).containsExactly("logcat", "-d", "-S")
    }

    @Test
    fun getPrune() {
        assertThat(
            LogcatCommand(getPrune = true).toList(),
        ).containsExactly("logcat", "-d", "-p")
    }

    @Test
    fun wrap() {
        assertThat(
            LogcatCommand(wrap = true).toList(),
        ).containsExactly("logcat", "-d", "--wrap")
    }

    @Test
    fun help() {
        assertThat(
            LogcatCommand(help = true).toList(),
        ).containsExactly("logcat", "-d", "-h")
    }
}
