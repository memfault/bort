package com.memfault.bort.logcat

import com.memfault.bort.parsers.LogcatLine
import com.memfault.bort.time.BaseAbsoluteTime
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.memfault.bort.tokenbucket.takeSimple

private const val OOPS_TOKEN_START = "------------[ cut here ]------------"

interface LogcatLineProcessor {
    fun process(line: LogcatLine)
    fun finish(lastLogTime: BaseAbsoluteTime)
}

object NoopLogcatLineProcessor : LogcatLineProcessor {
    override fun process(line: LogcatLine) {}
    override fun finish(lastLogTime: BaseAbsoluteTime) {}
}

class KernelOopsDetector(
    private val tokenBucketStore: TokenBucketStore,
    private val handleEventOfInterest: (time: BaseAbsoluteTime) -> Unit,
    foundOops: Boolean = false,
) : LogcatLineProcessor {
    var foundOops: Boolean = foundOops
        private set

    /**
     * Called for every logcat line, including separators
     */
    override fun process(line: LogcatLine) {
        if (foundOops) return
        if (line.buffer != "kernel") return
        if (line.message != OOPS_TOKEN_START) return
        foundOops = true
    }

    /**
     * Called at the end of processing a logcat file
     */
    override fun finish(lastLogTime: BaseAbsoluteTime) {
        if (!foundOops) return
        if (!tokenBucketStore.takeSimple(tag = "oops")) return
        handleEventOfInterest(lastLogTime)
    }
}
