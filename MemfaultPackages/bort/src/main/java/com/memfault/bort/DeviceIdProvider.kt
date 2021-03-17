package com.memfault.bort

import android.content.SharedPreferences
import com.memfault.bort.shared.PreferenceKeyProvider
import java.util.UUID

interface DeviceIdProvider {
    fun deviceId(): String
}

class RandomUuidDeviceIdProvider(
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
