package com.memfault.bort.settings

import android.content.SharedPreferences
import android.util.Log
import com.memfault.bort.BortJson
import com.memfault.bort.PREFERENCE_FETCHED_SDK_SETTINGS
import com.memfault.bort.shared.PreferenceKeyProvider
import javax.inject.Inject
import kotlinx.serialization.SerializationException

private const val INVALID_MARKER = "__NA__"

interface ReadonlyFetchedSettingsProvider {
    fun get(): FetchedSettings
}

interface StoredSettingsPreferenceProvider : ReadonlyFetchedSettingsProvider {
    fun set(settings: FetchedSettings)
    fun reset()
}

fun interface BundledConfig : () -> String

class RealStoredSettingsPreferenceProvider @Inject constructor(
    sharedPreferences: SharedPreferences,
    private val getBundledConfig: BundledConfig,
) : StoredSettingsPreferenceProvider, PreferenceKeyProvider<String>(
    sharedPreferences = sharedPreferences,
    defaultValue = INVALID_MARKER,
    PREFERENCE_FETCHED_SDK_SETTINGS,
) {
    override fun get(): FetchedSettings {
        val content = super.getValue()
        return if (content == INVALID_MARKER) {
            FetchedSettings.from(getBundledConfig()) { BortJson }
        } else try {
            FetchedSettings.from(content) { BortJson }
        } catch (ex: SerializationException) {
            // Don't use Logger here - that could cause a stackoverflow.
            Log.d("bort", "Unable to deserialize settings, falling back to bundled config", ex)
            FetchedSettings.from(getBundledConfig()) { BortJson }
        }
    }

    override fun set(settings: FetchedSettings) {
        super.setValue(
            BortJson.encodeToString(
                FetchedSettings.FetchedSettingsContainer.serializer(),
                FetchedSettings.FetchedSettingsContainer(settings)
            )
        )
    }

    override fun reset() {
        set(FetchedSettings.from(getBundledConfig()) { BortJson })
    }
}
