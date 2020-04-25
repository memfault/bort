package com.memfault.bort.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.memfault.bort.INTENT_ACTION_BOOT_COMPLETED
import com.memfault.bort.Logger
import com.memfault.bort.SettingsProvider
import com.memfault.bort.requester.BugReportRequester

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        intent ?: return
        when {
            intent.action != INTENT_ACTION_BOOT_COMPLETED -> return
        }
        Logger.v("Requesting periodic bug report from BootReceiver")
        BugReportRequester(
            context,
            SettingsProvider()
        ).requestPeriodic()
    }
}
