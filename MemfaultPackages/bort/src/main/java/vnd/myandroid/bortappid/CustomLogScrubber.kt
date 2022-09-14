package vnd.myandroid.bortappid

import com.memfault.bort.LineScrubbingCleaner

/**
 * Update this class to implement custom log scrubbing rules. This method will be called for each log line processed
 * by Bort.
 *
 * Returning the input (default behaviour below) will have no effect. The return value will be uploaded by Bort, in
 * place of the input.
 */
object CustomLogScrubber : LineScrubbingCleaner {
    override fun clean(line: String): String {
        return line
    }
}
