package com.memfault.bort

object FakeBootRelativeTimeProvider : BootRelativeTimeProvider {
    override fun now(): BootRelativeTime = BootRelativeTime(
        uptime = 987,
        linuxBootId = "230295cb-04d4-40b8-8624-ec37089b9b75",
        bootCount = 67,
    )
}
