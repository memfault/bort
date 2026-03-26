package com.memfault.bort.battery

import android.content.SharedPreferences
import com.memfault.bort.shared.SerializedCachedPreferenceKeyProvider
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class LastBatteryCycleCountState(
    val cycleCount: Int? = null,
)

interface LastBatteryCycleCountStateProvider {
    var state: LastBatteryCycleCountState
}

@Singleton
@ContributesBinding(SingletonComponent::class, boundType = LastBatteryCycleCountStateProvider::class)
class RealLastBatteryCycleCountStateProvider @Inject constructor(
    sharedPreferences: SharedPreferences,
) : LastBatteryCycleCountStateProvider,
    SerializedCachedPreferenceKeyProvider<LastBatteryCycleCountState>(
        sharedPreferences = sharedPreferences,
        defaultValue = LastBatteryCycleCountState(),
        serializer = LastBatteryCycleCountState.serializer(),
        preferenceKey = "com.memfault.preference.LAST_BATTERY_CYCLE_COUNT_STATE",
    )
