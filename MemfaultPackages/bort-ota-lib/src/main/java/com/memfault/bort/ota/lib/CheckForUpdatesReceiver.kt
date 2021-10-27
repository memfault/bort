package com.memfault.bort.ota.lib

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.memfault.bort.shared.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CheckForUpdatesReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "com.memfault.intent.action.OTA_CHECK_FOR_UPDATES" ->
                CoroutineScope(Dispatchers.Default).launch {
                    Logger.w("Update check requested via Intent")
                    with(context.applicationContext.updater()) {
                        perform(Action.CheckForUpdate(background = true))
                    }
                }
        }
    }
}
