package com.memfault.bort.ota.lib

import android.content.ContentResolver
import android.database.MatrixCursor
import android.net.Uri
import androidx.work.NetworkType.CONNECTED
import androidx.work.NetworkType.UNMETERED
import com.memfault.bort.shared.LegacySoftwareUpdateSettings
import com.memfault.bort.shared.SoftwareUpdateSettings
import com.memfault.bort.shared.SoftwareUpdateSettings.Companion.createCursor
import com.memfault.bort.shared.asLegacyOtaSettings
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests the various combinations of old/new bort/ota client, to ensure that the OTA settings provider always functions
 * as expected.
 *
 * Note: uses Junit4, to support Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
class BortSoftwareUpdateSettingsProviderTest {
    private var cursor: MatrixCursor? = null
    private val resolver: ContentResolver = mockk {
        every { query(any(), null, null, null, null) } answers { cursor }
    }
    private val settings = SoftwareUpdateSettings(
        deviceSerial = "deviceSerial1",
        currentVersion = "currentVersion2",
        hardwareVersion = "hardwareVersion3",
        softwareType = "softwareType4",
        updateCheckIntervalMs = 1,
        baseUrl = "baseUrl5",
        projectApiKey = "projectApiKey6",
        downloadNetworkTypeConstraint = CONNECTED,
    )

    @Test
    fun oldOtaNewBort() {
        cursor = createCursor(settings)
        val provider = LegacyProviderClient(resolver)
        assertEquals(settings.asLegacyOtaSettings(), provider.settings())
    }

    @Test
    fun oldOtaNewBort_newFormat_crashes() {
        // This is what would have happened if we did not go to all this trouble to include "legacy" formatted settings.
        cursor = legacyCreateCursorWithNewFormat(settings)
        val provider = LegacyProviderClient(resolver)
        assertNull(provider.settings())
    }

    @Test
    fun newOtaNewBort() {
        cursor = createCursor(settings)
        val provider = BortSoftwareUpdateSettingsFetcher(resolver)
        assertEquals(settings, provider.settings())
    }

    @Test
    fun newOtaOldBort() {
        cursor = legacyCreateCursor(settings.asLegacyOtaSettings())
        val provider = BortSoftwareUpdateSettingsFetcher(resolver)
        // Default value should be used for new field.
        assertEquals(settings.copy(downloadNetworkTypeConstraint = UNMETERED), provider.settings())
    }

    /**
     * Copy of the old implementation, which just adds settings as a single value (using the legacy serializer).
     */
    private fun legacyCreateCursor(settings: LegacySoftwareUpdateSettings) = MatrixCursor(arrayOf("settings")).apply {
        addRow(
            listOf(
                Json.encodeToString(LegacySoftwareUpdateSettings.serializer(), settings)
            )
        )
    }

    /**
     * Copy of the old implementation, which just adds settings as a single value.
     *
     * Uses the new data class serializer: this code never existed, but we use it to verify the "bad case" that we want
     * to avoid (new serializer, old deserializer) in oldOtaNewBort_newFormat_crashes above.
     */
    private fun legacyCreateCursorWithNewFormat(settings: SoftwareUpdateSettings) =
        MatrixCursor(arrayOf("settings")).apply {
            addRow(
                listOf(
                    Json.encodeToString(SoftwareUpdateSettings.serializer(), settings)
                )
            )
        }

    /**
     * Copy of the old implementation in the OTA client, which just reads the first value from the provider, and decodes
     * it using the standard Json object and the legacy data class deserializer.
     */
    private class LegacyProviderClient(
        private val resolver: ContentResolver
    ) {
        fun settings(): LegacySoftwareUpdateSettings? =
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
                        Json.decodeFromString(LegacySoftwareUpdateSettings.serializer(), serializedConfig)
                    } catch (ex: SerializationException) {
                        null
                    }
                } else {
                    null
                }
            }
    }
}
