package com.memfault.bort

import android.content.SharedPreferences
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

internal inline fun <reified T> SharedPreferences.getJson(key: String): T? =
    getString(key, null)?.let {
        try {
            BortJson.decodeFromString(it)
        } catch (e: Exception) {
            null
        }
    }

internal inline fun <reified T> SharedPreferences.Editor.putJson(key: String, value: T): SharedPreferences.Editor =
    putString(key, BortJson.encodeToString(value))
