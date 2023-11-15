package com.memfault.bort.settings

import android.content.SharedPreferences
import android.util.Log
import com.memfault.bort.BortJson
import com.memfault.bort.PREFERENCE_FETCHED_SDK_SETTINGS
import com.memfault.bort.shared.PreferenceKeyProvider
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.SerializationException
import javax.inject.Inject

private const val INVALID_MARKER = "__NA__"

interface ReadonlyFetchedSettingsProvider {
    fun get(): FetchedSettings
}

interface StoredSettingsPreferenceProvider : ReadonlyFetchedSettingsProvider {
    fun set(settings: FetchedSettings)
    fun reset()
}

fun interface BundledConfig : () -> String

@ContributesBinding(SingletonComponent::class, boundType = ReadonlyFetchedSettingsProvider::class)
@ContributesBinding(SingletonComponent::class, boundType = StoredSettingsPreferenceProvider::class)
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
        } else {
            try {
                FetchedSettings.from(content) { BortJson }
            } catch (ex: SerializationException) {
                // Don't use Logger here - that could cause a stackoverflow.
                Log.d("bort", "Unable to deserialize settings, falling back to bundled config", ex)
                FetchedSettings.from(getBundledConfig()) { BortJson }
            }
        }
    }

    override fun set(settings: FetchedSettings) {
        super.setValue(settings.toJson())
    }

    override fun reset() {
        set(FetchedSettings.from(getBundledConfig()) { BortJson })
    }
}

fun FetchedSettings.toJson() = BortJson.encodeToString(
    FetchedSettings.FetchedSettingsContainer.serializer(),
    FetchedSettings.FetchedSettingsContainer(this),
)
