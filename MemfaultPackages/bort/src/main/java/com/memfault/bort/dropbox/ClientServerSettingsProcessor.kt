package com.memfault.bort.dropbox

import android.os.DropBoxManager
import com.memfault.bort.BortJson
import com.memfault.bort.settings.FetchedSettings
import com.memfault.bort.settings.SettingsUpdateHandler
import com.memfault.bort.shared.CLIENT_SERVER_SETTINGS_UPDATE_DROPBOX_TAG
import com.memfault.bort.shared.Logger
import com.memfault.bort.time.AbsoluteTime
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlinx.serialization.SerializationException

@ContributesMultibinding(SingletonComponent::class)
class ClientServerSettingsProcessor @Inject constructor(
    private val settingsUpdateHandler: SettingsUpdateHandler,
) : EntryProcessor() {
    override val tags: List<String> = listOf(CLIENT_SERVER_SETTINGS_UPDATE_DROPBOX_TAG)

    override suspend fun process(entry: DropBoxManager.Entry, fileTime: AbsoluteTime?) {
        val content = entry.inputStream?.use { input ->
            input.reader().use { reader ->
                reader.readText()
            }
        } ?: return
        try {
            val newSettings = FetchedSettings.from(content) { BortJson }
            Logger.test("ClientServerSettingsProcessor: newSettings = $newSettings")
            settingsUpdateHandler.handleSettingsUpdate(newSettings, fromClientServer = true)
        } catch (e: SerializationException) {
            Logger.d("failed to deserialize fetched settings from client/server", e)
        }
    }
}
