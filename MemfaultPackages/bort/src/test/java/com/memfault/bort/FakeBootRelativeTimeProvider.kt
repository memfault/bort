package com.memfault.bort

import com.memfault.bort.time.BootRelativeTime
import com.memfault.bort.time.BootRelativeTimeProvider
import com.memfault.bort.time.boxed
import kotlin.time.Duration.Companion.milliseconds

object FakeBootRelativeTimeProvider : BootRelativeTimeProvider {
    override fun now(): BootRelativeTime = BootRelativeTime(
        uptime = 987.milliseconds.boxed(),
        elapsedRealtime = 456.milliseconds.boxed(),
        linuxBootId = "230295cb-04d4-40b8-8624-ec37089b9b75",
        bootCount = 67,
    )
}
