package com.memfault.bort.ota.lib

import android.content.ContentResolver
import android.net.Uri
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.SoftwareUpdateSettings
import javax.inject.Inject

class BortSoftwareUpdateSettingsFetcher @Inject constructor(
    private val resolver: ContentResolver
) {
    fun settings(): SoftwareUpdateSettings? = try {
        resolver.query(
            Uri.parse("content://$BORT_SOFTWARE_UPDATE_SETTINGS_PROVIDER"),
            null,
            null,
            null,
            null
        )?.use {
            return if (it.moveToNext()) {
                // If Bort is recent enough to send new settings fields, use them. See LegacyOtaSettings.
                val column = if (it.columnCount > OTA_SETTINGS_COLUMN_FULL) OTA_SETTINGS_COLUMN_FULL
                else OTA_SETTINGS_COLUMN_LEGACY
                SoftwareUpdateSettings.deserialize(it.getString(column))
            } else {
                null
            }
        }
    } catch (e: SecurityException) {
        Logger.w("Failed to read settings from Bort", e)
        null
    }
}
