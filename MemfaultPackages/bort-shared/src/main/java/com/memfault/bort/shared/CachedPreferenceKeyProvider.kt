package com.memfault.bort.shared

import android.content.SharedPreferences

/**
 * A [PreferenceKeyProvider] which caches the value in memory, for use by consumers who are expected to check the value
 * often.
 *
 * Be sure to make the implementation a Singleton, so that cached state is correct.
 */
abstract class CachedPreferenceKeyProvider<T>(
    sharedPreferences: SharedPreferences,
    defaultValue: T,
    preferenceKey: String,
) {
    private val pref = object : PreferenceKeyProvider<T>(sharedPreferences, defaultValue, preferenceKey) {}
    private var cachedValue: T? = null

    @Synchronized
    fun setValue(newValue: T) {
        cachedValue = newValue
        pref.setValue(newValue)
    }

    @Synchronized
    fun getValue(): T = cachedValue ?: pref.getValue().also { cachedValue = it }

    @Synchronized
    fun remove() {
        cachedValue = null
        pref.remove()
    }
}
