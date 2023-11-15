package com.memfault.bort.ota.lib

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.memfault.bort.shared.Logger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CheckForUpdatesReceiver : BroadcastReceiver() {
    @Inject lateinit var updater: Updater

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "com.memfault.intent.action.OTA_CHECK_FOR_UPDATES" ->
                CoroutineScope(Dispatchers.Default).launch {
                    Logger.w("Update check requested via Intent")
                    updater.perform(Action.CheckForUpdate(background = true))
                }
        }
    }
}
