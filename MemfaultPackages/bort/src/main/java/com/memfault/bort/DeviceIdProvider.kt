package com.memfault.bort

import android.content.SharedPreferences
import com.memfault.bort.shared.PreferenceKeyProvider
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import javax.inject.Inject

interface DeviceIdProvider {
    fun deviceId(): String
}

@ContributesBinding(SingletonComponent::class, boundType = DeviceIdProvider::class)
class RandomUuidDeviceIdProvider @Inject constructor(
    sharedPreferences: SharedPreferences
) : DeviceIdProvider, PreferenceKeyProvider<String>(
    sharedPreferences = sharedPreferences,
    defaultValue = "",
    preferenceKey = PREFERENCE_DEVICE_ID
) {
    init {
        if (deviceId() == "") {
            super.setValue(UUID.randomUUID().toString())
        }
    }

    override fun deviceId(): String = super.getValue()
}
