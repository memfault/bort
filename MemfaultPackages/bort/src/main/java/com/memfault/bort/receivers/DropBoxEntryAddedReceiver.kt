package com.memfault.bort.receivers

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.DropBoxManager
import com.memfault.bort.DevMode
import com.memfault.bort.dropbox.DropBoxFilters
import com.memfault.bort.dropbox.ProcessedEntryCursorProvider
import com.memfault.bort.dropbox.enqueueOneTimeDropBoxQueryTask
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.shared.Logger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DropBoxEntryAddedReceiver @Inject constructor(
    private val settingsProvider: SettingsProvider,
    private val dropBoxProcessedEntryCursorProvider: ProcessedEntryCursorProvider,
    private val dropBoxFilters: DropBoxFilters,
    private val application: Application,
    private val devMode: DevMode,
) : BortEnabledFilteringReceiver(
    setOf(DropBoxManager.ACTION_DROPBOX_ENTRY_ADDED),
) {
    private var registered = false

    fun initialize() {
        val enabled = bortEnabledProvider.isEnabled() && settingsProvider.dropBoxSettings.dataSourceEnabled &&
            settingsProvider.dropBoxSettings.processImmediately
        if (registered && !enabled) {
            application.unregisterReceiver(this)
            registered = false
        }
        if (!registered && enabled) {
            application.registerReceiver(this, IntentFilter(DropBoxManager.ACTION_DROPBOX_ENTRY_ADDED))
            registered = true
        }
        if (devMode.isEnabled()) {
            // Dev Mode: process any new dropbox entries right away after Bort starts.
            enqueueOneTimeDropBoxQueryTask(application)
        }
    }

    override fun onReceivedAndEnabled(
        context: Context,
        intent: Intent,
        action: String,
    ) {
        if (!settingsProvider.dropBoxSettings.dataSourceEnabled) return

        val thisTag = intent.getStringExtra(DropBoxManager.EXTRA_TAG)
        thisTag?.let { tag ->
            if (!dropBoxFilters.tagFilter().contains(tag)) {
                // Don't forward intents to Bort for unsupported tags.
                Logger.v("Dropping intent for $tag")
                return
            }
        }
        Logger.v("Using intent for $thisTag")

        dropBoxProcessedEntryCursorProvider.handleTimeFromEntryAddedIntent(intent)

        // Note we're not using the extras (tag, time & dropped count) of the intent.
        // The task will attempt to query and process any dropbox entry that has not been processed.
        enqueueOneTimeDropBoxQueryTask(context)
    }
}
