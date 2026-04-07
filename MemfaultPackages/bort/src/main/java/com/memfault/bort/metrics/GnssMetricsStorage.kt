package com.memfault.bort.metrics

import android.content.SharedPreferences
import com.memfault.bort.boot.LinuxBootId
import com.memfault.bort.shared.SerializedCachedPreferenceKeyProvider
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.Serializable
import javax.inject.Inject

interface GnssMetricsStorage {
    var state: GnssMetricsState

    /** Stores cumulative metrics as the new baseline and returns the interval deltas since the last call. */
    fun update(metrics: GnssKpiMetrics, bootId: String): GnssMetricsState {
        val baseline = state.let { if (it.bootId == bootId) it else GnssMetricsState.empty(bootId) }
        state = GnssMetricsState(
            bootId = bootId,
            cn0AboveThresholdTimeMin = metrics.cn0AboveThresholdTimeMin,
            cn0BelowThresholdTimeMin = metrics.cn0BelowThresholdTimeMin,
            energyConsumedMah = metrics.energyConsumedMah,
        )
        return GnssMetricsState(
            bootId = bootId,
            cn0AboveThresholdTimeMin = cumulativeDelta(
                metrics.cn0AboveThresholdTimeMin,
                baseline.cn0AboveThresholdTimeMin,
            ),
            cn0BelowThresholdTimeMin = cumulativeDelta(
                metrics.cn0BelowThresholdTimeMin,
                baseline.cn0BelowThresholdTimeMin,
            ),
            energyConsumedMah = cumulativeDelta(metrics.energyConsumedMah, baseline.energyConsumedMah),
        )
    }
}

private fun cumulativeDelta(current: Double?, previous: Double?): Double? = when {
    current == null -> null
    previous == null -> current
    else -> (current - previous).coerceAtLeast(0.0)
}

@Serializable
data class GnssMetricsState(
    val bootId: String,
    val cn0AboveThresholdTimeMin: Double?,
    val cn0BelowThresholdTimeMin: Double?,
    val energyConsumedMah: Double?,
) {
    companion object {
        fun empty(bootId: String) = GnssMetricsState(
            bootId = bootId,
            cn0AboveThresholdTimeMin = null,
            cn0BelowThresholdTimeMin = null,
            energyConsumedMah = null,
        )
    }
}

@ContributesBinding(SingletonComponent::class, boundType = GnssMetricsStorage::class)
class RealGnssMetricsStorage @Inject constructor(
    sharedPreferences: SharedPreferences,
    readBootId: LinuxBootId,
) : GnssMetricsStorage, SerializedCachedPreferenceKeyProvider<GnssMetricsState>(
    sharedPreferences,
    GnssMetricsState.empty(readBootId()),
    GnssMetricsState.serializer(),
    "GNSS_METRICS",
)
