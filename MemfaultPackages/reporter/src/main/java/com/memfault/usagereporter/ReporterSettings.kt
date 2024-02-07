package com.memfault.usagereporter

import android.content.SharedPreferences
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.PreferenceKeyProvider
import com.memfault.bort.shared.SetReporterSettingsRequest
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration

interface ReporterSettings {
    val maxFileTransferStorageBytes: Long
    val maxFileTransferStorageAge: Duration
    val maxReporterTempStorageBytes: Long
    val maxReporterTempStorageAge: Duration

    val settings: StateFlow<SetReporterSettingsRequest>
}

@Singleton
@ContributesBinding(SingletonComponent::class, boundType = ReporterSettings::class)
class ReporterSettingsPreferenceProvider
@Inject constructor(
    sharedPreferences: SharedPreferences,
) : ReporterSettings, PreferenceKeyProvider<String>(
    sharedPreferences = sharedPreferences,
    defaultValue = "",
    preferenceKey = "reporter_settings",
) {
    // Cache the value, so that we don't have to read from disk every time settings are queried.
    private val _settings = MutableStateFlow(
        SetReporterSettingsRequest.fromJson(getValue())
            // Use default values if never stored
            ?: SetReporterSettingsRequest(),
    )

    override val settings: StateFlow<SetReporterSettingsRequest> = _settings

    fun set(settings: SetReporterSettingsRequest) {
        if (_settings.value == settings) return
        Logger.test("Update reporter settings: $settings")
        setValue(settings.toJson())
        _settings.value = settings
    }

    override val maxFileTransferStorageBytes get() = settings.value.maxFileTransferStorageBytes
    override val maxFileTransferStorageAge get() = settings.value.maxFileTransferStorageAge.duration
    override val maxReporterTempStorageBytes get() = settings.value.maxReporterTempStorageBytes
    override val maxReporterTempStorageAge get() = settings.value.maxReporterTempStorageAge.duration
}
