package com.memfault.bort.metrics

import android.content.SharedPreferences
import com.memfault.bort.PREFERENCE_EVER_COLLECTED_METRICS
import com.memfault.bort.shared.PreferenceKeyProvider
import javax.inject.Inject

class EverCollectedMetricsPreferenceProvider @Inject constructor(
    sharedPreferences: SharedPreferences,
) : PreferenceKeyProvider<Boolean>(
    sharedPreferences = sharedPreferences,
    defaultValue = false,
    preferenceKey = PREFERENCE_EVER_COLLECTED_METRICS,
)
