package com.memfault.usagereporter.receivers

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.os.DropBoxManager
import com.memfault.bort.Main
import com.memfault.bort.android.registerForIntents
import com.memfault.bort.shared.BuildConfig
import com.memfault.bort.shared.DROPBOX_ENTRY_ADDED_RECEIVER_QUALIFIED_NAME
import com.memfault.bort.shared.INTENT_ACTION_DROPBOX_ENTRY_ADDED
import com.memfault.bort.shared.Logger
import com.memfault.usagereporter.RealDropBoxFilterSettingsProvider
import com.memfault.usagereporter.ReporterSettingsPreferenceProvider
import com.memfault.usagereporter.USER_CURRENT
import com.memfault.usagereporter.UserHandle
import com.memfault.usagereporter.onBortEnabledFlow
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class DropBoxEntryAddedForwardingReceiver
@Inject constructor(
    private val application: Application,
    @Main private val mainCoroutineContext: CoroutineContext,
    private val dropBoxFilterSettingsProvider: RealDropBoxFilterSettingsProvider,
    private val reporterSettingsPreferenceProvider: ReporterSettingsPreferenceProvider,
) {
    fun start() {
        CoroutineScope(mainCoroutineContext).launch {
            reporterSettingsPreferenceProvider.settings
                .onBortEnabledFlow("ACTION_DROPBOX_ENTRY_ADDED") {
                    application.registerForIntents(DropBoxManager.ACTION_DROPBOX_ENTRY_ADDED)
                }
                .onEach { intent ->
                    val tagsSupported = dropBoxFilterSettingsProvider.includedTags
                    val thisTag = intent.getStringExtra(DropBoxManager.EXTRA_TAG)
                    thisTag?.let { tag ->
                        if (!tagsSupported.contains(tag)) {
                            // Don't forward intents to Bort for unsupported tags.
                            Logger.v("Not forwarding dropbox intent for $tag")
                            return@onEach
                        }
                    }
                    Logger.v("Forwarding dropbox intent for $thisTag")

                    application.sendBroadcastAsUser(
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
                .launchIn(this)
        }
    }
}
