package com.memfault.bort.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.memfault.bort.settings.BortEnabledProvider
import com.memfault.bort.shared.Logger
import javax.inject.Inject

/** A receiver that only runs if the SDK is enabled. */
abstract class BortEnabledFilteringReceiver(
    actions: Set<String>,
) : FilteringReceiver(actions) {
    @Inject lateinit var bortEnabledProvider: BortEnabledProvider

    override fun onIntentReceived(context: Context, intent: Intent, action: String) {
        if (!bortEnabledProvider.isEnabled()) {
            Logger.i("Bort not enabled, not running receiver")
            return
        }
        onReceivedAndEnabled(context, intent, action)
    }

    abstract fun onReceivedAndEnabled(context: Context, intent: Intent, action: String)
}

/** A receiver that filters intents for the specified actions. */
abstract class FilteringReceiver(
    private val actions: Set<String>,
) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Logger.v("Received action=${intent?.action}")
        context ?: return
        intent ?: return
        intent.action?.let {
            if (!actions.contains(it)) {
                return
            }
            Logger.v("Handling $it")
            onIntentReceived(context, intent, it)
            Logger.test("Handled $it")
        }
    }

    abstract fun onIntentReceived(context: Context, intent: Intent, action: String)
}
