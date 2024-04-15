package com.memfault.bort.ota.lib

import com.memfault.bort.shared.LogLevel

interface OtaLoggerSettings {
    val minLogcatLevel: LogLevel
}
