package com.memfault.bort.logcat

import com.memfault.bort.LogcatCollectionId

class FakeNextLogcatCidProvider(
    private val values: Iterator<LogcatCollectionId>,
) : NextLogcatCidProvider {
    override var cid: LogcatCollectionId = values.next()

    override fun rotate(): Pair<LogcatCollectionId, LogcatCollectionId> =
        Pair(cid, values.next()).also { cid = it.second }

    companion object {
        fun incrementing() = FakeNextLogcatCidProvider(generateLogcatCollectionIds().iterator())
    }
}

fun generateLogcatCollectionIds() =
    generateUUIDs().map(::LogcatCollectionId)

fun generateUUIDs() = sequence {
    yieldAll(
        generateSequence(1L) { it + 1 }
            .map { java.util.UUID(0, it) },
    )
}
