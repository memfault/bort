package com.memfault.bort.shared

import android.content.SharedPreferences
import kotlinx.serialization.KSerializer

abstract class SerializedCachedPreferenceKeyProvider<T>(
    sharedPreferences: SharedPreferences,
    defaultValue: T,
    private val serializer: KSerializer<T>,
    preferenceKey: String,
) : CachedPreferenceKeyProvider<String>(
    sharedPreferences,
    BortSharedJson.encodeToString(serializer, defaultValue),
    preferenceKey,
) {
    var state: T
        get() = BortSharedJson.decodeFromString(serializer, super.getValue())
        set(value) {
            super.setValue(BortSharedJson.encodeToString(serializer, value))
        }
}
