package com.memfault.bort

import android.content.Context
import android.content.SharedPreferences
import com.memfault.bort.clientserver.MarBatchingTask.Companion.enqueueOneTimeBatchMarFiles
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.PreferenceKeyProvider
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks whether Bort is in developer mode. This is only allowed on userdebug/eng builds, and is enabled via Intent
 * (see [ShellControlReceiver]). Mode is persisted in SharedPrefs.
 *
 * Various callers will check whether dev mode is enabled by calling [isEnabled], to determine functionality.
 *
 * Note that the interface for this class ([DevMode]) is in bort-shared.
 */
@Singleton
@ContributesBinding(SingletonComponent::class)
class RealDevMode @Inject constructor(
    private val devModePreferenceProvider: DevModePreferenceProvider,
) : DevMode {
    private var enabled: Boolean? = null

    fun setEnabled(newEnabled: Boolean, context: Context) {
        Logger.d("Dev mode = $newEnabled")
        enabled = newEnabled
        devModePreferenceProvider.setValue(newEnabled)
        if (newEnabled) {
            // Enqueue a one-time mar upload, to clear the holding area (which will be skipped for uploads while dev
            // mode is enabled).
            enqueueOneTimeBatchMarFiles(context = context)
        }
    }

    override fun isEnabled(): Boolean = enabled ?: devModePreferenceProvider.getValue().also { enabled = it }
}

class DevModePreferenceProvider @Inject constructor(
    sharedPreferences: SharedPreferences,
) : PreferenceKeyProvider<Boolean>(sharedPreferences, defaultValue = false, preferenceKey = PREF_KEY) {
    companion object {
        private const val PREF_KEY = "dev_mode_enabled"
    }
}
