package com.memfault.usagereporter.receivers

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.memfault.bort.shared.DROPBOX_ENTRY_ADDED_RECEIVER_QUALIFIED_NAME
import com.memfault.bort.shared.INTENT_ACTION_DROPBOX_ENTRY_ADDED
import com.memfault.bort.shared.Logger
import com.memfault.usagereporter.BuildConfig
import com.memfault.usagereporter.USER_CURRENT
import com.memfault.usagereporter.UserHandle

class DropBoxEntryAddedForwardingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        intent ?: return
        Logger.v("Forwarding action=${intent.action}")

        context.sendBroadcastAsUser(Intent(intent).apply {
            action = INTENT_ACTION_DROPBOX_ENTRY_ADDED
            component = ComponentName(
                BuildConfig.BORT_APPLICATION_ID,
                DROPBOX_ENTRY_ADDED_RECEIVER_QUALIFIED_NAME
            )
        }, UserHandle(USER_CURRENT))
    }
}
