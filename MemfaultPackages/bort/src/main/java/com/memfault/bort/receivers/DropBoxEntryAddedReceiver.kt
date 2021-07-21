package com.memfault.bort.receivers

import android.content.Context
import android.content.Intent
import com.memfault.bort.dropbox.enqueueDropBoxQueryTask
import com.memfault.bort.shared.INTENT_ACTION_DROPBOX_ENTRY_ADDED
import com.memfault.bort.shared.goAsync

class DropBoxEntryAddedReceiver : BortEnabledFilteringReceiver(
    setOf(INTENT_ACTION_DROPBOX_ENTRY_ADDED)
) {
    override fun onReceivedAndEnabled(context: Context, intent: Intent, action: String) {
        if (!settingsProvider.dropBoxSettings.dataSourceEnabled) return

        goAsync {
            if (!bortSystemCapabilities.supportsCaliperDropBoxTraces()) return@goAsync

            dropBoxProcessedEntryCursorProvider.handleTimeChangeFromEntryAddedIntent(intent)

            // Note we're not using the extras (tag, time & dropped count) of the intent.
            // The task will attempt to query and process any dropbox entry that has not been processed.
            enqueueDropBoxQueryTask(context)
        }
    }
}
