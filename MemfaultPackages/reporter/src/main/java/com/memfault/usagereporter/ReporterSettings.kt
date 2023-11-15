package com.memfault.usagereporter

import android.content.SharedPreferences
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.PreferenceKeyProvider
import com.memfault.bort.shared.SetReporterSettingsRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration

interface ReporterSettings {
    val maxFileTransferStorageBytes: Long
    val maxFileTransferStorageAge: Duration
    val maxReporterTempStorageBytes: Long
    val maxReporterTempStorageAge: Duration
}

/**
 * Returns a Flow that subscribes to the [onBortEnabledFlow] if Bort is enabled, otherwise doesn't emit.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> StateFlow<SetReporterSettingsRequest>.onBortEnabledFlow(
    logName: String,
    bortEnabledFlow: suspend () -> Flow<T>,
) = map { it.bortEnabled }
    .distinctUntilChanged()
    .flatMapLatest { bortEnabled ->
        Logger.test("Listening for $logName: $bortEnabled")
        if (bortEnabled) bortEnabledFlow() else emptyFlow()
    }

@Singleton
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

    val settings: StateFlow<SetReporterSettingsRequest> = _settings

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
