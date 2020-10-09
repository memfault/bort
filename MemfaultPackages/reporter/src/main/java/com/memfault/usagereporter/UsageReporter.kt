package com.memfault.usagereporter

import android.app.Application
import com.memfault.bort.shared.LogLevel
import com.memfault.bort.shared.Logger
import com.memfault.usagereporter.receivers.DropBoxEntryAddedForwardingReceiver
import android.content.IntentFilter
import android.os.DropBoxManager

class UsageReporter : Application() {
    override fun onCreate() {
        super.onCreate()

        Logger.TAG = "mflt-report"
        Logger.TAG_TEST = "mflt-report-test"
        Logger.minLevel = LogLevel.fromInt(BuildConfig.MINIMUM_LOG_LEVEL) ?: LogLevel.NONE

        Logger.v("Registering for DropBoxManager intents")
        registerReceiver(
            DropBoxEntryAddedForwardingReceiver(),
            IntentFilter(DropBoxManager.ACTION_DROPBOX_ENTRY_ADDED)
        )
    }
}
