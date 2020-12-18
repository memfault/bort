package com.memfault.bort.parsers

import java.io.InputStream

/**
 * After running batterystats, Bort will need to pull out 2 things:
 *  - the "NEXT" value, this is the cursor value that Bort will use next time it runs batterystats, to only get the
 *    data since the last time.
 *  - whether there is a "TIME" command present in the output. When absent, it means that the --history-start (cursor)
 *    that was passed, lies in the future according to batterystats. In this case we need to reset our cursor.
 */
data class BatteryStatsReport(
    val hasTime: Boolean,
    val next: Long?,
)

class BatteryStatsParser(val inputStream: InputStream) {
    fun parse(): BatteryStatsReport {
        val lines = Lines(inputStream.bufferedReader().lineSequence().asIterable())
        val hasTime = lines.until { it.startsWith("NEXT: ") }.use {
            it.any(TIME_REGEX::containsMatchIn)
        }
        val next = Iterable({ lines })
            .firstOrNull()?.split(": ", limit = 2)?.elementAtOrNull(1)?.toLongOrNull()
        return BatteryStatsReport(next = next, hasTime = hasTime)
    }
}

private val TIME_REGEX = Regex("""^[0-9]+,h,[0-9]+(:RESET)?:TIME:""")
