package com.memfault.bort.shared

import android.os.Build
import android.os.Bundle
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Usage: logcat [options] [filterspecs]
 * options include:
 *   -s              Set default filter to silent. Equivalent to filterspec '*:S'
 *   -f <file>, --file=<file>               Log to file. Default is stdout
 *   -r <kbytes>, --rotate-kbytes=<kbytes>
 *                   Rotate log every kbytes. Requires -f option
 *   -n <count>, --rotate-count=<count>
 *                   Sets max number of rotated logs to <count>, default 4
 *   --id=<id>       If the signature id for logging to file changes, then clear
 *                   the fileset and continue
 *   -v <format>, --format=<format>
 *                   Sets log print format verb and adverbs, where <format> is:
 *                     brief help long process raw tag thread threadtime time
 *                   and individually flagged modifying adverbs can be added:
 *                     color descriptive epoch monotonic printable uid
 *                     usec UTC year zone
 *                   Multiple -v parameters or comma separated list of format and
 *                   format modifiers are allowed.
 *   -D, --dividers  Print dividers between each log buffer
 *   -c, --clear     Clear (flush) the entire log and exit
 *                   if Log to File specified, clear fileset instead
 *   -d              Dump the log and then exit (don't block)
 *   -e <expr>, --regex=<expr>
 *                   Only print lines where the log message matches <expr>
 *                   where <expr> is a Perl-compatible regular expression
 *   -m <count>, --max-count=<count>
 *                   Quit after printing <count> lines. This is meant to be
 *                   paired with --regex, but will work on its own.
 *   --print         Paired with --regex and --max-count to let content bypass
 *                   regex filter but still stop at number of matches.
 *   -t <count>      Print only the most recent <count> lines (implies -d)
 *   -t '<time>'     Print most recent lines since specified time (implies -d)
 *   -T <count>      Print only the most recent <count> lines (does not imply -d)
 *   -T '<time>'     Print most recent lines since specified time (not imply -d)
 *                   count is pure numerical, time is 'MM-DD hh:mm:ss.mmm...'
 *                   'YYYY-MM-DD hh:mm:ss.mmm...' or 'sssss.mmm...' format
 *   -g, --buffer-size                      Get the size of the ring buffer.
 *   -G <size>, --buffer-size=<size>
 *                   Set size of log ring buffer, may suffix with K or M.
 *   -L, --last      Dump logs from prior to last reboot
 *   -b <buffer>, --buffer=<buffer>         Request alternate ring buffer, 'main',
 *                   'system', 'radio', 'events', 'crash', 'default' or 'all'.
 *                   Additionally, 'kernel' for userdebug and eng builds, and
 *                   'security' for Device Owner installations.
 *                   Multiple -b parameters or comma separated list of buffers are
 *                   allowed. Buffers interleaved. Default -b main,system,crash.
 *   -B, --binary    Output the log in binary.
 *   -S, --statistics                       Output statistics.
 *   -p, --prune     Print prune white and ~black list. Service is specified as
 *                   UID, UID/PID or /PID. Weighed for quicker pruning if prefix
 *                   with ~, otherwise weighed for longevity if unadorned. All
 *                   other pruning activity is oldest first. Special case ~!
 *                   represents an automatic quicker pruning for the noisiest
 *                   UID as determined by the current statistics.
 *   -P '<list> ...', --prune='<list> ...'
 *                   Set prune white and ~black list, using same format as
 *                   listed above. Must be quoted.
 *   --pid=<pid>     Only prints logs from the given pid.
 *   --wrap          Sleep for 2 hours or when buffer about to wrap whichever
 *                   comes first. Improves efficiency of polling by providing
 *                   an about-to-wrap wakeup.
 *
 * filterspecs are a series of
 *   <tag>[:priority]
 *
 * where <tag> is a log component tag (or * for all) and priority is:
 *   V    Verbose (default for <tag>)
 *   D    Debug (default for '*')
 *   I    Info
 *   W    Warn
 *   E    Error
 *   F    Fatal
 *   S    Silent (suppress all output)
 *
 * '*' by itself means '*:D' and <tag> by itself means <tag>:V.
 * If no '*' filterspec or -s on command line, all filter defaults to '*:V'.
 * eg: '*:S <tag>' prints only <tag>, '<tag>:S' suppresses all <tag> log messages.
 *
 * If not specified on the command line, filterspec is set from ANDROID_LOG_TAGS.
 *
 * If not specified with -v on command line, format is set from ANDROID_PRINTF_LOG
 * or defaults to "threadtime"
 *
 * -v <format>, --format=<format> options:
 *   Sets log print format verb and adverbs, where <format> is:
 *     brief long process raw tag thread threadtime time
 *   and individually flagged modifying adverbs can be added:
 *     color descriptive epoch monotonic printable uid usec UTC year zone
 *
 * Single format verbs:
 *   brief      — Display priority/tag and PID of the process issuing the message.
 *   long       — Display all metadata fields, separate messages with blank lines.
 *   process    — Display PID only.
 *   raw        — Display the raw log message, with no other metadata fields.
 *   tag        — Display the priority/tag only.
 *   thread     — Display priority, PID and TID of process issuing the message.
 *   threadtime — Display the date, invocation time, priority, tag, and the PID
 *                and TID of the thread issuing the message. (the default format).
 *   time       — Display the date, invocation time, priority/tag, and PID of the
 *              process issuing the message.
 *
 * Adverb modifiers can be used in combination:
 *   color       — Display in highlighted color to match priority. i.e. VERBOSE
 *                 DEBUG INFO WARNING ERROR FATAL
 *   descriptive — events logs only, descriptions from event-log-tags database.
 *   epoch       — Display time as seconds since Jan 1 1970.
 *   monotonic   — Display time as cpu seconds since last boot.
 *   printable   — Ensure that any binary logging content is escaped.
 *   uid         — If permitted, display the UID or Android ID of logged process.
 *   usec        — Display time down the microsecond precision.
 *   UTC         — Display time as UTC.
 *   year        — Add the year to the displayed time.
 *   zone        — Add the local timezone to the displayed time.
 *   "<zone>"    — Print using this public named timezone (experimental).
 */

/**
 * @param recentSince Only include logs since given time. Logs with the exact same time given
 * will be included in the output. This is far from perfect: there are also race conditions internal
 * to logd, see https://cs.android.com/android/platform/superproject/+/master:system/logging/logd/LogReaderThread.cpp;l=81-88;drc=c5c9eba40284df46ede8b1b08062315a9130358e
 * This is also experienced when the device time is changed "backwards".
 * Also, logcat only keeps timestamps that do not have a timezone, so erroneous behavior is to be
 * expected when the device timezone is changed...
 */
data class LogcatCommand(
    val filterSpecs: List<LogcatFilterSpec> = emptyList(),
    val format: LogcatFormat? = null,
    val formatModifiers: List<LogcatFormatModifier> = emptyList(),
    val dividers: Boolean = false,
    val clear: Boolean = false,
    val dumpAndExit: Boolean = true,
    val maxCount: Int? = null,
    val recentCount: Int? = null,
    val recentSince: LocalDateTime? = null,
    val getBufferSize: Boolean = false,
    val last: Boolean = false,
    val buffers: List<LogcatBufferId> = emptyList(),
    val binary: Boolean = false,
    val statistics: Boolean = false,
    val getPrune: Boolean = false,
    val wrap: Boolean = false,
    val help: Boolean = false,
    val getSdkVersion: () -> Int = { Build.VERSION.SDK_INT }
) : Command {
    private fun booleanFlagMap() = mapOf(
        DIVIDERS to dividers,
        CLEAR to clear,
        DUMP_AND_EXIT to dumpAndExit,
        GET_BUFFER_SIZE to getBufferSize,
        LAST to last,
        BINARY to binary,
        STATISTICS to statistics,
        GET_PRUNE to getPrune,
        WRAP to wrap,
        HELP to help
    )

    private fun booleanFlags() = booleanFlagMap()
        .filter { (_, value) -> value }
        .map { (flag, _) -> flag }

    private fun filterSpecFlags() = filterSpecs.map {
        when (it.priority) {
            null -> it.tag
            else -> "${it.tag}:${it.priority.cliValue}"
        }
    }

    private fun formatFlags() = when (format) {
        null -> emptyList()
        else -> listOf("-v", format.cliValue)
    }

    private fun formatModifiersFlags() = formatModifiers.map {
        listOf("-v", it.cliValue)
    }.flatten()

    private fun bufferFlags() = buffers.map {
        listOf("-b", it.cliValue)
    }.flatten()

    private fun miscFlags() = run {
        val options = mutableListOf<String>()
        maxCount?.let {
            options.add("-m")
            options.add("$maxCount")
        }
        recentCount?.let {
            options.add("-T")
            options.add("$recentCount")
        }
        recentSince?.let {
            options.add("-T")
            options.add(
                // See accepted formats:
                // https://cs.android.com/android/platform/superproject/+/master:system/logging/logcat/logcat.cpp;l=441-447;drc=f78cf0f310a65febda6a31e827365eac795bcc46
                if (getSdkVersion() < 24) {
                    // Before Android 7, only "%m-%d %H:%M:%S.%q" was supported
                    DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSSSSSSSS").format(recentSince)
                } else {
                    // recentSince is in local time:
                    "%d.%09d".format(
                        recentSince.toEpochSecond(ZoneOffset.ofTotalSeconds(0)),
                        recentSince.nano
                    )
                }
            )
        }
        options.toList()
    }

    override fun toList(): List<String> =
        listOf("logcat") +
            bufferFlags() +
            booleanFlags() +
            miscFlags() +
            formatFlags() +
            formatModifiersFlags() +
            filterSpecFlags()

    override fun toBundle(): Bundle =
        Bundle().apply {
            for ((key, value) in booleanFlagMap()) {
                if (value) putBoolean(key, value)
            }
            if (filterSpecs.isNotEmpty()) {
                putParcelableArray(FILTER_SPECS, filterSpecs.map { it.toBundle() }.toTypedArray())
            }
            format?.let {
                putByte(FORMAT, it.id)
            }
            if (formatModifiers.isNotEmpty()) {
                putByteArray(FORMAT_MODIFIERS, formatModifiers.map { it.id }.toByteArray())
            }
            if (buffers.isNotEmpty()) {
                putByteArray(BUFFERS, buffers.map { it.id }.toByteArray())
            }
            maxCount?.let {
                putInt(MAX_COUNT, it)
            }
            recentCount?.let {
                putInt(RECENT_COUNT, it)
            }
            recentSince?.let {
                // recentSince is in local time:
                putLong(RECENT_SINCE_SECS, it.toEpochSecond(ZoneOffset.ofTotalSeconds(0)))
                putInt(RECENT_SINCE_NANOS, it.nano)
            }
        }

    companion object {
        fun fromBundle(bundle: Bundle) = with(bundle) {
            LogcatCommand(
                filterSpecs = getParcelableArray(FILTER_SPECS)?.mapNotNull {
                    LogcatFilterSpec.fromBundle(it as Bundle)
                }.listify(),
                format = getByteOrNull(FORMAT)?.let { LogcatFormat.getById(it) },
                formatModifiers = getByteArray(FORMAT_MODIFIERS)?.map {
                    LogcatFormatModifier.getById(it)
                }?.filterNotNull().listify(),
                dividers = getBoolean(DIVIDERS),
                clear = getBoolean(CLEAR),
                dumpAndExit = getBoolean(DUMP_AND_EXIT),
                maxCount = getIntOrNull(MAX_COUNT),
                recentCount = getIntOrNull(RECENT_COUNT),
                recentSince = getLongOrNull(RECENT_SINCE_SECS)?.let { secs ->
                    // recentSince is in local time:
                    LocalDateTime.ofEpochSecond(
                        secs, getInt(RECENT_SINCE_NANOS),
                        ZoneOffset.ofTotalSeconds(0)
                    )
                },
                getBufferSize = getBoolean(GET_BUFFER_SIZE),
                last = getBoolean(LAST),
                buffers = getByteArray(BUFFERS)?.map {
                    LogcatBufferId.getById(it)
                }?.filterNotNull().listify(),
                binary = getBoolean(BINARY),
                statistics = getBoolean(STATISTICS),
                getPrune = getBoolean(GET_PRUNE),
                wrap = getBoolean(WRAP),
                help = getBoolean(HELP)
            )
        }
    }
}

enum class LogcatFormat(val id: Byte, val cliValue: String) {
    BRIEF(0, "brief"),
    LONG(1, "long"),
    PROCESS(2, "process"),
    RAW(3, "raw"),
    TAG(4, "tag"),
    THREAD(5, "thread"),
    THREADTIME(6, "threadtime"),
    TIME(7, "time");

    companion object {
        fun getById(id: Byte) = values().firstOrNull { it.id == id }
    }
}

enum class LogcatFormatModifier(val id: Byte, val cliValue: String) {
    COLOR(0, "color"),
    DESCRIPTIVE(1, "descriptive"),
    EPOCH(2, "epoch"),
    MONOTONIC(3, "monotonic"),
    PRINTABLE(4, "printable"),
    UID(5, "uid"),
    USEC(6, "usec"),
    UTC(7, "UTC"),
    YEAR(8, "year"),
    ZONE(9, "zone"),

    // Undocumented, but present since Android 8.0:
    NSEC(10, "nsec");

    companion object {
        fun getById(id: Byte) = values().firstOrNull { it.id == id }
    }
}

/**
 * Represents the logd buffer to use.
 * Maps to log_id_t:
 * https://android.googlesource.com/platform/system/logging/+/refs/heads/android10-release/liblog/include/android/log.h#143
 */
enum class LogcatBufferId(val id: Byte, val cliValue: String) {
    MAIN(0, "main"),
    RADIO(1, "radio"),
    EVENTS(2, "events"),
    SYSTEM(3, "system"),
    CRASH(4, "crash"),
    STATS(5, "stats"),
    SECURITY(6, "security"),
    KERNEL(7, "kernel"),
    ALL(-1, "all");

    companion object {
        fun getById(id: Byte) = values().firstOrNull { it.id == id }
    }
}

/**
 * Represents the logd log priority (level).
 * Maps to android_LogPriority:
 * https://android.googlesource.com/platform/system/logging/+/refs/heads/android10-release/liblog/include/android/log.h#66
 */

@Serializable(with = LogcatPrioritySerializer::class)
enum class LogcatPriority(val id: Byte, val cliValue: String) {
    VERBOSE(2, "V"),
    DEBUG(3, "D"),
    INFO(4, "I"),
    WARN(5, "W"),
    ERROR(6, "E"),
    FATAL(7, "F"),
    SILENT(8, "S");

    companion object {
        fun getById(id: Byte) = values().firstOrNull { it.id == id }
        fun getByCliValue(cliValue: String) = values().firstOrNull { it.cliValue == cliValue }
    }
}

object LogcatPrioritySerializer : KSerializer<LogcatPriority> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        serialName = "com.memfault.bort.shared.LogcatPriority",
        PrimitiveKind.STRING
    )

    override fun deserialize(decoder: Decoder): LogcatPriority =
        LogcatPriority.getByCliValue(decoder.decodeString())
            ?: throw SerializationException("could not decode priority using the provided cli value")

    override fun serialize(encoder: Encoder, value: LogcatPriority) {
        encoder.encodeString(value.cliValue)
    }
}

@Serializable
data class LogcatFilterSpec(val tag: String = "*", val priority: LogcatPriority?) {
    fun toBundle(): Bundle =
        Bundle().apply {
            putString(TAG, tag)
            priority?.let {
                putByte(PRIORITY, priority.id)
            }
        }

    companion object {
        fun fromBundle(bundle: Bundle) = with(bundle) {
            LogcatFilterSpec(
                getString(TAG) ?: "*".also {
                    Logger.e("Missing tag, defaulting to *")
                },
                LogcatPriority.getById(getByte(PRIORITY))
            )
        }
    }
}

private const val DIVIDERS = "-D"
private const val CLEAR = "-c"
private const val DUMP_AND_EXIT = "-d"
private const val GET_BUFFER_SIZE = "-g"
private const val LAST = "-L"
private const val BINARY = "-B"
private const val STATISTICS = "-S"
private const val GET_PRUNE = "-p"
private const val WRAP = "--wrap"
private const val HELP = "-h"

private const val TAG = "tag"
private const val PRIORITY = "prio"
private const val FILTER_SPECS = "filters"
private const val FORMAT = "fmt"
private const val FORMAT_MODIFIERS = "fmt-mods"
private const val BUFFERS = "buffers"
private const val MAX_COUNT = "max-count"
private const val RECENT_COUNT = "recent-count"
private const val RECENT_SINCE_SECS = "recent-since-secs"
private const val RECENT_SINCE_NANOS = "recent-since-nanos"
