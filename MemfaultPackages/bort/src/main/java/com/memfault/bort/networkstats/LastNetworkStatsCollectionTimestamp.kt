package com.memfault.bort.networkstats

import android.content.SharedPreferences
import com.memfault.bort.shared.PreferenceKeyProvider
import javax.inject.Inject

class LastNetworkStatsCollectionTimestamp
@Inject constructor(
    sharedPreferences: SharedPreferences,
) : PreferenceKeyProvider<Long>(
    sharedPreferences = sharedPreferences,
    defaultValue = 0,
    preferenceKey = PREFERENCE_LAST_NETWORKS_STATS_COLLECTION_TIMESTAMP,
) {
    companion object {
        private const val PREFERENCE_LAST_NETWORKS_STATS_COLLECTION_TIMESTAMP =
            "PREFERENCE_LAST_NETWORKS_STATS_COLLECTION_TIMESTAMP"
    }
}
