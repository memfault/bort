package com.memfault.bort.battery

import android.content.SharedPreferences
import com.memfault.bort.shared.SerializedCachedPreferenceKeyProvider
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class LastBatteryVitalsState(
    val isCharging: Boolean = false,
    val level: Double = -1.0,
)

interface LastBatteryVitalsStateProvider {
    var state: LastBatteryVitalsState
}

@Singleton
@ContributesBinding(SingletonComponent::class, boundType = LastBatteryVitalsStateProvider::class)
class RealLastBatteryVitalsStateProvider @Inject constructor(
    sharedPreferences: SharedPreferences,
) : LastBatteryVitalsStateProvider, SerializedCachedPreferenceKeyProvider<LastBatteryVitalsState>(
    sharedPreferences = sharedPreferences,
    defaultValue = LastBatteryVitalsState(),
    serializer = LastBatteryVitalsState.serializer(),
    preferenceKey = "com.memfault.preference.LAST_BATTERY_VITALS_STATE",
)
