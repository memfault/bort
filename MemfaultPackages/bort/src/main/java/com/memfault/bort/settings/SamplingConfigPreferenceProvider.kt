package com.memfault.bort.settings

import android.content.SharedPreferences
import com.memfault.bort.PREFERENCE_SAMPLING_CONFIG
import com.memfault.bort.settings.SamplingConfig.Companion.decodeFromString
import com.memfault.bort.settings.SamplingConfig.Companion.toJson
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.PreferenceKeyProvider
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.SerializationException
import javax.inject.Inject

interface SamplingConfigPreferenceProvider {
    fun set(config: SamplingConfig)
    fun get(): SamplingConfig
}

@ContributesBinding(SingletonComponent::class, boundType = SamplingConfigPreferenceProvider::class)
class RealSamplingConfigPreferenceProvider @Inject constructor(
    sharedPreferences: SharedPreferences,
) : SamplingConfigPreferenceProvider, PreferenceKeyProvider<String>(
    sharedPreferences = sharedPreferences,
    defaultValue = INVALID_MARKER,
    preferenceKey = PREFERENCE_SAMPLING_CONFIG,
) {
    override fun set(config: SamplingConfig) {
        setValue(config.toJson())
    }

    override fun get(): SamplingConfig {
        val content = super.getValue()
        return if (content == INVALID_MARKER) {
            SamplingConfig()
        } else {
            try {
                decodeFromString(content)
            } catch (e: SerializationException) {
                Logger.w("Unable to deserialize sampling config: $content", e)
                SamplingConfig()
            }
        }
    }

    companion object {
        private const val INVALID_MARKER = "__NA__"
    }
}
