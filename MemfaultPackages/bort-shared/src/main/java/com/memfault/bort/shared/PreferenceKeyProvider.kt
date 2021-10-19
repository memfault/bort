package com.memfault.bort.shared

import android.content.SharedPreferences

/**
 * Does not support nullable types.
 */
abstract class PreferenceKeyProvider<T>(
    private val sharedPreferences: SharedPreferences,
    private val defaultValue: T,
    private val preferenceKey: String
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
        apply()
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
