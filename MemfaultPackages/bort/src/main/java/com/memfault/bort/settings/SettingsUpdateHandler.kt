package com.memfault.bort.settings

import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.clientserver.CachedClientServerMode
import com.memfault.bort.clientserver.LinkedDeviceFileSender
import com.memfault.bort.metrics.BuiltinMetricsStore
import com.memfault.bort.metrics.SETTINGS_CHANGED
import com.memfault.bort.shared.CLIENT_SERVER_SETTINGS_UPDATE_DROPBOX_TAG
import com.memfault.bort.shared.ClientServerMode.SERVER
import com.memfault.bort.shared.Logger
import javax.inject.Inject

class SettingsUpdateHandler @Inject constructor(
    private val settingsProvider: SettingsProvider,
    private val storedSettingsPreferenceProvider: StoredSettingsPreferenceProvider,
    private val settingsUpdateCallback: SettingsUpdateCallback,
    private val metrics: BuiltinMetricsStore,
    private val cachedClientServerMode: CachedClientServerMode,
    private val linkedDeviceFileSender: LinkedDeviceFileSender,
    private val temporaryFileFactory: TemporaryFileFactory,
) {
    suspend fun handleSettingsUpdate(new: FetchedSettings, fromClientServer: Boolean = false) {
        // The !fromClientServer check is present to avoid an infinite loop in E2E tests, where a single bort is running
        // configured as server.
        if (cachedClientServerMode.get() == SERVER && !fromClientServer) {
            // Forward settings to client device.
            temporaryFileFactory.createTemporaryFile("settings", "json").useFile { file, preventDeletion ->
                preventDeletion()
                file.writeText(new.toJson())
                linkedDeviceFileSender.sendFileToLinkedDevice(file, CLIENT_SERVER_SETTINGS_UPDATE_DROPBOX_TAG)
            }
        }

        val old = storedSettingsPreferenceProvider.get()
        val changed = old != new

        if (changed) {
            metrics.increment(SETTINGS_CHANGED)
            storedSettingsPreferenceProvider.set(new)

            // Force a reload on the next settings read
            settingsProvider.invalidate()

            settingsUpdateCallback.onSettingsUpdated(settingsProvider, FetchedSettingsUpdate(old = old, new = new))
        }

        Logger.test("Settings updated successfully (changed=$changed)")
    }
}
