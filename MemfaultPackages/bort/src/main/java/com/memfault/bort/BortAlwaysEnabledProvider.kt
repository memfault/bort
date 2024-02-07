package com.memfault.bort

import com.memfault.bort.settings.BortEnabledProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

/**
 * A stub "enabled" provider; used only when the device does not require being enabled at runtime.
 */
class BortAlwaysEnabledProvider @Inject constructor() : BortEnabledProvider {
    override fun setEnabled(isOptedIn: Boolean) = Unit

    override fun isEnabled(): Boolean = true

    override fun isEnabledFlow(): Flow<Boolean> = MutableStateFlow(true)

    override fun requiresRuntimeEnable(): Boolean = false
}
