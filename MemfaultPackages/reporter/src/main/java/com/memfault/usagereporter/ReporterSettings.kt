package com.memfault.usagereporter

import android.content.SharedPreferences
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.PreferenceKeyProvider
import com.memfault.bort.shared.SetReporterSettingsRequest
import kotlin.time.Duration

interface ReporterSettings {
    val maxFileTransferStorageBytes: Long
    val maxFileTransferStorageAge: Duration
    val maxReporterTempStorageBytes: Long
    val maxReporterTempStorageAge: Duration
}

class ReporterSettingsPreferenceProvider(
    sharedPreferences: SharedPreferences,
) : ReporterSettings, PreferenceKeyProvider<String>(
    sharedPreferences = sharedPreferences,
    defaultValue = "",
    preferenceKey = "reporter_settings",
) {
    // Cache the value, so that we don't have to read from disk every time settings are queried.
    private var cachedSettings: SetReporterSettingsRequest? = null

    fun get(): SetReporterSettingsRequest =
        cachedSettings ?: (
            SetReporterSettingsRequest.fromJson(getValue())
                // Use default values if never stored
                ?: SetReporterSettingsRequest()
            )
            .also { cachedSettings = it }

    fun set(settings: SetReporterSettingsRequest) {
        if (get() == settings) return
        cachedSettings = settings
        Logger.test("Update reporter settings: $settings")
        setValue(settings.toJson())
    }

    override val maxFileTransferStorageBytes get() = get().maxFileTransferStorageBytes
    override val maxFileTransferStorageAge get() = get().maxFileTransferStorageAge.duration
    override val maxReporterTempStorageBytes get() = get().maxReporterTempStorageBytes
    override val maxReporterTempStorageAge get() = get().maxReporterTempStorageAge.duration
}
