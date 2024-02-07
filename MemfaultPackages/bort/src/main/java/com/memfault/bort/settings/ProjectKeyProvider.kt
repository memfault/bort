package com.memfault.bort.settings

import android.content.SharedPreferences
import com.memfault.bort.ProjectKeySyspropName
import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.clientserver.MarFileHoldingArea
import com.memfault.bort.requester.cleanupFiles
import com.memfault.bort.settings.ProjectKeyChangeSource.BROADCAST
import com.memfault.bort.shared.CachedPreferenceKeyProvider
import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

fun interface BuiltInProjectKey : () -> String
fun interface AllowProjectKeyChange : () -> Boolean

/**
 * Wrapper around the current project key. Use this class instead of BuildConfig.MEMFAULT_PROJECT_API_KEY.
 */
open class ProjectKeyProvider @Inject constructor(
    private val preferenceProvider: ProjectKeyOverridePreferenceProvider,
    private val marFileHoldingArea: Provider<MarFileHoldingArea>,
    private val temporaryFileFactory: TemporaryFileFactory,
    private val projectKeySyspropName: ProjectKeySyspropName,
    private val builtInProjectKey: BuiltInProjectKey,
    private val allowProjectKeyChange: AllowProjectKeyChange,
) {
    val projectKey: String
        get() = if (allowProjectKeyChange()) {
            preferenceProvider.getValue()
        } else {
            builtInProjectKey()
        }

    fun setProjectKey(newKey: String, source: ProjectKeyChangeSource) {
        if (!allowProjectKeyChange()) {
            Logger.w("Changing Bort project not permitted (ALLOW_PROJECT_KEY_CHANGE=false)")
            return
        }
        val syspropName = projectKeySyspropName()
        if (source == BROADCAST && !syspropName.isNullOrEmpty()) {
            Logger.e("Changing project key via broadcast not permitted because sysprop is configured: $syspropName")
            return
        }
        // Don't wipe files if the key didn't change.
        if (newKey == projectKey) return
        Logger.i("Changing Bort project key from $source")
        preferenceProvider.setValue(newKey)
        wipeStaleFiles()
    }

    fun reset(source: ProjectKeyChangeSource) {
        if (preferenceProvider.getValue() == builtInProjectKey()) {
            // Already unset; don't wipe files (this could be called every time bort starts).
            return
        }
        val syspropName = projectKeySyspropName()
        if (source == BROADCAST && !syspropName.isNullOrEmpty()) {
            Logger.e("Resetting project key via broadcast not permitted because sysprop is configured: $syspropName")
            return
        }
        Logger.i("Removing project key override from $source")
        preferenceProvider.remove()
        wipeStaleFiles()
    }

    private fun wipeStaleFiles() {
        // Delete all pending mar files (they use the old project key).
        marFileHoldingArea.get().deleteAllFiles()
        // Also delete any pending file uploads (including mar files).
        temporaryFileFactory.temporaryFileDirectory?.let {
            cleanupFiles(dir = it, maxDirStorageBytes = 0)
        }
    }
}

enum class ProjectKeyChangeSource {
    SYSPROP,
    BROADCAST,
}

interface ProjectKeyOverridePreferenceProvider {
    fun setValue(newValue: String)
    fun getValue(): String
    fun remove()
}

@ContributesBinding(scope = SingletonComponent::class, boundType = ProjectKeyOverridePreferenceProvider::class)
@Singleton
class RealProjectKeyOverridePreferenceProvider @Inject constructor(
    sharedPreferences: SharedPreferences,
    builtInProjectKey: BuiltInProjectKey,
) :
    CachedPreferenceKeyProvider<String>(
        sharedPreferences = sharedPreferences,
        defaultValue = builtInProjectKey(),
        preferenceKey = "project-key-override",
    ),
    ProjectKeyOverridePreferenceProvider
