package com.memfault.bort.logcat

import androidx.annotation.VisibleForTesting
import com.memfault.bort.parsers.LogcatLine
import com.memfault.bort.time.BaseAbsoluteTime
import com.memfault.bort.tokenbucket.KernelOops
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.memfault.bort.tokenbucket.takeSimple
import com.memfault.bort.uploader.HandleEventOfInterest
import javax.inject.Inject

private const val OOPS_TOKEN_START = "------------[ cut here ]------------"

interface LogcatLineProcessor {
    fun process(line: LogcatLine)
    fun finish(lastLogTime: BaseAbsoluteTime): Boolean
}

object NoopLogcatLineProcessor : LogcatLineProcessor {
    override fun process(line: LogcatLine) {}
    override fun finish(lastLogTime: BaseAbsoluteTime) = false
}

class KernelOopsDetector @Inject constructor(
    @KernelOops private val tokenBucketStore: TokenBucketStore,
    private val handleEventOfInterest: HandleEventOfInterest,
) : LogcatLineProcessor {
    @VisibleForTesting var foundOops: Boolean = false

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
    override fun finish(lastLogTime: BaseAbsoluteTime): Boolean {
        if (!foundOops) return false
        if (!tokenBucketStore.takeSimple(tag = "oops")) return false
        handleEventOfInterest.handleEventOfInterest(lastLogTime)
        return true
    }
}
