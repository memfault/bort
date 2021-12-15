package com.memfault.bort

import com.memfault.bort.settings.BortEnabledProvider
import javax.inject.Inject

/**
 * A stub "enabled" provider; used only when the device does not require being enabled at runtime.
 */
class BortAlwaysEnabledProvider @Inject constructor() : BortEnabledProvider {
    override fun setEnabled(isOptedIn: Boolean) {
    }

    override fun isEnabled(): Boolean {
        return true
    }

    override fun requiresRuntimeEnable(): Boolean = false
}
