package com.memfault.bort

import android.app.Application
import com.memfault.bort.requester.BugReportRequester

class BortApplication : Application() {
    private val settingsProvider = SettingsProvider()

    override fun onCreate() {
        super.onCreate()
        Logger.minLevel = settingsProvider.minLogLevel()
        Logger.v("onCreate")

        BugReportRequester(
            context = this,
            settingsProvider = settingsProvider
        ).requestPeriodic()
    }
}
