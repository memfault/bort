package com.memfault.usagereporter.metrics

import android.content.SharedPreferences
import com.memfault.bort.shared.PreferenceKeyProvider
import com.memfault.usagereporter.PREFERENCE_METRICS_COLLECTION_PERIOD_MS
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.toDuration

class ReporterMetricsPreferenceProvider(
    sharedPreferences: SharedPreferences
) : PreferenceKeyProvider<Long>(
    sharedPreferences = sharedPreferences,
    defaultValue = 0,
    preferenceKey = PREFERENCE_METRICS_COLLECTION_PERIOD_MS
) {
    fun setValue(duration: Duration) {
        setValue(duration.inWholeMilliseconds)
    }

    fun getDurationValue() = getValue().toDuration(TimeUnit.MILLISECONDS)
}
