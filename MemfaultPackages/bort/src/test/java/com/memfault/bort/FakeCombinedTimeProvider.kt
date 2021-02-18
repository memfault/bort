package com.memfault.bort

import com.memfault.bort.time.CombinedTime
import com.memfault.bort.time.CombinedTimeProvider
import com.memfault.bort.time.boxed
import java.time.Instant
import kotlin.time.milliseconds

object FakeCombinedTimeProvider : CombinedTimeProvider {
    override fun now() = CombinedTime(
        uptime = 987.milliseconds.boxed(),
        elapsedRealtime = 456.milliseconds.boxed(),
        linuxBootId = "230295cb-04d4-40b8-8624-ec37089b9b75",
        bootCount = 67,
        timestamp = Instant.ofEpochSecond(123456),
    )
}
