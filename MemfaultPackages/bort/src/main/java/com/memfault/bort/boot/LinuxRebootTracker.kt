package com.memfault.bort.boot

import android.content.SharedPreferences
import com.memfault.bort.PREFERENCE_LAST_TRACKED_LINUX_BOOT_ID
import com.memfault.bort.shared.PreferenceKeyProvider
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

interface LastTrackedLinuxBootIdProvider {
    fun read(): String
    fun write(linuxBootId: String)
}

@ContributesBinding(SingletonComponent::class, boundType = LastTrackedLinuxBootIdProvider::class)
class RealLastTrackedLinuxBootIdProvider @Inject constructor(
    sharedPreferences: SharedPreferences,
) : LastTrackedLinuxBootIdProvider, PreferenceKeyProvider<String>(
    sharedPreferences = sharedPreferences,
    defaultValue = "",
    preferenceKey = PREFERENCE_LAST_TRACKED_LINUX_BOOT_ID,
) {
    override fun read() = super.getValue()
    override fun write(linuxBootId: String) = super.setValue(linuxBootId)
}

class LinuxRebootTracker @Inject constructor(
    private val linuxBootId: LinuxBootId,
    private val provider: LastTrackedLinuxBootIdProvider,
) {
    fun checkAndUnset(): Boolean =
        linuxBootId().let { currentBootId ->
            (currentBootId != provider.read()).also {
                if (it) provider.write(currentBootId)
            }
        }
}
