package com.memfault.bort.settings

import android.content.SharedPreferences
import com.memfault.bort.clientserver.MarFileHoldingArea
import com.memfault.bort.metrics.BuiltinMetricsStore
import com.memfault.bort.metrics.SETTINGS_CHANGED
import com.memfault.bort.receivers.DropBoxEntryAddedReceiver
import com.memfault.bort.requester.PeriodicWorkRequester.PeriodicWorkManager
import com.memfault.bort.settings.FetchedDeviceConfigContainer.Companion.asSamplingConfig
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.PreferenceKeyProvider
import java.time.Instant
import javax.inject.Inject

class SettingsUpdateHandler @Inject constructor(
    private val settingsProvider: SettingsProvider,
    private val storedSettingsPreferenceProvider: StoredSettingsPreferenceProvider,
    private val settingsUpdateCallback: SettingsUpdateCallback,
    private val metrics: BuiltinMetricsStore,
    private val everFetchedSettingsPreferenceProvider: EverFetchedSettingsPreferenceProvider,
    private val currentSamplingConfig: CurrentSamplingConfig,
    private val marFileHoldingArea: MarFileHoldingArea,
    private val periodicWorkManager: PeriodicWorkManager,
    private val continuousLoggingController: ContinuousLoggingController,
    private val dropBoxEntryAddedReceiver: DropBoxEntryAddedReceiver,
) {
    suspend fun handleDeviceConfig(deviceConfig: DecodedDeviceConfig) {
        // Stored first: the settings update reconfigures collection, which reads the current sampling config.
        val samplingChanged = deviceConfig.memfault?.sampling?.let { newSampling ->
            handleSamplingConfig(
                newSamplingConfig = newSampling.asSamplingConfig(deviceConfig.revision),
                completedRevision = deviceConfig.completedRevision,
                dataUploadStartDate = deviceConfig.memfault.dataUploadStartDate,
            )
        } ?: false

        val settingsChanged = deviceConfig.memfault?.bort?.sdkSettings?.let { newSettings ->
            handleSettingsUpdate(new = newSettings)
        } ?: false

        // Skipped when the settings also changed, because SettingsUpdateCallback has already done it.
        if (samplingChanged && !settingsChanged) {
            periodicWorkManager.restartTasksAfterSamplingConfigChange()
            continuousLoggingController.configureContinuousLogging()
            dropBoxEntryAddedReceiver.initialize()
        }
    }

    /** Returns whether the settings changed. */
    private suspend fun handleSettingsUpdate(new: FetchedSettings): Boolean {
        everFetchedSettingsPreferenceProvider.setValue(true)
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
        return changed
    }

    /** Returns whether the sampling config changed. */
    private suspend fun handleSamplingConfig(
        newSamplingConfig: SamplingConfig,
        completedRevision: Int?,
        dataUploadStartDate: Instant?,
    ): Boolean {
        val existingSamplingConfig = currentSamplingConfig.get()
        currentSamplingConfig.update(newSamplingConfig)

        if (newSamplingConfig != existingSamplingConfig) {
            // If we've received a new sampling config, apply it.
            marFileHoldingArea.handleSamplingConfigChange(newSamplingConfig, dataUploadStartDate)
            // Add a new mar entry, confirming that we processed this config revision.
            marFileHoldingArea.addDeviceConfigMarEntry(newSamplingConfig.revision)
            return true
        } else if (completedRevision != null && completedRevision != existingSamplingConfig.revision) {
            // Otherwise, resend a device-config revision if the revision is different.
            // Assume that the server has not reported receiving it.
            marFileHoldingArea.addDeviceConfigMarEntry(existingSamplingConfig.revision)
        }
        return false
    }
}

class EverFetchedSettingsPreferenceProvider @Inject constructor(
    sharedPreferences: SharedPreferences,
) : PreferenceKeyProvider<Boolean>(sharedPreferences, defaultValue = false, preferenceKey = PREF_KEY) {
    companion object {
        private const val PREF_KEY = "ever_fetched_settings"
    }
}
