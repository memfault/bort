package com.memfault.bort

import android.content.SharedPreferences
import com.memfault.bort.shared.PreferenceKeyProvider

interface LastTrackedLinuxBootIdProvider {
    fun read(): String
    fun write(linuxBootId: String)
}

class RealLastTrackedLinuxBootIdProvider(
    sharedPreferences: SharedPreferences,
) : LastTrackedLinuxBootIdProvider, PreferenceKeyProvider<String>(
    sharedPreferences = sharedPreferences,
    defaultValue = "",
    preferenceKey = PREFERENCE_LAST_TRACKED_LINUX_BOOT_ID,
) {
    override fun read() = super.getValue()
    override fun write(linuxBootId: String) = super.setValue(linuxBootId)
}

class LinuxRebootTracker(
    private val getLinuxBootId: () -> String,
    private val provider: LastTrackedLinuxBootIdProvider,
) {
    fun checkAndUnset(): Boolean =
        getLinuxBootId().let { currentBootId ->
            (currentBootId != provider.read()).also {
                if (it) provider.write(currentBootId)
            }
        }
}
