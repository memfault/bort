package com.memfault.usagereporter

import com.memfault.bort.shared.LogLevel

class FakeLogLevelPreferenceProvider : LogLevelPreferenceProvider {

    private var logLevel = LogLevel.TEST

    override fun setLogLevel(logLevel: LogLevel) {
        this.logLevel = logLevel
    }

    override fun getLogLevel(): LogLevel = logLevel
}
