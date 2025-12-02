package com.memfault.bort

import android.content.Context
import android.content.SharedPreferences
import com.memfault.bort.AppUpgrade.Companion.V0_PRE_VERSIONING
import com.memfault.bort.fileExt.deleteSilently
import com.memfault.bort.metrics.database.PREVIOUS_METRICS_DB_NAMES
import com.memfault.bort.settings.BortEnabledProvider
import com.memfault.bort.settings.CurrentSamplingConfig
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.PreferenceKeyProvider
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Handles any migration actions required between Bort versions.
 */
class AppUpgrade @Inject constructor(
    private val bortEnabledProvider: BortEnabledProvider,
    private val bortVersionPrefProvider: BortMigrationVersionPrefProvider,
    private val devMode: DevMode,
    private val currentSamplingConfig: CurrentSamplingConfig,
) {
    @Synchronized
    fun handleUpgrade(context: Context) {
        if (!bortEnabledProvider.isEnabled()) {
            return
        }

        PREVIOUS_METRICS_DB_NAMES.forEach { context.deleteDatabase(it) }

        val prevVersion = bortVersionPrefProvider.getValue()
        Logger.d("AppUpgrade: migrating from $prevVersion to $CURRENT_VERSION")
        bortVersionPrefProvider.setValue(CURRENT_VERSION)
        // Delete device props DB + metrics SharedPrefs file.
        if (prevVersion < V1_DELETE_DATABASE) {
            context.getDatabasePath("device_properties").deleteSilently()
            context.deleteSharedPreferences(METRICS_PREFERENCE_FILE_NAME)
        }
        // Set initial values for a couple of metrics.
        if (prevVersion < V2_ADD_METRICS) {
            devMode.updateMetric()
            runBlocking { currentSamplingConfig.updateMetrics(currentSamplingConfig.get()) }
        }
    }

    companion object {
        const val V0_PRE_VERSIONING = 0
        const val V1_DELETE_DATABASE = 1
        const val V2_ADD_METRICS = 2

        const val CURRENT_VERSION = V2_ADD_METRICS
    }
}

class BortMigrationVersionPrefProvider @Inject constructor(
    sharedPreferences: SharedPreferences,
) : PreferenceKeyProvider<Int>(
    sharedPreferences = sharedPreferences,
    defaultValue = V0_PRE_VERSIONING,
    preferenceKey = PREFERENCE_BORT_MIGRATOR_VERSION,
) {
    companion object {
        private const val PREFERENCE_BORT_MIGRATOR_VERSION = "bort_migrator_version"
    }
}
