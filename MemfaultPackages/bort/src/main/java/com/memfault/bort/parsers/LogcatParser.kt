package com.memfault.bort.parsers

import com.memfault.bort.shared.LogcatCommand
import com.memfault.bort.shared.LogcatFormat
import com.memfault.bort.shared.LogcatFormatModifier
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * After running logcat, Bort will need to pull out the timestamp of the last log line.
 * This timestamp + 1 nsec, will be used as the starting point for the next logcat invocation.
 */
data class LogcatLine(
    val logTime: Instant? = null,
    val uid: Int? = null,
    val lineUpToTag: String? = null,
    val message: String? = null,
)

class LogcatParser(
    val lines: Sequence<String>,
    val command: LogcatCommand,
    val uidDecoder: (String) -> Int = android.os.Process::getUidForName
) {
    private val uidCache: MutableMap<String, Int?> = mutableMapOf()
    init {
        // The parsing assumes these formatting flags are used:
        check(
            command.format == LogcatFormat.THREADTIME && command.formatModifiers.containsAll(
                listOf(
                    LogcatFormatModifier.NSEC,
                    LogcatFormatModifier.UTC,
                    LogcatFormatModifier.YEAR,
                    LogcatFormatModifier.UID,
                )
            )
        ) {
            "Unsupported logcat command: $command"
        }
    }

    fun parse(): Sequence<LogcatLine> {
        val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.nnnnnnnnn Z")
        return lines.map { line ->
            LINE_REGEX.matchEntire(line)?.let { result ->
                val time = result.groups.get(TIME_GROUP)?.value?.let {
                    val time = try {
                        ZonedDateTime.parse(it, timeFormatter).toInstant()
                    } catch (e: DateTimeParseException) {
                        null
                    }
                    time
                }

                val uid = result.groups.get(UID_GROUP)?.value?.let { parseUid(it) }
                // Note: named groups can't contain other named groups, that's why this one is not named:
                //  java.util.regex.PatternSyntaxException: named capturing group is missing trailing '>' near index 6
                val upToTag = result.groups.get(UP_TO_TAG_GROUP)?.value
                val msg = result.groups.get(MSG_GROUP)?.value
                LogcatLine(time, uid, upToTag, msg)
            } ?: LogcatLine(message = line)
        }
    }

    private fun parseUid(uid: String): Int? =
        uid.toIntOrNull() ?: uidCache.computeIfAbsent(uid) {
            uidDecoder(uid).let {
                if (it == -1) null else it
            }
        }
}

fun Sequence<String>.toLogcatLines(command: LogcatCommand): Sequence<LogcatLine> =
    LogcatParser(this, command).parse()

private val LINE_REGEX = Regex(
    """^(([0-9]+-[0-9]+-[0-9]+ [0-9]+:[0-9]+:[0-9]+\.[0-9]+ \+[0-9]+)""" +
        """\s+([a-zA-Z_\-0-9]+)""" +
        """\s+[^:]+:""" + // everything up to tag:
        """\s+)(.*)$"""
)

private const val UP_TO_TAG_GROUP = 1
private const val TIME_GROUP = 2
private const val UID_GROUP = 3
private const val MSG_GROUP = 4
