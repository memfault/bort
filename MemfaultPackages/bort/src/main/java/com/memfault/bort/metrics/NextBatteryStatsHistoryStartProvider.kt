package com.memfault.bort.metrics

import android.content.SharedPreferences
import com.memfault.bort.PREFERENCE_NEXT_BATTERYSTATS_HISTORY_START_TIME
import com.memfault.bort.shared.PreferenceKeyProvider
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/**
 * Provides the next --history-start value to use with batterystats.
 */
interface NextBatteryStatsHistoryStartProvider {
    var historyStart: Long
}

@ContributesBinding(SingletonComponent::class, boundType = NextBatteryStatsHistoryStartProvider::class)
class RealNextBatteryStatsHistoryStartProvider @Inject constructor(
    sharedPreferences: SharedPreferences
) : NextBatteryStatsHistoryStartProvider, PreferenceKeyProvider<Long>(
    sharedPreferences = sharedPreferences,
    defaultValue = 0,
    preferenceKey = PREFERENCE_NEXT_BATTERYSTATS_HISTORY_START_TIME,
) {
    override var historyStart
        get() = super.getValue()
        set(value) = super.setValue(value)
}
