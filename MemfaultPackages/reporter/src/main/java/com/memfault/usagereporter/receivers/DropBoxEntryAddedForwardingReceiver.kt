package com.memfault.usagereporter.receivers

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.DropBoxManager
import com.memfault.bort.shared.BuildConfig
import com.memfault.bort.shared.DROPBOX_ENTRY_ADDED_RECEIVER_QUALIFIED_NAME
import com.memfault.bort.shared.INTENT_ACTION_DROPBOX_ENTRY_ADDED
import com.memfault.bort.shared.Logger
import com.memfault.usagereporter.DropBoxFilterSettingsProvider
import com.memfault.usagereporter.USER_CURRENT
import com.memfault.usagereporter.UserHandle

class DropBoxEntryAddedForwardingReceiver(
    private val dropBoxFilterSettingsProvider: DropBoxFilterSettingsProvider,
) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        intent ?: return
        if (intent.action != DropBoxManager.ACTION_DROPBOX_ENTRY_ADDED) return

        val tagsSupported = dropBoxFilterSettingsProvider.includedTags
        val thisTag = intent.getStringExtra(DropBoxManager.EXTRA_TAG)
        thisTag?.let { tag ->
            if (!tagsSupported.contains(tag)) {
                // Don't forward intents to Bort for unsupported tags.
                Logger.v("Not forwarding dropbox intent for $tag")
                return
            }
        }
        Logger.v("Forwarding dropbox intent for $thisTag")

        context.sendBroadcastAsUser(
            Intent(intent).apply {
                action = INTENT_ACTION_DROPBOX_ENTRY_ADDED
                component = ComponentName(
                    BuildConfig.BORT_APPLICATION_ID,
                    DROPBOX_ENTRY_ADDED_RECEIVER_QUALIFIED_NAME
                )
            },
            UserHandle(USER_CURRENT)
        )
    }
}
