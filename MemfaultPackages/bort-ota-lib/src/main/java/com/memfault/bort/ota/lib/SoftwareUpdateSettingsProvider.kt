package com.memfault.bort.ota.lib

import android.content.ContentResolver
import android.net.Uri
import com.memfault.bort.shared.SoftwareUpdateSettings
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

interface SoftwareUpdateSettingsProvider {
    fun settings(): SoftwareUpdateSettings?
}

class BortSoftwareUpdateSettingsProvider(
    private val resolver: ContentResolver
) : SoftwareUpdateSettingsProvider {
    override fun settings(): SoftwareUpdateSettings? =
        resolver.query(
            Uri.parse("content://$BORT_SOFTWARE_UPDATE_SETTINGS_PROVIDER"),
            null,
            null,
            null,
            null
        )?.use {
            return if (it.moveToNext()) {
                val serializedConfig = it.getString(0)
                try {
                    Json.decodeFromString(SoftwareUpdateSettings.serializer(), serializedConfig)
                } catch (ex: SerializationException) {
                    ex.printStackTrace()
                    null
                }
            } else {
                null
            }
        }
}
