package com.memfault.bort.settings

import kotlinx.coroutines.flow.Flow

/**
 * Interface for whether Bort is enabled.
 *
 * Available to be injected in both Bort and UsageReporter with varying implementations.
 */
interface BortEnabledProvider {
    fun setEnabled(isOptedIn: Boolean)
    fun isEnabled(): Boolean
    fun isEnabledFlow(): Flow<Boolean>

    /**
     * Returns whether this app is enabled by default (false), or disabled by default and must be explicitly enabled
     * via an Intent. This allows for integrators to notify users that data is being collected, before enabling
     * Bort to collect the data.
     *
     * Returns the value of [BuildConfig.RUNTIME_ENABLE_REQUIRED].
     *
     * Note that this may crash depending on the App it is called from.
     */
    fun requiresRuntimeEnable(): Boolean
}
