package com.memfault.bort.settings

import com.memfault.bort.metrics.BuiltinMetricsStore
import com.memfault.bort.metrics.SETTINGS_CHANGED
import com.memfault.bort.shared.Logger
import javax.inject.Inject

class SettingsUpdateHandler @Inject constructor(
    private val settingsProvider: SettingsProvider,
    private val storedSettingsPreferenceProvider: StoredSettingsPreferenceProvider,
    private val settingsUpdateCallback: SettingsUpdateCallback,
    private val metrics: BuiltinMetricsStore,
) {
    suspend fun handleSettingsUpdate(new: FetchedSettings) {
        val old = storedSettingsPreferenceProvider.get()
        val changed = old != new

        if (changed) {
            metrics.increment(SETTINGS_CHANGED)
            storedSettingsPreferenceProvider.set(new)

            // Force a reload on the next settings read
            settingsProvider.invalidate()

            settingsUpdateCallback.onSettingsUpdated(settingsProvider, FetchedSettingsUpdate(old = old, new = new))
        }

        // Always do this even if no change, just in case it failed on enable/boot.
        reloadCustomEventConfigFrom(settingsProvider.structuredLogSettings)

        Logger.test("Settings updated successfully (changed=$changed)")
    }
}
