package com.memfault.bort.shared

import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * Does not support nullable types.
 */
abstract class PreferenceKeyProvider<T>(
    private val sharedPreferences: SharedPreferences,
    private val defaultValue: T,
    private val preferenceKey: String,
    private val commit: Boolean = false,
) {
    init {
        when (defaultValue) {
            is Boolean, is String, is Int, is Long, is Float, is Set<*> -> {
            }
            else -> throw IllegalArgumentException("Unsupported type $defaultValue")
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun setValue(newValue: T): Unit = with(sharedPreferences.edit()) {
        when (newValue) {
            is Boolean -> putBoolean(preferenceKey, newValue)
            is String -> putString(preferenceKey, newValue)
            is Int -> putInt(preferenceKey, newValue)
            is Long -> putLong(preferenceKey, newValue)
            is Float -> putFloat(preferenceKey, newValue)
            is Set<*> -> putStringSet(preferenceKey, ensureStringSet(newValue))
            else -> throw IllegalArgumentException("Unsupported type $newValue")
        }
        if (commit) commit() else apply()
    }

    @Suppress("UNCHECKED_CAST")
    fun getValue(): T = with(sharedPreferences) {
        return when (defaultValue) {
            is Boolean -> getBoolean(preferenceKey, defaultValue) as T
            is String -> getString(preferenceKey, defaultValue) as T
            is Int -> getInt(preferenceKey, defaultValue) as T
            is Long -> getLong(preferenceKey, defaultValue) as T
            is Float -> getFloat(preferenceKey, defaultValue) as T
            is Set<*> -> getStringSet(preferenceKey, ensureStringSet(defaultValue)) as T
            else -> throw IllegalArgumentException("Unsupported type $defaultValue")
        }
    }

    /**
     * Returns a [Flow] of the value of this preference, which emits when it changes.
     *
     * Note that this flow is [conflate]d, so it always buffers the latest value that's emitted, to avoid dropping
     * emissions due to slow collectors, since we use [trySend]. You will need to build another mechanism if you need
     * every change.
     *
     * Note that this method does not emit immediately upon subscription.
     */
    fun valueChangedFlow(): Flow<T> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key: String? ->
            // Key can be null when preferences are cleared on API 30+.
            if (key == preferenceKey || key == null) {
                trySend(getValue())
            }
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
        .conflate()

    @Suppress("UNCHECKED_CAST")
    private fun ensureStringSet(value: Set<*>): Set<String> {
        value.firstOrNull { it !is String }?.let { element ->
            throw IllegalArgumentException("Unsupported type $element in Set")
        }
        return value as Set<String>
    }

    fun remove() {
        sharedPreferences.edit()
            .remove(preferenceKey)
            .apply()
    }
}
