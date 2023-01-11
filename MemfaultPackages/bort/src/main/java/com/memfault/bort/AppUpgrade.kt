package com.memfault.bort

import android.content.Context
import android.content.SharedPreferences
import com.memfault.bort.AppUpgrade.Companion.V0_PRE_VERSIONING
import com.memfault.bort.fileExt.deleteSilently
import com.memfault.bort.shared.PreferenceKeyProvider
import javax.inject.Inject

/**
 * Handles any migration actions required between Bort versions.
 */
class AppUpgrade @Inject constructor(
    private val bortVersionPrefProvider: BortMigrationVersionPrefProvider,
) {
    fun handleUpgrade(context: Context) {
        val prevVersion = bortVersionPrefProvider.getValue()
        bortVersionPrefProvider.setValue(CURRENT_VERSION)
        // Delete device props DB + metrics SharedPrefs file.
        if (prevVersion < V1_DELETE_DATABASE) {
            context.getDatabasePath("device_properties").deleteSilently()
            context.deleteSharedPreferences(METRICS_PREFERENCE_FILE_NAME)
        }
    }

    companion object {
        const val V0_PRE_VERSIONING = 0
        const val V1_DELETE_DATABASE = 1

        const val CURRENT_VERSION = V1_DELETE_DATABASE
    }
}

class BortMigrationVersionPrefProvider @Inject constructor(
    sharedPreferences: SharedPreferences,
) : PreferenceKeyProvider<Int>(
    sharedPreferences = sharedPreferences,
    defaultValue = V0_PRE_VERSIONING,
    preferenceKey = PREFERENCE_BORT_MIGRATOR_VERSION
) {
    companion object {
        private const val PREFERENCE_BORT_MIGRATOR_VERSION = "bort_migrator_version"
    }
}
