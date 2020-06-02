package com.memfault.bort.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.memfault.bort.Bort
import com.memfault.bort.Logger
import com.memfault.bort.SettingsProvider

abstract class SingleActionBroadcastReceiver(
    protected val action: String
): BroadcastReceiver() {
    protected lateinit var settingsProvider: SettingsProvider

    override fun onReceive(context: Context?, intent: Intent?) {
        Logger.v("Received $action")
        context ?: return
        intent ?: return
        bindSettings()

        when {
            settingsProvider.isBuildTypeBlacklisted() -> return
            intent.action != action -> return
        }
        Logger.v("Handling $action")
        onIntentReceived(context, intent)
    }

    protected fun bindSettings() {
        settingsProvider = Bort.appComponents().settingsProvider
    }

    abstract fun onIntentReceived(context: Context, intent: Intent)
}
