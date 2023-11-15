package com.memfault.bort.metrics

import android.content.SharedPreferences
import com.memfault.bort.PREFERENCE_BATTERYSTATS_SUMMARY_DATA
import com.memfault.bort.parsers.BatteryStatsSummaryParser.BatteryStatsSummary
import com.memfault.bort.parsers.BatteryStatsSummaryParser.BatteryStatsSummary.Companion.toJson
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.PreferenceKeyProvider
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.SerializationException
import javax.inject.Inject

interface BatteryStatsSummaryProvider {
    fun get(): BatteryStatsSummary?
    fun set(summary: BatteryStatsSummary)
}

@ContributesBinding(SingletonComponent::class, boundType = BatteryStatsSummaryProvider::class)
class RealBatteryStatsSummaryProvider @Inject constructor(
    sharedPreferences: SharedPreferences,
) : BatteryStatsSummaryProvider, PreferenceKeyProvider<String>(
    sharedPreferences = sharedPreferences,
    defaultValue = INVALID_MARKER,
    preferenceKey = PREFERENCE_BATTERYSTATS_SUMMARY_DATA,
) {
    override fun get(): BatteryStatsSummary? {
        val content = super.getValue()
        return if (content == INVALID_MARKER) {
            null
        } else {
            try {
                BatteryStatsSummary.decodeFromString(content)
            } catch (e: SerializationException) {
                Logger.w("Unable to deserialize sampling config: $content", e)
                null
            }
        }
    }

    override fun set(summary: BatteryStatsSummary) {
        setValue(summary.toJson())
    }

    companion object {
        private const val INVALID_MARKER = "__NA__"
    }
}
