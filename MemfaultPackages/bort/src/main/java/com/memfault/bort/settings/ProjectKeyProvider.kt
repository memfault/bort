package com.memfault.bort.settings

import android.content.SharedPreferences
import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.clientserver.MarFileHoldingArea
import com.memfault.bort.requester.cleanupFiles
import com.memfault.bort.shared.BuildConfig
import com.memfault.bort.shared.CachedPreferenceKeyProvider
import com.memfault.bort.shared.Logger
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Wrapper around the current project key. Use this class instead of BuildConfig.MEMFAULT_PROJECT_API_KEY.
 */
open class ProjectKeyProvider @Inject constructor(
    private val preferenceProvider: ProjectKeyOverridePreferenceProvider,
    private val marFileHoldingArea: Provider<MarFileHoldingArea>,
    private val temporaryFileFactory: TemporaryFileFactory,
) {
    var projectKey: String
        get() = if (BuildConfig.ALLOW_PROJECT_KEY_CHANGE) {
            preferenceProvider.getValue()
        } else {
            BuildConfig.MEMFAULT_PROJECT_API_KEY
        }
        set(newKey) {
            if (!BuildConfig.ALLOW_PROJECT_KEY_CHANGE) {
                Logger.w("Changing Bort project not permitted")
                return
            }
            if (newKey == projectKey) return
            Logger.i("Changing Bort project key from broadcast")
            preferenceProvider.setValue(newKey)
            // Delete all pending mar files (they use the old project key).
            marFileHoldingArea.get().deleteAllFiles()
            // Also delete any pending file uploads (including mar files).
            temporaryFileFactory.temporaryFileDirectory?.let {
                cleanupFiles(dir = it, maxDirStorageBytes = 0)
            }
        }

    fun reset() {
        Logger.i("Removing project key override")
        preferenceProvider.remove()
    }
}

@Singleton
class ProjectKeyOverridePreferenceProvider @Inject constructor(sharedPreferences: SharedPreferences) :
    CachedPreferenceKeyProvider<String>(
        sharedPreferences = sharedPreferences,
        defaultValue = BuildConfig.MEMFAULT_PROJECT_API_KEY,
        preferenceKey = "project-key-override",
    )
