package com.memfault.usagereporter

import android.content.SharedPreferences
import com.memfault.bort.shared.LogLevel
import com.memfault.bort.shared.LogLevel.TEST
import com.memfault.bort.shared.PreferenceKeyProvider
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

interface LogLevelPreferenceProvider {
    fun setLogLevel(logLevel: LogLevel)
    fun getLogLevel(): LogLevel
}

@ContributesBinding(SingletonComponent::class, boundType = LogLevelPreferenceProvider::class)
class RealLogLevelPreferenceProvider
@Inject constructor(
    sharedPreferences: SharedPreferences,
) : LogLevelPreferenceProvider, PreferenceKeyProvider<Int>(
    sharedPreferences = sharedPreferences,
    // Defaults to test (just until updated level is received + persisted) - otherwise we might miss logs when
    // restarting the process during E2E tests.
    defaultValue = TEST.level,
    preferenceKey = PREFERENCE_LOG_LEVEL,
) {
    override fun setLogLevel(logLevel: LogLevel) {
        super.setValue(logLevel.level)
    }

    override fun getLogLevel(): LogLevel =
        LogLevel.fromInt(super.getValue()) ?: TEST
}
