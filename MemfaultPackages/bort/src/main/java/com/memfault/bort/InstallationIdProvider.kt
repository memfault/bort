package com.memfault.bort

import android.content.SharedPreferences
import com.memfault.bort.shared.PreferenceKeyProvider
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import javax.inject.Inject

/**
 * Provides a UUID string that persists for this "installation".
 *
 * A re-installation occurs if the app's app data is cleared, such as via a factory reset
 * or manually. So a single device id might be associated with multiple installation ids.
 */
interface InstallationIdProvider {
    fun id(): String
}

@ContributesBinding(SingletonComponent::class, boundType = InstallationIdProvider::class)
class RandomUuidInstallationIdProvider @Inject constructor(
    sharedPreferences: SharedPreferences
) : InstallationIdProvider, PreferenceKeyProvider<String>(
    sharedPreferences = sharedPreferences,
    defaultValue = UNSET,
    preferenceKey = PREFERENCE_DEVICE_ID
) {
    init {
        if (id() == UNSET) {
            super.setValue(UUID.randomUUID().toString())
        }
    }

    override fun id(): String = super.getValue()

    companion object {
        private const val UNSET = ""
    }
}
