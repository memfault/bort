package com.memfault.bort.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.memfault.bort.Bort
import com.memfault.bort.Logger
import com.memfault.bort.SettingsProvider
import com.memfault.bort.BortEnabledProvider

/** A receiver that only runs if the SDK is enabled. */
abstract class BortEnabledFilteringReceiver(
    action: String
): SingleActionReceiver(action) {
    override fun onIntentReceived(context: Context, intent: Intent) {
        if (!bortEnabledProvider.isEnabled()) {
            Logger.i("Bort not enabled, not running receiver")
            return
        }
        onReceivedAndEnabled(context, intent)
    }

    abstract fun onReceivedAndEnabled(context: Context, intent: Intent)
}

/** A receiver that whitelists intents with the specified action. */
abstract class SingleActionReceiver(
    private val action: String
): BroadcastReceiver() {
    protected lateinit var settingsProvider: SettingsProvider
    protected lateinit var bortEnabledProvider: BortEnabledProvider

    override fun onReceive(context: Context?, intent: Intent?) {
        Logger.v("Received $action")
        context ?: return
        intent ?: return
        bind()

        Logger.v("Handling $action")
        onIntentReceived(context, intent)
    }

    protected fun bind() = Bort.appComponents().also {
        settingsProvider = it.settingsProvider
        bortEnabledProvider = it.bortEnabledProvider
    }

    abstract fun onIntentReceived(context: Context, intent: Intent)
}
