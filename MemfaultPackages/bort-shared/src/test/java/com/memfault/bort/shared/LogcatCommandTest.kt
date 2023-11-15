package com.memfault.bort.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class LogcatCommandTest {
    @Test
    fun filterSpecs() {
        assertEquals(
            listOf("logcat", "-d", "TAG:D", "*:E"),
            LogcatCommand(
                filterSpecs = listOf(
                    LogcatFilterSpec(tag = Logger.getTag(), priority = LogcatPriority.DEBUG),
                    LogcatFilterSpec(priority = LogcatPriority.ERROR),
                ),
            ).toList(),
        )
    }

    @Test
    fun format() {
        assertEquals(
            listOf("logcat", "-d", "-v", "brief"),
            LogcatCommand(format = LogcatFormat.BRIEF).toList(),
        )
    }

    @Test
    fun formatModifiers() {
        assertEquals(
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
        )
    }

    @Test
    fun dividers() {
        assertEquals(
            listOf("logcat", "-D", "-d"),
            LogcatCommand(dividers = true).toList(),
        )
    }

    @Test
    fun clear() {
        assertEquals(
            listOf("logcat", "-c", "-d"),
            LogcatCommand(clear = true).toList(),
        )
    }

    @Test
    fun maxCount() {
        assertEquals(
            listOf("logcat", "-d", "-m", "4"),
            LogcatCommand(maxCount = 4).toList(),
        )
    }

    @Test
    fun recentCount() {
        assertEquals(
            listOf("logcat", "-d", "-T", "4"),
            LogcatCommand(recentCount = 4).toList(),
        )
    }

    @Test
    fun recentSinceAndroid6OrOlder() {
        val zeroOffset = ZoneOffset.ofTotalSeconds(0)
        assertEquals(
            listOf("logcat", "-d", "-T", "01-01 00:00:00.123456789"),
            LogcatCommand(
                recentSince = LocalDateTime.ofEpochSecond(0, 123456789, zeroOffset),
                getSdkVersion = { 23 },
            ).toList(),
        )
    }

    @Test
    fun recentSince() {
        val zeroOffset = ZoneOffset.ofTotalSeconds(0)
        assertEquals(
            listOf("logcat", "-d", "-T", "987654321.123456789"),
            LogcatCommand(
                recentSince = LocalDateTime.ofEpochSecond(987654321, 123456789, zeroOffset),
                getSdkVersion = { 24 },
            ).toList(),
        )
    }

    @Test
    fun getBufferSize() {
        assertEquals(
            listOf("logcat", "-d", "-g"),
            LogcatCommand(getBufferSize = true).toList(),
        )
    }

    @Test
    fun last() {
        assertEquals(
            listOf("logcat", "-d", "-L"),
            LogcatCommand(last = true).toList(),
        )
    }

    @Test
    fun buffers() {
        assertEquals(
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
        )
    }

    @Test
    fun binary() {
        assertEquals(
            listOf("logcat", "-d", "-B"),
            LogcatCommand(binary = true).toList(),
        )
    }

    @Test
    fun statistics() {
        assertEquals(
            listOf("logcat", "-d", "-S"),
            LogcatCommand(statistics = true).toList(),
        )
    }

    @Test
    fun getPrune() {
        assertEquals(
            listOf("logcat", "-d", "-p"),
            LogcatCommand(getPrune = true).toList(),
        )
    }

    @Test
    fun wrap() {
        assertEquals(
            listOf("logcat", "-d", "--wrap"),
            LogcatCommand(wrap = true).toList(),
        )
    }

    @Test
    fun help() {
        assertEquals(
            listOf("logcat", "-d", "-h"),
            LogcatCommand(help = true).toList(),
        )
    }
}
