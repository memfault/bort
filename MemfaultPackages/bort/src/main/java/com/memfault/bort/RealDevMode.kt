package com.memfault.bort

import android.content.Context
import android.content.SharedPreferences
import com.memfault.bort.clientserver.MarBatchingTask.Companion.enqueueOneTimeBatchMarFiles
import com.memfault.bort.receivers.DropBoxEntryAddedReceiver
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.reporting.StateAgg
import com.memfault.bort.shared.CachedPreferenceKeyProvider
import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesBinding
import dagger.Lazy
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
@ContributesBinding(SingletonComponent::class)
class RealDevMode @Inject constructor(
    private val devModePreferenceProvider: DevModePreferenceProvider,
    private val dropBoxEntryAddedReceiver: Lazy<DropBoxEntryAddedReceiver>,
) : DevMode {
    fun setEnabled(newEnabled: Boolean, context: Context) {
        Logger.d("Dev mode = $newEnabled")
        devModePreferenceProvider.setValue(newEnabled)
        updateMetric()
        if (newEnabled) {
            // Enqueue a one-time mar upload, to clear the holding area (which will be skipped for uploads while dev
            // mode is enabled).
            enqueueOneTimeBatchMarFiles(context = context)
        }
        dropBoxEntryAddedReceiver.get().initialize()
    }

    override fun updateMetric() {
        DEV_MODE_ENABLED_METRIC.state(isEnabled())
    }

    companion object {
        private val DEV_MODE_ENABLED_METRIC = Reporting.report()
            .boolStateTracker("dev_mode_enabled", aggregations = listOf(StateAgg.LATEST_VALUE), internal = true)
    }

    override fun isEnabled(): Boolean = devModePreferenceProvider.getValue()
}

@Singleton
class DevModePreferenceProvider @Inject constructor(
    sharedPreferences: SharedPreferences,
) : CachedPreferenceKeyProvider<Boolean>(sharedPreferences, defaultValue = false, preferenceKey = PREF_KEY) {
    companion object {
        private const val PREF_KEY = "dev_mode_enabled"
    }
}
