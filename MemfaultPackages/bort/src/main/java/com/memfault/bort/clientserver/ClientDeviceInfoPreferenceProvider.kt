package com.memfault.bort.clientserver

import android.content.SharedPreferences
import com.memfault.bort.PREFERENCE_CLIENT_DEVICE_INFO_CONFIG
import com.memfault.bort.settings.DeviceConfigUpdateService.DeviceInfo
import com.memfault.bort.settings.toJson
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.PreferenceKeyProvider
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlinx.serialization.SerializationException

interface ClientDeviceInfoPreferenceProvider {
    fun set(config: DeviceInfo)
    fun get(): DeviceInfo?
}

@ContributesBinding(SingletonComponent::class, boundType = ClientDeviceInfoPreferenceProvider::class)
class RealClientDeviceInfoPreferenceProvider @Inject constructor(
    sharedPreferences: SharedPreferences,
) : ClientDeviceInfoPreferenceProvider, PreferenceKeyProvider<String>(
    sharedPreferences = sharedPreferences,
    defaultValue = INVALID_MARKER,
    preferenceKey = PREFERENCE_CLIENT_DEVICE_INFO_CONFIG,
) {
    override fun set(config: DeviceInfo) {
        setValue(config.toJson())
    }

    override fun get(): DeviceInfo? {
        val content = super.getValue()
        return if (content == INVALID_MARKER) {
            null
        } else try {
            DeviceInfo.from(content)
        } catch (e: SerializationException) {
            Logger.w("Unable to deserialize sampling config: $content", e)
            null
        }
    }

    companion object {
        private const val INVALID_MARKER = "__NA__"
    }
}
