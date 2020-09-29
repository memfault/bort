package com.memfault.usagereporter

import android.app.Application
import com.memfault.bort.shared.LogLevel
import com.memfault.bort.shared.Logger

class UsageReporter : Application() {
    override fun onCreate() {
        super.onCreate()

        Logger.TAG = "mflt-report"
        Logger.TAG_TEST = "mflt-report-test"
        Logger.minLevel = LogLevel.fromInt(BuildConfig.MINIMUM_LOG_LEVEL) ?: LogLevel.NONE
    }
}
